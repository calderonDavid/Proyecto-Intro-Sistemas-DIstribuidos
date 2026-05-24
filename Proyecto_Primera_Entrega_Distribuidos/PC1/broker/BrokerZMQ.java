package broker;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class BrokerZMQ {

    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            
            // Escucha a los sensores generados en PublisherSensores Puerto 5554
            
            ZMQ.Socket localPub = context.createSocket(SocketType.SUB);
            localPub.bind("tcp://127.0.0.1:5554");
            localPub.subscribe("TRAFICO".getBytes());

            // PUB Expone los datos para que el PC2 se conecte (Puerto 5555)
            ZMQ.Socket enrutador = context.createSocket(SocketType.PUB);
            
            enrutador.bind("tcp://*:5555");

            System.out.println("BROKER ZMQ Iniciado y enrutando tráfico.");

            //Bucle infinito mandando mensajes de PC1 a PC2
            while (!Thread.currentThread().isInterrupted()) {
            
                // Recibe el tópico y el mensaje cifrado de generadorSensores
                byte[] topic = localPub.recv();
                
                byte[] message = localPub.recv();

                // Lo reenvía inmediatamente hacia el PC2
                enrutador.sendMore(topic);       
                enrutador.send(message);
            }
        }
    }
}
