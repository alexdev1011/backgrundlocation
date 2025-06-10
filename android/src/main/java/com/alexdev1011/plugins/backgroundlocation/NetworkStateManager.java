package com.alexdev1011.plugins.backgroundlocation;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import java.util.Timer;
import java.util.TimerTask;

public class NetworkStateManager {
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 5000; // 5 segundos
    private final Context context;
    private final BitacoraManager bitacoraManager;
    private final TramaStorage tramaStorage;
    private int retryCount = 0;
    private Timer retryTimer;

    public NetworkStateManager(Context context, BitacoraManager bitacoraManager, TramaStorage tramaStorage) {
        this.context = context;
        this.bitacoraManager = bitacoraManager;
        this.tramaStorage = tramaStorage;
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && 
                   (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
        } else {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
    }

    public void handleNetworkStateChange() {
        if (isNetworkAvailable()) {
            if (retryCount > 0) {
                bitacoraManager.guardarEvento("Conexión recuperada", 200, "Reintentando envío de datos pendientes");
                retryPendingData();
            }
        } else {
            bitacoraManager.guardarEvento("Conexión perdida", 400, "Esperando reconexión");
            scheduleRetry();
        }
    }

    private void scheduleRetry() {
        if (retryTimer != null) {
            retryTimer.cancel();
        }
        
        retryTimer = new Timer();
        retryTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (isNetworkAvailable()) {
                    retryPendingData();
                } else if (retryCount < MAX_RETRY_ATTEMPTS) {
                    retryCount++;
                    scheduleRetry();
                } else {
                    bitacoraManager.guardarEvento("Máximo de reintentos alcanzado", 401, 
                        "No se pudo recuperar la conexión después de " + MAX_RETRY_ATTEMPTS + " intentos");
                }
            }
        }, RETRY_DELAY_MS);
    }

    private void retryPendingData() {
        try {
            tramaStorage.retryPendingData();
            retryCount = 0;
            if (retryTimer != null) {
                retryTimer.cancel();
                retryTimer = null;
            }
        } catch (Exception e) {
            bitacoraManager.guardarEvento("Error al reintentar envío", 402, e.getMessage());
        }
    }
} 