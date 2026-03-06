const isValidString = (str) => !str.includes(':') && !str.includes('~');

const setLock = (btnSelector, isLocked) => {
    document.querySelectorAll(btnSelector).forEach(btn => {
        btn.disabled = isLocked;
        btn.style.opacity = isLocked ? "0.5" : "1";
        if (isLocked && !btn.dataset.origText) btn.dataset.origText = btn.innerText;
        btn.innerText = isLocked ? "Čekejte..." : (btn.dataset.origText || "Odeslat");
    });
};

const net = {
    sendText: (text) => {
        console.log("[NET] Odesílám:", text.substring(0, 40) + "...");
        if (!state.ws || state.ws.readyState !== 1) return;

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
    connect: (rawIp, pendingMessage) => {
        const statusEl = document.getElementById('auth-status');
        if (statusEl) {
            statusEl.innerText = "Navazuji spojení...";
            statusEl.style.color = "#949ba4";
        }

        state.pendingAuthMsg = pendingMessage;

        if (state.ws && state.ws.readyState === 1) {
            if (state.pendingAuthMsg) {
                net.sendText(state.pendingAuthMsg);
                state.pendingAuthMsg = null;
            }
            return;
        }

        // 🔥 FIX: Odstřihneme staré mrtvé spojení bez vyvolání falešné chybové hlášky
        if (state.ws) {
            state.ws.onclose = null;
            state.ws.close();
        }

        try {
            let ip = rawIp.replace("http://", "").replace("https://", "").replace("ws://", "").replace("wss://", "").trim();
            let url = ip.includes("ngrok") ? (ip.includes("tcp") && ip.includes(":") ? `wss://${ip}` : `wss://${ip.split(":")[0]}`) : (!ip.includes(":") ? `ws://${ip}:8887` : `ws://${ip}`);
            state.ws = new WebSocket(url);

            state.ws.onopen = () => { if (statusEl) statusEl.innerText = "Spojení navázáno! Ověřování..."; };
            state.ws.onmessage = (e) => protocol.handle(e.data);

            state.ws.onerror = () => {
                ui.showAlert("Chyba připojení. Zkontroluj adresu.", true);
                setLock('#form-login button, #form-register button, #form-recover button', false);
            };

            state.ws.onclose = (event) => {
                document.getElementById('app-screen').style.display = 'none';
                document.getElementById('auth-screen').style.display = 'flex';
                ModernDialog.showMessage("Odpojeno", event.reason ? event.reason : "Spojení se serverem bylo ztraceno.", true);
                document.getElementById('messages-box').innerHTML = "";
                setLock('#form-login button, #form-register button, #form-recover button', false);
            };
        } catch (err) { ui.showAlert("Neplatný formát adresy.", true); }
    },

    login: () => {
        // 🔥 OCHRANA PROTI DOUBLE-LOGINU (když uživatel drží / spamuje Enter)
        const btn = document.querySelector('#form-login button');
        if (btn && btn.disabled) return;

        const ip = document.getElementById('inp-ip').value || 'localhost';
        const u = document.getElementById('inp-user').value;
        const p = document.getElementById('inp-pass').value;

        if (!u || !p) return ui.showAlert("Vyplň jméno a heslo!", true);
        if (!isValidString(u) || !isValidString(p)) return ui.showAlert("Jméno a heslo nesmí obsahovat znaky ':' a '~'", true);

        setLock('#form-login button', true);
        localStorage.setItem('lan_ip', ip);
        localStorage.setItem('lan_user', u);

        if (localStorage.getItem('lan_autologin') !== 'false') {
            // 🔥 ULOŽENÍ JAKO HASH/BASE64 (Aby to nebylo vidět v plain textu v Local Storage)
            localStorage.setItem('lan_pass', btoa(encodeURIComponent(p)));
        } else {
            localStorage.removeItem('lan_pass');
        }

        auth.connect(ip, `LOGIN:${u}:${p}`);
    },

    logout: () => {
        localStorage.removeItem('lan_pass');
        location.reload();
    },

    autoLogin: () => {
        if (localStorage.getItem('lan_autologin') === 'false') return;

        const savedIp = localStorage.getItem('lan_ip');
        const savedUser = localStorage.getItem('lan_user');
        const savedPassHash = localStorage.getItem('lan_pass');

        if (savedIp && savedUser && savedPassHash) {
            document.getElementById('inp-ip').value = savedIp;
            document.getElementById('inp-user').value = savedUser;

            try {
                // 🔥 DEKÓDOVÁNÍ Z HASH/BASE64 zpět pro připojení na server
                const decodedPass = decodeURIComponent(atob(savedPassHash));
                document.getElementById('inp-pass').value = decodedPass;
            } catch (e) {
                // Pokud je v paměti staré plain text heslo (např. 12345), záměrně ho smažeme
                document.getElementById('inp-pass').value = "";
                localStorage.removeItem('lan_pass');
                return;
            }

            auth.login();
        }
    },

    register: () => {
        const btn = document.querySelector('#form-register button');
        if (btn && btn.disabled) return;

        const ip = document.getElementById('reg-ip').value || 'localhost', u = document.getElementById('reg-user').value, p = document.getElementById('reg-pass').value, c = document.getElementById('reg-code').value;
        if (!u || !p || !c) return ui.showAlert("Vyplň všechna pole!", true);
        if (!isValidString(u) || !isValidString(p) || !isValidString(c)) return ui.showAlert("Znaky ':' a '~' nejsou povoleny!", true);

        setLock('#form-register button', true);
        auth.connect(ip, `REGISTER:${u}:${p}:${c}`);
    },

    recover: () => {
        const btn = document.querySelector('#form-recover button');
        if (btn && btn.disabled) return;

        const ip = document.getElementById('rec-ip').value || 'localhost';
        const u = document.getElementById('rec-user').value;
        const code = document.getElementById('rec-code').value;
        const p = document.getElementById('rec-newpass').value;

        if (!u || !p || !code) return ui.showAlert("Vyplň jméno, nové heslo i recovery kód!", true);
        if (!isValidString(u) || !isValidString(p) || !isValidString(code)) return ui.showAlert("Znaky ':' a '~' nejsou povoleny!", true);

        setLock('#form-recover button', true);
        auth.connect(ip, `RECOVER:${u}:${p}:${code}`);
    },
};

const protocol = {
    handle: (msg) => {
        if (!msg.startsWith("GAME:WB:DRAW:")) console.log(`📥 RAW: ${msg}`);

        if (msg.startsWith("PUBKEY:")) {
            state.serverPublicKey = msg.split("PUBKEY:")[1];
            state.encryptor.setPublicKey(state.serverPublicKey);
            if (state.pendingAuthMsg) {
                net.sendText(state.pendingAuthMsg);
                state.pendingAuthMsg = null;
            }
            return;
        }

        if (msg.startsWith("LOGIN_OK:")) {
            const parts = msg.split(":");
            state.nick = parts[1];
            state.isAdmin = (parts[2] === "true");
            setLock('#form-login button', false);
            ui.initApp();
        }
        else if (msg === "REGISTER_OK") {
            setLock('#form-register button', false);
            ModernDialog.showMessage("Registrace", "Účet vytvořen! Nyní se přihlas.", false).then(() => {
                ui.switchAuth('login');
            });
        }
        else if (msg === "RECOVER_OK") {
            setLock('#form-recover button', false);
            ModernDialog.showMessage("Obnova Hesla", "Heslo bylo úspěšně změněno! Nyní se přihlas.", false).then(() => {
                ui.switchAuth('login');
            });
        }
        else if (msg.startsWith("ROOM_CHANGED:")) ui.updateRoomUI(msg.split(":")[1]);
        else if (msg.startsWith("ROOM_LIST:")) ui.renderRoomList(msg.substring(10).split(","));
        else if (msg.startsWith("USERS:")) {
            const usersList = msg.substring(6).split(",");
            document.getElementById('user-list').innerHTML = "";
            state.users = [];

            usersList.forEach(u => {
                if (u.trim() !== "" && u !== "SYSTEM") {
                    let mainParts = u.split("~");
                    let infoParts = mainParts[0].split("|");

                    let nick = infoParts[0].trim();
                    let level = infoParts.length > 1 ? infoParts[1].trim() : "";
                    let avatar = mainParts.length > 1 ? mainParts[1].trim() : null;

                    if (avatar) state.avatars[nick] = avatar + "?v=" + Date.now();

                    ui.addUserToSidebar(nick, level, avatar);

                    if (nick === state.nick && avatar) {
                        document.getElementById('my-avatar').innerHTML = `<img src="/avatars/${avatar}" style="width:100%; height:100%; object-fit:cover;">`;
                    }
                }
            });
        }
        else if (msg.startsWith("UPDATE_AVATAR:")) {
            setTimeout(() => net.sendText("/users"), 200);
        }
        else if (msg.startsWith("USER_TYPING:")) {
            const parts = msg.split(":");
            if (parts.length >= 3) {
                const typingUser = parts[1];
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
        // 🔥 ZPRACOVÁNÍ ODPOJENÍ OD SERVERU
        else if (msg.startsWith("DISCONNECT:")) {
            const reason = msg.substring(11);
            if (state.ws) { state.ws.onclose = null; state.ws.close(); }
            document.getElementById('app-screen').style.display = 'none';
            document.getElementById('auth-screen').style.display = 'flex';
            ModernDialog.showMessage("Odpojen ze serveru", reason, true);
            document.getElementById('messages-box').innerHTML = "";
            setLock('#form-login button, #form-register button, #form-recover button', false);
        }
        else if (msg.startsWith("ERROR:")) {
            const errText = msg.split(":")[1];
            ui.showAlert(errText, true);
            setLock('#form-login button, #form-register button, #form-recover button', false);
            if (errText.includes("Špatné heslo") || errText.includes("neexistuje")) {
                localStorage.removeItem('lan_pass');
            }
        }
    }
};

window.addEventListener('DOMContentLoaded', () => {
    auth.autoLogin();
});