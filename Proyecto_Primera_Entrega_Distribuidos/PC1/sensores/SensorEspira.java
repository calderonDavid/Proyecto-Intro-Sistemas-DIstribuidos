package sensores;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class SensorEspira extends Sensor {

    public SensorEspira(String id, String direccion, String interseccion) {
        super(id, "espira_inductiva", direccion, interseccion);
    }

    @Override
    public String generarEvento() {
        int vehiculos = (rand.nextInt(100) < 85) ? rand.nextInt(10) : 11 + rand.nextInt(10);
        
        ZonedDateTime tiempoFin = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        
        ZonedDateTime tiempoInicio = tiempoFin.minusSeconds(30);

        String timestampInicio = tiempoInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String timestampFin = tiempoFin.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        return String.format(
            "{\"sensor_id\": \"%s\", \"tipo_sensor\": \"%s\", \"interseccion\": \"%s\", \"direccion\": \"%s\", \"vehiculos_contados\": %d, \"intervalo_segundos\": 30, \"timestamp_inicio\": \"%s\", \"timestamp_fin\": \"%s\"}",
            sensorId, tipo, interseccion, direccion, vehiculos, timestampInicio, timestampFin
        );
    }
}
