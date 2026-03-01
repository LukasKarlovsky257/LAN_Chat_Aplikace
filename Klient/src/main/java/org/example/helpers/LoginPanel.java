package org.example.helpers;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import org.example.Klient;
import org.example.LanScanner;

public class LoginPanel extends JPanel {
    private final Klient app;
    private JTextField ipField;
    private JTextField userField;
    private JPasswordField passField;
    private JButton loginButton;
    private JButton registerButton;
    private JButton resetButton;
    private JButton scanButton;
    private JLabel statusLabel;
    private DefaultListModel<String> serverListModel;
    private JList<String> list;

    public LoginPanel(Klient app) {
        this.app = app;
        this.setLayout(new GridBagLayout());
        this.setBackground(ModernTheme.BG_MAIN);

        // --- HLAVNÍ KARTA ---
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new ModernTheme.RoundedBorder(20, new Color(220, 220, 220)),
                new EmptyBorder(30, 40, 30, 40)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 10, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // --- 1. NADPISY ---
        JLabel titleLabel = new JLabel("Vítejte zpět", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(ModernTheme.TEXT_DARK);
        card.add(titleLabel, gbc);

        gbc.gridy++;
        JLabel subLabel = new JLabel("Přihlaste se do chatu", SwingConstants.CENTER);
        subLabel.setFont(ModernTheme.FONT_PLAIN);
        subLabel.setForeground(ModernTheme.TEXT_GRAY);
        gbc.insets = new Insets(0, 0, 25, 0);
        card.add(subLabel, gbc);

        // --- 2. FORMULÁŘ ---
        gbc.insets = new Insets(0, 0, 15, 0);

        // IP Adresa (Upravená nápověda pro Ngrok)
        gbc.gridy++;
        this.ipField = createStyledInput("IP:Port (např. localhost nebo 0.tcp.ngrok.io:12345)");
        this.ipField.setText("localhost");
        card.add(this.ipField, gbc);

        // Jméno
        gbc.gridy++;
        this.userField = createStyledInput("Uživatelské jméno");
        this.userField.addActionListener(e -> passField.requestFocusInWindow());
        card.add(this.userField, gbc);

        // Heslo
        gbc.gridy++;
        this.passField = new JPasswordField();
        ModernTheme.styleTextField(this.passField);
        this.passField.setBorder(BorderFactory.createTitledBorder(
                new ModernTheme.RoundedBorder(15, new Color(200, 200, 200)), "Heslo"
        ));
        this.passField.setPreferredSize(new Dimension(300, 50));
        this.passField.addActionListener(e -> doConnect("/login"));
        card.add(this.passField, gbc);

        // --- 3. LAN SCANNER ---
        gbc.gridy++;
        JPanel scanPanel = new JPanel(new BorderLayout(5, 0));
        scanPanel.setBackground(Color.WHITE);

        this.serverListModel = new DefaultListModel<>();
        this.serverListModel.addElement("localhost");

        this.list = new JList<>(this.serverListModel);
        this.list.setFont(ModernTheme.FONT_PLAIN);
        this.list.setFixedCellHeight(25);
        this.list.setBorder(new ModernTheme.RoundedBorder(10, new Color(230, 230, 230)));
        this.list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                ipField.setText(list.getSelectedValue());
            }
        });

        JScrollPane scroll = new JScrollPane(this.list);
        scroll.setPreferredSize(new Dimension(300, 70));
        scroll.setBorder(null);

        this.scanButton = ModernTheme.createButton("🔍", false);
        this.scanButton.setToolTipText("Skenovat síť");
        this.scanButton.setPreferredSize(new Dimension(50, 70));
        this.scanButton.addActionListener(e -> startScan());

        JPanel labelPanel = new JPanel(new BorderLayout());
        labelPanel.setBackground(Color.WHITE);
        JLabel scanLbl = new JLabel("Dostupné servery:");
        scanLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        labelPanel.add(scanLbl, BorderLayout.WEST);

        scanPanel.add(labelPanel, BorderLayout.NORTH);
        scanPanel.add(scroll, BorderLayout.CENTER);
        scanPanel.add(this.scanButton, BorderLayout.EAST);

        card.add(scanPanel, gbc);

        // --- 4. TLAČÍTKA ---
        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 10, 0);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonPanel.setBackground(Color.WHITE);

        this.loginButton = ModernTheme.createButton("Přihlásit", true);
        this.loginButton.addActionListener(e -> doConnect("/login"));

        this.registerButton = ModernTheme.createButton("Registrace", false);
        this.registerButton.addActionListener(e -> doConnect("/register"));

        buttonPanel.add(this.loginButton);
        buttonPanel.add(this.registerButton);

        card.add(buttonPanel, gbc);

        // --- 5. RESET HESLA ---
        gbc.gridy++;
        this.resetButton = new JButton("Zapomněli jste heslo?");
        this.resetButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        this.resetButton.setForeground(ModernTheme.PRIMARY);
        this.resetButton.setContentAreaFilled(false);
        this.resetButton.setBorderPainted(false);
        this.resetButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        this.resetButton.addActionListener(e -> doReset());
        card.add(this.resetButton, gbc);

        // --- 6. STATUS LABEL ---
        gbc.gridy++;
        this.statusLabel = new JLabel(" ");
        this.statusLabel.setForeground(ModernTheme.ERROR);
        this.statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        this.statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        card.add(this.statusLabel, gbc);

        this.add(card);
    }

    private JTextField createStyledInput(String title) {
        JTextField field = new JTextField();
        ModernTheme.styleTextField(field);
        field.setBorder(BorderFactory.createTitledBorder(
                new ModernTheme.RoundedBorder(15, new Color(200, 200, 200)), title
        ));
        field.setPreferredSize(new Dimension(300, 50));
        return field;
    }

    // Zajišťuje povolení/zakázání prvků
    public void toggleControls(boolean enable) {
        SwingUtilities.invokeLater(() -> {
            ipField.setEnabled(enable);
            userField.setEnabled(enable);
            passField.setEnabled(enable);
            loginButton.setEnabled(enable);
            registerButton.setEnabled(enable);
            resetButton.setEnabled(enable);
            scanButton.setEnabled(enable);
            list.setEnabled(enable);

            if (enable) {
                setCursor(Cursor.getDefaultCursor());
            } else {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
        });
    }

    public void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    public String getUserField() {
        return userField.getText().trim();
    }

    private void doConnect(String command) {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword());
        String rawHost = ipField.getText().trim();

        if (u.isEmpty() || p.isEmpty() || rawHost.isEmpty()) {
            setStatus("Vyplňte všechna pole!");
            return;
        }

        if (app.getMyPublicKey() == null) {
            JOptionPane.showMessageDialog(this, "Generuji šifrování, vydržte...");
            return;
        }

        // Zpracování dynamického portu
        String host = rawHost;
        int port = 5555; // Výchozí port
        if (rawHost.contains(":")) {
            String[] parts = rawHost.split(":");
            host = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                setStatus("Neplatný formát portu!");
                return;
            }
        }

        final String extra;
        if (command.equals("/register")) {
            String code = JOptionPane.showInputDialog(this, "Zadej bezpečnostní kód (pro obnovu hesla):");
            if (code == null || code.trim().isEmpty()) return;
            extra = code;
        } else {
            extra = "";
        }

        setStatus("Připojuji na " + host + ":" + port + "...");
        toggleControls(false); // Zamkneme prvky

        final String finalHost = host;
        final int finalPort = port;

        new Thread(() -> {
            try {
                app.connectToServer(finalHost, finalPort, command, u, p, extra);
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    toggleControls(true); // Odemkneme prvky
                    setStatus("Chyba: " + e.getMessage());
                    if (e.getMessage() != null && !e.getMessage().contains("Socket closed")) {
                        JOptionPane.showMessageDialog(this, "Chyba připojení:\n" + e.getMessage());
                    }
                });
            }
        }).start();
    }

    private void doReset() {
        JTextField u = new JTextField();
        JTextField c = new JTextField();
        JPasswordField p = new JPasswordField();
        Object[] message = {"Uživatelské jméno:", u, "Bezpečnostní kód:", c, "Nové heslo:", p};

        int option = JOptionPane.showConfirmDialog(this, message, "Reset Hesla", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            if (u.getText().isEmpty() || c.getText().isEmpty()) return;

            String rawHost = ipField.getText().trim();
            if (rawHost.isEmpty()) {
                setStatus("Vyplňte IP adresu serveru!");
                return;
            }

            // Zpracování dynamického portu i pro resetování hesla
            String host = rawHost;
            int port = 5555;
            if (rawHost.contains(":")) {
                String[] parts = rawHost.split(":");
                host = parts[0];
                try {
                    port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ex) {
                    setStatus("Neplatný formát portu!");
                    return;
                }
            }

            setStatus("Resetuji heslo...");
            toggleControls(false);

            final String finalHost = host;
            final int finalPort = port;

            new Thread(() -> {
                try {
                    app.connectToServer(finalHost, finalPort, "/reset", u.getText(), new String(p.getPassword()), c.getText());
                } catch (Exception e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        setStatus("Chyba resetu: " + e.getMessage());
                        toggleControls(true);
                        JOptionPane.showMessageDialog(this, "Reset selhal:\n" + e.getMessage());
                    });
                }
            }).start();
        }
    }

    private void startScan() {
        scanButton.setEnabled(false);
        setStatus("Skenuji síť...");
        serverListModel.clear();
        serverListModel.addElement("localhost");

        new Thread(() -> {
            LanScanner.scan(5555, new LanScanner.ScanCallback() {
                @Override
                public void onServerFound(String ip) {
                    SwingUtilities.invokeLater(() -> serverListModel.addElement(ip));
                }
                @Override
                public void onScanFinished() {
                    SwingUtilities.invokeLater(() -> {
                        setStatus("Skenování dokončeno.");
                        scanButton.setEnabled(true);
                    });
                }
            });
        }).start();
    }
}