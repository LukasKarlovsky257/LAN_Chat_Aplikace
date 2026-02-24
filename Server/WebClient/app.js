/* =========================================
   LAN CHAT ULTIMATE - CORE LOGIC v5.5 (AJAX GAMES FIX)
   ========================================= */

if (!document.getElementById('auth-screen')) {
    console.log("Not on app page, script idle.");
    throw new Error("Script stopped explicitly for landing page safety.");
}

const state = {
    ws: null,
    nick: "",
    isAdmin: false,
    currentRoom: "Lobby",
    activeContextUser: null,
    activeMessageId: null,
    privateTarget: null,
    users: [],
    targetUser: "",
    isWindowActive: true,
    serverPublicKey: null,
    encryptor: new JSEncrypt()
};

window.onfocus = () => {
    state.isWindowActive = true;
};

window.onblur = () => {
    state.isWindowActive = false;
};

// --- GLOBAL MODAL CLICK-TO-CLOSE ---
window.addEventListener('click', (event) => {
    if (event.target.classList.contains('modal')) {
        ui.closeModals();
    }
});

// --- 1. NETWORK & AUTH MODULE ---
const net = {
    sendText: (text) => {
        if (!state.ws || state.ws.readyState !== 1) {
            console.error("[DEBUG - NET] ❌ Nelze odeslat, WebSocket NENÍ připojen! Text:", text);
            return;
        }

        console.log(`[DEBUG - NET] 📤 Odesílám: "${text}"`);

        if (state.serverPublicKey) {
            const enc = state.encryptor.encrypt(text);
            if (enc) {
                state.ws.send("ENC:" + enc);
            } else {
                alert("Chyba šifrování! Zpráva je možná příliš dlouhá.");
            }
        } else {
            state.ws.send(text);
        }
    }
};

const auth = {
    connect: (rawIp) => {
        if (state.ws) {
            state.ws.close();
        }
        document.getElementById('auth-status').innerText = "Navazuji spojení...";

        try {
            let ip = rawIp.replace("http://", "").replace("https://", "").replace("ws://", "").replace("wss://", "").trim();
            let url = "";

            if (ip.includes("ngrok")) {
                if (ip.includes("tcp") && ip.includes(":")) {
                    url = `wss://${ip}`;
                } else {
                    let hostOnly = ip.split(":")[0];
                    url = `wss://${hostOnly}`;
                }
            } else {
                if (!ip.includes(":")) {
                    url = `ws://${ip}:8887`;
                } else {
                    url = `ws://${ip}`;
                }
            }

            state.ws = new WebSocket(url);

            state.ws.onopen = () => {
                console.log("[DEBUG - AUTH] ✅ WebSocket spojení navázáno.");
                document.getElementById('auth-status').innerText = "Spojení navázáno! Ověřování...";
            };

            state.ws.onmessage = (e) => {
                protocol.handle(e.data);
            };

            state.ws.onerror = (e) => {
                document.getElementById('auth-status').innerText = "Chyba připojení. Zkontroluj adresu.";
            };

            state.ws.onclose = (event) => {
                document.getElementById('app-screen').style.display = 'none';
                document.getElementById('auth-screen').style.display = 'flex';
                let duvod = event.reason ? event.reason : "Spojení se serverem bylo ztraceno.";
                document.getElementById('auth-status').innerText = "❌ " + duvod;
                document.getElementById('messages-box').innerHTML = "";
            };

        } catch (err) {
            document.getElementById('auth-status').innerText = "Neplatný formát adresy.";
        }
    },

    login: () => {
        const ip = document.getElementById('inp-ip').value || 'localhost';
        const u = document.getElementById('inp-user').value;
        const p = document.getElementById('inp-pass').value;

        if (!u || !p) {
            alert("Vyplň jméno a heslo!");
            return;
        }

        auth.connect(ip);
        setTimeout(() => {
            net.sendText(`LOGIN:${u}:${p}`);
        }, 1500);
    },

    register: () => {
        const ip = document.getElementById('reg-ip').value || 'localhost';
        const u = document.getElementById('reg-user').value;
        const p = document.getElementById('reg-pass').value;
        const c = document.getElementById('reg-code').value;

        if (!u || !p || !c) {
            alert("Vyplň všechna pole!");
            return;
        }

        auth.connect(ip);
        setTimeout(() => {
            net.sendText(`REGISTER:${u}:${p}:${c}`);
        }, 1500);
    }
};

// --- 2. PROTOCOL HANDLER ---
const protocol = {
    handle: (msg) => {
        console.log(`[DEBUG - RAW INCOMING] 📥 Ze serveru dorazilo: ${msg}`);

        if (msg.startsWith("PUBKEY:")) {
            state.serverPublicKey = msg.split("PUBKEY:")[1];
            state.encryptor.setPublicKey(state.serverPublicKey);
            return;
        }

        if (msg.startsWith("LOGIN_OK:")) {
            const parts = msg.split(":");
            state.nick = parts[1];
            state.isAdmin = (parts[2] === "true");
            ui.initApp();
        }
        else if (msg === "REGISTER_OK") {
            alert("Účet vytvořen! Nyní se přihlas.");
            ui.switchAuth('login');
        }
        else if (msg.startsWith("ROOM_CHANGED:")) {
            ui.updateRoomUI(msg.split(":")[1]);
        }
        else if (msg.startsWith("ROOM_LIST:")) {
            ui.renderRoomList(msg.substring(10).split(","));
        }
        else if (msg.startsWith("USERS:")) {
            const usersList = msg.substring(6).split(",");
            document.getElementById('user-list').innerHTML = "";
            state.users = [];

            usersList.forEach(u => {
                if (u.trim() !== "" && u !== "SYSTEM") {
                    let parts = u.split("|");
                    let name = parts[0].trim();
                    let lvl = parts.length > 1 ? parts[1].trim() : "";
                    ui.addUserToSidebar(name, lvl);
                }
            });
        }
        else if (msg.startsWith("MSG:") || msg.startsWith("CHAT:") || msg.startsWith("HIST:") || msg.startsWith("BURN:") || msg.startsWith("GAME:")) {
            let cleanMsg = msg;

            // Očištění obálek historie a hrubého chatu
            if (cleanMsg.startsWith("HIST:")) {
                cleanMsg = cleanMsg.substring(5);
            }
            if (cleanMsg.startsWith("CHAT:")) {
                cleanMsg = cleanMsg.substring(5);
            }

            // Rozřazení do renderovacích funkcí
            if (cleanMsg.startsWith("IMG:") || cleanMsg.startsWith("FILE:")) {
                renderer.renderFile(cleanMsg);
            } else if (cleanMsg.startsWith("BURN:")) {
                renderer.renderBurnMessage(cleanMsg);
            } else {
                // Klasické textové zprávy, které nyní mohou obsahovat i hry (MSG:0:SYSTEM:GAME:...)
                if (cleanMsg.startsWith("MSG:")) {
                    cleanMsg = cleanMsg.substring(4);
                }
                renderer.renderMessage(cleanMsg);
            }
        }
        else if (msg.startsWith("IMG:") || msg.startsWith("FILE:")) {
            renderer.renderFile(msg);
        }
        else if (msg.startsWith("DELETE_MSG:")) {
            const idToDel = msg.split(":")[1].trim();
            const msgEl = document.getElementById(`msg-${idToDel}`);
            if (msgEl) {
                msgEl.style.display = 'none';
            }
        }
        else if (msg.startsWith("ERROR:")) {
            const errText = msg.split(":")[1];
            document.getElementById('auth-status').innerText = errText;
            alert(errText);
        }
    }
};

// --- 3. UI CONTROLLER ---
const ui = {
    switchAuth: (tab) => {
        document.getElementById('form-login').style.display = tab === 'login' ? 'block' : 'none';
        document.getElementById('form-register').style.display = tab === 'register' ? 'block' : 'none';
        document.getElementById('tab-login').classList.toggle('active', tab === 'login');
        document.getElementById('tab-register').classList.toggle('active', tab === 'register');
    },

    initApp: () => {
        document.getElementById('auth-screen').style.display = 'none';
        document.getElementById('app-screen').style.display = 'flex';

        document.getElementById('my-nick').innerText = state.nick;
        document.getElementById('my-role').innerText = state.isAdmin ? "ADMIN" : "#USER";

        if (state.isAdmin) {
            document.getElementById('my-role').style.color = '#ed4245';
            document.getElementById('admin-logs-trigger').style.display = 'block';
        }

        document.getElementById('my-avatar').innerText = state.nick.charAt(0).toUpperCase();

        chat.join('Lobby');
        setTimeout(() => { net.sendText("/users"); }, 300);

        document.getElementById('msg-input').addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && state.privateTarget) {
                ui.cancelWhisper();
            }
        });

        ui.initDragDrop();
    },

    openActionModal: () => {
        document.getElementById('modal-actions').style.display = 'flex';
    },

    cancelWhisper: () => {
        state.privateTarget = null;
        document.getElementById('private-mode-panel').style.display = 'none';
        const inp = document.getElementById('msg-input');
        inp.placeholder = `Poslat zprávu do #${state.currentRoom}...`;
        inp.focus();
    },

    initDragDrop: () => {
        const overlay = document.getElementById('drag-overlay');
        const body = document.body;

        body.addEventListener('dragenter', () => overlay.style.display = 'flex');
        overlay.addEventListener('dragleave', () => overlay.style.display = 'none');
        overlay.addEventListener('drop', (e) => {
            e.preventDefault();
            overlay.style.display = 'none';
            if(e.dataTransfer.files.length > 0) {
                chat.uploadFileObj(e.dataTransfer.files[0]);
            }
        });
        window.addEventListener('dragover', (e) => e.preventDefault());
    },

    updateRoomUI: (roomName) => {
        state.currentRoom = roomName;
        document.getElementById('current-room').innerText = roomName;
        document.getElementById('messages-box').innerHTML = "";
        document.getElementById('user-list').innerHTML = "";
        state.users = [];
        document.getElementById('user-count').innerText = "0";
        ui.cancelWhisper();
    },

    renderRoomList: (rooms) => {
        const list = document.getElementById('room-list');
        list.innerHTML = "";

        rooms.forEach(r => {
            let parts = r.split("|");
            let cleanName = parts[0];
            let isTemp = parts.length > 1 && parts[1] === "1";

            const li = document.createElement('li');
            li.innerHTML = isTemp
                ? `<span class="material-icons" style="font-size:16px; color:#faa61a;">timer</span> <span style="color:#faa61a">${cleanName}</span>`
                : `<span class="material-icons" style="font-size:16px;">tag</span> ${cleanName}`;

            li.onclick = () => chat.join(cleanName);

            if (state.isAdmin && cleanName !== 'Lobby') {
                const delBtn = document.createElement('span');
                delBtn.className = "material-icons";
                delBtn.style.marginLeft = "auto";
                delBtn.style.fontSize = "14px";
                delBtn.style.opacity = "0.5";
                delBtn.innerText = "close";
                delBtn.onclick = (e) => {
                    e.stopPropagation();
                    admin.deleteRoom(cleanName);
                };
                li.appendChild(delBtn);
            }
            list.appendChild(li);
        });
    },

    addUserToSidebar: (username, levelStr) => {
        if (state.users.includes(username) || username === "SYSTEM") {
            return;
        }

        state.users.push(username);
        document.getElementById('user-count').innerText = state.users.length;

        const list = document.getElementById('user-list');
        const li = document.createElement('li');
        const color = "#5865F2";

        let lvlHtml = levelStr ? `<span class="level-badge" style="background:#faa61a; color:#fff; font-size:10px; font-weight:bold; padding:2px 6px; border-radius:12px; margin-left:auto;">${levelStr}</span>` : "";

        li.innerHTML = `<div class="avatar" style="background:${color}; width:32px; height:32px; font-size:14px; color:white; display:flex; justify-content:center; align-items:center; border-radius:50%; font-weight:bold;">${username.charAt(0).toUpperCase()}</div>
                        <span style="font-weight:600; margin-left:10px">${username}</span> ${lvlHtml}`;

        li.oncontextmenu = (e) => {
            e.preventDefault();
            ctx.showSide(e, username);
        };
        list.appendChild(li);
    },

    igniteBurn: (id, encodedText, seconds) => {
        const btn = document.getElementById(`burn-btn-${id}`);
        const textBox = document.getElementById(`burn-text-${id}`);

        if(!btn || !textBox) return;

        btn.style.display = 'none';
        textBox.style.display = 'block';

        let text = decodeURIComponent(atob(encodedText));
        textBox.innerText = text;

        net.sendText("START_TIMER:" + id);

        let timeLeft = seconds;
        textBox.innerText = `${text} (Smaže se za ${timeLeft}s)`;

        const interval = setInterval(() => {
            timeLeft--;
            if (timeLeft > 0) {
                textBox.innerText = `${text} (Smaže se za ${timeLeft}s)`;
            } else {
                clearInterval(interval);
                const msgEl = document.getElementById(`msg-${id}`);
                if (msgEl) {
                    msgEl.style.display = 'none';
                }
            }
        }, 1000);
    },

    openGiphy: () => document.getElementById('modal-giphy').style.display = 'flex',
    openGamesDialog: () => document.getElementById('modal-games').style.display = 'flex',

    closeModals: () => {
        document.querySelectorAll('.modal').forEach(m => m.style.display = 'none');
    },

    toggleLogs: (show) => {
        document.getElementById('main-view-chat').style.display = show ? 'none' : 'flex';
        document.getElementById('main-view-logs').style.display = show ? 'flex' : 'none';
    },

    searchGiphy: () => {
        const q = document.getElementById('giphy-search').value;
        const apiKey = "NpCRJe4i5UivlUS5Vue1yk4PbOytBcno";
        fetch(`https://api.giphy.com/v1/gifs/search?api_key=${apiKey}&q=${q}&limit=9`)
            .then(r => r.json())
            .then(d => {
                const res = document.getElementById('giphy-results');
                res.innerHTML = "";
                d.data.forEach(gif => {
                    const img = document.createElement('img');
                    img.style.height = "80px";
                    img.style.objectFit = "cover";
                    img.style.cursor = "pointer";
                    img.src = gif.images.fixed_height_small.url;

                    img.onclick = () => {
                        fetch(gif.images.original.url).then(r=>r.blob()).then(b=>{
                            const r = new FileReader();
                            r.onload = () => {
                                state.ws.send(`IMG:${state.nick}:giphy.gif:${r.result.split(',')[1]}`);
                                ui.closeModals();
                            };
                            r.readAsDataURL(b);
                        });
                    };
                    res.appendChild(img);
                });
            });
    }
};

// --- 4. CHAT ACTIONS ---
const chat = {
    join: (r) => net.sendText("/join " + r),

    createRoom: () => {
        const r = prompt("Název místnosti:");
        if(r) chat.join(r);
    },

    createTempRoom: () => {
        const r = prompt("Název dočasné místnosti (Smaže se po opuštění):");
        if(r) net.sendText("/temproom " + r);
    },

    send: () => {
        const input = document.getElementById('msg-input');
        let txt = input.value.trim();

        if(!txt) return;

        if (state.privateTarget && !txt.startsWith("/")) {
            txt = `/w ${state.privateTarget} ${txt}`;
        }

        net.sendText(txt.startsWith("/") ? txt : "MSG:" + txt);
        input.value = "";
    },

    startTicTacToe: () => {
        const inp = document.getElementById('game-opponent-name');
        const name = inp.value.trim();

        if (!name) return;

        if (name.toLowerCase() === state.nick.toLowerCase()) {
            alert("Nemůžeš vyzvat sám sebe!");
            return;
        }

        net.sendText(`/ttt start ${name}`);
        inp.value = "";
        ui.closeModals();
    },

    uploadFile: () => {
        const f = document.getElementById('file-input').files[0];
        if(f) {
            chat.uploadFileObj(f);
        }
    },

    uploadFileObj: (f) => {
        if(f.size > 5 * 1024 * 1024) {
            alert("Soubor je příliš velký (Max 5MB)");
            return;
        }

        const reader = new FileReader();
        reader.onload = (e) => {
            const prefix = f.type.startsWith('image/') ? 'IMG' : 'FILE';
            state.ws.send(`${prefix}:${state.nick}:${f.name}:${e.target.result.split(',')[1]}`);
        };
        reader.readAsDataURL(f);
    }
};

// --- 5. RENDERER ---
const renderer = {

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
        let cleanMsg = raw.replace(/^BURN:/, "");
        let parts = cleanMsg.split(":");
        if (parts.length < 3) return;

        let id = parts[0].trim();
        let sender = parts[1].trim();
        let text = parts[2];
        let seconds = parts.length > 3 ? parseInt(parts[3]) : 10;

        const time = new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});

        if (text === "[SKRYTÁ ZPRÁVA]") {
            renderer.addBubble(id, sender, { text: "Zpráva již byla smazána nebo je skrytá.", expired: true }, time, 'burn');
        } else {
            renderer.addBubble(id, sender, { text: text, seconds: seconds, expired: false }, time, 'burn');
        }
    },

    renderMessage: (raw) => {
        const parts = raw.split(":");
        if (parts.length < 2) return;

        let id = "0", sender = "Neznámý", text = "";

        if (!isNaN(parts[0]) && parts.length >= 3) {
            id = parts[0].trim();
            sender = parts[1].trim();
            text = parts.slice(2).join(":");
        } else {
            sender = parts[0].trim();
            text = parts.slice(1).join(":");
        }

        // 🔥 ODCHYCENÍ HRY ODESLANÉ Z BACKENDU JAKO "SYSTEM"
        if (sender === "SYSTEM" && text.startsWith("GAME:TTT:")) {
            renderer.renderGame(text.substring(9));
            return;
        }

        if (sender === "SYSTEM") {
            setTimeout(() => net.sendText("/users"), 500);
        }

        if (sender !== "SYSTEM" && sender !== "MSG" && sender !== "HIST" && sender !== "CHAT") {
            ui.addUserToSidebar(sender, "");
        }
        if (sender !== state.nick && sender !== "SYSTEM") {
            utils.playNotification();
        }

        const time = new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
        renderer.addBubble(id, sender, text, time, 'text');
    },

    renderFile: (raw) => {
        const isImage = raw.startsWith("IMG:");
        const type = isImage ? 'image' : 'file';

        const cleanMsg = raw.replace(/^(IMG|FILE):/, "");
        const parts = cleanMsg.split(":");

        let id = "0", sender, filename, data;

        if (!isNaN(parts[0]) && parts.length >= 4) {
            id = parts[0].trim();
            sender = parts[1].trim();
            filename = parts[2];
            data = parts.slice(3).join(":");
        } else {
            sender = parts[0].trim();
            filename = parts[1];
            data = parts.slice(2).join(":");
        }

        if (sender !== state.nick && sender !== "SYSTEM") {
            utils.playNotification();
        }

        const time = new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
        renderer.addBubble(id, sender, {name: filename, data: data}, time, type);
    },

    addBubble: (id, sender, content, time, type) => {
        const box = document.getElementById('messages-box');
        const colors = ['#5865F2', '#3ba55d', '#faa61a', '#ed4245', '#eb459e'];
        const colorIndex = sender.length % colors.length;
        const avatarBg = sender === 'SYSTEM' ? '#313338' : colors[colorIndex];
        const avatarLetter = sender === 'SYSTEM' ? '⚙️' : sender.charAt(0).toUpperCase();

        let safeContent = "";
        const isWhisper = type === 'text' && (content.includes("🕵️") || content.includes("[WHISPER]"));

        if (type === 'text') {
            safeContent = utils.markdown(content);
        } else if (type === 'image') {
            safeContent = `<img src="data:image/png;base64,${content.data}" style="max-width: 350px; max-height: 350px; border-radius: 8px; margin-top: 5px; cursor: pointer; object-fit: contain; box-shadow: 0 4px 6px rgba(0,0,0,0.1);" onclick="window.open(this.src)">`;
        } else if (type === 'file') {
            safeContent = `
            <div style="background: #2b2d31; padding: 12px; border-radius: 6px; display: inline-flex; align-items: center; border: 1px solid #1e1f22; margin-top: 5px;">
                <span class="material-icons" style="color: #5865F2; font-size: 28px; margin-right: 10px;">insert_drive_file</span>
                <a href="data:application/octet-stream;base64,${content.data}" download="${content.name}" style="color: #00a8fc; text-decoration: none; font-weight: 500;">Stáhnout ${content.name}</a>
            </div>`;
        } else if (type === 'burn') {
            if (content.expired) {
                safeContent = `<div style="color: #ed4245; font-style: italic;">🔥 ${content.text}</div>`;
            } else {
                let encodedText = btoa(encodeURIComponent(content.text));
                safeContent = `
                    <div id="burn-box-${id}" style="background: #2b2d31; padding: 12px; border-radius: 6px; border: 1px solid #ed4245; display: inline-block;">
                        <button id="burn-btn-${id}" onclick="ui.igniteBurn('${id}', '${encodedText}', ${content.seconds})" style="background: #ed4245; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; font-weight: bold; display: flex; align-items: center; gap: 8px;">
                            <span class="material-icons" style="font-size: 18px;">local_fire_department</span> Zobrazit tajnou zprávu (${content.seconds}s)
                        </button>
                        <div id="burn-text-${id}" style="display: none; color: #f2f3f5; font-weight: bold; margin-top: 5px;"></div>
                    </div>
                `;
            }
        }

        const whisperStyle = isWhisper ? "background-color: rgba(142, 36, 170, 0.15); border-left: 3px solid #b046ff; padding-left: 10px;" : "";

        const msgHtml = `
            <div id="msg-${id}" oncontextmenu="ctx.showMsg(event, '${id}', '${sender}')" style="display: flex; gap: 16px; padding: 2px 0; margin-top: 16px; word-break: break-word; transition: 0.2s; ${whisperStyle}">
                <div style="width: 40px; height: 40px; border-radius: 50%; background-color: ${avatarBg}; display: flex; align-items: center; justify-content: center; color: white; font-weight: bold; font-size: 18px; flex-shrink: 0; box-shadow: 0 2px 5px rgba(0,0,0,0.2); cursor: pointer;" onclick="ctx.showSide(event, '${sender}')">
                    ${avatarLetter}
                </div>
                <div style="display: flex; flex-direction: column; max-width: 100%;">
                    <div style="display: flex; align-items: baseline; gap: 8px; margin-bottom: 4px;">
                        <span style="color: ${isWhisper ? '#b046ff' : (sender === 'SYSTEM' ? '#949ba4' : '#f2f3f5')}; font-weight: 500; font-size: 16px; cursor: pointer;" onclick="ctx.showSide(event, '${sender}')">
                            ${sender} ${isWhisper ? ' <i>(Šepot)</i>' : ''}
                        </span>
                        <span style="color: #949ba4; font-size: 12px; font-weight: 400;">${time}</span>
                    </div>
                    <div style="color: ${sender === 'SYSTEM' ? '#949ba4' : '#dbdee1'}; font-size: 16px; line-height: 1.5; ${sender === 'SYSTEM' ? 'font-style: italic;' : ''}">
                        ${safeContent}
                    </div>
                </div>
            </div>
        `;

        box.insertAdjacentHTML('beforeend', msgHtml);
        box.scrollTop = box.scrollHeight;
    }
};

// --- 6. UTILS & ADMIN ---
const utils = {
    playNotification: () => {
        if (!state.isWindowActive) {
            const a = document.getElementById('notify-sound');
            if(a) a.play().catch(()=>{});
        }
    },
    markdown: (text) => {
        return text
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/\*\*(.*?)\*\*/g, '<b style="color: #ffffff;">$1</b>')
            .replace(/\*(.*?)\*/g, '<i>$1</i>')
            .replace(/`(.*?)`/g, '<span style="background:#1e1f22; padding:3px 5px; border-radius:4px; font-family:Consolas, monospace; font-size: 14px;">$1</span>')
            .replace(/(https?:\/\/[^\s]+)/g, '<a href="$1" target="_blank" style="color:#00a8fc; text-decoration:none;">$1</a>');
    }
};

const admin = {
    kick: (u) => {
        const r = prompt("Důvod vyhození uživatele " + u + "?", "Porušení pravidel");
        if(r !== null) {
            net.sendText(`/kick ${u} ${r}`);
            setTimeout(() => net.sendText("/users"), 500);
        }
    },
    mute: (u) => {
        const d = prompt("Na kolik sekund? (-1 pro navždy)", "60");
        if(d === null) return;
        const r = prompt("Důvod umlčení:", "Spam");
        if (r !== null) {
            net.sendText(`/mute ${u} ${d} ${r}`);
        }
    },
    banModal: (u) => {
        state.targetUser = u;
        document.getElementById('ban-target-name').innerText = u;
        document.getElementById('modal-ban').style.display = 'flex';
    },
    confirmBan: () => {
        const t = document.getElementById('ban-duration').value || 60;
        const r = document.getElementById('ban-reason').value || "Banned";
        net.sendText(`/ban ${state.targetUser} ${t} ${r}`);
        setTimeout(() => net.sendText("/users"), 500);
        ui.closeModals();
    },
    deleteRoom: (r) => {
        if(confirm("Smazat kanál "+r+"?")) {
            net.sendText("/deleteroom "+r);
        }
    },
    broadcastPrompt: () => {
        const m = prompt("Zpráva pro všechny:");
        if(m) net.sendText("/broadcast " + m);
    }
};

// --- 7. CONTEXT MENU ---
const ctx = {
    menu: document.getElementById('context-menu'),

    showSide: (e, username) => {
        if(!ctx.menu || username === state.nick || username === "SYSTEM") return;

        state.activeContextUser = username;
        state.activeMessageId = null;

        const canMod = state.isAdmin && username !== state.nick && username !== "SYSTEM";

        ['ctx-kick', 'ctx-ban', 'ctx-mute'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.style.display = canMod ? 'block' : 'none';
        });

        const delEl = document.getElementById('ctx-del');
        if (delEl) delEl.style.display = 'none';

        const invEl = document.getElementById('ctx-invite');
        if (invEl) invEl.style.display = 'block';

        ctx.menu.style.display = 'block';
        ctx.menu.style.left = e.pageX + "px";
        ctx.menu.style.top = e.pageY + "px";
    },

    showMsg: (e, id, username) => {
        e.preventDefault();

        if(!ctx.menu) return;

        state.activeContextUser = username;
        state.activeMessageId = id;

        const canMod = state.isAdmin && username !== state.nick && username !== "SYSTEM";

        ['ctx-kick', 'ctx-ban', 'ctx-mute'].forEach(btnId => {
            const el = document.getElementById(btnId);
            if (el) el.style.display = canMod ? 'block' : 'none';
        });

        const delEl = document.getElementById('ctx-del');
        if (delEl) {
            const canDelete = ((state.isAdmin || username === state.nick) && id !== "0");
            delEl.style.display = canDelete ? 'block' : 'none';
        }

        const invEl = document.getElementById('ctx-invite');
        if (invEl) {
            invEl.style.display = (username === state.nick || username === "SYSTEM") ? 'none' : 'block';
        }

        ctx.menu.style.display = 'block';
        ctx.menu.style.left = e.pageX + "px";
        ctx.menu.style.top = e.pageY + "px";
    },

    hide: () => {
        if(ctx.menu) ctx.menu.style.display = 'none';
    },

    copyNick: () => {
        navigator.clipboard.writeText(state.activeContextUser);
        ctx.hide();
    },

    mention: () => {
        const inp = document.getElementById('msg-input');
        inp.value += `@${state.activeContextUser} `;
        inp.focus();
        ctx.hide();
    },

    whisper: () => {
        state.privateTarget = state.activeContextUser;
        const pmp = document.getElementById('private-mode-panel');
        const pml = document.getElementById('private-mode-label');
        const inp = document.getElementById('msg-input');

        pmp.style.display = 'flex';
        pml.innerText = `🔒 Šeptáš uživateli: ${state.privateTarget}`;
        inp.placeholder = "Napiš soukromou zprávu...";
        inp.focus();
        ctx.hide();
    },

    inviteToGame: () => {
        net.sendText(`/ttt start ${state.activeContextUser}`);
        ctx.hide();
    },

    deleteMsg: () => {
        if (state.activeMessageId && state.activeMessageId !== "0") {
            if (confirm("Opravdu smazat tuto zprávu?")) {
                net.sendText("/delmsg " + state.activeMessageId);

                const msgEl = document.getElementById(`msg-${state.activeMessageId}`);
                if(msgEl) {
                    msgEl.style.display = 'none';
                }
            }
        }
        ctx.hide();
    },

    kick: () => { admin.kick(state.activeContextUser); ctx.hide(); },
    mute: () => { admin.mute(state.activeContextUser); ctx.hide(); },
    ban: () => { admin.banModal(state.activeContextUser); ctx.hide(); }
};

document.addEventListener('click', () => ctx.hide());

// --- 8. PODPORA KLÁVESY ENTER ---
const bindEnter = (ids, action) => {
    ids.forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.addEventListener('keyup', (e) => {
                if (e.key === 'Enter') action();
            });
        }
    });
};
bindEnter(['inp-ip', 'inp-user', 'inp-pass'], auth.login);
bindEnter(['reg-ip', 'reg-user', 'reg-pass', 'reg-code'], auth.register);
document.getElementById('msg-input').addEventListener("keypress", (e) => {
    if(e.key === "Enter") chat.send();
});