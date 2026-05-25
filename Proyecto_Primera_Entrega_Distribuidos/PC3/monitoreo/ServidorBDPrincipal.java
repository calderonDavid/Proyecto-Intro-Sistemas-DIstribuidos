package monitoreo;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

public class ServidorBDPrincipal implements Runnable {
    @Override
    public void run() {

        try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
             ZContext context = new ZContext()) {
            
            MongoDatabase database = mongoClient.getDatabase("bd_trafico");
            MongoCollection<Document> colSemaforo = database.getCollection("historico_semaforos");
            MongoCollection<Document> colEvento = database.getCollection("historico_sensores");

            // Socket PULL para recibir y guardar datos (En background)
            ZMQ.Socket pullSocket = context.createSocket(SocketType.PULL);
            pullSocket.bind("tcp://*:5558");

            // NUEVO: Socket REP para responder a las consultas de la consola
            ZMQ.Socket repSocket = context.createSocket(SocketType.REP);
            repSocket.bind("tcp://*:5559");

            ZMQ.Poller poller = context.createPoller(2);
            poller.register(pullSocket, ZMQ.Poller.POLLIN);
            poller.register(repSocket, ZMQ.Poller.POLLIN);

            System.out.println(" [BD Principal] Iniciado ZMQ PULL en puerto 5558 (Escucha de eventos)");
            System.out.println(" [BD Principal] Iniciado ZMQ REP en puerto 5559 (Motor de Consultas)");

            while (!Thread.currentThread().isInterrupted()) {
                poller.poll(500);

                // 1. Ingesta de Datos Continua
                if (poller.pollin(0)) {
                    String jsonRecibido = pullSocket.recvStr();
                    if (jsonRecibido != null) {
                        try {
                            Document doc = Document.parse(jsonRecibido);
                            if (doc.containsKey("tipo_log")) {
                                colSemaforo.insertOne(doc);
                            } else if (doc.containsKey("tipo_sensor")) {
                                colEvento.insertOne(doc);
                            }
                        } catch (Exception e) {
                            System.out.println(" Error guardando dato [Mongo PC3] : " + e.getMessage());
                        }
                    }
                }

                // 2. Respuesta a Consultas de Usuario (REQ/REP)
                if (poller.pollin(1)) {
                    String consulta = repSocket.recvStr();
                    String respuesta = procesarConsulta(consulta, colSemaforo, colEvento);
                    repSocket.send(respuesta);
                }
            }
        } catch (Exception e) {
            System.out.println("Error crítico en BD Principal: " + e.getMessage());
        }
    }

    // Lógica para evaluar lo que pide el usuario y buscarlo en MongoDB
    private String procesarConsulta(String consulta, MongoCollection<Document> colSemaforo, MongoCollection<Document> colEvento) {
        try {
            // Consulta de Estado de Intersección (Puntual)
            if (consulta.startsWith("ESTADO_")) {
                String idInterseccion = consulta.substring(7); // Remueve "ESTADO_"
                
                Document ultimoSemaforo = colSemaforo.find(Filters.eq("interseccion", idInterseccion))
                        .sort(Sorts.descending("fecha")).first();
                Document ultimoSensor = colEvento.find(Filters.eq("interseccion", idInterseccion))
                        .sort(Sorts.descending("timestamp")).first();

                StringBuilder sb = new StringBuilder();
                sb.append("\n============================================\n");
                sb.append(" ESTADO ACTUAL: ").append(idInterseccion).append("\n");
                sb.append("============================================\n");
                
                if (ultimoSemaforo != null) {
                    sb.append("[SEMÁFORO]\n");
                    sb.append(" Horizontal: ").append(ultimoSemaforo.getString("estado_H")).append("\n");
                    sb.append(" Vertical:   ").append(ultimoSemaforo.getString("estado_V")).append("\n");
                    sb.append(" Razón:      ").append(ultimoSemaforo.getString("razon")).append("\n");
                    sb.append(" Actualizado:").append(ultimoSemaforo.getString("fecha")).append("\n");
                } else {
                    sb.append("[SEMÁFORO] Sin registros aún.\n");
                }
                
                sb.append("--------------------------------------------\n");
                
                if (ultimoSensor != null) {
                    sb.append("[TELEMETRÍA RECIENTE]\n");
                    sb.append(" Sensor: ").append(ultimoSensor.getString("tipo_sensor")).append("\n");
                    if (ultimoSensor.containsKey("velocidad_promedio")) {
                        sb.append(" Velocidad: ").append(ultimoSensor.getInteger("velocidad_promedio")).append(" km/h\n");
                    }
                    if (ultimoSensor.containsKey("densidad")) {
                        sb.append(" Densidad: ").append(ultimoSensor.getInteger("densidad")).append(" veh/km\n");
                    }
                    if (ultimoSensor.containsKey("volumen")) {
                        sb.append(" Cola (Volumen): ").append(ultimoSensor.getInteger("volumen")).append(" vehículos\n");
                    }
                } else {
                    sb.append("[TELEMETRÍA] Sin registros aún.\n");
                }
                sb.append("============================================\n");
                return sb.toString();

            // Consulta de Historial de Tráfico (Rango de tiempo)
            } else if (consulta.startsWith("HISTORIAL_")) {
                String datos = consulta.substring(10);
                String[] partes = datos.split("\\|"); // Usa | como separador
                if (partes.length == 2) {
                    String inicio = partes[0];
                    String fin = partes[1];

                    // Cuenta todo lo que pasó en ese rango de tiempo
                    long totalEventos = colEvento.countDocuments(Filters.and(
                            Filters.gte("timestamp", inicio),
                            Filters.lte("timestamp", fin)
                    ));

                    long totalCambios = colSemaforo.countDocuments(Filters.and(
                            Filters.gte("fecha", inicio),
                            Filters.lte("fecha", fin)
                    ));
                    
                    long emergencias = colSemaforo.countDocuments(Filters.and(
                            Filters.gte("fecha", inicio),
                            Filters.lte("fecha", fin),
                            Filters.eq("tipo_log", "SEMAFORO_EMERGENCIA")
                    ));

                    return "\n============================================\n" +
                           " HISTORIAL DE TRÁFICO\n" +
                           "============================================\n" +
                           " Desde: " + inicio + "\n" +
                           " Hasta: " + fin + "\n" +
                           "--------------------------------------------\n" +
                           " Lecturas de Sensores procesadas: " + totalEventos + "\n" +
                           " Cambios de Semáforo ejecutados: " + totalCambios + "\n" +
                           " Olas Verdes (Emergencias) activadas: " + emergencias + "\n" +
                           "============================================\n";
                }
            }
            return "Consulta no reconocida o formato inválido.";
        } catch (Exception e) {
            return "Error al ejecutar la consulta en la Base de Datos: " + e.getMessage();
        }
    }
}
