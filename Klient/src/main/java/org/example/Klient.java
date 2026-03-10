package org.example;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import javax.imageio.ImageIO;
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
    private TrayIcon trayIcon;

    // --- GLOBÁLNÍ POZADÍ ---
    private Image backgroundImage = null;
    private final String BG_FILE_PATH = "background.jpg";

    public Klient() {
        super("LAN CHAT ULTIMATE");
        this.setSize(1050, 750);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLocationRelativeTo(null);

        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception e) { e.printStackTrace(); }

        try {
            java.net.URL iconURL = getClass().getResource("/logo.png");
            if (iconURL != null) this.setIconImage(new ImageIcon(iconURL).getImage());
            else this.setIconImage(new ImageIcon("logo.png").getImage());
        } catch (Exception ignored) {}

        this.network = new NetworkClient(this);
        this.cardLayout = new CardLayout();

        // 🔥 ABSOLUTNÍ FIX GHOSTINGU A GLOBÁLNÍ POZADÍ
        this.mainPanel = new JPanel(this.cardLayout) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // 1. Zaručený výmaz předchozích panelů plnou barvou
                g.setColor(ModernTheme.BG_BASE);
                g.fillRect(0, 0, getWidth(), getHeight());

                // 2. Vykreslení uživatelského pozadí na celou appku
                if (backgroundImage != null) {
                    g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
                    g.setColor(new Color(10, 12, 18, 160)); // Ztmavení fotky pro čitelnost textu
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };
        this.mainPanel.setOpaque(true);

        loadBackgroundImage();

        new Thread(this::generateKeys).start();
        this.initUI();
    }

    public void loadBackgroundImage() {
        File bgFile = new File(BG_FILE_PATH);
        if (bgFile.exists()) {
            try { backgroundImage = ImageIO.read(bgFile); } catch (IOException ignored) {}
        } else {
            backgroundImage = null;
        }
        if (mainPanel != null) mainPanel.repaint();
    }

    public void setCustomBackground() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.copy(chooser.getSelectedFile().toPath(), new File(BG_FILE_PATH).toPath(), StandardCopyOption.REPLACE_EXISTING);
                loadBackgroundImage();
            } catch (IOException ex) {
                ModernDialog.showMessage(this, "Chyba", "Chyba při ukládání pozadí:<br>" + ex.getMessage(), true);
            }
        }
    }

    public void removeCustomBackground() {
        new File(BG_FILE_PATH).delete();
        backgroundImage = null;
        mainPanel.repaint();
    }

    private void generateKeys() {
        try {
            KeyPair kp = CryptoUtils.generateKeyPair();
            this.myPublicKey = kp.getPublic();
            this.myPrivateKey = kp.getPrivate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void initUI() {
        this.loginPanel = new LoginPanel(this);
        this.chatPanel = new ChatPanel(this);

        this.mainPanel.add(this.loginPanel, "LOGIN");
        this.mainPanel.add(this.chatPanel, "CHAT");
        this.add(this.mainPanel);

        if (SystemTray.isSupported()) {
            try {
                SystemTray tray = SystemTray.getSystemTray();
                Image image = Toolkit.getDefaultToolkit().getImage("logo.png");
                java.net.URL iconURL = getClass().getResource("/logo.png");
                if (iconURL != null) image = new ImageIcon(iconURL).getImage();

                trayIcon = new TrayIcon(image, "Síťový Klient");
                trayIcon.setImageAutoSize(true);
                trayIcon.setToolTip("Spojení se Sítí je aktivní");
                tray.add(trayIcon);
            } catch (Exception e) { System.err.println("Notifikace na pozadí nejsou podporovány OS."); }
        }
        SwingUtilities.invokeLater(() -> this.loginPanel.attemptAutoLogin());
    }

    public void showSystemNotification(String title, String message) {
        if (trayIcon != null && !this.isActive()) trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
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
        } else throw new Exception("Server neposlal klíč! Odpověď: " + serverLine);

        this.network.startListening();

        System.out.println("[3/5] Odesílám můj klíč...");
        String myKey = Base64.getEncoder().encodeToString(this.myPublicKey.getEncoded());
        this.network.sendRawMessage("PUBKEY:" + myKey);

        System.out.println("[4/5] Odesílám login...");
        String authMsg;
        if ("/register".equals(command)) authMsg = "REGISTER:" + username + ":" + password + ":" + extra;
        else authMsg = "LOGIN:" + username + ":" + password;

        this.network.sendEncryptedMessage(authMsg);
        System.out.println("[5/5] Hotovo. Čekám na odpověď.");
    }

    public void processMessage(String rawLine) {
        SwingUtilities.invokeLater(() -> {
            try {
                String line;
                try { line = CryptoUtils.decrypt(rawLine, this.myPrivateKey); } catch (Exception e) { line = rawLine; }

                System.out.println("PŘÍCHOZÍ: " + (line.length() > 50 ? line.substring(0, 50) + "..." : line));

                if (line.startsWith("AUTH_OK") || line.startsWith("LOGIN_OK")) {
                    if (line.contains("ADMIN")) this.isAdmin = true;
                    this.myNick = loginPanel.getUserField();
                    this.cardLayout.show(this.mainPanel, "CHAT");
                    this.chatPanel.clearChat();
                    this.chatPanel.addMessage(0, "SYSTEM", "Vítej v chatu!", "TEXT", null);
                    this.network.sendEncryptedMessage("/users");
                }
                else if (line.startsWith("AUTH_FAIL:") || line.startsWith("ERR:")) {
                    String reason = line.contains(":") ? line.split(":", 2)[1] : line;
                    ModernDialog.showMessage(this, "Chyba", reason, true);
                    this.loginPanel.setStatus(reason);
                    this.network.disconnect();
                    this.loginPanel.toggleControls(true);
                }
                else if (line.startsWith("MSG:")) {
                    String[] p = line.split(":", 4);
                    if (p.length >= 4) {
                        this.chatPanel.addMessage(Integer.parseInt(p[1]), p[2], p[3], "TEXT", null);
                        if (!p[2].equals(myNick) && !p[2].equals("SYSTEM")) {
                            String preview = p[3].length() > 60 ? p[3].substring(0, 60) + "..." : p[3];
                            showSystemNotification("Zpráva od: " + p[2], preview);
                        }
                    }
                }
                else if (line.startsWith("IMG:") || line.startsWith("FILE:")) {
                    try {
                        String[] p = line.split(":", 5);
                        if (p.length >= 5) {
                            String type = line.startsWith("IMG:") ? "IMAGE" : "FILE";
                            int msgId = Integer.parseInt(p[1]); String sender = p[2]; String fname = p[3];
                            if (p[4] != null && !p[4].isEmpty()) { byte[] data = Base64.getDecoder().decode(p[4]); this.chatPanel.addMessage(msgId, sender, fname, type, data); }
                        } else {
                            String[] oldP = line.split(":", 4);
                            if (oldP.length >= 4) { String type = line.startsWith("IMG:") ? "IMAGE" : "FILE"; byte[] data = Base64.getDecoder().decode(oldP[3]); this.chatPanel.addMessage(0, oldP[1], oldP[2], type, data); }
                        }
                    } catch (Exception e) { System.err.println("Chyba při načítání obrázku/souboru: " + e.getMessage()); }
                }
                else if (line.startsWith("MUTE_START:")) {
                    try { int seconds = Integer.parseInt(line.split(":")[1]); this.chatPanel.setMuted(seconds); } catch (Exception e) { e.printStackTrace(); }
                }
                else if (line.startsWith("ROOM_LIST:")) {
                    String content = line.length() > 10 ? line.substring(10) : ""; String[] rooms = content.split(",");
                    if (this.currentRoomManager != null && this.currentRoomManager.isVisible()) this.currentRoomManager.updateList(rooms);
                }
                else if (line.startsWith("USERS:")) { this.chatPanel.updateUserList(line.substring(6)); }
                else if (line.startsWith("UPDATE_AVATAR:")) { this.network.sendEncryptedMessage("/users"); }
                else if (line.startsWith("ROOM_CHANGED:")) { this.chatPanel.clearChat(); this.chatPanel.updateHeader(line.substring(13), isAdmin); }
                else if (line.startsWith("DELETE_MSG:")) { int id = Integer.parseInt(line.split(":")[1]); this.chatPanel.removeMessage(id); }
                else if (line.startsWith("DISCONNECT:")) {
                    String reason = line.substring(11);
                    ModernDialog.showMessage(this, "Odpojeno od serveru", reason, true);
                    logout();
                    this.loginPanel.setStatus("Odpojeno: " + reason);
                }
                else if (line.startsWith("USER_TYPING:")) {
                    String[] p = line.split(":", 3);
                    if (p.length == 3 && this.chatPanel != null) this.chatPanel.setTypingStatus(p[1], p[2].equals("1"));
                    return;
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    public void onDisconnect(String reason) {
        SwingUtilities.invokeLater(() -> {
            logout();
            ModernDialog.showMessage(this, "Chyba spojení", "Spojení ztraceno:<br>" + reason, true);
        });
    }

    public void logout() {
        if (this.network != null) this.network.disconnect();
        this.cardLayout.show(this.mainPanel, "LOGIN");
        this.loginPanel.toggleControls(true);
        this.loginPanel.setStatus("Odpojeno.");
        this.chatPanel.clearChat();
        this.isAdmin = false;
        if (this.currentRoomManager != null) { this.currentRoomManager.dispose(); this.currentRoomManager = null; }
    }

    public void showRoomDialog() {
        if (this.currentRoomManager != null && this.currentRoomManager.isVisible()) this.currentRoomManager.toFront();
        else { this.currentRoomManager = new RoomManager(this, this); this.currentRoomManager.setVisible(true); }
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