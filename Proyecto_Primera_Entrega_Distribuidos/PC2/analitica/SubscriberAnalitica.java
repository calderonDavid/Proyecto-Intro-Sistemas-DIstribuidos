package analitica;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.net.InetAddress;

public class SubscriberAnalitica {

    // IPs fijas de los otros nodos del sistema distribuido
    private static final String IP_BROKER_PC1 = "10.43.98.173"; 
    private static final String IP_DB_PC3 = "10.43.99.16"; 

    public static void main(String[] args) {
        System.out.println("Iniciando Nodo PC2 (Analítica y Control)...");

        try (ZContext context = new ZContext()) {
        
            // Arrancamos un hilo independiente para guardar datos en la base de datos local (réplica)
            Thread dbThread = new Thread(new DBHandler());
            dbThread.start();

            // Sockets PUSH para enviar los registros a las bases de datos (local y principal en PC3)
            ZMQ.Socket dbPushSocketLocal = context.createSocket(SocketType.PUSH);
            dbPushSocketLocal.connect("tcp://127.0.0.1:5556");

            ZMQ.Socket dbPushSocketPrincipal = context.createSocket(SocketType.PUSH);
            // Configuramos timeouts para que el hilo no se quede bloqueado si el PC3 se cae
            dbPushSocketPrincipal.setSendTimeOut(2000);
            dbPushSocketPrincipal.setLinger(0);
            dbPushSocketPrincipal.connect("tcp://" + IP_DB_PC3 + ":5558");
            
            // Le pasamos los sockets al analizador para que pueda guardar los resultados
            AnalizadorEventos.inicializar(dbPushSocketLocal, dbPushSocketPrincipal);

            // Socket SUB para escuchar todos los eventos de tráfico que publica el Broker en el PC1
            ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
            subscriber.connect("tcp://" + IP_BROKER_PC1 + ":5555");
            subscriber.subscribe("TRAFICO".getBytes());

            // Socket REP para recibir comandos manuales (como emergencias) desde el PC3
            ZMQ.Socket responderPC3 = context.createSocket(SocketType.REP);
            responderPC3.bind("tcp://*:5557");

            System.out.println("PC2 Conectado y escuchando eventos...");

            // Usamos un Poller para poder escuchar peticiones de varios sockets al mismo tiempo sin bloquear el hilo
            ZMQ.Poller poller = context.createPoller(2);
            poller.register(subscriber, ZMQ.Poller.POLLIN);
            poller.register(responderPC3, ZMQ.Poller.POLLIN);

            boolean pc3EstabaCaido = false;

            while (!Thread.currentThread().isInterrupted()) {
                poller.poll(500); 

                // Bloque 1: Procesar eventos que llegan de los sensores
                if (poller.pollin(0)) {
                    while (true) {
                        byte[] rawTopic = subscriber.recv(ZMQ.DONTWAIT);
                        if (rawTopic == null) {
                            break;
                        }
                        byte[] rawMessage = subscriber.recv();

                        if (rawMessage != null) {
                            try {
				long inicioProcesamiento = System.currentTimeMillis(); // INICIO MEDICIÓN EVENTO
				
				String decrypted = CryptoUtils.decrypt(new String(rawMessage));
				AnalizadorEventos.procesar(decrypted);
				
				long finProcesamiento = System.currentTimeMillis(); // FIN MEDICIÓN EVENTO
				System.out.println("[MÉTRICA LATENCIA] Evento procesado y persistido en BD en: " + (finProcesamiento - inicioProcesamiento) + " ms");
			    } catch (Exception e) {
				System.err.println("Error procesando evento: " + e.getMessage());
			    }
                        }
                    }
                }
                
                // Bloque 2: Procesar comandos manuales de emergencia que envía el PC3
                if (poller.pollin(1)) {
                    String comando = responderPC3.recvStr();
                    
                    System.out.println("[PC2] Comando manual recibido desde PC3: " + comando);

                    if (comando.startsWith("AMBULANCIA_")) {
			    long inicioOlaVerde = System.currentTimeMillis(); // INICIO MEDICIÓN OLA VERDE

			    String eje = comando.split("_")[1]; 
			    
			    java.util.List<String> logsEmergencia = Ciudad.activarOlaVerde(eje);

			    for (String log : logsEmergencia) {
				dbPushSocketLocal.send(log, ZMQ.DONTWAIT);
				dbPushSocketPrincipal.send(log, ZMQ.DONTWAIT);
			    }
			    
			    responderPC3.send("ÉXITO: Ola Verde activada en el eje " + eje);
			    
			    long finOlaVerde = System.currentTimeMillis(); // FIN MEDICIÓN OLA VERDE
			    System.out.println("[MÉTRICA LATENCIA] Ola Verde aplicada y confirmada en: " + (finOlaVerde - inicioOlaVerde) + " ms");
			} else {
			    responderPC3.send("ERROR: Comando no reconocido");
			}
                }

                // Bloque 3: Tolerancia a fallos (Health check del PC3)
                try {
                    boolean pc3Responde = false;
                    try (java.net.Socket socket = new java.net.Socket()) {
                        socket.connect(new java.net.InetSocketAddress(IP_DB_PC3, 5558), 1000);
                        pc3Responde = true;
                    } catch (Exception e) {
                        pc3Responde = false; 
                    }

                    // Lógica para detectar caída y recuperación del nodo principal
                    if (!pc3Responde) {
                        if (!pc3EstabaCaido) {
                            System.out.println("[SISTEMA] Pérdida de conexión con puerto 5558 del PC3. Entrando en Contingencia.");
                        }
                        pc3EstabaCaido = true; 
                    } 
                    else if (pc3EstabaCaido && pc3Responde) {
                        System.out.println("[SISTEMA] Conexión con PC3 restaurada. Ejecutando protocolo Recovery...");
                        
                        // Si el PC3 vuelve a estar en línea, lanzamos el hilo para enviarle los datos atrasados
                        Thread hiloSincronizacion = new Thread(new SincronizadorBD(IP_DB_PC3));
                        hiloSincronizacion.start();
                        
                        pc3EstabaCaido = false; 
                    }

                } catch (Exception e) {
                }
            }
        }
    }
}
