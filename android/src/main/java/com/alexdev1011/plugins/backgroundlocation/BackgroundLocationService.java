package com.alexdev1011.plugins.backgroundlocation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BackgroundLocationService extends Service {
    // Notificación del servicio en primer plano
    private Notification noty;
    // Binder para la comunicación con el servicio
    private final IBinder binder = new LocalBinder();
    // Almacenamiento local para configuraciones
    private LocalStorage localStorage = new LocalStorage(this);
    // Configuraciones del servicio de ubicación
    public ServiceSettings settings = new ServiceSettings();
    // Motivo del envío de la ubicación actual
    private JSObject motivo = new JSObject();
    // ID único para la notificación del servicio
    private static final int NOTIFICATION_ID = 45023;
    // Gestor de ubicación del sistema Android
    public LocationManager locationManager;
    // Listeners para diferentes proveedores de ubicación
    public MyLocationListener listenerNetwork;
    public MyLocationListener listenerGPS;
    public MyLocationListener listenerLocation;
    // Gestor para almacenar y enviar tramas de ubicación
    public TramaStorage tramaStorage;
    // Contexto de la aplicación
    private Context context;
    // Última ubicación enviada al servidor
    public Location lastLocationSending = null;
    // Tamaño máximo del buffer de ubicaciones
    private static final int MAX_BUFFER_SIZE = 1000; 
    // Buffer para almacenar ubicaciones recientes
    private List<Location> bufferTramas = new ArrayList<Location>();
    // ID de inicio del servicio
    int startId = 0;
    // Precisión mínima requerida en metros
    private int accuracy = 15;
    // Timestamp de cuando se inició el servicio
    long serviceStarterAt = 0;
    // Timestamp del último envío de ubicación
    long lastSendingAt = 0;
    // Timer para tareas programadas
    private Timer timer;
    private TimerTask timerTask;
    // Timer para verificar envío de tramas sin posición
    private Timer timerSinPosicion;
    private TimerTask timerTaskSinPosicion;
    // Bandera para reiniciar el servicio
    private Boolean RESTARTSERVICE = false;
    // Gestor para registrar eventos y logs
    private BitacoraManager bitacoraManager = new BitacoraManager(this);
    // Contador de ubicaciones repetidas para detectar GPS atascado
    private int repeatedLocationCount = 0;
    // Última ubicación recibida para comparar
    private Location lastReceivedLocation = null;
    // Máximo número de ubicaciones repetidas antes de reiniciar
    private static final int MAX_REPEATED_LOCATIONS = 5;

    /**
     * Obtiene la mejor ubicación del buffer de ubicaciones almacenadas
     * @param force Si es true, devuelve la mejor ubicación sin importar la precisión
     * @return La mejor ubicación disponible o null si no hay ninguna válida
     */
    protected Location isBetterLocationinArray(Boolean force) {
        Location tramaDeRetorno = null;
        
        try {
            // Limitar el tamaño del buffer para evitar uso excesivo de memoria
            if (bufferTramas.size() > MAX_BUFFER_SIZE) {
                bufferTramas = bufferTramas.subList(bufferTramas.size() - MAX_BUFFER_SIZE, bufferTramas.size());
            }

            // Recorrer el buffer para encontrar la mejor ubicación
            for(int i = 0; i < bufferTramas.size(); i++) {
                if(tramaDeRetorno == null) {
                    tramaDeRetorno = bufferTramas.get(i);
                } else {
                    if(bufferTramas.get(i) != null && tramaDeRetorno != null) {
                        // Calcular diferencia de tiempo entre ubicaciones
                        long timeDelta = bufferTramas.get(i).getTime() - tramaDeRetorno.getTime();
                        boolean isNewer = timeDelta > 0;
                        // Calcular diferencia de precisión
                        int accuracyDelta = (int) (bufferTramas.get(i).getAccuracy() - tramaDeRetorno.getAccuracy());
                        boolean isLessAccurate = accuracyDelta > 0;
                        boolean isMoreAccurate = accuracyDelta < 0;
                        boolean isSignificantlyLessAccurate = accuracyDelta > 2;
                        
                        // Priorizar ubicaciones más precisas
                        if(isMoreAccurate){
                            tramaDeRetorno = bufferTramas.get(i);
                        }else if( isNewer && !isSignificantlyLessAccurate ){
                            // Si es más nueva y no menos precisa, usar esta ubicación
                            tramaDeRetorno = bufferTramas.get(i);
                        }
                    }
                }
            }
        } catch (Error error) {
            bitacoraManager.guardarEvento("Error en buffer", 500, error.getMessage());
            bufferTramas.clear(); // Limpiar buffer en caso de error
        }
        
        if(tramaDeRetorno == null) {
            motivo = new JSObject();
            return null;
        }
        
        // Si se fuerza el retorno o la precisión es suficiente, devolver la ubicación
        if(force )
            return tramaDeRetorno;
        if( tramaDeRetorno.getAccuracy() < accuracy )
            return tramaDeRetorno;
        else return null;
    }

    /**
     * Determina si una nueva ubicación es mejor que la anterior
     * @param location Nueva ubicación recibida
     * @param lastLocationSending Última ubicación enviada
     * @return La mejor ubicación si cumple los criterios, null en caso contrario
     */
    protected Location isBetterLocation(Location location, Location lastLocationSending ) {

        if(location == null ){
            return null;
        }

        long deltaTime = 0;

        // Limpiar buffer si las ubicaciones son muy antiguas (más de 4 segundos)
        if(this.bufferTramas.size() > 2 ){
            deltaTime = location.getTime() - this.bufferTramas.get(0).getTime();
            if( deltaTime > 4000  ){
                this.bufferTramas.remove(0);
            }
        }
        
        // Agregar nueva ubicación al buffer
        try{
            this.bufferTramas.add(location);
        }catch (Error err){
            // Reinicializar buffer en caso de error
            this.bufferTramas = new ArrayList<Location>();
            this.bufferTramas.add(location);
            System.out.println(err);
            return null;
        }

        // Verificar si hay un motivo específico para enviar la ubicación
        try {
            Object obg =  this.localStorage.getObject("motivo");
            if(obg != null){
                Motivo mot = (Motivo) obg;
                if(!(mot).sending){
                    motivo = new JSObject();
                    motivo.put("code", mot.code);
                    motivo.put("message", mot.message);
                    Location resolver = this.isBetterLocationinArray(true);
                    mot.sending = true;
                    localStorage.setObject("motivo",mot);
                    return resolver;
                }
            }
        } catch (Error | IOException error ){
            Log.e("error",error.getMessage());
        }

        // Verificar si se debe detener el servicio
        if(settings.stopService){
            motivo = new JSObject();
            motivo.put("code", 1002);
            motivo.put("message", "serivico detenido ultima trama");
            return this.isBetterLocationinArray(true);
        }

        // Verificar si está activado el modo pánico
        if(settings.panico){
            motivo = new JSObject();
            motivo.put("code", 51);
            motivo.put("message", "Panico activado");
            return  this.isBetterLocationinArray(true);
        }

        // Si no hay ubicación previa, cualquier ubicación nueva es mejor
        if (lastLocationSending == null ) {
            // Aplicar retraso de 20 segundos al inicio del trackeo para estabilizar el GPS
            // Esto evita enviar ubicaciones imprecisas en los primeros momentos
            long tiempoTranscurrido = new Date().getTime() - serviceStarterAt;
            if (tiempoTranscurrido < 20000) { // 20 segundos = 20000 milisegundos
                // No enviar trama aún, esperar el período de estabilización inicial
                return null;
            }
            
            Location resolver = this.isBetterLocationinArray(true);
            if(resolver != null ) {
                motivo = new JSObject();
                motivo.put("code", 1001);
                motivo.put("message", "primer toma de contacto al activar el seguimiento");
                return resolver;
            }
            else
                return null;
        }

        // Verificar si ha pasado suficiente tiempo desde la última ubicación
        long timeDelta = new Date().getTime() - lastLocationSending.getTime();
        boolean isSignificantlyNewer = timeDelta > settings.minS * 1000;
        boolean isSignificantlyOlder = timeDelta < - settings.minS * 1000;
        boolean isNewer = timeDelta > 800;

        // Calcular distancia entre ubicaciones
        float distBetweenLocation = location.distanceTo(lastLocationSending);
        boolean minDist = distBetweenLocation > settings.distanceFilter;
        boolean moreLength = location.distanceTo(lastLocationSending) > ( settings.distanceFilter * 10 / 100 ) ;

        // Verificar precisión de las ubicaciones
        int accuracyDelta = (int) (location.getAccuracy() - lastLocationSending.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 1;

        // Verificar si ambas ubicaciones provienen del mismo proveedor
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                lastLocationSending.getProvider());


        // Si ha pasado mucho tiempo, usar la nueva ubicación
        if (isSignificantlyNewer && !isSignificantlyLessAccurate) {
            Location resolver = this.isBetterLocationinArray(true);

            if(resolver != null){
                motivo = new JSObject();
                motivo.put("code", 1000);
                motivo.put("message", "por tiempo limite " + settings.minS +" segundos" );
                return resolver;
            }
            else
                return null;
        }

        // Verificar cambio significativo en el rumbo/dirección
        float bearingCompare = location.getBearing() - lastLocationSending.getBearing();
        if(location.getProvider() != "fused" && lastLocationSending.getProvider() != "fused" && distBetweenLocation > accuracy){
            if( bearingCompare > settings.grados || bearingCompare < -settings.grados &&  isNewer ){
                Location resolver = this.isBetterLocationinArray(true);
                if(resolver != null){
                    motivo = new JSObject();
                    motivo.put("code", 5);
                    motivo.put("message", "por cambio de rumbo a "+ settings.grados +" grados" );
                    return resolver;
                }
                else
                    return null;
            }
        }
        
        // Verificar si se ha alcanzado la distancia mínima configurada
        if ( minDist ) {
            Location resolver = this.isBetterLocationinArray(true);
            if(resolver != null){
                motivo = new JSObject();
                motivo.put("code", 4);
                motivo.put("message", "por distancia " + settings.distanceFilter +" metros");
                return resolver;
            }
            else
                return null;
        }

        return null;
    }

    /** 
     * Verifica si dos proveedores de ubicación son el mismo
     * @param provider1 Primer proveedor a comparar
     * @param provider2 Segundo proveedor a comparar
     * @return true si son el mismo proveedor, false en caso contrario
     */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    /**
     * Verifica si una ubicación es repetida (misma coordenada)
     * @param newLocation Nueva ubicación recibida
     * @return true si es repetida, false en caso contrario
     */
    private boolean isLocationRepeated(Location newLocation) {
        if (lastReceivedLocation == null) {
            lastReceivedLocation = newLocation;
            return false;
        }
        
        // Verificar si la ubicación es muy antigua (más de 5 minutos)
        long currentTime = System.currentTimeMillis();
        long locationAge = currentTime - newLocation.getTime();
        if (locationAge > 300000) { // 5 minutos
            bitacoraManager.guardarEvento("Ubicación muy antigua recibida", 1007, "Edad: " + (locationAge/1000) + " segundos");
            return true; // Considerar como repetida para forzar reinicio
        }
        
        // Verificar si la distancia es menor a 2 metros
        float distance = newLocation.distanceTo(lastReceivedLocation);
        if (distance < 2.0f) {
            // Verificar también si el tiempo es muy similar
            long timeDiff = Math.abs(newLocation.getTime() - lastReceivedLocation.getTime());
            if (timeDiff < 2000) { // Menos de 2 segundos de diferencia
                return true;
            }
        }
        
        lastReceivedLocation = newLocation;
        return false;
    }

    /**
     * Reinicia los servicios de ubicación de forma robusta
     */
    private void restartLocationServices() {
        try {
            // Detener listeners actuales
            if (listenerGPS != null) {
                locationManager.removeUpdates(listenerGPS);
            }
            if (listenerNetwork != null) {
                locationManager.removeUpdates(listenerNetwork);
            }
            
            // Limpiar variables
            bufferTramas.clear();
            repeatedLocationCount = 0;
            lastReceivedLocation = null;
            lastLocationSending = null;
            
            // Esperar un momento antes de reagregar
            Thread.sleep(2000);
            
            // Reagregar listeners
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 250, 0, listenerGPS);
            }
            
            bitacoraManager.guardarEvento("Servicios de ubicación reiniciados", 1005, "GPS desatascado");
            
        } catch (Exception e) {
            bitacoraManager.guardarEvento("Error al reiniciar servicios", 500, e.getMessage());
        }
    }

    // Acción de broadcast para comunicación entre componentes
    static final String ACTION_BROADCAST = (
            BackgroundLocationService.class.getPackage().getName() + ".broadcast"
    );

    /**
     * Listener personalizado para recibir actualizaciones de ubicación
     * Implementa la interfaz LocationListener de Android
     */
    public class MyLocationListener implements LocationListener
    {
        /**
         * Método principal que se ejecuta cuando se recibe una nueva ubicación
         * @param loc Nueva ubicación recibida del proveedor
         */
        public void onLocationChanged(final Location loc) {
            try {
                // NUEVA VALIDACIÓN: Detectar GPS atascado
                if (isLocationRepeated(loc)) {
                    repeatedLocationCount++;
                    if (repeatedLocationCount > MAX_REPEATED_LOCATIONS) {
                        bitacoraManager.guardarEvento("GPS atascado detectado", 1004, "Reiniciando servicios");
                        // Usar método de reinicio robusto
                        restartLocationServices();
                        return;
                    }
                } else {
                    repeatedLocationCount = 0; // Resetear contador
                }
                
                // Inicializar configuraciones del servicio
                if (!initializeSettings()) return;
                
                // Procesar la nueva ubicación y determinar si es válida
                Location trama = procesarNuevaUbicacion(loc);
                if (trama == null) return;
                
                // Obtener nivel actual de batería
                float batteryPct = obtenerNivelBateria();
                // Crear objeto con datos de ubicación
                JSObject location = crearObjetoUbicacion(trama, batteryPct);
                // Determinar la precisión y ajustar coordenadas si es necesario
                String pos = determinarPrecisionUbicacion(trama, location);
                
                // Preparar datos para envío al servidor
                JSObject senData = crearDatosEnvio(location);
                // Registrar evento en la bitácora si está habilitado
                registrarEventoUbicacion(pos, trama);
                // Enviar trama al sistema de almacenamiento
                enviarTrama(senData);
                
            } catch (Error error) {
                bitacoraManager.guardarEvento("Servicio destruido", 10, error.getMessage());
            }
        }

        /**
         * Inicializa las configuraciones del servicio desde el almacenamiento local
         * @return true si se inicializó correctamente
         */
        private boolean initializeSettings() {
            ServiceSettings settingData = (ServiceSettings) localStorage.getObject("settingData");
            if (settingData != null) {
                settings = settingData;
            }
            return true;
        }

        /**
         * Procesa una nueva ubicación y determina si es mejor que las anteriores
         * @param loc Nueva ubicación recibida
         * @return La mejor ubicación disponible o null si no es válida
         */
        private Location procesarNuevaUbicacion(Location loc) {
            Location trama = null;
            if (loc != null) {
                try {
                    // Limpiar buffer si contiene elementos nulos
                    if (bufferTramas.size() > 0 && bufferTramas.get(0) == null) {
                        bufferTramas.clear();
                    }
                    // Determinar si la nueva ubicación es mejor que la anterior
                    trama = isBetterLocation(loc, lastLocationSending);
                } catch (Error error) {
                    bitacoraManager.guardarEvento("Error al obtener la mejor trama", 12, error.getMessage());
                }
            }
            return trama;
        }

        /**
         * Obtiene el nivel actual de batería del dispositivo
         * @return Porcentaje de batería (0-100)
         */
        private float obtenerNivelBateria() {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return level * 100 / (float) scale;
        }

        /**
         * Crea el objeto JSObject con todos los datos de la ubicación
         * @param trama Ubicación procesada
         * @param batteryPct Nivel de batería actual
         * @return Objeto JSObject con todos los datos de ubicación
         */
        private JSObject crearObjetoUbicacion(Location trama, float batteryPct) {
            JSObject location = new JSObject();
            // Incluir estado de pánico y resetear banderas
            location.put("panic", settings.panico);
            settings.panico = false;
            settings.stopService = false;
            
            try {
                // Guardar configuraciones actualizadas
                localStorage.setObject("settingData", settings);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            
            // Agregar todos los datos de la ubicación
            location.put("reason", motivo);  // Motivo del envío
            location.put("accuracy", Math.round(trama.getAccuracy()));  // Precisión en metros
            location.put("altitude", Math.round(trama.getAltitude()));  // Altitud en metros
            location.put("bearing", Math.round(trama.getBearing()));    // Rumbo en grados
            location.put("provider", trama.getProvider());              // Proveedor de ubicación
            location.put("speed", Math.round((trama.getSpeed() * 3.6))); // Velocidad en km/h
            location.put("time", trama.getTime());                      // Timestamp
            location.put("battery", batteryPct);                        // Nivel de batería
            
            return location;
        }

        /**
         * Determina la precisión de la ubicación y ajusta las coordenadas si es necesario
         * @param trama Ubicación a evaluar
         * @param location Objeto JSObject donde se guardan las coordenadas
         * @return String indicando el tipo de precisión ("si", "no", "si mas preciso", "si normal")
         */
        private String determinarPrecisionUbicacion(Location trama, JSObject location) {
            String pos = "si";
            
            if (lastLocationSending != null && trama.getAccuracy() > accuracy) {
                // Calcular si las ubicaciones están muy cerca considerando la precisión
                if (lastLocationSending.distanceTo(trama) < ((Math.round(trama.getAccuracy()) + Math.round(lastLocationSending.getAccuracy()))) * 1.3 && trama.getAccuracy() * 2 > accuracy) {
                    pos = "no";
                    // Si la ubicación anterior es significativamente más precisa
                    if (lastLocationSending.getAccuracy() > trama.getAccuracy() * 1.3) {
                        pos = "si mas preciso";
                    } else {
                        // Usar coordenadas de la ubicación anterior (más confiable)
                        trama.setLatitude(lastLocationSending.getLatitude());
                        trama.setLongitude(lastLocationSending.getLongitude());
                    }
                } else {
                    pos = "si normal";
                    location.put("latitude", trama.getLatitude());
                    location.put("longitude", trama.getLongitude());
                }
            } else {
                // Usar las nuevas coordenadas directamente
                location.put("latitude", trama.getLatitude());
                location.put("longitude", trama.getLongitude());
            }
            
            // Actualizar la última ubicación enviada
            lastLocationSending = trama;
            return pos;
        }

        /**
         * Crea el objeto con todos los datos necesarios para el envío al servidor
         * @param location Objeto JSObject con datos de ubicación
         * @return Objeto JSObject listo para enviar
         */
        private JSObject crearDatosEnvio(JSObject location) {
            JSObject senData = new JSObject();
            senData.put("userID", settings.userID);           // ID del usuario
            senData.put("urlRequest", settings.urlRequests);  // URL del servidor
            // Incluir token de autorización si está disponible
            if (settings.authorization != null) {
                senData.put("auth", settings.authorization);
            } else {
                senData.put("auth", "");
            }
            senData.put("trama", location);  // Datos de ubicación
            return senData;
        }

        /**
         * Registra el evento de ubicación en la bitácora local si está habilitado
         * @param pos Tipo de precisión de la ubicación
         * @param trama Datos de ubicación
         */
        private void registrarEventoUbicacion(String pos, Location trama) {
            if (settings.storageLocal) {
                bitacoraManager.guardarEvento("datos de posición " + pos + " precisión " + Math.round(trama.getAccuracy()), motivo.getInteger("code"), motivo.getString("message"));
            }
        }

        /**
         * Envía la trama de ubicación al sistema de almacenamiento y maneja errores
         * @param senData Datos completos listos para enviar
         */
        private void enviarTrama(JSObject senData) {
            System.out.println("se envia la trama");
            lastSendingAt = new Date().getTime();
            
            // Solo enviar si hay URL configurada
            if (!settings.urlRequests.equals("")) {
                try {
                    System.out.println("256 agregando trama");
                    tramaStorage.agregarTrama(senData);
                } catch (Error err) {
                    System.out.println(err);
                    System.out.println("un error");
                    // Manejar error según la versión de Android
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if(listenerGPS != null)
                            locationManager.removeUpdates(listenerGPS);
                        if(listenerNetwork != null)
                            locationManager.removeUpdates(listenerNetwork);

                        restearVariables();

                        // Reiniciar el servicio completo
                        Intent restartIntent = new Intent(context, BackgroundLocationService.class);
                        restartIntent.setAction("ACTION.STARTFOREGROUND_ACTION");
                        context.startForegroundService(restartIntent);

                        // Detener este servicio
                        stopForeground(true);
                        stopSelf();
                    } else {
                        // Reiniciar servicio en versiones anteriores
                        RESTARTSERVICE = true;
                        Intent ser = new Intent(context, BackgroundLocationService.class);
                        ser.setAction("ACTION.RESTARTFOREGROUND_ACTION");
                        context.startService(ser);
                    }
                }
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        public void onProviderDisabled(String provider)
        {
           // Toast.makeText( getApplicationContext(), "Gps Disabled", Toast.LENGTH_SHORT ).show();
        }


        public void onProviderEnabled(String provider)
        {
            // Toast.makeText( getApplicationContext(), "Gps Enabled", Toast.LENGTH_SHORT).show();
        }
    }

    public void stopService(){
        stopForeground(true);
        stopSelfResult(startId);
    }
    @Override
    public IBinder onBind(Intent arg0) {
        return binder;
    }

    public void onDestroy() {
        System.out.println("terminando el servicio");
        if(listenerGPS != null)
            locationManager.removeUpdates(listenerGPS);
        if(listenerNetwork != null)
            locationManager.removeUpdates(listenerNetwork);
        stopTimer();
        stopTimerSinPosicion();
        //Toast.makeText(this, "Traker Detenido", Toast.LENGTH_LONG).show();
        bitacoraManager.guardarEvento("Servicio destruido",10,"Tracker detenido");
        Log.d("My Service", "onDestroy");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<Boolean> handler = executor.submit(() -> restartService());
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                handler.get( 300 , TimeUnit.MILLISECONDS);
            }
        } catch (TimeoutException e) {
            handler.cancel(true);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        executor.shutdownNow();

        super.onDestroy();
    }

    @SuppressLint("LongLogTag")
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d("BG_LOCATION", "onTaskRemoved - Proceso matado por el OS");
        Log.d("BG_LOCATION", "Intent action: " + (rootIntent != null ? rootIntent.getAction() : "null"));
        bitacoraManager.guardarEvento("onTaskRemoved", 1003, "Proceso matado por el OS");
        
        // Programar reinicio automático
        programarReinicioAutomatico();
        
        super.onTaskRemoved(rootIntent);
    }

    private void programarReinicioAutomatico() {
        try {
            Log.d("BG_LOCATION", "Programando reinicio automático");
            
            // Usar tu AutoStartBackgroundLocation existente
            Intent restartIntent = new Intent("YouWillNeverKillMe");
            Log.d("BG_LOCATION", "Intent de reinicio creado");
            
            // Crear PendingIntent para broadcast
            PendingIntent restartPI = PendingIntent.getBroadcast(
                this, 
                1, 
                restartIntent, 
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );
            Log.d("BG_LOCATION", "PendingIntent creado");
            
            // Programar reinicio con AlarmManager
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Log.d("BG_LOCATION", "AlarmManager obtenido");
                
                // Intentar con setExact primero (más confiable)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                        SystemClock.elapsedRealtime() + 2000, // 2 segundos
                        restartPI
                    );
                    Log.d("BG_LOCATION", "AlarmManager.setExactAndAllowWhileIdle programado");
                } else {
                    am.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                        SystemClock.elapsedRealtime() + 2000, // 2 segundos
                        restartPI
                    );
                    Log.d("BG_LOCATION", "AlarmManager.setExact programado");
                }
                
                Log.d("BG_LOCATION", "AlarmManager programado para reinicio");
                bitacoraManager.guardarEvento("AlarmManager programado", 1004, "Reinicio en 2 segundos");
            } else {
                Log.e("BG_LOCATION", "AlarmManager es null");
                bitacoraManager.guardarEvento("AlarmManager null", 1005, "No se pudo obtener AlarmManager");
            }
        } catch (Exception e) {
            Log.e("BG_LOCATION", "Error en programarReinicioAutomatico: " + e.getMessage());
            bitacoraManager.guardarEvento("Error programarReinicioAutomatico", 1006, e.getMessage());
        }
    }

    public Boolean restartService(){
        if(RESTARTSERVICE){
            RESTARTSERVICE = false;
            Intent intent = new Intent(context, BackgroundLocationService.class);
            intent.setAction("ACTION.STARTFOREGROUND_ACTION");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        }
        return true;
    }
    public void restearVariables(){
        System.out.println("reseteando variables");
        serviceStarterAt = new Date().getTime();
        localStorage = new LocalStorage(this);
        bitacoraManager = new BitacoraManager(context);
        settings = new ServiceSettings();
        this.bufferTramas.clear();
        // Resetear variables de detección de GPS atascado
        repeatedLocationCount = 0;
        lastReceivedLocation = null;
        // Detener timers existentes
        stopTimer();
        stopTimerSinPosicion();
        try {
            Http cc =  Http.getInstance(this);
            cc.cancelAllRequests();
            tramaStorage = new TramaStorage(this);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        RESTARTSERVICE = false;
    }
    public class LocalBinder extends Binder {
        BackgroundLocationService getService(String id ) {
            return BackgroundLocationService.this;
        }
    }


    @SuppressLint({"WrongConstant", "In                                            WakeLockTag", "MissingPermission", "LongLogTag", "SuspiciousIndentation"})
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && "ACTION.STOP_SERVICE".equals(intent.getAction())) {
            Log.i("BackgroundLocationService", "Stopping service on request");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if(intent == null){
            intent = new Intent(context, BackgroundLocationService.class);
            intent.setAction("ACTION.STARTFOREGROUND_ACTION");
        }

        // CRÍTICO: Llamar a startForeground() inmediatamente para cumplir con el requisito de Android
        // Esto evita el error "Context.startForegroundService() did not then call Service.startForeground()"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Inicializar configuraciones antes de crear la notificación
            ServiceSettings settingData = (ServiceSettings) this.localStorage.getObject("settingData");
            if (settingData != null) {
                this.settings = settingData;
            }
            
            Notification notification = getNotification();
            if (notification != null) {
                startForeground(NOTIFICATION_ID, notification);
                Log.i("BackgroundLocationService", "Foreground service started with notification");
            }
        }

        if (intent.getAction().equals("ACTION.STARTFOREGROUND_ACTION")) {
            // onActivityStopped() ya no es necesario aquí porque startForeground() se llama arriba
            // onActivityStopped(); // Comentado para evitar llamada redundante
            motivo.put("code", 0 );
            motivo.put("message", "no coorresponde ningun motivo");
            System.out.println("el servicio se esta activando gracias al action");
            ServiceSettings settingData = (ServiceSettings) this.localStorage.getObject("settingData");
            if( settingData != null) {
                this.settings = settingData;
            }
            this.startId = startId;
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            listenerGPS = new MyLocationListener();
            listenerLocation = new MyLocationListener();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return 0;
            }
            bufferTramas.clear();
            serviceStarterAt = new Date().getTime();
            LocationCallback locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult == null) {
                        return;
                    }
                    for (Location location : locationResult.getLocations()) {
                        listenerLocation.onLocationChanged(location);
                        // Update UI with location data
                        // ...
                    }
                }
            };
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 250, 0, listenerGPS);
            Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            listenerLocation.onLocationChanged(loc);
            startTimer();
            startTimerSinPosicion();
           /**
            *  if(this.settings.inBG)
            *      onActivityStopped();
            *                 */
        } else {
            if (intent.getAction().equals( "ACTION.STOPFOREGROUND_ACTION")) {
                System.out.println("el servicio se detuvo gracias al action");
                //your end servce code
                stopForeground(true);
                stopSelfResult(startId);
            } else if (intent.getAction().equals( "ACTION.RESTARTFOREGROUND_ACTION")) {
                System.out.println("el servicio se detuvo gracias al action");
                //your end servce code
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    if(listenerGPS != null)
                        locationManager.removeUpdates(listenerGPS);
                    if(listenerNetwork != null)
                        locationManager.removeUpdates(listenerNetwork);
                    
                    restearVariables();
                    // Detener este servicio
                    stopForeground(true);

                     // Reiniciar el servicio completo
                    Intent restartIntent = new Intent(context, BackgroundLocationService.class);
                    restartIntent.setAction("ACTION.STARTFOREGROUND_ACTION");
                    context.startForegroundService(restartIntent);
        

                    stopSelf();
                } else {
                    RESTARTSERVICE = true;
                    stopForeground(true);
                    stopSelfResult(startId);
                }
            }
        }
        //return super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate()  {
        context = this;

        try {
            this.tramaStorage = new TramaStorage(this);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        ServiceSettings settingData = (ServiceSettings) this.localStorage.getObject("settingData");

        if( settingData != null) {
            this.settings = settingData;
        }
        createChannel();
       // Toast.makeText(this, "Traker iniciado", Toast.LENGTH_LONG).show();
        super.onCreate();
    }



    /** gestion de notificaciones **/

    // crea una nueva notificacion
    private Notification getNoty(){
        Notification.Builder builder = new Notification.Builder(this)
                .setContentTitle( this.settings.notificationTitle )
                .setContentText( this.settings.notificationContent )
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis());

        try {
            String name = getAppString(
                    "capacitor_background_geolocation_notification_icon",
                    "mipmap/ic_launcher"
            );
            String[] parts = name.split("/");
            // It is actually necessary to set a valid icon for the notification to behave
            // correctly when tapped. If there is no icon specified, tapping it will open the
            // app's settings, rather than bringing the application to the foreground.
            builder.setSmallIcon(
                    getAppResourceIdentifier(parts[1], parts[0])
            );
        } catch (Exception e) {
            Logger.error("Could not set notification icon", e);
        }

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(
                getPackageName()
        );
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            builder.setContentIntent(
                    PendingIntent.getActivity(
                            this,
                            0,
                            launchIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    )
            );
        }

        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId( BackgroundLocationService.class.getPackage().getName());
        }

        Notification build = builder.build();
        this.noty = build;
        return build;

    }
    // crea el canal para la notificación
    private void createChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE
            );
            NotificationChannel channel;
            channel = new NotificationChannel(
                    BackgroundLocationService.class.getPackage().getName(),
                    getAppString(
                            "capacitor_background_geolocation_notification_channel_name",
                            "Background Tracking"
                    ),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }
    }

    // controladores de la ejecucion de la notificacion
    void onActivityStarted() {
        stopForeground(true);
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    private Notification getNotification() {
        if(noty != null){
            return noty;
        } else {
            return getNoty();
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    void onActivityStopped() {
        // Este método se mantiene para compatibilidad y uso futuro
        // pero ya no se llama en el flujo principal para evitar redundancia
        Notification notification = getNotification();
        if (notification != null) {
            System.out.println("mostrando la notificación");
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    //funciones de soporte para crear la notificacion y el canal
    private int getAppResourceIdentifier(String name, String defType) {
        return getResources().getIdentifier(
                name,
                defType,
                getPackageName()
        );
    }

    private String getAppString(String name, String fallback) {
        int id = getAppResourceIdentifier(name, "string");
        return id == 0 ? fallback : getString(id);
    }

    private void startTimer() {
        System.out.println("Iniciando timer");
        stopTimer(); // Asegurar que no hay timers activos
        timer = new Timer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    // Verificar si ha pasado mucho tiempo sin envío de ubicaciones
                    long timeSinceLastSending = new Date().getTime() - lastSendingAt;
                    if (timeSinceLastSending > 600000) { // 10 minutos sin envío
                        bitacoraManager.guardarEvento("Sin ubicaciones por 10 minutos", 1006, "Verificando GPS");
                        // Forzar reinicio del GPS
                        Intent restartIntent = new Intent(context, BackgroundLocationService.class);
                        restartIntent.setAction("ACTION.RESTARTFOREGROUND_ACTION");
                        context.startService(restartIntent);
                        return;
                    }
                    
                    // Verificar si el servicio lleva más de 30 minutos activo
                    if(new Date().getTime() > serviceStarterAt + 1800000){ // 30 minutos
                        System.out.println("reiniciando servicio por tiempo");
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                            restearVariables();
                        } else {
                            System.out.println("reseteando servicio");
                            RESTARTSERVICE = true;
                            Intent ser = new Intent(context, BackgroundLocationService.class);
                            ser.setAction("ACTION.RESTARTFOREGROUND_ACTION");
                            context.startService(ser);
                        }
                    }
                    @SuppressLint("MissingPermission") Location locthis = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if(locthis != null){
                        locthis.setTime(new Date().getTime());
                        listenerLocation.onLocationChanged(locthis);
                    } else {
                        locthis = isBetterLocationinArray(true);
                        if(locthis != null) {
                            locthis.setTime(new Date().getTime());
                            listenerLocation.onLocationChanged(locthis);
                        }
                    }
                } catch (Exception e) {
                    bitacoraManager.guardarEvento("Error en timer", 501, e.getMessage());
                }
            }
        };
        // Ejecutar cada 5 minutos en lugar de una sola vez
        timer.scheduleAtFixedRate(timerTask, 300000, 300000);
    }

    private void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    /**
     * Crea una trama con la última posición conocida o sin posición si no está disponible
     * @return JSObject con datos de ubicación (última conocida o sin coordenadas)
     */
    private JSObject crearTramaConUltimaPosicion() {
        JSObject location = new JSObject();
        
        // Incluir estado de pánico y resetear banderas
        location.put("panic", settings.panico);
        settings.panico = false;
        settings.stopService = false;
        
        try {
            // Guardar configuraciones actualizadas
            localStorage.setObject("settingData", settings);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        // Obtener nivel de batería
        float batteryPct = obtenerNivelBateria();
        
        // Intentar obtener la última ubicación conocida
        Location ultimaUbicacion = null;
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                ultimaUbicacion = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {
            bitacoraManager.guardarEvento("Error al obtener última ubicación", 503, e.getMessage());
        }
        
        // Crear motivo para la trama
        JSObject motivoTrama = new JSObject();
        
        if (ultimaUbicacion != null) {
            // Usar la última ubicación conocida
            motivoTrama.put("code", 2002);
            motivoTrama.put("message", "trama con última posición conocida");
            
            // Agregar datos de la última ubicación conocida
            location.put("reason", motivoTrama);
            location.put("accuracy", Math.round(ultimaUbicacion.getAccuracy()));
            location.put("altitude", Math.round(ultimaUbicacion.getAltitude()));
            location.put("bearing", Math.round(ultimaUbicacion.getBearing()));
            location.put("provider", ultimaUbicacion.getProvider());
            location.put("speed", Math.round((ultimaUbicacion.getSpeed() * 3.6)));
            location.put("time", ultimaUbicacion.getTime());
            location.put("battery", batteryPct);
            location.put("latitude", ultimaUbicacion.getLatitude());
            location.put("longitude", ultimaUbicacion.getLongitude());
        } else {
            // No hay última ubicación conocida, crear trama sin posición
            motivoTrama.put("code", 2001);
            motivoTrama.put("message", "trama sin posición - sin ubicación disponible");
            
            // Agregar datos básicos sin coordenadas
            location.put("reason", motivoTrama);
            location.put("accuracy", -1);  // -1 indica sin precisión
            location.put("altitude", 0);
            location.put("bearing", 0);
            location.put("provider", "sin_posicion");
            location.put("speed", 0);
            location.put("time", new Date().getTime());
            location.put("battery", batteryPct);
            location.put("latitude", 0.0);  // Coordenadas en 0
            location.put("longitude", 0.0);
        }
        
        return location;
    }

    /**
     * Obtiene el nivel actual de batería del dispositivo
     * @return Porcentaje de batería (0-100)
     */
    private float obtenerNivelBateria() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level * 100 / (float) scale;
    }

    /**
     * Inicia el timer que verifica cada minuto si se debe enviar una trama sin posición
     */
    private void startTimerSinPosicion() {
        System.out.println("Iniciando timer sin posición");
        stopTimerSinPosicion(); // Asegurar que no hay timers activos
        timerSinPosicion = new Timer();
        timerTaskSinPosicion = new TimerTask() {
            @Override
            public void run() {
                try {
                    // Verificar si ha pasado más de 2 minutos sin envío de ubicación
                    long timeSinceLastSending = new Date().getTime() - lastSendingAt;
                    if (timeSinceLastSending > 120000) { // 2 minutos sin envío
                        bitacoraManager.guardarEvento("Enviando trama con última posición conocida", 2002, "Sin ubicación por " + (timeSinceLastSending/1000) + " segundos");
                        
                        // Crear trama con última posición conocida o sin posición
                        JSObject locationConUltimaPosicion = crearTramaConUltimaPosicion();
                        JSObject senData = listenerGPS.crearDatosEnvio(locationConUltimaPosicion);
                        
                        // Enviar trama con última posición o sin posición
                        listenerGPS.enviarTrama(senData);
                    }
                } catch (Exception e) {
                    bitacoraManager.guardarEvento("Error en timer sin posición", 502, e.getMessage());
                }
            }
        };
        // Ejecutar cada 1 minuto
        timerSinPosicion.scheduleAtFixedRate(timerTaskSinPosicion, 60000, 60000);
    }

    /**
     * Detiene el timer de tramas sin posición
     */
    private void stopTimerSinPosicion() {
        if (timerTaskSinPosicion != null) {
            timerTaskSinPosicion.cancel();
            timerTaskSinPosicion = null;
        }
        if (timerSinPosicion != null) {
            timerSinPosicion.cancel();
            timerSinPosicion.purge();
            timerSinPosicion = null;
        }
    }

}
