package analitica;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.net.InetAddress;

public class SubscriberAnalitica {

    private static final String IP_BROKER_PC1 = "10.43.98.173"; 
    private static final String IP_DB_PC3 = "10.43.99.16"; 

    public static void main(String[] args) {
        System.out.println("Iniciando Nodo PC2 (Analítica y Control)...");

        try (ZContext context = new ZContext()) {
        
            Thread dbThread = new Thread(new DBHandler());
            dbThread.start();

            ZMQ.Socket dbPushSocketLocal = context.createSocket(SocketType.PUSH);
            dbPushSocketLocal.connect("tcp://127.0.0.1:5556");

            ZMQ.Socket dbPushSocketPrincipal = context.createSocket(SocketType.PUSH);
            dbPushSocketPrincipal.setSendTimeOut(2000);
            dbPushSocketPrincipal.setLinger(0);
            dbPushSocketPrincipal.connect("tcp://" + IP_DB_PC3 + ":5558");
            
            AnalizadorEventos.inicializar(dbPushSocketLocal, dbPushSocketPrincipal);

            ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
            subscriber.connect("tcp://" + IP_BROKER_PC1 + ":5555");
            subscriber.subscribe("TRAFICO".getBytes());

            ZMQ.Socket responderPC3 = context.createSocket(SocketType.REP);
            responderPC3.bind("tcp://*:5557");

            System.out.println("PC2 Conectado y escuchando eventos...");

            ZMQ.Poller poller = context.createPoller(2);
            poller.register(subscriber, ZMQ.Poller.POLLIN);
            poller.register(responderPC3, ZMQ.Poller.POLLIN);

            boolean pc3EstabaCaido = false;

            while (!Thread.currentThread().isInterrupted()) {
                poller.poll(500); 

                if (poller.pollin(0)) {
                    while (true) {
                        byte[] rawTopic = subscriber.recv(ZMQ.DONTWAIT);
                        if (rawTopic == null) {
                            break;
                        }
                        byte[] rawMessage = subscriber.recv();

                        if (rawMessage != null) {
                            try {
                                String decrypted = CryptoUtils.decrypt(new String(rawMessage));
                                AnalizadorEventos.procesar(decrypted);
                            } catch (Exception e) {
                                System.err.println("Error procesando evento: " + e.getMessage());
                            }
                        }
                    }
                }
                
                if (poller.pollin(1)) {
                    String comando = responderPC3.recvStr();
                    System.out.println("[PC2] Comando manual recibido desde PC3: " + comando);

                    if (comando.startsWith("AMBULANCIA_")) {

                        String eje = comando.split("_")[1]; 
                        
                        java.util.List<String> logsEmergencia = Ciudad.activarOlaVerde(eje);

                        for (String log : logsEmergencia) {
                            dbPushSocketLocal.send(log, ZMQ.DONTWAIT);
                            dbPushSocketPrincipal.send(log, ZMQ.DONTWAIT);
                        }
                        
                        responderPC3.send("ÉXITO: Ola Verde activada en el eje " + eje);
                    } else {
                        responderPC3.send("ERROR: Comando no reconocido");
                    }
                }

                try {

                    boolean pc3Responde = InetAddress.getByName(IP_DB_PC3).isReachable(1000);

                    if (!pc3Responde) {
                        if (!pc3EstabaCaido) {
                            System.out.println("Pérdida de red con PC3.");
                        }
                        pc3EstabaCaido = true; 
                    } 
                    else if (pc3EstabaCaido && pc3Responde) {
                        System.out.println("[SISTEMA] Red con PC3 restaurada. Ejecutando protocolo Recovery...");
                        Thread hiloSincronizacion = new Thread(new SincronizadorBD(dbPushSocketPrincipal));
                        hiloSincronizacion.start();
                        
                        pc3EstabaCaido = false; 
                    }
                } catch (Exception e) {

                }
            }
        }
    }
}
