package sensores;

import java.util.ArrayList;
import java.util.List;

// patron factory
public class FabricaSensores {

    // Función que devuelve una lista de sensores listos para una intersección específica
    public static List<Sensor> crearSensoresPorInterseccion(String interseccion, int cantidadPorTipo) {
        List<Sensor> sensores = new ArrayList<>();

        // Creamos la cantidad de sensores solicitada
        for (int i = 1; i <= cantidadPorTipo; i++) {
            
            // Se usa el número 'i' en el ID para diferenciarlos si hay más de uno (right)
            sensores.add(new SensorCamara("CAM-" + interseccion + "-R-" + i, "R", interseccion));
            sensores.add(new SensorEspira("ESP-" + interseccion + "-R-" + i, "R", interseccion));
            sensores.add(new SensorGPS("GPS-" + interseccion + "-R-" + i, "R", interseccion));

            // Sensores para la vía que va hacia abajo (D de Down)
            sensores.add(new SensorCamara("CAM-" + interseccion + "-D-" + i, "D", interseccion));
            sensores.add(new SensorEspira("ESP-" + interseccion + "-D-" + i, "D", interseccion));
            sensores.add(new SensorGPS("GPS-" + interseccion + "-D-" + i, "D", interseccion));
        }

        return sensores;
    }
}
