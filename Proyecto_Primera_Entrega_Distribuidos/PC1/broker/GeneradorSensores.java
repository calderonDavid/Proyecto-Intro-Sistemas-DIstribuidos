package broker;

import sensores.CryptoUtils;
import sensores.FabricaSensores;
import sensores.GeneradorIntersecciones;
import sensores.Sensor;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GeneradorSensores {

    public static void main(String[] args) {
    
        List<String> intersecciones = GeneradorIntersecciones.getTodas();
        List<Sensor> todosLosSensores = new ArrayList<>();

        for (String inter : intersecciones) {
            todosLosSensores.addAll(FabricaSensores.crearSensoresPorInterseccion(inter));
        }

        ZContext context = new ZContext();
        
        ZMQ.Socket publisherLocal = context.createSocket(SocketType.PUB);
        publisherLocal.connect("tcp://127.0.0.1:5554");

        //Pool de hilos para generar los eventos concurrentemente
        
       ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
        
       System.out.println("Iniciando " + todosLosSensores.size() + " sensores hacia el Broker local");
        
        // Asignar las tareas de cada sensor
        for (Sensor sensor : todosLosSensores) {
            int intervalo = sensor.getTipo().equals("camara") ? 10 : 5;

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    String jsonEvento = sensor.generarEvento();
                    String eventoCifrado = CryptoUtils.encrypt(jsonEvento);
                    
                    // Bloque sincronizado porque el socket ZMQ será usado por varios hilos a la vez
                    synchronized (publisherLocal) {
                        publisherLocal.sendMore("TRAFICO"); 
                        publisherLocal.send(eventoCifrado);
                    }
                    
                    System.out.println("[HILO-" + Thread.currentThread().getId() + "] Enviado a Broker: " + sensor.getSensorId());
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                }
            }, 0, intervalo, TimeUnit.SECONDS);
        }
    }
}
