package analitica;

public class Semaforo {
    private String interseccion;
    private String estado; 
    private long ultimoCambio; 
    private long tiempoBloqueo;

    private static final long TIEMPO_NORMAL = 15000;
    private static final long TIEMPO_CONGESTION = 30000;

    public Semaforo(String interseccion) {
        this.interseccion = interseccion;
        this.estado = "HORIZONTAL";
        this.ultimoCambio = System.currentTimeMillis();
        this.tiempoBloqueo = TIEMPO_NORMAL;
    }

    public synchronized boolean aplicarRegla(boolean hayCongestion, String direccionCongestionada) {
        long tiempoActual = System.currentTimeMillis();
        long tiempoTranscurrido = tiempoActual - ultimoCambio;

        if (tiempoTranscurrido < tiempoBloqueo) {
            return false; 
        }

        String nuevoEstado;

        if (hayCongestion) {
            nuevoEstado = direccionCongestionada.equals("R") ? "HORIZONTAL" : "VERTICAL";
            this.tiempoBloqueo = TIEMPO_CONGESTION;
        } else {
            nuevoEstado = this.estado.equals("HORIZONTAL") ? "VERTICAL" : "HORIZONTAL";
            this.tiempoBloqueo = TIEMPO_NORMAL;
        }

        if (!this.estado.equals(nuevoEstado)) {
            this.estado = nuevoEstado;
            this.ultimoCambio = tiempoActual;
            
            String logMsg = hayCongestion ? "(Congestion detectada -> Tiempo extendido a 30s)" : "(Alternancia Normal -> Tiempo fijado a 15s)";
                
            System.out.println("[CONTROL] Semaforo " + interseccion + " cambia a: " + estado + " " + logMsg);
            return true;
        }

        this.ultimoCambio = tiempoActual;
        return false;
    }
    public String getEstado() {
        return estado;
    }
}
