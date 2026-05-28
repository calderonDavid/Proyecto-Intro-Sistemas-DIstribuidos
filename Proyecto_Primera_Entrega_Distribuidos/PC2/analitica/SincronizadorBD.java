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
            
            // Socket exclusivo para volcar los datos de recuperación
            ZMQ.Socket recoverySocket = context.createSocket(SocketType.PUSH);
            recoverySocket.setSendTimeOut(2000); // Previene bloqueos si la red es intermitente
            recoverySocket.setLinger(2000);      // Tiempo de gracia para vaciar la memoria antes de cerrar
            recoverySocket.connect("tcp://" + ipPC3 + ":5558");
            
            // Conexión a las colecciones locales
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

    // Método que busca los datos pendientes y los envía al nodo principal
    private void sincronizarColeccion(MongoCollection<Document> coleccion, String nombre, ZMQ.Socket recoverySocket) {
        // Solo buscamos documentos que no han sido sincronizados aún
        Document query = new Document("sincronizado", false);
        List<ObjectId> loteActualizacion = new ArrayList<>();
        
        try (MongoCursor<Document> cursor = coleccion.find(query).iterator()) {
            int count = 0;
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                ObjectId idLocal = doc.getObjectId("_id");
                
                // Limpiamos los campos internos antes de mandarlo por la red
                doc.remove("_id");
                doc.remove("sincronizado");
                
                String json = doc.toJson();
                boolean enviado = recoverySocket.send(json);
                
                if (enviado) {
                    loteActualizacion.add(idLocal);
                    count++;
                } else {
                    System.out.println("[RECOVERY] Red caída durante el volcado de " + nombre + ". Abortando lote.");
                    break; 
                }
                
                // Optimizamos las consultas a la base de datos actualizando por bloques de 500 registros
                if (loteActualizacion.size() >= 500) {
                    coleccion.updateMany(Filters.in("_id", loteActualizacion), Updates.set("sincronizado", true));
                    loteActualizacion.clear();
                }
            }
            
            // Procesamos los últimos registros que quedaron pendientes
            if (!loteActualizacion.isEmpty()) {
                coleccion.updateMany(Filters.in("_id", loteActualizacion), Updates.set("sincronizado", true));
            }
        }
    }
}
