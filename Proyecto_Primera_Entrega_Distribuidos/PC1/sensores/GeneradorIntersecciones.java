package sensores;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Clase para el mapa de cruces (intersecciones) de la ciudad
public class GeneradorIntersecciones {

    // Lista estática para guardar las intersecciones generadas
    private static final List<String> intersecciones = new ArrayList<>();
    private static final Random rand = new Random();

    // Bloque de código que se ejecuta una sola vez al cargar la clase para crear la matriz de intersecciones
    static {
        // Configuramos una cuadrícula de 5 filas por 5 columnas
        int filas = 5;
        int columnas = 5;

        // Generamos nombres como A1, A2..
        for (int i = 0; i < filas; i++) {
            for (int j = 0; j < columnas; j++) {
                // Convertimos el índice de la fila a su letra correspondiente
                char fila = (char) ('A' + i); 
                String id = fila + String.valueOf(j + 1);
                intersecciones.add(id);
            }
        }
    }

    //devuelve el nombre de una intersección al azar
    public static String obtenerRandom() {
        return intersecciones.get(rand.nextInt(intersecciones.size()));
    }

    // Retorna la lista completa de intersecciones
    public static List<String> getTodas() {
        return intersecciones;
    }
}
