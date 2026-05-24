package sensores;

import java.time.Instant;

public class SensorCamara extends Sensor {

    public SensorCamara(String id, String direccion, String interseccion) {
        super(id, "camara", direccion, interseccion);
    }

    @Override
    public String generarEvento() {
    	int volumen = (rand.nextInt(100) < 85) ? rand.nextInt(15) : 16 + rand.nextInt(10);
        int velocidad = 10 + rand.nextInt(40);
        
        //int volumen = rand.nextInt(20);
        //int velocidad = 10 + rand.nextInt(40);

        return String.format(
            "{\"sensor_id\": \"%s\", \"tipo_sensor\": \"%s\", \"interseccion\": \"%s\", \"direccion\": \"%s\", \"volumen\": %d, \"velocidad_promedio\": %d, \"timestamp\": \"%s\"}",
            sensorId, tipo, interseccion, direccion, volumen, velocidad, Instant.now().toString()
        );
    }
}

