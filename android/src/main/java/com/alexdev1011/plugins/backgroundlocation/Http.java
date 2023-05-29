package com.alexdev1011.plugins.backgroundlocation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.getcapacitor.JSObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Http {
    public interface CallBack{
        void onSuccess(JSONObject response );

        void onError(VolleyError error, JSONArray object ) throws JSONException;
    }
    public void get(String url, CallBack callback , Context context  ){

       // Instantiate the RequestQueue.
       RequestQueue queue = Volley.newRequestQueue(context);


       // Request a string response from the provided URL.
       JsonObjectRequest jsArrayRequest = new JsonObjectRequest(
               Request.Method.GET,
               url,
               null,
               new Response.Listener<JSONObject>() {
                   @Override
                   public void onResponse(JSONObject response) {
                       callback.onSuccess(response);
                   }


               },
               new Response.ErrorListener() {
                   @Override
                   public void onErrorResponse(VolleyError error) {
                       Log.d("HTTPRE", "Error Respuesta en JSON: " + error.getMessage());

                   }
               }
       );

       // Add the request to the RequestQueue.
       queue.add(jsArrayRequest);
   }

   public void post(String url, CallBack callback , Context context , JSONObject object , String authorization ){
       RequestQueue queue = Volley.newRequestQueue(context);
       JSObject data = new JSObject();
       // Request a string response from the provided URL.
       System.out.println("dentro del post =>");
       System.out.println(url);
       System.out.println(object);
       JsonObjectRequest jsArrayRequest = new JsonObjectRequest(
               Request.Method.POST,
               url,
               object,
               new Response.Listener<JSONObject>() {
                   @Override
                   public void onResponse(JSONObject response) {
                       callback.onSuccess(response);
                   }


               },
               new Response.ErrorListener() {
                   @Override
                   public void onErrorResponse(VolleyError error) {
                       try {
                           callback.onError(error, object.getJSONArray("reports"));
                       } catch (JSONException e) {
                           e.printStackTrace();
                       }
                       Log.d("HTTPRE", "Error Respuesta en JSON: " + error);
                   }
               }) {
               @SuppressLint("SuspiciousIndentation")
               @Override
               public Map<String, String> getHeaders() throws AuthFailureError {
                   Map<String, String>  params = new HashMap<String, String>();
                   if( authorization != null )
                   params.put("Authorization", authorization);
                   return params;
               }
       };

       // Add the request to the RequestQueue.
       queue.add(jsArrayRequest);
   }

}
