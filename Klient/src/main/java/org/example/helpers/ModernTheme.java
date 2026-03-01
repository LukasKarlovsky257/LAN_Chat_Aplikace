package org.example.helpers;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ModernTheme {

    // --- BARVY ---
    public static final Color BG_MAIN = new Color(240, 242, 245);  // Světle šedá (pozadí okna)
    public static final Color BG_DATA = Color.WHITE;               // Bílá (pozadí prvků)
    public static final Color PRIMARY = new Color(0, 132, 255);    // Modrá
    public static final Color TEXT_DARK = new Color(30, 30, 30);   // Tmavý text
    public static final Color TEXT_GRAY = new Color(100, 100, 100);// Šedý text
    public static final Color ERROR = new Color(220, 53, 69);      // Červená

    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_PLAIN = new Font("Segoe UI", Font.PLAIN, 14);

    // --- PŮVODNÍ TLAČÍTKA (Pro LoginPanel - s modrou barvou) ---
    public static JButton createButton(String text, boolean primary) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        if (primary) {
            btn.setBackground(PRIMARY);
            btn.setForeground(Color.WHITE);
            btn.setBorderPainted(false);
            btn.setOpaque(true);
            btn.setBorder(new EmptyBorder(10, 20, 10, 20));
        } else {
            btn.setBackground(Color.WHITE);
            btn.setForeground(TEXT_DARK);
            btn.setContentAreaFilled(false);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    new RoundedBorder(10, new Color(200, 200, 200)),
                    new EmptyBorder(8, 20, 8, 20)
            ));
        }
        return btn;
    }

    // --- NOVÁ TLAČÍTKA JEN PRO CHAT PANEL (Černobílá, minimalistická) ---
    public static JButton createChatButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);

        // Výchozí stav: Bílé pozadí, černý text
        btn.setBackground(Color.WHITE);
        btn.setForeground(TEXT_DARK);

        // Tmavý tenký rámeček pro jasné ohraničení
        btn.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(8, TEXT_DARK),
                new EmptyBorder(8, 20, 8, 20)
        ));

        // Hover efekt: Při najetí se barvy invertují (černé pozadí, bílý text)
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(TEXT_DARK);
                btn.setForeground(Color.WHITE);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(Color.WHITE);
                btn.setForeground(TEXT_DARK);
            }
        });

        return btn;
    }

    // --- STYL PRO TEXTOVÁ POLE ---
    public static void styleTextField(JTextField field) {
        field.setFont(FONT_PLAIN);
        field.setBackground(new Color(250, 250, 250));
        field.setCaretColor(PRIMARY);
        field.setBorder(new EmptyBorder(5, 10, 5, 10));
    }

    // --- KULATÝ RÁMEČEK ---
    public static class RoundedBorder implements Border {
        private final int radius;
        private final Color color;

        public RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }

        public Insets getBorderInsets(Component c) {
            return new Insets(this.radius/2, this.radius/2, this.radius/2, this.radius/2);
        }

        public boolean isBorderOpaque() {
            return true;
        }

        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }
    }
}