package analitica;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class DBHandler implements Runnable {
    // Parámetros de conexión a la base local
    private static final String MONGO_URI = "mongodb://localhost:27017";
    private static final String DATABASE_NAME = "bd_trafico_replica";

    @Override
    public void run() {
        try (MongoClient mongoClient = MongoClients.create(MONGO_URI);
             ZContext context = new ZContext()) {

            MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
            MongoCollection<Document> colSensores = database.getCollection("historico_sensores");
            MongoCollection<Document> colSemaforos = database.getCollection("historico_semaforos");

            // Socket PULL dedicado para recibir lo que le envían internamente desde la clase Subscriber
            ZMQ.Socket pullSocket = context.createSocket(SocketType.PULL);
            pullSocket.bind("tcp://*:5556"); 

            System.out.println("[BD REPLICA] Persistencia iniciada en MongoDB local...");

            // Bucle infinito escuchando datos para insertar en la BD
            while (!Thread.currentThread().isInterrupted()) {
                String jsonRecibido = pullSocket.recvStr();
                if (jsonRecibido != null) {
                    try {
                        // Parseamos el JSON a formato BSON para poder guardarlo en Mongo
                        Document doc = Document.parse(jsonRecibido);
                        
                        // Determinamos a qué colección va la data dependiendo de sus campos
                        if (doc.containsKey("tipo_sensor")) {
                            colSensores.insertOne(doc);
                        } else if (doc.containsKey("estado_nuevo")) {
                            colSemaforos.insertOne(doc);
                        }
                    } catch (Exception e) {
                        System.err.println("Error BSON: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error MongoDB: " + e.getMessage());
        }
    }
}
