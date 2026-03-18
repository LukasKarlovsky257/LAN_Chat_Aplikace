package org.example.helpers;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    private final JList<String> onlineUserList;
    private final DefaultListModel<String> userListModel;

    private String privateChatTarget = null;
    private final JPanel privateModePanel;
    private final JLabel privateModeLabel;

    private JLabel typingLabel;

    private JPanel replyPreviewPanel;
    private JLabel replyTitleLabel;
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

    private Image backgroundImage = null;
    private final String BG_FILE_PATH = "background.jpg";

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

    private static class TranslucentPanel extends JPanel {
        public TranslucentPanel(LayoutManager layout) { super(layout); setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            super.paintComponent(g);
        }
    }

    public ChatPanel(Klient app) {
        ConfigManager.load();
        this.app = app;
        this.setLayout(new BorderLayout());
        this.setOpaque(true);

        JPanel header = new TranslucentPanel(new BorderLayout());
        header.setBackground(new Color(10, 12, 18, 200));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.GLASS_BORDER),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        this.headerInfo = new JLabel("Lobby", SwingConstants.LEFT);
        this.headerInfo.setForeground(ModernTheme.NEON_CYAN);
        this.headerInfo.setFont(new Font("Segoe UI", Font.BOLD, 20));

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightHeader.setOpaque(false);

        JButton roomsButton = ModernTheme.createChatButton("Místnosti", ModernTheme.NEON_CYAN);
        roomsButton.addActionListener((e) -> app.showRoomDialog());

        JButton avatarButton = ModernTheme.createChatButton("Avatar", ModernTheme.NEON_CYAN);
        avatarButton.addActionListener((e) -> uploadAvatar());

        JButton settingsButton = ModernTheme.createChatButton("Nastavení", ModernTheme.TEXT_MAIN);
        settingsButton.addActionListener((e) -> {
            Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
            new SettingsDialog(parent, this).setVisible(true);
        });

        JButton logoutButton = ModernTheme.createChatButton("Odpojit", ModernTheme.DANGER);
        logoutButton.addActionListener((e) -> app.logout());

        rightHeader.add(roomsButton);
        rightHeader.add(avatarButton);
        rightHeader.add(settingsButton);
        rightHeader.add(logoutButton);
        header.add(this.headerInfo, BorderLayout.WEST);
        header.add(rightHeader, BorderLayout.EAST);
        this.add(header, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.82);
        splitPane.setDividerSize(0);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);

        this.messagesBox = new JPanel();
        this.messagesBox.setLayout(new BoxLayout(this.messagesBox, BoxLayout.Y_AXIS));
        this.messagesBox.setOpaque(false);
        this.messagesBox.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(this.messagesBox, BorderLayout.NORTH);
        wrapper.setOpaque(false);

        this.scrollPane = new JScrollPane(wrapper);
        this.scrollPane.setBorder(null);
        this.scrollPane.setOpaque(false);
        this.scrollPane.getViewport().setOpaque(true);
        this.scrollPane.getViewport().setBackground(new Color(15, 18, 25));
        this.scrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        this.scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        this.scrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        splitPane.setLeftComponent(this.scrollPane);

        JPanel userPanel = new TranslucentPanel(new BorderLayout());
        userPanel.setBackground(new Color(15, 18, 25, 180));
        userPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, ModernTheme.GLASS_BORDER));

        JLabel userTitle = new JLabel("Online Entity", SwingConstants.LEFT);
        userTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        userTitle.setForeground(ModernTheme.TEXT_MAIN);
        userTitle.setBorder(new EmptyBorder(15, 15, 10, 15));
        userPanel.add(userTitle, BorderLayout.NORTH);

        this.userListModel = new DefaultListModel<>();
        this.onlineUserList = new JList<>(this.userListModel);
        this.onlineUserList.setBorder(null);
        this.onlineUserList.setOpaque(false);
        this.onlineUserList.setCellRenderer(new UserListRenderer(app));

        this.onlineUserList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { checkPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { checkPopup(e); }
            private void checkPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int index = onlineUserList.locationToIndex(e.getPoint());
                    if (index != -1) {
                        onlineUserList.setSelectedIndex(index);
                        String selectedUserRaw = onlineUserList.getSelectedValue();
                        String selectedUser = selectedUserRaw.split("\\|")[0];
                        if (selectedUser.contains(" ")) selectedUser = selectedUser.substring(0, selectedUser.indexOf(" "));

                        if (selectedUser != null && !selectedUser.equals(app.getMyNick())) {
                            JPopupMenu menu = new JPopupMenu();
                            String finalSelectedUser = selectedUser;
                            JMenuItem whisper = new JMenuItem("Šifrovaný kanál (Šeptat)");
                            whisper.addActionListener(ev -> activatePrivateMode(finalSelectedUser));
                            menu.add(whisper);
                            JMenuItem roomInvite = new JMenuItem("Pozvat do soukromé místnosti");
                            roomInvite.addActionListener(ev -> app.getNetwork().sendEncryptedMessage("/roominvite " + finalSelectedUser));
                            menu.add(roomInvite);
                            if (app.isAdmin()) {
                                menu.addSeparator();
                                JMenu adminMenu = new JMenu("Admin Akce");
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
        userScroll.setOpaque(false);
        userScroll.getViewport().setOpaque(true);
        userScroll.getViewport().setBackground(new Color(15, 18, 25));
        userScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        userScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        userPanel.add(userScroll, BorderLayout.CENTER);
        splitPane.setRightComponent(userPanel);
        this.add(splitPane, BorderLayout.CENTER);

        JPanel southContainer = new TranslucentPanel(new BorderLayout());
        southContainer.setBackground(new Color(10, 12, 18, 200));
        southContainer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ModernTheme.GLASS_BORDER));

        JPanel notificationsPanel = new JPanel(new BorderLayout());
        notificationsPanel.setOpaque(false);

        this.privateModePanel = new TranslucentPanel(new BorderLayout());
        this.privateModePanel.setBackground(new Color(188, 19, 254, 40));
        this.privateModePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.NEON_PURPLE),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)));

        this.privateModeLabel = new JLabel("🔒 Zabezpečený kanál: ...");
        this.privateModeLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        this.privateModeLabel.setForeground(ModernTheme.NEON_PURPLE);

        JButton cancelPrivateButton = ModernTheme.createChatButton("Zrušit [X]", ModernTheme.NEON_PURPLE);
        cancelPrivateButton.addActionListener((e) -> this.deactivatePrivateMode());

        this.privateModePanel.add(this.privateModeLabel, BorderLayout.CENTER);
        this.privateModePanel.add(cancelPrivateButton, BorderLayout.EAST);
        this.privateModePanel.setVisible(false);

        notificationsPanel.add(this.privateModePanel, BorderLayout.NORTH);

        this.typingLabel = new JLabel(" ");
        this.typingLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        this.typingLabel.setForeground(ModernTheme.NEON_CYAN);
        this.typingLabel.setBorder(new EmptyBorder(5, 20, 5, 0));

        notificationsPanel.add(this.typingLabel, BorderLayout.CENTER);

        this.replyPreviewPanel = new TranslucentPanel(new BorderLayout());
        this.replyPreviewPanel.setBackground(new Color(20, 24, 34, 255));
        this.replyPreviewPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, ModernTheme.NEON_CYAN),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)));

        JPanel replyTextContainer = new JPanel();
        replyTextContainer.setLayout(new BoxLayout(replyTextContainer, BoxLayout.Y_AXIS));
        replyTextContainer.setOpaque(false);

        this.replyTitleLabel = new JLabel("ODPOVĚĎ NA PAKET");
        this.replyTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        this.replyTitleLabel.setForeground(ModernTheme.NEON_CYAN);

        this.replyPreviewLabel = new JLabel("");
        this.replyPreviewLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        this.replyPreviewLabel.setForeground(ModernTheme.TEXT_MAIN);

        replyTextContainer.add(this.replyTitleLabel);
        replyTextContainer.add(Box.createVerticalStrut(3));
        replyTextContainer.add(this.replyPreviewLabel);

        JButton cancelReplyBtn = ModernTheme.createChatButton("✖", ModernTheme.DANGER);
        cancelReplyBtn.setBorderPainted(false);
        cancelReplyBtn.addActionListener(e -> cancelReply());

        this.replyPreviewPanel.add(replyTextContainer, BorderLayout.CENTER);
        this.replyPreviewPanel.add(cancelReplyBtn, BorderLayout.EAST);
        this.replyPreviewPanel.setVisible(false);

        notificationsPanel.add(this.replyPreviewPanel, BorderLayout.SOUTH);
        southContainer.add(notificationsPanel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new BorderLayout(15, 0));
        inputPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        inputPanel.setOpaque(false);

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftButtons.setOpaque(false);

        JButton fileButton = ModernTheme.createChatButton("Soubor", ModernTheme.SUCCESS);
        fileButton.addActionListener((e) -> this.uploadFile());
        JButton gifButton = ModernTheme.createChatButton("GIF", ModernTheme.NEON_CYAN);
        gifButton.addActionListener(e -> new GifPicker(this).setVisible(true));
        JButton gamesButton = ModernTheme.createChatButton("Akce", ModernTheme.NEON_PURPLE);
        gamesButton.addActionListener(e -> {
            String roomName = this.headerInfo.getText().replace(" [ADMIN]", "");
            new GamesDialog((Frame) SwingUtilities.getWindowAncestor(this), app, roomName).setVisible(true);
        });

        leftButtons.add(fileButton); leftButtons.add(gifButton); leftButtons.add(gamesButton);

        this.stopTypingTimer = new javax.swing.Timer(2000, e -> { app.getNetwork().sendRawMessage("TYPING:0"); isTyping = false; });
        this.stopTypingTimer.setRepeats(false);

        this.messageField = new JTextField();
        ModernTheme.styleTextField(this.messageField);
        this.messageField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ModernTheme.GLASS_BORDER, 1),
                new EmptyBorder(12, 15, 12, 15)
        ));

        this.messageField.addActionListener((e) -> this.sendMessage());

        this.messageField.addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) {
                stopTypingTimer.restart();
                if (!isTyping) {
                    int typingType = (replyingToSender != null) ? 2 : 1;
                    app.getNetwork().sendRawMessage("TYPING:" + typingType);
                    isTyping = true;
                }
            }
        });

        setupCommandSuggester();
        setupClipboardPaste();

        this.sendButton = ModernTheme.createChatButton("Odeslat", ModernTheme.NEON_CYAN);
        this.sendButton.addActionListener((e) -> this.sendMessage());

        inputPanel.add(leftButtons, BorderLayout.WEST);
        inputPanel.add(this.messageField, BorderLayout.CENTER);
        inputPanel.add(this.sendButton, BorderLayout.EAST);

        southContainer.add(inputPanel, BorderLayout.CENTER);
        this.add(southContainer, BorderLayout.SOUTH);

        loadBackgroundImage();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
            g.setColor(new Color(10, 12, 18, 160));
            g.fillRect(0, 0, getWidth(), getHeight());
        } else {
            g.setColor(ModernTheme.BG_BASE);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private void loadBackgroundImage() {
        File bgFile = new File(BG_FILE_PATH);
        if (bgFile.exists()) {
            try { backgroundImage = ImageIO.read(bgFile); repaint(); } catch (IOException ignored) {}
        }
    }

    public void setCustomBackground() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.copy(chooser.getSelectedFile().toPath(), new File(BG_FILE_PATH).toPath(), StandardCopyOption.REPLACE_EXISTING);
                loadBackgroundImage();
            } catch (IOException ex) {
                ModernDialog.showMessage(SwingUtilities.getWindowAncestor(this), "Chyba", "Chyba při ukládání pozadí:<br>" + ex.getMessage(), true);
            }
        }
    }

    public void removeCustomBackground() {
        new File(BG_FILE_PATH).delete();
        backgroundImage = null; repaint();
    }

    private void setupClipboardPaste() {
        this.messageField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "pasteImage");
        this.messageField.getActionMap().put("pasteImage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    java.awt.datatransfer.Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                    if (cb.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                        Image img = (Image) cb.getData(DataFlavor.imageFlavor);
                        BufferedImage bImage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = bImage.createGraphics();
                        g.drawImage(img, 0, 0, null); g.dispose();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(bImage, "png", baos);
                        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                        app.getNetwork().sendRawMessage("IMG:" + app.getMyNick() + ":screenshot.png:" + base64);
                        return;
                    }
                } catch (Exception ex) {}
                messageField.paste();
            }
        });
    }

    public void addMessage(int id, String sender, String rawText, String type, byte[] data) {
        SwingUtilities.invokeLater(() -> {
            String text = rawText;
            String replySender = null; String replyText = null;

            if (text != null && text.startsWith("INVITE:RECEIVE:")) {
                String[] parts = text.split(":");
                if (parts.length >= 5) {
                    String invId = parts[2];
                    String invSender = parts[3];
                    String typeName = parts[4].equals("TTT") ? "Piškvorky" : "Sdílené plátno";
                    if (ModernDialog.showConfirm(SwingUtilities.getWindowAncestor(ChatPanel.this), "Nová výzva", "Hráč <b>" + invSender + "</b> tě vyzval na <b>" + typeName + "</b>!<br>Chceš přijmout výzvu?"))
                        app.getNetwork().sendEncryptedMessage("/invite accept " + invId);
                    else app.getNetwork().sendEncryptedMessage("/invite decline " + invId);
                }
                return;
            }

            if (text != null && text.startsWith("INVITE:CANCEL:")) return;

            if (text != null && text.startsWith("GAME:TTT:")) { renderGameUI(text.substring(9)); return; }
            if (text != null && text.startsWith("GAME:WB:START:")) { renderWhiteboardUI(text.substring(14)); return; }
            if (text != null && text.startsWith("GAME:WB:DRAW:")) { handleWhiteboardDraw(text.substring(13)); return; }
            if (text != null && text.startsWith("GAME:WB:CLEAR:")) { handleWhiteboardClear(text.substring(14)); return; }
            if (text != null && text.startsWith("GAME:WB:CLOSE:")) { handleWhiteboardClose(text.substring(14)); return; }

            boolean isMe = sender.equals(this.app.getMyNick());
            boolean isSystem = sender.equals("SYSTEM");
            boolean isPrivate = text != null && (text.startsWith("🕵️") || text.contains("[WHISPER]"));
            boolean isSenderAdmin = sender.toLowerCase().contains("admin") || (isMe && this.app.isAdmin());

            if (!isSystem && text != null) {
                String currentRoom = this.headerInfo.getText().replace(" [ADMIN]", "");

                // NAČTENÍ KLÍČE MÍSTNOSTI
                String roomSecret = app.roomKeys.getOrDefault(currentRoom, currentRoom);

                if (type.equals("TEXT") && text.startsWith("ZK:")) {
                    String decrypted = CryptoAES.decrypt(text.substring(3).trim(), roomSecret);
                    text = (decrypted != null) ? decrypted : "🔒 [Šifrováno]";
                } else if (type.equals("BURN") && !text.equals("[SKRYTÁ ZPRÁVA]")) {
                    int lastColon = text.lastIndexOf(":");
                    if (lastColon != -1) {
                        String potentialZk = text.substring(0, lastColon);
                        String secsStr = text.substring(lastColon);
                        if (potentialZk.startsWith("ZK:")) {
                            String decrypted = CryptoAES.decrypt(potentialZk.substring(3), roomSecret);
                            text = (decrypted != null ? decrypted : "🔒 [Šifrovaná zpráva]") + secsStr;
                        }
                    }
                }
            }

            if (type.equals("TEXT") && text != null && text.startsWith("REPLY|")) {
                String[] parts = text.split("\\|", 4);
                if (parts.length == 4) {
                    replySender = parts[1];
                    replyText = parts[2];
                    text = parts[3];
                } else if (parts.length == 3) {
                    replySender = parts[1];
                    text = parts[2];
                }
            }

            if (!isMe && !isSystem && ConfigManager.playSounds) playNotifySound();

            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(5, 0, 10, 0));

            if (isSystem) {
                JLabel sysLabel = new JLabel(text, SwingConstants.CENTER);
                sysLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                sysLabel.setForeground(ModernTheme.TEXT_MUTED);
                RoundedBubblePanel sysBubble = new RoundedBubblePanel(new BorderLayout(), false, "SYSTEM", app);
                sysBubble.setBubbleColor(new Color(0, 0, 0, 100));
                sysBubble.setBorderColor(ModernTheme.GLASS_BORDER);
                sysBubble.setBorder(new EmptyBorder(4, 12, 4, 12));
                sysBubble.add(sysLabel, BorderLayout.CENTER);
                row.add(Box.createHorizontalGlue()); row.add(sysBubble); row.add(Box.createHorizontalGlue());
                finalizeMessage(row, id);
                return;
            }

            RoundedBubblePanel bubble = new RoundedBubblePanel(new BorderLayout(), isMe, sender, app);
            if (isMe) {
                bubble.setBorder(new EmptyBorder(10, 14, 10, 55));
            } else {
                bubble.setBorder(new EmptyBorder(10, 55, 10, 14));
            }

            JPanel topContainer = new JPanel();
            topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
            topContainer.setOpaque(false);
            topContainer.setBorder(new EmptyBorder(0, 0, 4, 0));

            if (!isMe) {
                JLabel nameLbl = new JLabel(isSenderAdmin ? "🛡️ " + sender : sender);
                nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
                if (isPrivate) nameLbl.setForeground(ModernTheme.NEON_PURPLE);
                else if (isSenderAdmin) nameLbl.setForeground(ModernTheme.DANGER);
                else nameLbl.setForeground(ModernTheme.NEON_CYAN);
                topContainer.add(nameLbl);
            }

            if (replySender != null) {
                JPanel quotePanel = new JPanel(new BorderLayout());
                quotePanel.setBackground(new Color(0, 0, 0, 80));
                quotePanel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, ModernTheme.TEXT_MUTED),
                        new EmptyBorder(4, 8, 4, 8)
                ));
                JLabel quoteLabel = new JLabel("<html><b>" + replySender + "</b><br><i>" + replyText + "</i></html>");
                quoteLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                quoteLabel.setForeground(ModernTheme.TEXT_MUTED);
                quotePanel.add(quoteLabel, BorderLayout.CENTER);
                topContainer.add(Box.createVerticalStrut(3));
                topContainer.add(quotePanel);
            }

            if (topContainer.getComponentCount() > 0) bubble.add(topContainer, BorderLayout.NORTH);

            String htmlTextColor = "#ffffff";
            if (isPrivate) {
                bubble.setBubbleColor(new Color(188, 19, 254, 40));
                bubble.setBorderColor(ModernTheme.NEON_PURPLE);
            } else if (isMe) {
                bubble.setBubbleColor(new Color(0, 243, 255, 40));
                bubble.setBorderColor(ModernTheme.NEON_CYAN);
            } else {
                bubble.setBubbleColor(ModernTheme.GLASS_BG);
                bubble.setBorderColor(ModernTheme.GLASS_BORDER);
            }

            JComponent contentComp = null;

            if (type.equals("BURN")) {
                if (text.equals("[SKRYTÁ ZPRÁVA]")) {
                    JLabel l = new JLabel("🔥 Zpráva byla zničena.");
                    l.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                    l.setForeground(Color.RED);
                    bubble.add(l, BorderLayout.CENTER);
                } else {
                    String secretText = text; int secs = 10;
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
                            if (ticks[0] > 0) hiddenLbl.setText(finalText + " (Smaže se za " + ticks[0] + "s)");
                            else { ((javax.swing.Timer)ev.getSource()).stop(); removeMessage(id); }
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
                String htmlText = "<html><body style='font-family:Segoe UI; font-size:14px; color:" + htmlTextColor + "'>" + this.linkify(text, "#00f3ff") + "</body></html>";
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
                        @Override public void mouseClicked(MouseEvent e) { playVideoWithJavaFX(url, "Video: " + sender); }
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
                if (icon.getIconWidth() > 350) icon = new ImageIcon(icon.getImage().getScaledInstance(350, (350 * icon.getIconHeight()) / icon.getIconWidth(), Image.SCALE_SMOOTH));
                JLabel l = new JLabel(icon);
                bubble.add(l, BorderLayout.CENTER);
                contentComp = l;
            } else if (type.equals("FILE")) {
                JButton saveBtn = ModernTheme.createChatButton("💾 Stáhnout: " + text, ModernTheme.SUCCESS);
                saveBtn.setHorizontalAlignment(SwingConstants.LEFT);
                File appDir = new File(System.getProperty("user.dir"));
                File downloadDir = new File(appDir, "StazeneSoubory");
                if (!downloadDir.exists()) downloadDir.mkdirs();
                File f = new File(downloadDir, text);
                try {
                    if (!f.exists() && data != null) Files.write(f.toPath(), data, StandardOpenOption.CREATE);
                } catch (Exception e) { e.printStackTrace(); }
                saveBtn.addActionListener((ex) -> {
                    try { Desktop.getDesktop().open(f); }
                    catch (Exception e) { ModernDialog.showMessage(SwingUtilities.getWindowAncestor(ChatPanel.this), "Chyba", "Nelze otevřít soubor.", true); }
                });
                bubble.add(saveBtn, BorderLayout.CENTER);
                contentComp = saveBtn;
            }

            this.createContextMenu(bubble, contentComp, id, sender, type, text, isMe, isSystem);

            if (isMe) { row.add(Box.createHorizontalGlue()); row.add(bubble); }
            else { row.add(bubble); row.add(Box.createHorizontalGlue()); }

            finalizeMessage(row, id);
        });
    }

    private void finalizeMessage(JPanel row, int id) {
        this.messagesBox.add(row);
        this.messagesBox.add(Box.createVerticalStrut(5));
        this.messagesBox.revalidate(); this.messagesBox.repaint();
        SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(this.scrollPane.getVerticalScrollBar().getMaximum()));
        if (id > 0) this.messageMap.put(id, row);
    }

    public void setMuted(int seconds) {
        if (muteTimer != null && muteTimer.isRunning()) muteTimer.stop();
        this.messageField.setEnabled(false);
        this.sendButton.setEnabled(false);
        this.messageField.setBackground(new Color(255, 42, 85, 40));
        this.messageField.setDisabledTextColor(ModernTheme.DANGER);

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
        this.messageField.setBackground(new Color(20, 24, 34)); // ModernTheme.INPUT_BG
        this.messageField.setText("");
        this.messageField.requestFocus();
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

    private String linkify(String text, String color) { return text.replaceAll("(http[s]?://[^\\s]+)", "<a href='$1' style='color:" + color + "; text-decoration:none; font-weight:bold;'>$1</a>").replace("\n", "<br>"); }

    private void sendMessage() {
        String txt = this.messageField.getText();
        if (this.replyingToSender != null) {
            String safeText = this.replyingToText.replace("|", " ");
            txt = "REPLY|" + this.replyingToSender + "|" + safeText + "|" + txt;
            cancelReply();
        }
        if (!txt.trim().isEmpty()) {
            String currentRoom = this.headerInfo.getText().replace(" [ADMIN]", "");

            String roomSecret = app.roomKeys.getOrDefault(currentRoom, currentRoom);

            if (txt.startsWith("/")) {
                if (txt.equalsIgnoreCase("/quit")) {
                    this.app.logout();
                }
                else if (txt.toLowerCase().startsWith("/createprivate ")) {
                    String[] parts = txt.split(" ", 2);
                    if (parts.length == 2) {
                        String newRoomName = parts[1].trim();
                        byte[] randomBytes = new byte[16];
                        new java.security.SecureRandom().nextBytes(randomBytes);
                        StringBuilder sb = new StringBuilder();
                        for (byte b : randomBytes) { sb.append(String.format("%02x", b)); }
                        this.app.roomKeys.put(newRoomName, sb.toString());
                    }
                    this.app.getNetwork().sendEncryptedMessage(txt);
                }
                else if (txt.toLowerCase().startsWith("/burn ")) {
                    String[] parts = txt.split(" ", 3);
                    if (parts.length == 3) {
                        String encryptedMsg = CryptoAES.encrypt(parts[2], roomSecret);
                        this.app.getNetwork().sendEncryptedMessage(parts[0] + " " + parts[1] + " ZK:" + encryptedMsg);
                    } else { this.app.getNetwork().sendEncryptedMessage(txt); }
                } else { this.app.getNetwork().sendEncryptedMessage(txt); }
            } else if (this.privateChatTarget != null) {
                this.app.getNetwork().sendEncryptedMessage("/w " + this.privateChatTarget + " " + txt);
            } else {
                String encryptedMsg = CryptoAES.encrypt(txt, roomSecret);
                this.app.getNetwork().sendRawMessage("ZK:" + encryptedMsg);
            }
            this.messageField.setText("");
            commandPopup.setVisible(false);

            this.stopTypingTimer.stop();
            this.app.getNetwork().sendRawMessage("TYPING:0");
            this.isTyping = false;
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
                ModernDialog.showMessage(parentWindow, "Chyba", "Nelze přečíst soubor: " + ex.getMessage(), true);
            }
        }
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
                final String errorMsg = e.getMessage();
                Window parentWindow = SwingUtilities.getWindowAncestor(ChatPanel.this);
                SwingUtilities.invokeLater(() -> ModernDialog.showMessage(parentWindow, "Chyba GIF", errorMsg, true));
            }
        }).start();
    }

    private void activatePrivateMode(String target) {
        this.privateChatTarget = target;
        this.privateModeLabel.setText("🔒 Šifrovaný kanál: " + target);
        this.privateModePanel.setVisible(true);
        this.revalidate();
        this.repaint();
        this.messageField.requestFocus();
    }

    private void cancelReply() {
        this.replyingToSender = null;
        this.replyingToText = null;
        this.replyPreviewPanel.setVisible(false);
        this.revalidate();
        this.repaint();
    }

    private void deactivatePrivateMode() {
        this.privateChatTarget = null;
        this.privateModePanel.setVisible(false);
        this.revalidate();
        this.repaint();
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
                    if (text.startsWith("/") && text.length() > 0 && !text.contains(" ")) showSuggestions(text.toLowerCase());
                    else commandPopup.setVisible(false);
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
        if (hasMatch) commandPopup.show(messageField, 0, -commandPopup.getPreferredSize().height);
        else commandPopup.setVisible(false);
    }

    private JMenuItem createSuggestionItem(String cmd, String desc) {
        JMenuItem item = new JMenuItem("<html><b>" + cmd + "</b> - <span style='color:gray'>" + desc + "</span></html>");
        item.addActionListener(e -> {
            String baseCmd = cmd.contains("[") ? cmd.substring(0, cmd.indexOf("[")) : cmd;
            messageField.setText(baseCmd); messageField.requestFocus(); commandPopup.setVisible(false);
        });
        return item;
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
                    if (avatarFileName != null) downloadAndCacheAvatar(nick, avatarFileName);
                    userListModel.addElement(nick + " (" + level + ")");
                }
            }
        });
    }

    public void updateHeader(String roomName, boolean isAdmin) { headerInfo.setText(roomName + (isAdmin ? " [ADMIN]" : "")); deactivatePrivateMode(); }

    public void setTypingStatus(String user, boolean typing) {
        setTypingStatus(user, typing ? 1 : 0);
    }

    public void setTypingStatus(String user, int status) {
        if (user.equals(this.app.getMyNick())) return;
        SwingUtilities.invokeLater(() -> {
            if (status == 1) this.typingLabel.setText("✍️ " + user + " právě píše...");
            else if (status == 2) this.typingLabel.setText("✍️ " + user + " odpovídá na zprávu...");
            else this.typingLabel.setText(" ");
        });
    }

    private void renderGameUI(String data) {
        String[] p = data.split(":");
        if (p.length < 6) return;
        String gameId = p[0]; String p1 = p[1]; String p2 = p[2]; String turn = p[3]; String board = p[4]; String status = p[5];
        JPanel gamePanel = activeGamesMap.get(gameId); boolean isNew = false;

        if (gamePanel == null) {
            gamePanel = new JPanel(new BorderLayout());
            gamePanel.setBackground(new Color(255, 255, 255, 230));
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
        header.setFont(new Font("Segoe UI", Font.BOLD, 14)); header.setForeground(Color.BLACK);
        JLabel sub = new JLabel("", SwingConstants.CENTER); sub.setFont(new Font("Segoe UI", Font.BOLD, 12));

        if (status.equals("PLAYING")) { sub.setText("Na tahu: " + turn); sub.setForeground(new Color(250, 166, 26)); }
        else if (status.equals("WIN1")) { sub.setText("Vítěz: " + p1); sub.setForeground(new Color(46, 204, 113)); }
        else if (status.equals("WIN2")) { sub.setText("Vítěz: " + p2); sub.setForeground(new Color(46, 204, 113)); }
        else { sub.setText("Remíza"); sub.setForeground(Color.GRAY); }

        JPanel top = new JPanel(new GridLayout(2, 1)); top.setOpaque(false); top.add(header); top.add(sub); gamePanel.add(top, BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(3, 3, 5, 5)); grid.setOpaque(false); grid.setBorder(new EmptyBorder(10, 0, 0, 0));

        for (int i = 0; i < 9; i++) {
            int r = i / 3; int c = i % 3; char val = board.charAt(i);
            JButton btn = new JButton(val == '-' ? "" : String.valueOf(val));
            btn.setPreferredSize(new Dimension(60, 60)); btn.setFont(new Font("Segoe UI", Font.BOLD, 28));
            btn.setFocusPainted(false); btn.setBackground(new Color(245, 245, 245));
            btn.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            if (val == 'X') btn.setForeground(new Color(88, 101, 242));
            else if (val == 'O') btn.setForeground(new Color(237, 66, 69));

            final int finalR = r; final int finalC = c;
            if (status.equals("PLAYING") && val == '-') btn.addActionListener(e -> app.getNetwork().sendEncryptedMessage("/ttt tah " + finalR + " " + finalC));
            else { btn.setEnabled(false); btn.setCursor(Cursor.getDefaultCursor()); }
            grid.add(btn);
        }
        gamePanel.add(grid, BorderLayout.CENTER);
        gamePanel.revalidate(); gamePanel.repaint();

        if (isNew) {
            JPanel row = new JPanel(); row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS)); row.setOpaque(false); row.setBorder(new EmptyBorder(10, 0, 10, 0));
            row.add(Box.createHorizontalGlue()); row.add(gamePanel); row.add(Box.createHorizontalGlue());
            this.messagesBox.add(row); this.messagesBox.add(Box.createVerticalStrut(5));
            this.messagesBox.revalidate(); this.messagesBox.repaint();
            SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(this.scrollPane.getVerticalScrollBar().getMaximum()));
        }
    }

    private void renderWhiteboardUI(String data) {
        String[] p = data.split(":");
        String id = p[0]; String p1 = p[1]; String p2 = p[2];
        if (activeGamesMap.containsKey("WB_" + id)) return;

        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(new Color(240, 242, 245, 230));
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                new EmptyBorder(10, 15, 10, 15)
        ));
        container.setMaximumSize(new Dimension(420, 380));

        String titleText = p2.equals("ROOM") ? "🎨 Volné plátno (od: " + p1 + ")" : "🎨 Plátno: " + p1 + " & " + p2;
        JLabel header = new JLabel(titleText, SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 14)); header.setForeground(Color.BLACK);
        header.setBorder(new EmptyBorder(0, 0, 10, 0)); container.add(header, BorderLayout.NORTH);

        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics(); g2d.setColor(Color.WHITE); g2d.fillRect(0, 0, 400, 300); g2d.dispose();
        whiteboardImages.put(id, img);

        final String[] currentColor = {"#000000"};

        JPanel canvas = new JPanel() {
            @Override protected void paintComponent(Graphics g) { super.paintComponent(g); g.drawImage(whiteboardImages.get(id), 0, 0, null); }
        };
        canvas.setPreferredSize(new Dimension(400, 300)); canvas.setBorder(BorderFactory.createLineBorder(Color.GRAY)); canvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));

        final Point[] lastPt = {null}; final Point[] lastNetPt = {null}; final long[] lastSend = {0};

        canvas.addMouseListener(new MouseAdapter() { public void mousePressed(MouseEvent e) { lastPt[0] = e.getPoint(); lastNetPt[0] = e.getPoint(); }});
        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (lastPt[0] == null) return; Point pt = e.getPoint();
                Graphics2D g = img.createGraphics(); g.setColor(Color.decode(currentColor[0]));
                int strokeSize = currentColor[0].equals("#FFFFFF") ? 12 : 3;
                g.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.drawLine(lastPt[0].x, lastPt[0].y, pt.x, pt.y);
                g.dispose(); canvas.repaint();
                long now = System.currentTimeMillis();
                if (now - lastSend[0] > 40) {
                    app.getNetwork().sendRawMessage("GAME:WB:DRAW:" + id + ":" + lastNetPt[0].x + ":" + lastNetPt[0].y + ":" + pt.x + ":" + pt.y + ":" + currentColor[0]);
                    lastSend[0] = now; lastNetPt[0] = pt;
                }
                lastPt[0] = pt;
            }
        });

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5)); tools.setOpaque(false);
        JButton btnBlack = ModernTheme.createChatButton("Černá", Color.BLACK); btnBlack.addActionListener(e -> currentColor[0] = "#000000");
        JButton btnBlue = ModernTheme.createChatButton("Modrá", ModernTheme.NEON_CYAN); btnBlue.addActionListener(e -> currentColor[0] = "#5865F2");
        JButton btnRed = ModernTheme.createChatButton("Červená", ModernTheme.DANGER); btnRed.addActionListener(e -> currentColor[0] = "#ed4245");
        JButton btnEraser = ModernTheme.createChatButton("Guma", Color.WHITE); btnEraser.addActionListener(e -> currentColor[0] = "#FFFFFF");
        JButton btnClear = ModernTheme.createChatButton("Vymazat", ModernTheme.DANGER); btnClear.addActionListener(e -> app.getNetwork().sendRawMessage("GAME:WB:CLEAR:" + id));
        JButton btnClose = ModernTheme.createChatButton("Zavřít", Color.GRAY); btnClose.addActionListener(e -> app.getNetwork().sendRawMessage("GAME:WB:CLOSE:" + id));

        tools.add(btnBlack); tools.add(btnBlue); tools.add(btnRed); tools.add(btnEraser); tools.add(btnClear); tools.add(btnClose);
        container.add(canvas, BorderLayout.CENTER); container.add(tools, BorderLayout.SOUTH); activeGamesMap.put("WB_" + id, container);

        JPanel row = new JPanel(); row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS)); row.setOpaque(false); row.setBorder(new EmptyBorder(10, 0, 10, 0));
        row.add(Box.createHorizontalGlue()); row.add(container); row.add(Box.createHorizontalGlue());
        this.messagesBox.add(row); this.messagesBox.add(Box.createVerticalStrut(5));
        this.messagesBox.revalidate(); this.messagesBox.repaint();
        SwingUtilities.invokeLater(() -> this.scrollPane.getVerticalScrollBar().setValue(this.scrollPane.getVerticalScrollBar().getMaximum()));
    }

    private void handleWhiteboardDraw(String data) {
        String[] p = data.split(":");
        if (p.length < 6) return;
        String id = p[0]; int x1 = Integer.parseInt(p[1]); int y1 = Integer.parseInt(p[2]); int x2 = Integer.parseInt(p[3]); int y2 = Integer.parseInt(p[4]); String color = p[5];
        BufferedImage img = whiteboardImages.get(id);
        if (img != null) {
            Graphics2D g = img.createGraphics(); g.setColor(Color.decode(color)); int strokeSize = color.equals("#FFFFFF") ? 12 : 3;
            g.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.drawLine(x1, y1, x2, y2); g.dispose();
            JPanel pan = activeGamesMap.get("WB_" + id); if (pan != null) pan.repaint();
        }
    }

    private void handleWhiteboardClear(String id) {
        BufferedImage img = whiteboardImages.get(id);
        if (img != null) {
            Graphics2D g = img.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0, 0, 400, 300); g.dispose();
            JPanel pan = activeGamesMap.get("WB_" + id); if (pan != null) pan.repaint();
        }
    }

    private void handleWhiteboardClose(String id) {
        JPanel pan = activeGamesMap.remove("WB_" + id);
        if (pan != null) { Container parent = pan.getParent(); if (parent != null) { this.messagesBox.remove(parent); this.messagesBox.revalidate(); this.messagesBox.repaint(); } }
        whiteboardImages.remove(id);
    }

    private void playNotifySound() {
        new Thread(() -> {
            try {
                File soundFile = new File("notify.wav");
                if (soundFile.exists()) {
                    javax.sound.sampled.AudioInputStream audioIn = javax.sound.sampled.AudioSystem.getAudioInputStream(soundFile);
                    javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
                    clip.open(audioIn); clip.start(); Thread.sleep(clip.getMicrosecondLength() / 1000); clip.close(); audioIn.close();
                } else Toolkit.getDefaultToolkit().beep();
            } catch (Exception e) { Toolkit.getDefaultToolkit().beep(); }
        }).start();
    }

    private void uploadAvatar() {
        javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "jpg", "png", "jpeg"));
        int result = fileChooser.showOpenDialog(this);
        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            java.io.File file = fileChooser.getSelectedFile();
            if (file.length() > 2 * 1024 * 1024) {
                ModernDialog.showMessage(SwingUtilities.getWindowAncestor(this), "Chyba", "Obrázek je příliš velký (Max 2MB).", true);
                return;
            }
            try {
                byte[] fileContent = java.nio.file.Files.readAllBytes(file.toPath());
                String base64 = java.util.Base64.getEncoder().encodeToString(fileContent);
                app.getNetwork().sendRawMessage("SET_AVATAR:" + base64);
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    private void downloadAndCacheAvatar(String nick, String fileName) {
        new Thread(() -> {
            try {
                String serverIp = "localhost";
                java.net.URL url = new java.net.URL("http://" + serverIp + ":8080/avatars/" + fileName);
                java.awt.Image img = javax.imageio.ImageIO.read(url);
                if (img != null) { userAvatars.put(nick, img); SwingUtilities.invokeLater(() -> repaint()); }
            } catch (Exception e) { System.err.println("Nepodařilo se stáhnout avatar pro " + nick); }
        }).start();
    }

    private void createContextMenu(JPanel bubble, JComponent content, int id, String sender, String type, String text, boolean isMe, boolean isSystem) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(new ModernTheme.RoundedBorder(10, ModernTheme.GLASS_BORDER));

        if (type.equals("TEXT")) {
            JMenuItem replyItem = new JMenuItem("↩️ Odpovědět");

            replyItem.addActionListener(e -> {
                this.replyingToSender = sender;
                this.replyingToText = text;

                String previewTxt = text.length() > 60 ? text.substring(0, 60) + "..." : text;

                this.replyTitleLabel.setText("ODPOVĚĎ PRO UŽIVATELE " + sender.toUpperCase());
                this.replyPreviewLabel.setText("\"" + previewTxt + "\"");

                this.replyPreviewPanel.setVisible(true);
                this.revalidate();
                this.repaint();
                this.messageField.requestFocus();
            });
            menu.add(replyItem);

            JMenuItem copy = new JMenuItem("Kopírovat text");
            copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null));
            menu.add(copy);
        }

        if ((this.app.isAdmin() || isMe) && !isSystem && id > 0) {
            if (menu.getComponentCount() > 0) menu.addSeparator();
            JMenuItem del = new JMenuItem("🗑️ Smazat zprávu");
            del.setForeground(Color.RED);
            del.addActionListener(e -> {
                boolean confirm = ModernDialog.showConfirm(SwingUtilities.getWindowAncestor(this), "Potvrzení", "Opravdu smazat tuto zprávu?");
                if (confirm) this.app.getNetwork().sendEncryptedMessage("/delmsg " + id);
            });
            menu.add(del);
        }

        bubble.setComponentPopupMenu(menu);
        if (content != null) content.setComponentPopupMenu(menu);
    }

    private void addAdminItems(JMenu menu, String targetUser) {
        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        JMenuItem kick = new JMenuItem("Kick (Vyhodit)"); kick.addActionListener((e) -> (new AdminActionDialog(parent, this.app, "KICK", targetUser)).setVisible(true));
        JMenuItem mute = new JMenuItem("Mute (Umlčet)"); mute.addActionListener((e) -> (new AdminActionDialog(parent, this.app, "MUTE", targetUser)).setVisible(true));
        JMenuItem ban = new JMenuItem("BAN (Zablokovat)"); ban.setForeground(Color.RED); ban.addActionListener((e) -> (new AdminActionDialog(parent, this.app, "BAN", targetUser)).setVisible(true));
        menu.add(kick); menu.add(mute); menu.add(ban);
    }

    private String extractYouTubeId(String text) {
        Matcher m = Pattern.compile("(?:v=|youtu\\.be\\/)([a-zA-Z0-9_-]{11})").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private void playVideoWithJavaFX(String url, String title) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame(title); f.setSize(850, 600); f.setLocationRelativeTo(this); JFXPanel p = new JFXPanel(); f.add(p);
            String vId = this.extractYouTubeId(url); String embed = "https://www.youtube.com/embed/" + vId + "?autoplay=1";
            Platform.runLater(() -> { WebView wv = new WebView(); wv.getEngine().load(embed); p.setScene(new Scene(wv)); });
            f.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { Platform.runLater(() -> p.setScene(null)); } });
            f.setVisible(true);
        });
    }
    private static class RoundedBubblePanel extends JPanel {
        private Color bubbleColor = Color.WHITE; private Color borderColor = null; private final int radius = 18; private final boolean isMe; private final String sender; private final Klient app;

        public RoundedBubblePanel(LayoutManager layout, boolean isMe, String sender, Klient app) {
            super(layout); this.isMe = isMe; this.sender = sender; this.app = app; setOpaque(false);
        }
        public void setBubbleColor(Color c) { this.bubbleColor = c; } public void setBorderColor(Color c) { this.borderColor = c; }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            boolean isSys = sender.equals("SYSTEM");
            g2.setColor(bubbleColor);
            if (isSys) {
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
                if (borderColor != null) { g2.setColor(borderColor); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius); }
            } else if (isMe) {
                g2.fillRoundRect(0, 0, getWidth() - 45, getHeight() - 1, radius, radius);
                if (borderColor != null) { g2.setColor(borderColor); g2.drawRoundRect(0, 0, getWidth() - 45, getHeight() - 1, radius, radius); }
            } else {
                g2.fillRoundRect(45, 0, getWidth() - 46, getHeight() - 1, radius, radius);
                if (borderColor != null) { g2.setColor(borderColor); g2.drawRoundRect(45, 0, getWidth() - 46, getHeight() - 1, radius, radius); }
            }

            if (!isSys) {
                int size = 32;
                int x = isMe ? getWidth() - size : 0;

                String nick = sender.contains(" ") ? sender.substring(0, sender.indexOf(" ")) : sender;
                java.awt.Image avatarImg = ChatPanel.userAvatars.get(nick);

                BufferedImage circleBuffer = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2buf = circleBuffer.createGraphics();
                g2buf.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (avatarImg != null) {
                    g2buf.fillOval(0, 0, size, size);
                    g2buf.setComposite(AlphaComposite.SrcIn);
                    g2buf.drawImage(avatarImg, 0, 0, size, size, null);
                } else {
                    g2buf.setColor(new Color(40, 45, 55));
                    g2buf.fillOval(0, 0, size, size);
                    g2buf.setColor(ModernTheme.NEON_CYAN);
                    g2buf.setFont(new Font("Segoe UI", Font.BOLD, 16));
                    String initial = nick.length() > 0 ? nick.substring(0, 1).toUpperCase() : "?";
                    FontMetrics fm = g2buf.getFontMetrics();
                    int tx = (size - fm.stringWidth(initial)) / 2;
                    int ty = ((size - fm.getHeight()) / 2) + fm.getAscent();
                    g2buf.drawString(initial, tx, ty);
                }
                g2buf.dispose();
                g2.drawImage(circleBuffer, x, 5, null);
            }
            g2.dispose(); super.paintComponent(g);
        }
    }

    private static class UserListRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel nameLabel;
        private final JLabel avatarLabel;
        private final Klient app;

        public UserListRenderer(Klient app) {
            this.app = app;
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(8, 15, 8, 15));
            setOpaque(false);

            avatarLabel = new JLabel();
            avatarLabel.setPreferredSize(new Dimension(32, 32));

            nameLabel = new JLabel();
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            nameLabel.setForeground(ModernTheme.TEXT_MAIN);

            add(avatarLabel, BorderLayout.WEST);
            add(nameLabel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            String[] parts = value.split("\\|");
            String displayTxt = parts[0];
            String nick = displayTxt.contains(" ") ? displayTxt.substring(0, displayTxt.indexOf(" ")) : displayTxt;

            nameLabel.setText(displayTxt + (nick.equals(app.getMyNick()) ? " (Ty)" : ""));

            if (isSelected) {
                setBackground(new Color(0, 243, 255, 40));
                setOpaque(true);
            } else {
                setOpaque(false);
            }

            java.awt.Image avatarImg = ChatPanel.userAvatars.get(nick);
            BufferedImage circleBuffer = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = circleBuffer.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (avatarImg != null) {
                g2.fillOval(0, 0, 32, 32);
                g2.setComposite(AlphaComposite.SrcIn);
                g2.drawImage(avatarImg, 0, 0, 32, 32, null);
            } else {
                g2.setColor(new Color(40, 45, 55));
                g2.fillOval(0, 0, 32, 32);
                g2.setColor(ModernTheme.NEON_CYAN);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                String initial = nick.length() > 0 ? nick.substring(0, 1).toUpperCase() : "?";
                FontMetrics fm = g2.getFontMetrics();
                int x = (32 - fm.stringWidth(initial)) / 2;
                int y = ((32 - fm.getHeight()) / 2) + fm.getAscent();
                g2.drawString(initial, x, y);
            }
            g2.dispose();
            avatarLabel.setIcon(new ImageIcon(circleBuffer));

            return this;
        }
    }

    private static class ModernScrollBarUI extends BasicScrollBarUI {
        @Override protected JButton createDecreaseButton(int orientation) { JButton j = new JButton(); j.setPreferredSize(new Dimension(0,0)); return j; }
        @Override protected JButton createIncreaseButton(int orientation) { JButton j = new JButton(); j.setPreferredSize(new Dimension(0,0)); return j; }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) { g.setColor(new Color(0,0,0,50)); g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height); }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            Graphics2D g2 = (Graphics2D) g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(255, 255, 255, 80)); g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y, thumbBounds.width - 4, thumbBounds.height, 8, 8); g2.dispose();
        }
    }
}