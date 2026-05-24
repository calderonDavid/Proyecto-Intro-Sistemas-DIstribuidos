package analitica;

import java.util.concurrent.ConcurrentHashMap;

public class Ciudad {
    
    private static final ConcurrentHashMap<String, Semaforo> semaforos = new ConcurrentHashMap<>();

    public static Semaforo obtenerSemaforo(String interseccion) {
        semaforos.computeIfAbsent(interseccion, k -> new Semaforo(interseccion));
        return semaforos.get(interseccion);
    }
}
