package sensores;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

// Clase para el cifrado de los datos de los sensores
public class CryptoUtils {
    
    //llave de 16 caracteres usada para el cifrado
    private static final String KEY = "1234567890123456"; 

    //función que recibe un texto y lo devuelve encriptado en formato Base64
    public static String encrypt(String data) throws Exception {
    
        //preparamos la llave y configuramos el algoritmo de encriptación
        SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        
        // Iniciamos el proceso en modo encriptación
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        
        // Encriptamos los datos
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        
        //retornamos el resultado convertido a texto para que sea fácil de transmitir
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
}
