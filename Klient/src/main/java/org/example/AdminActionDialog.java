package org.example;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import org.example.helpers.ModernTheme;

public class AdminActionDialog extends JDialog {
    private final JTextField reasonField;
    private final JSpinner durationSpinner;
    private final JComboBox<String> unitCombo;
    private final JPanel durationPanel;

    public AdminActionDialog(Frame parent, Klient app, String action, String targetUser) {
        super(parent, "Admin: " + action, true);
        this.setSize(400, 380);
        this.setLocationRelativeTo(parent);
        this.setLayout(new BorderLayout());
        this.getContentPane().setBackground(Color.WHITE);

        // --- HLAVIČKA ---
        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setBackground(new Color(245, 247, 250));
        header.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel(action + ": " + targetUser, SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(getActionColor(action));

        JLabel sub = new JLabel("Nastavení trestu", SwingConstants.CENTER);
        sub.setFont(ModernTheme.FONT_PLAIN);
        sub.setForeground(Color.GRAY);

        header.add(title);
        header.add(sub);
        this.add(header, BorderLayout.NORTH);

        // --- FORMULÁŘ ---
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 15));
        form.setBackground(Color.WHITE);
        form.setBorder(new EmptyBorder(20, 30, 20, 30));

        this.reasonField = new JTextField();
        ModernTheme.styleTextField(this.reasonField);
        this.reasonField.setBorder(BorderFactory.createTitledBorder("Důvod (zobrazí se uživateli)"));

        this.durationPanel = new JPanel(new BorderLayout(10, 0));
        this.durationPanel.setBackground(Color.WHITE);
        this.durationPanel.setBorder(BorderFactory.createTitledBorder("Délka trvání"));

        this.durationSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
        this.durationSpinner.setFont(ModernTheme.FONT_PLAIN);

        this.unitCombo = new JComboBox<>(new String[]{"Sekund", "Minut", "Hodin", "Dní", "Navždy (Perma)"});
        this.unitCombo.setFont(ModernTheme.FONT_PLAIN);
        this.unitCombo.setBackground(Color.WHITE);
        this.unitCombo.setSelectedIndex(1);
        this.unitCombo.addActionListener(e -> {
            boolean perma = "Navždy (Perma)".equals(unitCombo.getSelectedItem());
            durationSpinner.setEnabled(!perma);
        });

        this.durationPanel.add(durationSpinner, BorderLayout.CENTER);
        this.durationPanel.add(unitCombo, BorderLayout.EAST);

        form.add(reasonField);
        form.add(durationPanel);
        this.add(form, BorderLayout.CENTER);

        // --- TLAČÍTKA ---
        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        btnPanel.setBackground(Color.WHITE);
        btnPanel.setBorder(new EmptyBorder(10, 30, 20, 30));

        JButton cancelBtn = ModernTheme.createButton("Zrušit", false);
        cancelBtn.addActionListener(e -> dispose());

        JButton confirmBtn = ModernTheme.createButton("PROVÉST", true);
        confirmBtn.setBackground(getActionColor(action));
        confirmBtn.addActionListener(e -> {
            sendCommand(app, action, targetUser);
            dispose();
        });

        btnPanel.add(cancelBtn);
        btnPanel.add(confirmBtn);
        this.add(btnPanel, BorderLayout.SOUTH);

        // Skrytí prvků podle akce
        if (action.equals("KICK")) {
            durationPanel.setVisible(false);
        } else if (action.equals("BAN")) {
            unitCombo.setSelectedItem("Navždy (Perma)");
        }
    }

    private Color getActionColor(String action) {
        return switch (action) {
            case "KICK" -> new Color(255, 140, 0);
            case "BAN" -> ModernTheme.ERROR;
            default -> Color.GRAY;
        };
    }

    // ... Metody sendCommand a calculateSeconds zůstávají stejné jako ve vašem kódu ...
    private void sendCommand(Klient app, String action, String target) {
        String reason = this.reasonField.getText().trim();
        if (reason.isEmpty()) reason = "Porušení pravidel";
        StringBuilder cmd = new StringBuilder();
        if (action.equals("KICK")) {
            cmd.append("/kick ").append(target).append(" ").append(reason);
        } else if (action.equals("MUTE")) {
            long seconds = this.calculateSeconds();
            cmd.append("/mute ").append(target).append(" ").append(seconds).append(" ").append(reason);
        } else if (action.equals("BAN")) {
            long seconds = this.calculateSeconds();
            if (seconds != -1L) reason = reason + " (Délka: " + durationSpinner.getValue() + " " + unitCombo.getSelectedItem() + ")";
            cmd.append("/ban ").append(target).append(" ").append(seconds).append(" ").append(reason);
        }
        app.getNetwork().sendEncryptedMessage(cmd.toString());
    }

    private long calculateSeconds() {
        if ("Navždy (Perma)".equals(unitCombo.getSelectedItem())) return -1L;
        int val = (Integer) durationSpinner.getValue();
        return switch ((String) unitCombo.getSelectedItem()) {
            case "Minut" -> val * 60L;
            case "Hodin" -> val * 3600L;
            case "Dní" -> val * 86400L;
            default -> (long) val;
        };
    }
}