package zaloha1612;

import javax.crypto.Cipher;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class CryptoUtils {
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }
    public static PublicKey getPublicKeyFromBytes(String b64) throws Exception {
        byte[] b = Base64.getDecoder().decode(b64);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(b));
    }
    public static String publicKeyToString(PublicKey k) {
        return Base64.getEncoder().encodeToString(k.getEncoded());
    }
    public static String encrypt(String txt, PublicKey k) throws Exception {
        Cipher c = Cipher.getInstance("RSA");
        c.init(Cipher.ENCRYPT_MODE, k);
        return Base64.getEncoder().encodeToString(c.doFinal(txt.getBytes("UTF-8")));
    }
    public static String decrypt(String txt, PrivateKey k) throws Exception {
        Cipher c = Cipher.getInstance("RSA");
        c.init(Cipher.DECRYPT_MODE, k);
        return new String(c.doFinal(Base64.getDecoder().decode(txt)), "UTF-8");
    }
}