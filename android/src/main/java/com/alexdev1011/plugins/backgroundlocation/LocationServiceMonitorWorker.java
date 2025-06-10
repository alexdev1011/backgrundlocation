package com.alexdev1011.plugins.backgroundlocation;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class LocationServiceMonitorWorker extends Worker {

    public LocationServiceMonitorWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        Log.i("WORKER", "Background service started");
        LocalStorage localStorage = new LocalStorage(context);
        ServiceSettings settingData = (ServiceSettings) localStorage.getObject("settingData");
        if (!isServiceRunning(context, BackgroundLocationService.class) && settingData != null && settingData.inBG && settingData.appStarted ) {
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