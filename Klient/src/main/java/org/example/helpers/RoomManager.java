package org.example.helpers;

import org.example.Klient;

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

public class RoomManager extends JDialog {
    private DefaultListModel<String> listModel;
    private JList<String> roomList;
    private final Klient app;
    private Point initialClick;

    public RoomManager(Frame parent, Klient app) {
        super(parent, "Správa Místností", false);
        this.app = app;

        // Vizuál bez okrajů - Temný Cyberpunk motiv
        setUndecorated(true);
        getRootPane().setBorder(BorderFactory.createLineBorder(new Color(0, 243, 255, 100), 1));
        getContentPane().setBackground(new Color(15, 18, 25));
        setSize(400, 550);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // Custom Title Bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(10, 12, 18));
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 45, 55)));

        JLabel titleLabel = new JLabel("  Správa Místností");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(new Color(0, 243, 255));
        titleLabel.setPreferredSize(new Dimension(200, 35));

        JButton closeBarBtn = new JButton("X");
        closeBarBtn.setFocusPainted(false);
        closeBarBtn.setBorderPainted(false);
        closeBarBtn.setContentAreaFilled(false);
        closeBarBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        closeBarBtn.setForeground(new Color(255, 42, 85));
        closeBarBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
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

        // Custom Renderer pro zobrazení ikon do temného režimu
        this.roomList.setCellRenderer(new RoomListRenderer());
        this.roomList.setBackground(new Color(20, 24, 34));
        this.roomList.setSelectionMode(0);

        // Načtení místností ze serveru
        app.getNetwork().sendEncryptedMessage("/getrooms");

        JScrollPane scroll = new JScrollPane(this.roomList);
        scroll.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        scroll.getViewport().setBackground(new Color(15, 18, 25));

        this.add(scroll, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(4, 1, 5, 8)); // 4 řádky
        btnPanel.setOpaque(false);
        btnPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));

        // 🔥 FIX: Bezpečné textové znaky místo rozbitých emoji
        JButton btnJoin = createStyledButton("> Vstoupit do vybrané", new Color(0, 119, 255), Color.WHITE);
        JButton btnCreate = createStyledButton("+ Vytvořit nový kanál", new Color(0, 230, 118), Color.BLACK);
        JButton btnCreatePrivate = createStyledButton("* Založit soukromou místnost", new Color(255, 42, 85), Color.WHITE);
        JButton btnDelete = createStyledButton("x Smazat místnost (Admin)", new Color(40, 45, 55), new Color(200, 200, 200));

        btnDelete.setEnabled(app.isAdmin());

        btnJoin.addActionListener((e) -> {
            String selected = getCleanRoomName(this.roomList.getSelectedValue());
            if (selected != null) {
                this.switchRoom(selected);
            }
        });

        btnCreate.addActionListener((e) -> {
            String newName = ModernDialog.showInput(this, "Nová místnost", "Zadejte název nového kanálu:");
            if (newName != null && !newName.trim().isEmpty()) {
                app.getNetwork().sendEncryptedMessage("/createroom " + newName.trim());
                this.dispose();
            }
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

    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setBorder(new EmptyBorder(10, 15, 10, 15));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(bg.brighter());
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(bg);
                }
            }
        });
        return btn;
    }

    private void switchRoom(String roomName) {
        app.getNetwork().sendEncryptedMessage("/join " + roomName);
        this.dispose();
    }

    public void updateList(String[] rooms) {
        SwingUtilities.invokeLater(() -> {
            this.listModel.clear();
            for(String r : rooms) {
                this.listModel.addElement(r);
            }
        });
    }

    // Vizuální vykreslovač místností
    private static class RoomListRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel iconLabel;
        private final JLabel nameLabel;

        public RoomListRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(8, 10, 8, 10));
            setOpaque(true);

            iconLabel = new JLabel();
            // 🔥 FIX: Obyčejný font Segoe UI zaručí správné vykreslení znaků #, * a ~
            iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));

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

            // 🔥 FIX: Místo Emoji použity "Terminálové" znaky
            if (type.equals("2")) {
                iconLabel.setText("*"); // Soukromá (hvězdička)
                iconLabel.setForeground(new Color(255, 42, 85));
                nameLabel.setForeground(new Color(255, 42, 85));
            } else if (type.equals("1")) {
                iconLabel.setText("~"); // Dočasná (vlnovka)
                iconLabel.setForeground(new Color(250, 166, 26));
                nameLabel.setForeground(new Color(250, 166, 26));
            } else {
                iconLabel.setText("#"); // Klasická (hashtag, běžný pro channely)
                iconLabel.setForeground(new Color(0, 243, 255)); // Neon Cyan
                nameLabel.setForeground(new Color(230, 230, 230));
            }

            if (isSelected) {
                setBackground(new Color(0, 243, 255, 40));
            } else {
                setBackground(new Color(20, 24, 34));
            }

            return this;
        }
    }
}