package sensores;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class SensorGPS extends Sensor {

    public SensorGPS(String id, String direccion, String interseccion) {
        super(id, "gps", direccion, interseccion);
    }

    @Override
    public String generarEvento() {
        int velocidad = (rand.nextInt(100) < 85) ? 11 + rand.nextInt(50) : rand.nextInt(10);
        int densidad = (velocidad < 20) ? 25 + rand.nextInt(15) : rand.nextInt(15); 
        
        String timestampLocal = ZonedDateTime.now(ZoneId.of("America/Bogota"))
                                             .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        return String.format(
            "{\"sensor_id\": \"%s\", \"tipo_sensor\": \"%s\", \"interseccion\": \"%s\", \"direccion\": \"%s\", \"densidad\": %d, \"velocidad_promedio\": %d, \"timestamp\": \"%s\"}",
            sensorId, tipo, interseccion, direccion, densidad, velocidad, timestampLocal
        );
    }
}
