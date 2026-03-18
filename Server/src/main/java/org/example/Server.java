package org.example;

import org.example.managers.CryptoRSA;
import org.example.managers.CryptoUtils;
import org.example.managers.DatabaseManager;
import org.example.threads.ServerThread;
import org.example.web.ChatSocketServer;
import org.example.web.WebServer;
import org.java_websocket.WebSocket;

import java.io.IOException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Server {
    public static final Map<String, ServerThread> clients = Collections.synchronizedMap(new LinkedHashMap<>());
    public static final List<String> serverLogs = new CopyOnWriteArrayList<>();
    public static final long serverStartTime = System.currentTimeMillis();
    public static final long startTime = System.currentTimeMillis();

    public static final Set<String> activeRooms = Collections.synchronizedSet(new HashSet<>());
    public static final Set<String> tempRooms = Collections.synchronizedSet(new HashSet<>());

    public static class PrivateRoom {
        public String host;
        public Set<String> allowedUsers = ConcurrentHashMap.newKeySet();
        public PrivateRoom(String host) {
            this.host = host;
            this.allowedUsers.add(host);
        }
    }
    public static final Map<String, PrivateRoom> privateRooms = new ConcurrentHashMap<>();

    static {
        activeRooms.add("Lobby");
    }

    public static PublicKey serverPublicKey;
    public static PrivateKey serverPrivateKey;
    private static ServerSocket serverSocket;
    private static volatile boolean isRunning = false;
    private static ExecutorService pool;

    public static final ScheduledExecutorService timer = Executors.newScheduledThreadPool(5);
    public static ChatSocketServer chatServerInstance;

    public static volatile Integer globalMathResult = null;
    public static long lastMathTime = System.currentTimeMillis();
    private static final long MATH_INTERVAL = 1000 * 60 * 2;

    public static void main(String[] args) {
        try {
            if (serverPublicKey == null) {
                System.out.println("🔐 Generating RSA keys at startup...");
                KeyPair kp = CryptoUtils.generateKeyPair();
                serverPublicKey = kp.getPublic();
                serverPrivateKey = kp.getPrivate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        ServerGUI.main(args);
    }

    public static void startServer() {
        if (isRunning) return;
        isRunning = true;

        new Thread(() -> {
            try {
                System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
                System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

                pool = Executors.newFixedThreadPool(50);
                log("⏳ Initializing database...");
                DatabaseManager.initDatabase();

                log("🔐 Generating RSA keys for Desktop...");
                if (serverPublicKey == null) {
                    KeyPair kp = CryptoUtils.generateKeyPair();
                    serverPublicKey = kp.getPublic();
                    serverPrivateKey = kp.getPrivate();
                }

                log("🔐 Generating RSA keys for Web...");
                CryptoRSA.init();

                log("🌐 Starting WebSocket Server (Port 8887)...");
                chatServerInstance = new ChatSocketServer(8887);
                chatServerInstance.start();

                log("🌍 Starting WebSite Server (Port 8080)...");
                WebServer.start();

                log("🛡️ Starting Admin Web Server (Port 8888)...");
                org.example.web.AdminWebServer.start();

                log("🖥️ Starting TCP Server (Port 5555)...");
                serverSocket = new ServerSocket(5555);

                log("🚀 SERVER IS ONLINE AND READY!");

                while (isRunning) {
                    try {
                        Socket socket = serverSocket.accept();
                        pool.execute(new ServerThread(socket));
                    } catch (IOException e) {
                        if (isRunning) log("Socket error: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                log("CRITICAL SERVER ERROR: " + e.getMessage());
            }
        }).start();
    }

    public static void stopServer() {
        if (!isRunning) return;
        isRunning = false;
        log("🛑 Stopping server...");

        WebServer.stop();
        if (chatServerInstance != null) {
            try { chatServerInstance.stop(); } catch (Exception e) {}
        }
        synchronized (clients) {
            for (ServerThread client : clients.values()) {
                client.sendEncryptedMessage("DISCONNECT:🛑 Server is shutting down.");
                client.disconnect();
            }
            clients.clear();
        }
        try { if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close(); } catch (IOException e) {}
        if (pool != null) pool.shutdownNow();
        timer.shutdownNow();
        log("Server is off.");
    }

    public static void log(String msg) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formatted = "[" + time + "] " + msg;
        System.out.println(formatted);
        serverLogs.add(formatted);
        if (serverLogs.size() > 5000) serverLogs.remove(0);
    }

    public static int getOnlineCount() {
        return clients.size() + (chatServerInstance != null ? ChatSocketServer.webClients.size() : 0);
    }

    public static int getOnlineUsersCount() {
        return getOnlineCount();
    }

    public static List<String> getServerLogs() {
        return serverLogs;
    }

    public static List<String> getOnlineUserNames() {
        Set<String> allUsers = new HashSet<>(clients.keySet());
        if (chatServerInstance != null) {
            allUsers.addAll(ChatSocketServer.webClients.values());
        }
        return new ArrayList<>(allUsers);
    }

    public static String[] getUserListWithLevels() {
        Set<String> allUsers = new HashSet<>(clients.keySet());
        if (chatServerInstance != null) {
            allUsers.addAll(ChatSocketServer.webClients.values());
        }

        String[] result = new String[allUsers.size()];
        int i = 0;
        for (String user : allUsers) {
            int level = DatabaseManager.getUserLevel(user);
            String avatar = DatabaseManager.getAvatar(user);

            StringBuilder sb = new StringBuilder(user).append("|Lvl").append(level);

            if (avatar != null && !avatar.isEmpty()) {
                sb.append("~").append(avatar);
            }

            result[i++] = sb.toString();
        }
        return result;
    }

    public static String[] getUserList() {
        return getUserListWithLevels();
    }

    public static void sendChatMessage(String senderNick, String message, String room) {
        String time = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
        int msgId = DatabaseManager.saveMessage(senderNick, message, time, room);
        DatabaseManager.addXp(senderNick, 5);
        log("[" + room + "] " + senderNick + ": " + message);
        broadcastRaw("MSG:" + msgId + ":" + senderNick + ":" + message, room);
    }

    public static void sendBurnMessage(String senderNick, String message, String room, int seconds) {
        String time = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
        int msgId = DatabaseManager.saveMessage(senderNick, message, time, room, true);
        DatabaseManager.addXp(senderNick, 10);
        log("🔥 [" + room + "] " + senderNick + " poslala TAJNOU zprávu (" + seconds + "s).");
        broadcastRaw("BURN:" + msgId + ":" + senderNick + ":" + message + ":" + seconds, room);
    }

    public static void sendSystemBroadcast(String message, String room) {
        String senderName = "SYSTEM";
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        int id = DatabaseManager.saveMessage(senderName, message, time, room);
        log("📢 [" + room + "] SYSTEM: " + message);
        broadcastRaw("MSG:" + id + ":" + senderName + ":" + message, room);
    }

    public static void broadcastRaw(String packet, String room) {
        synchronized (clients) {
            for (ServerThread client : clients.values()) {
                if (room.equals("ALL") || client.getCurrentRoom().equals(room)) {
                    if (packet.startsWith("IMG:") || packet.startsWith("FILE:")) {
                        client.sendRawMessage(packet);
                    } else {
                        client.sendEncryptedMessage(packet);
                    }
                }
            }
        }
        if (chatServerInstance != null) {
            String targetRoom = room.equals("ALL") ? null : room;
            chatServerInstance.broadcastToWeb(packet, targetRoom);
        }
    }

    public static void broadcastGame(String pureGamePacket, String room) {
        String desktopPacket = "MSG:0:SYSTEM:" + pureGamePacket;
        synchronized (clients) {
            for (ServerThread client : clients.values()) {
                if (room.equals("ALL") || client.getCurrentRoom().equals(room)) {
                    client.sendEncryptedMessage(desktopPacket);
                }
            }
        }
        if (chatServerInstance != null) {
            String targetRoom = room.equals("ALL") ? null : room;
            chatServerInstance.broadcastToWeb(pureGamePacket, targetRoom);
        }
    }

    public static void processWebMessage(String sender, String message, String currentRoom) {
        String time = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());

        if (message.startsWith("SET_AVATAR:")) {
            String base64 = message.substring(11);
            try {
                java.io.File avatarDir = new java.io.File("avatars");
                if (!avatarDir.exists()) avatarDir.mkdirs();

                byte[] imageBytes = java.util.Base64.getDecoder().decode(base64);
                String fileName = sender + ".jpg";
                java.io.File avatarFile = new java.io.File(avatarDir, fileName);
                java.nio.file.Files.write(avatarFile.toPath(), imageBytes);

                if (DatabaseManager.saveAvatar(sender, fileName)) {
                    long timestamp = System.currentTimeMillis();
                    broadcastRaw("UPDATE_AVATAR:" + sender + ":" + fileName + "?t=" + timestamp, currentRoom);
                }
            } catch (Exception e) {
                System.err.println("📸 ERROR: " + e.getMessage());
            }
            return;
        }

        if (message.startsWith("IMG:") || message.startsWith("FILE:")) {
            String[] parts = message.split(":", 4);
            if (parts.length == 4) {
                DatabaseManager.saveFullMessage(sender, parts[2], parts[3], parts[0], time, currentRoom);
                DatabaseManager.addXp(sender, 15);
                broadcastRaw(message, currentRoom);
            }
        } else {
            int id = DatabaseManager.saveMessage(sender, message, time, currentRoom);
            DatabaseManager.addXp(sender, 5);
            broadcastRaw("MSG:" + id + ":" + sender + ":" + message, currentRoom);
        }
        log("[WEB] " + sender + " -> " + currentRoom);
    }

    public static boolean createTempRoom(String roomName, String creator) {
        if (activeRooms.contains(roomName)) return false;
        activeRooms.add(roomName);
        tempRooms.add(roomName);
        broadcastRoomList();
        sendSystemBroadcast("⏱️ Dočasná místnost '" + roomName + "' vytvořena.", roomName);
        return true;
    }

    public static boolean createPrivateRoom(String roomName, String creator) {
        if (activeRooms.contains(roomName)) return false;
        activeRooms.add(roomName);
        privateRooms.put(roomName, new PrivateRoom(creator));
        broadcastRoomList();
        sendSystemBroadcast("🔒 Soukromá místnost '" + roomName + "' vytvořena. Jsi hostitel.", roomName);
        return true;
    }

    public static boolean canJoinRoom(String nick, String roomName) {
        if (!activeRooms.contains(roomName)) return false;
        if (privateRooms.containsKey(roomName)) {
            return privateRooms.get(roomName).allowedUsers.contains(nick) || DatabaseManager.isAdmin(nick);
        }
        return true;
    }

    public static void checkTempRooms() {
        Set<String> toDelete = new HashSet<>();
        for (String room : tempRooms) {
            boolean hasUsers = false;
            synchronized (clients) {
                for (ServerThread c : clients.values()) {
                    if (c.getCurrentRoom().equals(room)) { hasUsers = true; break; }
                }
            }
            if (!hasUsers && chatServerInstance != null) {
                for (String r : ChatSocketServer.clientRooms.values()) {
                    if (r.equals(room)) { hasUsers = true; break; }
                }
            }
            if (!hasUsers) toDelete.add(room);
        }
        for (String room : toDelete) {
            deleteRoom(room);
        }
    }

    public static void deleteMessage(int msgId) {
        if (DatabaseManager.deleteMessage(msgId)) broadcastRaw("DELETE_MSG:" + msgId, "ALL");
    }

    public static void deleteRoom(String roomName) {
        if (roomName.equals("Lobby")) return;
        log("♻️ Deleting room: " + roomName);

        synchronized (clients) {
            for (ServerThread client : clients.values()) {
                if (client.getCurrentRoom().equals(roomName)) {
                    client.setCurrentRoom("Lobby");
                    client.sendEncryptedMessage("MSG:0:SYSTEM:⚠️ Místnost byla odstraněna.");
                }
            }
        }
        activeRooms.remove(roomName);
        tempRooms.remove(roomName);
        privateRooms.remove(roomName);
        broadcastRoomList();
        broadcastRaw("MSG:0:SYSTEM:Room " + roomName + " odstraněna.", "ALL");
    }

    public static String getCustomRoomList(String nick) {
        if (activeRooms.isEmpty()) return "";
        List<String> list = new ArrayList<>();
        boolean isAdmin = DatabaseManager.isAdmin(nick);

        for (String r : activeRooms) {
            if (privateRooms.containsKey(r)) {
                if (isAdmin || privateRooms.get(r).allowedUsers.contains(nick)) {
                    list.add(r + "|2");
                }
            } else if (tempRooms.contains(r)) {
                list.add(r + "|1");
            } else {
                list.add(r + "|0");
            }
        }
        return String.join(",", list);
    }

    public static void broadcastRoomList() {
        synchronized (clients) {
            for (ServerThread client : clients.values()) {
                client.sendEncryptedMessage("ROOM_LIST:" + getCustomRoomList(client.getClientName()));
            }
        }
        if (chatServerInstance != null) {
            for (Map.Entry<WebSocket, String> entry : ChatSocketServer.webClients.entrySet()) {
                if (entry.getKey().isOpen()) {
                    entry.getKey().send("ROOM_LIST:" + getCustomRoomList(entry.getValue()));
                }
            }
        }
    }

    public static void broadcastUserList() {
        broadcastRaw("USERS:" + String.join(",", getUserListWithLevels()), "ALL");
    }

    public static void muteUser(String targetUser, int seconds, String reason) {
        DatabaseManager.muteUser(targetUser, seconds);

        synchronized(clients) {
            for (ServerThread client : clients.values()) {
                if (client.getClientName() != null && client.getClientName().equalsIgnoreCase(targetUser)) {
                    client.sendEncryptedMessage("MUTE_START:" + seconds);
                }
            }
        }

        if (chatServerInstance != null) {
            for (Map.Entry<WebSocket, String> entry : ChatSocketServer.webClients.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(targetUser)) {
                    entry.getKey().send("MUTE_START:" + seconds);
                }
            }
        }

        sendSystemBroadcast(targetUser + " byl umlcen. Duvod: " + reason, "ALL");
        log("MUTE: " + targetUser + " (" + seconds + "s)");
    }

    public static void kickUser(String targetUser, String reason) {
        synchronized(clients) {
            for (ServerThread client : clients.values()) {
                if (client.getClientName() != null && client.getClientName().equalsIgnoreCase(targetUser)) {
                    client.sendEncryptedMessage("DISCONNECT:Byl jsi vyhozen. Duvod: " + reason);
                    try { Thread.sleep(200); } catch (Exception ignored) {}
                    client.disconnect();
                }
            }
        }

        if (chatServerInstance != null) {
            for (Map.Entry<WebSocket, String> entry : ChatSocketServer.webClients.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(targetUser)) {
                    entry.getKey().send("DISCONNECT:Byl jsi vyhozen. Duvod: " + reason);
                    entry.getKey().close(1000, reason);
                }
            }
            ChatSocketServer.webClients.values().removeIf(val -> val.equalsIgnoreCase(targetUser));
        }

        sendSystemBroadcast(targetUser + " byl vyhozen.", "ALL");
        broadcastUserList();
        log("KICK: " + targetUser + " (" + reason + ")");
    }

    public static boolean banUser(String targetUser, String adminName, String reason, long seconds) {
        DatabaseManager.banUser(targetUser, adminName, reason, seconds);
        DatabaseManager.deleteMessagesByUser(targetUser);
        kickUser(targetUser, "BAN: " + reason);
        return true;
    }

    public static boolean unbanUser(String targetNick) {
        log("UNBAN: " + targetNick);
        return DatabaseManager.unbanUser(targetNick);
    }

    public static synchronized void registerClient(String nick, ServerThread client) {
        clients.put(nick, client);
        sendSystemBroadcast(nick + " se připojil.", "Lobby");
        broadcastUserList();
    }

    public static synchronized void removeClient(String nick) {
        if (clients.containsKey(nick)) {
            clients.remove(nick);
            sendSystemBroadcast(nick + " se odpojil.", "Lobby");
        }
        checkTempRooms();
        broadcastUserList();
    }

    public static void sendDirectMessage(String rawData, String room) {
        broadcastRaw(rawData, room);
    }

    public static synchronized void checkAndGenerateMath() {
        if (globalMathResult == null && (System.currentTimeMillis() - lastMathTime > MATH_INTERVAL)) {
            java.util.Random rand = new java.util.Random();
            int a = rand.nextInt(50) + 1;
            int b = rand.nextInt(50) + 1;
            int op = rand.nextInt(3);
            String operator = "+";
            switch (op) {
                case 0: globalMathResult = a + b; operator = "+"; break;
                case 1: globalMathResult = a - b; operator = "-"; break;
                case 2:
                    a = rand.nextInt(12) + 1; b = rand.nextInt(12) + 1;
                    globalMathResult = a * b; operator = "*"; break;
            }
            lastMathTime = System.currentTimeMillis();
            sendSystemBroadcast("🧮 SOUTĚŽ: Vypočítej " + a + " " + operator + " " + b + " (Kdo dřív přijde, ten dřív mele!)", "Lobby");
        }
    }

    public static synchronized boolean solveMath(String answer, String winnerNick) {
        if (globalMathResult != null) {
            try {
                int val = Integer.parseInt(answer.trim());
                if (val == globalMathResult) {
                    DatabaseManager.addXp(winnerNick, 50);
                    broadcastRaw("MSG:0:SYSTEM:🏆 " + winnerNick + " to dokázal! Odpověď byla " + globalMathResult + " (+50 XP).", "Lobby");
                    globalMathResult = null;
                    lastMathTime = System.currentTimeMillis();
                    broadcastUserList();
                    return true;
                }
            } catch (NumberFormatException e) {}
        }
        return false;
    }

    public static boolean isUserOnline(String nick) {
        if (clients.containsKey(nick)) return true;
        if (chatServerInstance != null && ChatSocketServer.webClients.containsValue(nick)) return true;
        return false;
    }

    public static boolean sendToUser(String targetNick, String rawPacket) {
        boolean delivered = false;
        ServerThread targetDesktop = clients.get(targetNick);
        if (targetDesktop != null) {
            targetDesktop.sendEncryptedMessage(rawPacket);
            delivered = true;
        }
        if (chatServerInstance != null) {
            for (Map.Entry<WebSocket, String> entry : ChatSocketServer.webClients.entrySet()) {
                if (entry.getValue().equals(targetNick)) {
                    entry.getKey().send(rawPacket);
                    delivered = true;
                    break;
                }
            }
        }
        return delivered;
    }

    public static void whisper(ServerThread sender, String targetNick, String message) {
        String formattedMsgRx = "🕵️ (šeptá ti): " + message;
        String formattedMsgTx = "🕵️ (šeptáš pro " + targetNick + "): " + message;

        boolean found = sendToUser(targetNick, "MSG:0:" + sender.getClientName() + ":" + formattedMsgRx);

        if (found) {
            sender.sendEncryptedMessage("MSG:0:" + sender.getClientName() + ":" + formattedMsgTx);
        } else {
            sender.sendEncryptedMessage("MSG:0:SYSTEM:Uživatel '" + targetNick + "' není online.");
        }
    }

    public static void relayKeyExchange(String senderNick, String targetNick, String rawMessage) {
        boolean delivered = sendToUser(targetNick, rawMessage);
        if (!delivered) {
            log("⚠️ RSA Handshake selhal: Uživatel '" + targetNick + "' nebyl nalezen pro doručení klíče od '" + senderNick + "'.");
        } else {
            log("🔐 KRYPTO: Předán Handshake balíček od '" + senderNick + "' pro '" + targetNick + "'.");
        }
    }
}