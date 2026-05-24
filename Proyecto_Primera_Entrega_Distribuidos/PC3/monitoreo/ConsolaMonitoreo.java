package monitoreo;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import java.util.Scanner;

public class ConsolaMonitoreo {
    public static void main(String[] args) {
    
        new Thread(new ServidorBDPrincipal()).start();
        
        try (ZContext context = new ZContext();
             Scanner scanner = new Scanner(System.in)) {
	    //Implementar autenticación
            while (true) {
                System.out.println("\n=== PANEL DE MONITOREO (PC3) ===");
                System.out.println("1. Priorizar Ambulancia (Ola Verde)");
                System.out.println("2.Realizar consulta.");
                System.out.println("3. Salir");
                System.out.print("Opción: ");
                
                String opcion = scanner.nextLine();

                if (opcion.equals("1")) {
                    System.out.print("Ingrese ID Intersección (Ej. INT_C5): ");
                    // Implementar consulta
                }else if (opcion.equals("2")) {
                    System.out.println("Realice su consulta:");
                    //implementar consulta
                } else if (opcion.equals("3")) {
                    System.out.println("saliendo de consola PC3.");
                    System.exit(0);
                }
            }
        }
    }
}
