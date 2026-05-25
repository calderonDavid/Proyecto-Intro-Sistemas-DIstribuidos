package analitica;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.zeromq.ZMQ;
import java.util.concurrent.ConcurrentHashMap;

public class AnalizadorEventos {

    private static ZMQ.Socket dbLocal;
    private static ZMQ.Socket dbPrincipal;

    private static final ConcurrentHashMap<String, MetricasTrafico> estadoTrafico = new ConcurrentHashMap<>();
    public static void inicializar(ZMQ.Socket pushLocal, ZMQ.Socket pushPrincipal) {
        dbLocal = pushLocal;
        dbPrincipal = pushPrincipal;
    }

    public static void procesar(String json) {

        guardarEnBD(json);

        String interseccion = extraerValorString(json, "interseccion");
        String direccion = extraerValorString(json, "direccion");
        String tipo = extraerValorString(json, "tipo_sensor");
        
        String claveEstado = interseccion + "-" + direccion;
        estadoTrafico.putIfAbsent(claveEstado, new MetricasTrafico());
        MetricasTrafico metricas = estadoTrafico.get(claveEstado);
        

        if (tipo.equals("camara")) {
            metricas.Q = extraerValorInt(json, "volumen"); 
        } else if (tipo.equals("espira_inductiva")) {
            metricas.V = extraerValorInt(json, "vehiculos_contados"); 
        } else if (tipo.equals("gps")) {
            metricas.Vp = extraerValorInt(json, "velocidad_promedio"); 
            metricas.D = extraerValorInt(json, "densidad"); 
        }
     
        boolean hayCongestion = false;

        if (metricas.Q >= 15 && metricas.Vp <= 15) {
            hayCongestion = true;
        }
        if (metricas.V >= 40 && metricas.D >= 45) {
            hayCongestion = true;
        } 
        if (metricas.Q >= 20 && metricas.V <= 3) {
            hayCongestion = true;
        }

        boolean cambioRealizado = Ciudad.procesarReglaInterseccion(interseccion, hayCongestion, direccion);
        
        if (cambioRealizado) {
            String razon = hayCongestion ? "Congestion detectada" : "Alternancia Normal";
            String fechaLocal = ZonedDateTime.now(ZoneId.of("America/Bogota")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            // USAMOS EL MÉTODO EN PLURAL PARA OBTENER EL ARREGLO
            Semaforo[] par = Ciudad.obtenerSemaforos(interseccion);

            String logSemaforo = String.format(
                "{\"tipo_log\": \"SEMAFORO\", \"interseccion\": \"%s\", \"estado_H\": \"%s\", \"estado_V\": \"%s\", \"razon\": \"%s\", \"fecha\": \"%s\"}",
                interseccion, par[0].getEstado(), par[1].getEstado(), razon, fechaLocal
            );
            
            guardarEnBD(logSemaforo);
        }
    } // <-- Aquí termina tu método procesar
    
    
    private static void guardarEnBD(String json) {
        if (dbPrincipal != null && dbLocal != null) {
            boolean enviadoPC3 = dbPrincipal.send(json, ZMQ.DONTWAIT); // Envío no bloqueante
            
            if (!enviadoPC3) {
                System.out.println("[ALERTA] Timeout en BD Principal (PC3), operando con BD Réplica.");
            }
            dbLocal.send(json, ZMQ.DONTWAIT);
        }
    }

    private static String extraerValorString(String json, String clave) {
        String patron = "\"" + clave + "\": \"";
        int inicio = json.indexOf(patron);
        if (inicio == -1) return "";
        inicio += patron.length();
        int fin = json.indexOf("\"", inicio);
        return json.substring(inicio, fin);
    }

    private static int extraerValorInt(String json, String clave) {
        String patron = "\"" + clave + "\": ";
        int inicio = json.indexOf(patron);
        if (inicio == -1) return 0;
        inicio += patron.length();
        int fin = json.indexOf(",", inicio);
        if (fin == -1) fin = json.indexOf("}", inicio);
        try {
            return Integer.parseInt(json.substring(inicio, fin).trim());
        } catch (Exception e) {
            return 0;
        }
    }


    private static class MetricasTrafico {
        int Q = 0;   
        int V = 0;   
        int D = 0;   
        int Vp = 40; 
    }
}
