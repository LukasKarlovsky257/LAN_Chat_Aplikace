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
        setSize(350, 250);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(240, 242, 245));

        JPanel header = new JPanel();
        header.setBackground(new Color(100, 100, 100));
        header.setBorder(new EmptyBorder(10, 10, 10, 10));
        JLabel title = new JLabel("Nastavení aplikace");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        header.add(title);
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        soundCheck = new JCheckBox("Přehrávat zvuk při nové zprávě");
        soundCheck.setOpaque(false);
        soundCheck.setSelected(ConfigManager.playSounds);
        soundCheck.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        soundCheck.setForeground(Color.BLACK); // 🔥 Vynucen černý text

        JLabel colorLabel = new JLabel("Barva tvých zpráv (Bublin):");
        colorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        colorLabel.setForeground(Color.BLACK); // 🔥 Vynucen černý text
        colorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[] colors = {"Modrá (Výchozí)", "Zelená", "Fialová", "Oranžová", "Tmavě Šedá"};
        colorBox = new JComboBox<>(colors);
        colorBox.setMaximumSize(new Dimension(300, 30));
        colorBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        colorBox.setForeground(Color.BLACK); // 🔥 Vynucen černý text
        colorBox.setBackground(Color.WHITE);

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

        add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);

        JButton saveBtn = new JButton("Uložit");
        saveBtn.setBackground(new Color(46, 204, 113));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false);
        saveBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // 🔥 HOVER EFEKT: Černé pozadí, bílý text
        Color origBg = saveBtn.getBackground();
        Color origFg = saveBtn.getForeground();
        saveBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                saveBtn.setBackground(Color.BLACK);
                saveBtn.setForeground(Color.WHITE);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                saveBtn.setBackground(origBg);
                saveBtn.setForeground(origFg);
            }
        });

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