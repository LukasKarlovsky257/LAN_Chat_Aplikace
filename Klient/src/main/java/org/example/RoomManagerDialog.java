package org.example;

import org.example.Klient;
import org.example.helpers.ModernDialog;
import org.example.helpers.ModernTheme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public class RoomManagerDialog extends JDialog {
    private DefaultListModel<String> listModel;
    private JList<String> roomList;
    private final Klient app;
    private Point initialClick;

    public RoomManagerDialog(Frame parent, Klient app) {
        super(parent, "Správa Místností", false);
        this.app = app;

        setUndecorated(true);
        getRootPane().setBorder(BorderFactory.createLineBorder(ModernTheme.GLASS_BORDER, 1));
        getContentPane().setBackground(ModernTheme.BG_BASE);
        setSize(400, 550);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // Custom Title Bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(15, 18, 25));
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ModernTheme.GLASS_BORDER));
        JLabel titleLabel = new JLabel("  Sítě & Komunikace");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(ModernTheme.NEON_CYAN);
        titleLabel.setPreferredSize(new Dimension(200, 35));

        JButton closeBarBtn = new JButton("X");
        closeBarBtn.setFocusPainted(false);
        closeBarBtn.setBorderPainted(false);
        closeBarBtn.setContentAreaFilled(false);
        closeBarBtn.setForeground(ModernTheme.TEXT_MUTED);
        closeBarBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        closeBarBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { closeBarBtn.setForeground(ModernTheme.DANGER); }
            public void mouseExited(MouseEvent e) { closeBarBtn.setForeground(ModernTheme.TEXT_MUTED); }
        });

        closeBarBtn.addActionListener(e -> dispose());

        titleBar.add(titleLabel, BorderLayout.CENTER);
        titleBar.add(closeBarBtn, BorderLayout.EAST);

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

        this.listModel = new DefaultListModel<>();
        this.roomList = new JList<>(this.listModel);

        this.roomList.setCellRenderer(new RoomListRenderer());
        this.roomList.setBackground(ModernTheme.INPUT_BG);
        this.roomList.setForeground(ModernTheme.TEXT_MAIN);
        this.roomList.setSelectionBackground(ModernTheme.NEON_CYAN);
        this.roomList.setSelectionForeground(Color.BLACK);
        this.roomList.setSelectionMode(0);

        app.getNetwork().sendEncryptedMessage("/getrooms");

        JScrollPane scroll = new JScrollPane(this.roomList);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(15, 15, 5, 15),
                new ModernTheme.RoundedBorder(10, ModernTheme.GLASS_BORDER)
        ));
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        this.add(scroll, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(4, 1, 5, 8));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 20, 20));

        JButton btnJoin = ModernTheme.createButton("Vstoupit do vybrané", true);
        JButton btnCreate = ModernTheme.createButton("➕ Vytvořit nový kanál", true);
        btnCreate.setBackground(ModernTheme.SUCCESS);

        JButton btnCreatePrivate = ModernTheme.createButton("🔒 Založit soukromou místnost", true);
        btnCreatePrivate.setBackground(ModernTheme.NEON_PURPLE);

        JButton btnDelete = ModernTheme.createButton("🗑️ Smazat místnost (Admin)", false);
        btnDelete.setForeground(ModernTheme.DANGER);

        btnDelete.setEnabled(app.isAdmin());

        btnJoin.addActionListener((e) -> {
            String selected = getCleanRoomName(this.roomList.getSelectedValue());
            if (selected != null) this.switchRoom(selected);
        });

        btnCreate.addActionListener((e) -> {
            String newName = ModernDialog.showInput(this, "Nová místnost", "Zadejte název nového kanálu:");
            if (newName != null && !newName.trim().isEmpty()) this.switchRoom(newName.trim());
        });

        btnCreatePrivate.addActionListener((e) -> {
            String newName = ModernDialog.showInput(this, "Soukromá místnost", "Zadejte název soukromé místnosti:");
            if (newName != null && !newName.trim().isEmpty()) {
                app.getNetwork().sendEncryptedMessage("/createprivate " + newName.trim());
                this.dispose();
            }
        });

        btnDelete.addActionListener((e) -> {
            String selected = getCleanRoomName(this.roomList.getSelectedValue());
            if (selected != null) {
                if (selected.equals("Lobby")) {
                    ModernDialog.showMessage(this, "Chyba", "Hlavní Lobby nelze smazat!", true);
                    return;
                }
                if (ModernDialog.showConfirm(this, "Varování", "Opravdu chcete smazat místnost <b>" + selected + "</b>?")) {
                    app.getNetwork().sendEncryptedMessage("/deleteroom " + selected);
                    try {
                        Thread.sleep(200L);
                        app.getNetwork().sendEncryptedMessage("/getrooms");
                    } catch (Exception ignored) {}
                }
            }
        });

        btnPanel.add(btnJoin);
        btnPanel.add(btnCreate);
        btnPanel.add(btnCreatePrivate);
        btnPanel.add(btnDelete);
        this.add(btnPanel, BorderLayout.SOUTH);
    }

    private String getCleanRoomName(String rawItem) {
        if (rawItem == null) return null;
        return rawItem.split("\\|")[0];
    }

    private void switchRoom(String roomName) {
        app.getNetwork().sendEncryptedMessage("/join " + roomName);
        this.dispose();
    }

    public void updateList(String[] rooms) {
        SwingUtilities.invokeLater(() -> {
            this.listModel.clear();
            for(String r : rooms) this.listModel.addElement(r);
        });
    }

    private static class RoomListRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel iconLabel;
        private final JLabel nameLabel;

        public RoomListRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(8, 10, 8, 10));
            setOpaque(true);

            iconLabel = new JLabel();
            iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));

            nameLabel = new JLabel();
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

            add(iconLabel, BorderLayout.WEST);
            add(nameLabel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            String[] parts = value.split("\\|");
            String name = parts[0];
            String type = parts.length > 1 ? parts[1] : "0";

            nameLabel.setText(name);

            if (type.equals("2")) {
                iconLabel.setText("🔒");
                nameLabel.setForeground(ModernTheme.DANGER);
            } else if (type.equals("1")) {
                iconLabel.setText("⏱️");
                nameLabel.setForeground(new Color(250, 166, 26)); // Oranžová
            } else {
                iconLabel.setText("💬");
                nameLabel.setForeground(ModernTheme.TEXT_MAIN);
            }

            if (isSelected) {
                setBackground(new Color(255, 255, 255, 20)); // Skleněný výběr
            } else {
                setBackground(new Color(0, 0, 0, 0)); // Průhledná pro ostatní
            }

            return this;
        }
    }
}