package org.example.helpers;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ModernDialog extends JDialog {

    private Point initialClick;

    private ModernDialog(Window parent, String title) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        getRootPane().setBorder(BorderFactory.createLineBorder(ModernTheme.GLASS_BORDER, 1));
        getContentPane().setBackground(ModernTheme.BG_BASE);
        setLayout(new BorderLayout());

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(15, 18, 25));
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.GLASS_BORDER));

        JLabel titleLabel = new JLabel("  " + title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(ModernTheme.NEON_CYAN);
        titleLabel.setPreferredSize(new Dimension(200, 35));

        titleBar.add(titleLabel, BorderLayout.CENTER);

        titleBar.addMouseListener(new MouseAdapter() { public void mousePressed(MouseEvent e) { initialClick = e.getPoint(); }});
        titleBar.addMouseMotionListener(new MouseAdapter() {
            public void mouseDragged(MouseEvent e) {
                setLocation(getLocation().x + e.getX() - initialClick.x, getLocation().y + e.getY() - initialClick.y);
            }
        });

        add(titleBar, BorderLayout.NORTH);
    }

    public static void showMessage(Window parent, String title, String message, boolean isError) {
        ModernDialog dialog = new ModernDialog(parent, title);
        dialog.setSize(350, 180); dialog.setLocationRelativeTo(parent);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false); content.setBorder(new EmptyBorder(20, 20, 20, 20));

        String htmlColor = isError ? "#ff2a55" : "#ffffff";
        JLabel msgLabel = new JLabel("<html><body style='width: 250px; text-align: center; color: " + htmlColor + ";'>" + message + "</body></html>", SwingConstants.CENTER);
        msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        content.add(msgLabel, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER)); footer.setOpaque(false);
        JButton okBtn = ModernTheme.createButton("Rozumím", true);
        if (isError) okBtn.setBackground(ModernTheme.DANGER);
        okBtn.addActionListener(e -> dialog.dispose());
        footer.add(okBtn);

        dialog.add(content, BorderLayout.CENTER); dialog.add(footer, BorderLayout.SOUTH); dialog.setVisible(true);
    }

    public static String showInput(Window parent, String title, String prompt) {
        ModernDialog dialog = new ModernDialog(parent, title);
        dialog.setSize(350, 200); dialog.setLocationRelativeTo(parent);

        JPanel content = new JPanel(new BorderLayout(0, 10)); content.setOpaque(false); content.setBorder(new EmptyBorder(20, 20, 10, 20));

        JLabel msgLabel = new JLabel("<html><body style='width: 250px; color: #ffffff;'>" + prompt + "</body></html>");
        msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14)); content.add(msgLabel, BorderLayout.NORTH);

        JTextField inputField = new JTextField();
        ModernTheme.styleTextField(inputField);
        inputField.setBorder(BorderFactory.createCompoundBorder(new ModernTheme.RoundedBorder(10, ModernTheme.GLASS_BORDER), new EmptyBorder(8, 10, 8, 10)));
        content.add(inputField, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); footer.setOpaque(false);
        final String[] result = {null};

        JButton cancelBtn = ModernTheme.createButton("Zrušit", false); cancelBtn.addActionListener(e -> dialog.dispose());
        JButton okBtn = ModernTheme.createButton("Potvrdit", true);
        okBtn.addActionListener(e -> { result[0] = inputField.getText(); dialog.dispose(); });

        inputField.addActionListener(e -> okBtn.doClick());
        footer.add(cancelBtn); footer.add(okBtn);
        dialog.add(content, BorderLayout.CENTER); dialog.add(footer, BorderLayout.SOUTH);
        SwingUtilities.invokeLater(inputField::requestFocusInWindow); dialog.setVisible(true);

        return result[0];
    }

    public static boolean showConfirm(Window parent, String title, String message) {
        ModernDialog dialog = new ModernDialog(parent, title);
        dialog.setSize(350, 180); dialog.setLocationRelativeTo(parent);

        JPanel content = new JPanel(new BorderLayout()); content.setOpaque(false); content.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel msgLabel = new JLabel("<html><body style='width: 250px; text-align: center; color: #ffffff;'>" + message + "</body></html>", SwingConstants.CENTER);
        msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14)); content.add(msgLabel, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10)); footer.setOpaque(false);
        final boolean[] result = {false};

        JButton noBtn = ModernTheme.createButton("Zrušit", false); noBtn.addActionListener(e -> dialog.dispose());
        JButton yesBtn = ModernTheme.createButton("Potvrdit", true); yesBtn.setBackground(ModernTheme.DANGER);
        yesBtn.addActionListener(e -> { result[0] = true; dialog.dispose(); });

        footer.add(noBtn); footer.add(yesBtn);
        dialog.add(content, BorderLayout.CENTER); dialog.add(footer, BorderLayout.SOUTH); dialog.setVisible(true);

        return result[0];
    }
}