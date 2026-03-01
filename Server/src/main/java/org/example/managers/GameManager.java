package org.example.managers;

import org.example.Server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {

    public static final Map<String, TicTacToeGame> activeTicTacToeGames = new ConcurrentHashMap<>();

    public static class TicTacToeGame {
        public String gameId;
        public String player1;
        public String player2;
        public char[][] board;
        public boolean isPlayer1Turn;

        public TicTacToeGame(String player1, String player2) {
            this.gameId = UUID.randomUUID().toString();
            this.player1 = player1;
            this.player2 = player2;
            this.board = new char[][]{
                    {'-', '-', '-'},
                    {'-', '-', '-'},
                    {'-', '-', '-'}
            };
            this.isPlayer1Turn = true;
        }

        public boolean makeMove(String player, int x, int y) {
            if (x < 0 || x > 2 || y < 0 || y > 2 || board[x][y] != '-') return false;

            if (isPlayer1Turn && player.equals(player1)) {
                board[x][y] = 'X';
                isPlayer1Turn = false;
                return true;
            } else if (!isPlayer1Turn && player.equals(player2)) {
                board[x][y] = 'O';
                isPlayer1Turn = true;
                return true;
            }
            return false;
        }

        public char checkWin() {
            // Kontrola řádků a sloupců
            for (int i = 0; i < 3; i++) {
                if (board[i][0] != '-' && board[i][0] == board[i][1] && board[i][1] == board[i][2]) return board[i][0];
                if (board[0][i] != '-' && board[0][i] == board[1][i] && board[1][i] == board[2][i]) return board[0][i];
            }
            // Kontrola diagonál
            if (board[0][0] != '-' && board[0][0] == board[1][1] && board[1][1] == board[2][2]) return board[0][0];
            if (board[0][2] != '-' && board[0][2] == board[1][1] && board[1][1] == board[2][0]) return board[0][2];
            return '-';
        }

        public boolean isDraw() {
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (board[i][j] == '-') return false;
                }
            }
            return true;
        }

        public String getGameStateData(String status) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    sb.append(board[i][j]);
                }
            }
            String turn = isPlayer1Turn ? player1 : player2;
            return "GAME:TTT:" + gameId + ":" + player1 + ":" + player2 + ":" + turn + ":" + sb.toString() + ":" + status;
        }
    }

    public static void handleGameCommand(String nick, String command, String room) {
        if (command.startsWith("/ttt start ")) {
            String opponent = command.substring(11).trim();

            if (opponent.equalsIgnoreCase(nick)) {
                Server.sendToUser(nick, "MSG:0:SYSTEM:❌ Nemůžeš hrát sám se sebou!");
                return;
            }

            if (isPlayerInGame(nick)) {
                Server.sendToUser(nick, "MSG:0:SYSTEM:❌ Už zrovna hraješ jinou hru!");
                return;
            }
            if (isPlayerInGame(opponent)) {
                Server.sendToUser(nick, "MSG:0:SYSTEM:❌ Hráč " + opponent + " je teď v jiné hře.");
                return;
            }

            if (Server.isUserOnline(opponent)) {
                TicTacToeGame newGame = new TicTacToeGame(nick, opponent);
                activeTicTacToeGames.put(room, newGame);
                Server.broadcastGame(newGame.getGameStateData("PLAYING"), room);
            } else {
                Server.sendToUser(nick, "MSG:0:SYSTEM:Hráč " + opponent + " není online.");
            }
            return;
        }

        if (command.startsWith("/ttt tah ")) {
            TicTacToeGame game = activeTicTacToeGames.get(room);
            if (game == null) {
                Server.sendToUser(nick, "MSG:0:SYSTEM:V této místnosti neprobíhá žádná hra.");
                return;
            }

            try {
                String[] parts = command.substring(9).trim().split(" ");
                int r = Integer.parseInt(parts[0]);
                int c = Integer.parseInt(parts[1]);

                if (game.makeMove(nick, r, c)) {
                    char winner = game.checkWin();
                    if (winner != '-') {
                        String winnerName = (winner == 'X') ? game.player1 : game.player2;
                        Server.broadcastGame(game.getGameStateData(winner == 'X' ? "WIN1" : "WIN2"), room);
                        Server.sendSystemBroadcast("🏆 Hráč " + winnerName + " vyhrál Piškvorky a získává 100 XP!", room);
                        DatabaseManager.addXp(winnerName, 100);
                        Server.broadcastUserList();
                        activeTicTacToeGames.remove(room);
                    } else if (game.isDraw()) {
                        Server.broadcastGame(game.getGameStateData("DRAW"), room);
                        Server.sendSystemBroadcast("🤝 Piškvorky skončily remízou!", room);
                        activeTicTacToeGames.remove(room);
                    } else {
                        Server.broadcastGame(game.getGameStateData("PLAYING"), room);
                    }
                } else {
                    Server.sendToUser(nick, "MSG:0:SYSTEM:❌ Neplatný tah nebo nejsi na řadě!");
                }
            } catch (Exception e) {
                Server.sendToUser(nick, "MSG:0:SYSTEM:Chyba formátu.");
            }
        }
    }

    public static boolean isPlayerInGame(String playerName) {
        for (TicTacToeGame game : activeTicTacToeGames.values()) {
            if (game.player1.equalsIgnoreCase(playerName) || game.player2.equalsIgnoreCase(playerName)) {
                return true;
            }
        }
        return false;
    }

    // 🔥 Nová metoda pro čisté startování
    public static void startGame(String player1, String player2, String room) {
        TicTacToeGame newGame = new TicTacToeGame(player1, player2);
        activeTicTacToeGames.put(room, newGame);
        Server.broadcastGame(newGame.getGameStateData("PLAYING"), room);
    }
}