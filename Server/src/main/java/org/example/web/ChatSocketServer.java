package org.example.web;

import org.example.Server;
import org.example.managers.CryptoRSA;
import org.example.managers.DatabaseManager;
import org.example.managers.GameManager;
import org.example.managers.InviteManager;
import org.example.managers.WhiteboardManager;
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
                    conn.send("ROOM_LIST:" + Server.getCustomRoomList(u));
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

        // 👉 PŘIDÁNO: Zpracování AVATARA přímo tady
        if (message.startsWith("SET_AVATAR:")) {
            System.out.println("📸 LOG: Přijat požadavek na avatar z WEBU od: " + nick);
            String base64 = message.substring(11);
            try {
                java.io.File avatarDir = new java.io.File("avatars");
                if (!avatarDir.exists()) avatarDir.mkdirs();

                byte[] imageBytes = java.util.Base64.getDecoder().decode(base64);
                String fileName = nick + ".jpg";
                java.io.File avatarFile = new java.io.File(avatarDir, fileName);
                java.nio.file.Files.write(avatarFile.toPath(), imageBytes);

                if (DatabaseManager.saveAvatar(nick, fileName)) {
                    long timestamp = System.currentTimeMillis();
                    Server.broadcastRaw("UPDATE_AVATAR:" + nick + ":" + fileName + "?t=" + timestamp, currentRoom);
                    System.out.println("📸 LOG: Avatar úspěšně uložen pro: " + nick);
                }
            } catch (Exception e) {
                System.err.println("📸 ERROR: Chyba při ukládání avatara z webu: " + e.getMessage());
            }
            return; // Konec, tohle není zpráva do chatu
        }

        if (message.startsWith("TYPING:")) {
            String status = message.substring(7);
            Server.broadcastRaw("USER_TYPING:" + nick + ":" + status, currentRoom);
            return;
        }

        if (message.startsWith("GAME:WB:")) {
            if (message.startsWith("GAME:WB:CLOSE:")) {
                String id = message.substring(14);
                WhiteboardManager.SharedBoard board = WhiteboardManager.activeBoards.get(id);
                if (board != null && board.p1.equals(nick)) {
                    WhiteboardManager.activeBoards.remove(id);
                    Server.broadcastGame(message, currentRoom);
                } else {
                    conn.send("MSG:0:SYSTEM:❌ Pouze zakladatel (" + (board != null ? board.p1 : "?") + ") může zavřít plátno!");
                }
                return;
            }
            Server.broadcastGame(message, currentRoom);
            return;
        }

        if (message.equals("/users")) {
            Server.broadcastUserList();
            return;
        }

        if (message.startsWith("/join ")) {
            String newRoom = message.substring(6).trim();
            if(!newRoom.isEmpty()){
                if (Server.canJoinRoom(nick, newRoom)) {
                    if(!Server.activeRooms.contains(newRoom)) {
                        Server.activeRooms.add(newRoom);
                        Server.broadcastRoomList();
                    }
                    clientRooms.put(conn, newRoom);
                    conn.send("ROOM_CHANGED:" + newRoom);
                    sendHistory(conn, newRoom);
                    Server.checkTempRooms();
                } else {
                    conn.send("MSG:0:SYSTEM:❌ Nemáš přístup do této soukromé místnosti!");
                }
            }
            return;
        }

        if (message.startsWith("/temproom ")) {
            String newRoom = message.substring(10).trim();
            if(!newRoom.isEmpty()){
                if (Server.createTempRoom(newRoom, nick)) {
                    clientRooms.put(conn, newRoom);
                    conn.send("ROOM_CHANGED:" + newRoom);
                    sendHistory(conn, newRoom);
                    Server.checkTempRooms();
                } else {
                    conn.send("MSG:0:SYSTEM:❌ Místnost '" + newRoom + "' už existuje! Zvol jiný název.");
                }
            }
            return;
        }

        if (message.startsWith("/createprivate ")) {
            String newRoom = message.substring(15).trim();
            if(!newRoom.isEmpty()){
                if (Server.createPrivateRoom(newRoom, nick)) {
                    clientRooms.put(conn, newRoom);
                    conn.send("ROOM_CHANGED:" + newRoom);
                    sendHistory(conn, newRoom);
                    Server.checkTempRooms();
                } else {
                    conn.send("MSG:0:SYSTEM:⚠️ Místnost s tímto názvem už existuje.");
                }
            }
            return;
        }

        if (message.startsWith("/roominvite ")) {
            String target = message.substring(12).trim();
            if (Server.privateRooms.containsKey(currentRoom)) {
                Server.PrivateRoom pr = Server.privateRooms.get(currentRoom);
                if (pr.host.equalsIgnoreCase(nick) || DatabaseManager.isAdmin(nick)) {
                    if (Server.isUserOnline(target)) {
                        pr.allowedUsers.add(target);
                        Server.broadcastRoomList();
                        Server.sendToUser(target, "MSG:0:SYSTEM:📩 Byl jsi pozván do soukromé místnosti '" + currentRoom + "'! Nyní ji vidíš v seznamu.");
                        conn.send("MSG:0:SYSTEM:✅ Uživatel " + target + " byl pozván do místnosti.");
                    } else {
                        conn.send("MSG:0:SYSTEM:❌ Uživatel " + target + " není online.");
                    }
                } else {
                    conn.send("MSG:0:SYSTEM:⛔ Jen hostitel (" + pr.host + ") může zvát další lidi!");
                }
            } else {
                conn.send("MSG:0:SYSTEM:⚠️ Tato místnost není soukromá!");
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
            if (message.startsWith("/ttt start ")) {
                String opponent = message.substring(11).trim();
                InviteManager.handleInviteRequest(nick, opponent, "TTT", currentRoom);
            } else if (message.startsWith("/ttt tah ")) {
                GameManager.handleGameCommand(nick, message, currentRoom);
            }
            return;
        }

        if (message.startsWith("/wb ")) {
            if (message.trim().equals("/wb room")) {
                WhiteboardManager.startRoom(nick, currentRoom);
            } else if (message.startsWith("/wb start ")) {
                String opponent = message.substring(10).trim();
                InviteManager.handleInviteRequest(nick, opponent, "WB", currentRoom);
            }
            return;
        }

        if (message.startsWith("/invite ")) {
            String[] p = message.substring(8).trim().split(" ");
            if (p.length == 2) {
                if (p[0].equals("accept")) InviteManager.acceptInvite(nick, p[1]);
                else if (p[0].equals("decline")) InviteManager.declineInvite(nick, p[1]);
            }
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

        if (message.startsWith("MSG:") || message.startsWith("ZK:")) {
            if (DatabaseManager.isBanned(nick)) {
                conn.send("MSG:0:SYSTEM:⛔ Jsi umlčen/zabanován.");
                return;
            }

            // Pokud zpráva začíná na MSG:, odstraníme prefix
            String contentToProcess = message.startsWith("MSG:") ? message.substring(4) : message;

            Server.processWebMessage(nick, contentToProcess, currentRoom);
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