package org.example.managers;

import javax.crypto.Cipher;
import java.security.*;
import java.util.Base64;

public class CryptoRSA {
    private static KeyPair serverKeyPair;

    public static void init() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            serverKeyPair = generator.generateKeyPair();
            System.out.println("🔐 KRYPTO: RSA klíče pro Web vygenerovány.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getPublicKeyBase64() {
        if (serverKeyPair == null) init();
        return Base64.getEncoder().encodeToString(serverKeyPair.getPublic().getEncoded());
    }

    public static String decrypt(String encryptedBase64) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, serverKeyPair.getPrivate());
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64));
            return new String(decryptedBytes);
        } catch (Exception e) {
            System.err.println("Chyba dešifrování RSA: " + e.getMessage());
            return null;
        }
    }
}