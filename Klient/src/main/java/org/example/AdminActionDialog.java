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
        super(parent, "Admin Protokol: " + action, true);
        this.setSize(400, 420);
        this.setLocationRelativeTo(parent);
        this.setLayout(new BorderLayout());
        this.getContentPane().setBackground(ModernTheme.BG_BASE);

        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setBackground(new Color(15, 18, 25));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0,0,1,0, ModernTheme.GLASS_BORDER),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JLabel title = new JLabel(action + ": " + targetUser, SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(getActionColor(action));

        JLabel sub = new JLabel("Konfigurace trestu", SwingConstants.CENTER);
        sub.setFont(ModernTheme.FONT_PLAIN);
        sub.setForeground(ModernTheme.TEXT_MUTED);

        header.add(title);
        header.add(sub);
        this.add(header, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(20, 30, 20, 30));

        JPanel reasonWrapper = new JPanel(new BorderLayout(0, 5));
        reasonWrapper.setOpaque(false);
        
        JLabel reasonLabel = new JLabel("Důvod zásahu:");
        reasonLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        reasonLabel.setForeground(ModernTheme.NEON_CYAN);
        reasonWrapper.add(reasonLabel, BorderLayout.NORTH);

        this.reasonField = new JTextField();
        styleInputField(this.reasonField);
        reasonWrapper.add(this.reasonField, BorderLayout.CENTER);

        this.durationPanel = new JPanel(new BorderLayout(0, 5));
        this.durationPanel.setOpaque(false);

        JLabel durationLabel = new JLabel("Délka trvání:");
        durationLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        durationLabel.setForeground(ModernTheme.NEON_CYAN);
        this.durationPanel.add(durationLabel, BorderLayout.NORTH);

        JPanel spinnerWrapper = new JPanel(new BorderLayout(10, 0));
        spinnerWrapper.setOpaque(false);

        this.durationSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
        this.durationSpinner.setFont(ModernTheme.FONT_PLAIN);
        this.durationSpinner.setBorder(BorderFactory.createLineBorder(ModernTheme.GLASS_BORDER));

        JComponent editor = durationSpinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor)editor).getTextField();
            styleInputField(tf);
            tf.setHorizontalAlignment(JTextField.CENTER);
            tf.setDisabledTextColor(Color.WHITE);
        }

        this.unitCombo = new JComboBox<>(new String[]{"Sekund", "Minut", "Hodin", "Dní", "Navždy (Perma)"});
        this.unitCombo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        this.unitCombo.setBackground(new Color(20, 24, 34));
        this.unitCombo.setForeground(Color.WHITE);
        this.unitCombo.setSelectedIndex(1);
        this.unitCombo.setFocusable(false);

        UIManager.put("ComboBox.selectionBackground", ModernTheme.NEON_CYAN);
        UIManager.put("ComboBox.selectionForeground", Color.BLACK);

        spinnerWrapper.add(this.durationSpinner, BorderLayout.CENTER);
        spinnerWrapper.add(this.unitCombo, BorderLayout.EAST);
        this.durationPanel.add(spinnerWrapper, BorderLayout.CENTER);

        this.unitCombo.addActionListener(e -> {
            boolean perma = "Navždy (Perma)".equals(unitCombo.getSelectedItem());
            durationSpinner.setEnabled(!perma);
            if (editor instanceof JSpinner.DefaultEditor) {
                ((JSpinner.DefaultEditor)editor).getTextField().setBackground(perma ? new Color(40, 40, 40) : new Color(20, 24, 34));
            }
        });

        form.add(reasonWrapper);
        form.add(Box.createVerticalStrut(20));
        form.add(this.durationPanel);
        this.add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(new EmptyBorder(10, 30, 25, 30));

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

        if (action.equals("KICK")) {
            durationPanel.setVisible(false);
        } else if (action.equals("BAN")) {
            unitCombo.setSelectedItem("Navždy (Perma)");
        }
    }

    private void styleInputField(JTextField field) {
        field.setBackground(new Color(20, 24, 34));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setBorder(new EmptyBorder(8, 10, 8, 10));
    }

    private Color getActionColor(String action) {
        return switch (action) {
            case "KICK" -> new Color(255, 140, 0);
            case "BAN" -> ModernTheme.DANGER;
            case "MUTE" -> ModernTheme.NEON_CYAN;
            default -> Color.GRAY;
        };
    }

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