package org.example.managers;

import org.example.Server;
import org.example.threads.ServerThread;

public class CommandHandler {

    public static void handle(ServerThread client, String command) {
        // Odstraníme lomítko na začátku
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        String[] parts = command.split(" ", 2); // Rozdělíme na PŘÍKAZ a ARGUMENTY
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "join":
            case "room":
                if (!args.isEmpty()) {
                    client.joinRoom(args);
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:Musíš zadat název místnosti! (např. /join Sport)");
                }
                break;

            case "temproom":
                if (!args.isEmpty()) {
                    String newRoom = args.trim();
                    Server.createTempRoom(newRoom, client.getClientName());
                    client.joinRoom(newRoom);
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:Musíš zadat název! (/temproom Název)");
                }
                break;

            case "rooms":
            case "getrooms":
                String roomList = String.join(",", Server.activeRooms);
                client.sendEncryptedMessage("ROOM_LIST:" + roomList);
                break;

            case "create":
            case "createroom":
                if (!args.isEmpty()) {
                    String newRoom = args.trim();
                    if (!Server.activeRooms.contains(newRoom)) {
                        Server.activeRooms.add(newRoom);
                        client.sendEncryptedMessage("MSG:0:SYSTEM:✅ Místnost '" + newRoom + "' byla vytvořena.");
                        Server.broadcastRoomList();
                    } else {
                        client.sendEncryptedMessage("MSG:0:SYSTEM:⚠️ Místnost s tímto názvem už existuje.");
                    }
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:Musíš zadat název! (/create Název)");
                }
                break;

            case "users":
            case "online":
            case "list":
                // Změněno na metodu s levely z naší aktualizace
                String[] users = Server.getUserListWithLevels();
                int count = Server.getOnlineCount();
                client.sendEncryptedMessage("MSG:0:SYSTEM:Online (" + count + "): " + String.join(", ", users));
                break;

            case "msg":
            case "w":
            case "whisper":
                String[] whisperParts = args.split(" ", 2);
                if (whisperParts.length > 1) {
                    String target = whisperParts[0];
                    String msg = whisperParts[1];
                    Server.whisper(client, target, msg);
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:Použití: /w Jméno Zpráva");
                }
                break;

            case "burn":
                String[] burnParts = args.split(" ", 2);
                if (burnParts.length == 2) {
                    try {
                        int sec = Integer.parseInt(burnParts[0].replace("s", ""));
                        Server.sendBurnMessage(client.getClientName(), burnParts[1], client.getCurrentRoom(), sec);
                    } catch (NumberFormatException e) {
                        client.sendEncryptedMessage("MSG:0:SYSTEM:Formát času je špatný. Použij např. /burn 10 tajná zpráva");
                    }
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:Použití: /burn [sekundy] [zpráva]");
                }
                break;

            case "ttt":
                if (!args.isEmpty()) {
                    GameManager.handleGameCommand(client.getClientName(), "/ttt " + args, client.getCurrentRoom());
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:Použití: /ttt start [soupeř] nebo /ttt tah [řádek] [sloupec]");
                }
                break;

            // 🔥 MANUÁLNÍ SPUŠTĚNÍ MATEMATIKY (jen pro Admina)
            case "math":
                if (client.isAdmin()) {
                    // Resetujeme časovač a smažeme aktuální výsledek, aby se příklad vygeneroval hned
                    Server.lastMathTime = 0;
                    Server.globalMathResult = null;

                    // Zavoláme generování
                    Server.checkAndGenerateMath();
                    // Log pro admina
                    client.sendEncryptedMessage("MSG:0:SYSTEM:🧮 Matematický příklad byl ručně spuštěn.");
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:⛔ Tento příkaz může použít pouze Admin.");
                }
                break;

            // --- ADMIN PŘÍKAZY ---

            case "kick":
                if (client.isAdmin()) {
                    if (!args.isEmpty()) {
                        Server.kickUser(args, "Vyhozen administrátorem " + client.getClientName());
                    } else {
                        client.sendEncryptedMessage("MSG:0:SYSTEM:Použití: /kick Jméno");
                    }
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:⛔ Nemáš práva admina!");
                }
                break;

            case "ban":
                if (client.isAdmin()) {
                    String[] banParts = args.split(" ", 2);
                    if(banParts.length > 0) {
                        String target = banParts[0];
                        String reason = banParts.length > 1 ? banParts[1] : "Porušení pravidel";
                        Server.banUser(target, client.getClientName(), reason, 300);
                    } else {
                        client.sendEncryptedMessage("MSG:0:SYSTEM:Použití: /ban Jméno [Důvod]");
                    }
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:⛔ Nemáš práva admina!");
                }
                break;

            case "unban":
                if (client.isAdmin()) {
                    if (!args.isEmpty()) {
                        if (Server.unbanUser(args)) {
                            client.sendEncryptedMessage("MSG:0:SYSTEM:✅ Uživatel " + args + " byl odblokován.");
                        } else {
                            client.sendEncryptedMessage("MSG:0:SYSTEM:⚠️ Uživatel " + args + " nebyl nalezen nebo nemá ban.");
                        }
                    } else {
                        client.sendEncryptedMessage("MSG:0:SYSTEM:Použití: /unban Jméno");
                    }
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:⛔ Nemáš práva admina!");
                }
                break;

            case "mute":
                if (client.isAdmin()) {
                    String[] muteParts = args.split(" ", 3);
                    if (muteParts.length >= 2) {
                        String target = muteParts[0];
                        try {
                            int seconds = Integer.parseInt(muteParts[1]);
                            String reason = muteParts.length > 2 ? muteParts[2] : "Nevhodné chování";
                            Server.muteUser(target, seconds, reason);
                            client.sendEncryptedMessage("MSG:0:SYSTEM:✅ Uživatel " + target + " byl umlčen na " + seconds + "s.");
                        } catch (NumberFormatException e) {
                            client.sendEncryptedMessage("MSG:0:SYSTEM:⚠️ Čas musí být číslo!");
                        }
                    } else {
                        client.sendEncryptedMessage("MSG:0:SYSTEM:Použití: /mute Jméno Čas(s) [Důvod]");
                    }
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:⛔ Nemáš práva admina!");
                }
                break;

            case "delmsg":
            case "deletemessage":
                if (client.isAdmin()) {
                    try {
                        int msgId = Integer.parseInt(args.trim());
                        Server.deleteMessage(msgId);
                        client.sendEncryptedMessage("MSG:0:SYSTEM:✅ Zpráva #" + msgId + " smazána.");
                    } catch (NumberFormatException e) {
                        client.sendEncryptedMessage("MSG:0:SYSTEM:⚠️ ID zprávy musí být číslo!");
                    }
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:⛔ Nemáš práva admina!");
                }
                break;

            case "deleteroom":
                if (client.isAdmin()) {
                    if (!args.isEmpty()) {
                        Server.deleteRoom(args);
                    } else {
                        client.sendEncryptedMessage("MSG:0:SYSTEM:Použití: /deleteroom Název");
                    }
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:⛔ Nemáš práva admina!");
                }
                break;

            case "broadcast":
                if (client.isAdmin()) {
                    if (!args.isEmpty()) {
                        Server.sendSystemBroadcast(args, "ALL");
                    } else {
                        client.sendEncryptedMessage("MSG:0:SYSTEM:Použití: /broadcast [zpráva]");
                    }
                } else {
                    client.sendEncryptedMessage("MSG:0:SYSTEM:⛔ Nemáš práva admina!");
                }
                break;

            case "help":
                sendHelp(client);
                break;

            default:
                client.sendEncryptedMessage("MSG:0:SYSTEM:Neznámý příkaz: " + cmd);
                break;
        }
    }

    public static void sendHelp(ServerThread client) {
        client.sendEncryptedMessage("MSG:0:SYSTEM:=== NÁPOVĚDA ===");
        client.sendEncryptedMessage("MSG:0:SYSTEM:/join [místnost] - Změní místnost");
        client.sendEncryptedMessage("MSG:0:SYSTEM:/create [název] - Vytvoří místnost");
        client.sendEncryptedMessage("MSG:0:SYSTEM:/temproom [název] - Dočasná místnost");
        client.sendEncryptedMessage("MSG:0:SYSTEM:/users - Seznam online lidí");
        client.sendEncryptedMessage("MSG:0:SYSTEM:/w [nick] [zpráva] - Soukromá zpráva");
        client.sendEncryptedMessage("MSG:0:SYSTEM:/burn [s] [text] - Zpráva, která se po přečtení smaže");
        client.sendEncryptedMessage("MSG:0:SYSTEM:/ttt start [nick] - Začne hru Piškvorky");

        if (client.isAdmin()) {
            client.sendEncryptedMessage("MSG:0:SYSTEM: ");
            client.sendEncryptedMessage("MSG:0:SYSTEM:=== ADMIN PŘÍKAZY ===");
            client.sendEncryptedMessage("MSG:0:SYSTEM:/math - Spustí matematický příklad");
            client.sendEncryptedMessage("MSG:0:SYSTEM:/kick [nick] - Vyhodit uživatele");
            client.sendEncryptedMessage("MSG:0:SYSTEM:/ban [nick] [důvod] - Zabanovat");
            client.sendEncryptedMessage("MSG:0:SYSTEM:/unban [nick] - Odblokovat");
            client.sendEncryptedMessage("MSG:0:SYSTEM:/mute [nick] [sekundy] - Umlčet");
            client.sendEncryptedMessage("MSG:0:SYSTEM:/delmsg [ID] - Smazat zprávu");
            client.sendEncryptedMessage("MSG:0:SYSTEM:/deleteroom [název] - Zrušit místnost");
            client.sendEncryptedMessage("MSG:0:SYSTEM:/broadcast [text] - Globální oznámení");
        }

        client.sendEncryptedMessage("MSG:0:SYSTEM:==================");
    }
}