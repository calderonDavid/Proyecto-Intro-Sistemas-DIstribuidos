package sensores;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PublisherSensores {

    // Cola concurrente segura para hilos. Limita la capacidad a 1000 para evitar OutOfMemory
    private static final BlockingQueue<String[]> colaEventos = new LinkedBlockingQueue<>(1000);

    public static void main(String[] args) {
        
        List<String> intersecciones = GeneradorIntersecciones.getTodas();
        
        List<Sensor> todosLosSensores = new ArrayList<>();
        
        System.out.println("Posiciones de los sensores");
        System.out.println("Total de intersecciones activas: " + intersecciones.size());
	System.out.println("Intersecciones: " + intersecciones.toString());

        for (String inter : intersecciones) {
            todosLosSensores.addAll(FabricaSensores.crearSensoresPorInterseccion(inter));}

        //Iniciar el hilo Consumidor exclusivo para ZeroMQ
        Thread zmqPublisherThread = new Thread(PublisherSensores::publicarEventosZMQ);
        zmqPublisherThread.start();

        //Pool de Hilos Productores
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

        System.out.println("Iniciando generación multihilo para " + todosLosSensores.size() + " sensores");

        //asigna cada sensor como una tarea concurrente en el Pool
        for (Sensor sensor : todosLosSensores) {
            
            // Cámaras cada 10 seg, GPS y Espiras cada 5 seg.
            
            int intervalo = sensor.getTipo().equals("camara") ? 15 : 10;

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    //Generar evento (hilo del pool)
                    String jsonEvento = sensor.generarEvento();
                    
                    //Encriptar
                    String eventoCifrado = CryptoUtils.encrypt(jsonEvento);
                    
                    // Colocar en la cola [Topico, Mensaje]
                    colaEventos.put(new String[]{"TRAFICO", eventoCifrado});
                    
                    System.out.println("[HILO" + Thread.currentThread().getId() + "] Generado: " + sensor.getSensorId());
                } catch (Exception e) {
                    System.err.println("Error en hilo generador: " + e.getMessage());
                }
            }, 0, intervalo, TimeUnit.SECONDS);
        }
    }

    /*Hilo dedicado exclusivamente a leer la cola y publicar en ZeroMQ para mandarlo al PC2*/
    
    
    private static void publicarEventosZMQ() {
        try (ZContext context = new ZContext()) {
        
            ZMQ.Socket publisher = context.createSocket(SocketType.PUB);
            publisher.bind("tcp://*:5555");
            System.out.println("[ZMQ] Hilo Publisher iniciado en tcp://*:5555...");

            while (!Thread.currentThread().isInterrupted()) {
                // .take() bloquea el hilo hasta que haya un elemento en la cola
                String[] datos = colaEventos.take(); 
                
                publisher.sendMore(datos[0]); // Tópico (TRAFICO)
                publisher.send(datos[1]);     // Mensaje Cifrado
            }
        } catch (Exception e) {
            System.err.println("Error en hilo ZMQ: " + e.getMessage());
        }
    }
}
