package analitica;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class Semaforo {
    // Atributos de estado del semáforo
    private String interseccion;
    private boolean esHorizontal; 
    private String estado; 
    private ZonedDateTime ultimoCambio; 
    private long tiempoBloqueo;

    // Tiempos parametrizados para operaciones normales o con tráfico
    private static final long TIEMPO_NORMAL = 15000;
    private static final long TIEMPO_CONGESTION = 30000;

    public Semaforo(String interseccion, boolean esHorizontal, String estadoInicial) {
        this.interseccion = interseccion;
        this.esHorizontal = esHorizontal;
        this.estado = estadoInicial;
        this.ultimoCambio = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        this.tiempoBloqueo = TIEMPO_NORMAL;
    }
    
    // Método sincronizado para asegurar la consistencia al alterar el color de un semáforo de forma concurrente
    public synchronized boolean aplicarRegla(boolean hayCongestion, String direccionCongestionada, Semaforo semaforoCompanero) {
        ZonedDateTime ahora = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        long tiempoTranscurrido = Duration.between(ultimoCambio, ahora).toMillis();

        // Control para evitar cambios demasiado rápidos, respeta el tiempo mínimo de bloqueo
        if (tiempoTranscurrido < tiempoBloqueo) {
            return false; 
        }

        boolean eventoEsHorizontal = direccionCongestionada.equals("R");
        String nuevoEstadoPropio;
        String nuevoEstadoCompanero;

        // Lógica para asignar verde al sentido congestionado y penalizar al otro
        if (hayCongestion) {
            if (eventoEsHorizontal) {
                nuevoEstadoPropio = this.esHorizontal ? "VERDE" : "ROJO";
                nuevoEstadoCompanero = this.esHorizontal ? "ROJO" : "VERDE";
            } else {
                nuevoEstadoPropio = this.esHorizontal ? "ROJO" : "VERDE";
                nuevoEstadoCompanero = this.esHorizontal ? "VERDE" : "ROJO";
            }
            this.tiempoBloqueo = TIEMPO_CONGESTION; // Se da más tiempo para descongestionar
        } else {
            // Ciclo normal de cambio de color
            nuevoEstadoPropio = this.estado.equals("VERDE") ? "ROJO" : "VERDE";
            nuevoEstadoCompanero = this.estado.equals("VERDE") ? "VERDE" : "ROJO";
            this.tiempoBloqueo = TIEMPO_NORMAL;
        }

        // Si realmente hubo un cambio de estado, lo aplicamos en memoria y reportamos
        if (!this.estado.equals(nuevoEstadoPropio)) {
            this.estado = nuevoEstadoPropio;
            this.ultimoCambio = ahora;
            
            semaforoCompanero.forzarCambio(nuevoEstadoCompanero, ahora, this.tiempoBloqueo);
            
            String logMsg = hayCongestion ? "(Congestion detectada -> Tiempo extendido a 30s)" : "(Alternancia Normal -> Tiempo fijado a 15s)";
            System.out.println("[CONTROL] Semáforos en " + interseccion + " cambian. H=" 
                + (this.esHorizontal ? this.estado : semaforoCompanero.getEstado()) 
                + " V=" + (!this.esHorizontal ? this.estado : semaforoCompanero.getEstado()) 
                + " " + logMsg);
            
            return true;
        }
        
        // Si no se cambió el estado, simplemente actualizamos la marca de tiempo
        this.ultimoCambio = ahora;
        semaforoCompanero.forzarCambio(semaforoCompanero.getEstado(), ahora, this.tiempoBloqueo);
        return false;
    }

    // Permite cambios abruptos, especialmente útil para emergencias como la Ola Verde
    public synchronized void forzarCambio(String nuevoEstado, ZonedDateTime tiempo, long nuevoTiempoBloqueo) {
        this.estado = nuevoEstado;
        this.ultimoCambio = tiempo;
        this.tiempoBloqueo = nuevoTiempoBloqueo;
    }

    public String getEstado() {
        return estado;
    }
    
    public boolean isEsHorizontal() {
        return esHorizontal;
    }
    
    public ZonedDateTime getUltimoCambio() {
        return ultimoCambio;
    }
}
