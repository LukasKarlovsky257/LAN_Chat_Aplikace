package org.example.web;

import org.example.Server;
import org.example.managers.CryptoRSA;
import org.example.managers.DatabaseManager;
import org.example.managers.GameManager;
import org.example.threads.ServerThread;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ChatSocketServer extends WebSocketServer {

    public static final Map<WebSocket, String> webClients = new ConcurrentHashMap<>();
    public static final Map<WebSocket, String> clientRooms = new ConcurrentHashMap<>();

    public ChatSocketServer(int port) {
        super(new InetSocketAddress(port));
        this.setReuseAddr(true);
        this.setConnectionLostTimeout(30);
    }

    @Override
    public void onStart() {
        Server.log("🌐 WEB SOCKET SERVER: Běží na portu " + getPort());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        conn.send("PUBKEY:" + CryptoRSA.getPublicKeyBase64());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String nick = webClients.remove(conn);
        clientRooms.remove(conn);
        if (nick != null) {
            Server.sendSystemBroadcast(nick + " (WEB) se odpojil.", "Lobby");
            Server.checkTempRooms();
            Server.broadcastUserList();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {

        if (message.startsWith("ENC:")) {
            String encryptedData = message.substring(4);
            String decrypted = CryptoRSA.decrypt(encryptedData);

            if (decrypted == null) {
                conn.send("ERROR:Chyba dešifrování nebo neplatný klíč.");
                return;
            }
            message = decrypted;
        }

        if (message.startsWith("LOGIN:")) {
            String[] parts = message.split(":", 3);
            if (parts.length == 3) {
                String u = parts[1];
                String p = parts[2];
                if (DatabaseManager.checkCredentials(u, p)) {
                    if (DatabaseManager.isBanned(u)) {
                        conn.send("ERROR:Máš BAN!");
                        conn.close();
                        return;
                    }
                    webClients.put(conn, u);
                    clientRooms.put(conn, "Lobby");
                    conn.send("LOGIN_OK:" + u + ":" + DatabaseManager.isAdmin(u));
                    conn.send("ROOM_LIST:" + String.join(",", Server.activeRooms));
                    sendHistory(conn, "Lobby");

                    Server.sendSystemBroadcast(u + " (WEB) se připojil.", "Lobby");
                    Server.broadcastUserList();
                } else {
                    conn.send("ERROR:Špatné heslo");
                }
            }
            return;
        }

        if (message.startsWith("REGISTER:")) {
            String[] parts = message.split(":", 4);
            if (parts.length == 4 && DatabaseManager.registerUser(parts[1], parts[2], parts[3])) {
                conn.send("REGISTER_OK");
            } else {
                conn.send("ERROR:Registrace selhala");
            }
            return;
        }

        String nick = webClients.get(conn);
        if (nick == null) { conn.send("ERROR:Nepřihlášen"); return; }
        String currentRoom = clientRooms.getOrDefault(conn, "Lobby");

        if (message.equals("/users")) {
            Server.broadcastUserList();
            return;
        }

        if (message.startsWith("/join ")) {
            String newRoom = message.substring(6).trim();
            if(!newRoom.isEmpty()){
                if(!Server.activeRooms.contains(newRoom)) {
                    Server.activeRooms.add(newRoom);
                    Server.broadcastRoomList();
                }
                clientRooms.put(conn, newRoom);
                conn.send("ROOM_CHANGED:" + newRoom);
                sendHistory(conn, newRoom);

                Server.checkTempRooms();
            }
            return;
        }

        if (message.startsWith("/temproom ")) {
            String newRoom = message.substring(10).trim();
            if(!newRoom.isEmpty()){
                Server.createTempRoom(newRoom, nick);
                clientRooms.put(conn, newRoom);
                conn.send("ROOM_CHANGED:" + newRoom);
                Server.checkTempRooms();
            }
            return;
        }

        if (message.startsWith("/burn ")) {
            String[] p = message.substring(6).split(" ", 2);
            if (p.length == 2) {
                try {
                    int seconds = Integer.parseInt(p[0].replace("s", ""));
                    Server.sendBurnMessage(nick, p[1], currentRoom, seconds);
                } catch (Exception e) {
                    conn.send("MSG:0:SYSTEM:Formát: /burn 10s tajny text");
                }
            }
            return;
        }

        if (message.startsWith("START_TIMER:")) {
            try {
                int msgId = Integer.parseInt(message.substring(12).trim());
                Server.timer.schedule(() -> {
                    Server.deleteMessage(msgId);
                }, 10, TimeUnit.SECONDS);
            } catch (Exception e) {}
            return;
        }

        if (message.startsWith("/ttt ")) {
            GameManager.handleGameCommand(nick, message, currentRoom);
            return;
        }

        if (message.startsWith("/w ")) {
            String[] p = message.substring(3).split(" ", 2);
            if (p.length == 2) {
                String targetNick = p[0];
                String whisperMsg = p[1];

                String formattedMsgRx = "🕵️ (šeptá ti): " + whisperMsg;
                String formattedMsgTx = "🕵️ (šeptáš pro " + targetNick + "): " + whisperMsg;

                boolean found = Server.sendToUser(targetNick, "MSG:0:" + nick + ":" + formattedMsgRx);

                if (found) {
                    conn.send("MSG:0:" + nick + ":" + formattedMsgTx);
                } else {
                    conn.send("MSG:0:SYSTEM:Uživatel '" + targetNick + "' není online.");
                }
            }
            return;
        }

        if (message.startsWith("/delmsg ")) {
            try {
                int msgId = Integer.parseInt(message.substring(8).trim());
                Server.deleteMessage(msgId);
            } catch (Exception e) {}
            return;
        }

        if (message.startsWith("/deleteroom ")) {
            if (DatabaseManager.isAdmin(nick)) {
                Server.deleteRoom(message.substring(12).trim());
            } return;
        }

        if (message.startsWith("/kick ")) {
            if (DatabaseManager.isAdmin(nick)) {
                String[] p = message.substring(6).split(" ", 2);
                if (p.length == 2) Server.kickUser(p[0], p[1]);
            } return;
        }

        if (message.startsWith("/mute ")) {
            if (DatabaseManager.isAdmin(nick)) {
                String[] p = message.substring(6).split(" ", 3);
                if (p.length == 3) {
                    try { Server.muteUser(p[0], Integer.parseInt(p[1]), p[2]); } catch (Exception e) {}
                }
            } return;
        }

        if (message.startsWith("/ban ")) {
            if (DatabaseManager.isAdmin(nick)) {
                String[] p = message.substring(5).split(" ", 3);
                if (p.length == 3) {
                    try { Server.banUser(p[0], nick, p[2], Long.parseLong(p[1])); } catch (Exception e) {}
                }
            } return;
        }

        if (message.startsWith("/broadcast ")) {
            if (DatabaseManager.isAdmin(nick)) {
                Server.sendSystemBroadcast(message.substring(11), "ALL");
            } return;
        }

        if (message.startsWith("MSG:")) {
            if (DatabaseManager.isBanned(nick)) {
                conn.send("MSG:0:SYSTEM:⛔ Jsi umlčen/zabanován.");
                return;
            }
            Server.processWebMessage(nick, message.substring(4), currentRoom);
        }
        else if (message.startsWith("FILE:") || message.startsWith("IMG:")) {
            if (DatabaseManager.isBanned(nick)) {
                conn.send("MSG:0:SYSTEM:⛔ Máš MUTE/BAN! Nemůžeš posílat soubory.");
                return;
            }
            Server.processWebMessage(nick, message, currentRoom);
        }
    }

    private void sendHistory(WebSocket conn, String room) {
        for (String h : DatabaseManager.getHistory(room)) {
            if (h.startsWith("MSG:") || h.startsWith("BURN:")) {
                conn.send("HIST:" + h);
            } else {
                conn.send(h);
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (ex.getMessage() != null && !ex.getMessage().contains("EOF")) {
            System.err.println("WS Error: " + ex.getMessage());
        }
    }

    public void broadcastToWeb(String msg, String targetRoom) {
        for (Map.Entry<WebSocket, String> entry : clientRooms.entrySet()) {
            if ((targetRoom == null || entry.getValue().equals(targetRoom)) && entry.getKey().isOpen()) {
                entry.getKey().send(msg);
            }
        }
    }
}