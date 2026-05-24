package analitica;

import org.zeromq.ZMQ;
import java.time.Instant;
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
            int vpCamara = extraerValorInt(json, "velocidad_promedio");
            if (vpCamara > 0) metricas.Vp = vpCamara; 
        } else if (tipo.equals("espira_inductiva")) {
            metricas.D = extraerValorInt(json, "vehiculos_contados"); 
        } else if (tipo.equals("gps")) {
            metricas.Vp = extraerValorInt(json, "velocidad_promedio");
        }
        
        boolean hayCongestion = false;

        if (metricas.Q >= 5 || metricas.Vp <= 35 || metricas.D >= 20) {
            hayCongestion = true;
        } 
        else {
            hayCongestion = false;
        }

        Semaforo semaforo = Ciudad.obtenerSemaforo(interseccion);
        boolean cambioRealizado = semaforo.aplicarRegla(hayCongestion, direccion);
        
        if (cambioRealizado) {
            String razon = hayCongestion ? "Congestion detectada" : "Alternancia Normal";
            String logSemaforo = String.format(
                "{\"tipo_log\": \"SEMAFORO\", \"interseccion\": \"%s\", \"estado_nuevo\": \"%s\", \"razon\": \"%s\", \"fecha\": \"%s\"}",
                interseccion, semaforo.getEstado(), razon, Instant.now().toString()
            );
            
            guardarEnBD(logSemaforo);
        }
    }
    
    private static void guardarEnBD(String json) {
        if (dbPrincipal != null && dbLocal != null) {
            boolean enviadoPC3 = dbPrincipal.send(json, ZMQ.DONTWAIT);
            
            if (!enviadoPC3) {
                System.out.println("[ALERTA] Timeout en BD Principal (PC3). Operando con BD Réplica.");
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
        int Vp = 40; 
        int D = 0;
    }
}
