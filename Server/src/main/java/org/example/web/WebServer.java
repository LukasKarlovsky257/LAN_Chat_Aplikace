package org.example.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.example.Server;
import org.example.managers.DatabaseManager;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Executors;

public class WebServer {
    private static final int PORT = 8080;
    private static HttpServer server;

    // Cesta ke složce s WebClientem
    private static final String WEB_ROOT = "WebClient";

    public static void start() {
        try {
            if (server != null) stop();
            server = HttpServer.create(new InetSocketAddress(PORT), 0);

            server.createContext("/", new StaticFileHandler());
            server.createContext("/admin", new DashboardHandler());
            server.createContext("/api/data", new ApiDataHandler());
            server.createContext("/action", new ActionHandler());
            server.createContext("/download-logs", new LogDownloadHandler());

            // Zvýšili jsme počet vláken na 50, aby se server jen tak nezahltil
            server.setExecutor(Executors.newFixedThreadPool(50));
            server.start();

            Server.log("🌍 WEB SERVER BĚŽÍ:");
            Server.log("   --> Chat Klient: http://localhost:" + PORT + "/");
            Server.log("   --> Admin Panel: http://localhost:" + PORT + "/admin");

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

    // ==========================================
    // 1. HANDLER PRO STATICKÉ SOUBORY
    // ==========================================
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                String uri = t.getRequestURI().getPath();
                if (uri.equals("/")) uri = "/index.html";
                System.out.println("🌐 [WEB] Požadavek na soubor: " + uri); // Diagnostika

                if (uri.contains("..")) {
                    sendResponse(t, "Access Denied", "text/plain", 403);
                    return;
                }

                File file = new File(WEB_ROOT + uri);

                if (file.exists() && !file.isDirectory()) {
                    byte[] bytes;
                    String type = "text/plain";

                    if (uri.endsWith(".html")) {
                        type = "text/html";
                        byte[] fileBytes = Files.readAllBytes(file.toPath());
                        String htmlContent = new String(fileBytes, StandardCharsets.UTF_8);

                        htmlContent = htmlContent.replace("{{DEFAULT_IP}}", "localhost:5555");
                        htmlContent = htmlContent.replace("{{APP_VERSION}}", "v1.5");

                        bytes = htmlContent.getBytes(StandardCharsets.UTF_8);
                    } else {
                        bytes = Files.readAllBytes(file.toPath());
                        if (uri.endsWith(".css")) type = "text/css";
                        else if (uri.endsWith(".js")) type = "application/javascript";
                        else if (uri.endsWith(".png")) type = "image/png";
                        else if (uri.endsWith(".jpg")) type = "image/jpeg";
                    }

                    sendResponse(t, bytes, type, 200);
                } else {
                    System.err.println("⚠️ [WEB] Soubor nenalezen: " + file.getAbsolutePath());
                    sendResponse(t, "404 - Soubor nenalezen", "text/plain", 404);
                }
            } catch (Throwable e) {
                System.err.println("❌ CHYBA VE STATIC HANDLERU:");
                e.printStackTrace();
                t.close();
            }
        }
    }

    // ==========================================
    // 2. HANDLER PRO ADMIN DASHBOARD
    // ==========================================
    static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                System.out.println("⚙️ [ADMIN] Požadavek na /admin"); // Diagnostika
                File file = new File(WEB_ROOT + "/admin.html");

                if (file.exists() && !file.isDirectory()) {
                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                    String htmlTemplate = new String(fileBytes, StandardCharsets.UTF_8);
                    String finalHtml = htmlTemplate.replace("{{START_TIME}}", String.valueOf(Server.startTime));
                    sendResponse(t, finalHtml, "text/html", 200);
                    System.out.println("✅ [ADMIN] Panel úspěšně odeslán prohlížeči.");
                } else {
                    System.err.println("⚠️ [ADMIN] Soubor admin.html neexistuje ve složce " + WEB_ROOT);
                    sendResponse(t, "404 - Soubor admin.html nenalezen.", "text/plain", 404);
                }
            } catch (Throwable e) {
                System.err.println("❌ CHYBA V DASHBOARD HANDLERU:");
                e.printStackTrace();
                try { sendResponse(t, "500 - Interni chyba serveru", "text/plain", 500); } catch (Exception ex) { t.close(); }
            }
        }
    }

    // ==========================================
    // 3. API DATA HANDLER
    // ==========================================
    static class ApiDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                StringBuilder json = new StringBuilder("{");
                json.append("\"online\":").append(Server.getOnlineCount()).append(",");
                json.append("\"logCount\":").append(Server.serverLogs.size()).append(",");

                json.append("\"logs\":[");
                List<String> logs = new ArrayList<>(Server.serverLogs);
                int start = Math.max(0, logs.size() - 50);
                for (int i = start; i < logs.size(); i++) {
                    json.append("\"").append(escapeJson(logs.get(i))).append("\"");
                    if (i < logs.size() - 1) json.append(",");
                }
                json.append("],");

                json.append("\"users\":[");
                String[] users = Server.getUserList();
                for (int i = 0; i < users.length; i++) {
                    json.append("\"").append(escapeJson(users[i])).append("\"");
                    if (i < users.length - 1) json.append(",");
                }
                json.append("],");

                json.append("\"banned\":[");
                List<String[]> bannedRaw = DatabaseManager.getBannedList(); // Tady se mohl zamykat server
                List<String> validBansJson = new ArrayList<>();
                long now = System.currentTimeMillis();

                for (String[] b : bannedRaw) {
                    String username = b[0];
                    String admin = b[1];
                    String reason = b[2];
                    String untilStr = (b.length > 3) ? b[3] : "-1";
                    long until = -1;
                    try { until = Long.parseLong(untilStr); } catch (Exception e) {}

                    if (until != -1 && until != 0 && now > until) {
                        Server.unbanUser(username);
                        continue;
                    }
                    validBansJson.add(String.format("{\"user\":\"%s\",\"admin\":\"%s\",\"reason\":\"%s\",\"until\":%s}",
                            escapeJson(username), escapeJson(admin), escapeJson(reason), untilStr));
                }
                json.append(String.join(",", validBansJson));
                json.append("]}");

                sendResponse(t, json.toString(), "application/json", 200);
            } catch (Throwable e) {
                System.err.println("❌ CHYBA V API (/api/data):");
                e.printStackTrace();
                t.close();
            }
        }
    }

    // ==========================================
    // 4. ACTION HANDLER
    // ==========================================
    static class ActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                if ("POST".equalsIgnoreCase(t.getRequestMethod())) {
                    String body = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, String> params = parseFormData(body);
                    String a = params.get("action");
                    String tg = params.get("target");
                    String r = params.get("reason");

                    System.out.println("⚡ [ADMIN AKCE] " + a + " -> " + tg);

                    if ("kick".equals(a)) { Server.kickUser(tg, r); }
                    else if ("ban".equals(a)) {
                        long seconds = -1;
                        try { if (params.containsKey("duration")) seconds = Long.parseLong(params.get("duration")); } catch (Exception e) {}
                        Server.banUser(tg, "WEB_ADMIN", r, seconds);
                    }
                    else if ("unban".equals(a)) { if(Server.unbanUser(tg)) Server.log("WEB UNBAN: " + tg); }
                    else if ("broadcast".equals(a)) { Server.sendSystemBroadcast(params.get("message"), "ALL"); }
                    else if ("mute".equals(a)) { try { Server.muteUser(tg, Integer.parseInt(params.get("duration")), r); } catch(Exception e){} }
                }
                sendResponse(t, "OK", "text/plain", 200);
            } catch (Throwable e) {
                System.err.println("❌ CHYBA V ACTION HANDLERU:");
                e.printStackTrace();
                t.close();
            }
        }
    }

    // ==========================================
    // 5. DOWNLOAD LOGS
    // ==========================================
    static class LogDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("LOGY SERVERU\nVygenerováno: ").append(new Date()).append("\n\n");
                for(String l : Server.serverLogs) sb.append(l).append("\n");
                byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
                t.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                t.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"server_logs.txt\"");
                t.sendResponseHeaders(200, bytes.length);
                try(OutputStream os=t.getResponseBody()){os.write(bytes);}
            } catch (Throwable e) {
                t.close();
            }
        }
    }

    // ==========================================
    // POMOCNÉ METODY
    // ==========================================
    private static void sendResponse(HttpExchange t, String resp, String type, int code) throws IOException {
        byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
        sendResponse(t, bytes, type, code);
    }

    private static void sendResponse(HttpExchange t, byte[] bytes, String type, int code) throws IOException {
        t.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        t.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = t.getResponseBody()) { os.write(bytes); }
    }

    private static String escapeJson(String raw) { return raw == null ? "" : raw.replace("\\", "\\\\").replace("\"", "\\\""); }

    private static Map<String, String> parseFormData(String body) {
        Map<String, String> map = new HashMap<>();
        if (body != null && !body.isEmpty()) {
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=");
                if (kv.length == 2) map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }
}