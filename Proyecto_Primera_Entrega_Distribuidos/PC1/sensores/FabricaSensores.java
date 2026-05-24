package sensores;

import java.util.ArrayList;
import java.util.List;

public class FabricaSensores {

    public static List<Sensor> crearSensoresPorInterseccion(String interseccion) {
        List<Sensor> sensores = new ArrayList<>();

        // Derecha (R) - Se agrega el parámetro interseccion al constructor
        sensores.add(new SensorCamara("CAM-" + interseccion + "-R", "R", interseccion));
        sensores.add(new SensorEspira("ESP-" + interseccion + "-R", "R", interseccion));
        sensores.add(new SensorGPS("GPS-" + interseccion + "-R", "R", interseccion));

        // Abajo (D) - Se agrega el parámetro interseccion al constructor
        sensores.add(new SensorCamara("CAM-" + interseccion + "-D", "D", interseccion));
        sensores.add(new SensorEspira("ESP-" + interseccion + "-D", "D", interseccion));
        sensores.add(new SensorGPS("GPS-" + interseccion + "-D", "D", interseccion));

        return sensores;
    }
}

