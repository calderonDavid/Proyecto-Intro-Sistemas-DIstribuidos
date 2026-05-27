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
                String idInterseccion = consulta.substring(7); // Remueve "ESTADO_" -> "INT_C5" o "C5"
                
                // NORMALIZACIÓN CRÍTICA: Si el usuario escribió "INT_C5", le quitamos el "INT_" 
                // para dejarlo como "C5", que es como realmente está guardado en MongoDB.
                if (idInterseccion.startsWith("INT_")) {
                    idInterseccion = idInterseccion.substring(4);
                }
                
                Document ultimoSemaforo = colSemaforo.find(Filters.eq("interseccion", idInterseccion))
                        .sort(Sorts.descending("fecha")).first();
                Document ultimoSensor = colEvento.find(Filters.eq("interseccion", idInterseccion))
                        .sort(Sorts.descending("timestamp")).first();

                // Volvemos a colocar el prefijo estético solo para la impresión en pantalla
                StringBuilder sb = new StringBuilder();
                sb.append("\n============================================\n");
                sb.append(" ESTADO ACTUAL: INT_").append(idInterseccion).append("\n");
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

            // NUEVA CONSULTA: CÁLCULO DE THROUGHPUT EN 2 MINUTOS
            } else if (consulta.startsWith("RENDIMIENTO_2MIN|")) {
                String fechaInicioStr = consulta.split("\\|")[1];
                
                try {
                    // 1. Convertir la fecha ingresada a formato manipulable
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    java.time.LocalDateTime startLocal = java.time.LocalDateTime.parse(fechaInicioStr, formatter);
                    
                    // 2. Sumar exactamente 2 minutos de forma matemática
                    java.time.LocalDateTime endLocal = startLocal.plusMinutes(2);
                    
                    // 3. Convertir a Date nativo para que MongoDB genere ObjectIDs
                    java.util.Date startDate = java.util.Date.from(startLocal.atZone(java.time.ZoneId.systemDefault()).toInstant());
                    java.util.Date endDate = java.util.Date.from(endLocal.atZone(java.time.ZoneId.systemDefault()).toInstant());
                    
                    // 4. Crear los ObjectIds límite (buscamos por fecha de inserción)
                    org.bson.types.ObjectId startId = new org.bson.types.ObjectId(startDate);
                    org.bson.types.ObjectId endId = new org.bson.types.ObjectId(endDate);
                    
                    // 5. Filtro entre el rango
                    com.mongodb.client.model.Bson filter = Filters.and(
                        Filters.gte("_id", startId), 
                        Filters.lte("_id", endId)
                    );
                    
                    long countSensores = colEvento.countDocuments(filter);
                    long countSemaforos = colSemaforo.countDocuments(filter);
                    long totalOperaciones = countSensores + countSemaforos;
                    
                    return "\n Ventana analizada: " + startLocal + " HASTA " + endLocal + "\n" +
                           " - Eventos de Sensores guardados: " + countSensores + "\n" +
                           " - Cambios de Semáforo guardados: " + countSemaforos + "\n" +
                           " -> THROUGHPUT TOTAL: " + totalOperaciones + " operaciones persistidas en BD.";
                                       
                } catch (Exception e) {
                    return "Error calculando rendimiento. Verifica que usaste el formato yyyy-MM-dd HH:mm:ss. Detalle: " + e.getMessage();
                }
            }
            return "Consulta no reconocida o formato inválido.";
        } catch (Exception e) {
            return "Error al ejecutar la consulta en la Base de Datos: " + e.getMessage();
        }
    }
}
