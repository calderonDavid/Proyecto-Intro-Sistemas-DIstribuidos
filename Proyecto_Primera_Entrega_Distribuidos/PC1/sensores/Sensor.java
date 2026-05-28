package sensores;

import java.util.Random;

// Clase bpara la plantilla de sensores
public abstract class Sensor {


    protected String sensorId;      // Identificador único del dispositivo
    protected String tipo;          // Define si es cámara, espira o GPS
    protected String direccion;     // Sentido del flujo vehicular (R o D)
    protected String interseccion;  // Lugar donde está ubicado el sensor 
    protected Random rand;          // Generador de números aleatorios para simular variaciones en el tráfico

    // Constructor
    public Sensor(String sensorId, String tipo, String direccion, String interseccion) {
        this.sensorId = sensorId;
        this.tipo = tipo;
        this.direccion = direccion;
        this.interseccion = interseccion;
        this.rand = new Random();
    }

    // Método que cada tipo de sensor
    public abstract String generarEvento();

    // Métodos para consultar la información del sensor
    public String getSensorId() { return sensorId; }
    public String getTipo() { return tipo; }
    public String getDireccion() { return direccion; }
    public String getInterseccion() { return interseccion; }
}
