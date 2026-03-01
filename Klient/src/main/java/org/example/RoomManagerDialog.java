package org.example;

import org.example.helpers.ModernDialog;

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
import java.io.PrintWriter;
import java.security.PublicKey;
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
import javax.swing.plaf.basic.BasicButtonUI;

public class RoomManagerDialog extends JDialog {
    private DefaultListModel<String> listModel;
    private JList<String> roomList;
    private PrintWriter out;
    private PublicKey serverKey;
    private boolean iAmAdmin;
    private Point initialClick;

    public RoomManagerDialog(Frame parent, PrintWriter out, PublicKey serverKey, boolean iAmAdmin) {
        super(parent, "Správa Místností", false);
        this.out = out;
        this.serverKey = serverKey;
        this.iAmAdmin = iAmAdmin;

        setUndecorated(true);
        getRootPane().setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
        getContentPane().setBackground(new Color(250, 250, 250));
        setSize(400, 550);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(240, 242, 245));
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));
        JLabel titleLabel = new JLabel("  Správa Místností");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(Color.BLACK);
        titleLabel.setPreferredSize(new Dimension(200, 35));

        JButton closeBarBtn = new JButton("X");
        closeBarBtn.setUI(new BasicButtonUI()); // Vynutí čistý vzhled i u křížku
        closeBarBtn.setFocusPainted(false);
        closeBarBtn.setBorderPainted(false);
        closeBarBtn.setContentAreaFilled(false);
        closeBarBtn.setOpaque(false);
        closeBarBtn.setForeground(Color.BLACK);
        closeBarBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        closeBarBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        closeBarBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { closeBarBtn.setForeground(Color.RED); }
            @Override
            public void mouseExited(MouseEvent e) { closeBarBtn.setForeground(Color.BLACK); }
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
        this.roomList.setBackground(Color.WHITE);
        this.roomList.setSelectionMode(0);
        this.sendEncryptedCommand("/getrooms");

        JScrollPane scroll = new JScrollPane(this.roomList);
        scroll.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        this.add(scroll, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(4, 1, 5, 8));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));

        // Tlačítka pro UI (bez barevných emotikonů kvůli prázdným čtvercům na Windows)
        JButton btnJoin = createStyledButton("Vstoupit do vybrané", new Color(0, 132, 255), Color.WHITE);
        JButton btnCreate = createStyledButton("Vytvořit nový kanál", new Color(46, 204, 113), Color.WHITE);
        JButton btnCreatePrivate = createStyledButton("Založit soukromou místnost", new Color(237, 66, 69), Color.WHITE);
        JButton btnDelete = createStyledButton("Smazat místnost (Admin)", new Color(200, 200, 200), Color.BLACK);

        btnDelete.setEnabled(this.iAmAdmin);

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
                this.sendEncryptedCommand("/createprivate " + newName.trim());
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
                    this.sendEncryptedCommand("/deleteroom " + selected);
                    try { Thread.sleep(200L); this.sendEncryptedCommand("/getrooms"); } catch (Exception ignored) {}
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

        // 🔥 MAGICKÝ ŘÁDEK PROTI BÍLÝM TLAČÍTKŮM NA WINDOWS 🔥
        btn.setUI(new BasicButtonUI());

        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        // 🔥 TOHLE OPRAVÍ TY BÍLÉ TLAČÍTKA NA WINDOWS:
        btn.setContentAreaFilled(false);
        btn.setOpaque(true);

        btn.setBorder(new EmptyBorder(10, 15, 10, 15));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(Color.BLACK);
                    btn.setForeground(Color.WHITE);
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setBackground(bg);
                    btn.setForeground(fg);
                }
            }
        });
        return btn;
    }

    private void switchRoom(String roomName) {
        this.sendEncryptedCommand("/join " + roomName);
        this.dispose();
    }

    private void sendEncryptedCommand(String cmd) {
        try {
            if (this.serverKey != null && this.out != null) {
                this.out.println(CryptoUtils.encrypt(cmd, this.serverKey));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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

            // Místo emoji používáme bezpečné znaky pro Windows
            if (type.equals("2")) {
                iconLabel.setText("*"); // Soukromá
                iconLabel.setForeground(new Color(237, 66, 69));
                nameLabel.setForeground(new Color(237, 66, 69));
            } else if (type.equals("1")) {
                iconLabel.setText("~"); // Dočasná
                iconLabel.setForeground(new Color(250, 166, 26));
                nameLabel.setForeground(new Color(250, 166, 26));
            } else {
                iconLabel.setText("#"); // Klasická
                iconLabel.setForeground(Color.GRAY);
                nameLabel.setForeground(Color.BLACK);
            }

            if (isSelected) setBackground(new Color(230, 240, 255));
            else setBackground(Color.WHITE);

            return this;
        }
    }
}