package monitoreo;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;

// Importaciones de MongoDB
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

public class ServidorBDPrincipal implements Runnable {
    @Override
    public void run() {

        try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
        
             ZContext context = new ZContext()) {
            
            MongoDatabase database = mongoClient.getDatabase("bd_trafico");
            
            MongoCollection<Document> colSemaforo = database.getCollection("historico_semaforo");
            MongoCollection<Document> colEvento = database.getCollection("historico_sensores");

            ZMQ.Socket pullSocket = context.createSocket(SocketType.PULL);
            pullSocket.bind("tcp://*:5558");

            System.out.println(" [BD Principal], iniciado ZMQ en puerto 5558");

            while (!Thread.currentThread().isInterrupted()) {
                String jsonRecibido = pullSocket.recvStr();
                
                try {
                    Document doc = Document.parse(jsonRecibido);
                    String tipoLog = doc.getString("tipo_log");

		    if ("SEMAFORO".equals(tipoLog)) {
		           colSemaforo.insertOne(doc);
		            System.out.println(" [Mongo] Guardado en Semaforos: " + jsonRecibido);
		   } else {
		   	colEvento.insertOne(doc);
		        System.out.println(" [Mongo] Guardado en Evento: " + jsonRecibido);
		    }
                  

                } catch (Exception e) {
                    System.out.println(" Error parseando o guardando dato [Mongo PC3] : " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("Error crítico en BD Principal: " + e.getMessage());
        }
    }
}
