package com.alexdev1011.plugins.backgroundlocation;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class BitacoraManager {

    private static final String PREFS_NAME = "AppBitacoraPrefs";
    private static final String BITACORA_KEY = "bitacora";
    private static final int MAX_SIZE = 90;
    private LocalStorage localStorage ;


    public BitacoraManager(Context context) {
        localStorage = new LocalStorage(context);
    }

    public void guardarEvento(String razon, Object codigo, String mensaje) {
        try {
            JSONArray bitacora = obtenerBitacoraInterna();

            // Crear objeto de motivo
            JSONObject motivo = new JSONObject();
            motivo.put("code", codigo);
            motivo.put("message", mensaje);

            // Crear objeto del evento
            JSONObject evento = new JSONObject();
            evento.put("razon", razon);
            evento.put("motivo", motivo);
            evento.put("fecha", new Date().getTime());

            // Limitar tamaño de la bitácora
            if (bitacora.length() >= MAX_SIZE) {
                JSONArray nuevaBitacora = new JSONArray();
                for (int i = bitacora.length() - (MAX_SIZE - 1); i < bitacora.length(); i++) {
                    nuevaBitacora.put(bitacora.get(i));
                }
                bitacora = nuevaBitacora;
            }

            // Agregar nuevo evento
            bitacora.put(evento);
            guardarBitacoraInterna(bitacora);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void limpiarBitacora() {
        localStorage.removeItem(BITACORA_KEY);
    }

    public JSONArray obtenerBitacora() {
        try {
            return obtenerBitacoraInterna();
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private JSONArray obtenerBitacoraInterna() {
        String data = this.localStorage.getItem(BITACORA_KEY);
        try {
            return data != null ? new JSONArray(data) : new JSONArray();
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void guardarBitacoraInterna(JSONArray bitacora) {
        localStorage.setItem(BITACORA_KEY,bitacora.toString());
    }
}
