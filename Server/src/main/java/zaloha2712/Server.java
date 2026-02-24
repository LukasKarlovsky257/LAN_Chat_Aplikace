package zaloha2712;

import org.example.managers.CryptoUtils;
import org.example.managers.DatabaseManager;
import org.example.threads.ServerThread;

import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    // Mapa klientů
    private static final Map<String, ServerThread> clients = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final ExecutorService pool = Executors.newFixedThreadPool(50);

    // 🔥 LOGY PRO WEBSERVER
    public static final List<String> serverLogs = new CopyOnWriteArrayList<>();
    public static final long startTime = System.currentTimeMillis();

    // Seznam aktivních místností (vždy obsahuje "Lobby")
    public static final Set<String> activeRooms = Collections.synchronizedSet(new HashSet<>());
    static {
        activeRooms.add("Lobby");
    }

    // Klíče
    public static PublicKey serverPublicKey;
    public static PrivateKey serverPrivateKey;

    public static void main(String[] args) {
        try {
            // 🔧 OPRAVA KÓDOVÁNÍ KONZOLE (Windows fix)
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

            KeyPair kp = CryptoUtils.generateKeyPair();
            serverPublicKey = kp.getPublic();
            serverPrivateKey = kp.getPrivate();

            DatabaseManager.initDatabase();

            // Start WebServeru (Dashboard)
            WebServer.start();

            ServerSocket serverSocket = new ServerSocket(5555);
            log("🚀 Chat Server běží na portu 5555...");

            while (true) {
                Socket socket = serverSocket.accept();
                pool.execute(new ServerThread(socket));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 🔥 LOGOVÁNÍ
    public static void log(String msg) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formatted = "[" + time + "] " + msg;

        // Výpis do konzole
        System.out.println(formatted);

        // Uložení do listu pro WebServer
        serverLogs.add(formatted);
        // Nechceme mazat logy, aby byly ve staženém souboru kompletní (nebo limit zvýšíme)
        if (serverLogs.size() > 5000) serverLogs.removeFirst();
    }

    // --- GETTERY PRO WEB ---
    public static int getOnlineCount() { return clients.size(); }
    public static String[] getUserList() { return clients.keySet().toArray(new String[0]); }

    // --- ZASÍLÁNÍ ZPRÁV ---
    public static void sendChatMessage(String senderNick, String message, String room) {
        // 1. Uložit do DB a získat ID
        String time = new java.text.SimpleDateFormat("HH:mm").format(new Date());
        int msgId = DatabaseManager.saveMessage(senderNick, message, time, room);
        log("[" + room + "] " + senderNick + ": " + message);

        // 2. Sestavit packet
        // Formát: MSG:ID:SENDER:TEXT
        String packet = "MSG:" + msgId + ":" + senderNick + ":" + message;

        broadcastRaw(packet, room);
    }

    public static void sendSystemBroadcast(String message, String room) {
        String senderName = "SYSTEM";
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        int id = DatabaseManager.saveMessage(senderName, message, time, room);
        log("📢 [" + room + "] SYSTEM: " + message);

        String packet = "MSG:" + id + ":" + senderName + ":" + message;
        broadcastRaw(packet, room);
    }

    public static void sendDirectMessage(String rawData, String room) {
        broadcastUnencrypted(rawData, room);
    }

    // 🔥 Metoda pro RAW data (soubory) - nešifrované
    private static void broadcastUnencrypted(String packet, String room) {
        synchronized (clients) {
            for (ServerThread client : clients.values()) {
                // Posíláme jen lidem ve stejné místnosti (nebo ALL)
                if (room.equals("ALL") || client.getCurrentRoom().equals(room)) {
                    client.sendRawMessage(packet); // <-- Volá sendRawMessage (NE Encrypted)
                }
            }
        }
    }

    // --- ADMIN LOGIKA ---

    public static void deleteMessage(int msgId) {
        if (DatabaseManager.deleteMessage(msgId)) {
            // Pošleme klientům příkaz ke smazání
            broadcastRaw("DELETE_MSG:" + msgId, "ALL");
        }
    }

    public static void kickUser(String targetNick, String reason) {
        if (DatabaseManager.isAdmin(targetNick)) {
            log("⛔ Nelze vyhodit admina: " + targetNick);
            return;
        }
        ServerThread target = clients.get(targetNick);
        if (target != null) {
            // 🔥 ZMĚNA: Posíláme příkaz DISCONNECT, ne obyčejnou zprávu
            target.sendEncryptedMessage("DISCONNECT:❌ BYL JSI VYHOZEN! Důvod: " + reason);

            // Dáme klientovi chvilku na zpracování zprávy a odhlášení
            try { Thread.sleep(200); } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            target.disconnect(); // Zavřeme socket na straně serveru
            log("KICK: " + targetNick);
            broadcastRaw("MSG:0:SYSTEM:👢 Uživatel " + targetNick + " byl vyhozen.", "ALL");
        }
    }

    public static boolean banUser(String targetNick, String adminName, String reason, long seconds) {
        if (DatabaseManager.isAdmin(targetNick)) {
            log("⛔ Nelze zabanovat admina: " + targetNick);
            return false;
        }

        // Voláme DB s časem
        DatabaseManager.banUser(targetNick, adminName, reason, seconds);
        DatabaseManager.deleteMessagesByUser(targetNick);

        broadcastRaw("MSG:0:SYSTEM:🔨 Uživatel " + targetNick + " byl zabanován.", "ALL");

        ServerThread target = clients.get(targetNick);
        if (target != null) {
            String durationStr = (seconds == -1) ? "Navždy" : (seconds / 60) + " minut";
            target.sendEncryptedMessage("DISCONNECT:⛔ DOSTAL JSI BAN! (" + durationStr + ") Důvod: " + reason);
            try { Thread.sleep(200); } catch (InterruptedException e) {}
            target.disconnect();
        }
        log("BAN: " + targetNick + " na " + seconds + "s");
        return true;
    }

    public static void muteUser(String targetNick, int seconds, String reason) {
        if (DatabaseManager.isAdmin(targetNick)) return;
        ServerThread target = clients.get(targetNick);
        if (target != null) {
            target.mute(seconds, reason);
            log("MUTE: " + targetNick);
        }
    }

    public static boolean unbanUser(String targetNick) {
        log("UNBAN: " + targetNick);
        return DatabaseManager.unbanUser(targetNick);
    }

    // 🔥 ŠEPOT (Private Message)
    public static void whisper(ServerThread sender, String targetNick, String message) {
        ServerThread target = clients.get(targetNick);

        if (target != null) {
            // Formát pro klienta
            String formattedMsg = "🕵️ (šeptá): " + message;

            // Pošleme příjemci (ID 0 = neukládá se do historie chatu v DB)
            target.sendEncryptedMessage("MSG:0:" + sender.getClientName() + ":" + formattedMsg);

            // Pošleme i odesílateli
            sender.sendEncryptedMessage("MSG:0:" + sender.getClientName() + ":" + formattedMsg);
        }
    }

    // --- SPRÁVA KLIENTŮ ---

    public static synchronized void registerClient(String nick, ServerThread client) {
        clients.put(nick, client);
        sendSystemBroadcast(nick + " se připojil.", "Lobby");
        broadcastUserList(); // Aktualizace seznamu
    }

    public static synchronized void removeClient(String nick) {
        if (nick != null && clients.remove(nick) != null) {
            sendSystemBroadcast(nick + " se odpojil.", "Lobby");
            broadcastUserList(); // Aktualizace seznamu
        }
    }

    // Broadcast pro textové zprávy a příkazy (ŠIFROVANÉ)
    public static void broadcastRaw(String packet, String room) {
        synchronized (clients) {
            for (ServerThread client : clients.values()) {
                if (room.equals("ALL") || client.getCurrentRoom().equals(room)) {
                    client.sendEncryptedMessage(packet);
                }
            }
        }
    }

    // Rozeslání seznamu uživatelů
    public static void broadcastUserList() {
        if (clients.isEmpty()) return;
        String list = "USERS:" + String.join(",", getUserList());

        synchronized (clients) {
            for (ServerThread client : clients.values()) {
                client.sendEncryptedMessage(list);
            }
        }
    }
}