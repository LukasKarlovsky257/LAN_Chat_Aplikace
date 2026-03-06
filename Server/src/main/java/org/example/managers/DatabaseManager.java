package org.example.managers;

import org.sqlite.SQLiteConfig;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String URL = "jdbc:sqlite:chat_secure.db";

    // --- 1. JEDNOTNÉ PŘIPOJENÍ S KONFIGURACÍ ---
    private static Connection connect() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setEncoding(SQLiteConfig.Encoding.UTF8);
        config.setBusyTimeout(3000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);

        return DriverManager.getConnection(URL, config.toProperties());
    }

    // --- 2. INICIALIZACE ---
    // --- 2. INICIALIZACE ---
    public static void initDatabase() {
        String sqlUsers = "CREATE TABLE IF NOT EXISTS users (\n"
                + " id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + " username TEXT UNIQUE NOT NULL,\n"
                + " password TEXT NOT NULL,\n"
                + " role INTEGER DEFAULT 0,\n"
                + " recovery_hash TEXT,\n"
                + " is_banned INTEGER DEFAULT 0,\n"
                + " banned_until INTEGER DEFAULT -1,\n"
                + " banned_by TEXT DEFAULT 'SYSTEM',\n"
                + " ban_reason TEXT DEFAULT '',\n"
                + " xp INTEGER DEFAULT 0,\n"
                + " level INTEGER DEFAULT 1,\n"
                + " avatar_base64 TEXT\n" // Nový sloupec pro avatary
                + ");";

        String sqlMessages = "CREATE TABLE IF NOT EXISTS messages (\n"
                + " id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + " sender TEXT NOT NULL,\n"
                + " message TEXT NOT NULL,\n"
                + " file_data TEXT,\n"
                + " type TEXT DEFAULT 'TEXT',\n"
                + " timestamp TEXT NOT NULL,\n"
                + " room TEXT DEFAULT 'Lobby',\n"
                + " is_burnable INTEGER DEFAULT 0\n"
                + ");";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlUsers);
            stmt.execute(sqlMessages);
            System.out.println("LOG: Databáze byla úspěšně inicializována.");
            migrovatSloupce(stmt);
        } catch (SQLException e) {
            System.out.println("CRITICAL: Chyba DB: " + e.getMessage());
        }
    }

    private static void migrovatSloupce(Statement stmt) {
        String[] columns = {
                "ALTER TABLE users ADD COLUMN banned_until INTEGER DEFAULT -1",
                "ALTER TABLE users ADD COLUMN banned_by TEXT DEFAULT 'SYSTEM'",
                "ALTER TABLE users ADD COLUMN ban_reason TEXT DEFAULT ''",
                "ALTER TABLE users ADD COLUMN recovery_hash TEXT",
                "ALTER TABLE users ADD COLUMN role INTEGER DEFAULT 0",
                "ALTER TABLE users ADD COLUMN xp INTEGER DEFAULT 0",
                "ALTER TABLE users ADD COLUMN level INTEGER DEFAULT 1",
                "ALTER TABLE users ADD COLUMN avatar_base64 TEXT", // Migrace pro existující DB
                "ALTER TABLE messages ADD COLUMN type TEXT DEFAULT 'TEXT'",
                "ALTER TABLE messages ADD COLUMN file_data TEXT",
                "ALTER TABLE messages ADD COLUMN is_burnable INTEGER DEFAULT 0"
        };
        for (String sql : columns) {
            try { stmt.execute(sql); } catch (SQLException ignored) {}
        }
    }

    // --- 3. XP A LEVELY (GAMIFIKACE) ---
    public static void addXp(String username, int amount) {
        if (username.equals("SYSTEM")) return;

        String sqlSelect = "SELECT xp, level FROM users WHERE username = ?";
        String sqlUpdate = "UPDATE users SET xp = ?, level = ? WHERE username = ?";

        try (Connection conn = connect();
             PreparedStatement pSelect = conn.prepareStatement(sqlSelect);
             PreparedStatement pUpdate = conn.prepareStatement(sqlUpdate)) {

            pSelect.setString(1, username);
            ResultSet rs = pSelect.executeQuery();

            if (rs.next()) {
                int currentXp = rs.getInt("xp") + amount;
                int currentLevel = rs.getInt("level");

                // Jednoduchý vzorec: každých 100 XP znamená postup o úroveň výš
                if (currentXp >= currentLevel * 100) {
                    currentLevel++;
                }

                pUpdate.setInt(1, currentXp);
                pUpdate.setInt(2, currentLevel);
                pUpdate.setString(3, username);
                pUpdate.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static int getUserLevel(String username) {
        if (username.equals("SYSTEM")) return 0;

        String sql = "SELECT level FROM users WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("level");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 1;
    }

    // --- 4. PRÁCE S UŽIVATELI ---

    public static boolean registerUser(String username, String password, String recoveryCode) {
        boolean isAdmin = getUserCount() == 0;
        String passHash = PasswordUtils.hashPassword(password);
        String recHash = PasswordUtils.hashPassword(recoveryCode);

        String sql = "INSERT INTO users(username, password, recovery_hash, role) VALUES(?,?,?,?)";

        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, passHash);
            pstmt.setString(3, recHash);
            pstmt.setInt(4, isAdmin ? 1 : 0);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Registrace selhala: " + e.getMessage());
            return false;
        }
    }

    public static boolean checkCredentials(String username, String rawPassword) {
        String sql = "SELECT password FROM users WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return PasswordUtils.verifyPassword(rawPassword, rs.getString("password"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean loginUser(String username, String password) {
        return checkCredentials(username, password);
    }

    public static boolean resetPassword(String username, String recoveryCode, String newPassword) {
        System.out.println("\n--- START RECOVER DEBUG ---");
        System.out.println("1. Hledám uživatele: [" + username + "]");
        System.out.println("2. Zadaný kód z webu: [" + recoveryCode + "]");
        System.out.println("3. Zadané NOVÉ HESLO z webu: [" + newPassword + "]"); // TADY JE TA PAST!

        String sqlCheck = "SELECT recovery_hash FROM users WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sqlCheck)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedRecHash = rs.getString("recovery_hash");
                System.out.println("4. Uživatel NALEZEN! Uložený kód v DB: [" + storedRecHash + "]");

                if (storedRecHash != null) {
                    boolean isValid = false;

                    // Pokus 1: Správné ověření přes tvoji utilitu (řeší hashe i se solí)
                    try {
                        isValid = PasswordUtils.verifyPassword(recoveryCode, storedRecHash);
                        System.out.println("5. Výsledek PasswordUtils.verifyPassword: " + isValid);
                    } catch (Exception e) {
                        System.out.println("5. verifyPassword hodilo chybu: " + e.getMessage());
                    }

                    // Pokus 2: Zpětná kompatibilita (kdyby náhodou kód v DB nebyl hash, ale prostý text)
                    if (!isValid) {
                        isValid = storedRecHash.equals(recoveryCode);
                        if (isValid) System.out.println("5. Výsledek equals (byl to čistý text): " + isValid);
                    }

                    if (isValid) {
                        System.out.println("6. KÓD SEDÍ! Zahashuji nové heslo a ukládám...");
                        String newPassHash = PasswordUtils.hashPassword(newPassword);

                        try (PreparedStatement update = conn.prepareStatement("UPDATE users SET password = ? WHERE username = ?")) {
                            update.setString(1, newPassHash);
                            update.setString(2, username);
                            update.executeUpdate();
                            System.out.println("7. HESLO ÚSPĚŠNĚ ZMĚNĚNO V DB!");
                            System.out.println("--- KONEC RECOVER DEBUG (Úspěch) ---\n");
                            return true;
                        }
                    } else {
                        System.out.println("6. CHYBA: Zadaný kód se neshoduje s databází!");
                    }
                } else {
                    System.out.println("4. CHYBA: Uživatel nemá v DB žádný záchranný kód.");
                }
            } else {
                System.out.println("4. CHYBA: Uživatel [" + username + "] neexistuje.");
            }
        } catch (SQLException e) {
            System.out.println("CHYBA SQL: " + e.getMessage());
        }
        System.out.println("--- KONEC RECOVER DEBUG (Selhalo) ---\n");
        return false;
    }

    public static boolean isAdmin(String username) {
        String sql = "SELECT role FROM users WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("role") == 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static int getUserCount() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // --- 5. ZPRÁVY A HISTORIE ---

    public static int saveMessage(String sender, String message, String timestamp, String room, boolean isBurnable) {
        String sql = "INSERT INTO messages(sender, message, type, timestamp, room, is_burnable) VALUES(?,?,?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, message);
            pstmt.setString(3, isBurnable ? "BURN" : "TEXT");
            pstmt.setString(4, timestamp);
            pstmt.setString(5, room);
            pstmt.setInt(6, isBurnable ? 1 : 0);

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.out.println("Chyba DB: " + e.getMessage());
        }
        return 0;
    }

    public static int saveMessage(String sender, String message, String timestamp, String room) {
        return saveMessage(sender, message, timestamp, room, false);
    }

    public static int saveFullMessage(String sender, String text, String fileData, String type, String timestamp, String room) {
        String sql = "INSERT INTO messages(sender, message, file_data, type, timestamp, room) VALUES(?,?,?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, text);
            pstmt.setString(3, fileData);
            pstmt.setString(4, type);
            pstmt.setString(5, timestamp);
            pstmt.setString(6, room);

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.out.println("Chyba DB: " + e.getMessage());
        }
        return 0;
    }

    public static List<String> getHistory(String room) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT * FROM (SELECT id, sender, message, file_data, type, is_burnable FROM messages WHERE room=? ORDER BY id DESC LIMIT 50) ORDER BY id ASC";

        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, room);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String sender = rs.getString("sender");
                String type = rs.getString("type");
                boolean isBurnable = rs.getInt("is_burnable") == 1;

                String text = rs.getString("message");
                // Bezpečnost: V historii nikdy neukážeme obsah tajné zprávy, ani když zůstane v DB
                if (isBurnable) {
                    text = "[SKRYTÁ ZPRÁVA]";
                }

                String data = rs.getString("file_data");

                if ("TEXT".equals(type) || "BURN".equals(type)) {
                    list.add((isBurnable ? "BURN:" : "MSG:") + id + ":" + sender + ":" + text);
                } else {
                    String prefix = "IMG".equals(type) ? "IMG:" : "FILE:";
                    list.add(prefix + id + ":" + sender + ":" + text + ":" + data);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean deleteMessage(int id) {
        String sql = "DELETE FROM messages WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public static void deleteMessagesByUser(String username) {
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement("DELETE FROM messages WHERE sender = ?")) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- 6. BANOVÁNÍ ---

    public static boolean isBanned(String username) {
        String sql = "SELECT is_banned, banned_until FROM users WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                boolean banned = rs.getInt("is_banned") == 1;
                long until = rs.getLong("banned_until");

                if (banned && until != -1 && System.currentTimeMillis() > until) {
                    unbanUser(username);
                    return false;
                }
                return banned;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void banUser(String username, String admin, String reason, long seconds) {
        long until = (seconds == -1) ? -1 : System.currentTimeMillis() + (seconds * 1000L);
        String sql = "UPDATE users SET is_banned=1, banned_by=?, ban_reason=?, banned_until=? WHERE username=?";

        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, admin);
            pstmt.setString(2, reason);
            pstmt.setLong(3, until);
            pstmt.setString(4, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean unbanUser(String username) {
        String sql = "UPDATE users SET is_banned=0, banned_until=-1 WHERE username=?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public static String getBanDetails(String username) {
        String sql = "SELECT ban_reason FROM users WHERE username=?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("ban_reason");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Neznámý důvod";
    }

    public static List<String[]> getBannedList() {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT username, banned_by, ban_reason, banned_until FROM users WHERE is_banned = 1";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new String[]{
                        rs.getString("username"),
                        rs.getString("banned_by"),
                        rs.getString("ban_reason"),
                        String.valueOf(rs.getLong("banned_until"))
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // --- 7. AVATARY ---

    public static boolean saveAvatar(String username, String base64Data) {
        String sql = "UPDATE users SET avatar_base64 = ? WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, base64Data);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Chyba při ukládání avatara: " + e.getMessage());
            return false;
        }
    }

    public static String getAvatar(String username) {
        String sql = "SELECT avatar_base64 FROM users WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("avatar_base64");
            }
        } catch (SQLException e) {
            System.out.println("Chyba při načítání avatara: " + e.getMessage());
        }
        return null;
    }
}