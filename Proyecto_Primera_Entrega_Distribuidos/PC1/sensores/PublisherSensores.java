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

    private static final BlockingQueue<String[]> colaEventos = new LinkedBlockingQueue<>(2000);

    public static void main(String[] args) {
        
        // Variables por defecto
        boolean esMultihilo = true;
        int escenario = 1;

        // Lectura de parámetros al ejecutar en consola
        if (args.length >= 2) {
            esMultihilo = args[0].equalsIgnoreCase("multihilo");
            escenario = Integer.parseInt(args[1]);
        }

        // Configuración exigida por la Tabla 1 del proyecto
        int cantidadSensoresPorTipo = (escenario == 2) ? 2 : 1;
        int intervaloSegundos = (escenario == 2) ? 5 : 10;
        
        // LA MAGIA DEL RENDIMIENTO: 10 hilos concurrentes vs 1 solo hilo cuello de botella
        int cantidadHilos = esMultihilo ? 10 : 1; 

        System.out.println("=== PRUEBA DE RENDIMIENTO INICIADA ===");
        System.out.println("Arquitectura: " + (esMultihilo ? "MULTIHILO ("+cantidadHilos+" hilos)" : "SECUENCIAL (Single-Thread)"));
        System.out.println("Carga: Escenario " + escenario + " (" + cantidadSensoresPorTipo + " sensor(es) por tipo cada " + intervaloSegundos + " seg)");
        System.out.println("======================================\n");

        List<String> intersecciones = GeneradorIntersecciones.getTodas();
        List<Sensor> todosLosSensores = new ArrayList<>();

        for (String inter : intersecciones) {
            todosLosSensores.addAll(FabricaSensores.crearSensoresPorInterseccion(inter, cantidadSensoresPorTipo));
        }

        Thread zmqPublisherThread = new Thread(PublisherSensores::publicarEventosZMQ);
        zmqPublisherThread.start();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(cantidadHilos);

        for (Sensor sensor : todosLosSensores) {
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    String jsonEvento = sensor.generarEvento();
                    String eventoCifrado = CryptoUtils.encrypt(jsonEvento);
                    colaEventos.put(new String[]{"TRAFICO", eventoCifrado});
                    
                    System.out.println("[PC1] Evento generado -> " + sensor.getSensorId());
                } catch (Exception e) {
                    System.err.println("Error en hilo generador: " + e.getMessage());
                }
            }, 0, intervaloSegundos, TimeUnit.SECONDS);
        }
    }

    private static void publicarEventosZMQ() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket publisher = context.createSocket(SocketType.PUB);
            publisher.bind("tcp://*:5555");

            while (!Thread.currentThread().isInterrupted()) {
                String[] datos = colaEventos.take(); 
                publisher.sendMore(datos[0]); 
                publisher.send(datos[1]);     
            }
        } catch (Exception e) {
            System.err.println("Error en hilo ZMQ: " + e.getMessage());
        }
    }
}
