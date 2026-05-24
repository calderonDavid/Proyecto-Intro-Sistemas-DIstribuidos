package sensores;

import java.time.Instant;

public class SensorEspira extends Sensor {

    public SensorEspira(String id, String direccion, String interseccion) {
        super(id, "espira_inductiva", direccion, interseccion);
    }

    @Override
    public String generarEvento() {
        int vehiculos = (rand.nextInt(100) < 85) ? rand.nextInt(10) : 11 + rand.nextInt(10);
        
        //int vehiculos = rand.nextInt(15);
        String timestampInicio = Instant.now().minusSeconds(30).toString();
        String timestampFin = Instant.now().toString();

        return String.format(
            "{\"sensor_id\": \"%s\", \"tipo_sensor\": \"%s\", \"interseccion\": \"%s\", \"direccion\": \"%s\", \"vehiculos_contados\": %d, \"intervalo_segundos\": 30, \"timestamp_inicio\": \"%s\", \"timestamp_fin\": \"%s\"}",
            sensorId, tipo, interseccion, direccion, vehiculos, timestampInicio, timestampFin
        );
    }
}
