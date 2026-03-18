package org.example.managers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PasswordUtils {
    private static final String DELIMITER = ":";

    public static String hashPassword(String plainPassword) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            digest.update(salt);

            byte[] encodedHash = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));

            String saltBase64 = Base64.getEncoder().encodeToString(salt);
            String hashBase64 = Base64.getEncoder().encodeToString(encodedHash);

            return saltBase64 + DELIMITER + hashBase64;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Chyba hashování: " + e.getMessage());
        }
    }

    public static boolean verifyPassword(String plainPassword, String storedHashWithSalt) {
        if (storedHashWithSalt == null || !storedHashWithSalt.contains(DELIMITER)) {
            return false;
        }

        try {
            String[] parts = storedHashWithSalt.split(DELIMITER);
            String saltBase64 = parts[0];
            String expectedHashBase64 = parts[1];

            byte[] salt = Base64.getDecoder().decode(saltBase64);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] newEncodedHash = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));

            String newHashBase64 = Base64.getEncoder().encodeToString(newEncodedHash);

            return newHashBase64.equals(expectedHashBase64);

        } catch (NoSuchAlgorithmException | IllegalArgumentException e) {
            return false;
        }
    }
}