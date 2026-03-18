package org.example.managers;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class CryptoAES {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    private static SecretKeySpec generateKey(String roomPassword) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(roomPassword.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }

    public static String encrypt(String plainText, String roomPassword) {
        try {
            SecretKeySpec secretKey = generateKey(roomPassword);

            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String cipherBase64 = Base64.getEncoder().encodeToString(encryptedBytes);

            return ivBase64 + ":" + cipherBase64;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String decrypt(String encryptedPayload, String roomPassword) {
        try {
            String[] parts = encryptedPayload.trim().split(":");
            if (parts.length != 2) return null;
            byte[] iv = Base64.getDecoder().decode(parts[0].trim());
            byte[] encryptedBytes = Base64.getDecoder().decode(parts[1].trim());

            SecretKeySpec secretKey = generateKey(roomPassword.trim());
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            return null;
        }
    }
}