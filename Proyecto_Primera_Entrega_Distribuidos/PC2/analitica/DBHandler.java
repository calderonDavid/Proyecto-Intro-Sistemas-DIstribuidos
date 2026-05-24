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
    private static final String MONGO_URI = "mongodb://localhost:27017";
    private static final String DATABASE_NAME = "bd_trafico_replica";

    @Override
    public void run() {
        try (MongoClient mongoClient = MongoClients.create(MONGO_URI);
             ZContext context = new ZContext()) {

            MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
            MongoCollection<Document> colSensores = database.getCollection("historico_sensores");
            MongoCollection<Document> colSemaforos = database.getCollection("historico_semaforos");

            ZMQ.Socket pullSocket = context.createSocket(SocketType.PULL);

            pullSocket.bind("tcp://*:5556"); 

            System.out.println("[BD REPLICA] Persistencia iniciada en MongoDB local...");

            while (!Thread.currentThread().isInterrupted()) {
                String jsonRecibido = pullSocket.recvStr();
                if (jsonRecibido != null) {
                    try {
                        Document doc = Document.parse(jsonRecibido);
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
