package sensores;

import java.util.ArrayList;
import java.util.List;

public class FabricaSensores {

    public static List<Sensor> crearSensoresPorInterseccion(String interseccion, int cantidadPorTipo) {
        List<Sensor> sensores = new ArrayList<>();

        // Iteramos según la cantidad solicitada (Escenario 1 = 1 vez, Escenario 2 = 2 veces)
        for (int i = 1; i <= cantidadPorTipo; i++) {
            // Derecha (R) - Añadimos el número 'i' al ID para que no se repitan los nombres
            sensores.add(new SensorCamara("CAM-" + interseccion + "-R-" + i, "R", interseccion));
            sensores.add(new SensorEspira("ESP-" + interseccion + "-R-" + i, "R", interseccion));
            sensores.add(new SensorGPS("GPS-" + interseccion + "-R-" + i, "R", interseccion));

            // Abajo (D)
            sensores.add(new SensorCamara("CAM-" + interseccion + "-D-" + i, "D", interseccion));
            sensores.add(new SensorEspira("ESP-" + interseccion + "-D-" + i, "D", interseccion));
            sensores.add(new SensorGPS("GPS-" + interseccion + "-D-" + i, "D", interseccion));
        }

        return sensores;
    }
}
