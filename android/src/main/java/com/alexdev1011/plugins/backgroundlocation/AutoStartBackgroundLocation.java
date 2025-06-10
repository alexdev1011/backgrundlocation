package com.alexdev1011.plugins.backgroundlocation;

import static android.content.Context.POWER_SERVICE;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AutoStartBackgroundLocation extends BroadcastReceiver {
    private static final String TAG = "AutoStartBGLocation";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult result = goAsync();

        new Thread(() -> {
            String action = intent.getAction();
            Log.d(TAG, "Received intent: " + action);
            logEvent(context, action);

            LocalStorage localStorage = new LocalStorage(context);
            ServiceSettings settingData = (ServiceSettings) localStorage.getObject("settingData");

            if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                        LocationServiceMonitorWorker.class,
                        15, TimeUnit.MINUTES
                ).build();

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                        "location_monitor_worker",
                        ExistingPeriodicWorkPolicy.REPLACE,
                        request
                );
            }

            if ("TU.ACCION.PARA.PARAR".equals(action)) {
                stopBackgroundService(context);
                result.finish();
                return;
            }

            if (settingData != null && settingData.inBG && settingData.appStarted) {
                comprobarYIniciarServicio(context);
            } else {
                Log.w(TAG, "settingData null o no cumple condiciones. No se inicia el servicio.");
            }

            result.finish();
        }).start();
    }

    private void comprobarYIniciarServicio(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(LocationServiceMonitorWorker.class)
                .addTag("location_startup")
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "location_startup_worker",
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    private void stopBackgroundService(Context context) {
        if (isServiceRunning(context, BackgroundLocationService.class)) {
            Intent stopIntent = new Intent(context, BackgroundLocationService.class);
            stopIntent.setAction("ACTION.STOP_SERVICE");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, stopIntent);
            } else {
                context.startService(stopIntent);
            }

            Log.i(TAG, "Se solicitó detener el servicio");
        } else {
            Log.i(TAG, "El servicio no estaba activo");
        }
    }

    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void logEvent(Context context, String action) {
        LocalStorage localStorage = new LocalStorage(context);
        JSONArray bitacora;

        try {
            String localTramas = localStorage.getItem("bitacora");
            bitacora = new JSONArray(localTramas != null && !localTramas.trim().isEmpty() ? localTramas : "[]");
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing bitacora", e);
            bitacora = new JSONArray();
        }

        try {
            JSONObject evento = new JSONObject();
            evento.put("razon", "auto inicio del servicio");
            evento.put("motivo", new JSONObject()
                    .put("code", 1)
                    .put("message", "Servicio reiniciado: " + action));
            evento.put("fecha", new Date().getTime());

            bitacora.put(evento);
            localStorage.setItem("bitacora", bitacora.toString());
            Log.d(TAG, "Event logged: " + evento.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error logging event", e);
        }
    }
}
