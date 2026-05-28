package analitica;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Ciudad {
    
    // Estructura en memoria estructurada de forma segura para hilos que almacena todas las intersecciones activas
    private static final ConcurrentHashMap<String, Semaforo[]> semaforos = new ConcurrentHashMap<>();

    // Bloque 1: Registro dinámico de calles. Si una intersección no existe en el mapa, la crea con sus dos semáforos
    public static Semaforo[] obtenerSemaforos(String interseccion) {
        return semaforos.computeIfAbsent(interseccion, k -> {
            Semaforo semH = new Semaforo(k, true, "VERDE"); 
            Semaforo semV = new Semaforo(k, false, "ROJO");
            System.out.println("[CIUDAD] Nueva intersección registrada dinámicamente: " + k);
            return new Semaforo[]{semH, semV};
        });
    }

    // Interfaz intermedia para que el analizador evalúe las reglas de tráfico sobre el par de semáforos correspondiente
    public static boolean procesarReglaInterseccion(String interseccion, boolean hayCongestion, String direccion) {
        Semaforo[] par = obtenerSemaforos(interseccion);
        return par[0].aplicarRegla(hayCongestion, direccion, par[1]);
    }

    // Bloque 2: Lógica de Ola Verde. Recorre las intersecciones y altera los tiempos ante una prioridad manual
    public static List<String> activarOlaVerde(String ejeVia) {
        List<String> logsDeCambio = new ArrayList<>();
        // Revisa si el comando recibido corresponde a una calle horizontal (Letras A-E) o a una avenida vertical
        boolean esFila = ejeVia.matches("[A-E]");        
        String fechaLocal = ZonedDateTime.now(ZoneId.of("America/Bogota")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // Ciclo principal de búsqueda: Identifica qué semáforos están ubicados en la ruta de la emergencia
        for (String idInterseccion : semaforos.keySet()) {
            boolean perteneceAEje = false;
            boolean prioridadHorizontal = false;

            if (esFila && idInterseccion.contains(ejeVia)) {
                perteneceAEje = true;
                prioridadHorizontal = true; 
            } 
            else if (!esFila && idInterseccion.endsWith(ejeVia)) {
                perteneceAEje = true;
                prioridadHorizontal = false; 
            }

            // Si la intersección coincide con la ruta, se fuerza el color verde en el sentido de la vía
            if (perteneceAEje) {
                Semaforo[] par = semaforos.get(idInterseccion);
                if (prioridadHorizontal) {
                    // Sentido horizontal en verde y vertical en rojo por 30 segundos
                    par[0].forzarCambio("VERDE", ZonedDateTime.now(ZoneId.of("America/Bogota")), 30000); 
                    par[1].forzarCambio("ROJO", ZonedDateTime.now(ZoneId.of("America/Bogota")), 30000);  
                } else {
                    // Sentido vertical en verde y horizontal en rojo por 30 segundos
                    par[0].forzarCambio("ROJO", ZonedDateTime.now(ZoneId.of("America/Bogota")), 30000);  
                    par[1].forzarCambio("VERDE", ZonedDateTime.now(ZoneId.of("America/Bogota")), 30000); 
                }

                // Generación manual de la estructura JSON que describe el evento de emergencia
                String log = String.format(
                    "{\"tipo_log\": \"SEMAFORO_EMERGENCIA\", \"interseccion\": \"%s\", \"estado_H\": \"%s\", \"estado_V\": \"%s\", \"razon\": \"Ola Verde (Ambulancia)\", \"fecha\": \"%s\"}",
                    idInterseccion, par[0].getEstado(), par[1].getEstado(), fechaLocal
                );
                logsDeCambio.add(log);
            }
        }
        return logsDeCambio; 
    }
}
