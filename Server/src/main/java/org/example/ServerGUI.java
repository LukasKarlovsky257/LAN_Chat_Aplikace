package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
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
        root.setStyle("-fx-background-color: #1e1e1e;");

        VBox centerPanel = new VBox(10);
        centerPanel.setAlignment(Pos.CENTER);
        centerPanel.setPadding(new Insets(30, 20, 20, 20));

        Label infoText = new Label("Adresa pro připojení (Web & Chat):");
        infoText.setTextFill(Color.WHITE);
        infoText.setFont(Font.font("Segoe UI", 16));

        String odkazText = "http://" + mojeIpAdresa + ":8888/admin";
        Hyperlink link = new Hyperlink(odkazText);
        link.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        link.setTextFill(Color.web("#4db8ff"));
        link.setBorder(null);

        link.setOnAction(e -> getHostServices().showDocument("http://localhost:8888/admin"));

        Label subText = new Label("Tuto IP adresu zadej do klienta\nnebo webového prohlížeče.");
        subText.setTextFill(Color.GRAY);
        subText.setFont(Font.font("Segoe UI", 12));
        subText.setStyle("-fx-text-alignment: center;");

        centerPanel.getChildren().addAll(infoText, link, subText);
        root.setCenter(centerPanel);

        VBox bottomPanel = new VBox(10);
        bottomPanel.setAlignment(Pos.CENTER);
        bottomPanel.setPadding(new Insets(10, 50, 25, 50));

        statusLabel = new Label("🔴 Zastaveno");
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        statusLabel.setTextFill(Color.RED);

        toggleButton = new Button("Spustit Server");
        toggleButton.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        toggleButton.setPrefSize(200, 45);
        toggleButton.setCursor(javafx.scene.Cursor.HAND);

        toggleButton.setOnAction(e -> toggleServer());

        bottomPanel.getChildren().addAll(statusLabel, toggleButton);
        root.setBottom(bottomPanel);

        Scene scene = new Scene(root, 400, 320);
        stage.setTitle("Server Control Panel");
        stage.setScene(scene);
        stage.setResizable(false);

        stage.setOnCloseRequest(event -> {
            if (isRunning) Server.stopServer();
            Platform.exit();
            System.exit(0);
        });

        stage.show();

        toggleServer();
    }

    private void toggleServer() {
        if (!isRunning) {
            Server.startServer();
            isRunning = true;

            toggleButton.setText("Vypnout Server");
            toggleButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-background-radius: 5;");

            statusLabel.setText("🟢 Běží na " + mojeIpAdresa + ":5555");
            statusLabel.setTextFill(Color.LIGHTGREEN);
        } else {
            Server.stopServer();
            isRunning = false;

            toggleButton.setText("Zapnout Server");
            toggleButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-background-radius: 5;");

            statusLabel.setText("🔴 Zastaveno");
            statusLabel.setTextFill(Color.RED);
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