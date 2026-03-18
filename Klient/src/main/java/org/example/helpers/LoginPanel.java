package org.example.helpers;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Base64;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import org.example.Klient;
import org.example.LanScanner;
import org.example.managers.ConfigManager;

public class LoginPanel extends JPanel {
    private final Klient app;
    private JTextField ipField;
    private JTextField userField;
    private JPasswordField passField;
    private JCheckBox rememberMeCheck;
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
        this.setBackground(ModernTheme.BG_BASE);
        this.setOpaque(true);

        JPanel card = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ModernTheme.GLASS_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(ModernTheme.GLASS_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(30, 40, 30, 40));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 10, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        JLabel titleLabel = new JLabel("Vítej v Síti", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(ModernTheme.NEON_CYAN);
        card.add(titleLabel, gbc);

        gbc.gridy++;
        JLabel subLabel = new JLabel("Inicializuj spojení", SwingConstants.CENTER);
        subLabel.setFont(ModernTheme.FONT_PLAIN);
        subLabel.setForeground(ModernTheme.TEXT_MUTED);
        gbc.insets = new Insets(0, 0, 25, 0);
        card.add(subLabel, gbc);

        gbc.insets = new Insets(0, 0, 15, 0);

        gbc.gridy++;
        this.ipField = new JTextField("localhost");
        card.add(createInputGroup("IP adresa serveru (např. localhost, 1.1.1.1:8080)", this.ipField), gbc);

        gbc.gridy++;
        this.userField = new JTextField();
        this.userField.addActionListener(e -> passField.requestFocusInWindow());
        card.add(createInputGroup("Identifikátor (Jméno)", this.userField), gbc);

        gbc.gridy++;
        this.passField = new JPasswordField();
        this.passField.addActionListener(e -> doConnect("/login"));
        card.add(createInputGroup("Bezpečnostní klíč (Heslo)", this.passField), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(-5, 0, 15, 0);
        this.rememberMeCheck = new JCheckBox("Pamatovat si mě (Auto-Login)");
        this.rememberMeCheck.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        this.rememberMeCheck.setForeground(ModernTheme.TEXT_MUTED);
        this.rememberMeCheck.setOpaque(false);
        this.rememberMeCheck.setFocusPainted(false);
        this.rememberMeCheck.setCursor(new Cursor(Cursor.HAND_CURSOR));
        card.add(this.rememberMeCheck, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        JPanel scanPanel = new JPanel(new BorderLayout(5, 0));
        scanPanel.setOpaque(false);

        this.serverListModel = new DefaultListModel<>();
        this.serverListModel.addElement("localhost");

        this.list = new JList<>(this.serverListModel);
        this.list.setFont(ModernTheme.FONT_PLAIN);
        this.list.setFixedCellHeight(25);
        this.list.setBackground(new Color(20, 24, 34));
        this.list.setForeground(ModernTheme.TEXT_MAIN);
        this.list.setSelectionBackground(ModernTheme.NEON_CYAN);
        this.list.setSelectionForeground(Color.BLACK);
        this.list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) ipField.setText(list.getSelectedValue());
        });

        JScrollPane scroll = new JScrollPane(this.list);
        scroll.setPreferredSize(new Dimension(300, 80));
        scroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ModernTheme.GLASS_BORDER, 1), new EmptyBorder(2, 2, 2, 2)));
        scroll.setOpaque(false); scroll.getViewport().setOpaque(false);

        this.scanButton = ModernTheme.createButton("🔍", false);
        this.scanButton.setPreferredSize(new Dimension(50, 80));
        this.scanButton.addActionListener(e -> startScan());

        JLabel scanLbl = new JLabel("Dostupné uzly:");
        scanLbl.setForeground(ModernTheme.TEXT_MUTED); scanLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        scanLbl.setBorder(new EmptyBorder(0, 0, 5, 0));

        scanPanel.add(scanLbl, BorderLayout.NORTH); scanPanel.add(scroll, BorderLayout.CENTER); scanPanel.add(this.scanButton, BorderLayout.EAST);
        card.add(scanPanel, gbc);

        gbc.gridy++; gbc.insets = new Insets(15, 0, 10, 0);
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonPanel.setOpaque(false);
        this.loginButton = ModernTheme.createButton("Připojit", true);
        this.loginButton.addActionListener(e -> doConnect("/login"));
        this.registerButton = ModernTheme.createButton("Nová Identita", false);
        this.registerButton.addActionListener(e -> doConnect("/register"));
        buttonPanel.add(this.loginButton); buttonPanel.add(this.registerButton);
        card.add(buttonPanel, gbc);

        gbc.gridy++; gbc.insets = new Insets(0, 0, 10, 0);
        this.resetButton = new JButton("Obnovit přístup (Reset hesla)");
        this.resetButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        this.resetButton.setForeground(new Color(255, 140, 0));
        this.resetButton.setContentAreaFilled(false); this.resetButton.setBorderPainted(false);
        this.resetButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        this.resetButton.addActionListener(e -> showResetDialog());
        card.add(this.resetButton, gbc);

        gbc.gridy++;
        this.statusLabel = new JLabel(" ");
        this.statusLabel.setForeground(ModernTheme.DANGER);
        this.statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        this.statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        card.add(this.statusLabel, gbc);

        this.add(card);
    }

    private JPanel createInputGroup(String title, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setOpaque(false);

        JLabel lbl = new JLabel(title);
        lbl.setForeground(ModernTheme.TEXT_MUTED);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));

        field.setOpaque(true);
        field.setBackground(new Color(20, 24, 34));
        field.setForeground(ModernTheme.TEXT_MAIN);
        field.setCaretColor(ModernTheme.NEON_CYAN);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ModernTheme.GLASS_BORDER, 1),
                new EmptyBorder(12, 15, 12, 15)
        ));
        field.setPreferredSize(new Dimension(300, 45));

        panel.add(lbl, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private void showResetDialog() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Obnova přístupu", true);
        dialog.setSize(380, 420); dialog.setLocationRelativeTo(this); dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(ModernTheme.BG_BASE);
        JPanel panel = new JPanel(new GridBagLayout()); panel.setOpaque(false); panel.setBorder(new EmptyBorder(20, 25, 20, 25));
        GridBagConstraints dgbc = new GridBagConstraints(); dgbc.gridx = 0; dgbc.gridy = 0; dgbc.fill = GridBagConstraints.HORIZONTAL; dgbc.weightx = 1.0; dgbc.insets = new Insets(0, 0, 15, 0);
        JLabel title = new JLabel("Bezpečnostní obnova", SwingConstants.CENTER); title.setFont(new Font("Segoe UI", Font.BOLD, 20)); title.setForeground(new Color(255, 140, 0)); panel.add(title, dgbc);

        JTextField uField = new JTextField(); JTextField cField = new JTextField(); JPasswordField pField = new JPasswordField();
        dgbc.gridy++; panel.add(createInputGroup("Identifikátor (Jméno):", uField), dgbc);
        dgbc.gridy++; panel.add(createInputGroup("Recovery Kód:", cField), dgbc);
        dgbc.gridy++; panel.add(createInputGroup("Nové heslo:", pField), dgbc);

        JButton confirmBtn = ModernTheme.createButton("Provést reset", true); confirmBtn.setBackground(new Color(255, 140, 0));
        confirmBtn.addActionListener(e -> {
            if (uField.getText().isEmpty() || cField.getText().isEmpty() || pField.getPassword().length == 0) {
                ModernDialog.showMessage(dialog, "Chyba", "Vyplňte všechna pole!", true);
                return;
            }
            dialog.dispose(); executeReset(uField.getText().trim(), cField.getText().trim(), new String(pField.getPassword()));
        });

        JButton cancelBtn = ModernTheme.createButton("Zrušit", false); cancelBtn.addActionListener(e -> dialog.dispose());
        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 10, 0)); btnPanel.setOpaque(false); btnPanel.setBorder(new EmptyBorder(0, 25, 25, 25));
        btnPanel.add(cancelBtn); btnPanel.add(confirmBtn);
        dialog.add(panel, BorderLayout.CENTER); dialog.add(btnPanel, BorderLayout.SOUTH); dialog.setVisible(true);
    }

    public void toggleControls(boolean enable) {
        SwingUtilities.invokeLater(() -> {
            ipField.setEnabled(enable); userField.setEnabled(enable); passField.setEnabled(enable); rememberMeCheck.setEnabled(enable);
            loginButton.setEnabled(enable); registerButton.setEnabled(enable); resetButton.setEnabled(enable); scanButton.setEnabled(enable); list.setEnabled(enable);
            if (enable) setCursor(Cursor.getDefaultCursor()); else setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        });
    }

    public void setStatus(String msg) { SwingUtilities.invokeLater(() -> statusLabel.setText(msg)); }
    public String getUserField() { return userField.getText().trim(); }

    public void attemptAutoLogin() {
        if (ConfigManager.autoLogin && !ConfigManager.savedUser.isEmpty() && !ConfigManager.savedPass.isEmpty()) {
            this.userField.setText(ConfigManager.savedUser);
            this.passField.setText(new String(Base64.getDecoder().decode(ConfigManager.savedPass)));
            this.rememberMeCheck.setSelected(true);
            doConnect("/login");
        }
    }

    private void doConnect(String command) {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword());
        String rawHost = ipField.getText().trim();

        if (u.isEmpty() || p.isEmpty() || rawHost.isEmpty()) { setStatus("Vyplňte všechna pole!"); return; }
        if (app.getMyPublicKey() == null) {
            ModernDialog.showMessage(SwingUtilities.getWindowAncestor(this), "Info", "Generuji šifrování, vydržte...", false);
            return;
        }

        if (this.rememberMeCheck.isSelected() && command.equals("/login")) {
            ConfigManager.autoLogin = true; ConfigManager.savedUser = u;
            ConfigManager.savedPass = Base64.getEncoder().encodeToString(p.getBytes()); ConfigManager.save();
        } else {
            ConfigManager.autoLogin = false; ConfigManager.savedUser = ""; ConfigManager.savedPass = ""; ConfigManager.save();
        }

        String host = rawHost; int port = 5555;
        if (rawHost.contains(":")) {
            String[] parts = rawHost.split(":"); host = parts[0];
            try { port = Integer.parseInt(parts[1]); } catch (NumberFormatException ex) { setStatus("Neplatný formát portu!"); return; }
        }

        final String extra;
        if (command.equals("/register")) {
            String code = ModernDialog.showInput(SwingUtilities.getWindowAncestor(this), "Registrace", "Zadej bezpečnostní kód (pro obnovu hesla):");
            if (code == null || code.trim().isEmpty()) return; extra = code;
        } else { extra = ""; }

        setStatus("Připojuji na " + host + ":" + port + "..."); toggleControls(false);
        final String finalHost = host; final int finalPort = port;

        new Thread(() -> {
            try { app.connectToServer(finalHost, finalPort, command, u, p, extra); }
            catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    toggleControls(true); setStatus("Chyba: " + e.getMessage());
                    if (e.getMessage() != null && !e.getMessage().contains("Socket closed")) {
                        ModernDialog.showMessage(SwingUtilities.getWindowAncestor(this), "Chyba připojení", e.getMessage(), true);
                    }
                });
            }
        }).start();
    }

    private void executeReset(String username, String code, String newPass) {
        String rawHost = ipField.getText().trim();
        if (rawHost.isEmpty()) { setStatus("Vyplňte IP adresu serveru na hlavní obrazovce!"); return; }
        String host = rawHost; int port = 5555;
        if (rawHost.contains(":")) { String[] parts = rawHost.split(":"); host = parts[0]; try { port = Integer.parseInt(parts[1]); } catch (NumberFormatException ex) { setStatus("Neplatný formát portu!"); return; } }
        setStatus("Obnovuji heslo..."); toggleControls(false);
        final String finalHost = host; final int finalPort = port;
        new Thread(() -> {
            try { app.connectToServer(finalHost, finalPort, "/reset", username, newPass, code); }
            catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    setStatus("Chyba obnovy: " + e.getMessage()); toggleControls(true);
                    ModernDialog.showMessage(SwingUtilities.getWindowAncestor(this), "Obnova selhala", e.getMessage(), true);
                });
            }
        }).start();
    }

    private void startScan() {
        scanButton.setEnabled(false); setStatus("Skenuji síť..."); serverListModel.clear(); serverListModel.addElement("localhost");
        new Thread(() -> { LanScanner.scan(5555, new LanScanner.ScanCallback() { @Override public void onServerFound(String ip) { SwingUtilities.invokeLater(() -> serverListModel.addElement(ip)); } @Override public void onScanFinished() { SwingUtilities.invokeLater(() -> { setStatus("Skenování dokončeno."); scanButton.setEnabled(true); }); } }); }).start();
    }
}