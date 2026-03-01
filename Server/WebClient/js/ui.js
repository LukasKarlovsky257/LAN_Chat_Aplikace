/* =========================================
   3. UI & RENDERER - Vykreslování a Modaly
   ========================================= */

const ui = {
    setReply: (sender, text) => {
        state.replySender = sender;
        // Očistíme text od | aby se nerozbilo parsování
        state.replyText = text.replace(/\|/g, " ");
        let shortText = state.replyText.length > 45 ? state.replyText.substring(0, 45) + "..." : state.replyText;

        document.getElementById('reply-text').innerText = `Odpovídáš uživateli ${sender}: "${shortText}"`;
        document.getElementById('reply-preview').style.display = 'block';
        document.getElementById('msg-input').focus();
    },
    cancelReply: () => {
        state.replySender = null;
        state.replyText = null;
        document.getElementById('reply-preview').style.display = 'none';
    },
    showAlert: (msg, isError = true) => {
        const authScreen = document.getElementById('auth-screen');
        if (authScreen && authScreen.style.display !== 'none') {
            const statusEl = document.getElementById('auth-status');
            if (statusEl) {
                statusEl.innerText = (isError ? "❌ " : "✅ ") + msg;
                statusEl.style.color = isError ? "#ed4245" : "#46ff50";
            }
        } else {
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

        // 👇 PŘIDÁNO: Detekce psaní pro Web 👇
        let typingTimeout;
        let isTyping = false;
        document.getElementById('msg-input').addEventListener('input', () => {
            if (!isTyping) {
                net.sendText("TYPING:1");
                isTyping = true;
            }
            clearTimeout(typingTimeout);
            typingTimeout = setTimeout(() => {
                net.sendText("TYPING:0");
                isTyping = false;
            }, 2000);
        });
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
    uploadAvatar: (input) => {
        const file = input.files[0];
        if (!file) return;

        // Zkontrolujeme velikost (max 2MB pro profilovku)
        if (file.size > 2 * 1024 * 1024) {
            ui.showAlert("Avatar je příliš velký (Max 2MB).", true);
            return;
        }

        const reader = new FileReader();
        reader.onload = (e) => {
            // Vytvoříme obrázek v paměti pro zmenšení
            const img = new Image();
            img.onload = () => {
                const canvas = document.createElement('canvas');
                const ctx = canvas.getContext('2d');

                // Zmenšíme ho na čtverec 128x128 pixelů
                const size = 128;
                canvas.width = size;
                canvas.height = size;

                // Vykreslíme ho vycentrovaný (ořízneme obdélníky)
                const minSize = Math.min(img.width, img.height);
                const startX = (img.width - minSize) / 2;
                const startY = (img.height - minSize) / 2;

                ctx.drawImage(img, startX, startY, minSize, minSize, 0, 0, size, size);

                // Získáme Base64 a pošleme na server
                const base64 = canvas.toDataURL('image/jpeg', 0.8).split(',')[1];
                net.sendText("SET_AVATAR:" + base64);

                // Rovnou si ho vizuálně změníme i u sebe v levém panelu
                document.getElementById('my-avatar').innerHTML = `<img src="data:image/jpeg;base64,${base64}" style="width:100%; height:100%; object-fit:cover;">`;
            };
            img.src = e.target.result;
        };
        reader.readAsDataURL(file);
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
    addUserToSidebar: (username, levelStr, avatarBase64) => {
        if (state.users.includes(username) || username === "SYSTEM") return;
        state.users.push(username);
        document.getElementById('user-count').innerText = state.users.length;

        const list = document.getElementById('user-list');
        const li = document.createElement('li');

        let lvlHtml = levelStr ? `<span class="level-badge" style="background:#faa61a; color:#fff; font-size:10px; font-weight:bold; padding:2px 6px; border-radius:12px; margin-left:auto;">${levelStr}</span>` : "";

        // Už neřešíme base64, ale bereme přímo cestu ze serveru
        let avatarContent = avatarBase64 ?
            `<img src="/avatars/${avatarBase64}" style="width:100%; height:100%; object-fit:cover;">` :
            username.charAt(0).toUpperCase();

        li.innerHTML = `<div class="avatar" style="background:#5865F2; width:32px; height:32px; font-size:14px; color:white; display:flex; justify-content:center; align-items:center; border-radius:50%; font-weight:bold; overflow:hidden;">${avatarContent}</div><span style="font-weight:600; margin-left:10px">${username}</span> ${lvlHtml}`;
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
        if (state.pendingInviteId) { ui.declineInvite(); }
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

const renderer = {
    renderWhiteboard: (id, p1, p2) => {
        if (document.getElementById(`wb-container-${id}`)) return;
        const titleText = (p2 === "ROOM") ? `🎨 Volné plátno (od: ${p1})` : `🎨 Plátno: ${p1} & ${p2}`;

        const inlineHtml = `
        <div id="wb-container-${id}" style="width: 100%; display: flex; justify-content: center; margin-top: 16px;">
            <div style="background: #2b2d31; padding: 16px; border-radius: 8px; border: 1px solid #4f545c; text-align: center; box-shadow: 0 4px 15px rgba(0,0,0,0.3);">
                <div style="color: #f2f3f5; font-weight: bold; margin-bottom: 12px; font-size: 16px;">${titleText}</div>
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
        let lastPt = null; let lastNetPt = null; let lastSendTime = 0;

        const getPos = (e) => { const rect = canvas.getBoundingClientRect(); return { x: Math.round(e.clientX - rect.left), y: Math.round(e.clientY - rect.top) }; };
        const startDrawing = (e) => { isDrawing = true; lastPt = getPos(e); lastNetPt = getPos(e); };
        const stopDrawing = () => { isDrawing = false; ctxCanvas.beginPath(); };

        const draw = (e) => {
            if (!isDrawing || !lastPt) return;
            const pt = getPos(e); const color = canvas.dataset.color || '#000000';
            ctxCanvas.lineWidth = color === '#FFFFFF' ? 12 : 3; ctxCanvas.lineCap = 'round'; ctxCanvas.strokeStyle = color;
            ctxCanvas.beginPath(); ctxCanvas.moveTo(lastPt.x, lastPt.y); ctxCanvas.lineTo(pt.x, pt.y); ctxCanvas.stroke();

            const now = Date.now();
            if (now - lastSendTime > 40) {
                net.sendText(`GAME:WB:DRAW:${id}:${lastNetPt.x}:${lastNetPt.y}:${pt.x}:${pt.y}:${color}`);
                lastSendTime = now; lastNetPt = pt;
            }
            lastPt = pt;
        };

        canvas.addEventListener('mousedown', startDrawing); canvas.addEventListener('mousemove', draw);
        canvas.addEventListener('mouseup', stopDrawing); canvas.addEventListener('mouseout', stopDrawing);
    },
    drawWhiteboard: (data) => {
        const parts = data.split(":"); if (parts.length < 6) return;
        const id = parts[0]; const canvas = document.getElementById(`wb-canvas-${id}`); if (!canvas) return;
        const ctxCanvas = canvas.getContext('2d');
        const x1 = parseFloat(parts[1]), y1 = parseFloat(parts[2]), x2 = parseFloat(parts[3]), y2 = parseFloat(parts[4]), color = parts[5];
        ctxCanvas.lineWidth = color === '#FFFFFF' ? 12 : 3; ctxCanvas.lineCap = 'round'; ctxCanvas.strokeStyle = color;
        ctxCanvas.beginPath(); ctxCanvas.moveTo(x1, y1); ctxCanvas.lineTo(x2, y2); ctxCanvas.stroke();
    },
    clearWhiteboard: (id) => { const canvas = document.getElementById(`wb-canvas-${id}`); if (canvas) canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height); },
    closeWhiteboard: (id) => { const container = document.getElementById(`wb-container-${id}`); if (container) { container.style.opacity = "0.5"; container.style.pointerEvents = "none"; const header = container.querySelector('div > div'); if (header) header.innerText = "🎨 Společné plátno (Uzavřeno)"; } },
    renderGame: (data) => {
        let p = data.split(":"); if (p.length < 6) return;
        let gameId = `game-${p[0]}`, p1 = p[1], p2 = p[2], turn = p[3], board = p[4], status = p[5];
        let container = document.getElementById(gameId); let isNew = false;
        if (!container) { container = document.createElement('div'); container.id = gameId; container.setAttribute("oncontextmenu", "event.stopPropagation(); return false;"); container.style = "background:#2b2d31; padding:15px; border-radius:8px; margin-top:10px; width:fit-content; border:1px solid #1e1f22;"; isNew = true; }

        let html = `<div style="color:white; font-weight:bold; margin-bottom:10px; text-align:center; font-size:16px;">🎮 ${p1} <span style="color:#5865F2">(X)</span> vs ${p2} <span style="color:#ed4245">(O)</span></div>`;
        if (status === 'PLAYING') html += `<div style="color:#faa61a; text-align:center; margin-bottom:15px; font-weight:600;">Na tahu: ${turn}</div>`;
        else if (status === 'WIN1') html += `<div style="color:#46ff50; text-align:center; margin-bottom:15px; font-weight:600;">🏆 Vítěz: ${p1}!</div>`;
        else if (status === 'WIN2') html += `<div style="color:#46ff50; text-align:center; margin-bottom:15px; font-weight:600;">🏆 Vítěz: ${p2}!</div>`;
        else if (status === 'DRAW') html += `<div style="color:gray; text-align:center; margin-bottom:15px; font-weight:600;">🤝 Remíza!</div>`;

        html += `<div style="display:grid; grid-template-columns:repeat(3, 60px); gap:5px; justify-content:center;">`;
        for(let i = 0; i < 9; i++) {
            let r = Math.floor(i / 3), c = i % 3, val = board[i];
            let color = val === 'X' ? '#5865F2' : (val === 'O' ? '#ed4245' : '#4f545c'); let char = val === '-' ? '' : val;
            let dis = (status !== 'PLAYING' || val !== '-') ? 'disabled' : ''; let cursor = dis ? 'default' : 'pointer';
            html += `<button ${dis} onclick="net.sendText('/ttt tah ${r} ${c}')" style="width:60px; height:60px; background:#1e1f22; border:1px solid #313338; border-radius:8px; color:${color}; font-size:28px; font-weight:bold; cursor:${cursor}; transition:0.2s;">${char}</button>`;
        }
        html += `</div>`; container.innerHTML = html;
        if (isNew) { let box = document.getElementById('messages-box'); let wrapper = document.createElement('div'); wrapper.setAttribute("oncontextmenu", "event.stopPropagation(); return false;"); wrapper.style = "display:flex; justify-content:center;"; wrapper.appendChild(container); box.appendChild(wrapper); box.scrollTop = box.scrollHeight; }
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

        if (sender !== "SYSTEM" && text.startsWith("ZK:")) { let decrypted = cryptoAES.decrypt(text.substring(3), state.currentRoom); text = decrypted ? decrypted : "🔒 [Šifrovaná zpráva - nelze dešifrovat]"; }
        if (sender === "SYSTEM" && text.startsWith("GAME:TTT:")) { renderer.renderGame(text.substring(9)); return; }
        if (sender === "SYSTEM" && text.startsWith("GAME:WB:START:")) { let parts = text.substring(14).split(":"); renderer.renderWhiteboard(parts[0], parts[1], parts[2]); return; }
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
        const box = document.getElementById('messages-box');
        const colors = ['#5865F2', '#3ba55d', '#faa61a', '#ed4245', '#eb459e'];
        const avatarBg = sender === 'SYSTEM' ? '#313338' : colors[sender.length % colors.length];
        const avatarLetter = sender === 'SYSTEM' ? '⚙️' : sender.charAt(0).toUpperCase()

        let finalBg = avatarBg;
        let avatarDisplay = avatarLetter;

        if (state.avatars[sender]) {
            avatarDisplay = `<img src="/avatars/${state.avatars[sender]}" style="width:100%; height:100%; object-fit:cover; border-radius:50%;">`;
            finalBg = "transparent";
        }

        const isWhisper = type === 'text' && (typeof content === 'string') && (content.includes("🕵️") || content.includes("[WHISPER]"));

        // 🔥 OPRAVA PRO REPLY 🔥
        let replyHTML = "";
        if (type === 'text' && typeof content === 'string' && content.startsWith("REPLY|")) {
            let parts = content.split("|");
            if (parts.length >= 4) {
                let replySender = parts[1];
                let replyText = parts[2];
                // Odřízneme hlavičku a necháme jen novou zprávu
                content = parts.slice(3).join("|");

                // Vytvoříme hezký HTML blok pro citaci (podobný tomu z Javy)
                replyHTML = `
                    <div style="background: rgba(0,0,0,0.15); border-left: 3px solid #5865F2; padding: 6px 10px; margin-bottom: 6px; border-radius: 4px; display: inline-block; min-width: 200px;">
                        <div style="font-size: 11px; font-weight: bold; color: #a3a6aa; margin-bottom: 2px;">@${replySender}</div>
                        <div style="font-size: 13px; color: #dbdee1; font-style: italic;">${replyText}</div>
                    </div><br>
                `;
            }
        }

        let safeContent = "";
        if (type === 'text') {
            // Přilepíme ReplyHTML před zbytek textu
            safeContent = replyHTML + utils.markdown(content);
        }
        else if (type === 'image') safeContent = `<img src="data:image/png;base64,${content.data}" style="max-width: 350px; max-height: 350px; border-radius: 8px; margin-top: 5px; cursor: pointer;" onclick="window.open(this.src)">`;
        else if (type === 'file') safeContent = `<div style="background:#2b2d31; padding:12px; border-radius:6px; display:inline-flex; align-items:center;"><a href="data:application/octet-stream;base64,${content.data}" download="${content.name}" style="color:#00a8fc;">Stáhnout ${content.name}</a></div>`;
        else if (type === 'burn') {
            if (content.expired) safeContent = `<div style="color:#ed4245; font-style:italic;">🔥 ${content.text}</div>`;
            else {
                let encodedText = btoa(encodeURIComponent(content.text));
                safeContent = `<div id="burn-box-${id}" style="background:#2b2d31; padding:12px; border-radius:6px; border:1px solid #ed4245;"><button id="burn-btn-${id}" onclick="ui.igniteBurn('${id}', '${encodedText}', ${content.seconds})" style="background:#ed4245; color:white; border:none; padding:8px 16px; border-radius:4px; cursor:pointer;">Zobrazit zprávu (${content.seconds}s)</button><div id="burn-text-${id}" style="display:none; color:white; margin-top:5px;"></div></div>`;
            }
        }

        const msgHtml = `<div id="msg-${id}" oncontextmenu="ctx.showMsg(event, '${id}', '${sender}', '${(typeof content === 'string' ? content.replace(/'/g, "\\'") : '')}')" style="display:flex; gap:16px; padding:2px 0; margin-top:16px; ${isWhisper ? 'background:rgba(142,36,170,0.15); border-left:3px solid #b046ff; padding-left:10px;' : ''}">
            <div style="width:40px; height:40px; border-radius:50%; background:${finalBg}; display:flex; align-items:center; justify-content:center; color:white; font-weight:bold; font-size:18px; flex-shrink:0; cursor:pointer; overflow:hidden;" onclick="ctx.showSide(event, '${sender}')">${avatarDisplay}</div>
            <div style="display:flex; flex-direction:column; max-width:calc(100% - 56px); min-width:0;">
                <div style="display:flex; align-items:baseline; gap:8px; margin-bottom:4px;">
                    <span style="color:${isWhisper ? '#b046ff' : (sender==='SYSTEM'?'#949ba4':'#f2f3f5')}; font-weight:500; cursor:pointer;" onclick="ctx.showSide(event, '${sender}')">${sender}</span>
                    <span style="color:#949ba4; font-size:12px;">${time}</span>
                </div>
                <div style="color:${sender==='SYSTEM'?'#949ba4':'#dbdee1'}; font-size:16px; line-height:1.5; overflow-wrap:anywhere; word-break:break-word; white-space:pre-wrap;">${safeContent}</div>
            </div>
        </div>`;

        box.insertAdjacentHTML('beforeend', msgHtml);
        box.scrollTop = box.scrollHeight;
    }
};

const admin = { kick: (u) => { const r = prompt("Důvod?", "Porušení pravidel"); if(r) net.sendText(`/kick ${u} ${r}`); }, mute: (u) => { const d = prompt("Sekundy (-1 navždy):", "60"); if(d) net.sendText(`/mute ${u} ${d}`); }, banModal: (u) => { state.targetUser = u; document.getElementById('ban-target-name').innerText = u; document.getElementById('modal-ban').style.display = 'flex'; }, confirmBan: () => { const t = document.getElementById('ban-duration').value || 60; const r = document.getElementById('ban-reason').value; net.sendText(`/ban ${state.targetUser} ${t} ${r}`); ui.closeModals(); }, deleteRoom: (r) => { if(confirm("Smazat?")) net.sendText("/deleteroom "+r); }, broadcastPrompt: () => { const m = prompt("Oznámení:"); if(m) net.sendText("/broadcast " + m); } };
const ctx = {
    menu: document.getElementById('context-menu'),

    showSide: (e, u) => {
        if(!ctx.menu || u === state.nick || u === "SYSTEM") return;
        state.activeContextUser = u;
        state.activeMessageId = null;
        state.activeMessageText = null;
        ctx.menu.style.display = 'block';
        ctx.menu.style.left = e.pageX + "px";
        ctx.menu.style.top = e.pageY + "px";
    },

    showMsg: (e, id, u, text) => {
        e.preventDefault();
        if(!ctx.menu) return;
        state.activeContextUser = u;
        state.activeMessageId = id;
        state.activeMessageText = text; // Uložení textu pro citaci
        ctx.menu.style.display = 'block';
        ctx.menu.style.left = e.pageX + "px";
        ctx.menu.style.top = e.pageY + "px";
    },

    hide: () => {
        if(ctx.menu) ctx.menu.style.display = 'none';
    },

    // 👇 PŘIDÁNA A OPRAVENA FUNKCE REPLY 👇
    reply: () => {
        if (state.activeContextUser && state.activeMessageText) {
            // Změněno na ui.setReply, aby to volalo správnou funkci z tvého ui.js
            ui.setReply(state.activeContextUser, state.activeMessageText);
        }
        ctx.hide();
    },

    copyNick: () => { navigator.clipboard.writeText(state.activeContextUser); ctx.hide(); },
    mention: () => { const i = document.getElementById('msg-input'); i.value += `@${state.activeContextUser} `; i.focus(); ctx.hide(); },
    whisper: () => { state.privateTarget = state.activeContextUser; document.getElementById('private-mode-panel').style.display = 'flex'; document.getElementById('private-mode-label').innerText = `🔒 Šeptáš: ${state.privateTarget}`; ctx.hide(); },
    inviteToGame: () => { net.sendText(`/ttt start ${state.activeContextUser}`); ctx.hide(); },
    roomInvite: () => { net.sendText(`/roominvite ${state.activeContextUser}`); ctx.hide(); },
    deleteMsg: () => { if(state.activeMessageId && confirm("Smazat?")) net.sendText("/delmsg " + state.activeMessageId); ctx.hide(); },
    kick: () => { admin.kick(state.activeContextUser); ctx.hide(); },
    mute: () => { admin.mute(state.activeContextUser); ctx.hide(); },
    ban: () => { admin.banModal(state.activeContextUser); ctx.hide(); }
};
window.cancelReply = ui.cancelReply;