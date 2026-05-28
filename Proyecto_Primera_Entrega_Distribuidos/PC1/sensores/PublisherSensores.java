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

//clase principal para la generación y publicación de los eventos de los sensores
public class PublisherSensores {

    // Una cola para almacenar los eventos generados antes de enviarlos. Soporta hasta 2000 elementos
    private static final BlockingQueue<String[]> colaEventos = new LinkedBlockingQueue<>(2000);

    public static void main(String[] args) {
        
        // Configuraciones iniciales por defecto
        boolean esMultihilo = true;
        int escenario = 1;

        //evaluar si se manda para multihilo
        if (args.length >= 2) {
            esMultihilo = args[0].equalsIgnoreCase("multihilo");
            escenario = Integer.parseInt(args[1]);
        }

        // Definimos la cantidad de sensores y los tiempos de envío
        int cantidadSensoresPorTipo = (escenario == 2) ? 2 : 1;
        int intervaloSegundos = (escenario == 2) ? 5 : 10;
        
        // Si usamos multihilo, habilitamos 10 hilos o todo corre en 1 hilo
        int cantidadHilos = esMultihilo ? 10 : 1; 

        System.out.println("=== PRUEBA DE RENDIMIENTO INICIADA ===");
        System.out.println("Arquitectura: " + (esMultihilo ? "MULTIHILO ("+cantidadHilos+" hilos)" : "SECUENCIAL (Single-Thread)"));
        System.out.println("Carga: Escenario " + escenario + " (" + cantidadSensoresPorTipo + " sensor(es) por tipo cada " + intervaloSegundos + " seg)");
        System.out.println("======================================\n");

        // Creamos todos los sensores usando las intersecciones generadas
        List<String> intersecciones = GeneradorIntersecciones.getTodas();
        List<Sensor> todosLosSensores = new ArrayList<>();

        for (String inter : intersecciones) {
            todosLosSensores.addAll(FabricaSensores.crearSensoresPorInterseccion(inter, cantidadSensoresPorTipo));
        }

        //iniciamos un hilo en segundo plano dedicado exclusivamente a enviar los datos acumulados en la cola
        Thread zmqPublisherThread = new Thread(PublisherSensores::publicarEventosZMQ);
        zmqPublisherThread.start();

        //configuramos los hilos para que los sensores empiecen a trabajar
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(cantidadHilos);

        for (Sensor sensor : todosLosSensores) {
            //cada sensor va a generar un evento cada 'x' segundos según el escenario
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    String jsonEvento = sensor.generarEvento();
                    String eventoCifrado = CryptoUtils.encrypt(jsonEvento);
                    
                    // Metemos el evento en la cola para que el publicador lo envíe luego
                    colaEventos.put(new String[]{"TRAFICO", eventoCifrado});
                    
                    System.out.println("[PC1] Evento generado -> " + sensor.getSensorId());
                } catch (Exception e) {
                    System.err.println("Error en hilo generador: " + e.getMessage());
                }
            }, 0, intervaloSegundos, TimeUnit.SECONDS);
        }
    }

    // función que para sacar los eventos de la cola y los manda mediante ZeroMQ
    private static void publicarEventosZMQ() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket publisher = context.createSocket(SocketType.PUB);
            publisher.bind("tcp://*:5555");

            // Bucle infinito que funciona mientras el programa esté encendido
            while (!Thread.currentThread().isInterrupted()) {
                // Toma un evento de la cola (si la cola está vacía, espera automáticamente)
                String[] datos = colaEventos.take(); 
                publisher.sendMore(datos[0]); 
                publisher.send(datos[1]);     
            }
        } catch (Exception e) {
            System.err.println("Error en hilo ZMQ: " + e.getMessage());
        }
    }
}
