package sensores;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class SensorCamara extends Sensor {

    public SensorCamara(String id, String direccion, String interseccion) {
        super(id, "camara", direccion, interseccion);
    }

    @Override
    public String generarEvento() {
        int volumen = (rand.nextInt(100) < 85) ? rand.nextInt(15) : 16 + rand.nextInt(10);
        
        String timestampLocal = ZonedDateTime.now(ZoneId.of("America/Bogota"))
                                             .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        return String.format(
            "{\"sensor_id\": \"%s\", \"tipo_sensor\": \"%s\", \"interseccion\": \"%s\", \"direccion\": \"%s\", \"volumen\": %d, \"timestamp\": \"%s\"}",
            sensorId, tipo, interseccion, direccion, volumen, timestampLocal
        );
    }
}
