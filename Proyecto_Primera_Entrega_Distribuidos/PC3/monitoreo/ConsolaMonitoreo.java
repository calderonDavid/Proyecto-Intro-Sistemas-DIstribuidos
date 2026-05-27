package monitoreo;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import java.util.Scanner;

public class ConsolaMonitoreo {
    
    // IP del PC2 para enviar los comandos de la ambulancia
    private static final String IP_PC2 = "10.43.99.141"; 

    public static void main(String[] args) {
    
        new Thread(new ServidorBDPrincipal()).start();
        
        try (Scanner scanner = new Scanner(System.in)) {
            
            System.out.println("\n=== SISTEMA DISTRIBUIDO DE TRÁFICO ===");
            System.out.println("--- MÓDULO DE SEGURIDAD ---");
            boolean autenticado = false;
            int intentos = 0;
            
            while (!autenticado && intentos < 3) {
                System.out.print("Usuario: ");
                String usuario = scanner.nextLine();
                System.out.print("Contraseña: ");
                String password = scanner.nextLine();
                
                if (usuario.equals("admin") && password.equals("1234")) {
                    autenticado = true;
                    System.out.println("\n[EXITO] Autenticación correcta.");
                } else {
                    intentos++;
                    System.out.println("[ERROR] Credenciales incorrectas. Intentos restantes: " + (3 - intentos));
                }
            }
            
            if (!autenticado) {
                System.out.println("[ALERTA] Acceso bloqueado. Apagando consola.");
                System.exit(0);
            }

            try (ZContext context = new ZContext()) {
                
                // Socket REQ para enviar comandos al PC2 (Control de Semáforos)
                ZMQ.Socket reqPC2 = context.createSocket(SocketType.REQ);
                reqPC2.setReceiveTimeOut(3000); 
                reqPC2.connect("tcp://" + IP_PC2 + ":5557");

                // NUEVO: Socket REQ para enviar consultas a la BD Local (PC3)
                ZMQ.Socket reqDB = context.createSocket(SocketType.REQ);
                reqDB.setReceiveTimeOut(3000); 
                reqDB.connect("tcp://127.0.0.1:5559");

                while (true) {
                    System.out.println("\n=== PANEL DE MANDO (PC3) ===");
                    System.out.println("1. Priorizar Ambulancia (Ola Verde)");
                    System.out.println("2. Realizar consulta a la Base de Datos");
                    System.out.println("3. Medir Rendimiento de Ingesta (2 minutos)");
                    System.out.println("4. Salir");
                    System.out.print("Opción: ");
                    
                    String opcion = scanner.nextLine();

                    if (opcion.equals("1")) {
                        System.out.print("Ingrese el Eje de la Vía para la Ola Verde (Ej. Fila 'C' o Columna '5'): ");
                        String eje = scanner.nextLine().toUpperCase();
                        
                        String comando = "AMBULANCIA_" + eje;
                        System.out.println("Enviando orden de emergencia al sistema distribuido...");
                        reqPC2.send(comando);

                        String respuesta = reqPC2.recvStr();
                        System.out.println(respuesta != null ? ">> Respuesta del PC2: " + respuesta : ">> [ALERTA] Red caída.");

                    } else if (opcion.equals("2")) {
                        
                        // Opciones de Consultas requeridas en la rúbrica
                        System.out.println("\n--- MÓDULO DE CONSULTAS ---");
                        System.out.println("A. Consultar Estado Actual de Intersección");
                        System.out.println("B. Consultar Historial por Rango de Tiempo");
                        System.out.print("Elija una opción (A/B): ");
                        String subOpcion = scanner.nextLine().toUpperCase();

                        if (subOpcion.equals("A")) {
                            System.out.print("Ingrese el ID de la Intersección (Ej. INT_C5): ");
                            String idInterseccion = scanner.nextLine().toUpperCase();
                            
                            System.out.println("Consultando la Base de Datos...");
                            reqDB.send("ESTADO_" + idInterseccion);
                            
                            // Espera la respuesta síncrona
                            String respuesta = reqDB.recvStr();
                            System.out.println(respuesta != null ? respuesta : "[ERROR] La BD no responde.");
                            
                        } else if (subOpcion.equals("B")) {
                            System.out.println("NOTA: Use formato ISO (Ej. 2026-05-24T10:00:00)");
                            System.out.print("Fecha/Hora de Inicio: ");
                            String inicio = scanner.nextLine();
                            System.out.print("Fecha/Hora de Fin: ");
                            String fin = scanner.nextLine();
                            
                            System.out.println("Generando reporte histórico...");
                            // Concatenamos con el separador | para facilitar el split en el servidor
                            reqDB.send("HISTORIAL_" + inicio + "|" + fin);
                            
                            // Espera la respuesta síncrona
                            String respuesta = reqDB.recvStr();
                            System.out.println(respuesta != null ? respuesta : "[ERROR] La BD no responde.");
                            
                        } else {
                            System.out.println("Opción no válida.");
                        }
                        
                    } else if (opcion.equals("3")) {
                        
                        // NUEVA SECCIÓN DE RENDIMIENTO AUTOMÁTICO
                        System.out.println("\n=== PRUEBA DE RENDIMIENTO (2 MINUTOS) ===");
                        System.out.println("Ingrese la fecha y hora exacta de INICIO de la prueba.");
                        System.out.println("Formato estricto (AÑO-MES-DIA HORA:MINUTO:SEGUNDO) -> Ej: 2026-05-27 18:00:00");
                        System.out.print("Fecha inicio: ");
                        String fechaInicioRendimiento = scanner.nextLine();

                        // Enviamos el comando especial al servidor BD
                        String requestRendimiento = "RENDIMIENTO_2MIN|" + fechaInicioRendimiento;
                        reqDB.send(requestRendimiento);

                        // Esperamos la respuesta automatizada
                        String replyRendimiento = reqDB.recvStr();
                        System.out.println("\n>> RESULTADOS DEL EXPERIMENTO:");
                        System.out.println(replyRendimiento != null ? replyRendimiento : "[ERROR] El motor de BD no respondió.");
                        System.out.println("-----------------------------------------\n");

                    } else if (opcion.equals("4")) {
                        System.out.println("Cerrando sesión y apagando consola PC3...");
                        System.exit(0);
                    } else {
                        System.out.println("Opción no válida.");
                    }
                }
            }
        }
    }
}
