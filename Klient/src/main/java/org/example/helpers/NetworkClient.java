package org.example.helpers;

import org.example.Klient;
import org.example.CryptoUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;

public class NetworkClient {
    private final Klient app;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenerThread;
    private boolean connected = false;

    // Klíč serveru pro šifrování odchozích zpráv
    private PublicKey serverPublicKey;

    public NetworkClient(Klient app) {
        this.app = app;
    }

    public void connect(String host, int port) throws Exception {
        this.socket = new Socket(host, port);
        // UTF-8 je kritické!
        this.out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.connected = true;
    }

    // 🔥 NOVÁ METODA: Přečte jeden řádek (pro Handshake)
    public String readOneLine() throws Exception {
        if (in != null) {
            return in.readLine();
        }
        throw new Exception("Nejsem připojen!");
    }

    // 🔥 NOVÁ METODA: Uloží klíč serveru
    public void setServerPublicKey(PublicKey key) {
        this.serverPublicKey = key;
    }

    public void startListening() {
        this.listenerThread = new Thread(() -> {
            try {
                String line;
                while (connected && in != null && (line = in.readLine()) != null) {
                    app.processMessage(line);
                }
            } catch (Exception e) {
                if (connected) app.onDisconnect(e.getMessage());
            }
        });
        this.listenerThread.start();
    }

    public void sendRawMessage(String msg) {
        if (out != null) out.println(msg);
    }

    public void sendEncryptedMessage(String msg) {
        try {
            if (serverPublicKey != null) {
                // Šifrujeme VEŘEJNÝM KLÍČEM SERVERU
                String encrypted = CryptoUtils.encrypt(msg, serverPublicKey);
                out.println(encrypted);
            } else {
                // Pokud nemáme klíč, nelze poslat bezpečně
                System.err.println("Chyba: Nemám veřejný klíč serveru!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (Exception e) {}
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
}