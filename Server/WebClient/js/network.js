/* =========================================
   2. NETWORK - WebSockets a Protokol
   ========================================= */

const net = {
    sendText: (text) => {
        console.log("[NET] Odesílám:", text.substring(0, 40) + "...");
        if (!state.ws || state.ws.readyState !== 1) return;

        // PŘIDÁNO: || text.startsWith("SET_AVATAR:")
        if (text.startsWith("GAME:") || text.startsWith("IMG:") || text.startsWith("FILE:") || text.includes("ZK:") || text.startsWith("SET_AVATAR:")) {
            state.ws.send(text);
        } else if (state.serverPublicKey) {
            const enc = state.encryptor.encrypt(text);
            if (enc) {
                state.ws.send("ENC:" + enc);
            } else {
                console.warn("[NET] Zpráva je příliš dlouhá pro RSA fallback, odeslána napřímo.");
                state.ws.send(text);
            }
        } else {
            state.ws.send(text);
        }
    }
};

const auth = {
    connect: (rawIp) => {
        if (state.ws) state.ws.close();
        const statusEl = document.getElementById('auth-status');
        if (statusEl) {
            statusEl.innerText = "Navazuji spojení...";
            statusEl.style.color = "#949ba4";
        }
        try {
            let ip = rawIp.replace("http://", "").replace("https://", "").replace("ws://", "").replace("wss://", "").trim();
            let url = ip.includes("ngrok") ? (ip.includes("tcp") && ip.includes(":") ? `wss://${ip}` : `wss://${ip.split(":")[0]}`) : (!ip.includes(":") ? `ws://${ip}:8887` : `ws://${ip}`);
            state.ws = new WebSocket(url);
            state.ws.onopen = () => { if (statusEl) statusEl.innerText = "Spojení navázáno! Ověřování..."; };
            state.ws.onmessage = (e) => protocol.handle(e.data);
            state.ws.onerror = () => ui.showAlert("Chyba připojení. Zkontroluj adresu.");
            state.ws.onclose = (event) => {
                document.getElementById('app-screen').style.display = 'none';
                document.getElementById('auth-screen').style.display = 'flex';
                ui.showAlert(event.reason ? event.reason : "Spojení se serverem bylo ztraceno.");
                document.getElementById('messages-box').innerHTML = "";
            };
        } catch (err) { ui.showAlert("Neplatný formát adresy."); }
    },
    login: () => {
        const ip = document.getElementById('inp-ip').value || 'localhost', u = document.getElementById('inp-user').value, p = document.getElementById('inp-pass').value;
        if (!u || !p) return ui.showAlert("Vyplň jméno a heslo!");
        auth.connect(ip); setTimeout(() => net.sendText(`LOGIN:${u}:${p}`), 1500);
    },
    register: () => {
        const ip = document.getElementById('reg-ip').value || 'localhost', u = document.getElementById('reg-user').value, p = document.getElementById('reg-pass').value, c = document.getElementById('reg-code').value;
        if (!u || !p || !c) return ui.showAlert("Vyplň všechna pole!");
        auth.connect(ip); setTimeout(() => net.sendText(`REGISTER:${u}:${p}:${c}`), 1500);
    }
};

const protocol = {
    handle: (msg) => {
        if (!msg.startsWith("GAME:WB:DRAW:")) console.log(`📥 RAW: ${msg}`);

        if (msg.startsWith("PUBKEY:")) { state.serverPublicKey = msg.split("PUBKEY:")[1]; state.encryptor.setPublicKey(state.serverPublicKey); return; }
        if (msg.startsWith("LOGIN_OK:")) { const parts = msg.split(":"); state.nick = parts[1]; state.isAdmin = (parts[2] === "true"); ui.initApp(); }
        else if (msg === "REGISTER_OK") { ui.showAlert("Účet vytvořen! Nyní se přihlas.", false); ui.switchAuth('login'); }
        else if (msg.startsWith("ROOM_CHANGED:")) ui.updateRoomUI(msg.split(":")[1]);
        else if (msg.startsWith("ROOM_LIST:")) ui.renderRoomList(msg.substring(10).split(","));
        else if (msg.startsWith("USERS:")) {
            const usersList = msg.substring(6).split(",");
            document.getElementById('user-list').innerHTML = "";
            state.users = [];

            usersList.forEach(u => {
                if (u.trim() !== "" && u !== "SYSTEM") {
                    // Rozdělíme na jméno+level a avatar (podle vlnovky ~)
                    let mainParts = u.split("~");
                    let infoParts = mainParts[0].split("|");

                    let nick = infoParts[0].trim();
                    let level = infoParts.length > 1 ? infoParts[1].trim() : "";
                    let avatar = mainParts.length > 1 ? mainParts[1].trim() : null;

                    if (avatar) state.avatars[nick] = avatar + "?v=" + Date.now();

                    ui.addUserToSidebar(nick, level, avatar);

                    // Pokud je to náš účet a přišel nám náš avatar z DB, hned si ho nastavíme
                    if (nick === state.nick && avatar) {
                        document.getElementById('my-avatar').innerHTML = `<img src="/avatars/${avatar}" style="width:100%; height:100%; object-fit:cover;">`;
                    }
                }
            });
        }
        // ... A PŘIDEJ JEŠTĚ TOTO úplně pod to:
        else if (msg.startsWith("UPDATE_AVATAR:")) {
            // Když si někdo v místnosti změní avatar, musíme si znovu vyžádat seznam
            setTimeout(() => net.sendText("/users"), 200);
        }

        // 👇 PŘIDÁNO: Zpracování indikátoru psaní 👇
        else if (msg.startsWith("USER_TYPING:")) {
            const parts = msg.split(":");
            if (parts.length >= 3) {
                const typingUser = parts[1];

                // 👇 PŘIDÁNO: Pokud jsme to my, nic nezobrazujeme
                if (typingUser === state.nick) return;

                const isTypingStatus = parts[2] === "1";
                const indicator = document.getElementById('typing-indicator');
                if (indicator) {
                    indicator.innerText = isTypingStatus ? `✍️ ${typingUser} právě píše...` : "";
                }
            }
        }

        else if (msg.startsWith("MSG:") || msg.startsWith("CHAT:") || msg.startsWith("HIST:") || msg.startsWith("BURN:") || msg.startsWith("GAME:")) {
            let cleanMsg = msg;
            if (cleanMsg.startsWith("HIST:")) cleanMsg = cleanMsg.substring(5);
            if (cleanMsg.startsWith("CHAT:")) cleanMsg = cleanMsg.substring(5);
            if (cleanMsg.startsWith("GAME:WB:START:")) {
                let parts = cleanMsg.substring(14).split(":");
                renderer.renderWhiteboard(parts[0], parts[1], parts[2]);
            }
            else if (cleanMsg.startsWith("GAME:WB:DRAW:")) renderer.drawWhiteboard(cleanMsg.substring(13));
            else if (cleanMsg.startsWith("GAME:WB:CLEAR:")) renderer.clearWhiteboard(cleanMsg.substring(14));
            else if (cleanMsg.startsWith("GAME:WB:CLOSE:")) renderer.closeWhiteboard(cleanMsg.substring(14));
            else if (cleanMsg.startsWith("GAME:TTT:")) renderer.renderGame(cleanMsg.substring(9));
            else if (cleanMsg.startsWith("IMG:") || cleanMsg.startsWith("FILE:")) renderer.renderFile(cleanMsg);
            else if (cleanMsg.startsWith("BURN:")) renderer.renderBurnMessage(cleanMsg);
            else { if (cleanMsg.startsWith("MSG:")) cleanMsg = cleanMsg.substring(4); renderer.renderMessage(cleanMsg); }
        }
        else if (msg.startsWith("IMG:") || msg.startsWith("FILE:")) renderer.renderFile(msg);
        else if (msg.startsWith("DELETE_MSG:")) { const idToDel = msg.split(":")[1].trim(); const msgEl = document.getElementById(`msg-${idToDel}`); if (msgEl) msgEl.style.display = 'none'; }
        else if (msg.startsWith("ERROR:")) { const errText = msg.split(":")[1]; ui.showAlert(errText); }
    }
};