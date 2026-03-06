package org.example.helpers;

import org.example.managers.ConfigManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SettingsDialog extends JDialog {
    private final JCheckBox soundCheck;
    private final JComboBox<String> colorBox;

    public SettingsDialog(Frame parent, ChatPanel chatPanel) {
        super(parent, "Nastavení", true);
        setSize(350, 320);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(ModernTheme.BG_BASE);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(15, 18, 25));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.GLASS_BORDER),
                new EmptyBorder(15, 20, 15, 20)
        ));

        JLabel title = new JLabel("Nastavení Systému");
        title.setForeground(ModernTheme.NEON_CYAN);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        header.add(title, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        soundCheck = new JCheckBox("Přehrávat zvuk pípnutí zpráv");
        soundCheck.setOpaque(false);
        soundCheck.setFocusPainted(false);
        soundCheck.setSelected(ConfigManager.playSounds);
        soundCheck.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        soundCheck.setForeground(ModernTheme.TEXT_MAIN);

        JLabel colorLabel = new JLabel("Barva tvé identity (Bublin):");
        colorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        colorLabel.setForeground(ModernTheme.TEXT_MUTED);
        colorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[] colors = {"Modrá (Výchozí)", "Zelená", "Fialová", "Oranžová", "Tmavě Šedá"};
        colorBox = new JComboBox<>(colors);
        colorBox.setMaximumSize(new Dimension(300, 35));
        colorBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        colorBox.setForeground(ModernTheme.TEXT_MAIN);
        colorBox.setBackground(ModernTheme.INPUT_BG);

        switch (ConfigManager.myBubbleColor) {
            case "#0084ff": colorBox.setSelectedIndex(0); break;
            case "#2ecc71": colorBox.setSelectedIndex(1); break;
            case "#9b59b6": colorBox.setSelectedIndex(2); break;
            case "#e67e22": colorBox.setSelectedIndex(3); break;
            case "#34495e": colorBox.setSelectedIndex(4); break;
        }

        content.add(soundCheck);
        content.add(Box.createVerticalStrut(20));
        content.add(colorLabel);
        content.add(Box.createVerticalStrut(5));
        content.add(colorBox);
        content.add(Box.createVerticalStrut(20));

        // Tlačítka pro pozadí
        JButton btnUploadBg = ModernTheme.createButton("Nahrát vlastní pozadí", false);
        btnUploadBg.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnUploadBg.setMaximumSize(new Dimension(300, 40));
        btnUploadBg.addActionListener(e -> {
            chatPanel.setCustomBackground();
            this.dispose();
        });

        JButton btnRemoveBg = ModernTheme.createButton("Smazat pozadí", false);
        btnRemoveBg.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRemoveBg.setMaximumSize(new Dimension(300, 40));
        btnRemoveBg.setForeground(ModernTheme.DANGER);
        btnRemoveBg.addActionListener(e -> {
            chatPanel.removeCustomBackground();
            this.dispose();
        });

        content.add(btnUploadBg);
        content.add(Box.createVerticalStrut(10));
        content.add(btnRemoveBg);

        add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        footer.setOpaque(false);

        JButton saveBtn = ModernTheme.createButton("Uložit Změny", true);
        saveBtn.setBackground(ModernTheme.SUCCESS);

        saveBtn.addActionListener(e -> {
            ConfigManager.playSounds = soundCheck.isSelected();
            switch (colorBox.getSelectedIndex()) {
                case 0: ConfigManager.myBubbleColor = "#0084ff"; break;
                case 1: ConfigManager.myBubbleColor = "#2ecc71"; break;
                case 2: ConfigManager.myBubbleColor = "#9b59b6"; break;
                case 3: ConfigManager.myBubbleColor = "#e67e22"; break;
                case 4: ConfigManager.myBubbleColor = "#34495e"; break;
            }
            ConfigManager.save();
            chatPanel.repaint();
            dispose();
        });

        footer.add(saveBtn);
        add(footer, BorderLayout.SOUTH);
    }
}