package org.example.helpers;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ModernDialog extends JDialog {

    private static final Color BG_COLOR = new Color(250, 250, 250);
    private static final Color TEXT_COLOR = new Color(30, 30, 30); // Tmavě černá pro čitelnost
    private static final Color PRIMARY_BTN = new Color(0, 132, 255);
    private static final Color SECONDARY_BTN = new Color(220, 220, 220);
    private static final Color DANGER_BTN = new Color(237, 66, 69);

    private Point initialClick;

    private ModernDialog(Window parent, String title) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        getRootPane().setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
        getContentPane().setBackground(BG_COLOR);
        setLayout(new BorderLayout());

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(240, 242, 245));
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));

        JLabel titleLabel = new JLabel("  " + title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setPreferredSize(new Dimension(200, 35));

        titleBar.add(titleLabel, BorderLayout.CENTER);

        titleBar.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { initialClick = e.getPoint(); }
        });
        titleBar.addMouseMotionListener(new MouseAdapter() {
            public void mouseDragged(MouseEvent e) {
                int thisX = getLocation().x;
                int thisY = getLocation().y;
                int xMoved = e.getX() - initialClick.x;
                int yMoved = e.getY() - initialClick.y;
                setLocation(thisX + xMoved, thisY + yMoved);
            }
        });

        add(titleBar, BorderLayout.NORTH);
    }

    private static JButton createButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(8, 15, 8, 15));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // 🔥 HOVER EFEKT: Černé pozadí, bílý text
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(Color.BLACK);
                btn.setForeground(Color.WHITE);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(bg);
                btn.setForeground(fg);
            }
        });

        return btn;
    }

    public static void showMessage(Window parent, String title, String message, boolean isError) {
        ModernDialog dialog = new ModernDialog(parent, title);
        dialog.setSize(350, 180);
        dialog.setLocationRelativeTo(parent);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Přebarvení HTML textu na černo
        String htmlColor = isError ? "#ed4245" : "#1e1e1e";
        JLabel msgLabel = new JLabel("<html><body style='width: 250px; text-align: center; color: " + htmlColor + ";'>" + message + "</body></html>", SwingConstants.CENTER);
        msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        content.add(msgLabel, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footer.setOpaque(false);
        JButton okBtn = createButton("Rozumím", isError ? DANGER_BTN : PRIMARY_BTN, Color.WHITE);
        okBtn.addActionListener(e -> dialog.dispose());
        footer.add(okBtn);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    public static String showInput(Window parent, String title, String prompt) {
        ModernDialog dialog = new ModernDialog(parent, title);
        dialog.setSize(350, 200);
        dialog.setLocationRelativeTo(parent);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(20, 20, 10, 20));

        JLabel msgLabel = new JLabel("<html><body style='width: 250px; color: #1e1e1e;'>" + prompt + "</body></html>");
        msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        content.add(msgLabel, BorderLayout.NORTH);

        JTextField inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setForeground(Color.BLACK); // Vynucený černý text
        inputField.setBackground(Color.WHITE); // Vynucené bílé pozadí
        inputField.setCaretColor(Color.BLACK); // Vynucený černý kurzor
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                new EmptyBorder(5, 10, 5, 10)
        ));
        content.add(inputField, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);

        final String[] result = {null};

        JButton cancelBtn = createButton("Zrušit", SECONDARY_BTN, Color.BLACK);
        cancelBtn.addActionListener(e -> dialog.dispose());

        JButton okBtn = createButton("Potvrdit", PRIMARY_BTN, Color.WHITE);
        okBtn.addActionListener(e -> {
            result[0] = inputField.getText();
            dialog.dispose();
        });

        inputField.addActionListener(e -> okBtn.doClick());

        footer.add(cancelBtn);
        footer.add(okBtn);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);

        SwingUtilities.invokeLater(inputField::requestFocusInWindow);

        dialog.setVisible(true);
        return result[0];
    }

    public static boolean showConfirm(Window parent, String title, String message) {
        ModernDialog dialog = new ModernDialog(parent, title);
        dialog.setSize(350, 180);
        dialog.setLocationRelativeTo(parent);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel msgLabel = new JLabel("<html><body style='width: 250px; text-align: center; color: #1e1e1e;'>" + message + "</body></html>", SwingConstants.CENTER);
        msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        content.add(msgLabel, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        footer.setOpaque(false);

        final boolean[] result = {false};

        JButton noBtn = createButton("Ne / Zrušit", SECONDARY_BTN, Color.BLACK);
        noBtn.addActionListener(e -> dialog.dispose());

        JButton yesBtn = createButton("Ano / Potvrdit", PRIMARY_BTN, Color.WHITE);
        yesBtn.addActionListener(e -> {
            result[0] = true;
            dialog.dispose();
        });

        footer.add(noBtn);
        footer.add(yesBtn);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);
        dialog.setVisible(true);

        return result[0];
    }
}