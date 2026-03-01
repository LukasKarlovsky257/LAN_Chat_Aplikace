package zaloha1612;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Klient extends JFrame {

    // --- SÍŤOVÉ PROMĚNNÉ ---
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private PublicKey serverKey;
    private PrivateKey myPrivateKey;
    private PublicKey myPublicKey;
    private volatile boolean running = true;

    // --- STAV ---
    private String myNick = "";
    private boolean iAmAdmin = false;

    // --- GUI PRVKY ---
    private CardLayout cardLayout;
    private JPanel mainPanel, loginPanel, chatPanel;
    private JPanel messagesBox;
    private JScrollPane scrollPane;
    private JTextField messageField, ipField, userField;
    private JPasswordField passField;
    private JButton loginButton, registerButton, resetButton, scanButton, sendButton, fileButton, logoutButton;
    private JLabel statusLabel, chatInfoLabel;
    private DefaultListModel<String> serverListModel;

    // Mapy pro správu zpráv (ID -> Panel)
    private final Map<Integer, JPanel> messageMap = new HashMap<>();
    private final Map<String, List<Integer>> userMessagesMap = new HashMap<>();

    public Klient() {
        super("Java Chat v8.1 (YouTube & Admin Tools)");
        setSize(950, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Složka pro stahování
        new File("received_files").mkdirs();

        try {
            KeyPair kp = CryptoUtils.generateKeyPair();
            myPublicKey = kp.getPublic();
            myPrivateKey = kp.getPrivate();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Chyba krypto: " + e.getMessage());
        }

        initUI();
    }

    private void initUI() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        loginPanel = createLoginPanel();
        chatPanel = createChatPanel();

        mainPanel.add(loginPanel, "LOGIN");
        mainPanel.add(chatPanel, "CHAT");

        add(mainPanel);
    }

    // ====================================================================================
    // 1. LOGIN PANEL
    // ====================================================================================
    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new BorderLayout(20, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        JLabel titleLabel = new JLabel("🔐 Přihlášení", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        panel.add(titleLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 30, 0));
        centerPanel.setOpaque(false);

        // Levá část: Scanner
        JPanel scanPanel = new JPanel(new BorderLayout(5, 5));
        scanPanel.setBorder(BorderFactory.createTitledBorder("LAN Servery"));

        serverListModel = new DefaultListModel<>();
        serverListModel.addElement("localhost");
        JList<String> list = new JList<>(serverListModel);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null)
                ipField.setText(list.getSelectedValue());
        });

        scanButton = new JButton("🔄 Skenovat síť");
        scanButton.addActionListener(e -> startScan());

        scanPanel.add(new JScrollPane(list), BorderLayout.CENTER);
        scanPanel.add(scanButton, BorderLayout.SOUTH);

        // Pravá část: Formulář
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 0, 5, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weightx = 1.0; gbc.gridx = 0;

        ipField = new JTextField("localhost");
        userField = new JTextField();
        passField = new JPasswordField();
        Dimension fs = new Dimension(200, 30);
        ipField.setPreferredSize(fs); userField.setPreferredSize(fs); passField.setPreferredSize(fs);

        gbc.gridy = 0; formPanel.add(new JLabel("IP Serveru (nebo Ngrok):"), gbc);
        gbc.gridy = 1; formPanel.add(ipField, gbc);
        gbc.gridy = 2; formPanel.add(Box.createVerticalStrut(10), gbc);
        gbc.gridy = 3; formPanel.add(new JLabel("Jméno:"), gbc);
        gbc.gridy = 4; formPanel.add(userField, gbc);
        gbc.gridy = 5; formPanel.add(Box.createVerticalStrut(10), gbc);
        gbc.gridy = 6; formPanel.add(new JLabel("Heslo:"), gbc);
        gbc.gridy = 7; formPanel.add(passField, gbc);
        gbc.gridy = 8; gbc.weighty = 1.0; formPanel.add(new JLabel(), gbc);

        centerPanel.add(scanPanel);
        centerPanel.add(formPanel);
        panel.add(centerPanel, BorderLayout.CENTER);

        // Tlačítka
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.setOpaque(false);

        statusLabel = new JLabel("Připraven.");
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        btnRow.setOpaque(false);

        loginButton = new JButton("Přihlásit");
        registerButton = new JButton("Registrovat");
        resetButton = new JButton("Zapomněl jsem heslo");
        resetButton.setForeground(Color.RED);
        resetButton.setBorderPainted(false);
        resetButton.setContentAreaFilled(false);
        resetButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        resetButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        loginButton.addActionListener(e -> {
            String u = userField.getText().trim();
            String p = new String(passField.getPassword());
            myNick = u;
            connectAndAuth("/login", u, p, null);
        });

        registerButton.addActionListener(e -> {
            String u = userField.getText().trim();
            String p = new String(passField.getPassword());
            String c = JOptionPane.showInputDialog(this, "Zvolte BEZPEČNOSTNÍ KÓD:");
            if (c != null && !c.trim().isEmpty()) connectAndAuth("/register", u, p, c);
        });

        resetButton.addActionListener(e -> {
            JPanel popup = new JPanel(new GridLayout(0,1));
            JTextField tu = new JTextField(userField.getText());
            JTextField tc = new JTextField();
            JPasswordField tnp = new JPasswordField();
            popup.add(new JLabel("Jméno:")); popup.add(tu);
            popup.add(new JLabel("Kód:")); popup.add(tc);
            popup.add(new JLabel("Nové heslo:")); popup.add(tnp);

            if(JOptionPane.showConfirmDialog(null, popup, "Reset", JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) {
                userField.setText(tu.getText());
                passField.setText(new String(tnp.getPassword()));
                connectAndAuth("/reset", tu.getText(), new String(tnp.getPassword()), tc.getText());
            }
        });

        btnRow.add(loginButton);
        btnRow.add(registerButton);
        southPanel.add(statusLabel);
        southPanel.add(btnRow);
        southPanel.add(resetButton);

        panel.add(southPanel, BorderLayout.SOUTH);
        return panel;
    }

    // ====================================================================================
    // 2. CHAT PANEL
    // ====================================================================================
    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(45, 45, 48));
        header.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        chatInfoLabel = new JLabel("Chat Room", SwingConstants.LEFT);
        chatInfoLabel.setForeground(Color.WHITE);
        chatInfoLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));

        logoutButton = new JButton("Odhlásit");
        logoutButton.setBackground(new Color(220, 53, 69));
        logoutButton.setForeground(Color.WHITE);
        logoutButton.setFocusPainted(false);
        logoutButton.setBorderPainted(false);
        logoutButton.setOpaque(true);
        logoutButton.addActionListener(e -> disconnect());

        header.add(chatInfoLabel, BorderLayout.WEST);
        header.add(logoutButton, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        messagesBox = new JPanel();
        messagesBox.setLayout(new BoxLayout(messagesBox, BoxLayout.Y_AXIS));
        messagesBox.setBackground(new Color(240, 242, 245));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(messagesBox, BorderLayout.NORTH);
        wrapper.setBackground(new Color(240, 242, 245));

        scrollPane = new JScrollPane(wrapper);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(Color.WHITE);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        fileButton = new JButton("📎");
        fileButton.setBackground(Color.LIGHT_GRAY);
        fileButton.addActionListener(e -> uploadFile());

        messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageField.addActionListener(e -> sendMessage());

        sendButton = new JButton("Odeslat");
        sendButton.setBackground(new Color(0, 120, 215));
        sendButton.setForeground(Color.WHITE);
        sendButton.setBorderPainted(false);
        sendButton.setOpaque(true);
        sendButton.addActionListener(e -> sendMessage());

        inputPanel.add(fileButton, BorderLayout.WEST);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        panel.add(inputPanel, BorderLayout.SOUTH);
        return panel;
    }

    // --- VYKRESLENÍ ZPRÁVY (BUBLINA + YouTube + Admin Actions) ---
    private void addMessageBubble(int id, String sender, String text, String type, byte[] data) {
        boolean isMe = sender.equals(myNick);
        boolean isSystem = sender.equals("SYSTEM");

        JPanel rowPanel = new JPanel();
        rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.X_AXIS));
        rowPanel.setOpaque(false);
        rowPanel.setBorder(new EmptyBorder(5, 10, 5, 10));

        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBorder(new EmptyBorder(8, 12, 8, 12));
        bubble.setOpaque(true);

        Color textColor;
        if (isSystem) {
            bubble.setBackground(new Color(255, 245, 200)); textColor = Color.BLACK;
            rowPanel.add(Box.createHorizontalGlue()); rowPanel.add(bubble); rowPanel.add(Box.createHorizontalGlue());
        } else if (isMe) {
            bubble.setBackground(new Color(0, 132, 255)); textColor = Color.WHITE;
            rowPanel.add(Box.createHorizontalGlue()); rowPanel.add(bubble);
        } else {
            bubble.setBackground(Color.WHITE); textColor = Color.BLACK;
            rowPanel.add(bubble); rowPanel.add(Box.createHorizontalGlue());
        }

        JComponent nameComp = null;
        if (!isMe && !isSystem) {
            JLabel nameLabel = new JLabel(sender);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            nameLabel.setForeground(Color.GRAY);
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(nameLabel);
            nameComp = nameLabel;
        }

        JComponent contentComp = null;
        JComponent extraComp = null; // Pro YouTube náhled

        if (type.equals("TEXT")) {
            JTextArea ta = new JTextArea(text);
            ta.setWrapStyleWord(true); ta.setLineWrap(true);
            ta.setOpaque(false); ta.setEditable(false);
            ta.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            ta.setForeground(textColor);
            ta.setSize(new Dimension(300, Short.MAX_VALUE));
            ta.setMaximumSize(new Dimension(300, 2000));
            ta.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(ta);
            contentComp = ta;

            // --- YOUTUBE DETEKCE ---
            String ytId = extractYouTubeId(text);
            if (ytId != null) {
                JLabel ytLabel = new JLabel("Načítám video...");
                ytLabel.setFont(new Font("Segoe UI", Font.ITALIC, 10));
                ytLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                ytLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));

                // Přidání kliknutí pro otevření videa
                String finalUrl = text.contains("http") ? text : "https://www.youtube.com/watch?v=" + ytId;
                ytLabel.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        try { Desktop.getDesktop().browse(new URI(finalUrl)); } catch (Exception ex) {}
                    }
                });

                bubble.add(Box.createVerticalStrut(5));
                bubble.add(ytLabel);
                extraComp = ytLabel;

                // Asynchronní načtení thumbnailu
                new Thread(() -> {
                    try {
                        URL url = new URL("https://img.youtube.com/vi/" + ytId + "/mqdefault.jpg");
                        BufferedImage img = ImageIO.read(url);
                        if (img != null) {
                            // Resize pokud je moc velký
                            Image scaled = img.getScaledInstance(250, -1, Image.SCALE_SMOOTH);
                            ImageIcon icon = new ImageIcon(scaled);
                            SwingUtilities.invokeLater(() -> {
                                ytLabel.setText("");
                                ytLabel.setIcon(icon);
                                ytLabel.setToolTipText("Klikni pro přehrání");
                                bubble.revalidate(); bubble.repaint();
                            });
                        }
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> ytLabel.setText("[Náhled nedostupný]"));
                    }
                }).start();
            }

        } else if (type.equals("IMAGE")) {
            ImageIcon icon = new ImageIcon(data);
            if (icon.getIconWidth() > 300) {
                Image img = icon.getImage().getScaledInstance(300, -1, Image.SCALE_SMOOTH);
                icon = new ImageIcon(img);
            }
            JLabel l = new JLabel(icon);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(l);
            contentComp = l;
        } else if (type.equals("FILE")) {
            JButton saveBtn = new JButton("💾 " + text);
            saveBtn.setBackground(Color.WHITE);
            saveBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

            File cachedFile = new File("received_files/" + text);
            try { if (!cachedFile.exists() && data != null) Files.write(cachedFile.toPath(), data); } catch (Exception e) {}

            saveBtn.addActionListener(e -> {
                try { Desktop.getDesktop().open(cachedFile); }
                catch (Exception ex) { JOptionPane.showMessageDialog(this, "Nelze otevřít: " + ex.getMessage()); }
            });
            bubble.add(saveBtn);
            contentComp = saveBtn;
        }

        // --- KONTEXTOVÉ MENU (Right Click) ---
        JPopupMenu menu = new JPopupMenu();
        boolean menuNeeded = false;

        if (type.equals("TEXT")) {
            JMenuItem copyItem = new JMenuItem("Kopírovat text");
            copyItem.addActionListener(e -> {
                StringSelection selection = new StringSelection(text);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            });
            menu.add(copyItem);
            menuNeeded = true;
        } else if (type.equals("FILE") || type.equals("IMAGE")) {
            JMenuItem saveAs = new JMenuItem("Uložit jako...");
            saveAs.addActionListener(e -> {
                JFileChooser c = new JFileChooser(); c.setSelectedFile(new File(text));
                if(c.showSaveDialog(this)==JFileChooser.APPROVE_OPTION) {
                    try { Files.write(c.getSelectedFile().toPath(), data); } catch(Exception ex){}
                }
            });
            menu.add(saveAs);
            menuNeeded = true;
        }

        // Admin nebo Vlastník (Delete)
        if ((iAmAdmin || isMe) && !isSystem && id > 0) {
            if (menuNeeded) menu.addSeparator();
            JMenuItem del = new JMenuItem("🗑️ Smazat zprávu");
            del.addActionListener(e -> sendAdminCommand("/delete " + id));
            menu.add(del);
            menuNeeded = true;
        }

        // 🔥 ADMIN ACTIONS (Kick, Ban, Mute)
        // Přidáno pro bublinu, text i jméno uživatele
        if (iAmAdmin && !isMe && !isSystem) {
            menu.addSeparator();
            JLabel headerInfo = new JLabel(" Akce pro: " + sender);
            headerInfo.setFont(new Font("Segoe UI", Font.BOLD, 10));
            headerInfo.setBorder(new EmptyBorder(2,5,2,5));
            menu.add(headerInfo);

            JMenuItem kick = new JMenuItem("👢 Kick " + sender);
            kick.addActionListener(e -> sendAdminCommand("/kick " + sender));

            JMenuItem mute = new JMenuItem("🤐 Mute " + sender + " (60s)");
            mute.addActionListener(e -> sendAdminCommand("/mute " + sender + " 60 Spam"));

            JMenuItem ban = new JMenuItem("🔨 BAN " + sender);
            ban.addActionListener(e -> sendAdminCommand("/ban " + sender + " AdminBan"));

            menu.add(kick);
            menu.add(mute);
            menu.add(ban);
            menuNeeded = true;
        }

        if (menuNeeded) {
            bubble.setComponentPopupMenu(menu);
            if (contentComp != null) contentComp.setComponentPopupMenu(menu);
            if (nameComp != null) nameComp.setComponentPopupMenu(menu); // Menu na jméně
            if (extraComp != null) extraComp.setComponentPopupMenu(menu); // Menu na YouTube náhledu
        }

        messagesBox.add(rowPanel);
        messagesBox.revalidate(); messagesBox.repaint();
        SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum()));

        if (id > 0) {
            messageMap.put(id, rowPanel);
            userMessagesMap.computeIfAbsent(sender, k -> new ArrayList<>()).add(id);
        }
    }

    // Pomocná metoda pro extrakci YouTube ID
    private String extractYouTubeId(String text) {
        String pattern = "(?:https?:\\/\\/)?(?:www\\.)?(?:youtube\\.com\\/(?:[^\\/\\n\\s]+\\/\\S+\\/|(?:v|e(?:mbed)?)\\/|\\S*?[?&]v=)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // --- ZMĚNA OBSAHU ZPRÁVY (Pro Soft Delete) ---
    private void updateMessageContent(int id, String newText) {
        JPanel row = messageMap.get(id);
        if (row != null) {
            for (Component c : row.getComponents()) {
                if (c instanceof JPanel) {
                    JPanel bubble = (JPanel) c;
                    bubble.removeAll();
                    JLabel l = new JLabel(newText);
                    l.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                    l.setForeground(Color.GRAY);
                    bubble.add(l);
                    bubble.revalidate(); bubble.repaint();
                    break;
                }
            }
        }
    }

    private void removeMessageBubble(int id) {
        Component c = messageMap.get(id);
        if (c != null) { messagesBox.remove(c); messagesBox.revalidate(); messagesBox.repaint(); messageMap.remove(id); }
    }

    // --- SÍŤ A PŘIPOJOVÁNÍ ---

    private void connectAndAuth(String cmd, String u, String p, String extra) {
        String rawInput = ipField.getText().trim();
        // 🔥 PODPORA PRO NGROK
        if (rawInput.startsWith("tcp://")) rawInput = rawInput.substring(6);

        String host = rawInput;
        int port = 5555;

        if (rawInput.contains(":")) {
            String[] parts = rawInput.split(":");
            host = parts[0];
            try { port = Integer.parseInt(parts[1]); } catch(Exception e) { JOptionPane.showMessageDialog(this,"Chybný port"); return; }
        }

        statusLabel.setText("Připojuji...");
        loginButton.setEnabled(false); registerButton.setEnabled(false); resetButton.setEnabled(false);

        String finalHost = host;
        int finalPort = port;

        new Thread(() -> {
            try {
                if(socket==null || socket.isClosed()) {
                    socket = new Socket(finalHost, finalPort);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                    String k = in.readLine();
                    if(k!=null && k.startsWith("KEY:")) {
                        serverKey = CryptoUtils.getPublicKeyFromBytes(k.substring(4));
                        out.println("KEY:" + CryptoUtils.publicKeyToString(myPublicKey));
                    }
                }
                String auth = cmd + " " + u + " " + p + (extra!=null?" "+extra:"");
                out.println(CryptoUtils.encrypt(auth, serverKey));
                startListener();
            } catch(Exception e) {
                SwingUtilities.invokeLater(()-> {
                    statusLabel.setText("Chyba: " + e.getMessage());
                    loginButton.setEnabled(true); registerButton.setEnabled(true); resetButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void startListener() {
        running = true;
        new Thread(() -> {
            try {
                String l;
                while(running && (l = in.readLine()) != null) {
                    String finalL = l;
                    SwingUtilities.invokeLater(() -> processIncomingMessage(finalL));
                }
            } catch(IOException e) { SwingUtilities.invokeLater(this::disconnect); }
        }).start();
    }

    private void processIncomingMessage(String line) {
        try {
            if (line.startsWith("AUTH_REQ:")) {
                statusLabel.setText(line.substring(9));
            }
            else if (line.equals("AUTH_OK")) {
                statusLabel.setText("Přihlášeno.");
                cardLayout.show(mainPanel, "CHAT");
                chatInfoLabel.setText("Přihlášen jako: " + userField.getText());
                addMessageBubble(0, "SYSTEM", "👋 Vítej v chatu, " + userField.getText() + "!", "TEXT", null);
            }
            else if (line.startsWith("MSG:")) {
                String content = line.substring(4);
                if (content.contains(":") && content.split(":").length >= 3) {
                    String[] parts = line.split(":", 4);
                    int id = Integer.parseInt(parts[1]);
                    String sender = parts[2];
                    String text = parts[3];
                    if (text.contains("Tvůj status:") && text.contains("ADMIN")) iAmAdmin = true;
                    addMessageBubble(id, sender, text, "TEXT", null);
                } else {
                    JOptionPane.showMessageDialog(this, content);
                    loginButton.setEnabled(true); registerButton.setEnabled(true); resetButton.setEnabled(true);
                }
            }
            else if (line.startsWith("MSG_EDIT:")) {
                String[] parts = line.split(":", 3);
                updateMessageContent(Integer.parseInt(parts[1]), parts[2]);
            }
            else if (line.startsWith("DELETE_MSG:")) {
                int id = Integer.parseInt(line.split(":")[1]);
                removeMessageBubble(id);
            }
            else if (line.startsWith("CLEAR_USER:")) {
                String target = line.split(":")[1];
                if (userMessagesMap.containsKey(target)) {
                    for (int id : userMessagesMap.get(target)) removeMessageBubble(id);
                    userMessagesMap.remove(target);
                }
            }
            else if (line.startsWith("FILE:") || line.startsWith("IMG:")) {
                String[] parts = line.split(":", 4);
                byte[] data = Base64.getDecoder().decode(parts[3]);
                String type = parts[2].toLowerCase().matches(".*(jpg|png|gif|jpeg)$") ? "IMAGE" : "FILE";
                addMessageBubble(0, parts[1], parts[2], type, data);
            }
            else if (line.startsWith("ENC:")) {
                String dec = CryptoUtils.decrypt(line.substring(4), myPrivateKey);
                if (dec.startsWith("DISCONNECT:")) {
                    disconnect(); JOptionPane.showMessageDialog(this, dec);
                } else if (dec.startsWith("MUTE_START:")) {
                    int seconds = Integer.parseInt(dec.split(":")[1]);
                    activateMuteState(seconds);
                } else {
                    processIncomingMessage(dec);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void activateMuteState(int seconds) {
        messageField.setBackground(new Color(255, 200, 200));
        messageField.setText("🛑 JSI ZTLUMEN (" + seconds + "s)");
        messageField.setEnabled(false);
        sendButton.setEnabled(false);
        fileButton.setEnabled(false);

        Timer timer = new Timer(seconds * 1000, e -> {
            messageField.setBackground(Color.WHITE);
            messageField.setText("");
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            fileButton.setEnabled(true);
            messageField.requestFocus();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void sendMessage() {
        String text = messageField.getText();
        if (text.trim().isEmpty()) return;
        try {
            if (text.equalsIgnoreCase("/quit")) { disconnect(); return; }
            out.println(CryptoUtils.encrypt(text, serverKey));
            messageField.setText("");
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Chyba odeslání: " + e.getMessage()); }
    }

    private void uploadFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (f.length() > 15 * 1024 * 1024) { JOptionPane.showMessageDialog(this, "Max 15MB!"); return; }
            try {
                byte[] content = Files.readAllBytes(f.toPath());
                String base64 = Base64.getEncoder().encodeToString(content);
                out.println("FILE:" + myNick + ":" + f.getName() + ":" + base64);
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void sendAdminCommand(String cmd) { try { out.println(CryptoUtils.encrypt(cmd, serverKey)); } catch (Exception e) {} }

    private void disconnect() {
        running = false;
        try { if(out!=null) out.println(CryptoUtils.encrypt("/quit", serverKey)); if(socket!=null) socket.close(); } catch(Exception e){}
        socket = null;
        cardLayout.show(mainPanel, "LOGIN");
        messagesBox.removeAll();
        loginButton.setEnabled(true); registerButton.setEnabled(true); resetButton.setEnabled(true);
        statusLabel.setText("Odpojeno.");
    }

    private void startScan() {
        scanButton.setEnabled(false); statusLabel.setText("Skenuji...");
        serverListModel.clear();
        serverListModel.addElement("localhost");
        LanScanner.scan(5555, new LanScanner.ScanCallback() {
            public void onServerFound(String ip) { SwingUtilities.invokeLater(() -> { if(!serverListModel.contains(ip)) serverListModel.addElement(ip); }); }
            public void onScanFinished() { SwingUtilities.invokeLater(() -> { scanButton.setEnabled(true); statusLabel.setText("Hotovo."); }); }
        });
    }

    public static void main(String[] args) { try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {} SwingUtilities.invokeLater(() -> new Klient().setVisible(true)); }
}