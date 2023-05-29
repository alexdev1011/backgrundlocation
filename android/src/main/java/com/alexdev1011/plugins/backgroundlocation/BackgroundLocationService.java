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
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class BackgroundLocationService extends Service {
    private Notification noty;
    private final IBinder binder = new LocalBinder();
    private LocalStorage localStorage = new LocalStorage(this);
    public ServiceSettings settings = new ServiceSettings();
    private JSObject motivo = new JSObject();

    private static final int NOTIFICATION_ID = 45023;
    private Toast toast;
    public static final String BROADCAST_ACTION = "Hello World";
    private static final int TWO_MINUTES = 1000 * 60 * 2;
    public LocationManager locationManager;
    public MyLocationListener listenerNetwork;
    public MyLocationListener listenerGPS;
    public MyLocationListener listenerLocation;
    public JSONArray unSaveLocations;
    public TramaStorage tramaStorage;
    private Http httpRequests = new Http();
    private Context context;
    public Location lastLocationSending = null;
    private JSONArray tramasAllLocal = new JSONArray();
    private List<Location> bufferTramas = new ArrayList<>();
    Intent intent;
    int counter = 0;
    int startId = 0;

    private int accuracy = 15;
    long serviceStarterAt = 0;
    long lastSendingAt = 0;
    private PowerManager.WakeLock wl;


    protected Location isBetterLocationinArray( Boolean force ){
        Location tramaDeRetorno = null;
        List<Location> stackbufferTramas = this.bufferTramas;
        for(int i = 0; i < stackbufferTramas.size() ; i++ ){
            if(tramaDeRetorno == null ){
                   tramaDeRetorno = stackbufferTramas.get(i);
            } else {
                long timeDelta = stackbufferTramas.get(i).getTime() - tramaDeRetorno.getTime();
                boolean isNewer = timeDelta > 0;
                int accuracyDelta = (int) (stackbufferTramas.get(i).getAccuracy() - tramaDeRetorno.getAccuracy());
                boolean isLessAccurate = accuracyDelta > 0;
                boolean isMoreAccurate = accuracyDelta < 0;
                boolean isSignificantlyLessAccurate = accuracyDelta > 1;
                if(isMoreAccurate){
                    tramaDeRetorno = stackbufferTramas.get(i);
                }else if( isNewer && !isLessAccurate ){
                    tramaDeRetorno = stackbufferTramas.get(i);
                }
            }
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
        long deltaTime = 0;
        if(this.bufferTramas.size() > 2 ){
            deltaTime = location.getTime() - this.bufferTramas.get(0).getTime();
            if( deltaTime > 4000  ){
                this.bufferTramas.remove(0);
            }
        }
        this.bufferTramas.add(location);

        System.out.println(deltaTime + " "  + bufferTramas.size());

        if(this.bufferTramas.size() < 3 || deltaTime < 4000 ){
            return null;
        }

        long latitud = 0;
        long longitud = 0;
        if(settings.stopService){
           // toast.makeText( context, "serivico detenido ultima trama " , Toast.LENGTH_SHORT ).show();
            motivo = new JSObject();
            motivo.put("code", 25);
            motivo.put("message", "serivico detenido ultima trama");
            Location resolver = this.isBetterLocationinArray(true);
            return resolver;
        }
        if(settings.panico){
            toast.makeText( context, "panico activado " , Toast.LENGTH_SHORT ).show();
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
                motivo.put("code", 1);
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
                motivo.put("code", 2);
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
                    motivo.put("code", 3);
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
                    trama = isBetterLocation(loc, lastLocationSending);

                if( trama != null ) {

                    bufferTramas = new ArrayList<>();
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
                        System.out.println( "salto por la mala ubicacion");
                        if( lastLocationSending.distanceTo(trama) < ((Math.round(trama.getAccuracy()) + Math.round(lastLocationSending.getAccuracy()))) *1.1 && trama.getAccuracy() * 3 > accuracy ){
                            pos = "no";
                            int param = 0;
                            if(lastLocationSending.getAccuracy() > trama.getAccuracy() * 1.2 ){
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
                    System.out.println(loc.getProvider());
                    senData.put("userID",settings.userID);
                    senData.put("urlRequest",settings.urlRequests);
                    System.out.println( "Accuracy => " +  loc.getAccuracy());

                    if(settings.authorization != null )
                        senData.put("auth", settings.authorization );
                    else
                        senData.put("auth", "");
                    senData.put( "trama",location);

                    try {
                        String localTramas = localStorage.getItem("localTramas");
                        tramasAllLocal =  new JSONArray(localTramas);
                    } catch (Exception e ){
                        tramasAllLocal = new JSONArray();
                    }
                    if(settings.storageLocal){
                        tramasAllLocal.put(location);
                        localStorage.setItem("localTramas",tramasAllLocal.toString());
                    }

                    JSONArray bitacora = new JSONArray();
                    try {
                        String localTramas = localStorage.getItem("bitacora");
                        bitacora =  new JSONArray(localTramas);
                    } catch (Exception e ){
                        bitacora = new JSONArray();
                    }
                    if(settings.storageLocal){
                        JSObject evento = new JSObject();
                        evento.put("razon","datos "+ pos+" precisión "+ Math.round(trama.getAccuracy()));
                        evento.put("motivo",motivo);
                        evento.put("fecha",trama.getTime());
                        System.out.println(evento);
                        System.out.println(bitacora.length());
                        if(bitacora.length() > 91){
                            JSONArray bit =  new JSONArray();
                            for (int i = bitacora.length() -89 ; i < bitacora.length() ; i++ ){
                                try {
                                    bit.put(bitacora.get(i));
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            bitacora = bit;
                        }
                        bitacora.put(evento);
                        localStorage.setItem("bitacora",bitacora.toString());
                    }

                    System.out.println("se envia la trama");
                    System.out.println(senData);
                    lastSendingAt = new Date().getTime();
                    if ( settings.urlRequests != "" ) {
                        try{
                            tramaStorage.agregarTrama(senData);
                        } catch ( Exception err ){
                            System.out.println(err);
                            System.out.println("un error");
                        }
                    }
                    //.makeText( context, "entrada tomada" , Toast.LENGTH_SHORT ).show();
                }

            }catch (Error error){
                JSONArray bitacora = new JSONArray();
                try {
                    String localTramas = localStorage.getItem("bitacora");
                    bitacora =  new JSONArray(localTramas);
                } catch (Exception e ){
                    bitacora = new JSONArray();
                }
                JSObject motivo = new JSObject();
                motivo.put("code","12");
                motivo.put("message", error.getMessage());
                JSObject evento = new JSObject();
                evento.put("razon","Servicio destruido");
                evento.put("motivo",motivo);
                evento.put("fecha",new Date().getTime());
                System.out.println(evento);
                if(bitacora.length() > 91){
                    JSONArray bit =  new JSONArray();
                    for (int i = bitacora.length() -89 ; i < bitacora.length() ; i++){
                        try {
                            bit.put(bitacora.get(i));
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    bitacora = bit;
                }

                bitacora.put(evento);
                localStorage.setItem("bitacora",bitacora.toString());
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

        //Toast.makeText(this, "Traker Detenido", Toast.LENGTH_LONG).show();
        JSONArray bitacora = new JSONArray();
        try {
            String localTramas = localStorage.getItem("bitacora");
            bitacora =  new JSONArray(localTramas);
        } catch (Exception e ){
            bitacora = new JSONArray();
        }
        JSObject motivo = new JSObject();
        motivo.put("code","08");
        motivo.put("message", "Tracker detenido");
        JSObject evento = new JSObject();
        evento.put("razon","Servicio destruido");
        evento.put("motivo",motivo);
        evento.put("fecha",new Date().getTime());
        System.out.println(evento);
        if(bitacora.length() > 91){
            JSONArray bit =  new JSONArray();
            for (int i = bitacora.length() -89 ; i < bitacora.length() ; i++ ){
                try {
                    bit.put(bitacora.get(i));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
            bitacora = bit;
        }
        bitacora.put(evento);
        localStorage.setItem("bitacora",bitacora.toString());
        Log.d("My Service", "onDestroy");
        super.onDestroy();
    }

    public class LocalBinder extends Binder {
        BackgroundLocationService getService(String id ) {
            return BackgroundLocationService.this;
        }
    }


    @SuppressLint({"WrongConstant", "InvalidWakeLockTag", "MissingPermission"})
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        if(intent == null){
            intent = new Intent(context, BackgroundLocationService.class);
            intent.setAction("ACTION.STARTFOREGROUND_ACTION");
        }

        if (intent.getAction().equals("ACTION.STARTFOREGROUND_ACTION")) {
            motivo.put("code", 0 );
            motivo.put("message", "no coorresponde ningun motivo");
            try {
                String localTramas = localStorage.getItem("localTramas");
                if(localTramas != "")
                    tramasAllLocal = new JSONArray(localTramas);
                else
                    tramasAllLocal = new JSONArray();
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("el servicio se esta activando gracias al action");
            ServiceSettings settingData = (ServiceSettings) this.localStorage.getObject("settingData");
            if( settingData != null) {
                this.settings = settingData;
            }
            this.startId = startId;
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setInterval(1000);
            locationRequest.setFastestInterval(500);
            locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);


            //listenerNetwork = new MyLocationListener();
            listenerGPS = new MyLocationListener();
            listenerLocation = new MyLocationListener();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return 0;
            }
            bufferTramas = new ArrayList<>();
            System.out.println(locationManager.getAllProviders());
            //locationManager.requestLocationUpdates(LocationManager.FUSED_PROVIDER,  3000 , 0, listenerNetwork);
            serviceStarterAt = new Date().getTime();
            System.out.println(locationManager.getAllProviders());
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
            LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 90, 0, listenerGPS);
            Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            listenerGPS.onLocationChanged(loc);
            new Timer().scheduleAtFixedRate(new TimerTask(){
                @SuppressLint("MissingPermission")
                @Override
                public void run(){
                    if(lastSendingAt != 0){
                        long timeDelta = new Date().getTime() - lastSendingAt;
                        System.out.println("timer => " + timeDelta);
                        if(timeDelta > settings.minS *1.2 * 1000 ){
                            System.out.println("aca el voy a reenviar la ultima posi conocida");
                           Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                           if(loc != null){
                               loc.setTime(new Date().getTime());
                            listenerLocation.onLocationChanged(loc);
                           }
                        }
                    }
                }
            },0,settings.minS * 1000);

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
            }
        }
        //return super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate()  {
        intent = new Intent(BROADCAST_ACTION);
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
        // this.noty = build;
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

    private void setLocalData(){
        localStorage.getItem("notification");
    }
}
