package com.alexdev1011.plugins.backgroundlocation;

import android.content.Context;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

public class Http {

    private static Http instance;
    private RequestQueue queue;
    private Context context;

    // Constructor privado para forzar singleton
    private Http(Context context) {
        this.context = context.getApplicationContext();
        queue = Volley.newRequestQueue(this.context);
    }

    // Método sincronizado para obtener la instancia única
    public static synchronized Http getInstance(Context context) {
        if (instance == null) {
            instance = new Http(context);
            Log.d("Http", "Nueva instancia creada");
        }
        return instance;
    }

    // Método para reiniciar la instancia (reinicia la cola)
    public static synchronized void resetHttpInstance(Context context) {
        if (instance != null) {
            Log.d("Http", "Reiniciando instancia existente");
            instance.resetInstance();
        } else {
            Log.d("Http", "Creando nueva instancia al resetear");
            instance = new Http(context);
        }
    }

    // Reinicia la cola de requests cancelando todas las solicitudes pendientes
    private void resetInstance() {
        if (queue != null) {
            queue.cancelAll(request -> true); // Cancela todas las requests
            queue = Volley.newRequestQueue(context);
            Log.d("Http", "RequestQueue reiniciada");
        }
    }

    public interface CallBack {
        void onSuccess(JSONObject response);

        void onError(VolleyError error, JSONArray object) throws JSONException;
    }

    public void get(String url, CallBack callback) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> callback.onSuccess(response),
                error -> {
                    // EXTRAER Y LOGUEAR CUERPO DE ERROR
                    String errorBody = null;
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        errorBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                        Log.e("Http", "Error body: " + errorBody);
                    }

                    try {
                        callback.onError(error, null);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
        queue.add(request);
    }

    public void post(String url, CallBack callback, JSONObject object, String authorization) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                object,
                response -> callback.onSuccess(response),
                error -> {
                    // EXTRAER Y LOGUEAR CUERPO DE ERROR AQUÍ
                    String errorBody = null;
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        errorBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                        Log.e("Http", "Error body: " + errorBody);
                    }

                    try {
                        callback.onError(error, object != null ? object.getJSONArray("reports") : null);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                if (authorization != null) {
                    params.put("Authorization", authorization);
                }
                return params;
            }
        };

        queue.add(request);
    }

    public void cancelAllRequests() {
        queue.cancelAll(request -> true);
    }
}
