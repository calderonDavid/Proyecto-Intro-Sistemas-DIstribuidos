package analitica;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.zeromq.ZMQ;

public class SincronizadorBD implements Runnable {
    private ZMQ.Socket pushPrincipal;
    
    public SincronizadorBD(ZMQ.Socket pushPrincipal) {
        this.pushPrincipal = pushPrincipal;
    }

    @Override
    public void run() {
        System.out.println("[RECOVERY] Iniciando volcado de datos asíncrono hacia PC3...");
        
        try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017")) {
            MongoDatabase database = mongoClient.getDatabase("bd_trafico_replica");
            MongoCollection<Document> colSensores = database.getCollection("historico_sensores");
            MongoCollection<Document> colSemaforos = database.getCollection("historico_semaforos");
            
            sincronizarColeccion(colSensores, "Sensores");
            sincronizarColeccion(colSemaforos, "Semáforos");
            
        } catch (Exception e) {
            System.err.println("[ERROR RECOVERY] Fallo crítico: " + e.getMessage());
        }
        
        System.out.println("[RECOVERY] Volcado de datos finalizado exitosamente.");
    }

    private void sincronizarColeccion(MongoCollection<Document> coleccion, String nombre) throws InterruptedException {
        Document query = new Document("sincronizado", false);
        
        try (MongoCursor<Document> cursor = coleccion.find(query).iterator()) {
            int count = 0;
            while (cursor.hasNext()) {
                Document doc = cursor.next();
              
                doc.remove("sincronizado");
                String json = doc.toJson();
               
                boolean enviado = pushPrincipal.send(json, ZMQ.DONTWAIT);
                
                if (enviado) {
                    coleccion.updateOne(Filters.eq("_id", doc.getObjectId("_id")), Updates.set("sincronizado", true));
                    count++;
                } else {
                    System.out.println("[RECOVERY] La conexión a PC3 se volvió a caer durante el volcado.");
                    break; 
                }
                
                Thread.sleep(10);
            }
            System.out.println("[RECOVERY] Sincronizados " + count + " registros atrasados de " + nombre);
        }
    }
}
