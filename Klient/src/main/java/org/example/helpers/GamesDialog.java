package org.example.helpers;

import org.example.Klient;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class GamesDialog extends JDialog {

    private final Klient app;
    private final String currentRoom;

    public GamesDialog(Frame parent, Klient app, String currentRoom) {
        super(parent, "Herní Centrum", true);
        this.app = app;
        this.currentRoom = currentRoom;

        setSize(420, 450);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(ModernTheme.BG_BASE);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(15, 18, 25));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.GLASS_BORDER),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JLabel headerLabel = new JLabel("Akce & Zábava", SwingConstants.CENTER);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        headerLabel.setForeground(ModernTheme.NEON_PURPLE);
        headerPanel.add(headerLabel, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        contentPanel.add(createGameCard(
                "Piškvorky (Tic Tac Toe)",
                "Vyzvi někoho z místnosti na souboj!",
                () -> startTicTacToe()
        ));

        contentPanel.add(Box.createVerticalStrut(15));

        contentPanel.add(createGameCard(
                "Sdílené Plátno",
                "Kresli s někým, nebo vytvoř volné plátno.",
                () -> startWhiteboard()
        ));

        contentPanel.add(Box.createVerticalStrut(15));

        if (app.isAdmin()) {
            contentPanel.add(createGameCard(
                    "Spustit Kvíz",
                    "Aktivuj matematický úkol pro všechny v chatu.",
                    () -> startMath()
            ));
        } else {
            JLabel info = new JLabel("Matematický kvíz se spouští automaticky serverem.");
            info.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            info.setForeground(ModernTheme.TEXT_MUTED);
            info.setAlignmentX(Component.CENTER_ALIGNMENT);
            contentPanel.add(info);
        }

        add(contentPanel, BorderLayout.CENTER);
    }

    // Nyní to využívá custom paintComponent pro odstranění ghostingu
    private JPanel createGameCard(String title, String desc, Runnable onClick) {
        JPanel card = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ModernTheme.GLASS_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.setColor(ModernTheme.GLASS_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(15, 15, 15, 15));
        card.setMaximumSize(new Dimension(380, 90));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLbl.setForeground(ModernTheme.TEXT_MAIN);

        JLabel descLbl = new JLabel("<html><body style='width: 180px; color: #6b7a90;'>" + desc + "</body></html>");
        descLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        textPanel.add(titleLbl);
        textPanel.add(Box.createVerticalStrut(5));
        textPanel.add(descLbl);

        JButton playBtn = ModernTheme.createButton("Aktivovat", true);
        playBtn.setBackground(ModernTheme.NEON_PURPLE);
        playBtn.addActionListener(e -> onClick.run());

        card.add(textPanel, BorderLayout.CENTER);
        card.add(playBtn, BorderLayout.EAST);

        return card;
    }

    private void startTicTacToe() {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        String opponent = ModernDialog.showInput(parentWindow, "Piškvorky", "Zadej jméno hráče, kterého chceš vyzvat:");

        if (opponent != null) {
            if (opponent.trim().isEmpty()) {
                ModernDialog.showMessage(parentWindow, "Chyba", "Musíš zadat jméno soupeře!", true);
                return;
            }
            if (opponent.trim().equalsIgnoreCase(app.getMyNick())) {
                ModernDialog.showMessage(parentWindow, "Chyba", "Nemůžeš vyzvat sám sebe!", true);
                return;
            }
            app.getNetwork().sendEncryptedMessage("/ttt start " + opponent.trim());
            dispose();
        }
    }

    private void startWhiteboard() {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        String opponent = ModernDialog.showInput(parentWindow, "Whiteboard", "Zadej jméno hráče (nech prázdné pro veřejné plátno):");

        if (opponent != null) {
            if (opponent.trim().isEmpty()) {
                app.getNetwork().sendEncryptedMessage("/wb room");
            } else {
                if (opponent.trim().equalsIgnoreCase(app.getMyNick())) {
                    ModernDialog.showMessage(parentWindow, "Chyba", "Pro volné plátno nech pole prázdné!", true);
                    return;
                }
                app.getNetwork().sendEncryptedMessage("/wb start " + opponent.trim());
            }
            dispose();
        }
    }

    private void startMath() {
        app.getNetwork().sendEncryptedMessage("/math");
        dispose();
    }
}