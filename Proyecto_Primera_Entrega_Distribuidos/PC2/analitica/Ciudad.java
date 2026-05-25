package analitica;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Ciudad {
    
    private static final ConcurrentHashMap<String, Semaforo[]> semaforos = new ConcurrentHashMap<>();

    public static Semaforo[] obtenerSemaforos(String interseccion) {
        return semaforos.computeIfAbsent(interseccion, k -> {
            Semaforo semH = new Semaforo(k, true, "VERDE"); 
            Semaforo semV = new Semaforo(k, false, "ROJO");
            System.out.println("[CIUDAD] Nueva intersección registrada dinámicamente: " + k);
            return new Semaforo[]{semH, semV};
        });
    }

    public static boolean procesarReglaInterseccion(String interseccion, boolean hayCongestion, String direccion) {
        Semaforo[] par = obtenerSemaforos(interseccion);
        return par[0].aplicarRegla(hayCongestion, direccion, par[1]);
    }

    public static List<String> activarOlaVerde(String ejeVia) {
        List<String> logsDeCambio = new ArrayList<>();
        boolean esFila = ejeVia.matches("[A-E]");        
        String fechaLocal = ZonedDateTime.now(ZoneId.of("America/Bogota")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

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

            if (perteneceAEje) {
                Semaforo[] par = semaforos.get(idInterseccion);
                if (prioridadHorizontal) {
                    par[0].forzarCambio("VERDE", ZonedDateTime.now(ZoneId.of("America/Bogota")), 30000); 
                    par[1].forzarCambio("ROJO", ZonedDateTime.now(ZoneId.of("America/Bogota")), 30000);  
                } else {
                    par[0].forzarCambio("ROJO", ZonedDateTime.now(ZoneId.of("America/Bogota")), 30000);  
                    par[1].forzarCambio("VERDE", ZonedDateTime.now(ZoneId.of("America/Bogota")), 30000); 
                }

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
