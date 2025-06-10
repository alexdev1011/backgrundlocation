package com.alexdev1011.plugins.backgroundlocation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import android.app.ActivityManager;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.volley.NoConnectionError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

public class TramaStorage {
    private LocalStorage localStorage;
    private JSONArray usersTramas;
    private Context context;
    public  NetworkUtil networkStatus;
    private final AtomicBoolean publicando = new AtomicBoolean(false);
    public ServiceSettings settings = null;
    private long ultimaConexionExitosa = 0;
    private BitacoraManager bitacoraManager ;

    public static class NetworkUtil {
        public static final int TYPE_WIFI = 1;
        public static final int TYPE_MOBILE = 2;
        public static final int TYPE_NOT_CONNECTED = 0;
        public static final int NETWORK_STATUS_NOT_CONNECTED = 0;
        public static final int NETWORK_STATUS_WIFI = 1;
        public static final int NETWORK_STATUS_MOBILE = 2;


        public static int getConnectivityStatus(Context context) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (null != activeNetwork) {
                if(activeNetwork.getType() == ConnectivityManager.TYPE_WIFI)
                    return TYPE_WIFI;

                if(activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE)
                    return TYPE_MOBILE;
            }
            return TYPE_NOT_CONNECTED;
        }

        public static int getConnectivityStatusString(Context context) {
            int conn = NetworkUtil.getConnectivityStatus(context);
            int status = 0;
            if (conn == NetworkUtil.TYPE_WIFI) {
                status = NETWORK_STATUS_WIFI;
            } else if (conn == NetworkUtil.TYPE_MOBILE) {
                status = NETWORK_STATUS_MOBILE;
            } else if (conn == NetworkUtil.TYPE_NOT_CONNECTED) {
                status = NETWORK_STATUS_NOT_CONNECTED;
            }
            return status;
        }
    }

    TramaStorage(Context context) throws JSONException {
        this.networkStatus = new NetworkUtil();
        this.context = context;
        localStorage = new LocalStorage(context);
        bitacoraManager = new BitacoraManager(context);
        publicando.set(false);
        ServiceSettings settingData = (ServiceSettings) this.localStorage.getObject("settingData");
        if( settingData != null) {
            this.settings = settingData;
        }
        System.out.println("Iniciando tramas storage");
        String tramasCrud = localStorage.getItem("tramas");
        if(tramasCrud != null ){
            usersTramas = new JSONArray(tramasCrud);
            if(usersTramas != null ){
                if(usersTramas.length() > 0 ){
                    System.out.println("90 cantidad de lotes => " + usersTramas.getJSONObject(0).getJSONArray("tramas").length());
                }
            }
        } else{
            usersTramas = new JSONArray();
        }
    }

    private JSONArray concatArray(JSONArray... arrs)
            throws JSONException {
        JSONArray result = new JSONArray();
        for (JSONArray arr : arrs) {
            for (int i = 0; i < arr.length(); i++) {
                result.put(arr.get(i));
            }
        }
        return result;
    }


    private boolean guardarTramas(){
        try {
            System.out.println("guardando estado actual de tramas.");
            localStorage.setItem("tramas",usersTramas.toString());
            return true;
        } catch ( Exception e ){
            System.out.println(e);
            return false;
        }
    }

    public void retryPendingData() {
        if (usersTramas == null || usersTramas.length() == 0) return;

        for (int i = 0; i < usersTramas.length(); i++) {
            try {
                JSONObject userData = usersTramas.getJSONObject(i);
                JSONArray tramas = userData.getJSONArray("tramas");

                if (tramas.length() > 0) {
                    // Intentar enviar los datos pendientes
                    publicar(i);
                }
            } catch (JSONException e) {
                bitacoraManager.guardarEvento("Error al procesar datos pendientes", 403, e.getMessage());
            }
        }
    }

    public void publicar( Integer userIndex ){
        try {
            System.out.println("publicando tramas => usuario :" + userIndex  );
            JSONObject userTrama = usersTramas.getJSONObject(userIndex);
            class Callback implements Http.CallBack {
                @Override
                public void onSuccess(JSONObject response) {
                    Log.d("HTTPRE", String.valueOf(response));
                    // Toast.makeText( context, "tramas enviadas" ,Toast.LENGTH_LONG).show();
                    ultimaConexionExitosa = System.currentTimeMillis();
                    try {
                        JSONArray tramas = userTrama.getJSONArray("tramas");
                        bitacoraManager.guardarEvento("tramas enviadas y validadas " +  tramas.getJSONArray(0).length(),200,response.get("message").toString() );
                        tramas.remove(0);
                        userTrama.put("tramas", tramas);
                        usersTramas.put(userIndex,userTrama);
                        if(tramas.length() > 0 ) {
                            try {
                                publicar(userIndex);
                            } catch (Exception e) {
                                publicando.set(false);
                                bitacoraManager.guardarEvento("Error en llamada recursiva a publicar()", 997, e.getMessage());
                                guardarTramas();
                            }
                        }
                        else{
                            publicando.set(false);
                            usersTramas.remove(userIndex);
                            guardarTramas();
                        }

                    } catch ( Exception e ){
                        publicando.set(false);
                        bitacoraManager.guardarEvento("trama rechazada",600, e.getMessage() );
                        guardarTramas();
                    }
                }

                @Override
                public void onError(VolleyError error, JSONArray locationList  ) {
                    publicando.set(false);
                    showToastIfAppVisible("Error al enviar, On Error");
                    String mensaje = "";
                    String causa = "";
                    String cuerpoRespuesta = "";
                    int statusCode = -1;


                    if (error instanceof NoConnectionError || error instanceof TimeoutError) {
                        mensaje = "Error de conexion o timeOut";
                        causa = "error al conectar o la peticion tardo demaciado";
                    }

                    try {
                        if (error.networkResponse != null) {
                            statusCode = error.networkResponse.statusCode;
                            cuerpoRespuesta = new String(error.networkResponse.data, "UTF-8");
                        }
                        if (error.getCause() != null) {
                            causa = error.getCause().toString();
                        }
                        mensaje = error.getLocalizedMessage();
                    } catch (Exception e) {
                        causa = "Error al procesar detalles del error: " + e.getMessage();
                    }

                    JSONObject detalleError = new JSONObject();
                    try {
                        detalleError.put("status", statusCode);
                        detalleError.put("mensaje", mensaje);
                        detalleError.put("causa", causa);
                        detalleError.put("cuerpo", cuerpoRespuesta);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    if (error instanceof TimeoutError) {
                        bitacoraManager.guardarEvento("Timeout en envío", 404, "La solicitud excedió el tiempo de espera");
                    } else if (error instanceof NoConnectionError) {
                        bitacoraManager.guardarEvento("Error de conexión", 405, "No hay conexión a internet");
                    } else {
                        bitacoraManager.guardarEvento(
                                "Error al enviar.",
                                statusCode > 0 ? statusCode : "desconocido",
                                detalleError.toString()
                        );
                    }



                    guardarTramas();
                    // Toast.makeText( context, "tramas rechazadas" ,Toast.LENGTH_LONG).show();
                }
            }
            Callback callback = new Callback();
            JSONArray tramas = userTrama.getJSONArray("tramas");
            JSONArray lote = tramas.getJSONArray(0);
            System.out.println( "tramas : " +   lote.length() );
            JSONObject data = new JSONObject();
            data.put("reports" , lote);
            String auth = "";
            String userID = "";
            if (userTrama.has("auth"))
                auth = userTrama.getString("auth");
            if(userTrama.getString("userID").compareTo("") == 0 ) {
                userID = "sinDefinir";
            }
            else {
                userID = userTrama.getString("userID");
            }
            data.put("userId" , userID );
            ServiceSettings settingData = (ServiceSettings) this.localStorage.getObject("settingData");
            if( settingData != null) {
                this.settings = settingData;
            }
            String url = userTrama.getString("urlRequest") + "/tracker/" + this.settings.imei;
            if(this.settings != null ){
                url = userTrama.getString("urlRequest") + "/tracker/" + this.settings.imei;
            } else {
                url = userTrama.getString("urlRequest") + "/tracker/" + "sinImei";
            }
            System.out.println("url final" +  url);
            bitacoraManager.guardarEvento("urlRequest" ,200, url );
            if (networkStatus.getConnectivityStatus(context) > 0) {
                Http httpRequests = Http.getInstance(context);
                // Intentar enviar
                bitacoraManager.guardarEvento(
                        "Intento de envío de trama",
                        100,  // puedes usar otro código si prefieres
                        "Enviando lote con " + lote.length() + " tramas para userID: " + userID
                );
                System.out.println("data => ");
                System.out.println(data.toString());
                httpRequests.post(url, callback, data, auth);
            } else {
                publicando.set(false);
                guardarTramas();
            }


        } catch ( JSONException e) {
            showToastIfAppVisible("Error al enviar tramas");
            publicando.set(false);
            bitacoraManager.guardarEvento("Error general en publicar()", 999, e.getMessage());
            System.out.println(e);
            guardarTramas();
        }
    }

    private JSONArray crearLote(JSONObject trama) throws JSONException {
        JSONArray lote = new JSONArray();
        lote.put(trama);
        JSONArray tramasNuevas = new JSONArray();
        tramasNuevas.put(lote);
        return tramasNuevas;
    }


    private Boolean sePuedePublicar(){


        Boolean publicandoTramas = publicando.compareAndSet(false, true);
        if(!publicandoTramas){
            showToastIfAppVisible("no se pueden publicar tramas 01");
            if (System.currentTimeMillis() - ultimaConexionExitosa > 10 * 60 * 1000) {
                bitacoraManager.guardarEvento("Reset automático de 'publicando'", 101, "Reinicio tras timeout");
                publicando.set(false);
                Http.resetHttpInstance(context);
                publicandoTramas = true;
            }
        }
        return publicandoTramas;
    }

    void inmprimirDetalle(){
        try {
            System.out.println(usersTramas.toString(2)); // Imprime con indentación de 2 espacios
            System.out.println("cantidad de usuarios => " + usersTramas.length());
            for(int i = 0; i < usersTramas.length(); i++){
                JSONObject user = usersTramas.getJSONObject(i);
                System.out.println("usuario => " + user.getString("userID"));
                System.out.println("urlRequest => " + user.getString("urlRequest"));
                System.out.println("auth => " + user.getString("auth"));
                System.out.println("lotes => " + user.getJSONArray("tramas").length());
                JSONArray lotes = user.getJSONArray("tramas");
                for(int j = 0; j < lotes.length(); j++){
                    JSONArray lote = lotes.getJSONArray(j);
                    System.out.println("tramas => " + lote.length());
                }
            }
        } catch (JSONException e) {
            System.out.println(usersTramas.toString()); // Imprime sin formato si hay error
        }
    }


    @SuppressLint("SuspiciousIndentation")
    public void agregarTrama(JSONObject contenido ){
        try {
            guardarTramas();
            System.out.println(" publicando ?" + publicando.get());
            System.out.println("259 contenido =>");
            System.out.println(contenido);
            Boolean nuevo = true;
            System.out.println(usersTramas);
            System.out.println(" 263 usuarios => " + usersTramas.length());
            System.out.println(networkStatus.getConnectivityStatus(context));
            Boolean networkstatus = (networkStatus.getConnectivityStatus(context) > 0);

            System.out.println("264 estado de la conexion " + networkstatus.toString());
            if (usersTramas.length() == 0) {
                JSONObject content = new JSONObject();
                try {
                    JSONArray  tramasNuevas = this.crearLote(contenido.getJSONObject("trama"));
                    content.put("userID", contenido.getString("userID"));
                    content.put("urlRequest", contenido.getString("urlRequest"));
                    content.put("auth", contenido.getString("auth"));
                    content.put("intents", 0);
                    content.put("tramas", tramasNuevas);
                    usersTramas.put(content);
                    // System.out.println("cantidad de lotes => " + usersTramas.getJSONObject(0).getJSONArray("tramas").length());
                    // System.out.println("cantidad de tramas en el primer lote => " + usersTramas.getJSONObject(0).getJSONArray("tramas").getJSONArray(0).length());
                    if (networkStatus.getConnectivityStatus(context) > 0 && sePuedePublicar()) {
                        System.out.println("277 decide si enviar o no la trama.");
                        publicar(0);
                    } else {
                        bitacoraManager.guardarEvento( "sin conexión",0,"sin conexión");
                        this.guardarTramas();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                JSONObject nuevoObjeto = null;
                int indiceUsuario = -1;
                boolean esNuevo = true;

                try {
                    String contenidoUserID = contenido.getString("userID");

                    for (int i = 0; i < usersTramas.length(); i++) {
                        JSONObject usuario = usersTramas.getJSONObject(i);
                        String userID = usuario.getString("userID");

                        if (userID.equals(contenidoUserID)) {
                            esNuevo = false;
                            indiceUsuario = i;

                            // Actualiza info básica
                            usuario.put("urlRequest", contenido.getString("urlRequest"));
                            usuario.put("auth", contenido.getString("auth"));

                            JSONArray tramas = usuario.getJSONArray("tramas");

                            if (tramas.length() == 0) {
                                JSONArray nuevoLote = new JSONArray();
                                nuevoLote.put(contenido.getJSONObject("trama"));
                                tramas.put(nuevoLote);
                            } else {
                                JSONArray loteActual = tramas.getJSONArray(tramas.length() - 1);
                                if (loteActual.length() <= 100 && !publicando.get()) {
                                    loteActual.put(contenido.getJSONObject("trama"));
                                    tramas.put(tramas.length() - 1, loteActual);
                                } else {
                                    JSONArray nuevoLote = new JSONArray();
                                    nuevoLote.put(contenido.getJSONObject("trama"));
                                    tramas.put(nuevoLote);
                                }
                            }

                            usuario.put("tramas", tramas);
                            nuevoObjeto = usuario;
                            break; // ya lo encontramos, salimos del loop
                        }
                    }

                    inmprimirDetalle();

                    System.out.println("320 Es un usuario nuevo ? " + esNuevo);

                    // Ahora hacemos las modificaciones al JSONArray
                    if (!esNuevo && nuevoObjeto != null) {
                        usersTramas.put(indiceUsuario, nuevoObjeto);
                        if (networkStatus.getConnectivityStatus(context) > 0 && sePuedePublicar()) {
                            System.out.println("Procedemos a publicar la trama");
                            publicar(indiceUsuario);
                        } else {
                            guardarTramas();
                        }
                    }

                    // Si es nuevo usuario, lo agregamos ahora
                    if (esNuevo) {
                        JSONObject nuevoUsuario = new JSONObject();
                        JSONArray tramasNuevas = this.crearLote(contenido.getJSONObject("trama"));

                        nuevoUsuario.put("userID", contenidoUserID);
                        nuevoUsuario.put("urlRequest", contenido.getString("urlRequest"));
                        nuevoUsuario.put("auth", contenido.getString("auth"));
                        nuevoUsuario.put("intents", 0);
                        nuevoUsuario.put("tramas", tramasNuevas);

                        usersTramas.put(nuevoUsuario);

                        if (networkStatus.getConnectivityStatus(context) > 0 && sePuedePublicar()) {
                            publicar(0); // puedes ajustar si quieres publicar el último o el primero
                        } else {
                            bitacoraManager.guardarEvento("sin conexion", 0, "sin conexion");
                            guardarTramas();
                        }
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }catch ( Error error ){
            bitacoraManager.guardarEvento("Error al enviar trama detalle general",12,error.getMessage());
            guardarTramas();
        }
    }

    public class AppVisibilityChecker {
        public boolean isAppInForeground(Context context) {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) return false;
            String packageName = context.getPackageName();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // En Android 10+ hay limitaciones, pero se puede usar ProcessLifecycleOwner si es una app con Application extendida
                return true; // asume true si no puedes chequear, o implementa una alternativa con lifecycle observer
            } else {
                for (ActivityManager.RunningAppProcessInfo appProcess : activityManager.getRunningAppProcesses()) {
                    if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                            && appProcess.processName.equals(packageName)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private void showToastIfAppVisible(final String message) {
        // Asegúrate de que se corre en el hilo principal (UI)
        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
        AppVisibilityChecker app = new AppVisibilityChecker();
        mainHandler.post(() -> {
            if (app.isAppInForeground(context)) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static class LocationServiceMonitorWorker extends Worker {

        public LocationServiceMonitorWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            Context context = getApplicationContext();

            if (!isServiceRunning(context, BackgroundLocationService.class)) {
                Intent serviceIntent = new Intent(context, BackgroundLocationService.class);
                serviceIntent.setAction("ACTION.STARTFOREGROUND_ACTION");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }

            return Result.success();
        }

        private boolean isServiceRunning(Context context, Class<?> serviceClass) {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
            return false;
        }
    }
}

/*
package com.alexdev1011.plugins.backgroundlocation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import android.app.ActivityManager;

import androidx.activity.result.contract.ActivityResultContracts;

import com.android.volley.NoConnectionError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.getcapacitor.JSObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

public class TramaStorage {
    private LocalStorage localStorage;
    private JSONArray usersTramas;
    private Context context;
    public  NetworkUtil networkStatus;
    private final AtomicBoolean publicando = new AtomicBoolean(false);
    public ServiceSettings settings = null;
    private long ultimaConexionExitosa = 0;
    private BitacoraManager bitacoraManager ;

    public static class NetworkUtil {
        public static final int TYPE_WIFI = 1;
        public static final int TYPE_MOBILE = 2;
        public static final int TYPE_NOT_CONNECTED = 0;
        public static final int NETWORK_STATUS_NOT_CONNECTED = 0;
        public static final int NETWORK_STATUS_WIFI = 1;
        public static final int NETWORK_STATUS_MOBILE = 2;


        public static int getConnectivityStatus(Context context) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (null != activeNetwork) {
                if(activeNetwork.getType() == ConnectivityManager.TYPE_WIFI)
                    return TYPE_WIFI;

                if(activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE)
                    return TYPE_MOBILE;
            }
            return TYPE_NOT_CONNECTED;
        }

        public static int getConnectivityStatusString(Context context) {
            int conn = NetworkUtil.getConnectivityStatus(context);
            int status = 0;
            if (conn == NetworkUtil.TYPE_WIFI) {
                status = NETWORK_STATUS_WIFI;
            } else if (conn == NetworkUtil.TYPE_MOBILE) {
                status = NETWORK_STATUS_MOBILE;
            } else if (conn == NetworkUtil.TYPE_NOT_CONNECTED) {
                status = NETWORK_STATUS_NOT_CONNECTED;
            }
            return status;
        }
    }

    TramaStorage(Context context) throws JSONException {
        this.networkStatus = new NetworkUtil();
        this.context = context;
        localStorage = new LocalStorage(context);
        bitacoraManager = new BitacoraManager(context);
        publicando.set(false);
        ServiceSettings settingData = (ServiceSettings) this.localStorage.getObject("settingData");
        if( settingData != null) {
            this.settings = settingData;
        }
        System.out.println("Iniciando tramas storage");
        String tramasCrud = localStorage.getItem("tramas");
        if(tramasCrud != null ){
            usersTramas = new JSONArray(tramasCrud);
            if(usersTramas != null ){
                if(usersTramas.length() > 0 ){
                System.out.println("90 cantidad de lotes => " + usersTramas.getJSONObject(0).getJSONArray("tramas").length());
                }
            }
        } else{
            usersTramas = new JSONArray();
        }
    }
    private JSONArray concatArray(JSONArray... arrs)
            throws JSONException {
        JSONArray result = new JSONArray();
        for (JSONArray arr : arrs) {
            for (int i = 0; i < arr.length(); i++) {
                result.put(arr.get(i));
            }
        }
        return result;
    }


    private boolean guardarTramas(){
         try {
             System.out.println("guardando tramas");
             localStorage.setItem("tramas",usersTramas.toString());
             return true;
         } catch ( Exception e ){
             System.out.println(e);
             return false;
         }
    }

    public void publicar( Integer userIndex ){
        try {
            System.out.println("publicando tramas => usuario :" + userIndex  );
            JSONObject userTrama = usersTramas.getJSONObject(userIndex);
            class Callback implements Http.CallBack {
                @Override
                public void onSuccess(JSONObject response) {
                    Log.d("HTTPRE", String.valueOf(response));
                   // Toast.makeText( context, "tramas enviadas" ,Toast.LENGTH_LONG).show();
                    ultimaConexionExitosa = System.currentTimeMillis();
                   try {
                       JSONArray tramas = userTrama.getJSONArray("tramas");
                       bitacoraManager.guardarEvento("tramas enviadas y validadas " +  tramas.getJSONArray(0).length(),200,response.get("message").toString() );
                       tramas.remove(0);
                       userTrama.put("tramas", tramas);
                       usersTramas.put(userIndex,userTrama);
                       if(tramas.length() > 0 ) {
                           try {
                               publicar(userIndex);
                           } catch (Exception e) {
                               publicando.set(false);
                               bitacoraManager.guardarEvento("Error en llamada recursiva a publicar()", 997, e.getMessage());
                               guardarTramas();
                           }
                       }
                       else{
                           publicando.set(false);
                           usersTramas.remove(userIndex);
                           guardarTramas();
                       }

                   } catch ( Exception e ){
                       publicando.set(false);
                       bitacoraManager.guardarEvento("trama rechazada",600, e.getMessage() );
                       guardarTramas();
                   }
                }

                @Override
                public void onError(VolleyError error, JSONArray locationList  ) {
                    publicando.set(false);
                    showToastIfAppVisible("Error al enviar, On Error");
                    String mensaje = "";
                    String causa = "";
                    String cuerpoRespuesta = "";
                    int statusCode = -1;

                    if (error instanceof NoConnectionError || error instanceof TimeoutError) {
                        mensaje = "Error de conexion o timeOut";
                        causa = "error al conectar o la peticion tardo demaciado";
                    }

                    try {
                        if (error.networkResponse != null) {
                            statusCode = error.networkResponse.statusCode;
                            cuerpoRespuesta = new String(error.networkResponse.data, "UTF-8");
                        }
                        if (error.getCause() != null) {
                            causa = error.getCause().toString();
                        }
                        mensaje = error.getLocalizedMessage();
                    } catch (Exception e) {
                        causa = "Error al procesar detalles del error: " + e.getMessage();
                    }

                    JSONObject detalleError = new JSONObject();
                    try {
                        detalleError.put("status", statusCode);
                        detalleError.put("mensaje", mensaje);
                        detalleError.put("causa", causa);
                        detalleError.put("cuerpo", cuerpoRespuesta);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    bitacoraManager.guardarEvento(
                            "Error al enviar.",
                            statusCode > 0 ? statusCode : "desconocido",
                            detalleError.toString()
                    );

                    guardarTramas();
                   // Toast.makeText( context, "tramas rechazadas" ,Toast.LENGTH_LONG).show();
                }
            }
            Callback callback = new Callback();
            JSONArray tramas = userTrama.getJSONArray("tramas");
            System.out.println( "tramas : " +   tramas.length() );
            JSONArray lote = tramas.getJSONArray(0);
            JSONObject data = new JSONObject();
            data.put("reports" , lote);
            String auth = "";
            String userID = "";
            if (userTrama.has("auth"))
             auth = userTrama.getString("auth");
            if(userTrama.getString("userID").compareTo("") == 0 ) {
                userID = "sinDefinir";
            }
            else {
                userID = userTrama.getString("userID");
            }
            data.put("userId" , userID );
            ServiceSettings settingData = (ServiceSettings) this.localStorage.getObject("settingData");
            if( settingData != null) {
                this.settings = settingData;
            }
            String url = userTrama.getString("urlRequest") + "/tracker/" + this.settings.imei;
            if(this.settings != null ){
                url = userTrama.getString("urlRequest") + "/tracker/" + this.settings.imei;
            } else {
                url = userTrama.getString("urlRequest") + "/tracker/" + "sinImei";
            }
            System.out.println("url final" +  url);
            bitacoraManager.guardarEvento("urlRequest" ,200, url );
            if (networkStatus.getConnectivityStatus(context) > 0) {
                // Reiniciar la instancia HTTP si ha pasado mucho tiempo
                if (System.currentTimeMillis() - ultimaConexionExitosa > 10 * 60 * 100) { // 30 minutos
                    Http.resetHttpInstance(context);
                }
                Http httpRequests = Http.getInstance(context);
                // Intentar enviar
                bitacoraManager.guardarEvento(
                        "Intento de envío de trama",
                        100,  // puedes usar otro código si prefieres
                        "Enviando lote con " + lote.length() + " tramas para userID: " + userID
                );
                httpRequests.post(url, callback, data, auth);
            } else {
                publicando.set(false);
                guardarTramas();
            }


        } catch ( JSONException e) {
            showToastIfAppVisible("Error al enviar tramas");
            publicando.set(false);
            bitacoraManager.guardarEvento("Error general en publicar()", 999, e.getMessage());
            System.out.println(e);
            guardarTramas();
        }
    }

    private JSONArray crearLote(JSONObject trama) throws JSONException {
        JSONArray lote = new JSONArray();
        lote.put(trama);
        JSONArray tramasNuevas = new JSONArray();
        tramasNuevas.put(lote);
        return tramasNuevas;
    }


    private Boolean sePuedePublicar(){


        Boolean publicandoTramas = publicando.compareAndSet(false, true);
        if (System.currentTimeMillis() - ultimaConexionExitosa > 10 * 60 * 1000) {
            bitacoraManager.guardarEvento("Reset automático de 'publicando'", 101, "Reinicio tras timeout");
            publicando.set(false);
            Http.resetHttpInstance(context);
            publicandoTramas = true;
        }
        if(!publicandoTramas){
            showToastIfAppVisible("no se pueden publicar tramas 01");
        }
        return publicandoTramas;
    }

    @SuppressLint("SuspiciousIndentation")
    public void agregarTrama(JSONObject contenido ){
        try {
            System.out.println(" publicando ?" + publicando.get());
            System.out.println("259 contenido =>");
            System.out.println(contenido);
            Boolean nuevo = true;
            System.out.println(usersTramas);
            System.out.println(" 263 usuarios => " + usersTramas.length());
            Boolean networkstatus = (networkStatus.getConnectivityStatus(context) > 0);

            System.out.println("264 estado de la conexion " + networkstatus.toString());
            if (usersTramas.length() == 0) {
                JSONObject content = new JSONObject();
                try {
                    JSONArray  tramasNuevas = this.crearLote(contenido.getJSONObject("trama"));
                    content.put("userID", contenido.getString("userID"));
                    content.put("urlRequest", contenido.getString("urlRequest"));
                    content.put("auth", contenido.getString("auth"));
                    content.put("intents", 0);
                    content.put("tramas", tramasNuevas);
                    usersTramas.put(content);
                   // System.out.println("cantidad de lotes => " + usersTramas.getJSONObject(0).getJSONArray("tramas").length());
                    // System.out.println("cantidad de tramas en el primer lote => " + usersTramas.getJSONObject(0).getJSONArray("tramas").getJSONArray(0).length());
                    if (networkStatus.getConnectivityStatus(context) > 0 && sePuedePublicar()) {
                        System.out.println("277 decide si enviar o no la trama.");
                        publicar(0);
                    } else {
                        bitacoraManager.guardarEvento( "sin conexión",0,"sin conexión");
                        this.guardarTramas();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                for (int i = 0; i < usersTramas.length(); i++) {
                    try {
                        JSONObject jsonObject = usersTramas.getJSONObject(i);
                        String userID = jsonObject.getString("userID");
                        if ((userID).compareTo(contenido.getString("userID")) == 0) {
                            jsonObject.put("urlRequest", contenido.getString("urlRequest"));
                            jsonObject.put("auth", contenido.getString("auth"));
                            nuevo = false;
                            //System.out.println(userID + "  " + contenido.getString("userID"));
                            JSONArray currentTramas;
                            //System.out.println(jsonObject);
                            currentTramas = jsonObject.getJSONArray("tramas");
                            //System.out.println(currentTramas);
                            //System.out.println(currentTramas.getJSONArray(currentTramas.length() - 1).length() <= 100 && !publicando);
                            if (currentTramas.length() == 0) {
                                JSONArray nuevoLote = new JSONArray();
                                nuevoLote.put(contenido.getJSONObject("trama"));
                                currentTramas.put(nuevoLote);
                            } else if (currentTramas.getJSONArray(currentTramas.length() - 1).length() <= 100 && !publicando.get()) {
                                JSONArray loteNuevo = currentTramas.getJSONArray(currentTramas.length() - 1).put(contenido.getJSONObject("trama"));
                                currentTramas.put(currentTramas.length() - 1, loteNuevo);
                            } else {
                                JSONArray nuevoLote = new JSONArray();
                                nuevoLote.put(contenido.getJSONObject("trama"));
                                currentTramas.put(nuevoLote);
                            }
                            jsonObject.put("tramas", currentTramas);
                            usersTramas.put(i, jsonObject);
                        }
                        System.out.println("320 Es un usuario nuevo ? " + nuevo.toString());
                       // System.out.println("cantidad de lotes => " + usersTramas.getJSONObject(i).getJSONArray("tramas").length());
                       // System.out.println("cantidad de tramas en el primer lote => " + usersTramas.getJSONObject(i).getJSONArray("tramas").getJSONArray(0).length());
                        if (networkStatus.getConnectivityStatus(context) > 0 && !nuevo && sePuedePublicar()) {
                            System.out.println("Procedemos a publicar la trama");
                            System.out.println(publicando.get());
                            publicar(i);
                        } else {
                            if (i == usersTramas.length() - 1) {
                                if (nuevo == true) {
                                    System.out.println("nuevo");
                                    JSONObject content = new JSONObject();
                                    try {
                                        JSONArray  tramasNuevas = this.crearLote(contenido.getJSONObject("trama"));
                                        content.put("userID", contenido.getString("userID"));
                                        content.put("urlRequest", contenido.getString("urlRequest"));
                                        content.put("auth", contenido.getString("auth"));
                                        content.put("intents", 0);
                                        content.put("tramas", tramasNuevas);
                                        usersTramas.put(content);

                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                                if (networkStatus.getConnectivityStatus(context) > 0 && sePuedePublicar()) {
                                    publicar(0);
                                } else {
                                    bitacoraManager.guardarEvento("sin conexion",0,"sin conexion");
                                    System.out.println("sin conexión");
                                    //System.out.println("cantidad de lotes => " + usersTramas.getJSONObject(i).getJSONArray("tramas").length());
                                    //System.out.println("cantidad de tramas en el primer lote => " + usersTramas.getJSONObject(i).getJSONArray("tramas").getJSONArray(0).length());
                                    this.guardarTramas();
                                }
                            } else guardarTramas();
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }catch ( Error error ){
            bitacoraManager.guardarEvento("Error al enviar trama detalle general",12,error.getMessage());
        }
    }

    public class AppVisibilityChecker {
        public boolean isAppInForeground(Context context) {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) return false;
            String packageName = context.getPackageName();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // En Android 10+ hay limitaciones, pero se puede usar ProcessLifecycleOwner si es una app con Application extendida
                return true; // asume true si no puedes chequear, o implementa una alternativa con lifecycle observer
            } else {
                for (ActivityManager.RunningAppProcessInfo appProcess : activityManager.getRunningAppProcesses()) {
                    if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                            && appProcess.processName.equals(packageName)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private void showToastIfAppVisible(final String message) {
        // Asegúrate de que se corre en el hilo principal (UI)
        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
        AppVisibilityChecker app = new AppVisibilityChecker();
        mainHandler.post(() -> {
            if (app.isAppInForeground(context)) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
*/