package analitica;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.ArrayList;
import java.util.List;

public class SincronizadorBD implements Runnable {
    
    private String ipPC3;

    public SincronizadorBD(String ipPC3) {
        this.ipPC3 = ipPC3;
    }

    @Override
    public void run() {
        try (ZContext context = new ZContext();
             MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017")) {
            
            ZMQ.Socket recoverySocket = context.createSocket(SocketType.PUSH);
            // 1. TIMEOUT: Si PC3 se desconecta, esperará 2 segundos y cancelará el envío limpiamente
            recoverySocket.setSendTimeOut(2000);
            // 2. LINGER: Le da 2 segundos a ZMQ para vaciar la memoria antes de cerrar el hilo
            recoverySocket.setLinger(2000); 
            recoverySocket.connect("tcp://" + ipPC3 + ":5558");
            
            MongoDatabase database = mongoClient.getDatabase("bd_trafico_replica");
            MongoCollection<Document> colSemaforos = database.getCollection("historico_semaforos");
            MongoCollection<Document> colSensores = database.getCollection("historico_sensores");

            sincronizarColeccion(colSemaforos, "Semáforos", recoverySocket);
            sincronizarColeccion(colSensores, "Sensores", recoverySocket);
            
            System.out.println("[RECOVERY] Volcado de datos finalizado exitosamente.");
            
        } catch (Exception e) {
            System.err.println("Error crítico en sincronización: " + e.getMessage());
        }
    }

    private void sincronizarColeccion(MongoCollection<Document> coleccion, String nombre, ZMQ.Socket recoverySocket) {
        Document query = new Document("sincronizado", false);
        // Lista para guardar los IDs y hacer actualizaciones masivas
        List<ObjectId> loteActualizacion = new ArrayList<>();
        
        try (MongoCursor<Document> cursor = coleccion.find(query).iterator()) {
            int count = 0;
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                ObjectId idLocal = doc.getObjectId("_id");
                
                doc.remove("_id");
                doc.remove("sincronizado");
                
                String json = doc.toJson();
                
                // Usamos send normal (bloqueante hasta el límite del TimeOut de 2 segs)
                boolean enviado = recoverySocket.send(json);
                
                if (enviado) {
                    loteActualizacion.add(idLocal);
                    count++;
                } else {
                    System.out.println("[RECOVERY] Red caída durante el volcado de " + nombre + ". Abortando lote.");
                    break; // Cortamos de raíz si PC3 se fue, sin hacer spam
                }
                
                // 3. BULK UPDATE: Actualizamos Mongo de 500 en 500 (Velocidad extrema)
                if (loteActualizacion.size() >= 500) {
                    coleccion.updateMany(Filters.in("_id", loteActualizacion), Updates.set("sincronizado", true));
                    loteActualizacion.clear();
                }
            }
            
            // Guardamos el sobrante que no haya alcanzado a completar 500
            if (!loteActualizacion.isEmpty()) {
                coleccion.updateMany(Filters.in("_id", loteActualizacion), Updates.set("sincronizado", true));
            }
            
            if (count > 0) {
                 System.out.println("[RECOVERY] Sincronizados " + count + " registros atrasados de " + nombre);
            }
        }
    }
}
