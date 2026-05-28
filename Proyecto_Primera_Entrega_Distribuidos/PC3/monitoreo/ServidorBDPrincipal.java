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

        // Iniciamos la conexion local con la base de datos MongoDB y el contexto de red ZMQ
        try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
             ZContext context = new ZContext()) {
            
            // Apuntamos a la base de datos principal y a sus colecciones historicas
            MongoDatabase database = mongoClient.getDatabase("bd_trafico");
            MongoCollection<Document> colSemaforo = database.getCollection("historico_semaforos");
            MongoCollection<Document> colEvento = database.getCollection("historico_sensores");

            // Socket PULL: Dedicado exclusivamente a escuchar y recibir la ingesta masiva de datos que manda el PC2
            ZMQ.Socket pullSocket = context.createSocket(SocketType.PULL);
            pullSocket.bind("tcp://*:5558");

            // Socket REP: Funciona como un servidor de respuestas para atender las consultas que se hacen desde la Consola
            ZMQ.Socket repSocket = context.createSocket(SocketType.REP);
            repSocket.bind("tcp://*:5559");

            // Usamos un Poller para poder escuchar ambos canales (ingesta de datos y consultas) al mismo tiempo sin trabar el hilo
            ZMQ.Poller poller = context.createPoller(2);
            poller.register(pullSocket, ZMQ.Poller.POLLIN);
            poller.register(repSocket, ZMQ.Poller.POLLIN);

            System.out.println(" [BD Principal] Iniciado ZMQ PULL en puerto 5558 (Escucha de eventos)");
            System.out.println(" [BD Principal] Iniciado ZMQ REP en puerto 5559 (Motor de Consultas)");

            // Bucle principal de ejecucion
            while (!Thread.currentThread().isInterrupted()) {
                poller.poll(500);

                // Bloque 1: Ingesta de Datos Continua (Cuando llega algo al puerto 5558)
                if (poller.pollin(0)) {
                    String jsonRecibido = pullSocket.recvStr();
                    if (jsonRecibido != null) {
                        try {
                            // Convertimos el texto JSON a formato BSON para insertarlo en Mongo
                            Document doc = Document.parse(jsonRecibido);
                            // Revisamos una llave clave en el documento para saber a que coleccion mandarlo
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

                // Bloque 2: Respuesta a Consultas de Usuario (Cuando la consola pregunta algo en el puerto 5559)
                if (poller.pollin(1)) {
                    String consulta = repSocket.recvStr();
                    // Pasamos la cadena de texto a un metodo especializado que hace la busqueda real
                    String respuesta = procesarConsulta(consulta, colSemaforo, colEvento);
                    repSocket.send(respuesta);
                }
            }
        } catch (Exception e) {
            System.out.println("Error critico en BD Principal: " + e.getMessage());
        }
    }

    // Metodo core de busqueda: Analiza el texto que mando el usuario y ejecuta la consulta adecuada en MongoDB
    private String procesarConsulta(String consulta, MongoCollection<Document> colSemaforo, MongoCollection<Document> colEvento) {
        try {
            // Caso 1: El usuario quiere saber el estado en tiempo real de una interseccion
            if (consulta.startsWith("ESTADO_")) {
                String idInterseccion = consulta.substring(7); 
                
                // Normalizacion para evitar errores de capa 8 (si el usuario escribe INT_C5 o solo C5, el sistema lo entiende igual)
                if (idInterseccion.startsWith("INT_")) {
                    idInterseccion = idInterseccion.substring(4);
                }
                
                // Buscamos el ultimo registro ordenando de forma descendente por la fecha de ingreso
                Document ultimoSemaforo = colSemaforo.find(Filters.eq("interseccion", idInterseccion))
                        .sort(Sorts.descending("fecha")).first();
                Document ultimoSensor = colEvento.find(Filters.eq("interseccion", idInterseccion))
                        .sort(Sorts.descending("timestamp")).first();

                // Construimos la interfaz de texto que se va a imprimir de vuelta en la consola
                StringBuilder sb = new StringBuilder();
                sb.append("\n============================================\n");
                sb.append(" ESTADO ACTUAL: INT_").append(idInterseccion).append("\n");
                sb.append("============================================\n");
                
                if (ultimoSemaforo != null) {
                    sb.append("[SEMAFORO]\n");
                    sb.append(" Horizontal: ").append(ultimoSemaforo.getString("estado_H")).append("\n");
                    sb.append(" Vertical:   ").append(ultimoSemaforo.getString("estado_V")).append("\n");
                    sb.append(" Razon:      ").append(ultimoSemaforo.getString("razon")).append("\n");
                    sb.append(" Actualizado:").append(ultimoSemaforo.getString("fecha")).append("\n");
                } else {
                    sb.append("[SEMAFORO] Sin registros aun.\n");
                }
                
                sb.append("--------------------------------------------\n");
                
                if (ultimoSensor != null) {
                    sb.append("[TELEMETRIA RECIENTE]\n");
                    sb.append(" Sensor: ").append(ultimoSensor.getString("tipo_sensor")).append("\n");
                    if (ultimoSensor.containsKey("velocidad_promedio")) {
                        sb.append(" Velocidad: ").append(ultimoSensor.getInteger("velocidad_promedio")).append(" km/h\n");
                    }
                    if (ultimoSensor.containsKey("densidad")) {
                        sb.append(" Densidad: ").append(ultimoSensor.getInteger("densidad")).append(" veh/km\n");
                    }
                    if (ultimoSensor.containsKey("volumen")) {
                        sb.append(" Cola (Volumen): ").append(ultimoSensor.getInteger("volumen")).append(" vehiculos\n");
                    }
                } else {
                    sb.append("[TELEMETRIA] Sin registros aun.\n");
                }
                sb.append("============================================\n");
                return sb.toString();

            // Caso 2: El usuario quiere un resumen de todo lo que paso en un rango de fechas
            } else if (consulta.startsWith("HISTORIAL_")) {
                String datos = consulta.substring(10);
                String[] partes = datos.split("\\|"); // Separamos las fechas inicio y fin
                if (partes.length == 2) {
                    String inicio = partes[0];
                    String fin = partes[1];

                    // Usamos conteo de documentos con filtros de mayor-igual (gte) y menor-igual (lte)
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
                           " HISTORIAL DE TRAFICO\n" +
                           "============================================\n" +
                           " Desde: " + inicio + "\n" +
                           " Hasta: " + fin + "\n" +
                           "--------------------------------------------\n" +
                           " Lecturas de Sensores procesadas: " + totalEventos + "\n" +
                           " Cambios de Semaforo ejecutados: " + totalCambios + "\n" +
                           " Olas Verdes (Emergencias) activadas: " + emergencias + "\n" +
                           "============================================\n";
                }

            // Caso 3: Calculo de rendimiento para medir el Throughput del sistema
            } else if (consulta.startsWith("RENDIMIENTO_2MIN|")) {
                String fechaInicioStr = consulta.split("\\|")[1];
                
                try {
                    // Paso 1: Parsear la fecha de texto a un objeto de tiempo de Java
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    java.time.LocalDateTime startLocal = java.time.LocalDateTime.parse(fechaInicioStr, formatter);
                    
                    // Paso 2: Calculamos la ventana exacta sumando dos minutos matematicamente
                    java.time.LocalDateTime endLocal = startLocal.plusMinutes(3);
                    
                    // Paso 3: Convertimos a Date clasico para que MongoDB entienda los limites temporales
                    java.util.Date startDate = java.util.Date.from(startLocal.atZone(java.time.ZoneId.systemDefault()).toInstant());
                    java.util.Date endDate = java.util.Date.from(endLocal.atZone(java.time.ZoneId.systemDefault()).toInstant());
                    
                    // Paso 4: Truco avanzado de MongoDB. Como el "_id" incluye la fecha de creacion, buscamos por los IDs limite
                    org.bson.types.ObjectId startId = new org.bson.types.ObjectId(startDate);
                    org.bson.types.ObjectId endId = new org.bson.types.ObjectId(endDate);
                    
                    // Paso 5: Ejecutamos el filtro para encontrar cuantos registros se guardaron en esa franja de 2 minutos
                    org.bson.conversions.Bson filter = Filters.and(
                        Filters.gte("_id", startId), 
                        Filters.lte("_id", endId)
                    );
                    
                    long countSensores = colEvento.countDocuments(filter);
                    long countSemaforos = colSemaforo.countDocuments(filter);
                    long totalOperaciones = countSensores + countSemaforos;
                    
                    return "\n Ventana analizada: " + startLocal + " HASTA " + endLocal + "\n" +
                           " - Eventos de Sensores guardados: " + countSensores + "\n" +
                           " - Cambios de Semaforo guardados: " + countSemaforos + "\n" +
                           " -> THROUGHPUT TOTAL: " + totalOperaciones + " operaciones persistidas en BD.";
                                       
                } catch (Exception e) {
                    return "Error calculando rendimiento. Verifica que usaste el formato yyyy-MM-dd HH:mm:ss. Detalle: " + e.getMessage();
                }
            }
            return "Consulta no reconocida o formato invalido.";
        } catch (Exception e) {
            return "Error al ejecutar la consulta en la Base de Datos: " + e.getMessage();
        }
    }
}
