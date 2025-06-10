package com.alexdev1011.plugins.backgroundlocation;

import java.io.Serializable;

public class Permiso implements Serializable {
    private static final long serialVersionUID = 1L;
    public String nombre;
    public int estado;
    public String codigo;

    public Permiso(String nombre, int estado, String codigo) {
        this.nombre = nombre;
        this.estado = estado;
        this.codigo = codigo;
    }

    @Override
    public String toString() {
        return "{" +
                "\"nombre\":\"" + nombre + "\"," +
                "\"estado\":" + estado + "," +
                "\"codigo\":\"" + codigo + "\"" +
                "}";
    }
}