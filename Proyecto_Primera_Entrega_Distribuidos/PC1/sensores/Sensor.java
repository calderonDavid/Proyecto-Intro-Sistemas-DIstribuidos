package sensores;

import java.util.Random;

public abstract class Sensor {

    protected String sensorId;
    protected String tipo;
    protected String direccion;     // "R" o "D"
    protected String interseccion;  // Ej: "C5"
    protected Random rand;

    public Sensor(String sensorId, String tipo, String direccion, String interseccion) {
        this.sensorId = sensorId;
        this.tipo = tipo;
        this.direccion = direccion;
        this.interseccion = interseccion;
        this.rand = new Random();
    }

    public abstract String generarEvento();

    public String getSensorId() { return sensorId; }
    
    public String getTipo() { return tipo; }
    
    public String getDireccion() { return direccion; }
    
    public String getInterseccion() { return interseccion; }
}
