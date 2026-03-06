package org.example.threads;

import org.example.Server;
import org.example.managers.CommandHandler;
import org.example.managers.CryptoUtils;
import org.example.managers.DatabaseManager;
import org.example.managers.WhiteboardManager;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PublicKey;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ServerThread implements Runnable {
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String nick = null;
    private PublicKey clientPublicKey;

    private String currentRoom = "Lobby";
    private boolean isAdmin = false;
    private long mutedUntil = 0;

    private long lastMessageTime = 0;
    private int spamWarnings = 0;
    private long spamWindowStart = 0;
    private int msgCountInWindow = 0;
    private boolean isRunning = true;

    public ServerThread(Socket socket) {
        this.socket = socket;
    }

    public String getClientName() { return nick; }
    public String getCurrentRoom() { return currentRoom; }
    public boolean isAdmin() { return isAdmin; }

    public void setCurrentRoom(String roomName) {
        this.currentRoom = roomName;
        sendEncryptedMessage("ROOM_CHANGED:" + roomName);

        List<String> history = DatabaseManager.getHistory(roomName);
        for (String h : history) {
            if (h.startsWith("MSG:") || h.startsWith("BURN:")) {
                sendEncryptedMessage(h);
            } else {
                sendRawMessage(h);
            }
        }

        Server.checkTempRooms();
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            System.out.println("✅ New connection from " + socket.getInetAddress());

            if (Server.serverPublicKey != null) {
                String serverKey = Base64.getEncoder().encodeToString(Server.serverPublicKey.getEncoded());
                sendRawMessage("PUBKEY:" + serverKey);
                System.out.println("📤 Server public key sent to client.");
            } else {
                System.err.println("❌ ERROR: Server keys missing!");
                return;
            }

            String rawLine;
            while (isRunning && (rawLine = in.readLine()) != null) {
                if (rawLine.startsWith("PUBKEY:")) {
                    try {
                        String keyData = rawLine.substring(7);
                        this.clientPublicKey = CryptoUtils.getPublicKeyFromBytes(keyData);
                        System.out.println("🔑 Client key received and stored.");
                    } catch (Exception e) {
                        System.out.println("❌ Client key error: " + e.getMessage());
                    }
                    continue;
                }

                String line = rawLine;
                boolean isEncrypted = false;
                try {
                    line = CryptoUtils.decrypt(rawLine, Server.serverPrivateKey);
                    isEncrypted = true;
                } catch (Exception e) {
                }

                if (nick == null) {
                    if (isEncrypted) processLogin(line);
                    else sendRawMessage("AUTH_REQ:/login");
                } else {
                    processMessage(rawLine, line, isEncrypted);
                }
            }

        } catch (Exception e) {
            System.out.println("🔌 Client disconnected: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void processLogin(String line) {
        String[] parts = line.split(":", 4);
        String cmd = parts[0];

        if (cmd.equals("LOGIN") && parts.length >= 3) {
            String user = parts[1];
            String pass = parts[2];

            if (DatabaseManager.loginUser(user, pass)) {
                if (DatabaseManager.isBanned(user)) {
                    sendEncryptedMessage("AUTH_FAIL:⛔ Máš BAN! " + DatabaseManager.getBanDetails(user));
                    return;
                }
                this.nick = user;
                this.isAdmin = DatabaseManager.isAdmin(user);

                sendEncryptedMessage("LOGIN_OK:" + (isAdmin ? "ADMIN" : "USER"));

                Server.registerClient(nick, this);
                joinRoom("Lobby");

            } else {
                sendEncryptedMessage("AUTH_FAIL:❌ Špatné jméno nebo heslo.");
            }
        } else if (cmd.equals("REGISTER") && parts.length >= 4) {
            if (DatabaseManager.registerUser(parts[1], parts[2], parts[3])) {
                this.nick = parts[1];
                sendEncryptedMessage("LOGIN_OK:" + (isAdmin ? "ADMIN" : "USER"));

                Server.registerClient(nick, this);
                joinRoom("Lobby");
            } else {
                sendEncryptedMessage("AUTH_FAIL:❌ Uživatel již existuje.");
            }
        }
        // 👇 PŘIDÁNO: Zpracování obnovy hesla 👇
        else if (cmd.equals("RECOVER") && parts.length >= 4) {
            String user = parts[1];
            String code = parts[2];
            String newPass = parts[3];

            // Tato metoda musí existovat v DatabaseManager
            if (DatabaseManager.resetPassword(user, newPass, code)) {
                sendEncryptedMessage("RECOVER_OK");
            } else {
                sendEncryptedMessage("AUTH_FAIL:❌ Špatné jméno nebo Recovery Kód!");
            }
        }
    }

    private void processMessage(String rawInput, String decryptedLine, boolean wasEncrypted) {

        if (rawInput.startsWith("FILE:") || rawInput.startsWith("IMG:")) {
            if (System.currentTimeMillis() < mutedUntil) {
                long remaining = (mutedUntil - System.currentTimeMillis()) / 1000;
                sendEncryptedMessage("MSG:0:SYSTEM:🤐 Nemůžeš posílat soubory ani GIFy. Jsi ztlumen. Zbývá: " + remaining + "s");
                return;
            }
            handleFileTransfer(rawInput);
            return;
        }

        if (rawInput.startsWith("GAME:WB:")) {
            if (rawInput.startsWith("GAME:WB:CLOSE:")) {
                String id = rawInput.substring(14);
                WhiteboardManager.SharedBoard board = WhiteboardManager.activeBoards.get(id);
                if (board != null && board.p1.equals(this.nick)) {
                    WhiteboardManager.activeBoards.remove(id);
                    Server.broadcastGame(rawInput, currentRoom);
                } else {
                    sendEncryptedMessage("MSG:0:SYSTEM:❌ Pouze zakladatel (" + (board != null ? board.p1 : "?") + ") může zavřít plátno!");
                }
                return;
            }
            Server.broadcastGame(rawInput, currentRoom);
            return;
        }

        if (rawInput.startsWith("SET_AVATAR:")) {
            System.out.println("📸 LOG: Přijat požadavek na avatar od uživatele: " + this.nick);
            String base64 = rawInput.substring(11);
            try {
                // 1. Vytvoříme složku
                File avatarDir = new File("avatars");
                if (!avatarDir.exists()) {
                    avatarDir.mkdirs();
                    System.out.println("📸 LOG: Vytvořena složka pro avatary na cestě: " + avatarDir.getAbsolutePath());
                }

                // 2. Uložení na disk
                byte[] imageBytes = Base64.getDecoder().decode(base64);
                String fileName = this.nick + ".jpg";
                File avatarFile = new File(avatarDir, fileName);
                java.nio.file.Files.write(avatarFile.toPath(), imageBytes);
                System.out.println("📸 LOG: Obrázek fyzicky uložen do: " + avatarFile.getAbsolutePath());

                // 3. Uložení do DB
                if (DatabaseManager.saveAvatar(this.nick, fileName)) {
                    System.out.println("📸 LOG: Úspěšně zapsáno do databáze pro uživatele " + this.nick);
                    long timestamp = System.currentTimeMillis();
                    Server.sendDirectMessage("UPDATE_AVATAR:" + this.nick + ":" + fileName + "?t=" + timestamp, this.currentRoom);
                } else {
                    System.err.println("📸 ERROR: Metoda DatabaseManager.saveAvatar() vrátila FALSE! Jméno se neshoduje?");
                }
            } catch (Exception e) {
                System.err.println("📸 CRITICAL ERROR: Chyba při zpracování avatara:");
                e.printStackTrace();
            }
            return;
        }

        if (rawInput.startsWith("TYPING:")) {
            String status = rawInput.substring(7);
            Server.broadcastRaw("USER_TYPING:" + this.nick + ":" + status, this.currentRoom);
            return;
        }

        if (rawInput.startsWith("ZK:")) {
            Server.sendChatMessage(this.nick, rawInput, this.currentRoom);
            return;
        }

        if (!wasEncrypted) return;

        if (decryptedLine.startsWith("START_TIMER:")) {
            try {
                int msgId = Integer.parseInt(decryptedLine.substring(12).trim());
                Server.timer.schedule(() -> {
                    Server.deleteMessage(msgId);
                }, 10, TimeUnit.SECONDS);
            } catch (Exception e) {}
            return;
        }

        if (Server.globalMathResult != null) {
            if (Server.solveMath(decryptedLine, this.nick)) {
                return;
            }
        }

        long now = System.currentTimeMillis();
        if (now - spamWindowStart > 1000) {
            spamWindowStart = now;
            msgCountInWindow = 0;
        }
        msgCountInWindow++;

        if (!isAdmin && msgCountInWindow > 10) {
            spamWarnings++;
            if (spamWarnings >= 3) {
                mute(60, "Auto-Mute: Spam");
                spamWarnings = 0;
                msgCountInWindow = 0;
                return;
            } else {
                sendEncryptedMessage("MSG:0:SYSTEM:⚠️ Spamuješ! (" + spamWarnings + "/3)");
                return;
            }
        }

        if (decryptedLine.startsWith("/")) {
            if (decryptedLine.equalsIgnoreCase("/quit")) {
                disconnect();
                return;
            }
            CommandHandler.handle(this, decryptedLine);
            return;
        }

        if (System.currentTimeMillis() < mutedUntil) {
            long remaining = (mutedUntil - System.currentTimeMillis()) / 1000;
            sendEncryptedMessage("MSG:0:SYSTEM:🤐 Jsi ztlumen. Zbývá: " + remaining + "s");
        } else {
            Server.sendChatMessage(this.nick, decryptedLine, this.currentRoom);
            Server.checkAndGenerateMath();
        }
    }

    private void handleFileTransfer(String rawInput) {
        String[] p = rawInput.split(":", 4);
        if (p.length == 4) {
            String sender = p[1];
            String fileName = p[2];
            String base64 = p[3];
            String type = rawInput.startsWith("IMG:") ? "IMG" : "FILE";

            File tempFile = null;
            try {
                byte[] data = Base64.getDecoder().decode(base64);
                tempFile = File.createTempFile("upload_" + sender + "_", ".tmp");
                Files.write(tempFile.toPath(), data);

                System.out.println("🔍 Scanning file: " + fileName);
                if (isFileSafe(tempFile)) {
                    String time = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
                    int msgId = DatabaseManager.saveFullMessage(sender, fileName, base64, type, time, currentRoom);
                    String newPacket = (type.equals("IMG") ? "IMG:" : "FILE:") + msgId + ":" + sender + ":" + fileName + ":" + base64;
                    Server.sendDirectMessage(newPacket, currentRoom);
                    DatabaseManager.addXp(sender, 15);
                    sendEncryptedMessage("MSG:0:SYSTEM:✅ File '" + fileName + "' sent.");
                } else {
                    System.err.println("🚨 VIRUS DETECTED! User: " + sender + ", File: " + fileName);
                    sendEncryptedMessage("MSG:0:SYSTEM:⛔ WARNING! File '" + fileName + "' blocked by antivirus!");
                }

            } catch (IOException e) {
                e.printStackTrace();
                sendEncryptedMessage("MSG:0:SYSTEM:❌ Error processing file.");
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        }
    }

    private boolean isFileSafe(File file) {
        try {
            String defenderPath = "C:\\Program Files\\Windows Defender\\MpCmdRun.exe";
            if (!new File(defenderPath).exists()) return true;

            ProcessBuilder pb = new ProcessBuilder(defenderPath, "-Scan", "-ScanType", "3", "-File", file.getAbsolutePath());
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode != 2;
        } catch (Exception e) {
            return true;
        }
    }

    public void joinRoom(String roomName) {
        setCurrentRoom(roomName);
    }

    public void mute(long seconds, String reason) {
        this.mutedUntil = System.currentTimeMillis() + (seconds * 1000L);
        sendEncryptedMessage("MUTE_START:" + seconds);
        sendEncryptedMessage("MSG:0:SYSTEM:You have been muted for " + seconds + "s. Reason: " + reason);
    }

    public void disconnect() {
        isRunning = false;
        try { if (socket != null) socket.close(); } catch (IOException e) {}
        Server.removeClient(nick);
    }

    public void sendRawMessage(String msg) {
        if (out != null) out.println(msg);
    }

    public void sendEncryptedMessage(String msg) {
        try {
            if (clientPublicKey != null) {
                String encrypted = CryptoUtils.encrypt(msg, clientPublicKey);
                out.println(encrypted);
            } else {
                out.println(msg);
            }
        } catch (Exception e) {
            System.err.println("Encryption error: " + e.getMessage());
        }
    }
}