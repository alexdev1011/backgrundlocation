package com.alexdev1011.plugins.backgroundlocation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.net.Uri;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;

@CapacitorPlugin(name = "BackgroundLocation")
public class BackgroundLocationPlugin extends Plugin {
    private BackgroundLocation implementation = new BackgroundLocation();
    /** notifications setings */
    public ServiceSettings settings = new ServiceSettings();
    private BackgroundLocationService service;
    private AbstractCollection<String> permisosAceptados;
    private List<String> permisosARequerir = new ArrayList<>();
    private String currentRequestedPermission = null;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private BitacoraManager bitacoraManager;
    private ActivityResultLauncher<Intent> batteryOptimizationLauncher;
    private Boolean permisos = false;
    private Boolean sync = false;
    private int tryrequest = 0;
    private PluginCall status;
    private static String LOG_TAG = BackgroundLocationService.class.getName();
    private LocalStorage localStorage;
    private PluginCall startServiceCall = null;
    private JSONArray log;


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
        config.put("permisosStatus",this.settings.permisos);
        System.out.println("status config");
        ret.put("settings", config);
        this.status = call;
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
                // requestPermissionLauncher.launch(permission);
                tryrequest++;
            }
        });
        builder.create().show();
    }

    public void scheduleServiceMonitor(Context context) {
        PeriodicWorkRequest workRequest =
                new PeriodicWorkRequest.Builder(LocationServiceMonitorWorker.class, 15,TimeUnit.MINUTES)
                        .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "location_monitor_worker",
                ExistingPeriodicWorkPolicy.KEEP, // no lo reinicia si ya está encolado
                workRequest
        );
    }


    private void actulizarOAnadirPermisos(int estado, String codigo) {
        String nombrePermiso = "";
        switch (codigo) {
            case Manifest.permission.ACCESS_FINE_LOCATION:
                nombrePermiso = "Ubicación precisa";
                break;
            case Manifest.permission.ACCESS_BACKGROUND_LOCATION:
                nombrePermiso = "Ubicación en segundo plano";
                break;
            case Manifest.permission.POST_NOTIFICATIONS:
                nombrePermiso = "Notificaciones";
                break;
            case "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS":
                nombrePermiso = "Optimización de batería";
                break;
            default:
                nombrePermiso = "permiso";
        }

        for (Permiso p : this.settings.permisos) {
            if (p.codigo.equals(codigo)) {
                p.estado = estado;
                p.nombre = nombrePermiso;
                return;
            }
        }
        this.settings.permisos.add(new Permiso(nombrePermiso, estado, codigo));
    }

    private void verificarPermisos() {
        permisosARequerir.clear(); // Limpiar la lista anterior

        // Verificar optimización de batería
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getContext().getPackageName())) {
                permisosARequerir.add("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
                this.actulizarOAnadirPermisos(
                    1, // Pendiente
                    "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
                );
            } else {
                this.actulizarOAnadirPermisos(
                    0, // Concedido
                    "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
                );
            }
        }

        // Verificar si el permiso de ubicación fina ha sido concedido
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permisosARequerir.add(Manifest.permission.ACCESS_FINE_LOCATION);
            this.actulizarOAnadirPermisos(
                1, // Pendiente
                Manifest.permission.ACCESS_FINE_LOCATION
            );
        } else {
            this.actulizarOAnadirPermisos(
                0, // Concedido
                Manifest.permission.ACCESS_FINE_LOCATION
            );
            // Solo agregar el permiso de ubicación en segundo plano si el de ubicación fina ya ha sido concedido
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permisosARequerir.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                this.actulizarOAnadirPermisos(
                    1, // Pendiente
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                );
            } else {
                this.actulizarOAnadirPermisos(
                    0, // Concedido
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                );
            }
        }

        // Verificar si el permiso de notificaciones ha sido concedido (para Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permisosARequerir.add(Manifest.permission.POST_NOTIFICATIONS);
            this.actulizarOAnadirPermisos(
                1, // Pendiente
                Manifest.permission.POST_NOTIFICATIONS
            );
        } else {
            this.actulizarOAnadirPermisos(
                0, // Concedido
                Manifest.permission.POST_NOTIFICATIONS
            );
        }
        try {
            this.localStorage.setObject("settingData", this.settings);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        if (!permisosARequerir.isEmpty()) {
            solicitarSiguientePermiso();
        } else {
            System.out.println("Todos los permisos requeridos ya han sido concedidos.");
            iniciarServicio(); // Para Android < 6
        }
    }

    private void solicitarIgnorarOptimizacionesDeBateria() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getContext().getPackageName()));
            getContext().startActivity(intent);
            Log.d("Permisos", "Solicitud de exclusión de batería lanzada");
        } catch (Exception e) {
            Log.e("Permisos", "Error al solicitar optimización de batería", e);
            iniciarServicio(); // Continuar si falla
        }
    }

    private void solicitarSiguientePermiso() {
        if (!permisosARequerir.isEmpty()) {
            // Solicitar el siguiente permiso en la lista
            currentRequestedPermission = permisosARequerir.remove(0);
            
            if (currentRequestedPermission.equals("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")) {
                // Manejar el permiso de optimización de batería
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                    batteryOptimizationLauncher.launch(intent);
                } catch (Exception e) {
                    this.actulizarOAnadirPermisos(2, currentRequestedPermission);
                }
            } else {
                // Manejar otros permisos normalmente
                requestPermissionLauncher.launch(currentRequestedPermission);
            }
        } else {
            this.verificarPermisos();
        }
    }
    void resolverperisoimei(Boolean isGranted)  {
        if (isGranted) {
            // Permission is granted. Continue the action or workflow in your app.
            new Thread(() -> {
                String imeiValue = getDeviceIMEI(getContext());
                getActivity().runOnUiThread(() -> {
                    this.settings.imei = imeiValue;
                    try {
                        localStorage.setObject("settingData", settings);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }).start();
        } else {
            getActivity().finish();
            // Explain to the user that the feature is unavailable because the
            // feature requires a permission that the user has denied.
        }
    }

    public String getDeviceIMEI(Context context) {
        String deviceUniqueIdentifier = null;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);
                deviceUniqueIdentifier = adInfo.getId();
            } catch (GooglePlayServicesNotAvailableException | GooglePlayServicesRepairableException | IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (deviceUniqueIdentifier == null || deviceUniqueIdentifier.isEmpty()) {
                deviceUniqueIdentifier = Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ANDROID_ID);
            }

        } else {
            int readIMEI= -1;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                readIMEI= context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE);
            }
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
        // Eliminado el uso de logcat que causa problemas de SELinux
        // Usar solo la bitácora interna del sistema
        JSObject data = new JSObject();
        try {
            JSONArray bitacora = bitacoraManager.obtenerBitacora();
            data.put("bitacora",  bitacora);
            data.put("console", new JSONArray()); // Array vacío en lugar de logcat
            call.resolve(data);
        } catch (Exception e ){
            data.put("bitacora",  new JSONArray());
            data.put("console", new JSONArray());
            call.resolve(data);
        }
    }

    @PluginMethod(returnType=PluginMethod.RETURN_CALLBACK)
    public void clearTramas(PluginCall call) throws JSONException {
        bitacoraManager.limpiarBitacora();
    }

    @PluginMethod
    public void panic(PluginCall call) throws IOException {
        this.settings.panico = true;
        this.localStorage.setObject("settingData" , this.settings );
    }

    @PluginMethod
    public void setMotivo(PluginCall call) throws IOException {
        Motivo motivo = new Motivo(call.getInt("code"),call.getString("message"), false);
        this.localStorage.setObject("motivo", motivo);
    }

    @PluginMethod
    public void startService(PluginCall call) throws InterruptedException, IOException {
        Log.d("cc","intentando iniciar el servicio" );
        this.startServiceCall = call;
        verificarPermisos( );
    }


    @RequiresApi(api = Build.VERSION_CODES.O)

    @PluginMethod
    public void stopService(PluginCall call) throws InterruptedException {
        if (checkServiceRunning(BackgroundLocationService.class)){
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
                    System.out.println("intentando parar el servicio");
                    ServiceSettings settingData = (ServiceSettings) localStorage.getObject("settingData");
                    System.out.println(settingData.toString());
                    System.out.println(settingData.stopService);
                    if (settingData != null && settingData.stopService == true) {
                        settings = settingData;
                        JSObject ret = new JSObject();
                        Intent ser = new Intent(getContext(), BackgroundLocationService.class);
                        ser.setAction("ACTION.STOPFOREGROUND_ACTION");
                        getContext().startService(ser);
                        boolean b = getContext().stopService(new Intent(getActivity(), BackgroundLocationService.class));
                        ret.put("value", "implementation.echo(value)");
                        call.resolve(ret);
                        myTimer.cancel(); // Cancela el timer inmediatamente después de resolver
                    }
                }
            }, 10);  // Ejecútalo una vez, sin repetir
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
            service.onActivityStopped();
        }
        // stoppedWithfoutPermissions = !hasRequiredPermissions();
        super.handleOnPause();
    }

    private void setImei(){
        // Mover la obtención del IMEI a un hilo secundario

        if(this.settings.imei != "") { try {
            localStorage.setObject("settingData", settings);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        new Thread(() -> {
            try {
                String imeiValue = getDeviceIMEI(getContext());
                // Actualizar el valor en el hilo principal
                getActivity().runOnUiThread(() -> {
                    this.settings.imei = imeiValue;
                    try {
                        localStorage.setObject("settingData", settings);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void load() {
        super.load();
        System.out.println("reload");
        // Eliminado el uso de logcat que causa problemas de SELinux

        this.localStorage = new LocalStorage(this.getContext());
        this.bitacoraManager  = new BitacoraManager(this.getContext());
        //localStorage.removeItem("tramas");
        permisosAceptados = new ArrayList<>();
        requestPermissionLauncher = getActivity().registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    System.out.println("permiso aceptado ? " + isGranted);
                    // Manejar la respuesta del permiso solicitado
                    if (isGranted) {
                        // Permiso concedido
                        System.out.println("Permiso concedido: " + currentRequestedPermission);
                        permisosAceptados.add(currentRequestedPermission);
                        this.actulizarOAnadirPermisos(0, currentRequestedPermission);
                        // Continuar solicitando el siguiente permiso, si queda alguno
                        solicitarSiguientePermiso();
                    } else {
                        this.actulizarOAnadirPermisos(2, currentRequestedPermission);
                        // Permiso denegado
                        if(startServiceCall != null) {
                            JSObject ret = new JSObject();
                            ret.put("started", false);
                            ret.put("permisos", this.settings.permisos);
                            startServiceCall.reject("Permiso no concedido",ret);
                        }
                    }

                });
                batteryOptimizationLauncher = getActivity().registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
                        boolean isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
                        
                        if (isIgnoringBatteryOptimizations) {
                            this.actulizarOAnadirPermisos(0, "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
                            solicitarSiguientePermiso();
                        } else {
                            this.actulizarOAnadirPermisos(2, "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
                            // Permiso denegado
                            if(startServiceCall != null) {
                                JSObject ret = new JSObject();
                                ret.put("started", false);
                                ret.put("permisos", this.settings.permisos);
                                startServiceCall.reject("Permiso no concedido",ret);
                            }
                        }
                    }
                );

        ServiceSettings settingData = (ServiceSettings) this.localStorage.getObject("settingData");
        if( settingData != null) {
            this.settings = settingData;
        } else {
            this.settings = new ServiceSettings( );
        }
        setImei();
        verificarPermisos( );

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


    @SuppressLint("SuspiciousIndentation")
    private void onActivityResult(Map<String, Boolean> result) {
        System.out.println("permisos solicitados ");
        System.out.println(result);
        // Verifica si ambos permisos fueron concedidos
        Boolean locationPermissionGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
        Boolean backgroundLocationPermissionGranted = result.get(Manifest.permission.ACCESS_BACKGROUND_LOCATION);

        if (locationPermissionGranted != null && locationPermissionGranted) {
            // Si está en Android Q o superior, verifica el permiso de ubicación en segundo plano
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (backgroundLocationPermissionGranted != null && backgroundLocationPermissionGranted) {
                    permisos = true;
                    iniciarServicio();
                } else {
                    /*
                    showExplanation("SOLICITUD DE PERMISO",
                            "Esta app requiere permisos de ubicación todo el tiempo para poder funcionar. Solo se tomará la ubicación cuando esté activo el servicio identificado por la notificación fijada.",
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION);*/
                }
            } else {
                permisos = true;
                iniciarServicio();
            }
        } else {
            permisos = false;
            // Notifica que el permiso no fue concedido
            JSObject ret = new JSObject();
            ret.put("started", false);
            if(startServiceCall != null)
            startServiceCall.reject("Permiso no concedido");
        }
    }

    private void iniciarServicio() {
        permisos = true;
        // Configuración común para iniciar el servicio
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        AutoStartBackgroundLocation e = new AutoStartBackgroundLocation();
        getContext().registerReceiver(e, intentFilter);
        if (this.startServiceCall != null) {
            this.settings.notificationTitle = startServiceCall.getString("notificationTitle");
            this.settings.notificationContent = startServiceCall.getString("notificationContent");
            this.settings.distanceFilter = startServiceCall.getInt("distanceFilter");
            this.settings.urlRequests = startServiceCall.getString("urlRequests");
            this.settings.minS = startServiceCall.getInt("minS");
            this.settings.inBG = startServiceCall.getBoolean("inBG");
            this.settings.userID = startServiceCall.getString("userId");
            this.settings.authorization = startServiceCall.getString("authorization");
        }

        this.settings.stopService = false;
        this.settings.appStarted = true;

        try {
            this.localStorage.setObject("settingData", this.settings);
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
        config.put("notificationContent", this.settings.notificationContent);
        config.put("userId", this.settings.userID);
        config.put("inBG", this.settings.inBG);
        config.put("authorization", this.settings.authorization);
        config.put("distanceFilter", this.settings.distanceFilter);
        config.put("urlRequests", this.settings.urlRequests);
        config.put("minS", this.settings.minS);
        config.put("imei", this.settings.imei);

        ret.put("settings", config);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent ser = new Intent(getContext(), BackgroundLocationService.class);
            ser.setAction("ACTION.STARTFOREGROUND_ACTION");
            this.getContext().startForegroundService(ser);
        }

        if (status != null)  status.resolve(ret);

        System.out.println("resolviendo ");
        if (startServiceCall != null) {
            startServiceCall.resolve(ret);
        }

        this.scheduleServiceMonitor(getContext());
    }


    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");
        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

}
