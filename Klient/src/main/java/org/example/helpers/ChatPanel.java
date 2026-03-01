package org.example.helpers;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebView;

import org.example.AdminActionDialog;
import org.example.Klient;
import org.example.managers.ConfigManager;
import org.example.managers.CryptoAES;
import org.example.helpers.gif.GifPicker;

public class ChatPanel extends JPanel {
    private final Klient app;
    private final JPanel messagesBox;
    private final JScrollPane scrollPane;
    private final JTextField messageField;
    private final JLabel headerInfo;
    private final JButton sendButton;
    private final JButton fileButton;
    private final JButton roomsButton;
    private final JButton settingsButton;
    private final JButton logoutButton;
    private final JButton gifButton;
    private final JButton gamesButton;
    private final JList<String> onlineUserList;
    private final DefaultListModel<String> userListModel;

    private String privateChatTarget = null;
    private final JPanel privateModePanel;
    private final JLabel privateModeLabel;

    private JLabel typingLabel;
    private JPanel replyPreviewPanel;
    private JLabel replyPreviewLabel;
    private String replyingToSender = null;
    private String replyingToText = null;
    private boolean isTyping = false;
    private javax.swing.Timer stopTypingTimer;

    private final Map<Integer, JPanel> messageMap = new HashMap<>();
    private final Map<String, JPanel> activeGamesMap = new HashMap<>();
    private final Map<String, BufferedImage> whiteboardImages = new HashMap<>();

    private static Map<String, java.awt.Image> userAvatars = new ConcurrentHashMap<>();

    private javax.swing.Timer muteTimer;
    private JPopupMenu commandPopup;

    private static final Color BG_MAIN = new Color(240, 242, 245);
    private static final Color BG_HEADER = new Color(255, 255, 255);
    private static final Color BUBBLE_OTHER = Color.WHITE;
    private static final Color SYS_MSG_COLOR = new Color(120, 120, 120);

    private final String[][] COMMANDS = {
            {"/w [nick] [zpráva]", "Soukromá zpráva"},
            {"/join [místnost]", "Připojit do místnosti"},
            {"/temproom [název]", "Vytvořit dočasnou místnost"},
            {"/createprivate [název]", "Vytvořit soukromou místnost"},
            {"/roominvite [nick]", "Pozvat uživatele do soukromé místnosti"},
            {"/burn [s] [text]", "Samozničující tajná zpráva"},
            {"/ttt start [soupeř]", "Vyzvat někoho na Piškvorky"},
            {"/wb start [soupeř]", "Založit sdílené plátno (nebo /wb room)"},
            {"/users", "Zobrazit seznam lidí"}
    };

    private final String[][] ADMIN_COMMANDS = {
            {"/math", "Spustit matematickou hádanku"},
            {"/kick [nick]", "Vyhodit hráče ze serveru"},
            {"/ban [nick] [důvod]", "Zabanovat hráče na serveru"},
            {"/mute [nick] [sekundy]", "Ztlumit hráče"},
            {"/delmsg [id]", "Smazat zprávu s daným ID"},
            {"/deleteroom [název]", "Odstranit místnost"},
            {"/broadcast [zpráva]", "Oznámení všem místnostem"}
    };

    public ChatPanel(Klient app) {
        ConfigManager.load();

        this.app = app;
        this.setLayout(new BorderLayout());
        this.setBackground(BG_MAIN);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        this.headerInfo = new JLabel("Lobby", SwingConstants.LEFT);
        this.headerInfo.setForeground(Color.BLACK);
        this.headerInfo.setFont(new Font("Segoe UI", Font.BOLD, 20));

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightHeader.setOpaque(false);

        this.roomsButton = createFlatButton("Místnosti", new Color(245, 245, 245), Color.BLACK);
        this.roomsButton.addActionListener((e) -> app.showRoomDialog());

        // 👈 PŘIDEJ TOTO TLAČÍTKO:
        JButton avatarButton = createFlatButton("Avatar", new Color(245, 245, 245), Color.BLACK);
        avatarButton.addActionListener((e) -> uploadAvatar());

        this.settingsButton = createFlatButton("Nastavení", new Color(245, 245, 245), Color.DARK_GRAY);
        this.settingsButton.addActionListener((e) -> {
            Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
            new SettingsDialog(parent, this).setVisible(true);
        });

        this.logoutButton = createFlatButton("Odhlásit", new Color(255, 235, 238), Color.RED);
        this.logoutButton.addActionListener((e) -> app.logout());

        rightHeader.add(this.roomsButton);
        rightHeader.add(avatarButton);
        rightHeader.add(this.settingsButton);
        rightHeader.add(this.logoutButton);
        header.add(this.headerInfo, BorderLayout.WEST);
        header.add(rightHeader, BorderLayout.EAST);
        this.add(header, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.82);
        splitPane.setDividerSize(0);
        splitPane.setBorder(null);
        splitPane.setBackground(BG_MAIN);

        this.messagesBox = new JPanel();
        this.messagesBox.setLayout(new BoxLayout(this.messagesBox, BoxLayout.Y_AXIS));
        this.messagesBox.setBackground(BG_MAIN);
        this.messagesBox.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(this.messagesBox, BorderLayout.NORTH);
        wrapper.setBackground(BG_MAIN);

        this.scrollPane = new JScrollPane(wrapper);
        this.scrollPane.setBorder(null);
        this.scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        this.scrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        splitPane.setLeftComponent(this.scrollPane);

        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setBackground(Color.WHITE);
        userPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(220, 220, 220)));

        JLabel userTitle = new JLabel("Online", SwingConstants.LEFT);
        userTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        userTitle.setForeground(Color.BLACK);
        userTitle.setBorder(new EmptyBorder(15, 15, 10, 15));
        userPanel.add(userTitle, BorderLayout.NORTH);

        this.userListModel = new DefaultListModel<>();
        this.onlineUserList = new JList<>(this.userListModel);
        this.onlineUserList.setBorder(null);
        this.onlineUserList.setCellRenderer(new UserListRenderer(app));

        this.onlineUserList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { checkPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { checkPopup(e); }

            private void checkPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int index = onlineUserList.locationToIndex(e.getPoint());
                    if (index != -1) {
                        onlineUserList.setSelectedIndex(index);
                        String selectedUserRaw = onlineUserList.getSelectedValue();
                        String selectedUser = selectedUserRaw.split("\\|")[0];

                        if (selectedUser != null && !selectedUser.equals(app.getMyNick())) {
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem whisper = new JMenuItem("Šeptat");
                            whisper.addActionListener(ev -> activatePrivateMode(selectedUser));
                            menu.add(whisper);

                            JMenuItem roomInvite = new JMenuItem("Pozvat do soukromé místnosti");
                            roomInvite.addActionListener(ev -> app.getNetwork().sendEncryptedMessage("/roominvite " + selectedUser));
                            menu.add(roomInvite);

                            JMenuItem playTtt = new JMenuItem("Vyzvat na Piškvorky");
                            playTtt.addActionListener(ev -> app.getNetwork().sendEncryptedMessage("/ttt start " + selectedUser));
                            menu.add(playTtt);

                            JMenuItem playWb = new JMenuItem("Sdílené Plátno (Kreslení)");
                            playWb.addActionListener(ev -> app.getNetwork().sendEncryptedMessage("/wb start " + selectedUser));
                            menu.add(playWb);

                            if (app.isAdmin()) {
                                menu.addSeparator();
                                JMenu adminMenu = new JMenu("Admin");
                                addAdminItems(adminMenu, selectedUser);
                                menu.add(adminMenu);
                            }
                            menu.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                }
            }
        });

        JScrollPane userScroll = new JScrollPane(this.onlineUserList);
        userScroll.setBorder(null);
        userScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        userPanel.add(userScroll, BorderLayout.CENTER);
        splitPane.setRightComponent(userPanel);
        this.add(splitPane, BorderLayout.CENTER);

        JPanel southContainer = new JPanel(new BorderLayout());
        southContainer.setBackground(BG_HEADER);
        southContainer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));

        this.privateModePanel = new JPanel(new BorderLayout());
        this.privateModePanel.setBackground(new Color(255, 248, 225));
        this.privateModePanel.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));

        this.privateModeLabel = new JLabel("🔒 Soukromý chat");
        this.privateModeLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        this.privateModeLabel.setForeground(new Color(200, 140, 0));

        JButton cancelPrivateButton = createFlatButton("Zrušit", Color.WHITE, Color.GRAY);
        cancelPrivateButton.addActionListener((e) -> this.deactivatePrivateMode());

        this.privateModePanel.add(this.privateModeLabel, BorderLayout.CENTER);
        this.privateModePanel.add(cancelPrivateButton, BorderLayout.EAST);
        this.privateModePanel.setVisible(false);
        southContainer.add(this.privateModePanel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new BorderLayout(15, 0));
        inputPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        inputPanel.setBackground(BG_HEADER);

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftButtons.setOpaque(false);

        this.fileButton = createFlatButton("Soubor", new Color(245, 245, 245), Color.BLACK);
        this.fileButton.addActionListener((e) -> this.uploadFile());

        this.gifButton = createFlatButton("GIF", new Color(245, 245, 245), Color.BLACK);
        this.gifButton.addActionListener(e -> new GifPicker(this).setVisible(true));

        this.gamesButton = createFlatButton("Akce...", new Color(245, 245, 245), Color.BLACK);
        this.gamesButton.addActionListener(e -> {
            String roomName = this.headerInfo.getText().replace(" [ADMIN]", "");
            Window window = SwingUtilities.getWindowAncestor(this);
            Frame parent = window instanceof Frame ? (Frame) window : null;
            new GamesDialog(parent, app, roomName).setVisible(true);
        });

        leftButtons.add(this.fileButton);
        leftButtons.add(this.gifButton);
        leftButtons.add(this.gamesButton);

        // --- UI PRO INDIKÁTOR PSANÍ ---
        this.typingLabel = new JLabel(" ");
        this.typingLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        this.typingLabel.setForeground(Color.GRAY);
        this.typingLabel.setBorder(new EmptyBorder(0, 20, 5, 0));
        southContainer.add(this.typingLabel, BorderLayout.NORTH); // Nad input panel

        // --- UI PRO NÁHLED ODPOVĚDI (REPLY) ---
        this.replyPreviewPanel = new JPanel(new BorderLayout());
        this.replyPreviewPanel.setBackground(new Color(235, 245, 255));
        this.replyPreviewPanel.setBorder(new EmptyBorder(5, 15, 5, 15));

        this.replyPreviewLabel = new JLabel("");
        this.replyPreviewLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        this.replyPreviewLabel.setForeground(new Color(100, 100, 100));

        JButton cancelReplyBtn = createFlatButton("✖", new Color(235, 245, 255), Color.RED);
        cancelReplyBtn.addActionListener(e -> cancelReply());

        this.replyPreviewPanel.add(this.replyPreviewLabel, BorderLayout.CENTER);
        this.replyPreviewPanel.add(cancelReplyBtn, BorderLayout.EAST);
        this.replyPreviewPanel.setVisible(false);

        // Vložení panelu nad input box
        JPanel inputWrapper = new JPanel(new BorderLayout());
        inputWrapper.add(this.replyPreviewPanel, BorderLayout.NORTH);
        inputWrapper.add(inputPanel, BorderLayout.CENTER); // Tvůj stávající inputPanel
        southContainer.add(inputWrapper, BorderLayout.CENTER);

        // --- LOGIKA INDIKÁTORU PSANÍ (DEBOUNCE) ---
        this.stopTypingTimer = new javax.swing.Timer(2000, e -> {
            app.getNetwork().sendRawMessage("TYPING:0");
            isTyping = false;
        });
        this.stopTypingTimer.setRepeats(false);

        this.messageField = new JTextField();
        this.messageField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        this.messageField.setForeground(Color.BLACK);
        this.messageField.setBackground(Color.WHITE);
        this.messageField.setCaretColor(Color.BLACK);
        this.messageField.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(20, new Color(220, 220, 220)),
                new EmptyBorder(10, 15, 10, 15)
        ));
        this.messageField.addActionListener((e) -> this.sendMessage());

        this.messageField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                stopTypingTimer.restart();
                if (!isTyping) {
                    app.getNetwork().sendRawMessage("TYPING:1");
                    isTyping = true;
                }
            }
        });

        setupCommandSuggester();

        this.sendButton = createFlatButton("Odeslat", new Color(0, 132, 255), Color.WHITE);
        this.sendButton.addActionListener((e) -> this.sendMessage());

        inputPanel.add(leftButtons, BorderLayout.WEST);
        inputPanel.add(this.messageField, BorderLayout.CENTER);
        inputPanel.add(this.sendButton, BorderLayout.EAST);

        southContainer.add(inputPanel, BorderLayout.CENTER);
        this.add(southContainer, BorderLayout.SOUTH);
    }

    private JButton createFlatButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setUI(new BasicButtonUI());
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBorder(new EmptyBorder(8, 12, 8, 12));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(Color.BLACK);
                    btn.setForeground(Color.WHITE);
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(bg);
                    btn.setForeground(fg);
                }
            }
        });

        return btn;
    }

    private void playNotifySound() {
        new Thread(() -> {
            try {
                File soundFile = new File("notify.wav");
                if (soundFile.exists()) {
                    javax.sound.sampled.AudioInputStream audioIn = javax.sound.sampled.AudioSystem.getAudioInputStream(soundFile);
                    javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
                    clip.open(audioIn);
                    clip.start();
                    Thread.sleep(clip.getMicrosecondLength() / 1000);
                    clip.close();
                    audioIn.close();
                } else {
                    Toolkit.getDefaultToolkit().beep();
                }
            } catch (Exception e) {
                Toolkit.getDefaultToolkit().beep();
            }
        }).start();
    }

    private void setupCommandSuggester() {
        commandPopup = new JPopupMenu();
        commandPopup.setFocusable(false);

        messageField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { checkCommand(); }
            public void removeUpdate(DocumentEvent e) { checkCommand(); }
            public void changedUpdate(DocumentEvent e) { checkCommand(); }

            private void checkCommand() {
                SwingUtilities.invokeLater(() -> {
                    String text = messageField.getText();
                    if (text.startsWith("/") && text.length() > 0 && !text.contains(" ")) {
                        showSuggestions(text.toLowerCase());
                    } else {
                        commandPopup.setVisible(false);
                    }
                });
            }
        });
    }

    private void showSuggestions(String input) {
        commandPopup.removeAll();
        boolean hasMatch = false;

        for (String[] cmd : COMMANDS) {
            if (cmd[0].toLowerCase().startsWith(input)) {
                commandPopup.add(createSuggestionItem(cmd[0], cmd[1]));
                hasMatch = true;
            }
        }

        if (app.isAdmin()) {
            for (String[] cmd : ADMIN_COMMANDS) {
                if (cmd[0].toLowerCase().startsWith(input)) {
                    commandPopup.add(createSuggestionItem(cmd[0], cmd[1]));
                    hasMatch = true;
                }
            }
        }

        if (hasMatch) {
            commandPopup.show(messageField, 0, -commandPopup.getPreferredSize().height);
        } else {
            commandPopup.setVisible(false);
        }
    }

    private JMenuItem createSuggestionItem(String cmd, String desc) {
        JMenuItem item = new JMenuItem("<html><b>" + cmd + "</b> - <span style='color:gray'>" + desc + "</span></html>");
        item.addActionListener(e -> {
            String baseCmd = cmd.contains("[") ? cmd.substring(0, cmd.indexOf("[")) : cmd;
            messageField.setText(baseCmd);
            messageField.requestFocus();
            commandPopup.setVisible(false);
        });
        return item;
    }

    private static class UserListRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel nameLabel;
        private final JLabel levelLabel;
        private final Klient app;
        private boolean isMe;

        public UserListRenderer(Klient app) {
            this.app = app;
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(8, 15, 8, 15));
            setOpaque(true);

            nameLabel = new JLabel();
            nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            nameLabel.setForeground(Color.BLACK);

            levelLabel = new JLabel();
            levelLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
            levelLabel.setForeground(Color.WHITE);
            levelLabel.setOpaque(true);
            levelLabel.setBackground(new Color(250, 166, 26));
            levelLabel.setBorder(BorderFactory.createCompoundBorder(
                    new RoundedBorder(12, new Color(250, 166, 26)),
                    new EmptyBorder(2, 6, 2, 6)
            ));

            add(nameLabel, BorderLayout.CENTER);
            add(levelLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            String[] parts = value.split("\\|");
            String name = parts[0];
            String level = parts.length > 1 ? parts[1] : "";

            this.isMe = name.equals(app.getMyNick());
            nameLabel.setText(name + (isMe ? " (Ty)" : ""));
            nameLabel.setFont(isMe ? new Font("Segoe UI", Font.BOLD, 14) : new Font("Segoe UI", Font.PLAIN, 14));

            if (!level.isEmpty()) {
                levelLabel.setText(level);
                levelLabel.setVisible(true);
            } else {
                levelLabel.setVisible(false);
            }

            if (isSelected) {
                setBackground(new Color(235, 245, 255));
            } else {
                setBackground(Color.WHITE);
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(46, 204, 113));
            int circleSize = 10;
            int yPos = (getHeight() - circleSize) / 2;
            g2.fillOval(5, yPos, circleSize, circleSize);
        }
    }

    private void addAdminItems(JMenu menu, String targetUser) {
        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        JMenuItem kick = new JMenuItem("Kick (Vyhodit)");
        kick.addActionListener((e) -> (new AdminActionDialog(parent, this.app, "KICK", targetUser)).setVisible(true));
        JMenuItem mute = new JMenuItem("Mute (Umlčet)");
        mute.addActionListener((e) -> (new AdminActionDialog(parent, this.app, "MUTE", targetUser)).setVisible(true));
        JMenuItem ban = new JMenuItem("BAN (Zablokovat)");
        ban.setForeground(Color.RED);
        ban.addActionListener((e) -> (new AdminActionDialog(parent, this.app, "BAN", targetUser)).setVisible(true));
        menu.add(kick); menu.add(mute); menu.add(ban);
    }

    private void activatePrivateMode(String target) {
        this.privateChatTarget = target;
        this.privateModeLabel.setText("🔒 Píšeš soukromě uživateli: " + target);
        this.privateModePanel.setVisible(true);
        this.messageField.requestFocus();
    }

    private void deactivatePrivateMode() {
        this.privateChatTarget = null;
        this.privateModePanel.setVisible(false);
    }

    public void updateUserList(String usersStr) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            String[] list = usersStr.split(",");
            for (String u : list) {
                if (!u.trim().isEmpty() && !u.equals("SYSTEM")) {
                    String[] mainParts = u.split("~");
                    String[] infoParts = mainParts[0].split("\\|");
                    String nick = infoParts[0].trim();
                    String level = infoParts.length > 1 ? infoParts[1].trim() : "";
                    String avatarFileName = mainParts.length > 1 ? mainParts[1].trim() : null;

                    // Pokusíme se stáhnout obrázek ze serveru (pokud ho hráč má)
                    if (avatarFileName != null) {
                        downloadAndCacheAvatar(nick, avatarFileName);
                    }

                    userListModel.addElement(nick + " (" + level + ")");
                }
            }
        });
    }

    public void updateHeader(String roomName, boolean isAdmin) {
        this.headerInfo.setText(roomName + (isAdmin ? " [ADMIN]" : ""));
        this.deactivatePrivateMode();
    }

    // 🔥 ZERO-KNOWLEDGE: Šifrování před odesláním
    private void sendMessage() {
        String txt = this.messageField.getText();

        if (this.replyingToSender != null) {
            // Odstraníme z původního textu případné znaky |, aby to nerozbilo strukturu
            String safeText = this.replyingToText.replace("|", " ");
            txt = "REPLY|" + this.replyingToSender + "|" + safeText + "|" + txt;
            cancelReply(); // Vyresetujeme UI po odeslání
        }

        if (!txt.trim().isEmpty()) {

            // Jméno místnosti slouží jako šifrovací klíč
            String currentRoom = this.headerInfo.getText().replace(" [ADMIN]", "");

            if (txt.startsWith("/")) {
                if (txt.equalsIgnoreCase("/quit")) {
                    this.app.logout();
                } else if (txt.toLowerCase().startsWith("/burn ")) {
                    // Tajné zprávy se také šifrují
                    String[] parts = txt.split(" ", 3);
                    if (parts.length == 3) {
                        String encryptedMsg = CryptoAES.encrypt(parts[2], currentRoom);
                        this.app.getNetwork().sendEncryptedMessage(parts[0] + " " + parts[1] + " ZK:" + encryptedMsg);
                    } else {
                        this.app.getNetwork().sendEncryptedMessage(txt);
                    }
                } else {
                    this.app.getNetwork().sendEncryptedMessage(txt);
                }
            } else if (this.privateChatTarget != null) {
                this.app.getNetwork().sendEncryptedMessage("/w " + this.privateChatTarget + " " + txt);
            } else {
                // 🔥 ZK ODESLÁNÍ
                String encryptedMsg = CryptoAES.encrypt(txt, currentRoom);
                this.app.getNetwork().sendRawMessage("ZK:" + encryptedMsg);
            }
            this.messageField.setText("");
            commandPopup.setVisible(false);
        }
    }

    private void uploadFile() {
        JFileChooser chooser = new JFileChooser();
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (f.length() > 15 * 1024 * 1024) {
                ModernDialog.showMessage(parentWindow, "Chyba", "Soubor je příliš velký (Max 15MB)!", true);
                return;
            }
            try {
                byte[] content = Files.readAllBytes(f.toPath());
                String base64 = Base64.getEncoder().encodeToString(content);
                String prefix = f.getName().toLowerCase().matches(".*\\.(jpg|png|gif|jpeg)$") ? "IMG:" : "FILE:";
                this.app.getNetwork().sendRawMessage(prefix + this.app.getMyNick() + ":" + f.getName() + ":" + base64);
            } catch (IOException ex) {
                ex.printStackTrace();
                ModernDialog.showMessage(parentWindow, "Chyba", "Nelze přečíst soubor: " + ex.getMessage(), true);
            }
        }
    }

    // 🔥 OPRAVA: Přejmenování rawText na vyřešení lambda scope problému
    public void addMessage(int id, String sender, String rawText, String type, byte[] data) {
        SwingUtilities.invokeLater(() -> {
            // Lokální proměnná pro manipulaci v lambdě
            String text = rawText;


            String replySender = null;
            String replyText = null;

            // Zkontrolujeme, zda je to odpověď
            if (type.equals("TEXT") && text != null && text.startsWith("REPLY|")) {
                String[] parts = text.split("\\|", 4);
                if (parts.length == 4) {
                    replySender = parts[1];
                    replyText = parts[2];
                    text = parts[3]; // Samotná zpráva, která se vykreslí standardně
                }
            }

            if (text != null && text.startsWith("INVITE:RECEIVE:")) {
                String[] parts = text.split(":");
                if (parts.length >= 5) {
                    String invId = parts[2];
                    String invSender = parts[3];
                    String invType = parts[4];
                    String typeName = invType.equals("TTT") ? "Piškvorky" : "Sdílené plátno (Whiteboard)";

                    Window parentWindow = SwingUtilities.getWindowAncestor(ChatPanel.this);
                    boolean accept = ModernDialog.showConfirm(parentWindow, "Nová výzva", "Hráč <b>" + invSender + "</b> tě vyzval na <b>" + typeName + "</b>!<br>Chceš přijmout výzvu?");

                    if (accept) {
                        app.getNetwork().sendEncryptedMessage("/invite accept " + invId);
                    } else {
                        app.getNetwork().sendEncryptedMessage("/invite decline " + invId);
                    }
                }
                return;
            }

            if (text != null && text.startsWith("INVITE:CANCEL:")) {
                return;
            }

            if (text != null && text.startsWith("GAME:TTT:")) {
                renderGameUI(text.substring(9));
                return;
            }

            if (text != null && text.startsWith("GAME:WB:START:")) {
                renderWhiteboardUI(text.substring(14));
                return;
            }
            if (text != null && text.startsWith("GAME:WB:DRAW:")) {
                handleWhiteboardDraw(text.substring(13));
                return;
            }
            if (text != null && text.startsWith("GAME:WB:CLEAR:")) {
                handleWhiteboardClear(text.substring(14));
                return;
            }
            if (text != null && text.startsWith("GAME:WB:CLOSE:")) {
                handleWhiteboardClose(text.substring(14));
                return;
            }

            boolean isMe = sender.equals(this.app.getMyNick());
            boolean isSystem = sender.equals("SYSTEM");
            boolean isPrivate = text != null && (text.startsWith("🕵️") || text.contains("[WHISPER]"));
            boolean isSenderAdmin = sender.toLowerCase().contains("admin") || (isMe && this.app.isAdmin());

            // 🔥 DEŠIFROVÁNÍ ZERO-KNOWLEDGE ZPRÁV 🔥
            if (!isSystem && text != null) {
                String currentRoom = this.headerInfo.getText().replace(" [ADMIN]", "");

                if (type.equals("TEXT") && text.startsWith("ZK:")) {
                    String decrypted = CryptoAES.decrypt(text.substring(3).trim(), currentRoom.trim());
                    text = (decrypted != null) ? decrypted : "🔒 [Šifrovaná zpráva - nelze dešifrovat]";
                }

                replySender = null;
                replyText = null;

                if (type.equals("TEXT") && text != null && text.startsWith("REPLY|")) {
                    String[] parts = text.split("\\|", 4);
                    if (parts.length == 4) {
                        replySender = parts[1];
                        replyText = parts[2];
                        text = parts[3]; // Samotná nová zpráva
                    }
                }

                if (type.equals("TEXT") && text.startsWith("ZK:")) {
                    String decrypted = CryptoAES.decrypt(text.substring(3).trim(), currentRoom.trim());
                    text = (decrypted != null) ? decrypted : "🔒 [Šifrovaná zpráva - nelze dešifrovat]";
                }
                else if (type.equals("BURN") && !text.equals("[SKRYTÁ ZPRÁVA]")) {
                    int lastColon = text.lastIndexOf(":");
                    if (lastColon != -1) {
                        String potentialZk = text.substring(0, lastColon);
                        String secsStr = text.substring(lastColon);
                        if (potentialZk.startsWith("ZK:")) {
                            String decrypted = CryptoAES.decrypt(potentialZk.substring(3), currentRoom);
                            String decText = (decrypted != null) ? decrypted : "🔒 [Šifrovaná zpráva]";
                            text = decText + secsStr;
                        }
                    }
                }
            }

            if (!isMe && !isSystem && ConfigManager.playSounds) {
                playNotifySound();
            }

            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(5, 0, 10, 0));

            if (isSystem) {
                JLabel sysLabel = new JLabel(text, SwingConstants.CENTER);
                sysLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                sysLabel.setForeground(SYS_MSG_COLOR);
                sysLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
                row.add(Box.createHorizontalGlue());
                row.add(sysLabel);
                row.add(Box.createHorizontalGlue());
                finalizeMessage(row, id);
                return;
            }


            RoundedBubblePanel bubble = new RoundedBubblePanel(new BorderLayout(), isMe, sender);
            bubble.setBorder(new EmptyBorder(10, 14, 10, 14));

            JPanel topContainer = new JPanel();
            topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
            topContainer.setOpaque(false);
            topContainer.setBorder(new EmptyBorder(0, 0, 4, 0));

            if (!isMe) {
                JLabel nameLbl = new JLabel(isSenderAdmin ? "🛡️ " + sender : sender);
                nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
                if (isPrivate) nameLbl.setForeground(new Color(120, 0, 180));
                else if (isSenderAdmin) nameLbl.setForeground(new Color(220, 50, 50));
                else nameLbl.setForeground(Color.BLACK);
                topContainer.add(nameLbl);
            }

            // B) Panel s citací
            if (replySender != null) {
                JPanel quotePanel = new JPanel(new BorderLayout());
                quotePanel.setBackground(new Color(0, 0, 0, 15)); // Jemná šedá
                quotePanel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(0, 132, 255)), // Modrý pruh vlevo
                        new EmptyBorder(4, 8, 4, 8)
                ));
                JLabel quoteLabel = new JLabel("<html><b>" + replySender + "</b><br><i>" + replyText + "</i></html>");
                quoteLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                quoteLabel.setForeground(isMe ? new Color(240,240,240) : new Color(80, 80, 80));
                quotePanel.add(quoteLabel, BorderLayout.CENTER);

                topContainer.add(Box.createVerticalStrut(3)); // Mezera
                topContainer.add(quotePanel);
            }

            // Přidáme celý blok nahoru do bubliny
            if (topContainer.getComponentCount() > 0) {
                bubble.add(topContainer, BorderLayout.NORTH);
            }


            if (replySender != null) {
                JPanel quotePanel = new JPanel(new BorderLayout());
                quotePanel.setBackground(new Color(0, 0, 0, 15)); // Jemně šedý rámeček
                quotePanel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(100, 150, 255)), // Modrý pruh vlevo
                        new EmptyBorder(5, 8, 5, 8)
                ));
                JLabel quoteLabel = new JLabel("<html><b>" + replySender + "</b><br><i>" + replyText + "</i></html>");
                quoteLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                quoteLabel.setForeground(new Color(80, 80, 80));
                quotePanel.add(quoteLabel, BorderLayout.CENTER);
                bubble.add(quotePanel, BorderLayout.NORTH); // Přidáme nad samotný text
            }

            if (isPrivate) {
                bubble.setBubbleColor(new Color(245, 235, 255));
                bubble.setBorderColor(new Color(200, 180, 220));
            } else if (isMe) {
                Color myColor = Color.decode(ConfigManager.myBubbleColor);
                bubble.setBubbleColor(myColor);
                bubble.setBorderColor(myColor);
            } else {
                bubble.setBubbleColor(BUBBLE_OTHER);
                bubble.setBorderColor(new Color(230, 230, 230));
            }

            if (!isMe) {
                JLabel nameLbl = new JLabel(isSenderAdmin ? "🛡️ " + sender : sender);
                nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
                if (isPrivate) nameLbl.setForeground(new Color(120, 0, 180));
                else if (isSenderAdmin) nameLbl.setForeground(new Color(220, 50, 50));
                else nameLbl.setForeground(Color.BLACK);
                nameLbl.setBorder(new EmptyBorder(0, 0, 4, 0));
                bubble.add(nameLbl, BorderLayout.NORTH);
            }

            JComponent contentComp = null;

            if (type.equals("BURN")) {
                if (text.equals("[SKRYTÁ ZPRÁVA]")) {
                    JLabel l = new JLabel("🔥 Zpráva byla zničena.");
                    l.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                    l.setForeground(Color.RED);
                    bubble.add(l, BorderLayout.CENTER);
                } else {
                    String secretText = text;
                    int secs = 10;
                    int lastColon = text.lastIndexOf(":");
                    if (lastColon != -1 && text.length() > lastColon + 1) {
                        try {
                            secs = Integer.parseInt(text.substring(lastColon + 1));
                            secretText = text.substring(0, lastColon);
                        } catch (Exception ignored) {}
                    }

                    final String finalText = secretText;
                    final int finalSecs = secs;

                    JButton revealBtn = new JButton("🔥 Zobrazit tajnou zprávu (" + finalSecs + "s)");
                    revealBtn.setBackground(new Color(237, 66, 69));
                    revealBtn.setForeground(Color.WHITE);
                    revealBtn.setFocusPainted(false);
                    revealBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

                    JLabel hiddenLbl = new JLabel();
                    hiddenLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
                    hiddenLbl.setForeground(isMe ? Color.WHITE : Color.BLACK);
                    hiddenLbl.setVisible(false);

                    revealBtn.addActionListener(e -> {
                        revealBtn.setVisible(false);
                        hiddenLbl.setText(finalText + " (Smaže se za " + finalSecs + "s)");
                        hiddenLbl.setVisible(true);

                        app.getNetwork().sendEncryptedMessage("START_TIMER:" + id);

                        final int[] ticks = {finalSecs};
                        javax.swing.Timer t = new javax.swing.Timer(1000, ev -> {
                            ticks[0]--;
                            if (ticks[0] > 0) {
                                hiddenLbl.setText(finalText + " (Smaže se za " + ticks[0] + "s)");
                            } else {
                                ((javax.swing.Timer)ev.getSource()).stop();
                                removeMessage(id);
                            }
                        });
                        t.start();
                    });

                    JPanel bPanel = new JPanel(new BorderLayout());
                    bPanel.setOpaque(false);
                    bPanel.add(revealBtn, BorderLayout.NORTH);
                    bPanel.add(hiddenLbl, BorderLayout.CENTER);
                    bubble.add(bPanel, BorderLayout.CENTER);
                }

            } else if (type.equals("TEXT")) {
                JTextPane ta = new JTextPane();
                ta.setContentType("text/html");
                ta.setEditable(false);
                ta.setOpaque(false);
                ta.putClientProperty("JEditorPane.honorDisplayProperties", Boolean.TRUE);

                String textColor = (isMe && !isPrivate) ? "white" : "#1e1e1e";
                String linkColor = (isMe && !isPrivate) ? "#e0f0ff" : "#007bff";
                String htmlText = "<html><body style='font-family:Segoe UI; font-size:14px; color:" + textColor + "'>"
                        + this.linkify(text, linkColor) + "</body></html>";

                ta.setText(htmlText);
                ta.addHyperlinkListener((ex) -> {
                    if (ex.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                        try { Desktop.getDesktop().browse(ex.getURL().toURI()); } catch (Exception ignored) {}
                    }
                });
                bubble.add(ta, BorderLayout.CENTER);
                contentComp = ta;

                String ytId = this.extractYouTubeId(text);
                if (ytId != null) {
                    JPanel ytPanel = new JPanel(new BorderLayout());
                    ytPanel.setOpaque(false);
                    ytPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

                    JLabel ytPreview = new JLabel("⟳ Načíst video...", SwingConstants.CENTER);
                    ytPreview.setPreferredSize(new Dimension(200, 112));
                    ytPreview.setOpaque(true);
                    ytPreview.setBackground(new Color(30, 30, 30));
                    ytPreview.setForeground(Color.WHITE);
                    ytPreview.setCursor(new Cursor(Cursor.HAND_CURSOR));

                    String url = text.contains("http") ? text : "https://www.youtube.com/watch?v=" + ytId;
                    ytPreview.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) { playVideoWithJavaFX(url, "Video: " + sender); }
                    });

                    ytPanel.add(ytPreview, BorderLayout.CENTER);
                    bubble.add(ytPanel, BorderLayout.SOUTH);

                    new Thread(() -> {
                        try {
                            URL u = new URL("https://img.youtube.com/vi/" + ytId + "/mqdefault.jpg");
                            BufferedImage img = ImageIO.read(u);
                            if (img != null) {
                                Image s = img.getScaledInstance(200, 112, Image.SCALE_SMOOTH);
                                SwingUtilities.invokeLater(() -> ytPreview.setIcon(new ImageIcon(s)));
                            }
                        } catch (Exception ignored) {}
                    }).start();
                }

            } else if (type.equals("IMAGE")) {
                ImageIcon icon = new ImageIcon(data);
                int maxWidth = 350;

                boolean isGif = text != null && text.toLowerCase().endsWith(".gif");

                if (!isGif && icon.getIconWidth() > maxWidth) {
                    int newHeight = (maxWidth * icon.getIconHeight()) / icon.getIconWidth();
                    icon = new ImageIcon(icon.getImage().getScaledInstance(maxWidth, newHeight, Image.SCALE_SMOOTH));
                }

                JLabel l = new JLabel(icon);
                bubble.add(l, BorderLayout.CENTER);
                contentComp = l;

            } else if (type.equals("FILE")) {
                JButton saveBtn = createFlatButton("💾 Stáhnout: " + text, new Color(240, 240, 240), Color.BLACK);
                saveBtn.setHorizontalAlignment(SwingConstants.LEFT);
                File appDir = new File(System.getProperty("user.dir"));
                File downloadDir = new File(appDir, "StazeneSoubory");
                if (!downloadDir.exists()) downloadDir.mkdirs();
                File f = new File(downloadDir, text);
                try {
                    if (!f.exists() && data != null) Files.write(f.toPath(), data, StandardOpenOption.CREATE);
                } catch (Exception e) { e.printStackTrace(); }
                saveBtn.addActionListener((ex) -> {
                    try {
                        Desktop.getDesktop().open(f);
                    } catch (Exception e) {
                        Window parentWindow = SwingUtilities.getWindowAncestor(ChatPanel.this);
                        ModernDialog.showMessage(parentWindow, "Chyba", "Nelze otevřít soubor.", true);
                    }
                });
                bubble.add(saveBtn, BorderLayout.CENTER);
                contentComp = saveBtn;
            }

            this.createContextMenu(bubble, contentComp, id, sender, type, text, isMe, isSystem);

            if (isMe) {
                row.add(Box.createHorizontalGlue());
                row.add(bubble);
            } else {
                row.add(bubble);
                row.add(Box.createHorizontalGlue());
            }

            finalizeMessage(row, id);
        });
    }

    private void renderWhiteboardUI(String data) {
        String[] p = data.split(":");
        String id = p[0];
        String p1 = p[1];
        String p2 = p[2];

        if (activeGamesMap.containsKey("WB_" + id)) return;

        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(new Color(240, 242, 245));
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                new EmptyBorder(10, 15, 10, 15)
        ));
        container.setMaximumSize(new Dimension(420, 380));

        String titleText = p2.equals("ROOM") ? "🎨 Volné plátno (od: " + p1 + ")" : "🎨 Plátno: " + p1 + " & " + p2;
        JLabel header = new JLabel(titleText, SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.setForeground(Color.BLACK);
        header.setBorder(new EmptyBorder(0, 0, 10, 0));
        container.add(header, BorderLayout.NORTH);

        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 400, 300);
        g2d.dispose();
        whiteboardImages.put(id, img);

        final String[] currentColor = {"#000000"};

        JPanel canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(whiteboardImages.get(id), 0, 0, null);
            }
        };
        canvas.setPreferredSize(new Dimension(400, 300));
        canvas.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        canvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));

        // PŮVODNÍ LOGIKA Z TVÉHO KÓDU PRO HLÍDÁNÍ SOUŘADNIC
        final Point[] lastPt = {null};
        final Point[] lastNetPt = {null};
        final long[] lastSend = {0};

        canvas.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                lastPt[0] = e.getPoint();
                lastNetPt[0] = e.getPoint();
            }
        });

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (lastPt[0] == null) return;
                Point pt = e.getPoint();

                Graphics2D g = img.createGraphics();
                g.setColor(Color.decode(currentColor[0]));
                int strokeSize = currentColor[0].equals("#FFFFFF") ? 12 : 3;
                g.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(lastPt[0].x, lastPt[0].y, pt.x, pt.y);
                g.dispose();
                canvas.repaint();

                long now = System.currentTimeMillis();
                if (now - lastSend[0] > 40) {
                    // Odesílání přesně tvým formátem: id:x1:y1:x2:y2:color
                    app.getNetwork().sendRawMessage("GAME:WB:DRAW:" + id + ":" + lastNetPt[0].x + ":" + lastNetPt[0].y + ":" + pt.x + ":" + pt.y + ":" + currentColor[0]);
                    lastSend[0] = now;
                    lastNetPt[0] = pt;
                }
                lastPt[0] = pt;
            }
        });

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        tools.setOpaque(false);

        JButton btnBlack = createFlatButton("Černá", new Color(245, 245, 245), Color.BLACK);
        btnBlack.addActionListener(e -> currentColor[0] = "#000000");

        JButton btnBlue = createFlatButton("Modrá", new Color(245, 245, 245), Color.BLACK);
        btnBlue.addActionListener(e -> currentColor[0] = "#5865F2");

        JButton btnRed = createFlatButton("Červená", new Color(245, 245, 245), Color.BLACK);
        btnRed.addActionListener(e -> currentColor[0] = "#ed4245");

        JButton btnEraser = createFlatButton("Guma", new Color(245, 245, 245), Color.BLACK);
        btnEraser.addActionListener(e -> currentColor[0] = "#FFFFFF");

        JButton btnClear = createFlatButton("Vymazat", new Color(237, 66, 69), Color.WHITE);
        btnClear.addActionListener(e -> app.getNetwork().sendRawMessage("GAME:WB:CLEAR:" + id));

        JButton btnClose = createFlatButton("❌ Zavřít", new Color(100, 100, 100), Color.WHITE);
        btnClose.addActionListener(e -> app.getNetwork().sendRawMessage("GAME:WB:CLOSE:" + id));

        tools.add(btnBlack);
        tools.add(btnBlue);
        tools.add(btnRed);
        tools.add(btnEraser);
        tools.add(btnClear);
        tools.add(btnClose);

        container.add(canvas, BorderLayout.CENTER);
        container.add(tools, BorderLayout.SOUTH);

        activeGamesMap.put("WB_" + id, container);

        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(10, 0, 10, 0));
        row.add(Box.createHorizontalGlue());
        row.add(container);
        row.add(Box.createHorizontalGlue());

        this.messagesBox.add(row);
        this.messagesBox.add(Box.createVerticalStrut(5));
        this.messagesBox.revalidate();
        this.messagesBox.repaint();
        SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(this.scrollPane.getVerticalScrollBar().getMaximum()));
    }

    private void handleWhiteboardDraw(String data) {
        // Zpracování tvého přesného formátu: x1:y1:x2:y2:color
        String[] p = data.split(":");
        if (p.length < 6) return;
        String id = p[0];
        int x1 = Integer.parseInt(p[1]);
        int y1 = Integer.parseInt(p[2]);
        int x2 = Integer.parseInt(p[3]);
        int y2 = Integer.parseInt(p[4]);
        String color = p[5];

        BufferedImage img = whiteboardImages.get(id);
        if (img != null) {
            Graphics2D g = img.createGraphics();
            g.setColor(Color.decode(color));
            int strokeSize = color.equals("#FFFFFF") ? 12 : 3;
            g.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x1, y1, x2, y2);
            g.dispose();
            JPanel pan = activeGamesMap.get("WB_" + id);
            if (pan != null) pan.repaint();
        }
    }

    private void handleWhiteboardClear(String id) {
        BufferedImage img = whiteboardImages.get(id);
        if (img != null) {
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 400, 300);
            g.dispose();
            JPanel pan = activeGamesMap.get("WB_" + id);
            if (pan != null) pan.repaint();
        }
    }

    private void handleWhiteboardClose(String id) {
        JPanel pan = activeGamesMap.remove("WB_" + id);
        if (pan != null) {
            Container parent = pan.getParent();
            if (parent != null) {
                this.messagesBox.remove(parent);
                this.messagesBox.revalidate();
                this.messagesBox.repaint();
            }
        }
        whiteboardImages.remove(id);
    }

    private void renderGameUI(String data) {
        String[] p = data.split(":");
        if (p.length < 6) return;

        String gameId = p[0];
        String p1 = p[1];
        String p2 = p[2];
        String turn = p[3];
        String board = p[4];
        String status = p[5];

        JPanel gamePanel = activeGamesMap.get(gameId);
        boolean isNew = false;

        if (gamePanel == null) {
            gamePanel = new JPanel(new BorderLayout());
            gamePanel.setBackground(Color.WHITE);
            gamePanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(220,220,220), 1, true),
                    new EmptyBorder(10, 15, 10, 15)
            ));
            gamePanel.setMaximumSize(new Dimension(250, 300));
            activeGamesMap.put(gameId, gamePanel);
            isNew = true;
        }

        gamePanel.removeAll();

        JLabel header = new JLabel("Piškvorky: " + p1 + " (X) vs " + p2 + " (O)", SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.setForeground(Color.BLACK);

        JLabel sub = new JLabel("", SwingConstants.CENTER);
        sub.setFont(new Font("Segoe UI", Font.BOLD, 12));

        if (status.equals("PLAYING")) {
            sub.setText("Na tahu: " + turn);
            sub.setForeground(new Color(250, 166, 26));
        } else if (status.equals("WIN1")) {
            sub.setText("Vítěz: " + p1);
            sub.setForeground(new Color(46, 204, 113));
        } else if (status.equals("WIN2")) {
            sub.setText("Vítěz: " + p2);
            sub.setForeground(new Color(46, 204, 113));
        } else {
            sub.setText("Remíza");
            sub.setForeground(Color.GRAY);
        }

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setOpaque(false);
        top.add(header);
        top.add(sub);
        gamePanel.add(top, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(3, 3, 5, 5));
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(10, 0, 0, 0));

        for (int i = 0; i < 9; i++) {
            int r = i / 3;
            int c = i % 3;
            char val = board.charAt(i);
            JButton btn = new JButton(val == '-' ? "" : String.valueOf(val));
            btn.setPreferredSize(new Dimension(60, 60));
            btn.setFont(new Font("Segoe UI", Font.BOLD, 28));
            btn.setFocusPainted(false);
            btn.setBackground(new Color(245, 245, 245));
            btn.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

            if (val == 'X') btn.setForeground(new Color(88, 101, 242));
            else if (val == 'O') btn.setForeground(new Color(237, 66, 69));

            btn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { if (btn.isEnabled() && val == '-') btn.setBackground(new Color(220,220,220)); }
                @Override
                public void mouseExited(MouseEvent e) { if (btn.isEnabled() && val == '-') btn.setBackground(new Color(245,245,245)); }
            });

            if (status.equals("PLAYING") && val == '-') {
                btn.addActionListener(e -> app.getNetwork().sendEncryptedMessage("/ttt tah " + r + " " + c));
            } else {
                btn.setEnabled(false);
                btn.setCursor(Cursor.getDefaultCursor());
            }
            grid.add(btn);
        }

        gamePanel.add(grid, BorderLayout.CENTER);
        gamePanel.revalidate();
        gamePanel.repaint();

        if (isNew) {
            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(10, 0, 10, 0));
            row.add(Box.createHorizontalGlue());
            row.add(gamePanel);
            row.add(Box.createHorizontalGlue());

            this.messagesBox.add(row);
            this.messagesBox.add(Box.createVerticalStrut(5));
            this.messagesBox.revalidate();
            this.messagesBox.repaint();
            SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(this.scrollPane.getVerticalScrollBar().getMaximum()));
        }
    }

    private void finalizeMessage(JPanel row, int id) {
        this.messagesBox.add(row);
        this.messagesBox.add(Box.createVerticalStrut(5));
        this.messagesBox.revalidate();
        this.messagesBox.repaint();
        SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(this.scrollPane.getVerticalScrollBar().getMaximum()));
        if (id > 0) this.messageMap.put(id, row);
    }

    private String linkify(String text, String color) {
        return text.replaceAll("(http[s]?://[^\\s]+)", "<a href='$1' style='color:" + color + "; text-decoration:none; font-weight:bold;'>$1</a>")
                .replace("\n", "<br>");
    }

    private void createContextMenu(JPanel bubble, JComponent content, int id, String sender, String type, String text, boolean isMe, boolean isSystem) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(new RoundedBorder(10, new Color(200, 200, 200)));

        if (type.equals("TEXT")) {
            JMenuItem replyItem = new JMenuItem("↩️ Odpovědět");
            replyItem.setFont(new Font("Segoe UI", Font.BOLD, 13));
            replyItem.addActionListener(e -> {
                this.replyingToSender = sender;
                this.replyingToText = text;
                // Zkrátíme text pro náhled, pokud je moc dlouhý
                String previewTxt = text.length() > 40 ? text.substring(0, 40) + "..." : text;
                this.replyPreviewLabel.setText("Odpovídáš uživateli " + sender + ": \"" + previewTxt + "\"");
                this.replyPreviewPanel.setVisible(true);
                this.messageField.requestFocus();
            });
            menu.add(replyItem);

            JMenuItem copy = new JMenuItem("Kopírovat text");
            copy.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null));
            menu.add(copy);
        }

        if ((this.app.isAdmin() || isMe) && !isSystem && id > 0) {
            if (menu.getComponentCount() > 0) menu.addSeparator();
            JMenuItem del = new JMenuItem("🗑️ Smazat zprávu");
            del.setFont(new Font("Segoe UI", Font.BOLD, 13));
            del.setForeground(Color.RED);
            del.addActionListener(e -> {
                Window parentWindow = SwingUtilities.getWindowAncestor(this);
                boolean confirm = ModernDialog.showConfirm(parentWindow, "Potvrzení", "Opravdu smazat tuto zprávu?");
                if (confirm) {
                    this.app.getNetwork().sendEncryptedMessage("/delmsg " + id);
                }
            });
            menu.add(del);
        }

        bubble.setComponentPopupMenu(menu);
        if (content != null) {
            content.setComponentPopupMenu(menu);
        }
    }

    public void removeMessage(int id) {
        JPanel row = this.messageMap.get(id);
        if (row != null) {
            int index = this.messagesBox.getComponentZOrder(row);
            this.messagesBox.remove(row);
            if (index < this.messagesBox.getComponentCount()) {
                Component filler = this.messagesBox.getComponent(index);
                if (filler instanceof Box.Filler) this.messagesBox.remove(filler);
            }
            this.messagesBox.revalidate();
            this.messagesBox.repaint();
            this.messageMap.remove(id);
        }
    }

    public void clearChat() {
        this.messagesBox.removeAll();
        this.messagesBox.revalidate();
        this.messagesBox.repaint();
        this.messageMap.clear();
        this.activeGamesMap.clear();
        this.whiteboardImages.clear();
    }

    public void setMuted(int seconds) {
        if (muteTimer != null && muteTimer.isRunning()) muteTimer.stop();

        this.messageField.setEnabled(false);
        this.sendButton.setEnabled(false);
        this.fileButton.setEnabled(false);
        this.gifButton.setEnabled(false);
        this.gamesButton.setEnabled(false);

        this.messageField.setBackground(new Color(255, 230, 230));
        this.messageField.setDisabledTextColor(Color.RED);

        final int[] timeLeft = {seconds};
        this.muteTimer = new javax.swing.Timer(1000, e -> {
            timeLeft[0]--;
            this.messageField.setText("⛔ JSI ZTLUMEN! Zbývá: " + timeLeft[0] + "s");
            if (timeLeft[0] <= 0) {
                ((javax.swing.Timer)e.getSource()).stop();
                unmuteUI();
            }
        });

        this.messageField.setText("⛔ JSI ZTLUMEN! Zbývá: " + seconds + "s");
        this.muteTimer.start();
    }

    private void unmuteUI() {
        this.messageField.setEnabled(true);
        this.sendButton.setEnabled(true);
        this.fileButton.setEnabled(true);
        this.gifButton.setEnabled(true);
        this.gamesButton.setEnabled(true);
        this.messageField.setBackground(new Color(245, 245, 245));
        this.messageField.setText("");
        this.messageField.requestFocus();
    }

    private String extractYouTubeId(String text) {
        Matcher m = Pattern.compile("(?:v=|youtu\\.be\\/)([a-zA-Z0-9_-]{11})").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private void playVideoWithJavaFX(String url, String title) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame(title);
            f.setSize(850, 600);
            f.setLocationRelativeTo(this);
            JFXPanel p = new JFXPanel();
            f.add(p);
            String vId = this.extractYouTubeId(url);
            String embed = "https://www.youtube.com/embed/" + vId + "?autoplay=1";
            Platform.runLater(() -> {
                WebView wv = new WebView();
                wv.getEngine().load(embed);
                p.setScene(new Scene(wv));
            });
            f.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) { Platform.runLater(() -> p.setScene(null)); }
            });
            f.setVisible(true);
        });
    }

    public void sendGifAsImage(String urlString) {
        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                InputStream in = url.openStream();
                byte[] data = in.readAllBytes();
                in.close();
                String base64 = Base64.getEncoder().encodeToString(data);
                this.app.getNetwork().sendRawMessage("IMG:" + this.app.getMyNick() + ":giphy.gif:" + base64);
            } catch (Exception e) {
                Window parentWindow = SwingUtilities.getWindowAncestor(ChatPanel.this);
                SwingUtilities.invokeLater(() -> ModernDialog.showMessage(parentWindow, "Chyba GIF", e.getMessage(), true));
            }
        }).start();
    }

    private void downloadAndCacheAvatar(String nick, String fileName) {
        // Poběží ve vedlejším vlákně, ať neseká grafiku
        new Thread(() -> {
            try {
                // Předpokládáme, že HTTP server běží na stejné IP
                String serverIp = "localhost"; // Zde bys měl dát reálnou IP ze ServerManageru
                java.net.URL url = new java.net.URL("http://" + serverIp + ":8080/avatars/" + fileName);
                java.awt.Image img = javax.imageio.ImageIO.read(url);
                if (img != null) {
                    userAvatars.put(nick, img);
                    // Refresh okna
                    SwingUtilities.invokeLater(() -> repaint());
                }
            } catch (Exception e) {
                System.err.println("Nepodařilo se stáhnout avatar pro " + nick);
            }
        }).start();
    }

    private void uploadAvatar() {
        javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "jpg", "png", "jpeg"));
        int result = fileChooser.showOpenDialog(this);
        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            java.io.File file = fileChooser.getSelectedFile();
            if (file.length() > 2 * 1024 * 1024) {
                javax.swing.JOptionPane.showMessageDialog(this, "Obrázek je příliš velký (Max 2MB).");
                return;
            }
            try {
                byte[] fileContent = java.nio.file.Files.readAllBytes(file.toPath());
                String base64 = java.util.Base64.getEncoder().encodeToString(fileContent);
                // Pošleme příkaz SET_AVATAR rovnou do sítě (musíš přidat výjimku do Client.sendCommand, aby to nešifroval, pokud používáš RSA!)
                app.getNetwork().sendRawMessage("SET_AVATAR:" + base64);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static class RoundedBubblePanel extends JPanel {
        private Color bubbleColor = Color.WHITE;
        private Color borderColor = null;
        private final int radius = 18;
        private final boolean isMe;
        private final String sender; // 👈 PŘIDEJ TENTO ŘÁDEK

        // 👈 UPRAV KONSTRUKTOR (přidán String sender)
        public RoundedBubblePanel(LayoutManager layout, boolean isMe, String sender) {
            super(layout);
            this.isMe = isMe;
            this.sender = sender; // 👈 ULOŽÍME ODESÍLATELE
            setOpaque(false);
        }

        public void setBubbleColor(Color c) { this.bubbleColor = c; }
        public void setBorderColor(Color c) { this.borderColor = c; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            g2.setColor(bubbleColor);
            g2.fillRoundRect(0, 0, width - 1, height - 1, radius, radius);

            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, width - 1, height - 1, radius, radius);
            }

            // 🔥 VYKRESLENÍ AVATARA (použije uložený sender)
            java.awt.Image avatarImg = ChatPanel.userAvatars.get(this.sender);
            if (avatarImg != null) {
                Graphics2D gAvatar = (Graphics2D) g2.create();
                int size = 32; // Velikost v bublině
                int x = isMe ? width - size - 5 : 5; // Pozice vpravo pro mě, vlevo pro ostatní
                gAvatar.setClip(new java.awt.geom.Ellipse2D.Float(x, 5, size, size));
                gAvatar.drawImage(avatarImg, x, 5, size, size, null);
                gAvatar.dispose();
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class RoundedBorder implements javax.swing.border.Border {
        private String sender;
        private final int radius;
        private final Color color;
        RoundedBorder( int radius, Color color) { this.radius = radius; this.color = color;}
        public Insets getBorderInsets(Component c) { return new Insets(this.radius + 1, this.radius + 1, this.radius + 2, this.radius); }
        public boolean isBorderOpaque() { return true; }
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }
    }

    private static class ModernScrollBarUI extends BasicScrollBarUI {
        @Override protected JButton createDecreaseButton(int orientation) { return createZeroButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return createZeroButton(); }
        private JButton createZeroButton() {
            JButton j = new JButton(); j.setPreferredSize(new Dimension(0, 0)); return j;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(new Color(240, 242, 245)); g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(180, 180, 180));
            g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y, thumbBounds.width - 4, thumbBounds.height, 8, 8);
            g2.dispose();
        }
    }

    public void setTypingStatus(String user, boolean typing) {
        if (user.equals(this.app.getMyNick())) return;

        SwingUtilities.invokeLater(() -> {
            if (typing) {
                this.typingLabel.setText("✍️ " + user + " právě píše...");
            } else {
                this.typingLabel.setText(" ");
            }
        });
    }

    private void cancelReply() {
        this.replyingToSender = null;
        this.replyingToText = null;
        this.replyPreviewPanel.setVisible(false);
    }
}