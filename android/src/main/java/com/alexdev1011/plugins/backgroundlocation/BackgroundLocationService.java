package com.alexdev1011.plugins.backgroundlocation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BackgroundLocationService extends Service {
    private Notification noty;
    private final IBinder binder = new LocalBinder();
    private LocalStorage localStorage = new LocalStorage(this);
    public ServiceSettings settings = new ServiceSettings();
    private JSObject motivo = new JSObject();
    private static final int NOTIFICATION_ID = 45023;
    public LocationManager locationManager;
    public MyLocationListener listenerNetwork;
    public MyLocationListener listenerGPS;
    public MyLocationListener listenerLocation;
    public TramaStorage tramaStorage;
    private Context context;
    public Location lastLocationSending = null;
    private static final int MAX_BUFFER_SIZE = 1000; // Ajustar según necesidades
    private List<Location> bufferTramas = new ArrayList<Location>();
    int startId = 0;
    private int accuracy = 15;
    long serviceStarterAt = 0;
    long lastSendingAt = 0;
    private Timer timer;
    private TimerTask timerTask;
    private Boolean RESTARTSERVICE = false;
    private BitacoraManager bitacoraManager = new BitacoraManager(this);

    protected Location isBetterLocationinArray(Boolean force) {
        Location tramaDeRetorno = null;
        
        try {
            // Limitar tamaño del buffer
            if (bufferTramas.size() > MAX_BUFFER_SIZE) {
                bufferTramas = bufferTramas.subList(bufferTramas.size() - MAX_BUFFER_SIZE, bufferTramas.size());
            }

            for(int i = 0; i < bufferTramas.size(); i++) {
                if(tramaDeRetorno == null) {
                    tramaDeRetorno = bufferTramas.get(i);
                } else {
                    if(bufferTramas.get(i) != null && tramaDeRetorno != null) {
                        long timeDelta = bufferTramas.get(i).getTime() - tramaDeRetorno.getTime();
                        boolean isNewer = timeDelta > 0;
                        int accuracyDelta = (int) (bufferTramas.get(i).getAccuracy() - tramaDeRetorno.getAccuracy());
                        boolean isLessAccurate = accuracyDelta > 0;
                        boolean isMoreAccurate = accuracyDelta < 0;
                        boolean isSignificantlyLessAccurate = accuracyDelta > 1;
                        if(isMoreAccurate){
                            tramaDeRetorno = bufferTramas.get(i);
                        }else if( isNewer && !isLessAccurate ){
                            tramaDeRetorno = bufferTramas.get(i);
                        }
                    }
                }
            }
        } catch (Error error) {
            bitacoraManager.guardarEvento("Error en buffer", 500, error.getMessage());
            bufferTramas.clear(); // Limpiar en lugar de crear nueva lista
        }
        if(tramaDeRetorno == null) {
            motivo = new JSObject();
            return null;
        }
        if(force )
            return tramaDeRetorno;
        if( tramaDeRetorno.getAccuracy() < accuracy )
            return tramaDeRetorno;
        else return null;
    }
    protected Location isBetterLocation(Location location, Location lastLocationSending ) {

        if(location == null ){
            return null;
        }

        long deltaTime = 0;

        if(this.bufferTramas.size() > 2 ){
            deltaTime = location.getTime() - this.bufferTramas.get(0).getTime();
            if( deltaTime > 4000  ){
                this.bufferTramas.remove(0);
            }
        }
        try{
            this.bufferTramas.add(location);
        }catch (Error err){
            this.bufferTramas = new ArrayList<Location>();
            this.bufferTramas.add(location);
            System.out.println(err);
            return null;
        }


        /*
        if((this.bufferTramas.size() < 3 || deltaTime < 4000 ) && lastLocationSending != null ){
            return null;
        }
        */
        try {
            Object obg =  this.localStorage.getObject("motivo");
            if(obg != null){
                Motivo mot = (Motivo) obg;
                if(!(mot).sending){
                    motivo = new JSObject();
                    motivo.put("code", mot.code);
                    motivo.put("message", mot.message);
                    Location resolver = this.isBetterLocationinArray(true);
                    mot.sending = true;
                    localStorage.setObject("motivo",mot);
                    return resolver;
                }
            }
        } catch (Error | IOException error ){
            Log.e("error",error.getMessage());
        }


        if(settings.stopService){
           // toast.makeText( context, "serivico detenido ultima trama " , Toast.LENGTH_SHORT ).show();
            motivo = new JSObject();
            motivo.put("code", 1002);
            motivo.put("message", "serivico detenido ultima trama");
            Location resolver = this.isBetterLocationinArray(true);
            return resolver;
        }

        if(settings.panico){
            //toast.makeText( context, "panico activado " , Toast.LENGTH_SHORT ).show();
            motivo = new JSObject();
            motivo.put("code", 51);
            motivo.put("message", "Panico activado");
            Location resolver = this.isBetterLocationinArray(true);
            return resolver;
        }

        if (lastLocationSending == null ) {
            // A new location is always better than no location
            Location resolver = this.isBetterLocationinArray(true);
            if(resolver != null ) {
                //Toast.makeText( getApplicationContext(), "porque es la primera" ,Toast.LENGTH_LONG).show();
                motivo = new JSObject();
                motivo.put("code", 1001);
                motivo.put("message", "primer toma de contacto al activar el seguimiento");
                return resolver;
            }
            else
                return null;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = new Date().getTime() - lastLocationSending.getTime();
        boolean isSignificantlyNewer = timeDelta > settings.minS * 1000;
        boolean isSignificantlyOlder = timeDelta < - settings.minS * 1000;
        boolean isNewer = timeDelta > 800;

        float distBetweenLocation = location.distanceTo(lastLocationSending);
        boolean minDist = distBetweenLocation > settings.distanceFilter;
        boolean moreLength = location.distanceTo(lastLocationSending) > ( settings.distanceFilter * 10 / 100 ) ;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - lastLocationSending.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 1;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                lastLocationSending.getProvider());

        if (isSignificantlyNewer) {
            Location resolver = this.isBetterLocationinArray(true);

            if(resolver != null){
                motivo = new JSObject();
                motivo.put("code", 1000);
                motivo.put("message", "por tiempo limite " + settings.minS +" segundos" );
                //Toast.makeText( getApplicationContext(), "porque llego al tiempo limite " ,Toast.LENGTH_LONG).show();
                return resolver;
            }
            else
                return null;
            // If the new location is more than two minutes older, it must be worse
        }

        float bearingCompare = location.getBearing() - lastLocationSending.getBearing();
        if(location.getProvider() != "fused" && lastLocationSending.getProvider() != "fused" && distBetweenLocation > accuracy){
            if( bearingCompare > settings.grados || bearingCompare < -settings.grados &&  isNewer ){
                Location resolver = this.isBetterLocationinArray(true);
                if(resolver != null){
                    //Toast.makeText( getApplicationContext(), "porque cambio el rumbo" ,Toast.LENGTH_LONG).show();
                    motivo = new JSObject();
                    motivo.put("code", 5);
                    motivo.put("message", "por cambio de rumbo a "+ settings.grados +" grados" );
                    return resolver;
                }
                else
                    return null;
            }
        }
        if ( minDist ) {
            Location resolver = this.isBetterLocationinArray(true);
            if(resolver != null){
                //Toast.makeText( getApplicationContext(), "porque llego a la distancia objetivo" ,Toast.LENGTH_LONG).show();
                motivo = new JSObject();
                motivo.put("code", 4);
                motivo.put("message", "por distancia " + settings.distanceFilter +" metros");
                return resolver;
            }
            else
                return null;
        }

        return null;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }


    static final String ACTION_BROADCAST = (
            BackgroundLocationService.class.getPackage().getName() + ".broadcast"
    );


    public class MyLocationListener implements LocationListener
    {

        public void onLocationChanged(final Location loc)
        {
            try{
                ServiceSettings settingData = (ServiceSettings) localStorage.getObject("settingData");
                if( settingData != null) {
                    settings = settingData;
                }
                Location trama = null;
                if(loc != null )
                  try {
                      if(bufferTramas.size() > 0)
                      if(bufferTramas.get(0) == null ){
                          bufferTramas.clear();
                      }
                      trama = isBetterLocation(loc, lastLocationSending);
                  }catch ( Error error ){
                      bitacoraManager.guardarEvento("Error al obtener la mejor trama",12, error.getMessage());
                  }

                if( trama != null ) {
                    String pos = "si";
                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = context.registerReceiver(null, ifilter);
                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    float batteryPct = level * 100 / (float)scale;
                    JSObject location = new JSObject();
                    location.put("panic",settings.panico);
                    settings.panico = false;
                    settings.stopService = false;
                    try {
                        localStorage.setObject("settingData" , settings );
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    if( lastLocationSending != null &&  trama.getAccuracy() > accuracy   ) {
                        if( lastLocationSending.distanceTo(trama) < ((Math.round(trama.getAccuracy()) + Math.round(lastLocationSending.getAccuracy()))) *1.3 && trama.getAccuracy() * 2 > accuracy ){
                            pos = "no";
                            int param = 0;
                            if(lastLocationSending.getAccuracy() > trama.getAccuracy() * 1.3 ){
                                pos = "si mas preciso";
                            } else {
                                trama.setLatitude(lastLocationSending.getLatitude());
                                trama.setLongitude(lastLocationSending.getLongitude());
                            }
                        } else {
                            pos = "si normal";
                            location.put("latitude", trama.getLatitude());
                            location.put("longitude", trama.getLongitude());
                        }
                    } else {
                        location.put("latitude", trama.getLatitude());
                        location.put("longitude", trama.getLongitude());
                    }
                    lastLocationSending = trama;
                    location.put("reason", motivo);
                    location.put("accuracy",Math.round(trama.getAccuracy()));
                    location.put("altitude",Math.round(trama.getAltitude()));
                    location.put("bearing", Math.round(trama.getBearing()));
                    location.put("provider", trama.getProvider());
                    location.put("speed",Math.round((trama.getSpeed() * 3.6)));
                    location.put("time", trama.getTime());
                    location.put("battery",batteryPct);
                    JSObject senData = new JSObject();
                    senData.put("userID",settings.userID);
                    senData.put("urlRequest",settings.urlRequests);
                    if(settings.authorization != null )
                        senData.put("auth", settings.authorization );
                    else
                        senData.put("auth", "");
                    senData.put( "trama",location);
                    if(settings.storageLocal){
                        bitacoraManager.guardarEvento("datos de posición "+ pos+" precisión "+ Math.round(trama.getAccuracy()),motivo.getInteger("code"),motivo.getString("message"));
                    }
                    System.out.println("se envia la trama");
                    lastSendingAt = new Date().getTime();
                    if ( settings.urlRequests != "" ) {
                        try{
                            System.out.println("256 agregando trama");
                            tramaStorage.agregarTrama(senData);
                        } catch ( Error err ){
                            System.out.println(err);
                            System.out.println("un error");
                            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                                restearVariables();
                            } else {
                                RESTARTSERVICE = true;
                                Intent ser = new Intent(context, BackgroundLocationService.class);
                                ser.setAction("ACTION.RESTARTFOREGROUND_ACTION");
                                context.startService(ser);
                            }

                        }
                    }
                    //.makeText( context, "entrada tomada" , Toast.LENGTH_SHORT ).show();
                }

            }catch (Error error){
                bitacoraManager.guardarEvento("Servicio destruido",10,error.getMessage());
            };
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        public void onProviderDisabled(String provider)
        {
           // Toast.makeText( getApplicationContext(), "Gps Disabled", Toast.LENGTH_SHORT ).show();
        }


        public void onProviderEnabled(String provider)
        {
            // Toast.makeText( getApplicationContext(), "Gps Enabled", Toast.LENGTH_SHORT).show();
        }
    }

    public void stopService(){
        stopForeground(true);
        stopSelfResult(startId);
    }
    @Override
    public IBinder onBind(Intent arg0) {
        return binder;
    }

    public void onDestroy() {
        System.out.println("terminando el servicio");
        if(listenerGPS != null)
            locationManager.removeUpdates(listenerGPS);
        if(listenerNetwork != null)
            locationManager.removeUpdates(listenerNetwork);
        stopTimer();
        //Toast.makeText(this, "Traker Detenido", Toast.LENGTH_LONG).show();
        bitacoraManager.guardarEvento("Servicio destruido",10,"Tracker detenido");
        Log.d("My Service", "onDestroy");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<Boolean> handler = executor.submit(() -> restartService());
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                handler.get( 300 , TimeUnit.MILLISECONDS);
            }
        } catch (TimeoutException e) {
            handler.cancel(true);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        executor.shutdownNow();

        super.onDestroy();
    }
    public Boolean restartService(){
        if(RESTARTSERVICE){
            RESTARTSERVICE = false;
            Intent intent = new Intent(context, BackgroundLocationService.class);
            intent.setAction("ACTION.STARTFOREGROUND_ACTION");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        }
        return true;
    }
    public void restearVariables(){
        System.out.println("reseteando variables");
        serviceStarterAt = new Date().getTime();
        localStorage = new LocalStorage(this);
        bitacoraManager = new BitacoraManager(context);
        settings = new ServiceSettings();
        this.bufferTramas.clear();
        try {
            Http cc =  Http.getInstance(this);
            cc.cancelAllRequests();
            tramaStorage = new TramaStorage(this);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        RESTARTSERVICE = false;
    }
    public class LocalBinder extends Binder {
        BackgroundLocationService getService(String id ) {
            return BackgroundLocationService.this;
        }
    }


    @SuppressLint({"WrongConstant", "InvalidWakeLockTag", "MissingPermission", "LongLogTag"})
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && "ACTION.STOP_SERVICE".equals(intent.getAction())) {
            Log.i("BackgroundLocationService", "Stopping service on request");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if(intent == null){
            intent = new Intent(context, BackgroundLocationService.class);
            intent.setAction("ACTION.STARTFOREGROUND_ACTION");
        }

        if (intent.getAction().equals("ACTION.STARTFOREGROUND_ACTION")) {
            motivo.put("code", 0 );
            motivo.put("message", "no coorresponde ningun motivo");
            System.out.println("el servicio se esta activando gracias al action");
            ServiceSettings settingData = (ServiceSettings) this.localStorage.getObject("settingData");
            if( settingData != null) {
                this.settings = settingData;
            }
            this.startId = startId;
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            /*
            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setInterval(1000);
            locationRequest.setFastestInterval(500);
            locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
            */

            //listenerNetwork = new MyLocationListener();
            listenerGPS = new MyLocationListener();
            listenerLocation = new MyLocationListener();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return 0;
            }
            bufferTramas.clear();
            //locationManager.requestLocationUpdates(LocationManager.FUSED_PROVIDER,  3000 , 0, listenerNetwork);
            serviceStarterAt = new Date().getTime();
            LocationCallback locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult == null) {
                        return;
                    }
                    for (Location location : locationResult.getLocations()) {
                        listenerLocation.onLocationChanged(location);
                        // Update UI with location data
                        // ...
                    }
                }
            };
            //LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 250, 0, listenerGPS);
            Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            listenerLocation.onLocationChanged(loc);
             startTimer();

            //locationManager.requestLocationUpdates(LocationManager.	FUSED_PROVIDER, 120 , 0, listenerLocation);
           /**
            *  if(this.settings.inBG)
            *      onActivityStopped();
            *                 */
            //Toast.makeText(this, "Traker iniciado", Toast.LENGTH_LONG).show();
            onActivityStopped();
        } else {
            if (intent.getAction().equals( "ACTION.STOPFOREGROUND_ACTION")) {
                System.out.println("el servicio se detuvo gracias al action");
                //your end servce code
                stopForeground(true);
                stopSelfResult(startId);
            } else if (intent.getAction().equals( "ACTION.RESTARTFOREGROUND_ACTION")) {
                System.out.println("el servicio se detuvo gracias al action");
                //your end servce code
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    restearVariables();
                } else {
                    RESTARTSERVICE = true;
                    stopForeground(true);
                    stopSelfResult(startId);
                }
            }
        }
        //return super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate()  {
        context = this;

        try {
            this.tramaStorage = new TramaStorage(this);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        ServiceSettings settingData = (ServiceSettings) this.localStorage.getObject("settingData");

        if( settingData != null) {
            this.settings = settingData;
        }
        createChannel();
       // Toast.makeText(this, "Traker iniciado", Toast.LENGTH_LONG).show();
        super.onCreate();
    }



    /** gestion de notificaciones **/

    // crea una nueva notificacion
    private Notification getNoty(){
        Notification.Builder builder = new Notification.Builder(this)
                .setContentTitle( this.settings.notificationTitle )
                .setContentText( this.settings.notificationContent )
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis());

        try {
            String name = getAppString(
                    "capacitor_background_geolocation_notification_icon",
                    "mipmap/ic_launcher"
            );
            String[] parts = name.split("/");
            // It is actually necessary to set a valid icon for the notification to behave
            // correctly when tapped. If there is no icon specified, tapping it will open the
            // app's settings, rather than bringing the application to the foreground.
            builder.setSmallIcon(
                    getAppResourceIdentifier(parts[1], parts[0])
            );
        } catch (Exception e) {
            Logger.error("Could not set notification icon", e);
        }

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(
                getPackageName()
        );
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            builder.setContentIntent(
                    PendingIntent.getActivity(
                            this,
                            0,
                            launchIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    )
            );
        }

        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId( BackgroundLocationService.class.getPackage().getName());
        }

        Notification build = builder.build();
        this.noty = build;
        return build;

    }
    // crea el canal para la notificación
    private void createChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE
            );
            NotificationChannel channel;
            channel = new NotificationChannel(
                    BackgroundLocationService.class.getPackage().getName(),
                    getAppString(
                            "capacitor_background_geolocation_notification_channel_name",
                            "Background Tracking"
                    ),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }
    }

    // controladores de la ejecucion de la notificacion
    void onActivityStarted() {
        stopForeground(true);
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    private Notification getNotification() {
        if(noty != null){
            return noty;
        } else {
            return getNoty();
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    void onActivityStopped() {
        Notification notification = getNotification();
        if (notification != null) {
            System.out.println("mostrando la notificación" );
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    //funciones de soporte para crear la notificacion y el canal
    private int getAppResourceIdentifier(String name, String defType) {
        return getResources().getIdentifier(
                name,
                defType,
                getPackageName()
        );
    }

    private String getAppString(String name, String fallback) {
        int id = getAppResourceIdentifier(name, "string");
        return id == 0 ? fallback : getString(id);
    }

    private void startTimer() {
        stopTimer(); // Asegurar que no hay timers activos
        timer = new Timer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    if(new Date().getTime() > serviceStarterAt + 1800000 ){
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                            restearVariables();
                        } else {
                            System.out.println("reseteando servicio");
                            RESTARTSERVICE = true;
                            Intent ser = new Intent(context, BackgroundLocationService.class);
                            ser.setAction("ACTION.RESTARTFOREGROUND_ACTION");
                            context.startService(ser);
                        }
                    }
                    Location locthis = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if(locthis != null){
                        locthis.setTime(new Date().getTime());
                        listenerLocation.onLocationChanged(locthis);
                    } else {
                        locthis = isBetterLocationinArray(true);
                        if(locthis != null) {
                            locthis.setTime(new Date().getTime());
                            listenerLocation.onLocationChanged(locthis);
                        }
                    }
                } catch (Exception e) {
                    bitacoraManager.guardarEvento("Error en timer", 501, e.getMessage());
                }
            }
        };
        timer.schedule(timerTask, 1800000);
    }

    private void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

}
