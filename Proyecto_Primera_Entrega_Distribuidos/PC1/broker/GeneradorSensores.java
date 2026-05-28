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

//clase encargada de generar los eventos de todos los sensores y enviarlos al Broker local
public class GeneradorSensores {

    public static void main(String[] args) {
    
        //variable para definir si usamos múltiples hilos o no
        boolean esMultihilo = true;
        
        //verificamos si al ejecutar el programa se pasó el parámetro para activar multihilo
        if (args.length >= 1) {
            esMultihilo = args[0].equalsIgnoreCase("multihilo");
        }
        
        //si el usuario indicó multihilo usamos 10, de lo contrario 1 hilo
        int cantidadHilos = esMultihilo ? 10 : 1;

        //obtenemos la lista de todas las intersecciones de la ciudad
        List<String> intersecciones = GeneradorIntersecciones.getTodas();
        List<Sensor> todosLosSensores = new ArrayList<>();

        //recorremos cada intersección para crear sus respectivos sensores y agregarlos a la lista
        for (String inter : intersecciones) {
            todosLosSensores.addAll(FabricaSensores.crearSensoresPorInterseccion(inter));
        }

        //inicializar el contexto de ZeroMQ para manejar las conexiones de red
        ZContext context = new ZContext();
        
        //crear el socket publicador y nos conectamos al puerto 5554
        ZMQ.Socket publisherLocal = context.createSocket(SocketType.PUB);
        publisherLocal.connect("tcp://127.0.0.1:5554");

        //pool de hilos para generar los eventos concurrentemente
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(cantidadHilos);
        
        System.out.println("Iniciando " + todosLosSensores.size() + " sensores hacia el Broker local");
        System.out.println("Ejecutando con: " + cantidadHilos + " hilo(s)");
        
        // Asignar las tareas a cada sensor creado
        for (Sensor sensor : todosLosSensores) {
            
            //las cámaras envian información cada 10 segundos, los demás cada 5
            int intervalo = sensor.getTipo().equals("camara") ? 10 : 5;

            // Programamos el envío automático de datos de este sensor
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    // Se crea el dato de tráfico y se cifra por seguridad
                    String jsonEvento = sensor.generarEvento();
                    String eventoCifrado = CryptoUtils.encrypt(jsonEvento);
                    
                    // Bloque sincronizado porque el socket ZMQ será usado por varios hilos a la vez
                    // Esto evita que los mensajes choquen o se corrompan al enviarse al mismo tiempo
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
