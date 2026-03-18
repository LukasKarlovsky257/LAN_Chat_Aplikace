package org.example.helpers.gif;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;
import org.example.helpers.ChatPanel;
import org.example.helpers.GiphyAPI;
import org.example.helpers.ModernTheme;

public class GifPicker extends JDialog {
    private final JTextField searchField;
    private final JPanel resultsPanel;
    private final ChatPanel chatPanel;
    private final ExecutorService executor;

    public GifPicker(ChatPanel parent) {
        super((Frame)SwingUtilities.getWindowAncestor(parent), "Databáze GIFů", true);
        this.chatPanel = parent;
        this.executor = Executors.newFixedThreadPool(4);
        this.setSize(600, 500);
        this.setLocationRelativeTo(parent);
        this.setLayout(new BorderLayout());
        this.getContentPane().setBackground(ModernTheme.BG_BASE);

        JPanel topPanel = new JPanel(new BorderLayout(10,0));
        topPanel.setOpaque(false);
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        this.searchField = new JTextField();
        ModernTheme.styleTextField(this.searchField);
        this.searchField.setBorder(BorderFactory.createCompoundBorder(
                new ModernTheme.RoundedBorder(10, ModernTheme.GLASS_BORDER),
                BorderFactory.createEmptyBorder(8,10,8,10)
        ));
        this.searchField.addActionListener((e) -> this.loadGifs(this.searchField.getText()));

        JButton searchBtn = ModernTheme.createButton("Hledat", true);
        searchBtn.setBackground(ModernTheme.NEON_PURPLE);
        searchBtn.addActionListener((e) -> this.loadGifs(this.searchField.getText()));

        topPanel.add(this.searchField, BorderLayout.CENTER);
        topPanel.add(searchBtn, BorderLayout.EAST);
        this.add(topPanel, BorderLayout.NORTH);

        this.resultsPanel = new JPanel(new GridLayout(0, 3, 10, 10));
        this.resultsPanel.setBackground(ModernTheme.BG_BASE);
        this.resultsPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JScrollPane scroll = new JScrollPane(this.resultsPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        this.add(scroll, BorderLayout.CENTER);

        this.loadGifs("cyberpunk");
    }

    private void loadGifs(String query) {
        this.resultsPanel.removeAll();
        this.resultsPanel.repaint();
        JLabel loading = new JLabel("Navazuji spojení s databází...", SwingConstants.CENTER);
        loading.setForeground(ModernTheme.TEXT_MUTED);
        this.resultsPanel.add(loading);
        this.resultsPanel.revalidate();

        (new Thread(() -> {
            List<String> urls = GiphyAPI.searchGifs(query);
            SwingUtilities.invokeLater(() -> {
                this.resultsPanel.removeAll();
                if (urls.isEmpty()) {
                    JLabel notFound = new JLabel("Žádná data nenalezena.", SwingConstants.CENTER);
                    notFound.setForeground(ModernTheme.DANGER);
                    this.resultsPanel.add(notFound);
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
        JLabel label = new JLabel("...", SwingConstants.CENTER);
        label.setForeground(ModernTheme.TEXT_MUTED);
        label.setBorder(new ModernTheme.RoundedBorder(5, ModernTheme.GLASS_BORDER));
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
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
                Image img = icon.getImage().getScaledInstance(170, 140, Image.SCALE_SMOOTH);
                ImageIcon scaled = new ImageIcon(img);
                SwingUtilities.invokeLater(() -> {
                    label.setText("");
                    label.setIcon(scaled);
                });
            } catch (Exception var6) {
                SwingUtilities.invokeLater(() -> label.setText("Chyba přenosu"));
            }
        });
    }
}