package org.example.managers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class ConfigManager {
    private static final String CONFIG_FILE = "config.properties";
    private static final Properties props = new Properties();

    // Výchozí hodnoty
    public static boolean playSounds = true;
    public static String myBubbleColor = "#0084ff"; // Výchozí modrá

    public static void load() {
        try {
            File f = new File(CONFIG_FILE);
            if (f.exists()) {
                FileInputStream in = new FileInputStream(f);
                props.load(in);
                in.close();

                playSounds = Boolean.parseBoolean(props.getProperty("playSounds", "true"));
                myBubbleColor = props.getProperty("myBubbleColor", "#0084ff");
            } else {
                save(); // Vytvoří soubor s výchozími hodnotami, pokud neexistuje
            }
        } catch (Exception e) {
            System.err.println("Chyba při načítání konfigurace: " + e.getMessage());
        }
    }

    public static void save() {
        try {
            props.setProperty("playSounds", String.valueOf(playSounds));
            props.setProperty("myBubbleColor", myBubbleColor);

            FileOutputStream out = new FileOutputStream(CONFIG_FILE);
            props.store(out, "LAN Chat Ultimate - Client Settings");
            out.close();
        } catch (Exception e) {
            System.err.println("Chyba při ukládání konfigurace: " + e.getMessage());
        }
    }
}