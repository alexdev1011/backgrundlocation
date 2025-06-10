package com.alexdev1011.plugins.backgroundlocation;

import java.io.Serializable;

public class Motivo  implements Serializable {
    public int code;
    public String message;
    public boolean sending = true;
    Motivo( int code, String message , boolean sending ){
        this.code = code;
        this.sending = sending;
        this.message = message;
    }
}
