package org.example;

import java.awt.*;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import javax.swing.*;

import org.example.helpers.*;

public class Klient extends JFrame {
    private final NetworkClient network;
    private final CardLayout cardLayout;
    private final JPanel mainPanel;

    private LoginPanel loginPanel;
    private ChatPanel chatPanel;
    private RoomManager currentRoomManager;

    private PrivateKey myPrivateKey;
    private PublicKey myPublicKey;

    private String myNick = "";
    private boolean isAdmin = false;

    public Klient() {
        super("Java Chat v15");
        this.setSize(1050, 750);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLocationRelativeTo(null);

        // Fix pro UTF-8 v konzoli
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception e) { e.printStackTrace(); }

        // Načítání ikony
        try {
            java.net.URL iconURL = getClass().getResource("/logo.png");
            if (iconURL != null) {
                this.setIconImage(new ImageIcon(iconURL).getImage());
            } else {
                this.setIconImage(new ImageIcon("logo.png").getImage());
            }
        } catch (Exception ignored) {}

        this.network = new NetworkClient(this);
        this.cardLayout = new CardLayout();
        this.mainPanel = new JPanel(this.cardLayout);

        new Thread(this::generateKeys).start();
        this.initUI();
    }

    private void generateKeys() {
        try {
            KeyPair kp = CryptoUtils.generateKeyPair();
            this.myPublicKey = kp.getPublic();
            this.myPrivateKey = kp.getPrivate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initUI() {
        this.loginPanel = new LoginPanel(this);
        this.chatPanel = new ChatPanel(this);

        // Přidání karet do hlavního panelu
        this.mainPanel.add(this.loginPanel, "LOGIN");
        this.mainPanel.add(this.chatPanel, "CHAT");
        this.add(this.mainPanel);
    }

    public void connectToServer(String host, int port, String command, String username, String password, String extra) throws Exception {
        System.out.println("=== ZAČÁTEK PŘIPOJOVÁNÍ ===");
        if (this.myPublicKey == null) throw new Exception("Generuji klíče, vydržte...");
        if (this.network.isConnected()) this.network.disconnect();

        System.out.println("[1/5] Připojuji k " + host + ":" + port);
        this.network.connect(host, port);

        System.out.println("[2/5] Čekám na klíč serveru...");
        String serverLine = this.network.readOneLine();

        if (serverLine != null && serverLine.startsWith("PUBKEY:")) {
            String keyData = serverLine.substring(7);
            this.network.setServerPublicKey(CryptoUtils.getPublicKeyFromBytes(keyData));
            System.out.println("✅ Klíč serveru přijat.");
        } else {
            throw new Exception("Server neposlal klíč! Odpověď: " + serverLine);
        }

        this.network.startListening();

        System.out.println("[3/5] Odesílám můj klíč...");
        String myKey = Base64.getEncoder().encodeToString(this.myPublicKey.getEncoded());
        this.network.sendRawMessage("PUBKEY:" + myKey);

        System.out.println("[4/5] Odesílám login...");
        String authMsg;
        if ("/register".equals(command)) {
            authMsg = "REGISTER:" + username + ":" + password + ":" + extra;
        } else {
            authMsg = "LOGIN:" + username + ":" + password;
        }

        this.network.sendEncryptedMessage(authMsg);
        System.out.println("[5/5] Hotovo. Čekám na odpověď.");
    }

    public void processMessage(String rawLine) {
        SwingUtilities.invokeLater(() -> {
            try {
                String line;
                try {
                    line = CryptoUtils.decrypt(rawLine, this.myPrivateKey);
                } catch (Exception e) {
                    line = rawLine;
                }

                System.out.println("PŘÍCHOZÍ: " + (line.length() > 50 ? line.substring(0, 50) + "..." : line));

                if (line.startsWith("AUTH_OK") || line.startsWith("LOGIN_OK")) {
                    if (line.contains("ADMIN")) this.isAdmin = true;
                    this.myNick = loginPanel.getUserField();

                    // Přepnutí na chat
                    this.cardLayout.show(this.mainPanel, "CHAT");
                    this.chatPanel.clearChat();
                    this.chatPanel.addMessage(0, "SYSTEM", "Vítej v chatu!", "TEXT", null);
                    this.network.sendEncryptedMessage("/users");
                }
                else if (line.startsWith("AUTH_FAIL:") || line.startsWith("ERR:")) {
                    String reason = line.contains(":") ? line.split(":", 2)[1] : line;
                    JOptionPane.showMessageDialog(this, reason, "Chyba", JOptionPane.ERROR_MESSAGE);
                    this.loginPanel.setStatus(reason);
                    this.network.disconnect();
                    this.loginPanel.toggleControls(true);
                }
                else if (line.startsWith("MSG:")) {
                    // MSG:ID:Sender:Text
                    String[] p = line.split(":", 4);
                    if (p.length >= 4) {
                        this.chatPanel.addMessage(Integer.parseInt(p[1]), p[2], p[3], "TEXT", null);
                    }
                }
                else if (line.startsWith("IMG:") || line.startsWith("FILE:")) {
                    try {
                        // NOVÝ FORMÁT: IMG:ID:Sender:File:Data
                        String[] p = line.split(":", 5);
                        if (p.length >= 5) {
                            String type = line.startsWith("IMG:") ? "IMAGE" : "FILE";
                            int msgId = Integer.parseInt(p[1]);
                            String sender = p[2];
                            String fname = p[3];

                            if (p[4] != null && !p[4].isEmpty()) {
                                byte[] data = Base64.getDecoder().decode(p[4]);
                                this.chatPanel.addMessage(msgId, sender, fname, type, data);
                            }
                        }
                        // STARÝ FORMÁT (Fallback)
                        else {
                            String[] oldP = line.split(":", 4);
                            if (oldP.length >= 4) {
                                String type = line.startsWith("IMG:") ? "IMAGE" : "FILE";
                                byte[] data = Base64.getDecoder().decode(oldP[3]);
                                this.chatPanel.addMessage(0, oldP[1], oldP[2], type, data);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Chyba při načítání obrázku/souboru: " + e.getMessage());
                    }
                }
                else if (line.startsWith("MUTE_START:")) {
                    try {
                        int seconds = Integer.parseInt(line.split(":")[1]);
                        this.chatPanel.setMuted(seconds);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else if (line.startsWith("ROOM_LIST:")) {
                    String content = line.length() > 10 ? line.substring(10) : "";
                    String[] rooms = content.split(",");
                    if (this.currentRoomManager != null && this.currentRoomManager.isVisible()) {
                        this.currentRoomManager.updateList(rooms);
                    }
                }
                else if (line.startsWith("USERS:")) {
                    // Smazáno .split(",") - posíláme to jako jeden text
                    this.chatPanel.updateUserList(line.substring(6));
                }
                // 🔥 PŘIDÁNO: Zpracování změny avatara
                else if (line.startsWith("UPDATE_AVATAR:")) {
                    // Jakmile nám server řekne, že si někdo změnil avatar, vyžádáme si nový seznam uživatelů
                    this.network.sendEncryptedMessage("/users");
                }
                else if (line.startsWith("ROOM_CHANGED:")) {
                    this.chatPanel.clearChat();
                    this.chatPanel.updateHeader(line.substring(13), isAdmin);
                }
                else if (line.startsWith("DELETE_MSG:")) {
                    int id = Integer.parseInt(line.split(":")[1]);
                    this.chatPanel.removeMessage(id);
                }
                // 🔥 OPRAVENÉ ZPRACOVÁNÍ ODPOJENÍ A KICKU
                else if (line.startsWith("DISCONNECT:")) {
                    String reason = line.substring(11);

                    // Zobrazíme upozornění
                    JOptionPane.showMessageDialog(this, reason, "Odpojeno od serveru", JOptionPane.ERROR_MESSAGE);

                    // Bezpečně se odhlásíme (vyčistí okno a přepne na Login)
                    logout();

                    // Napíšeme důvod do statusu na přihlašovací obrazovce
                    this.loginPanel.setStatus("Odpojeno: " + reason);
                }

                else if (line.startsWith("USER_TYPING:")) {
                    String[] p = line.split(":", 3);
                    if (p.length == 3 && this.chatPanel != null) {
                        this.chatPanel.setTypingStatus(p[1], p[2].equals("1"));
                    }
                    return;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void onDisconnect(String reason) {
        SwingUtilities.invokeLater(() -> {
            logout();
            JOptionPane.showMessageDialog(this, "Spojení ztraceno: " + reason, "Chyba spojení", JOptionPane.WARNING_MESSAGE);
        });
    }

    // 🔥 CENTRÁLNÍ METODA PRO ODHLÁŠENÍ (Stará se o CardLayout)
    public void logout() {
        if (this.network != null) this.network.disconnect();

        // Přepnutí UI
        this.cardLayout.show(this.mainPanel, "LOGIN");
        this.loginPanel.toggleControls(true);
        this.loginPanel.setStatus("Odpojeno.");

        // Vyčištění stavu
        this.chatPanel.clearChat();
        this.isAdmin = false;

        // Zavření správce místností, pokud je otevřen
        if (this.currentRoomManager != null) {
            this.currentRoomManager.dispose();
            this.currentRoomManager = null;
        }
    }

    public void showRoomDialog() {
        if (this.currentRoomManager != null && this.currentRoomManager.isVisible()) {
            this.currentRoomManager.toFront();
        } else {
            this.currentRoomManager = new RoomManager(this, this);
            this.currentRoomManager.setVisible(true);
        }
    }

    public PublicKey getMyPublicKey() { return myPublicKey; }
    public String getMyNick() { return myNick; }
    public boolean isAdmin() { return isAdmin; }
    public NetworkClient getNetwork() { return network; }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new Klient().setVisible(true));
    }
}