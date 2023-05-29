package com.alexdev1011.plugins.backgroundlocation;

import java.io.Serializable;

public class ServiceSettings implements Serializable {
    public String notificationTitle = "Estamos obteniendo tu ubicación.";
    public Boolean panico = false;
    public String notificationContent = "Puedes apagar el servicio dentro de la aplicación.";
    public int minS = 60;
    public int grados =  70;
    public String userID = "sinDefinir";
    public String urlRequests = "http://captura1.akercontrol.com:9007";
    public Boolean inBG = true;
    public String authorization = "";
    public int distanceFilter = 90;
    public Boolean storageLocal = true;
    public Boolean stopService = false;
    public String imei ="";

    public Boolean appStarted = false;
    public ServiceSettings(){

    }
}
