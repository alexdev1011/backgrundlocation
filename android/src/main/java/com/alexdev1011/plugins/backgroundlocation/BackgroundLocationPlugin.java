package com.alexdev1011.plugins.backgroundlocation;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@CapacitorPlugin(name = "BackgroundLocation")
public class BackgroundLocationPlugin extends Plugin {
    private BackgroundLocation implementation = new BackgroundLocation();
    /** notifications setings */
    public ServiceSettings settings = new ServiceSettings();
    private BackgroundLocationService service;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private Boolean permisos = false;
    private Boolean sync = false;
    private int tryrequest = 0;
    private String callbackId;
    private PluginCall status;
    private static String LOG_TAG = BackgroundLocationService.class.getName();
    private LocalStorage localStorage;
    private PluginCall startServiceCall = null;

    public boolean checkServiceRunning(Class<?> serviceClass){
        final ActivityManager activityManager = (ActivityManager) this.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

        for (ActivityManager.RunningServiceInfo runningServiceInfo : services) {
            if (runningServiceInfo.service.getClassName().equals(serviceClass.getName())){
                return true;
            }
        }
        return false;
    }

    @PluginMethod(returnType=PluginMethod.RETURN_CALLBACK)
    public void status(PluginCall call) {
        call.setKeepAlive(true);
        JSObject ret = new JSObject();
        JSObject config = new JSObject();
        config.put("notificationTitle", this.settings.notificationTitle);
        config.put("notificationContent" , this.settings.notificationContent);
        config.put("userId", this.settings.userID);
        config.put("inBG", this.settings.inBG);
        config.put("authorization", this.settings.authorization);
        config.put("distanceFilter",this.settings.distanceFilter);
        config.put("urlRequests", this.settings.urlRequests);
        config.put("minS", this.settings.minS);
        config.put("imei", this.settings.imei);
        ret.put("settings", config);
        this.status = call;
        System.out.println( this.status);
        if (checkServiceRunning(BackgroundLocationService.class)) {
            ret.put("status", true);
            call.resolve(ret);
        }
        else {
            ret.put("status", false);
            call.resolve(ret);
        }
    }


    private void showExplanation(String title, String message, final String permission) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                requestPermissionLauncher.launch(permission);
                tryrequest++;
            }
        });
        builder.create().show();
    }
    private void verificarPermisos( PluginCall call ){

        System.out.println("instanciado el onActivity");
        requestPermissionLauncher.launch( android.Manifest.permission.ACCESS_FINE_LOCATION);

    }
    void resolverperisoimei(Boolean isGranted)  {
        System.out.println("solicitud de permiso");
        if (isGranted) {
            // Permission is granted. Continue the action or workflow in your
            // app.
            this.settings.imei = getDeviceIMEI(getContext());

            try {
                localStorage.setObject("settingData" , settings);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            getActivity().finish();
            // Explain to the user that the feature is unavailable because the
            // feature requires a permission that the user has denied. At the
            // same time, respect the user's decision. Don't link to system
            // settings in an effort to convince the user to change their
            // decision.
        }
        System.out.println(permisos);
    }
    public String getDeviceIMEI(Context context) {
        String deviceUniqueIdentifier = null;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            deviceUniqueIdentifier = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
        } else {
            int readIMEI= -1;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                readIMEI= context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE);
            }
            System.out.println( readIMEI);
            if (readIMEI == PackageManager.PERMISSION_GRANTED) {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (null != tm) {
                    deviceUniqueIdentifier = tm.getDeviceId();
                }
                if (null == deviceUniqueIdentifier || 0 == deviceUniqueIdentifier.length()) {
                    deviceUniqueIdentifier = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                }
            } else {
                getActivity().registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::resolverperisoimei).launch(Manifest.permission.READ_PHONE_STATE);
            }
        }
        return deviceUniqueIdentifier;
    }

    @PluginMethod(returnType=PluginMethod.RETURN_CALLBACK)
    public void getTramas(PluginCall call) throws JSONException {
        JSObject data = new JSObject();
        try {
            String localTramas = localStorage.getItem("localTramas");
            data.put("tramas",  new JSONArray(localTramas));
            call.resolve(data);
        } catch (Exception e ){
            data.put("tramas",  new JSONArray());
            call.resolve(data);
        }
    }

    @PluginMethod(returnType=PluginMethod.RETURN_CALLBACK)
    public void getBitacora(PluginCall call) throws JSONException {
        System.out.println("obteniendo bitacora 1 ");
        JSObject data = new JSObject();
        try {
            String localTramas = localStorage.getItem("bitacora");
            data.put("bitacora",  new JSONArray(localTramas));
            System.out.println("data =>");
            System.out.println(data);
            call.resolve(data);
        } catch (Exception e ){
            data.put("bitacora",  new JSONArray());
            call.resolve(data);
        }
    }

    @PluginMethod(returnType=PluginMethod.RETURN_CALLBACK)
    public void clearTramas(PluginCall call) throws JSONException {
        localStorage.removeItem("localTramas");
        localStorage.removeItem("bitacora");
        call.resolve();
    }

    @PluginMethod
    public void panic(PluginCall call) throws IOException {
        this.settings.panico = true;
        this.localStorage.setObject("settingData" , this.settings );
    }
    @PluginMethod
    public void startService(PluginCall call) throws InterruptedException, IOException {
        this.startServiceCall = call;

        verificarPermisos(call);

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @PluginMethod
    public void stopService(PluginCall call) throws InterruptedException {
        if (checkServiceRunning(BackgroundLocationService.class)){
            System.out.println("stopService");
            settings.stopService=true;
            this.settings.appStarted=false;
            try {
                localStorage.setObject("settingData" , settings );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Timer myTimer = new Timer(); //Set up a timer, to execute TimerMethod repeatedly

            myTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    ServiceSettings settingData = (ServiceSettings) localStorage.getObject("settingData");
                    if( settingData != null) {
                        System.out.println(settingData.stopService);
                        if(settingData.stopService == false){
                            settings = settingData;
                            JSObject ret = new JSObject();
                            Intent ser = new Intent(getContext(), BackgroundLocationService.class);
                            ser.setAction("ACTION.STOPFOREGROUND_ACTION");
                            getContext().startService(ser);
                            boolean b = getContext().stopService(new Intent( getActivity(), BackgroundLocationService.class));
                            System.out.println(b);
                            ret.put("value", "implementation.echo(value)");
                            call.resolve(ret);
                            myTimer.cancel();
                        }
                    }
                }
            }, 0, 500);
        }


    }

    @Override
    protected void handleOnResume() {
        if (service != null) {
            service.onActivityStarted();
            /*
            if (stoppedWithoutPermissions && hasRequiredPermissions()) {
                service.onPermissionsGranted();
            }*/
        }
        super.handleOnResume();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void handleOnPause() {
        if (service != null) {
            System.out.println( "Desde el on pause =>" +  service.settings.inBG);
            service.onActivityStopped();
        }
        // stoppedWithfoutPermissions = !hasRequiredPermissions();
        super.handleOnPause();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void load() {
        super.load();
        this.localStorage = new LocalStorage(this.getContext());
        //localStorage.removeItem("tramas");
        ServiceSettings settingData = (ServiceSettings) this.localStorage.getObject("settingData");
        requestPermissionLauncher = getActivity().registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::onActivityResult);
        if( settingData != null) {
            this.settings = settingData;
        } {
            this.settings.imei = getDeviceIMEI(getContext());
            try {
                localStorage.setObject("settingData" , settings);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        final ActivityManager activityManager = (ActivityManager) this.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);
        /**
         *  class Test implements Http.CallBack {
         *             @Override
         *             public void onSuccess(JSONObject response) {
         *                 System.out.println("desde afuera");
         *                 Log.d("HTTPRE", String.valueOf(response));
         *             }
         *         }
         *         Test callback = new Test();
         *         httpRequests.get( "http://192.168.0.109:8200/api/data", callback,this.getContext());
         *         JSObject sendData = new JSObject();
         *         sendData.put("si", "llego");
         *         httpRequests.post("http://192.168.0.109:8200/api/data" , callback,this.getContext(),sendData);
        **/

    }


    private void onActivityResult(Boolean isGranted)  {
        System.out.println("solicitud de permiso");

        if (isGranted) {
            // Permission is granted. Continue the action or workflow in your
            // app.
            System.out.println(Build.VERSION.SDK_INT  + " algo de la version ");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                System.out.println(getContext().checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED);
                if(getContext().checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED){
                    showExplanation("SOLICITUD DE PERMISO", "Esta app requiere permisos de ubicacion todo el tiempo para poder funcionar. solo se tomara la ubicacion cuando este activo el servicio identificado por la notificación fijada.", Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                } else {
                    permisos = true;
                    callbackId = startServiceCall.getCallbackId();
                    IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
                    intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
                    intentFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
                    AutoStartBackgroundLocation e = new AutoStartBackgroundLocation();
                    getContext().registerReceiver( e , intentFilter);
                    this.settings.notificationTitle = startServiceCall.getString("notificationTitle");
                    this.settings.notificationContent = startServiceCall.getString("notificationContent");
                    this.settings.distanceFilter = startServiceCall.getInt("distanceFilter");
                    this.settings.urlRequests = startServiceCall.getString("urlRequests");
                    this.settings.minS = startServiceCall.getInt("minS");
                    this.settings.inBG = startServiceCall.getBoolean("inBG");
                    this.settings.userID = startServiceCall.getString("userId");
                    this.settings.authorization = startServiceCall.getString("authorization");
                    this.settings.stopService = false;
                    this.settings.appStarted = true;
                    System.out.println(this.settings.imei);
                    System.out.println("desde dentro del plugin");
                    System.out.println(startServiceCall.getInt("distanceFilter"));
                    try {
                        this.localStorage.setObject("settingData" , this.settings );
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
                    filter.addAction(Intent.ACTION_BOOT_COMPLETED);
                    filter.addCategory(Intent.CATEGORY_DEFAULT);
                    getContext().registerReceiver(e, filter);
                    JSObject ret = new JSObject();
                    ret.put("status", true);
                    JSObject config = new JSObject();
                    config.put("notificationTitle", this.settings.notificationTitle);
                    config.put("notificationContent" , this.settings.notificationContent);
                    config.put("userId", this.settings.userID);
                    config.put("inBG", this.settings.inBG);
                    config.put("authorization", this.settings.authorization);
                    config.put("distanceFilter",this.settings.distanceFilter);
                    config.put("urlRequests", this.settings.urlRequests);
                    config.put("minS", this.settings.minS);
                    config.put("imei", this.settings.imei);
                    ret.put("settings", config);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent ser = new Intent(getContext(), BackgroundLocationService.class);
                        ser.setAction("ACTION.STARTFOREGROUND_ACTION");
                        this.getContext().startForegroundService(ser);
                        status.resolve(ret);
                    }
                    System.out.print(service);
                    startServiceCall.resolve(ret);
                }
            } else { permisos = true;
                callbackId = startServiceCall.getCallbackId();
                IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
                intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
                intentFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
                AutoStartBackgroundLocation e = new AutoStartBackgroundLocation();
                getContext().registerReceiver( e , intentFilter);
                this.settings.notificationTitle = startServiceCall.getString("notificationTitle");
                this.settings.notificationContent = startServiceCall.getString("notificationContent");
                this.settings.distanceFilter = startServiceCall.getInt("distanceFilter");
                this.settings.urlRequests = startServiceCall.getString("urlRequests");
                this.settings.minS = startServiceCall.getInt("minS");
                this.settings.inBG = startServiceCall.getBoolean("inBG");
                this.settings.userID = startServiceCall.getString("userId");
                this.settings.authorization = startServiceCall.getString("authorization");
                this.settings.stopService = false;
                this.settings.appStarted = true;
                System.out.println(this.settings.imei);
                System.out.println("desde dentro del plugin");
                System.out.println(startServiceCall.getInt("distanceFilter"));
                try {
                    this.localStorage.setObject("settingData" , this.settings );
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
                filter.addAction(Intent.ACTION_BOOT_COMPLETED);
                filter.addCategory(Intent.CATEGORY_DEFAULT);
                getContext().registerReceiver(e, filter);
                JSObject ret = new JSObject();
                ret.put("status", true);
                JSObject config = new JSObject();
                config.put("notificationTitle", this.settings.notificationTitle);
                config.put("notificationContent" , this.settings.notificationContent);
                config.put("userId", this.settings.userID);
                config.put("inBG", this.settings.inBG);
                config.put("authorization", this.settings.authorization);
                config.put("distanceFilter",this.settings.distanceFilter);
                config.put("urlRequests", this.settings.urlRequests);
                config.put("minS", this.settings.minS);
                config.put("imei", this.settings.imei);
                ret.put("settings", config);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent ser = new Intent(getContext(), BackgroundLocationService.class);
                    ser.setAction("ACTION.STARTFOREGROUND_ACTION");
                    this.getContext().startForegroundService(ser);
                    status.resolve(ret);
                }
                System.out.print(service);
                startServiceCall.resolve(ret);
            }
        } else {
            permisos = false;
            JSObject ret = new JSObject();
            ret.put("started", false);
            startServiceCall.reject("Solicitando el permiso");
            // Explain to the user that the feature is unavailable because the
            // feature requires a permission that the user has denied. At the
            // same time, respect the user's decision. Don't link to system
            // settings in an effort to convince the user to change their
            // decision.
        }
        System.out.println(permisos);
    }

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");
        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

}
