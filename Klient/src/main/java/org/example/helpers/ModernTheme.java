package org.example.helpers;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ModernTheme {
    public static final Color BG_BASE = new Color(7, 9, 15);
    public static final Color GLASS_BG = new Color(15, 18, 25, 160);
    public static final Color GLASS_BORDER = new Color(255, 255, 255, 30);

    public static final Color INPUT_BG = new Color(20, 24, 34);

    public static final Color NEON_CYAN = new Color(0, 243, 255);
    public static final Color NEON_PURPLE = new Color(188, 19, 254);

    public static final Color TEXT_MAIN = new Color(255, 255, 255);
    public static final Color TEXT_MUTED = new Color(107, 122, 144);
    public static final Color DANGER = new Color(255, 42, 85);
    public static final Color SUCCESS = new Color(0, 230, 118);

    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_PLAIN = new Font("Segoe UI", Font.PLAIN, 14);

    public static JButton createButton(String text, boolean primary) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        if (primary) {
            btn.setBackground(NEON_CYAN);
            btn.setForeground(Color.BLACK);
            btn.setBorderPainted(false);
            btn.setOpaque(true);
            btn.setBorder(new EmptyBorder(10, 20, 10, 20));
        } else {
            btn.setBackground(new Color(0, 0, 0, 0));
            btn.setForeground(TEXT_MAIN);
            btn.setContentAreaFilled(false);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    new RoundedBorder(10, GLASS_BORDER),
                    new EmptyBorder(8, 20, 8, 20)
            ));
        }

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!primary) {
                    btn.setForeground(NEON_CYAN);
                    btn.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(10, NEON_CYAN), new EmptyBorder(8, 20, 8, 20)));
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (!primary) {
                    btn.setForeground(TEXT_MAIN);
                    btn.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(10, GLASS_BORDER), new EmptyBorder(8, 20, 8, 20)));
                }
            }
        });
        return btn;
    }

    public static JButton createChatButton(String text, Color hoverColor) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setForeground(TEXT_MUTED);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(10, GLASS_BORDER),
                new EmptyBorder(8, 15, 8, 15)
        ));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setForeground(hoverColor);
                btn.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(10, hoverColor), new EmptyBorder(8, 15, 8, 15)));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setForeground(TEXT_MUTED);
                btn.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(10, GLASS_BORDER), new EmptyBorder(8, 15, 8, 15)));
            }
        });
        return btn;
    }

    public static void styleTextField(JTextField field) {
        field.setFont(FONT_PLAIN);
        field.setBackground(INPUT_BG);
        field.setForeground(TEXT_MAIN);
        field.setCaretColor(NEON_CYAN);
        field.setOpaque(true);
        field.setBorder(new EmptyBorder(5, 10, 5, 10));
    }

    public static class RoundedBorder implements Border {
        private final int radius; private final Color color;
        public RoundedBorder(int radius, Color color) { this.radius = radius; this.color = color; }
        public Insets getBorderInsets(Component c) { return new Insets(this.radius/2, this.radius/2, this.radius/2, this.radius/2); }
        public boolean isBorderOpaque() { return true; }
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color); g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius); g2.dispose();
        }
    }

    public static class ModernScrollBarUI extends BasicScrollBarUI {
        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(new Color(15, 18, 25));
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isDragging) {
                g2.setColor(new Color(107, 122, 144, 200));
            } else if (isThumbRollover()) {
                g2.setColor(new Color(107, 122, 144, 150));
            } else {
                g2.setColor(new Color(255, 255, 255, 30));
            }

            g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2, thumbBounds.width - 4, thumbBounds.height - 4, 10, 10);
            g2.dispose();
        }
    }
}