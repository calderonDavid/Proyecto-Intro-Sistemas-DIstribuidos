package sensores;

import java.time.Instant;

public class SensorGPS extends Sensor {

    public SensorGPS(String id, String direccion, String interseccion) {
        super(id, "gps", direccion, interseccion);
    }

    @Override
    public String generarEvento() {
    	int velocidad = (rand.nextInt(100) < 85) ? 11 + rand.nextInt(50) : rand.nextInt(10);
        String nivel = (velocidad < 10) ? "ALTA" : ((velocidad <= 39) ? "NORMAL" : "BAJA");
        //int velocidad = rand.nextInt(50);
        //String nivel = (velocidad < 10) ? "ALTA" : ((velocidad <= 39) ? "NORMAL" : "BAJA");

        return String.format(
            "{\"sensor_id\": \"%s\", \"tipo_sensor\": \"%s\", \"interseccion\": \"%s\", \"direccion\": \"%s\", \"nivel_congestion\": \"%s\", \"velocidad_promedio\": %d, \"timestamp\": \"%s\"}",
            sensorId, tipo, interseccion, direccion, nivel, velocidad, Instant.now().toString()
        );
    }
}
