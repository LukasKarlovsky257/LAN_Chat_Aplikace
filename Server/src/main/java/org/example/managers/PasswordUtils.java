package org.example.managers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PasswordUtils {

    // Oddělovač pro uložení soli a hashe do jednoho stringu v databázi
    private static final String DELIMITER = ":";

    /**
     * Vygeneruje hash hesla s použitím náhodné soli.
     * Výsledek je ve formátu "Base64(Salt):Base64(Hash)".
     */
    public static String hashPassword(String plainPassword) {
        try {
            // 1. Vygenerování náhodné soli (16 bytů)
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);

            // 2. Připravení hashovací funkce
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // 3. Přidání soli do hashe
            digest.update(salt);

            // 4. Zahašování samotného hesla
            byte[] encodedHash = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));

            // 5. Zakódování obou částí do Base64, abychom je mohli uložit jako text
            String saltBase64 = Base64.getEncoder().encodeToString(salt);
            String hashBase64 = Base64.getEncoder().encodeToString(encodedHash);

            // 6. Vrácení spojeného řetězce, který uložíte do DB
            return saltBase64 + DELIMITER + hashBase64;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Chyba hashování: " + e.getMessage());
        }
    }

    /**
     * Ověří zadané heslo vůči uloženému hashi (který obsahuje i sůl).
     */
    public static boolean verifyPassword(String plainPassword, String storedHashWithSalt) {
        // Kontrola, zda uložený řetězec má správný formát (obsahuje dvojtečku)
        if (storedHashWithSalt == null || !storedHashWithSalt.contains(DELIMITER)) {
            return false;
        }

        try {
            // 1. Rozdělení uloženého textu z DB na sůl a hash
            String[] parts = storedHashWithSalt.split(DELIMITER);
            String saltBase64 = parts[0];
            String expectedHashBase64 = parts[1];

            // 2. Dekódování soli z Base64 zpět na byty
            byte[] salt = Base64.getDecoder().decode(saltBase64);

            // 3. Znovu-zahašování zadaného hesla se stejnou solí
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] newEncodedHash = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));

            // 4. Zakódování nového hashe do Base64 a porovnání s tím z databáze
            String newHashBase64 = Base64.getEncoder().encodeToString(newEncodedHash);

            return newHashBase64.equals(expectedHashBase64);

        } catch (NoSuchAlgorithmException | IllegalArgumentException e) {
            // IllegalArgumentException chytá chyby při špatném Base64 formátu
            return false;
        }
    }
}