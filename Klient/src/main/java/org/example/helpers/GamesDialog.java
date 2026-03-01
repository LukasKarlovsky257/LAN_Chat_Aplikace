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

        setSize(400, 400);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(240, 242, 245));

        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(0, 132, 255));
        headerPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        JLabel headerLabel = new JLabel("Vyber si hru / interakci");
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        headerLabel.setForeground(Color.WHITE); // Bílá v modré hlavičce je v pořádku
        headerPanel.add(headerLabel);
        add(headerPanel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        contentPanel.add(createGameCard(
                "Piškvorky (Tic Tac Toe)",
                "Vyzvi někoho z místnosti na souboj v piškvorkách!",
                () -> startTicTacToe()
        ));

        contentPanel.add(Box.createVerticalStrut(15));

        contentPanel.add(createGameCard(
                "Sdílené Plátno (Whiteboard)",
                "Kresli s někým společně, nebo vytvoř volné plátno!",
                () -> startWhiteboard()
        ));

        contentPanel.add(Box.createVerticalStrut(15));

        if (app.isAdmin()) {
            contentPanel.add(createGameCard(
                    "Matematický Kvíz",
                    "Spustit nový matematický příklad pro všechny v chatu.",
                    () -> startMath()
            ));
        } else {
            JLabel info = new JLabel("Matematický kvíz se spouští automaticky.");
            info.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            info.setForeground(Color.BLACK); // 🔥 Vynucen černý text
            info.setAlignmentX(Component.CENTER_ALIGNMENT);
            contentPanel.add(info);
        }

        add(contentPanel, BorderLayout.CENTER);
    }

    private JPanel createGameCard(String title, String desc, Runnable onClick) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                new EmptyBorder(10, 15, 10, 15)
        ));
        card.setMaximumSize(new Dimension(350, 80));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLbl.setForeground(Color.BLACK); // 🔥 Vynucen černý text

        JLabel descLbl = new JLabel("<html><body style='width: 200px; color: #000000;'>" + desc + "</body></html>");
        descLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        textPanel.add(titleLbl);
        textPanel.add(Box.createVerticalStrut(5));
        textPanel.add(descLbl);

        JButton playBtn = new JButton("Spustit");
        playBtn.setBackground(new Color(46, 204, 113));
        playBtn.setForeground(Color.WHITE);
        playBtn.setFocusPainted(false);
        playBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // 🔥 HOVER EFEKT
        Color origBg = playBtn.getBackground();
        Color origFg = playBtn.getForeground();
        playBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                playBtn.setBackground(Color.BLACK);
                playBtn.setForeground(Color.WHITE);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                playBtn.setBackground(origBg);
                playBtn.setForeground(origFg);
            }
        });

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
        String opponent = ModernDialog.showInput(parentWindow, "Whiteboard", "Zadej jméno hráče pro sdílené kreslení<br>(Nebo nech prázdné pro volné plátno místnosti):");

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