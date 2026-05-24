package sensores;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class CryptoUtils {
    
    private static final String KEY = "1234567890123456"; 

    public static String encrypt(String data) throws Exception {
    
        SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
}
