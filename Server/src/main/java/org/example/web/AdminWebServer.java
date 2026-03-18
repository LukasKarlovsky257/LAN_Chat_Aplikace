package org.example.web;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.example.Server;
import org.example.managers.DatabaseManager;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class AdminWebServer {
    private static final int PORT = 8888;
    private static HttpServer server;
    private static final String WEB_ROOT = "/WebClient";

    public static void start() {
        try {
            if (server != null) stop();

            server = HttpServer.create(new InetSocketAddress(PORT), 0);

            BasicAuthenticator auth = new BasicAuthenticator("LAN Chat - Admin Trezor") {
                @Override
                public boolean checkCredentials(String user, String pwd) {
                    return DatabaseManager.checkCredentials(user, pwd) && DatabaseManager.isAdmin(user);
                }
            };

            HttpContext ctxRoot = server.createContext("/", new DashboardHandler());
            ctxRoot.setAuthenticator(auth);

            HttpContext ctxAdmin = server.createContext("/admin", new DashboardHandler());
            ctxAdmin.setAuthenticator(auth);

            HttpContext ctxApi = server.createContext("/api/data", new ApiDataHandler());
            ctxApi.setAuthenticator(auth);

            HttpContext ctxAction = server.createContext("/admin/execute", new ActionHandler());
            ctxAction.setAuthenticator(auth);

            HttpContext ctxLogs = server.createContext("/download-logs", new LogDownloadHandler());
            ctxLogs.setAuthenticator(auth);

            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();

            Server.log("🛡️ ADMIN SERVER BĚŽÍ (Chráněno heslem): Port " + PORT);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            Server.log("🛡️ AdminServer zastaven.");
        }
    }

    static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                InputStream is = AdminWebServer.class.getResourceAsStream(WEB_ROOT + "/admin.html");
                if (is != null) {
                    byte[] fileBytes = is.readAllBytes();
                    is.close();
                    String htmlTemplate = new String(fileBytes, StandardCharsets.UTF_8);
                    String finalHtml = htmlTemplate.replace("{{START_TIME}}", String.valueOf(Server.startTime));
                    sendResponse(t, finalHtml, "text/html", 200);
                } else {
                    sendResponse(t, "404 - Soubor admin.html nenalezen.", "text/plain", 404);
                }
            } catch (Throwable e) {
                e.printStackTrace();
                try { sendResponse(t, "500 - Interni chyba", "text/plain", 500); } catch (Exception ex) { t.close(); }
            }
        }
    }

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
                List<String[]> bannedRaw = DatabaseManager.getBannedList();
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
                e.printStackTrace();
                t.close();
            }
        }
    }

    static class ActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                if ("POST".equalsIgnoreCase(t.getRequestMethod())) {
                    String body = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, String> params = parseFormData(body);
                    String a = params.get("action");
                    String tg = params.get("target");
                    String r = params.getOrDefault("reason", "Zásah z Web Admina");

                    String adminName = t.getPrincipal().getUsername();

                    if ("kick".equals(a)) {
                        Server.kickUser(tg, r);
                    }
                    else if ("ban".equals(a)) {
                        long seconds = -1;
                        try { if (params.containsKey("duration")) seconds = Long.parseLong(params.get("duration")); } catch (Exception e) {}
                        Server.banUser(tg, adminName, r, seconds);
                    }
                    else if ("unban".equals(a)) {
                        if(Server.unbanUser(tg)) Server.log("WEB UNBAN: " + tg + " (by " + adminName + ")");
                    }
                    else if ("broadcast".equals(a)) {
                        Server.sendSystemBroadcast(r, "ALL");
                    }
                    else if ("mute".equals(a)) {
                        try { Server.muteUser(tg, Integer.parseInt(params.get("duration")), r); } catch(Exception e){}
                    }
                }
                sendResponse(t, "OK", "text/plain", 200);
            } catch (Throwable e) {
                e.printStackTrace();
                t.close();
            }
        }
    }

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

    private static void sendResponse(HttpExchange t, String resp, String type, int code) throws IOException {
        byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
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
                if (kv.length == 2) {
                    try {
                        map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                    } catch (Exception e) {}
                }
            }
        }
        return map;
    }
}