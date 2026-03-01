package org.example.managers;

import org.example.Server;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class InviteManager {
    public static final Map<String, Invite> pendingInvites = new ConcurrentHashMap<>();

    public static class Invite {
        public String id;
        public String sender;
        public String target;
        public String type; // "TTT" nebo "WB"
        public String room;

        public Invite(String sender, String target, String type, String room) {
            this.id = UUID.randomUUID().toString();
            this.sender = sender;
            this.target = target;
            this.type = type;
            this.room = room;
        }
    }

    public static void handleInviteRequest(String sender, String target, String type, String room) {
        if (target.equalsIgnoreCase(sender)) {
            Server.sendToUser(sender, "MSG:0:SYSTEM:❌ Nemůžeš vyzvat sám sebe!");
            return;
        }

        if (!Server.isUserOnline(target)) {
            Server.sendToUser(sender, "MSG:0:SYSTEM:❌ Hráč " + target + " není online.");
            return;
        }

        if (GameManager.isPlayerInGame(target) || GameManager.isPlayerInGame(sender)) {
            Server.sendToUser(sender, "MSG:0:SYSTEM:❌ Někdo z vás už aktuálně hraje hru.");
            return;
        }

        for (Invite inv : pendingInvites.values()) {
            if (inv.target.equalsIgnoreCase(target) || inv.sender.equalsIgnoreCase(sender)) {
                Server.sendToUser(sender, "MSG:0:SYSTEM:❌ Ty nebo cíl už máte nevyřízenou pozvánku.");
                return;
            }
        }

        Invite invite = new Invite(sender, target, type, room);
        pendingInvites.put(invite.id, invite);

        // 🔥 OPRAVA: Zabaleno do MSG:0:SYSTEM:, aby to propustil i Java Desktop!
        Server.sendToUser(target, "MSG:0:SYSTEM:INVITE:RECEIVE:" + invite.id + ":" + sender + ":" + type);
        Server.sendToUser(sender, "MSG:0:SYSTEM:⏳ Čekám na přijetí výzvy od hráče " + target + "...");

        // Časovač na vypršení pozvánky (30 sekund)
        Server.timer.schedule(() -> {
            if (pendingInvites.containsKey(invite.id)) {
                pendingInvites.remove(invite.id);
                Server.sendToUser(sender, "MSG:0:SYSTEM:⏱️ Výzva pro hráče " + target + " vypršela.");
                Server.sendToUser(target, "MSG:0:SYSTEM:INVITE:CANCEL:" + invite.id);
            }
        }, 30, TimeUnit.SECONDS);
    }

    public static void acceptInvite(String target, String inviteId) {
        Invite inv = pendingInvites.remove(inviteId);
        if (inv != null && inv.target.equalsIgnoreCase(target)) {
            Server.sendToUser(inv.sender, "MSG:0:SYSTEM:✅ Hráč " + target + " přijal výzvu!");

            if (inv.type.equals("TTT")) {
                GameManager.startGame(inv.sender, inv.target, inv.room);
            } else if (inv.type.equals("WB")) {
                WhiteboardManager.startGame(inv.sender, inv.target, inv.room);
            }
        }
    }

    public static void declineInvite(String target, String inviteId) {
        Invite inv = pendingInvites.remove(inviteId);
        if (inv != null && inv.target.equalsIgnoreCase(target)) {
            Server.sendToUser(inv.sender, "MSG:0:SYSTEM:❌ Hráč " + target + " tvou výzvu odmítl.");
        }
    }
}