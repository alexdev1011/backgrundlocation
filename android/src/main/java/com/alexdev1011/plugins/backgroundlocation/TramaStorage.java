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

import androidx.activity.result.contract.ActivityResultContracts;

import com.android.volley.VolleyError;
import com.getcapacitor.JSObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;

public class TramaStorage {
    private LocalStorage localStorage;
    private JSONArray usersTramas;
    private Http httpRequests = new Http();
    private Context context;
    public  NetworkUtil networkStatus;
    private Boolean publicando = false;
    public ServiceSettings settings = null;
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
        ServiceSettings settingData = (ServiceSettings) this.localStorage.getObject("settingData");
        if( settingData != null) {
            this.settings = settingData;
        }
        String tramasCrud = localStorage.getItem("tramas");
        System.out.println( "tramas como string => " + tramasCrud );
        if(tramasCrud != null ){
            System.out.println( "tramas como json => " + new JSONArray(tramasCrud) );
            usersTramas = new JSONArray(tramasCrud);
            if(usersTramas != null ){
                if(usersTramas.length() > 0 ){
                System.out.println("cantidad de lotes => " + usersTramas.getJSONObject(0).getJSONArray("tramas").length());
                System.out.println("cantidad de tramas en el primer lote => " + usersTramas.getJSONObject(0).getJSONArray("tramas").getJSONArray(0).length());
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

    private boolean obtenerTramas(){
        try {
            String tramas = localStorage.getItem("tramas");
            System.out.println( new JSONArray(tramas));
            return true;
        } catch ( Exception e ){
            System.out.println(e);
            return false;
        }
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
            publicando = true;
            System.out.println("publicando tramas => " + userIndex );
            JSONObject userTrama = usersTramas.getJSONObject(userIndex);
            class Callback implements Http.CallBack {
                @Override
                public void onSuccess(JSONObject response) {
                    Log.d("HTTPRE", String.valueOf(response));
                   // Toast.makeText( context, "tramas enviadas" ,Toast.LENGTH_LONG).show();
                   try {
                       JSONArray bitacora = new JSONArray();
                       try {
                           String localTramas = localStorage.getItem("bitacora");
                           bitacora =  new JSONArray(localTramas);
                       } catch (Exception e ){
                           bitacora = new JSONArray();
                       }
                       JSONArray tramas = userTrama.getJSONArray("tramas");
                       JSObject motivo = new JSObject();
                       motivo.put("code",200);
                       motivo.put("message",response.get("message"));
                       JSObject evento = new JSObject();
                       evento.put("razon","tramas enviadas y validadas " +  tramas.getJSONArray(0).length());
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


                       tramas.remove(0);
                       userTrama.put("tramas", tramas);
                       usersTramas.put(userIndex,userTrama);
                       if(tramas.length() > 0 )
                       publicar(userIndex);
                       else{
                           publicando = false;
                           usersTramas.remove(userIndex);
                           guardarTramas();
                       }

                   } catch ( Exception e ){
                       publicando = false;
                       System.out.println(e);
                       JSONArray bitacora = new JSONArray();
                       try {
                           String localTramas = localStorage.getItem("bitacora");
                           bitacora =  new JSONArray(localTramas);
                       } catch (Exception ex ){
                           bitacora = new JSONArray();
                       }
                       JSObject motivo = new JSObject();
                       motivo.put("code",600);
                       motivo.put("message", e.getMessage() );
                       JSObject evento = new JSObject();
                       evento.put("razon","trama rechazada");
                       evento.put("motivo",motivo);
                       evento.put("fecha",new Date().getTime());
                       System.out.println(evento);
                       if(bitacora.length() > 91){
                           JSONArray bit =  new JSONArray();
                           for (int i = bitacora.length() -89 ; i < bitacora.length() ; i++){
                               try {
                                   bit.put(bitacora.get(i));
                               } catch (JSONException ex) {
                                   throw new RuntimeException(ex);
                               }
                           }
                           bitacora = bit;
                       }
                       bitacora.put(evento);
                       localStorage.setItem("bitacora",bitacora.toString());
                       guardarTramas();
                   }
                }

                @Override
                public void onError(VolleyError error, JSONArray locationList  ) {
                    publicando = false;
                    System.out.println("algo fallo");
                    System.out.println(error.toString());
                    JSONArray bitacora = new JSONArray();
                    try {
                        String localTramas = localStorage.getItem("bitacora");
                        bitacora =  new JSONArray(localTramas);
                    } catch (Exception e ){
                        bitacora = new JSONArray();
                    }
                    JSObject motivo = new JSObject();
                    motivo.put("code",error.getLocalizedMessage());
                    motivo.put("message",error.toString());
                    JSObject evento = new JSObject();
                    evento.put("razon","error al enviar.");
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
                   // Toast.makeText( context, "tramas rechazadas" ,Toast.LENGTH_LONG).show();
                    System.out.println(error);
                    guardarTramas();
                } 
            }
            Callback callback = new Callback();
            JSONArray tramas = userTrama.getJSONArray("tramas");
            JSONArray lote = tramas.getJSONArray(0);
            System.out.println( tramas.getJSONArray(tramas.length() - 1).length());
            JSONObject data = new JSONObject();
            data.put("reports" , lote);
            String auth = "";
            String userID = "";
            if (userTrama.has("auth"))
             auth = userTrama.getString("auth");
            if(userTrama.getString("userID").compareTo("") == 0 ) {
                System.out.println("aca entro porque se supone que esta vacio");
                userID = "sinDefinir";
            }
            else {
                System.out.println("aca entro porque se supone que esta tiene algo");
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
            System.out.println(userTrama.getString("userID"));
            System.out.println(url);
            System.out.println(userTrama);
            httpRequests.post( url , callback,context, data , auth );

        } catch ( JSONException e) {
            publicando = false;
            System.out.println(e);
            guardarTramas();
        }
    }

    @SuppressLint("SuspiciousIndentation")
    public void agregarTrama(JSONObject contenido ){
        System.out.println("contenido =>");
        System.out.println(contenido);
        Boolean nuevo = true;
        System.out.println( usersTramas);
        System.out.println("usuarios => " + usersTramas.length());
        if(usersTramas.length() == 0 ) {
                JSONObject content = new JSONObject();
                try {
                    JSONArray tramasNuevas = new JSONArray();
                    JSONArray lote = new JSONArray();
                    lote.put(contenido.getJSONObject("trama"));
                    tramasNuevas.put(lote);
                    content.put("userID",contenido.getString("userID"));
                    content.put("urlRequest",contenido.getString("urlRequest"));
                    content.put("auth",contenido.getString("auth"));
                    content.put("intents",0);
                    content.put( "tramas",tramasNuevas);
                    usersTramas.put(content);
                    System.out.println( "cantidad de lotes => " + usersTramas.getJSONObject(0).getJSONArray("tramas").length() );
                    System.out.println( "cantidad de tramas en el primer lote => " + usersTramas.getJSONObject(0).getJSONArray("tramas").getJSONArray(0).length() );
                    if( networkStatus.getConnectivityStatusString(context) > 0 ){
                        if (!publicando)
                        publicar(0);
                    }else {
                        JSONArray bitacora = new JSONArray();
                        try {
                            String localTramas = localStorage.getItem("bitacora");
                            bitacora =  new JSONArray(localTramas);
                        } catch (Exception ex ){
                            bitacora = new JSONArray();
                        }
                        JSObject motivo = new JSObject();
                        motivo.put("code",0);
                        motivo.put("message", "sin conexión" );
                        JSObject evento = new JSObject();
                        evento.put("razon","sin conexión");
                        evento.put("motivo",motivo);
                        evento.put("fecha",new Date().getTime());
                        System.out.println(evento);
                        if(bitacora.length()  > 91){
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
                        this.guardarTramas();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        else
            for (int i = 0; i < usersTramas.length(); i++) {
                try {
                    JSONObject jsonObject = usersTramas.getJSONObject(i);
                    String userID = jsonObject.getString("userID");
                    if( (userID).compareTo(contenido.getString("userID")) == 0) {
                        jsonObject.put("urlRequest",contenido.getString("urlRequest"));
                        jsonObject.put("auth",contenido.getString("auth"));
                        nuevo = false;
                        System.out.println(userID + "  " + contenido.getString("userID") );
                        JSONArray currentTramas;
                        System.out.println(jsonObject);
                        currentTramas = jsonObject.getJSONArray("tramas");
                        System.out.println(currentTramas);
                        System.out.println(currentTramas.getJSONArray(currentTramas.length() - 1).length() <= 100 && !publicando );
                        if(currentTramas.length() == 0 ){
                            JSONArray nuevoLote = new JSONArray();
                            nuevoLote.put(contenido.getJSONObject("trama"));
                            currentTramas.put(nuevoLote);
                        } else if (currentTramas.getJSONArray(currentTramas.length() - 1).length() <= 100 && !publicando ) {
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
                    System.out.println( "cantidad de lotes => " + usersTramas.getJSONObject(i).getJSONArray("tramas").length() );
                    System.out.println( "cantidad de tramas en el primer lote => " + usersTramas.getJSONObject(i).getJSONArray("tramas").getJSONArray(0).length() );
                    if( networkStatus.getConnectivityStatusString(context) > 0 && !nuevo ){
                        if (!publicando) {
                            publicar(i);
                        }
                    } else {
                        if( i == usersTramas.length() -1 ){
                            if(nuevo == true){
                                System.out.println("nuevo");
                                JSONObject content = new JSONObject();
                                try {
                                    JSONArray tramasNuevas = new JSONArray();
                                    JSONArray lote = new JSONArray();
                                    lote.put(contenido.getJSONObject("trama"));
                                    tramasNuevas.put(lote);
                                    content.put("userID",contenido.getString("userID"));
                                    content.put("urlRequest",contenido.getString("urlRequest"));
                                    content.put("auth",contenido.getString("auth"));
                                    content.put("intents",0);
                                    content.put( "tramas",tramasNuevas);
                                    usersTramas.put(content);

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                            if( networkStatus.getConnectivityStatusString(context) > 0 ){
                                if (!publicando)
                                    publicar(0);
                            }else {
                                JSONArray bitacora = new JSONArray();
                                try {
                                    String localTramas = localStorage.getItem("bitacora");
                                    bitacora =  new JSONArray(localTramas);
                                } catch (Exception ex ){
                                    bitacora = new JSONArray();
                                }
                                JSObject motivo = new JSObject();
                                motivo.put("code",0);
                                motivo.put("message", "sin conexión" );
                                JSObject evento = new JSObject();
                                evento.put("razon","sin conexión");
                                evento.put("motivo",motivo);
                                evento.put("fecha",new Date().getTime());
                                System.out.println(evento);
                                if(bitacora.length() > 91){
                                    JSONArray bit =  new JSONArray();
                                    for (int a = bitacora.length() - 89 ; a < bitacora.length() ; a++ ){
                                        try {
                                            bit.put(bitacora.get(a));
                                        } catch (JSONException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                    bitacora = bit;
                                }
                                bitacora.put(evento);
                                localStorage.setItem("bitacora",bitacora.toString());
                                System.out.println("sin conexión");
                                System.out.println( "cantidad de lotes => " + usersTramas.getJSONObject(i).getJSONArray("tramas").length() );
                                System.out.println( "cantidad de tramas en el primer lote => " + usersTramas.getJSONObject(i).getJSONArray("tramas").getJSONArray(0).length() );
                                this.guardarTramas();
                            }
                        }else guardarTramas();
                    }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
