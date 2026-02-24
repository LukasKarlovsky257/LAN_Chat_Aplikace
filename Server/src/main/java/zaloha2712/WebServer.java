package zaloha2712;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.example.managers.DatabaseManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class WebServer {
    private static final int PORT = 8080;
    private static HttpServer server;

    public static void start() {
        try {
            if (server != null) stop();
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/", new DashboardHandler());
            server.createContext("/api/data", new ApiDataHandler());
            server.createContext("/action", new ActionHandler());
            server.createContext("/download-logs", new LogDownloadHandler());
            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();
            Server.log("🌍 ADMIN DASHBOARD běží na: http://localhost:" + PORT);
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

    static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                String htmlTemplate = """
                <!DOCTYPE html><html lang='cs' data-bs-theme='dark'><head><meta charset='UTF-8'>
                <title>Server Admin Panel</title>
                <link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>
                <style>
                    body { background-color: #121212; color: #e0e0e0; }
                    .log-box { 
                        background: #0d1117; color: #c9d1d9; font-family: monospace; 
                        padding: 15px; height: 400px; overflow-y: scroll; 
                        border: 1px solid #30363d; border-radius: 6px; font-size: 13px;
                    }
                    .log-line { border-bottom: 1px solid #21262d; padding: 2px 0; }
                    .log-time { color: #8b949e; margin-right: 10px; font-size: 0.85em; }
                    .log-err { color: #ff7b72; font-weight: bold; }
                    .log-sys { color: #79c0ff; }
                    .log-join { color: #56d364; }
                    .log-adm { color: #d2a8ff; font-weight: bold; }
                    .card { background-color: #161b22; border-color: #30363d; }
                    .card-header { background-color: #21262d; border-bottom: 1px solid #30363d; font-weight: bold; }
                    #offline-overlay { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.9); display: none; justify-content: center; align-items: center; z-index: 9999; color: red; font-size: 2rem; }
                </style>
                </head><body>
                <div id="offline-overlay">⚠️ SERVER JE OFFLINE</div>

                <nav class='navbar navbar-dark bg-dark border-bottom border-secondary mb-4 p-3'>
                    <div class='container d-flex justify-content-between'>
                        <span class='navbar-brand fw-bold text-primary'>🚀 JAVA CHAT ADMIN</span>
                        <a href="/download-logs" class="btn btn-sm btn-outline-warning">📥 LOGY</a>
                    </div>
                </nav>
                
                <div class='container'>
                    <div class='row mb-4'>
                        <div class='col-md-4'><div class='card text-center'><div class='card-body'><h6 class="text-muted">ONLINE</h6><h2 class='fw-bold text-success' id='statOnline'>0</h2></div></div></div>
                        <div class='col-md-4'><div class='card text-center'><div class='card-body'><h6 class="text-muted">UPTIME</h6><h2 class='fw-bold text-info' id='statUptime'>00:00:00</h2></div></div></div>
                        <div class='col-md-4'><div class='card text-center'><div class='card-body'><h6 class="text-muted">ZAZNAMENANÉ AKCE</h6><h2 class='fw-bold text-warning' id='statLogs'>0</h2></div></div></div>
                    </div>

                    <div class='row'>
                        <div class='col-md-4 mb-4'>
                            <div class='card h-50 mb-3'>
                                <div class='card-header'>👤 Online Uživatelé</div>
                                <div class='card-body p-0' style="overflow-y: auto; max-height: 300px;">
                                    <ul class='list-group list-group-flush' id='userList'></ul>
                                </div>
                                <div class='card-footer'>
                                    <div class='input-group input-group-sm'>
                                        <input type='text' id='bcInput' class='form-control bg-dark text-light border-secondary' placeholder='Oznámení...'>
                                        <button onclick='sendBroadcast()' class='btn btn-primary'>📢</button>
                                    </div>
                                </div>
                            </div>
                            
                            <div class='card h-50'>
                                <div class='card-header text-danger'>⛔ Zabanovaní</div>
                                <div class='card-body p-0' style="overflow-y: auto; max-height: 200px;">
                                    <ul class='list-group list-group-flush' id='banList'></ul>
                                </div>
                            </div>
                        </div>

                        <div class='col-md-8 mb-4'>
                            <div class='card h-100'>
                                <div class='card-header d-flex justify-content-between'>
                                    <span>💻 Live Console</span><span class="badge bg-success">LIVE</span>
                                </div>
                                <div class='card-body p-0'><div class='log-box' id='logBox'></div></div>
                            </div>
                        </div>
                    </div>
                </div>

                <script>
                    const START_TIME = {{START_TIME}};
                    
                    setInterval(() => {
                        let diff = Math.floor((Date.now() - START_TIME) / 1000);
                        let h = Math.floor(diff / 3600).toString().padStart(2,'0');
                        let m = Math.floor((diff % 3600) / 60).toString().padStart(2,'0');
                        let s = (diff % 60).toString().padStart(2,'0');
                        document.getElementById('statUptime').innerText = `${h}:${m}:${s}`;
                    }, 1000);

                    function refreshData() {
                        fetch('/api/data').then(r => r.json()).then(data => {
                            document.getElementById('offline-overlay').style.display = 'none';
                            document.getElementById('statOnline').innerText = data.online;
                            document.getElementById('statLogs').innerText = data.logCount;
                            
                            // LOGS
                            const logBox = document.getElementById('logBox');
                            const wasBottom = logBox.scrollHeight - logBox.clientHeight <= logBox.scrollTop + 50;
                            logBox.innerHTML = data.logs.map(l => {
                                let c = ''; if(l.includes("ERR")||l.includes("BAN")) c='log-err'; else if(l.includes("SYS")) c='log-sys'; else if(l.includes("join")) c='log-join'; else if(l.includes("ADMIN")) c='log-adm';
                                return `<div class='log-line ${c}'>${l}</div>`;
                            }).join('');
                            if(wasBottom) logBox.scrollTop = logBox.scrollHeight;

                            // ONLINE USERS
                            const uList = document.getElementById('userList');
                            if(data.users.length === 0) uList.innerHTML = "<li class='list-group-item bg-dark text-muted text-center'>Nikdo není online</li>";
                            else uList.innerHTML = data.users.map(u => `
                                <li class='list-group-item bg-dark text-light d-flex justify-content-between align-items-center'>
                                    <span>🟢 <b>${u}</b></span>
                                    <div class="btn-group btn-group-sm">
                                        <button onclick="cmd('mute','${u}')" class='btn btn-outline-secondary'>🤐</button>
                                        <button onclick="cmd('kick','${u}')" class='btn btn-outline-warning'>👢</button>
                                        <button onclick="cmd('ban','${u}')" class='btn btn-outline-danger'>🔨</button>
                                    </div>
                                </li>`).join('');
                                
                            // BANNED USERS
                            const bList = document.getElementById('banList');
                            if(data.banned.length === 0) bList.innerHTML = "<li class='list-group-item bg-dark text-muted text-center'>Žádné bany</li>";
                            else bList.innerHTML = data.banned.map(b => {
                                let expireInfo = (b.until === -1 || b.until === 0) ? "Navždy" : new Date(b.until).toLocaleString();
                                return `
                                <li class='list-group-item bg-dark text-light d-flex justify-content-between align-items-center'>
                                    <div>
                                        <span class="text-danger fw-bold">${b.user}</span><br>
                                        <small class="text-muted">${b.reason}</small><br>
                                        <small class="text-info" style="font-size: 0.7em">Do: ${expireInfo}</small>
                                    </div>
                                    <button onclick="cmd('unban','${b.user}')" class='btn btn-sm btn-success'>✅</button>
                                </li>`
                            }).join('');
                                
                        }).catch(() => document.getElementById('offline-overlay').style.display = 'flex');
                    }
                    setInterval(refreshData, 2000); refreshData();

                    function cmd(a, t) {
                        let r="", d="";
                        if(a==='kick'||a==='ban'||a==='mute') { r=prompt("Důvod?"); if(!r) return; }
                        
                        // 🔥 ZMĚNA: Na čas se ptáme i u BANu
                        if(a==='mute' || a==='ban') { d=prompt("Sekundy? (-1 pro navždy)", "60"); if(!d) return; }
                        
                        fetch('/action', { method: 'POST', body: `action=${a}&target=${t}&reason=${encodeURIComponent(r)}&duration=${d}` });
                    }
                    function sendBroadcast() { let v=document.getElementById('bcInput').value; if(v) fetch('/action', { method: 'POST', body: `action=broadcast&message=${encodeURIComponent(v)}` }); document.getElementById('bcInput').value=''; }
                </script>
                </body></html>
                """;
                String finalHtml = htmlTemplate.replace("{{START_TIME}}", String.valueOf(Server.startTime));
                sendResponse(t, finalHtml, "text/html");
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    static class ApiDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                StringBuilder json = new StringBuilder("{");
                json.append("\"online\":").append(Server.getOnlineCount()).append(",");
                json.append("\"logCount\":").append(Server.serverLogs.size()).append(",");

                // Logs
                json.append("\"logs\":[");
                List<String> logs = new ArrayList<>(Server.serverLogs);
                int start = Math.max(0, logs.size() - 50);
                for (int i = start; i < logs.size(); i++) {
                    json.append("\"").append(escapeJson(logs.get(i))).append("\"");
                    if (i < logs.size() - 1) json.append(",");
                }
                json.append("],");

                // Users
                json.append("\"users\":[");
                String[] users = Server.getUserList();
                for (int i = 0; i < users.length; i++) {
                    json.append("\"").append(escapeJson(users[i])).append("\"");
                    if (i < users.length - 1) json.append(",");
                }
                json.append("],");

                // BANNED - ZDE JE UPRAVENÁ LOGIKA
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

                    // KONTROLA EXPIRACE BANU
                    if (until != -1 && until != 0 && now > until) {
                        // Ban vypršel -> Automaticky odbanovat v DB a přeskočit výpis
                        Server.unbanUser(username);
                        continue;
                    }

                    // Pokud je ban stále platný, přidáme ho do JSONu
                    validBansJson.add(String.format("{\"user\":\"%s\",\"admin\":\"%s\",\"reason\":\"%s\",\"until\":%s}",
                            escapeJson(username), escapeJson(admin), escapeJson(reason), untilStr));
                }

                // Spojíme validní bany čárkou
                json.append(String.join(",", validBansJson));
                json.append("]}");

                sendResponse(t, json.toString(), "application/json");
            } catch (Exception e) { t.close(); }
        }
    }

    static class ActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equalsIgnoreCase(t.getRequestMethod())) {
                String body = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);
                String a = params.get("action");
                String tg = params.get("target");
                String r = params.get("reason");

                if ("kick".equals(a)) {
                    Server.kickUser(tg, r);
                }
                else if ("ban".equals(a)) {
                    // 🔥 ZMĚNA: Načtení času i pro BAN
                    long seconds = -1;
                    try {
                        if (params.containsKey("duration") && !params.get("duration").isEmpty()) {
                            seconds = Long.parseLong(params.get("duration"));
                        }
                    } catch (Exception e) {}

                    Server.banUser(tg, "WEB_ADMIN", r, seconds);
                }
                else if ("unban".equals(a)) {
                    if(Server.unbanUser(tg)) Server.log("WEB UNBAN: " + tg);
                }
                else if ("broadcast".equals(a)) {
                    Server.sendSystemBroadcast(params.get("message"), "ALL");
                }
                else if ("mute".equals(a)) {
                    try {
                        Server.muteUser(tg, Integer.parseInt(params.get("duration")), r);
                    } catch(Exception e){}
                }
            }
            sendResponse(t, "OK", "text/plain");
        }
    }

    static class LogDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("LOGY SERVERU\nVygenerováno: ").append(new Date()).append("\n\n");
            for(String l : Server.serverLogs) sb.append(l).append("\n");
            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            t.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            t.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"server_logs.txt\"");
            t.sendResponseHeaders(200, bytes.length);
            try(OutputStream os=t.getResponseBody()){os.write(bytes);}
        }
    }

    private static void sendResponse(HttpExchange t, String resp, String type) throws IOException {
        byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        t.sendResponseHeaders(200, bytes.length);
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