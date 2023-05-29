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

import com.getcapacitor.JSObject;

import org.json.JSONArray;

import java.util.Date;
import java.util.List;

public class AutoStartBackgroundLocation extends BroadcastReceiver {

    public boolean checkServiceRunning( Context context, Class<?> serviceClass){
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

        for (ActivityManager.RunningServiceInfo runningServiceInfo : services) {
            if (runningServiceInfo.service.getClassName().equals(serviceClass.getName())){
                return true;
            }
        }
        return false;
    }
    PowerManager.WakeLock wl;

    @SuppressLint("InvalidWakeLockTag")
    public void onReceive(Context context, Intent arg1)
    {
        System.out.println("motivos de inicio de servicio");
        System.out.println(arg1.getAction());
        LocalStorage localStorage = new LocalStorage(context);
        if(  Intent.ACTION_SCREEN_OFF.equals(arg1.getAction())){


        }
        if(Intent.ACTION_SCREEN_ON.equals(arg1.getAction())){

        }
        if(Intent.ACTION_BOOT_COMPLETED.equals(arg1.getAction()) ) {
            if (!checkServiceRunning(context,BackgroundLocationService.class)) {
                ServiceSettings settingData = (ServiceSettings) localStorage.getObject("settingData");
                Intent intent = new Intent(context, BackgroundLocationService.class);
                intent.setAction("ACTION.STARTFOREGROUND_ACTION");
                Log.i("Autostart", "iniciar ? " + settingData.inBG);
                if(settingData.inBG && settingData.appStarted ) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent);
                    } else {
                        context.startService(intent);
                    }
                    JSONArray bitacora = new JSONArray();
                    try {
                        String localTramas = localStorage.getItem("bitacora");
                        bitacora =  new JSONArray(localTramas);
                    } catch (Exception e ){
                        bitacora = new JSONArray();
                    }
                    JSObject motivo = new JSObject();

                    if(Intent.ACTION_BOOT_COMPLETED.equals(arg1.getAction())) {
                        motivo.put("code",01);
                        motivo.put("message", "Servicio reinicado ACTION_BOOT_COMPLETED");
                    }
                    if(Intent.ACTION_SCREEN_ON.equals(arg1.getAction())) {
                        motivo.put("code",02);
                        motivo.put("message", "Servicio reinicado ACTION_SCREEN_ON");
                    }
                    if(Intent.ACTION_SCREEN_OFF.equals(arg1.getAction())) {
                        motivo.put("code",03);
                        motivo.put("message", "Servicio reinicado ACTION_SCREEN_OFF");
                    }

                    JSObject evento = new JSObject();
                    evento.put("razon","auto inicio del servicio");
                    evento.put("motivo",motivo);
                    evento.put("fecha",new Date().getTime());
                    System.out.println(evento);
                    bitacora.put(evento);
                    localStorage.setItem("bitacora",bitacora.toString());
                    Log.i("Autostart", "started");
                }
            }
        }
    }
}
