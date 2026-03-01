/* =========================================
   LAN CHAT ULTIMATE - CORE LOGIC v7.0 (PERFECT ZK)
   ========================================= */

if (!document.getElementById('auth-screen')) {
    throw new Error("Script stopped explicitly for landing page safety.");
}

const appConfig = {
    sound: true, enterToSend: true,
    load: () => {
        let saved = localStorage.getItem('lanChatConfig');
        if(saved) {
            let parsed = JSON.parse(saved);
            appConfig.sound = parsed.sound !== undefined ? parsed.sound : true;
            appConfig.enterToSend = parsed.enterToSend !== undefined ? parsed.enterToSend : true;
        }
    },
    save: () => { localStorage.setItem('lanChatConfig', JSON.stringify({sound: appConfig.sound, enterToSend: appConfig.enterToSend})); }
};
appConfig.load();

const state = { ws: null, nick: "", isAdmin: false, currentRoom: "Lobby", activeContextUser: null, activeMessageId: null, privateTarget: null, users: [], targetUser: "", isWindowActive: true, serverPublicKey: null, encryptor: new JSEncrypt(), pendingInviteId: null };

window.onfocus = () => { state.isWindowActive = true; };
window.onblur = () => { state.isWindowActive = false; };
window.addEventListener('click', (event) => { if (event.target.classList.contains('modal')) ui.closeModals(); });

// 🔥 OPRAVENÝ A OŠETŘENÝ ZERO-KNOWLEDGE MOTOR
const cryptoAES = {
    getKey: (password) => { return CryptoJS.SHA256(password.trim()); },
    encrypt: (plainText, roomPassword) => {
        try {
            if (typeof CryptoJS === 'undefined') return null;
            const key = cryptoAES.getKey(roomPassword);
            const iv = CryptoJS.lib.WordArray.random(16);
            const encrypted = CryptoJS.AES.encrypt(plainText.trim(), key, { iv: iv, mode: CryptoJS.mode.CBC, padding: CryptoJS.pad.Pkcs7 });
            return CryptoJS.enc.Base64.stringify(iv) + ":" + CryptoJS.enc.Base64.stringify(encrypted.ciphertext);
        } catch (e) {
            console.error("[ZK] Encrypt Error:", e);
            return null;
        }
    },
    decrypt: (encryptedPayload, roomPassword) => {
        try {
            if (typeof CryptoJS === 'undefined') return null;
            const parts = encryptedPayload.split(":");
            if (parts.length !== 2) return null;

            const iv = CryptoJS.enc.Base64.parse(parts[0].trim());
            const cipherText = CryptoJS.enc.Base64.parse(parts[1].trim());
            const cipherParams = CryptoJS.lib.CipherParams.create({ ciphertext: cipherText });
            const key = cryptoAES.getKey(roomPassword);

            const decrypted = CryptoJS.AES.decrypt(cipherParams, key, { iv: iv, mode: CryptoJS.mode.CBC, padding: CryptoJS.pad.Pkcs7 });
            const plainText = decrypted.toString(CryptoJS.enc.Utf8);
            if (!plainText) { console.warn("[ZK] Selhalo dešifrování. Špatný klíč?"); return null; }
            return plainText;
        } catch (e) {
            console.error("[ZK] Decrypt Error:", e);
            return null;
        }
    }
};

const net = {
    sendText: (text) => {
        console.log("[NET] Odesílám:", text.substring(0, 40) + "...");
        if (!state.ws || state.ws.readyState !== 1) return;

        if (text.startsWith("GAME:") || text.startsWith("IMG:") || text.startsWith("FILE:") || text.includes("ZK:")) {
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
            statusEl.style.color = "#949ba4"; // Reset barvy
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
            document.getElementById('user-list').innerHTML = ""; state.users = [];
            usersList.forEach(u => { if (u.trim() !== "" && u !== "SYSTEM") { let parts = u.split("|"); ui.addUserToSidebar(parts[0].trim(), parts.length > 1 ? parts[1].trim() : ""); } });
        }
        else if (msg.startsWith("MSG:") || msg.startsWith("CHAT:") || msg.startsWith("HIST:") || msg.startsWith("BURN:") || msg.startsWith("GAME:")) {
            let cleanMsg = msg;
            if (cleanMsg.startsWith("HIST:")) cleanMsg = cleanMsg.substring(5);
            if (cleanMsg.startsWith("CHAT:")) cleanMsg = cleanMsg.substring(5);
            if (cleanMsg.startsWith("GAME:WB:START:")) {
                let parts = cleanMsg.substring(14).split(":");
                renderer.renderWhiteboard(parts[0], parts[1], parts[2]); // Zde je oprava! Posíláme jen čisté ID a jména zvlášť
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

const ui = {
    // 🔥 NOVÁ FUNKCE PRO VIZUÁLNÍ NOTIFIKACE (NAHRADILA ALERTY)
    showAlert: (msg, isError = true) => {
        const authScreen = document.getElementById('auth-screen');
        if (authScreen && authScreen.style.display !== 'none') {
            // Jsme na přihlašovací obrazovce
            const statusEl = document.getElementById('auth-status');
            if (statusEl) {
                statusEl.innerText = (isError ? "❌ " : "✅ ") + msg;
                statusEl.style.color = isError ? "#ed4245" : "#46ff50";
            }
        } else {
            // Jsme v chatu - vykreslíme jako SYSTEM zprávu
            const time = new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
            renderer.addBubble(Date.now().toString(), "SYSTEM", (isError ? "⚠️ " : "✅ ") + msg, time, 'text');
        }
    },
    switchAuth: (tab) => {
        document.getElementById('form-login').style.display = tab === 'login' ? 'block' : 'none';
        document.getElementById('form-register').style.display = tab === 'register' ? 'block' : 'none';
        document.getElementById('tab-login').classList.toggle('active', tab === 'login');
        document.getElementById('tab-register').classList.toggle('active', tab === 'register');
    },
    initApp: () => {
        document.getElementById('auth-screen').style.display = 'none'; document.getElementById('app-screen').style.display = 'flex';
        document.getElementById('my-nick').innerText = state.nick; document.getElementById('my-role').innerText = state.isAdmin ? "ADMIN" : "#USER";
        if (state.isAdmin) { document.getElementById('my-role').style.color = '#ed4245'; document.getElementById('admin-logs-trigger').style.display = 'block'; }
        document.getElementById('my-avatar').innerText = state.nick.charAt(0).toUpperCase();
        chat.join('Lobby'); setTimeout(() => { net.sendText("/users"); }, 300);
        document.getElementById('msg-input').addEventListener('keydown', (e) => { if (e.key === 'Escape' && state.privateTarget) ui.cancelWhisper(); });
        ui.initDragDrop();
    },
    openActionModal: () => document.getElementById('modal-actions').style.display = 'flex',
    openSettings: () => { document.getElementById('set-sound').checked = appConfig.sound; document.getElementById('set-enter').checked = appConfig.enterToSend; document.getElementById('modal-settings').style.display = 'flex'; },
    saveSettings: () => { appConfig.sound = document.getElementById('set-sound').checked; appConfig.enterToSend = document.getElementById('set-enter').checked; appConfig.save(); ui.closeModals(); },
    cancelWhisper: () => { state.privateTarget = null; document.getElementById('private-mode-panel').style.display = 'none'; const inp = document.getElementById('msg-input'); inp.placeholder = `Poslat zprávu do #${state.currentRoom}...`; inp.focus(); },
    initDragDrop: () => {
        const overlay = document.getElementById('drag-overlay');
        document.body.addEventListener('dragenter', () => overlay.style.display = 'flex');
        overlay.addEventListener('dragleave', () => overlay.style.display = 'none');
        overlay.addEventListener('drop', (e) => { e.preventDefault(); overlay.style.display = 'none'; if(e.dataTransfer.files.length > 0) chat.uploadFileObj(e.dataTransfer.files[0]); });
        window.addEventListener('dragover', (e) => e.preventDefault());
    },
    updateRoomUI: (roomName) => { state.currentRoom = roomName.trim(); document.getElementById('current-room').innerText = roomName; document.getElementById('messages-box').innerHTML = ""; document.getElementById('user-list').innerHTML = ""; state.users = []; document.getElementById('user-count').innerText = "0"; ui.cancelWhisper(); },
    renderRoomList: (rooms) => {
        const list = document.getElementById('room-list'); list.innerHTML = "";
        rooms.forEach(r => {
            let parts = r.split("|"); let cleanName = parts[0]; let roomType = parts.length > 1 ? parts[1] : "0";
            const li = document.createElement('li');
            if (roomType === "2") li.innerHTML = `<span class="material-icons" style="font-size:16px; color:#ed4245;">lock</span> <span style="color:#ed4245">${cleanName}</span>`;
            else if (roomType === "1") li.innerHTML = `<span class="material-icons" style="font-size:16px; color:#faa61a;">timer</span> <span style="color:#faa61a">${cleanName}</span>`;
            else li.innerHTML = `<span class="material-icons" style="font-size:16px;">tag</span> ${cleanName}`;
            li.onclick = () => chat.join(cleanName);
            if (state.isAdmin && cleanName !== 'Lobby') {
                const delBtn = document.createElement('span'); delBtn.className = "material-icons"; delBtn.style.marginLeft = "auto"; delBtn.style.fontSize = "14px"; delBtn.style.opacity = "0.5"; delBtn.innerText = "close";
                delBtn.onclick = (e) => { e.stopPropagation(); admin.deleteRoom(cleanName); }; li.appendChild(delBtn);
            }
            list.appendChild(li);
        });
    },
    addUserToSidebar: (username, levelStr) => {
        if (state.users.includes(username) || username === "SYSTEM") return;
        state.users.push(username); document.getElementById('user-count').innerText = state.users.length;
        const list = document.getElementById('user-list'); const li = document.createElement('li');
        let lvlHtml = levelStr ? `<span class="level-badge" style="background:#faa61a; color:#fff; font-size:10px; font-weight:bold; padding:2px 6px; border-radius:12px; margin-left:auto;">${levelStr}</span>` : "";
        li.innerHTML = `<div class="avatar" style="background:#5865F2; width:32px; height:32px; font-size:14px; color:white; display:flex; justify-content:center; align-items:center; border-radius:50%; font-weight:bold;">${username.charAt(0).toUpperCase()}</div><span style="font-weight:600; margin-left:10px">${username}</span> ${lvlHtml}`;
        li.oncontextmenu = (e) => { e.preventDefault(); ctx.showSide(e, username); };
        list.appendChild(li);
    },
    igniteBurn: (id, encodedText, seconds) => {
        const btn = document.getElementById(`burn-btn-${id}`); const textBox = document.getElementById(`burn-text-${id}`);
        if(!btn || !textBox) return;
        btn.style.display = 'none'; textBox.style.display = 'block'; let text = decodeURIComponent(atob(encodedText));
        net.sendText("START_TIMER:" + id); let timeLeft = seconds; textBox.innerText = `${text} (Smaže se za ${timeLeft}s)`;
        const interval = setInterval(() => {
            timeLeft--;
            if (timeLeft > 0) textBox.innerText = `${text} (Smaže se za ${timeLeft}s)`;
            else { clearInterval(interval); const msgEl = document.getElementById(`msg-${id}`); if (msgEl) msgEl.style.display = 'none'; }
        }, 1000);
    },
    openGiphy: () => document.getElementById('modal-giphy').style.display = 'flex',
    openGamesDialog: () => document.getElementById('modal-games').style.display = 'flex',
    closeModals: () => {
        // Pokud uživatel klikne mimo okno a visí tam pozvánka, automaticky ji odmítneme
        if (state.pendingInviteId) {
            ui.declineInvite();
        }
        // Následně skryjeme všechna otevřená okna (giphy, nastavení, atd.)
        document.querySelectorAll('.modal').forEach(m => m.style.display = 'none');
    },
    acceptInvite: () => { if(state.pendingInviteId) { net.sendText("/invite accept " + state.pendingInviteId); state.pendingInviteId = null; document.getElementById('modal-invite').style.display = 'none'; } },
    declineInvite: () => { if(state.pendingInviteId) { net.sendText("/invite decline " + state.pendingInviteId); state.pendingInviteId = null; document.getElementById('modal-invite').style.display = 'none'; } },
    toggleLogs: (show) => { document.getElementById('main-view-chat').style.display = show ? 'none' : 'flex'; document.getElementById('main-view-logs').style.display = show ? 'flex' : 'none'; },
    searchGiphy: () => {
        const q = document.getElementById('giphy-search').value;
        fetch(`https://api.giphy.com/v1/gifs/search?api_key=NpCRJe4i5UivlUS5Vue1yk4PbOytBcno&q=${q}&limit=9`)
            .then(r => r.json()).then(d => {
            const res = document.getElementById('giphy-results'); res.innerHTML = "";
            d.data.forEach(gif => {
                const img = document.createElement('img'); img.style.height = "80px"; img.style.objectFit = "cover"; img.style.cursor = "pointer"; img.src = gif.images.fixed_height_small.url;
                img.onclick = () => { fetch(gif.images.fixed_height.url).then(r=>r.blob()).then(b=>{ const r = new FileReader(); r.onload = () => { state.ws.send(`IMG:${state.nick}:giphy.gif:${r.result.split(',')[1]}`); ui.closeModals(); }; r.readAsDataURL(b); }); };
                res.appendChild(img);
            });
        });
    }
};

const chat = {
    join: (r) => net.sendText("/join " + r),
    createRoom: () => { const r = prompt("Název místnosti:"); if(r) net.sendText("/create " + r); },
    createTempRoom: () => { const r = prompt("Název dočasné místnosti:"); if(r) net.sendText("/temproom " + r); },
    createPrivateRoom: () => { const r = prompt("Název SOUKROMÉ místnosti:"); if(r) net.sendText("/createprivate " + r); },
    send: () => {
        const input = document.getElementById('msg-input');
        let txt = input.value.trim();
        if(!txt) return;

        // LIMIT ZNAKŮ
        if (txt.length > 1000) {
            ui.showAlert("Zpráva je příliš dlouhá! (Maximum je 1000 znaků).", true);
            return;
        }

        input.value = "";

        if (state.privateTarget && !txt.startsWith("/")) {
            net.sendText(`/w ${state.privateTarget} ${txt}`);
        } else if (txt.startsWith("/")) {
            if (txt.toLowerCase().startsWith("/burn ")) {
                let parts = txt.split(" ");
                if (parts.length >= 3) {
                    let secretText = parts.slice(2).join(" ");
                    let encryptedMsg = cryptoAES.encrypt(secretText, state.currentRoom);
                    if (encryptedMsg) net.sendText(parts[0] + " " + parts[1] + " ZK:" + encryptedMsg);
                } else { net.sendText(txt); }
            } else { net.sendText(txt); }
        } else {
            let encryptedMsg = cryptoAES.encrypt(txt, state.currentRoom);
            if (encryptedMsg) net.sendText("ZK:" + encryptedMsg);
        }
    },
    startTicTacToe: () => {
        const name = document.getElementById('game-opponent-name').value.trim();
        if (!name) { ui.showAlert("Musíš zadat jméno soupeře!"); return; }
        if (name.toLowerCase() === state.nick.toLowerCase()) { ui.showAlert("Nemůžeš vyzvat sám sebe!"); return; }
        net.sendText(`/ttt start ${name}`);
        document.getElementById('game-opponent-name').value = "";
        ui.closeModals();
    },
    startWhiteboard: () => {
        const name = document.getElementById('game-opponent-name').value.trim();
        if (!name) net.sendText(`/wb room`);
        else {
            if (name.toLowerCase() === state.nick.toLowerCase()) { ui.showAlert("Nech pole prázdné pro volné plátno!"); return; }
            net.sendText(`/wb start ${name}`);
        }
        document.getElementById('game-opponent-name').value = "";
        ui.closeModals();
    },
    uploadFile: () => { const f = document.getElementById('file-input').files[0]; if(f) chat.uploadFileObj(f); },
    uploadFileObj: (f) => {
        if(f.size > 5 * 1024 * 1024) { ui.showAlert("Soubor je příliš velký (Max 5MB)"); return; }
        const r = new FileReader();
        r.onload = (e) => {
            const prefix = f.type.startsWith('image/') ? 'IMG' : 'FILE';
            state.ws.send(`${prefix}:${state.nick}:${f.name}:${e.target.result.split(',')[1]}`);
        };
        r.readAsDataURL(f);
    }
};

const renderer = {
    renderWhiteboard: (id, p1, p2) => {
        if (document.getElementById(`wb-container-${id}`)) return;

        // Dynamický titulek podle toho, kdo hraje
        const titleText = (p2 === "ROOM") ? `🎨 Volné plátno (od: ${p1})` : `🎨 Plátno: ${p1} & ${p2}`;

        const inlineHtml = `
        <div id="wb-container-${id}" style="width: 100%; display: flex; justify-content: center; margin-top: 16px;">
            <div style="background: #2b2d31; padding: 16px; border-radius: 8px; border: 1px solid #4f545c; text-align: center; box-shadow: 0 4px 15px rgba(0,0,0,0.3);">
                <div style="color: #f2f3f5; font-weight: bold; margin-bottom: 12px; font-size: 16px;">
                    ${titleText}
                </div>
                <div style="background: white; border-radius: 4px; border: 1px solid #ccc; display: inline-block;">
                    <canvas id="wb-canvas-${id}" width="500" height="350" style="cursor: crosshair; touch-action: none; display: block;" data-color="#000000"></canvas>
                </div>
                <div style="margin-top: 12px; display: flex; gap: 8px; justify-content: center; flex-wrap: wrap;">
                    <button onclick="document.getElementById('wb-canvas-${id}').dataset.color = '#000000'" style="background: #000000; color: white; border: 2px solid #555; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-weight: bold;">Černá</button>
                    <button onclick="document.getElementById('wb-canvas-${id}').dataset.color = '#0000FF'" style="background: #0000FF; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-weight: bold;">Modrá</button>
                    <button onclick="document.getElementById('wb-canvas-${id}').dataset.color = '#FF0000'" style="background: #FF0000; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-weight: bold;">Červená</button>
                    <button onclick="document.getElementById('wb-canvas-${id}').dataset.color = '#FFFFFF'" style="background: #f2f3f5; color: black; border: 1px solid #ccc; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-weight: bold;">Guma</button>
                    <div style="width: 1px; background: #4f545c; margin: 0 4px;"></div>
                    <button onclick="renderer.clearWhiteboard('${id}'); net.sendText('GAME:WB:CLEAR:${id}');" style="background: #faa61a; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-weight: bold;">Vymazat</button>
                    <button onclick="renderer.closeWhiteboard('${id}'); net.sendText('GAME:WB:CLOSE:${id}');" style="background: #ed4245; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-weight: bold;">Zavřít</button>
                </div>
            </div>
        </div>`;

        const box = document.getElementById('messages-box');
        box.insertAdjacentHTML('beforeend', inlineHtml);
        box.scrollTop = box.scrollHeight;

        const canvas = document.getElementById(`wb-canvas-${id}`);
        const ctxCanvas = canvas.getContext('2d');
        let isDrawing = false;

        let lastPt = null;
        let lastNetPt = null;
        let lastSendTime = 0;

        const getPos = (e) => {
            const rect = canvas.getBoundingClientRect();
            return { x: Math.round(e.clientX - rect.left), y: Math.round(e.clientY - rect.top) };
        };

        const startDrawing = (e) => {
            isDrawing = true;
            lastPt = getPos(e);
            lastNetPt = getPos(e);
        };

        const stopDrawing = () => { isDrawing = false; ctxCanvas.beginPath(); };

        const draw = (e) => {
            if (!isDrawing || !lastPt) return;
            const pt = getPos(e);
            const color = canvas.dataset.color || '#000000';

            ctxCanvas.lineWidth = color === '#FFFFFF' ? 12 : 3;
            ctxCanvas.lineCap = 'round';
            ctxCanvas.strokeStyle = color;

            ctxCanvas.beginPath();
            ctxCanvas.moveTo(lastPt.x, lastPt.y);
            ctxCanvas.lineTo(pt.x, pt.y);
            ctxCanvas.stroke();

            const now = Date.now();
            if (now - lastSendTime > 40) {
                // Přesný formát Javy: id:x1:y1:x2:y2:color
                net.sendText(`GAME:WB:DRAW:${id}:${lastNetPt.x}:${lastNetPt.y}:${pt.x}:${pt.y}:${color}`);
                lastSendTime = now;
                lastNetPt = pt;
            }
            lastPt = pt;
        };

        canvas.addEventListener('mousedown', startDrawing);
        canvas.addEventListener('mousemove', draw);
        canvas.addEventListener('mouseup', stopDrawing);
        canvas.addEventListener('mouseout', stopDrawing);
    },

    drawWhiteboard: (data) => {
        // Příjem přesného formátu: id:x1:y1:x2:y2:color
        const parts = data.split(":");
        if (parts.length < 6) return;

        const id = parts[0];
        const canvas = document.getElementById(`wb-canvas-${id}`);
        if (!canvas) return;
        const ctxCanvas = canvas.getContext('2d');

        const x1 = parseFloat(parts[1]);
        const y1 = parseFloat(parts[2]);
        const x2 = parseFloat(parts[3]);
        const y2 = parseFloat(parts[4]);
        const color = parts[5];

        ctxCanvas.lineWidth = color === '#FFFFFF' ? 12 : 3;
        ctxCanvas.lineCap = 'round';
        ctxCanvas.strokeStyle = color;

        ctxCanvas.beginPath();
        ctxCanvas.moveTo(x1, y1);
        ctxCanvas.lineTo(x2, y2);
        ctxCanvas.stroke();
    },

    clearWhiteboard: (id) => {
        const canvas = document.getElementById(`wb-canvas-${id}`);
        if (canvas) canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
    },

    closeWhiteboard: (id) => {
        const container = document.getElementById(`wb-container-${id}`);
        if (container) {
            container.style.opacity = "0.5";
            container.style.pointerEvents = "none";
            const header = container.querySelector('div > div');
            if (header) header.innerText = "🎨 Společné plátno (Uzavřeno)";
        }
    },
    renderGame: (data) => {
        let p = data.split(":");
        if (p.length < 6) return;

        let gameId = `game-${p[0]}`;
        let p1 = p[1];
        let p2 = p[2];
        let turn = p[3];
        let board = p[4];
        let status = p[5];

        let container = document.getElementById(gameId);
        let isNew = false;

        if (!container) {
            container = document.createElement('div');
            container.id = gameId;
            container.setAttribute("oncontextmenu", "event.stopPropagation(); return false;");
            container.style = "background:#2b2d31; padding:15px; border-radius:8px; margin-top:10px; width:fit-content; border:1px solid #1e1f22;";
            isNew = true;
        }

        let html = `<div style="color:white; font-weight:bold; margin-bottom:10px; text-align:center; font-size:16px;">
            🎮 ${p1} <span style="color:#5865F2">(X)</span> vs ${p2} <span style="color:#ed4245">(O)</span>
        </div>`;

        if (status === 'PLAYING') {
            html += `<div style="color:#faa61a; text-align:center; margin-bottom:15px; font-weight:600;">Na tahu: ${turn}</div>`;
        } else if (status === 'WIN1') {
            html += `<div style="color:#46ff50; text-align:center; margin-bottom:15px; font-weight:600;">🏆 Vítěz: ${p1}!</div>`;
        } else if (status === 'WIN2') {
            html += `<div style="color:#46ff50; text-align:center; margin-bottom:15px; font-weight:600;">🏆 Vítěz: ${p2}!</div>`;
        } else if (status === 'DRAW') {
            html += `<div style="color:gray; text-align:center; margin-bottom:15px; font-weight:600;">🤝 Remíza!</div>`;
        }

        html += `<div style="display:grid; grid-template-columns:repeat(3, 60px); gap:5px; justify-content:center;">`;

        for(let i = 0; i < 9; i++) {
            let r = Math.floor(i / 3);
            let c = i % 3;
            let val = board[i];
            let color = val === 'X' ? '#5865F2' : (val === 'O' ? '#ed4245' : '#4f545c');
            let char = val === '-' ? '' : val;
            let dis = (status !== 'PLAYING' || val !== '-') ? 'disabled' : '';
            let cursor = dis ? 'default' : 'pointer';

            html += `<button ${dis} onclick="net.sendText('/ttt tah ${r} ${c}')" style="width:60px; height:60px; background:#1e1f22; border:1px solid #313338; border-radius:8px; color:${color}; font-size:28px; font-weight:bold; cursor:${cursor}; transition:0.2s;">${char}</button>`;
        }

        html += `</div>`;
        container.innerHTML = html;

        if (isNew) {
            let box = document.getElementById('messages-box');
            let wrapper = document.createElement('div');
            wrapper.setAttribute("oncontextmenu", "event.stopPropagation(); return false;");
            wrapper.style = "display:flex; justify-content:center;";
            wrapper.appendChild(container);
            box.appendChild(wrapper);
            box.scrollTop = box.scrollHeight;
        }
    },
    renderBurnMessage: (raw) => {
        let cleanMsg = raw.replace(/^BURN:/, ""); let parts = cleanMsg.split(":"); if (parts.length < 3) return;
        let id = parts[0].trim(), sender = parts[1].trim(), text = parts[2]; let seconds = parts.length > 3 ? parseInt(parts[3]) : 10;
        const time = new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});

        if (sender !== "SYSTEM" && text.startsWith("ZK:")) { let dec = cryptoAES.decrypt(text.substring(3), state.currentRoom); text = dec ? dec : "🔒 [Šifrováno]"; }
        if (text === "[SKRYTÁ ZPRÁVA]") renderer.addBubble(id, sender, { text: "Zpráva byla smazána.", expired: true }, time, 'burn');
        else renderer.addBubble(id, sender, { text: text, seconds: seconds, expired: false }, time, 'burn');
    },
    renderMessage: (raw) => {
        let id = "0", sender = "Neznámý", text = ""; let time = new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
        let firstColonIndex = raw.indexOf(":"); if (firstColonIndex === -1) return;
        let header = raw.substring(0, firstColonIndex).trim(); let restOfMsg = raw.substring(firstColonIndex + 1); let headerParts = header.split(" ");

        if (headerParts.length >= 3 && !isNaN(headerParts[0]) && headerParts[1].includes(":")) { id = headerParts[0]; time = headerParts[1]; sender = headerParts.slice(2).join(" "); text = restOfMsg; }
        else if (headerParts.length >= 2 && !isNaN(headerParts[0]) && !headerParts[1].includes(":")) { id = headerParts[0]; sender = headerParts.slice(1).join(" "); text = restOfMsg; }
        else { const parts = raw.split(":"); if (!isNaN(parts[0]) && parts.length >= 3) { id = parts[0].trim(); sender = parts[1].trim(); text = parts.slice(2).join(":"); } else { sender = header; text = restOfMsg; } }

        if (sender !== "SYSTEM" && text.startsWith("ZK:")) {
            let decrypted = cryptoAES.decrypt(text.substring(3), state.currentRoom);
            text = decrypted ? decrypted : "🔒 [Šifrovaná zpráva - nelze dešifrovat]";
        }

        if (sender === "SYSTEM" && text.startsWith("GAME:TTT:")) { renderer.renderGame(text.substring(9)); return; }
        if (sender === "SYSTEM" && text.startsWith("GAME:WB:START:")) { renderer.renderWhiteboard(text.substring(14)); return; }
        if (sender === "SYSTEM" && text.startsWith("INVITE:RECEIVE:")) { let invParts = text.split(":"); state.pendingInviteId = invParts[2]; document.getElementById('invite-text').innerHTML = `Hráč <b>${invParts[3]}</b> tě vyzval na hru.<br>Máš 30 sekund na přijetí.`; document.getElementById('modal-invite').style.display = 'flex'; return; }
        if (sender === "SYSTEM" && text.startsWith("INVITE:CANCEL:")) { if (state.pendingInviteId === text.split(":")[2]) { state.pendingInviteId = null; document.getElementById('modal-invite').style.display = 'none'; } return; }
        if (sender === "SYSTEM") setTimeout(() => net.sendText("/users"), 500);
        if (sender !== "SYSTEM" && sender !== "MSG" && sender !== "HIST" && sender !== "CHAT") ui.addUserToSidebar(sender, "");
        if (sender !== state.nick && sender !== "SYSTEM") utils.playNotification();

        renderer.addBubble(id, sender, text, time, 'text');
    },
    renderFile: (raw) => {
        const isImage = raw.startsWith("IMG:"); const cleanMsg = raw.replace(/^(IMG|FILE):/, ""); const parts = cleanMsg.split(":");
        let id = "0", sender, filename, data;
        if (!isNaN(parts[0]) && parts.length >= 4) { id = parts[0].trim(); sender = parts[1].trim(); filename = parts[2]; data = parts.slice(3).join(":"); } else { sender = parts[0].trim(); filename = parts[1]; data = parts.slice(2).join(":"); }
        if (sender !== state.nick && sender !== "SYSTEM") utils.playNotification();
        renderer.addBubble(id, sender, {name: filename, data: data}, new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}), isImage ? 'image' : 'file');
    },
    addBubble: (id, sender, content, time, type) => {
        const box = document.getElementById('messages-box'); const colors = ['#5865F2', '#3ba55d', '#faa61a', '#ed4245', '#eb459e'];
        const avatarBg = sender === 'SYSTEM' ? '#313338' : colors[sender.length % colors.length]; const avatarLetter = sender === 'SYSTEM' ? '⚙️' : sender.charAt(0).toUpperCase();
        let safeContent = ""; const isWhisper = type === 'text' && (content.includes("🕵️") || content.includes("[WHISPER]"));
        if (type === 'text') safeContent = utils.markdown(content);
        else if (type === 'image') safeContent = `<img src="data:image/png;base64,${content.data}" style="max-width: 350px; max-height: 350px; border-radius: 8px; margin-top: 5px; cursor: pointer;" onclick="window.open(this.src)">`;
        else if (type === 'file') safeContent = `<div style="background:#2b2d31; padding:12px; border-radius:6px; display:inline-flex; align-items:center;"><a href="data:application/octet-stream;base64,${content.data}" download="${content.name}" style="color:#00a8fc;">Stáhnout ${content.name}</a></div>`;
        else if (type === 'burn') {
            if (content.expired) safeContent = `<div style="color:#ed4245; font-style:italic;">🔥 ${content.text}</div>`;
            else { let encodedText = btoa(encodeURIComponent(content.text)); safeContent = `<div id="burn-box-${id}" style="background:#2b2d31; padding:12px; border-radius:6px; border:1px solid #ed4245;"><button id="burn-btn-${id}" onclick="ui.igniteBurn('${id}', '${encodedText}', ${content.seconds})" style="background:#ed4245; color:white; border:none; padding:8px 16px; border-radius:4px; cursor:pointer;">Zobrazit zprávu (${content.seconds}s)</button><div id="burn-text-${id}" style="display:none; color:white; margin-top:5px;"></div></div>`; }
        }
        const msgHtml = `<div id="msg-${id}" oncontextmenu="ctx.showMsg(event, '${id}', '${sender}')" style="display:flex; gap:16px; padding:2px 0; margin-top:16px; ${isWhisper ? 'background:rgba(142,36,170,0.15); border-left:3px solid #b046ff; padding-left:10px;' : ''}">
            <div style="width:40px; height:40px; border-radius:50%; background:${avatarBg}; display:flex; align-items:center; justify-content:center; color:white; font-weight:bold; font-size:18px; flex-shrink:0; cursor:pointer;" onclick="ctx.showSide(event, '${sender}')">${avatarLetter}</div>
            <div style="display:flex; flex-direction:column; max-width:calc(100% - 56px); min-width:0;">
                <div style="display:flex; align-items:baseline; gap:8px; margin-bottom:4px;">
                    <span style="color:${isWhisper ? '#b046ff' : (sender==='SYSTEM'?'#949ba4':'#f2f3f5')}; font-weight:500; cursor:pointer;" onclick="ctx.showSide(event, '${sender}')">${sender}</span>
                    <span style="color:#949ba4; font-size:12px;">${time}</span>
                </div>
                <div style="color:${sender==='SYSTEM'?'#949ba4':'#dbdee1'}; font-size:16px; line-height:1.5; overflow-wrap:anywhere; word-break:break-word; white-space:pre-wrap;">${safeContent}</div>
            </div>
        </div>`;
        box.insertAdjacentHTML('beforeend', msgHtml); box.scrollTop = box.scrollHeight;
    }
};

const utils = {
    playNotification: () => { if (!state.isWindowActive && appConfig.sound) { const a = document.getElementById('notify-sound'); if(a) a.play().catch(()=>{}); } },
    markdown: (text) => { return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/\*\*(.*?)\*\*/g, '<b style="color:#fff;">$1</b>').replace(/\*(.*?)\*/g, '<i>$1</i>').replace(/`(.*?)`/g, '<span style="background:#1e1f22; padding:3px 5px; border-radius:4px; font-family:monospace;">$1</span>').replace(/(https?:\/\/[^\s]+)/g, '<a href="$1" target="_blank" style="color:#00a8fc;">$1</a>'); }
};

const admin = { kick: (u) => { const r = prompt("Důvod?", "Porušení pravidel"); if(r) net.sendText(`/kick ${u} ${r}`); }, mute: (u) => { const d = prompt("Sekundy (-1 navždy):", "60"); if(d) net.sendText(`/mute ${u} ${d}`); }, banModal: (u) => { state.targetUser = u; document.getElementById('ban-target-name').innerText = u; document.getElementById('modal-ban').style.display = 'flex'; }, confirmBan: () => { const t = document.getElementById('ban-duration').value || 60; const r = document.getElementById('ban-reason').value; net.sendText(`/ban ${state.targetUser} ${t} ${r}`); ui.closeModals(); }, deleteRoom: (r) => { if(confirm("Smazat?")) net.sendText("/deleteroom "+r); }, broadcastPrompt: () => { const m = prompt("Oznámení:"); if(m) net.sendText("/broadcast " + m); } };
const ctx = { menu: document.getElementById('context-menu'), showSide: (e, u) => { if(!ctx.menu || u === state.nick || u === "SYSTEM") return; state.activeContextUser = u; state.activeMessageId = null; ctx.menu.style.display = 'block'; ctx.menu.style.left = e.pageX + "px"; ctx.menu.style.top = e.pageY + "px"; }, showMsg: (e, id, u) => { e.preventDefault(); if(!ctx.menu) return; state.activeContextUser = u; state.activeMessageId = id; ctx.menu.style.display = 'block'; ctx.menu.style.left = e.pageX + "px"; ctx.menu.style.top = e.pageY + "px"; }, hide: () => { if(ctx.menu) ctx.menu.style.display = 'none'; }, copyNick: () => { navigator.clipboard.writeText(state.activeContextUser); ctx.hide(); }, mention: () => { const i = document.getElementById('msg-input'); i.value += `@${state.activeContextUser} `; i.focus(); ctx.hide(); }, whisper: () => { state.privateTarget = state.activeContextUser; document.getElementById('private-mode-panel').style.display = 'flex'; document.getElementById('private-mode-label').innerText = `🔒 Šeptáš: ${state.privateTarget}`; ctx.hide(); }, inviteToGame: () => { net.sendText(`/ttt start ${state.activeContextUser}`); ctx.hide(); }, roomInvite: () => { net.sendText(`/roominvite ${state.activeContextUser}`); ctx.hide(); }, deleteMsg: () => { if(state.activeMessageId && confirm("Smazat?")) net.sendText("/delmsg " + state.activeMessageId); ctx.hide(); }, kick: () => { admin.kick(state.activeContextUser); ctx.hide(); }, mute: () => { admin.mute(state.activeContextUser); ctx.hide(); }, ban: () => { admin.banModal(state.activeContextUser); ctx.hide(); } };

document.addEventListener('click', () => ctx.hide());
['inp-ip', 'inp-user', 'inp-pass'].forEach(id => { let e = document.getElementById(id); if(e) e.addEventListener('keyup', (ev) => { if(ev.key === 'Enter') auth.login(); }); });
['reg-ip', 'reg-user', 'reg-pass', 'reg-code'].forEach(id => { let e = document.getElementById(id); if(e) e.addEventListener('keyup', (ev) => { if(ev.key === 'Enter') auth.register(); }); });

document.getElementById('msg-input').addEventListener("keydown", (e) => {
    if(e.key === "Enter" && appConfig.enterToSend) {
        e.preventDefault();
        chat.send();
    }
});