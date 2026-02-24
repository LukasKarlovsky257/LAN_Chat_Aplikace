package org.example.managers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PasswordUtils {

    // Jednoduché SHA-256 hashování (pro školní projekt stačí)
    // V reálné aplikaci bys měl použít BCrypt nebo Argon2 se solí.

    public static String hashPassword(String plainPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Chyba hashování: " + e.getMessage());
        }
    }

    public static boolean verifyPassword(String plainPassword, String storedHash) {
        String newHash = hashPassword(plainPassword);
        return newHash.equals(storedHash);
    }
}