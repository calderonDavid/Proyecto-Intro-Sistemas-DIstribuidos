package sensores;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GeneradorIntersecciones {

    private static final List<String> intersecciones = new ArrayList<>();
    private static final Random rand = new Random();

    static {
        int filas = 5;
        int columnas = 5;

        for (int i = 0; i < filas; i++) {
            for (int j = 0; j < columnas; j++) {
		char fila = (char) ('A' + i); 
		String id = fila + String.valueOf(j + 1);
		intersecciones.add(id);
            }
        }
    }

    public static String obtenerRandom() {
        return intersecciones.get(rand.nextInt(intersecciones.size()));
    }

    public static List<String> getTodas() {
        return intersecciones;
    }
}
