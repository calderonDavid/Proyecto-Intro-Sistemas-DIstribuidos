package analitica;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.zeromq.ZMQ;
import java.util.concurrent.ConcurrentHashMap;

public class AnalizadorEventos {

    // Sockets para enviar logs a las bases de datos
    private static ZMQ.Socket dbLocal;
    private static ZMQ.Socket dbPrincipal;

    // Memoria temporal segura para hilos que guarda cómo está el tráfico por intersección y dirección
    private static final ConcurrentHashMap<String, MetricasTrafico> estadoTrafico = new ConcurrentHashMap<>();
    
    public static void inicializar(ZMQ.Socket pushLocal, ZMQ.Socket pushPrincipal) {
        dbLocal = pushLocal;
        dbPrincipal = pushPrincipal;
    }

    public static void procesar(String json) {
        // Todo dato que entra se guarda directamente en BD
        guardarEnBD(json);

        // Extracción manual de los valores del JSON sin usar librerías externas pesadas
        String interseccion = extraerValorString(json, "interseccion");
        String direccion = extraerValorString(json, "direccion");
        String tipo = extraerValorString(json, "tipo_sensor");
        
        // Creamos una llave única para saber exactamente de qué calle estamos hablando
        String claveEstado = interseccion + "-" + direccion;
        estadoTrafico.putIfAbsent(claveEstado, new MetricasTrafico());
        MetricasTrafico metricas = estadoTrafico.get(claveEstado);
        
        // Actualizamos la métrica correcta dependiendo de qué tipo de sensor envió el dato
        if (tipo.equals("camara")) {
            metricas.Q = extraerValorInt(json, "volumen"); 
        } else if (tipo.equals("espira_inductiva")) {
            metricas.V = extraerValorInt(json, "vehiculos_contados"); 
        } else if (tipo.equals("gps")) {
            metricas.Vp = extraerValorInt(json, "velocidad_promedio"); 
            metricas.D = extraerValorInt(json, "densidad"); 
        }
     
        boolean hayCongestion = false;

        // Reglas de negocio para determinar si hay un trancón cruzando las distintas variables
        if (metricas.Q >= 15 && metricas.Vp <= 15) {
            hayCongestion = true;
        }
        if (metricas.V >= 40 && metricas.D >= 45) {
            hayCongestion = true;
        } 
        if (metricas.Q >= 20 && metricas.V <= 3) {
            hayCongestion = true;
        }

        // Le pasamos el resultado a la Ciudad para que decida si debe cambiar el semáforo
        boolean cambioRealizado = Ciudad.procesarReglaInterseccion(interseccion, hayCongestion, direccion);
        
        // Si el semáforo cambió de color, armamos el log y lo disparamos a las bases de datos
        if (cambioRealizado) {
            String razon = hayCongestion ? "Congestion detectada" : "Alternancia Normal";
            String fechaLocal = ZonedDateTime.now(ZoneId.of("America/Bogota")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            Semaforo[] par = Ciudad.obtenerSemaforos(interseccion);

            String logSemaforo = String.format(
                "{\"tipo_log\": \"SEMAFORO\", \"interseccion\": \"%s\", \"estado_H\": \"%s\", \"estado_V\": \"%s\", \"razon\": \"%s\", \"fecha\": \"%s\"}",
                interseccion, par[0].getEstado(), par[1].getEstado(), razon, fechaLocal
            );
            
            guardarEnBD(logSemaforo);
        }
    } 
    
    // Método auxiliar para enviar el dato a ambas bases de datos asegurando que no bloquee el sistema (DONTWAIT)
    private static void guardarEnBD(String json) {
        if (dbPrincipal != null && dbLocal != null) {
            boolean enviadoPC3 = dbPrincipal.send(json, ZMQ.DONTWAIT); 
            
            if (!enviadoPC3) {
                System.out.println("[ALERTA] Timeout en BD Principal (PC3), operando con BD Réplica.");
            }
            dbLocal.send(json, ZMQ.DONTWAIT);
        }
    }

    // Métodos auxiliares para parsear el JSON como texto plano
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
    // Clase interna para llevar los conteos agrupados
    private static class MetricasTrafico {
        int Q = 0;   
        int V = 0;   
        int D = 0;   
        int Vp = 40; 
    }
}
