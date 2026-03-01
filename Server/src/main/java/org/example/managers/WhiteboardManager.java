package org.example.managers;

import org.example.Server;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WhiteboardManager {
    public static final Map<String, SharedBoard> activeBoards = new ConcurrentHashMap<>();

    public static class SharedBoard {
        public String id;
        public String p1;
        public String p2;

        public SharedBoard(String p1, String p2) {
            this.id = UUID.randomUUID().toString();
            this.p1 = p1;
            this.p2 = p2;
        }

        public String getStartPacket() {
            return "GAME:WB:START:" + id + ":" + p1 + ":" + p2;
        }
    }

    public static void startGame(String p1, String p2, String room) {
        SharedBoard board = new SharedBoard(p1, p2);
        activeBoards.put(board.id, board);
        Server.broadcastGame(board.getStartPacket(), room);
        Server.sendSystemBroadcast("🎨 Uživatelé " + p1 + " a " + p2 + " začali sdílet plátno!", room);
    }

    public static void startRoom(String p1, String room) {
        SharedBoard board = new SharedBoard(p1, "ROOM");
        activeBoards.put(board.id, board);
        Server.broadcastGame(board.getStartPacket(), room);
        Server.sendSystemBroadcast("🎨 Uživatel " + p1 + " vytvořil volné plátno pro celou místnost!", room);
    }
}