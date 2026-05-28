package broker;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

// Clase que recibe los datos de los sensores locales y los reenvía
public class BrokerZMQ {

    public static void main(String[] args) {
        //contexto de ZeroMQ para manejar las conexiones
        try (ZContext context = new ZContext()) {
            
            //socket de tipo suscriptor para escuchar a los sensores locales
            ZMQ.Socket localPub = context.createSocket(SocketType.SUB);
            localPub.bind("tcp://127.0.0.1:5554");
            //nos suscribimos a los mensajes que tengan el tema "TRAFICO"
            localPub.subscribe("TRAFICO".getBytes());

            //creamos un socket de tipo publish para enviar los datos hacia el PC2
            ZMQ.Socket enrutador = context.createSocket(SocketType.PUB);
            enrutador.bind("tcp://*:5555");

            System.out.println("BROKER ZMQ Iniciado y enrutando tráfico.");

            //bucle infinito para mantener el broker activo recibiendo y enviando mensajes
            while (!Thread.currentThread().isInterrupted()) {
            
                //recibimos la primera parte del mensaje que corresponde al (tópico)
                byte[] topic = localPub.recv();
                //recibimos la segunda parte que contiene el evento cifrado del sensor
                byte[] message = localPub.recv();

                // Reenviamos el mensaje hacia el PC2 manteniendo la estructura (tema + mensaje)
                enrutador.sendMore(topic);       
                enrutador.send(message);
            }
        }
    }
}
