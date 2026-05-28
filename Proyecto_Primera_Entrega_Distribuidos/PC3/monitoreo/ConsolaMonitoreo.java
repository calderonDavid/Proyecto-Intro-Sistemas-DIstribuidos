package monitoreo;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;

public class ConsolaMonitoreo {
    
    // Configuracion de red importante: Apunta directo al PC2 que controla la logica de los semaforos
    private static final String IP_PC2 = "10.43.99.141"; 

    public static void main(String[] args) {
    
        // Al arrancar la consola, lanzamos un hilo independiente para prender el Servidor de Base de Datos
        new Thread(new ServidorBDPrincipal()).start();
        
        try (Scanner scanner = new Scanner(System.in)) {
            
            System.out.println("\n=== SISTEMA DISTRIBUIDO DE TRAFICO ===");
            System.out.println("--- MODULO DE SEGURIDAD ---");
            
            // Bloque de seguridad actualizado: Ahora depende de un archivo externo
            boolean autenticado = false;
            int intentos = 0;
            
            while (!autenticado && intentos < 3) {
                System.out.print("Usuario: ");
                String usuario = scanner.nextLine();
                System.out.print("Contrasena: ");
                String password = scanner.nextLine();
                
                // En lugar de comparar textos quemados, llamamos a nuestro nuevo metodo validador
                if (validarEnArchivo(usuario, password)) {
                    autenticado = true;
                    System.out.println("\n[EXITO] Autenticacion correcta.");
                } else {
                    intentos++;
                    System.out.println("[ERROR] Credenciales incorrectas. Intentos restantes: " + (3 - intentos));
                }
            }
            
            // Si agota los intentos, abortamos la ejecucion del programa por seguridad
            if (!autenticado) {
                System.out.println("[ALERTA] Acceso bloqueado. Apagando consola.");
                System.exit(0);
            }

            try (ZContext context = new ZContext()) {
                
                // Socket REQ 1: Conexion externa. Envia las instrucciones manuales de la ambulancia directo al PC2
                ZMQ.Socket reqPC2 = context.createSocket(SocketType.REQ);
                reqPC2.setReceiveTimeOut(3000); 
                reqPC2.connect("tcp://" + IP_PC2 + ":5557");

                // Socket REQ 2: Conexion interna. Envia las consultas textuales al hilo de BD
                ZMQ.Socket reqDB = context.createSocket(SocketType.REQ);
                reqDB.setReceiveTimeOut(3000); 
                reqDB.connect("tcp://127.0.0.1:5559");

                // Menu interactivo infinito
                while (true) {
                    System.out.println("\n=== PANEL DE MANDO (PC3) ===");
                    System.out.println("1. Priorizar Ambulancia (Ola Verde)");
                    System.out.println("2. Realizar consulta a la Base de Datos");
                    System.out.println("3. Medir Rendimiento de Ingesta (2 minutos)");
                    System.out.println("4. Salir");
                    System.out.print("Opcion: ");
                    
                    String opcion = scanner.nextLine();

                    if (opcion.equals("1")) {
                        System.out.print("Ingrese el Eje de la Via para la Ola Verde (Ej. Fila 'C' o Columna '5'): ");
                        String eje = scanner.nextLine().toUpperCase();
                        
                        String comando = "AMBULANCIA_" + eje;
                        System.out.println("Enviando orden de emergencia al sistema distribuido...");
                        reqPC2.send(comando); 

                        String respuesta = reqPC2.recvStr();
                        System.out.println(respuesta != null ? ">> Respuesta del PC2: " + respuesta : ">> [ALERTA] Red caida.");

                    } else if (opcion.equals("2")) {
                        
                        System.out.println("\n--- MODULO DE CONSULTAS ---");
                        System.out.println("A. Consultar Estado Actual de Interseccion");
                        System.out.println("B. Consultar Historial por Rango de Tiempo");
                        System.out.print("Elija una opcion (A/B): ");
                        String subOpcion = scanner.nextLine().toUpperCase();

                        if (subOpcion.equals("A")) {
                            System.out.print("Ingrese el ID de la Interseccion (Ej. INT_C5): ");
                            String idInterseccion = scanner.nextLine().toUpperCase();
                            
                            System.out.println("Consultando la Base de Datos...");
                            reqDB.send("ESTADO_" + idInterseccion);
                            
                            String respuesta = reqDB.recvStr(); 
                            System.out.println(respuesta != null ? respuesta : "[ERROR] La BD no responde.");
                            
                        } else if (subOpcion.equals("B")) {
                            System.out.println("NOTA: Use formato ISO (Ej. 2026-05-24T10:00:00)");
                            System.out.print("Fecha/Hora de Inicio: ");
                            String inicio = scanner.nextLine();
                            System.out.print("Fecha/Hora de Fin: ");
                            String fin = scanner.nextLine();
                            
                            System.out.println("Generando reporte historico...");
                            reqDB.send("HISTORIAL_" + inicio + "|" + fin);
                            
                            String respuesta = reqDB.recvStr();
                            System.out.println(respuesta != null ? respuesta : "[ERROR] La BD no responde.");
                            
                        } else {
                            System.out.println("Opcion no valida.");
                        }
                        
                    } else if (opcion.equals("3")) {
                        
                        System.out.println("\n=== PRUEBA DE RENDIMIENTO (2 MINUTOS) ===");
                        System.out.println("Ingrese la fecha y hora exacta de INICIO de la prueba.");
                        System.out.println("Formato estricto (ANO-MES-DIA HORA:MINUTO:SEGUNDO) -> Ej: 2026-05-27 18:00:00");
                        System.out.print("Fecha inicio: ");
                        String fechaInicioRendimiento = scanner.nextLine();

                        String requestRendimiento = "RENDIMIENTO_2MIN|" + fechaInicioRendimiento;
                        reqDB.send(requestRendimiento);

                        String replyRendimiento = reqDB.recvStr();
                        System.out.println("\n>> RESULTADOS DEL EXPERIMENTO:");
                        System.out.println(replyRendimiento != null ? replyRendimiento : "[ERROR] El motor de BD no respondio.");
                        System.out.println("-----------------------------------------\n");

                    } else if (opcion.equals("4")) {
                        System.out.println("Cerrando sesion y apagando consola PC3...");
                        System.exit(0);
                    } else {
                        System.out.println("Opcion no valida.");
                    }
                }
            }
        }
    }

    // Nuevo metodo: Lee un archivo txt en busca del usuario y contrasena
    private static boolean validarEnArchivo(String usuarioIngresado, String passwordIngresado) {
        // Busca el archivo en la raiz desde donde se esta ejecutando el proyecto
        File archivoCredenciales = new File("credenciales.txt");
        
        // Control de seguridad por si el administrador olvido crear el archivo
        if (!archivoCredenciales.exists()) {
            System.out.println("\n[SISTEMA] Archivo credenciales.txt no encontrado. Por favor crealo.");
            return false;
        }

        try (Scanner lectorArchivo = new Scanner(archivoCredenciales)) {
            while (lectorArchivo.hasNextLine()) {
                String linea = lectorArchivo.nextLine();
                
                // Ignoramos lineas vacias por si el archivo tiene saltos de linea extra
                if (linea.trim().isEmpty()) continue;
                
                // Asumimos que el formato interno del archivo sera: usuario,contrasena
                String[] partes = linea.split(",");
                if (partes.length == 2) {
                    String usuarioGuardado = partes[0].trim();
                    String passwordGuardado = partes[1].trim();
                    
                    // Verificamos si hay un match exacto
                    if (usuarioGuardado.equals(usuarioIngresado) && passwordGuardado.equals(passwordIngresado)) {
                        return true; 
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("[ERROR] Ocurrio un problema leyendo el archivo: " + e.getMessage());
        }
        
        // Si termino de leer todas las lineas y no encontro el usuario/clave, retorna falso
        return false; 
    }
}
