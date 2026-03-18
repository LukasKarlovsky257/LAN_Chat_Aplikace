package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.net.*;
import java.util.Enumeration;

public class ServerGUI extends Application {

    private boolean isRunning = false;
    private Button toggleButton;
    private Label statusLabel;
    private String mojeIpAdresa;

    @Override
    public void start(Stage stage) {
        this.mojeIpAdresa = ziskejLanIP();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0f172a; -fx-font-family: 'Segoe UI', sans-serif;"); // Moderní tmavé pozadí

        // --- HLAVIČKA ---
        Label titleLabel = new Label("🛡️ LAN CHAT SERVER");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 22));
        titleLabel.setTextFill(Color.web("#f8fafc"));
        HBox header = new HBox(titleLabel);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(20, 0, 10, 0));
        root.setTop(header);

        // --- STŘEDNÍ PANEL ---
        VBox centerPanel = new VBox(15);
        centerPanel.setAlignment(Pos.CENTER);
        centerPanel.setPadding(new Insets(10, 30, 20, 30));

        // Karta s IP adresou
        VBox ipCard = new VBox(10);
        ipCard.setAlignment(Pos.CENTER);
        ipCard.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 10; -fx-padding: 20; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 10, 0, 0, 5);");

        Label infoText = new Label("Lokální IPv4 Adresa Serveru");
        infoText.setTextFill(Color.web("#94a3b8"));
        infoText.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        Label ipLabel = new Label(mojeIpAdresa);
        ipLabel.setTextFill(Color.web("#38bdf8"));
        ipLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 28));

        Button copyBtn = new Button("📋 Kopírovat IP");
        copyBtn.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-background-radius: 5; -fx-cursor: hand;");
        copyBtn.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(mojeIpAdresa);
            Clipboard.getSystemClipboard().setContent(content);
            copyBtn.setText("✔️ Zkopírováno");
            Platform.runLater(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ex) {}
                Platform.runLater(() -> copyBtn.setText("📋 Kopírovat IP"));
            });
        });

        ipCard.getChildren().addAll(infoText, ipLabel, copyBtn);

        // Karta s odkazy
        VBox linksCard = new VBox(8);
        linksCard.setAlignment(Pos.CENTER);
        linksCard.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 10; -fx-padding: 15; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 10, 0, 0, 5);");

        Hyperlink chatLink = new Hyperlink("💬 Otevřít Webový Chat (Port 8080)");
        chatLink.setTextFill(Color.web("#a7f3d0"));
        chatLink.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        chatLink.setOnAction(e -> getHostServices().showDocument("http://localhost:8080"));

        Hyperlink adminLink = new Hyperlink("⚙️ Otevřít Admin Panel (Port 8888)");
        adminLink.setTextFill(Color.web("#fca5a5"));
        adminLink.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        adminLink.setOnAction(e -> getHostServices().showDocument("http://localhost:8888/admin"));

        linksCard.getChildren().addAll(chatLink, adminLink);

        centerPanel.getChildren().addAll(ipCard, linksCard);
        root.setCenter(centerPanel);

        // --- SPODNÍ PANEL ---
        VBox bottomPanel = new VBox(15);
        bottomPanel.setAlignment(Pos.CENTER);
        bottomPanel.setPadding(new Insets(10, 50, 30, 50));

        statusLabel = new Label("🔴 OFFLINE");
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 14));
        statusLabel.setTextFill(Color.web("#ef4444"));

        toggleButton = new Button("SPUSTIT SERVER");
        toggleButton.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 16));
        toggleButton.setPrefSize(250, 50);
        toggleButton.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand;");

        toggleButton.setOnAction(e -> toggleServer());

        bottomPanel.getChildren().addAll(statusLabel, toggleButton);
        root.setBottom(bottomPanel);

        Scene scene = new Scene(root, 450, 550);
        stage.setTitle("LAN Chat - Server Manager");
        stage.setScene(scene);
        stage.setResizable(false);

        stage.setOnCloseRequest(event -> {
            if (isRunning) Server.stopServer();
            Platform.exit();
            System.exit(0);
        });

        stage.show();
        toggleServer(); // Automatický start při spuštění aplikace
    }

    private void toggleServer() {
        if (!isRunning) {
            Server.startServer();
            isRunning = true;

            toggleButton.setText("VYPNOUT SERVER");
            toggleButton.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand;");

            statusLabel.setText("🟢 ONLINE BĚŽÍ (PORT 5555, 8080, 8888)");
            statusLabel.setTextFill(Color.web("#22c55e"));
        } else {
            Server.stopServer();
            isRunning = false;

            toggleButton.setText("SPUSTIT SERVER");
            toggleButton.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand;");

            statusLabel.setText("🔴 OFFLINE");
            statusLabel.setTextFill(Color.web("#ef4444"));
        }
    }

    private String ziskejLanIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                String nazev = iface.getDisplayName().toLowerCase();
                if (nazev.contains("virtual") || nazev.contains("vmware") ||
                        nazev.contains("docker") || nazev.contains("hyper-v") || nazev.contains("pseudo")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            return "Neznámá IP";
        }
        return "127.0.0.1";
    }

    public static void main(String[] args) {
        launch(args);
    }
}