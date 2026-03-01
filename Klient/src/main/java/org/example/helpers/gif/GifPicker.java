package org.example.helpers.gif;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.example.helpers.ChatPanel;
import org.example.helpers.GiphyAPI;

public class GifPicker extends JDialog {
    private final JTextField searchField;
    private final JPanel resultsPanel;
    private final ChatPanel chatPanel;
    private final ExecutorService executor;

    public GifPicker(ChatPanel parent) {
        super((Frame)SwingUtilities.getWindowAncestor(parent), "Vybrat GIF", true);
        this.chatPanel = parent;
        this.executor = Executors.newFixedThreadPool(4);
        this.setSize(600, 500);
        this.setLocationRelativeTo(parent);
        this.setLayout(new BorderLayout());
        JPanel topPanel = new JPanel(new BorderLayout());
        this.searchField = new JTextField();
        this.searchField.addActionListener((e) -> this.loadGifs(this.searchField.getText()));
        JButton searchBtn = new JButton("\ud83d\udd0d Hledat");
        searchBtn.addActionListener((e) -> this.loadGifs(this.searchField.getText()));
        topPanel.add(this.searchField, "Center");
        topPanel.add(searchBtn, "East");
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        this.add(topPanel, "North");
        this.resultsPanel = new JPanel(new GridLayout(0, 3, 5, 5));
        this.resultsPanel.setBackground(Color.WHITE);
        JScrollPane scroll = new JScrollPane(this.resultsPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        this.add(scroll, "Center");
        this.loadGifs("cat");
    }

    private void loadGifs(String query) {
        this.resultsPanel.removeAll();
        this.resultsPanel.repaint();
        JLabel loading = new JLabel("Načítám...", 0);
        this.resultsPanel.add(loading);
        this.resultsPanel.revalidate();
        (new Thread(() -> {
            List<String> urls = GiphyAPI.searchGifs(query);
            SwingUtilities.invokeLater(() -> {
                this.resultsPanel.removeAll();
                if (urls.isEmpty()) {
                    this.resultsPanel.add(new JLabel("Nic nenalezeno \ud83d\ude22"));
                } else {
                    for(String url : urls) {
                        this.addGifToPanel(url);
                    }
                }

                this.resultsPanel.revalidate();
                this.resultsPanel.repaint();
            });
        })).start();
    }

    private void addGifToPanel(final String urlStr) {
        JLabel label = new JLabel("...", 0);
        label.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        label.setCursor(new Cursor(12));
        label.setPreferredSize(new Dimension(150, 150));
        label.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                GifPicker.this.chatPanel.sendGifAsImage(urlStr);
                GifPicker.this.dispose();
            }
        });
        this.resultsPanel.add(label);
        this.executor.submit(() -> {
            try {
                URL url = new URL(urlStr);
                ImageIcon icon = new ImageIcon(url);
                Image img = icon.getImage().getScaledInstance(180, 140, 1);
                ImageIcon scaled = new ImageIcon(img);
                SwingUtilities.invokeLater(() -> {
                    label.setText("");
                    label.setIcon(scaled);
                });
            } catch (Exception var6) {
                SwingUtilities.invokeLater(() -> label.setText("Chyba"));
            }

        });
    }
}
