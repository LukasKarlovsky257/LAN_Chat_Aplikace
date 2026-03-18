package org.example.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.example.Server;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class WebServer {
    private static final int PORT = 8080;
    private static HttpServer server;
    private static final String WEB_ROOT = "/WebClient";

    public static void start() {
        try {
            if (server != null) stop();
            server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // Zůstaly pouze veřejné cesty
            server.createContext("/", new StaticFileHandler());

            server.createContext("/avatars", exchange -> {
                String path = exchange.getRequestURI().getPath();
                File file = new File(System.getProperty("user.dir"), path);

                if (file.exists() && !file.isDirectory()) {
                    exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
                    exchange.sendResponseHeaders(200, file.length());
                    try (OutputStream os = exchange.getResponseBody();
                         FileInputStream fs = new FileInputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int count;
                        while ((count = fs.read(buffer)) != -1) {
                            os.write(buffer, 0, count);
                        }
                    }
                } else {
                    sendResponse(exchange, "404 - Avatar nenalezen", "text/plain", 404);
                }
            });

            server.setExecutor(Executors.newFixedThreadPool(50));
            server.start();

            Server.log("🌍 WEB SERVER BĚŽÍ: http://localhost:" + PORT + "/");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            Server.log("🌍 WebServer zastaven.");
        }
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                String uri = t.getRequestURI().getPath();
                if (uri.equals("/")) uri = "/index.html";

                if (uri.contains("..")) {
                    sendResponse(t, "Access Denied", "text/plain", 403);
                    return;
                }
                InputStream is = WebServer.class.getResourceAsStream(WEB_ROOT + uri);

                if (is != null) {
                    byte[] bytes = is.readAllBytes();
                    is.close();

                    String type = "text/plain";

                    if (uri.endsWith(".html")) {
                        type = "text/html";
                        String htmlContent = new String(bytes, StandardCharsets.UTF_8);

                        htmlContent = htmlContent.replace("{{DEFAULT_IP}}", "localhost:5555");
                        htmlContent = htmlContent.replace("{{APP_VERSION}}", "v1.5");

                        bytes = htmlContent.getBytes(StandardCharsets.UTF_8);
                    } else {
                        if (uri.endsWith(".css")) type = "text/css";
                        else if (uri.endsWith(".js")) type = "application/javascript";
                        else if (uri.endsWith(".png")) type = "image/png";
                        else if (uri.endsWith(".jpg") || uri.endsWith(".jpeg")) type = "image/jpeg";
                        else if (uri.endsWith(".mp3") || uri.endsWith(".wav")) type = "audio/mpeg";
                    }

                    sendResponse(t, bytes, type, 200);
                } else {
                    sendResponse(t, "404 - Soubor nenalezen", "text/plain", 404);
                }
            } catch (Throwable e) {
                e.printStackTrace();
                try { sendResponse(t, "500 - Chyba", "text/plain", 500); } catch (Exception ex) { t.close(); }
            }
        }
    }

    private static void sendResponse(HttpExchange t, String resp, String type, int code) throws IOException {
        byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        t.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = t.getResponseBody()) { os.write(bytes); }
    }

    private static void sendResponse(HttpExchange t, byte[] bytes, String type, int code) throws IOException {
        t.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        t.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = t.getResponseBody()) { os.write(bytes); }
    }
}