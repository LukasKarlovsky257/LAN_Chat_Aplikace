const ui = {
    loadBackground: () => {
        const bg = localStorage.getItem('lan_custom_bg');
        if (bg) {
            document.body.style.backgroundImage = `url(${bg})`;
        } else {
            document.body.style.backgroundImage = 'none';
        }
    },
    uploadBackground: (input) => {
        const file = input.files[0];
        if (!file) return;
        if (file.size > 5 * 1024 * 1024) {
            ModernDialog.showMessage("Chyba", "Pozadí je příliš velké (Max 5MB).", true);
            return;
        }
        const reader = new FileReader();
        reader.onload = (e) => {
            localStorage.setItem('lan_custom_bg', e.target.result);
            ui.loadBackground();
            ModernDialog.showMessage("Úspěch", "Pozadí úspěšně změněno!", false);
        };
        reader.readAsDataURL(file);
    },
    removeBackground: () => {
        localStorage.removeItem('lan_custom_bg');
        ui.loadBackground();
        ModernDialog.showMessage("Info", "Pozadí odstraněno.", false);
    },
    toggleRightSidebar: () => {
        const sb = document.querySelector('.sidebar-right');
        if (sb) sb.classList.toggle('hidden');
    },
    setReply: (id, sender, text) => {
        state.replyId = id;
        state.replySender = sender;
        state.replyText = text.replace(/\|/g, " ");
        let shortText = state.replyText.length > 45 ? state.replyText.substring(0, 45) + "..." : state.replyText;

        document.getElementById('reply-text').innerText = `"${shortText}"`;
        document.getElementById('reply-preview').classList.add('active');
        document.getElementById('msg-input').focus();
    },
    cancelReply: () => {
        state.replyId = null;
        state.replySender = null;
        state.replyText = null;
        document.getElementById('reply-preview').classList.remove('active');
    },
    scrollToMsg: (id) => {
        const el = document.getElementById(`msg-${id}`);
        if (el) {
            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
            el.style.transition = "background 0.5s";
            el.style.background = "rgba(0, 243, 255, 0.2)";
            setTimeout(() => el.style.background = "transparent", 1500);
        } else {
            ModernDialog.showMessage("Upozornění", "Zpráva už je moc stará a není načtená.", true);
        }
    },
    showAlert: (msg, isError = true) => {
        const authScreen = document.getElementById('auth-screen');
        if (authScreen && authScreen.style.display !== 'none') {
            const statusEl = document.getElementById('auth-status');
            if (statusEl) {
                statusEl.innerText = (isError ? "❌ " : "✅ ") + msg;
                statusEl.style.color = isError ? "var(--danger)" : "var(--success)";
            }
        } else {
            ModernDialog.showMessage("Systém", msg, isError);
        }
    },
    switchAuth: (type) => {
        document.getElementById('form-login').style.display = 'none';
        document.getElementById('form-register').style.display = 'none';

        const recoverForm = document.getElementById('form-recover');
        if (recoverForm) recoverForm.style.display = 'none';

        document.getElementById('tab-login').classList.remove('active');
        document.getElementById('tab-register').classList.remove('active');

        const tabRecover = document.getElementById('tab-recover');
        if (tabRecover) tabRecover.classList.remove('active');

        if (type === 'login') {
            document.getElementById('form-login').style.display = 'block';
            document.getElementById('tab-login').classList.add('active');
        } else if (type === 'register') {
            document.getElementById('form-register').style.display = 'block';
            document.getElementById('tab-register').classList.add('active');
        } else if (type === 'recover') {
            document.getElementById('form-recover').style.display = 'block';
            document.getElementById('tab-recover').classList.add('active');
        }
    },
    initApp: () => {
        ui.loadBackground();
        document.getElementById('auth-screen').style.display = 'none';
        document.getElementById('app-screen').style.display = 'flex';
        document.getElementById('my-nick').innerText = state.nick;
        document.getElementById('my-role').innerText = state.isAdmin ? "ADMIN" : "#USER";

        if (state.isAdmin) {
            document.getElementById('my-role').style.color = 'var(--danger)';
            document.getElementById('admin-logs-trigger').style.display = 'block';

            let rightsPanel = document.getElementById('admin-rights-panel');
            if (!rightsPanel) {
                const rightSidebar = document.querySelector('.sidebar-right');
                rightsPanel = document.createElement('div');
                rightsPanel.id = 'admin-rights-panel';
                rightsPanel.innerHTML = `
                    <div style="border-top: 1px solid var(--glass-border); padding-top: 15px; margin-top: 15px;">
                        <div class="label" style="color: var(--danger); display: flex; align-items: center; gap: 5px;">
                            <span class="material-icons" style="font-size: 16px;">gavel</span> Admin Modul
                        </div>
                        <a href="#" onclick="window.open('http://' + window.location.hostname + ':8888/admin', '_blank'); return false;" style="display: block; text-decoration: none;">
                            <button class="btn-primary" style="width: 100%; background: rgba(237, 66, 69, 0.2); border: 1px solid var(--danger); color: var(--danger); box-shadow: none;">
                                Otevřít Dashboard
                            </button>
                        </a>
                    </div>
                `;
                rightSidebar.appendChild(rightsPanel);
            }
        }

        document.getElementById('my-avatar').innerText = state.nick.charAt(0).toUpperCase();
        chat.join('Lobby');
        setTimeout(() => { net.sendText("/users"); }, 300);
        ui.initDragDrop();

        document.getElementById('msg-input').addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && state.privateTarget) {
                ui.cancelWhisper();
            }
            if (e.key === 'Enter' && !e.shiftKey) {
                if (typeof appConfig !== 'undefined' && appConfig.enterToSend === false) return;
                e.preventDefault();
                chat.send();
            }
        });

        document.getElementById('msg-input').addEventListener('paste', (e) => {
            const items = (e.clipboardData || e.originalEvent.clipboardData).items;
            for (let index in items) {
                const item = items[index];
                if (item.kind === 'file' && item.type.includes('image')) {
                    const blob = item.getAsFile();
                    const reader = new FileReader();
                    reader.onload = (event) => {
                        const base64 = event.target.result.split(',')[1];
                        net.sendText(`IMG:${state.nick}:clipboard_image.png:${base64}`);
                    };
                    reader.readAsDataURL(blob);
                }
            }
        });

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
    openSettings: () => {
        document.getElementById('set-sound').checked = appConfig.sound;
        document.getElementById('set-enter').checked = appConfig.enterToSend;
        document.getElementById('set-autologin').checked = (localStorage.getItem('lan_autologin') !== 'false');
        document.getElementById('modal-settings').style.display = 'flex';
    },
    saveSettings: () => {
        appConfig.sound = document.getElementById('set-sound').checked;
        appConfig.enterToSend = document.getElementById('set-enter').checked;

        const isAuto = document.getElementById('set-autologin').checked;
        localStorage.setItem('lan_autologin', isAuto);
        if (!isAuto) localStorage.removeItem('lan_pass');

        appConfig.save();
        ui.closeModals();
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
        document.body.addEventListener('dragenter', () => overlay.style.display = 'flex');
        overlay.addEventListener('dragleave', () => overlay.style.display = 'none');
        overlay.addEventListener('drop', (e) => {
            e.preventDefault();
            overlay.style.display = 'none';
            if(e.dataTransfer.files.length > 0) chat.uploadFileObj(e.dataTransfer.files[0]);
        });
        window.addEventListener('dragover', (e) => e.preventDefault());
    },
    uploadAvatar: (input) => {
        const file = input.files[0];
        if (!file) return;

        if (file.size > 2 * 1024 * 1024) {
            ModernDialog.showMessage("Chyba", "Avatar je příliš velký (Max 2MB).", true);
            return;
        }

        const reader = new FileReader();
        reader.onload = (e) => {
            const img = new Image();
            img.onload = () => {
                const canvas = document.createElement('canvas');
                const ctx = canvas.getContext('2d');
                const size = 128;
                canvas.width = size;
                canvas.height = size;
                const minSize = Math.min(img.width, img.height);
                const startX = (img.width - minSize) / 2;
                const startY = (img.height - minSize) / 2;
                ctx.drawImage(img, startX, startY, minSize, minSize, 0, 0, size, size);
                const base64 = canvas.toDataURL('image/jpeg', 0.8).split(',')[1];
                net.sendText("SET_AVATAR:" + base64);
                document.getElementById('my-avatar').innerHTML = `<img src="data:image/jpeg;base64,${base64}" style="width:100%; height:100%; object-fit:cover;">`;
            };
            img.src = e.target.result;
        };
        reader.readAsDataURL(file);
    },
    updateRoomUI: (roomName) => {
        state.currentRoom = roomName.trim();
        document.getElementById('current-room').innerText = roomName;
        document.getElementById('messages-box').innerHTML = "";
        document.getElementById('user-list').innerHTML = "";
        state.users = [];
        document.getElementById('user-count').innerText = "0";
        ui.cancelWhisper();
    },
    renderRoomList: (rooms) => {
        const list = document.getElementById('room-list'); list.innerHTML = "";
        rooms.forEach(r => {
            let parts = r.split("|"); let cleanName = parts[0]; let roomType = parts.length > 1 ? parts[1] : "0";
            const li = document.createElement('li');
            if (roomType === "2") li.innerHTML = `<span class="material-icons" style="font-size:16px; color:var(--danger);">lock</span> <span style="color:var(--danger)">${cleanName}</span>`;
            else if (roomType === "1") li.innerHTML = `<span class="material-icons" style="font-size:16px; color:var(--warning);">timer</span> <span style="color:var(--warning)">${cleanName}</span>`;
            else li.innerHTML = `<span class="material-icons" style="font-size:16px;">tag</span> ${cleanName}`;
            li.onclick = () => chat.join(cleanName);
            if (state.isAdmin && cleanName !== 'Lobby') {
                const delBtn = document.createElement('span');
                delBtn.className = "material-icons";
                delBtn.style.marginLeft = "auto";
                delBtn.style.fontSize = "14px";
                delBtn.style.opacity = "0.5";
                delBtn.innerText = "close";
                delBtn.onclick = (e) => { e.stopPropagation(); admin.deleteRoom(cleanName); };
                li.appendChild(delBtn);
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

        let lvlHtml = levelStr ? `<span class="level-badge">${levelStr}</span>` : "";

        let avatarContent = avatarBase64 ?
            `<img src="/avatars/${avatarBase64}" style="width:100%; height:100%; object-fit:cover;">` :
            username.charAt(0).toUpperCase();

        li.innerHTML = `
            <div class="avatar" style="width:32px; height:32px; flex-shrink:0; font-size:14px; color:white; display:flex; justify-content:center; align-items:center; border-radius:8px; font-weight:bold; overflow:hidden; background:linear-gradient(135deg, var(--neon-cyan), #0077ff); margin-right:10px;">
                ${avatarContent}
            </div>
            <span style="font-weight:600; color:var(--text-main); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${username}</span>
            ${lvlHtml}
        `;
        li.oncontextmenu = (e) => { e.preventDefault(); ctx.showSide(e, username); };
        list.appendChild(li);
    },
    igniteBurn: (id, encodedText, seconds) => {
        const btn = document.getElementById(`burn-btn-${id}`);
        const textBox = document.getElementById(`burn-text-${id}`);
        if(!btn || !textBox) return;
        btn.style.display = 'none';
        textBox.style.display = 'block';
        let text = decodeURIComponent(atob(encodedText));
        net.sendText("START_TIMER:" + id);
        let timeLeft = seconds;
        textBox.innerText = `${text} (Smaže se za ${timeLeft}s)`;
        const interval = setInterval(() => {
            timeLeft--;
            if (timeLeft > 0) textBox.innerText = `${text} (Smaže se za ${timeLeft}s)`;
            else {
                clearInterval(interval);
                const msgEl = document.getElementById(`msg-${id}`);
                if (msgEl) msgEl.style.display = 'none';
            }
        }, 1000);
    },
    openGiphy: () => document.getElementById('modal-giphy').style.display = 'flex',
    openGamesDialog: () => document.getElementById('modal-games').style.display = 'flex',
    closeModals: () => {
        if (state.pendingInviteId) { ui.declineInvite(); }
        document.querySelectorAll('.modal').forEach(m => m.style.display = 'none');
    },
    acceptInvite: () => {
        if(state.pendingInviteId) {
            net.sendText("/invite accept " + state.pendingInviteId);
            state.pendingInviteId = null;
            document.getElementById('modal-invite').style.display = 'none';
        }
    },
    declineInvite: () => {
        if(state.pendingInviteId) {
            net.sendText("/invite decline " + state.pendingInviteId);
            state.pendingInviteId = null;
            document.getElementById('modal-invite').style.display = 'none';
        }
    },
    toggleLogs: (show) => {
        document.getElementById('main-view-chat').style.display = show ? 'none' : 'flex';
        document.getElementById('main-view-logs').style.display = show ? 'flex' : 'none';
    },
    searchGiphy: () => {
        const q = document.getElementById('giphy-search').value;
        fetch(`https://api.giphy.com/v1/gifs/search?api_key=NpCRJe4i5UivlUS5Vue1yk4PbOytBcno&q=${q}&limit=9`)
            .then(r => r.json()).then(d => {
            const res = document.getElementById('giphy-results'); res.innerHTML = "";
            d.data.forEach(gif => {
                const img = document.createElement('img');
                img.style.height = "80px";
                img.style.objectFit = "cover";
                img.style.cursor = "pointer";
                img.style.borderRadius = "8px";
                img.src = gif.images.fixed_height_small.url;
                img.onclick = () => {
                    fetch(gif.images.fixed_height.url).then(r=>r.blob()).then(b=>{
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

const renderer = {
    renderWhiteboard: (id, p1, p2) => {
        if (document.getElementById(`wb-container-${id}`)) return;
        const titleText = (p2 === "ROOM") ? `🎨 Volné plátno (od: ${p1})` : `🎨 Plátno: ${p1} & ${p2}`;

        const inlineHtml = `
        <div id="wb-container-${id}" style="width: 100%; display: flex; justify-content: center; margin-top: 16px;">
            <div style="background: var(--glass-bg); backdrop-filter: var(--blur); -webkit-backdrop-filter: var(--blur); padding: 20px; border-radius: 16px; border: 1px solid var(--glass-border); text-align: center; box-shadow: 0 10px 30px rgba(0,0,0,0.5);">
                <div style="color: var(--text-main); font-weight: bold; margin-bottom: 12px; font-size: 16px; letter-spacing: 1px;">${titleText}</div>
                <div style="background: white; border-radius: 8px; border: 1px solid rgba(0,0,0,0.2); display: inline-block; overflow: hidden;">
                    <canvas id="wb-canvas-${id}" width="500" height="350" style="cursor: crosshair; touch-action: none; display: block;" data-color="#000000"></canvas>
                </div>
                <div style="margin-top: 15px; display: flex; gap: 8px; justify-content: center; flex-wrap: wrap;">
                    <button onclick="document.getElementById('wb-canvas-${id}').dataset.color = '#000000'" style="background: #000000; color: white; border: 1px solid var(--glass-border); padding: 8px 16px; border-radius: 8px; cursor: pointer; font-weight: bold;">Černá</button>
                    <button onclick="document.getElementById('wb-canvas-${id}').dataset.color = '#0077FF'" style="background: #0077FF; color: white; border: none; padding: 8px 16px; border-radius: 8px; cursor: pointer; font-weight: bold;">Modrá</button>
                    <button onclick="document.getElementById('wb-canvas-${id}').dataset.color = '#FF2A55'" style="background: #FF2A55; color: white; border: none; padding: 8px 16px; border-radius: 8px; cursor: pointer; font-weight: bold;">Červená</button>
                    <button onclick="document.getElementById('wb-canvas-${id}').dataset.color = '#FFFFFF'" style="background: #ffffff; color: black; border: 1px solid #ccc; padding: 8px 16px; border-radius: 8px; cursor: pointer; font-weight: bold;">Guma</button>
                    <div style="width: 1px; background: var(--glass-border); margin: 0 4px;"></div>
                    <button onclick="renderer.clearWhiteboard('${id}'); net.sendText('GAME:WB:CLEAR:${id}');" style="background: var(--warning); color: #000; border: none; padding: 8px 16px; border-radius: 8px; cursor: pointer; font-weight: bold;">Vymazat</button>
                    <button onclick="renderer.closeWhiteboard('${id}'); net.sendText('GAME:WB:CLOSE:${id}');" style="background: rgba(255, 42, 85, 0.2); color: var(--danger); border: 1px solid var(--danger); padding: 8px 16px; border-radius: 8px; cursor: pointer; font-weight: bold;">Zavřít</button>
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
        if (!container) {
            container = document.createElement('div');
            container.id = gameId;
            container.setAttribute("oncontextmenu", "event.stopPropagation(); return false;");
            container.style = "background:var(--glass-bg); backdrop-filter: var(--blur); padding:20px; border-radius:16px; margin-top:10px; width:fit-content; border:1px solid var(--glass-border); box-shadow: 0 10px 30px rgba(0,0,0,0.3);";
            isNew = true;
        }

        let html = `<div style="color:var(--text-main); font-weight:bold; margin-bottom:15px; text-align:center; font-size:16px;">🎮 ${p1} <span style="color:var(--neon-cyan)">(X)</span> vs ${p2} <span style="color:var(--danger)">(O)</span></div>`;
        if (status === 'PLAYING') html += `<div style="color:var(--warning); text-align:center; margin-bottom:15px; font-weight:600;">Na tahu: ${turn}</div>`;
        else if (status === 'WIN1') html += `<div style="color:var(--success); text-align:center; margin-bottom:15px; font-weight:600;">🏆 Vítěz: ${p1}!</div>`;
        else if (status === 'WIN2') html += `<div style="color:var(--success); text-align:center; margin-bottom:15px; font-weight:600;">🏆 Vítěz: ${p2}!</div>`;
        else if (status === 'DRAW') html += `<div style="color:var(--text-muted); text-align:center; margin-bottom:15px; font-weight:600;">🤝 Remíza!</div>`;

        html += `<div style="display:grid; grid-template-columns:repeat(3, 60px); gap:8px; justify-content:center;">`;
        for(let i = 0; i < 9; i++) {
            let r = Math.floor(i / 3), c = i % 3, val = board[i];
            let color = val === 'X' ? 'var(--neon-cyan)' : (val === 'O' ? 'var(--danger)' : 'var(--text-muted)');
            let char = val === '-' ? '' : val;
            let dis = (status !== 'PLAYING' || val !== '-') ? 'disabled' : '';
            let cursor = dis ? 'default' : 'pointer';
            html += `<button ${dis} onclick="net.sendText('/ttt tah ${r} ${c}')" style="width:60px; height:60px; background:rgba(0,0,0,0.4); border:1px solid var(--glass-border); border-radius:12px; color:${color}; font-size:28px; font-weight:bold; cursor:${cursor}; transition:0.2s;">${char}</button>`;
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

        if (sender !== "SYSTEM" && text.startsWith("ZK:")) {
            if (!state.roomKeys) state.roomKeys = {};

            let roomSecret = state.roomKeys[state.currentRoom] || state.currentRoom;
            let decrypted = cryptoAES.decrypt(text.substring(3), roomSecret);
            text = decrypted ? decrypted : "🔒 [Šifrovaná zpráva - nelze dešifrovat]";
        }

        if (sender === "SYSTEM" && text.startsWith("GAME:TTT:")) { renderer.renderGame(text.substring(9)); return; }
        if (sender === "SYSTEM" && text.startsWith("GAME:WB:START:")) { let parts = text.substring(14).split(":"); renderer.renderWhiteboard(parts[0], parts[1], parts[2]); return; }
        if (sender === "SYSTEM" && text.startsWith("INVITE:RECEIVE:")) {
            let invParts = text.split(":"); state.pendingInviteId = invParts[2];
            document.getElementById('invite-text').innerHTML = `Hráč <b>${invParts[3]}</b> tě vyzval na hru.<br>Máš 30 sekund na přijetí.`;
            document.getElementById('modal-invite').style.display = 'flex';
            return;
        }
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
        const colors = ['#00f3ff', '#00e676', '#ffb300', '#ff2a55', '#bc13fe'];
        const avatarBg = sender === 'SYSTEM' ? 'rgba(0,0,0,0.5)' : colors[sender.length % colors.length];
        const avatarLetter = sender === 'SYSTEM' ? '⚙️' : sender.charAt(0).toUpperCase();

        let finalBg = avatarBg;
        let avatarDisplay = avatarLetter;

        if (state.avatars[sender]) {
            avatarDisplay = `<img src="/avatars/${state.avatars[sender]}" style="width:100%; height:100%; object-fit:cover; border-radius:12px;">`;
            finalBg = "transparent";
        }

        const isWhisper = type === 'text' && (typeof content === 'string') && (content.includes("🕵️") || content.includes("[WHISPER]"));

        let replyHTML = "";

        if (type === 'text' && typeof content === 'string' && content.startsWith("REPLY|")) {
            let parts = content.split("|");
            let replyId = "0", replySender = "", replyText = "";

            if (parts.length >= 5 && !isNaN(parts[1])) {
                replyId = parts[1];
                replySender = parts[2];
                replyText = parts[3];
                content = parts.slice(4).join("|");
            }
            else if (parts.length >= 4) {
                replySender = parts[1];
                replyText = parts[2];
                content = parts.slice(3).join("|");
            }

            if (replySender) {
                let shortReplyText = replyText.length > 80 ? replyText.substring(0, 80) + "..." : replyText;
                let replyAvatarDisplay = replySender.charAt(0).toUpperCase();
                let replyAvatarBg = colors[replySender.length % colors.length];

                if (state.avatars[replySender]) {
                    replyAvatarDisplay = `<img src="/avatars/${state.avatars[replySender]}" style="width:100%; height:100%; object-fit:cover; border-radius: 6px;">`;
                    replyAvatarBg = "transparent";
                }

                let clickAction = replyId !== "0" ? `ui.scrollToMsg('${replyId}')` : `ModernDialog.showMessage('Info', 'Tato citace je ze staré verze bez ID.', false)`;

                replyHTML = `
                    <div class="modern-quote-wrapper">
                        <div class="modern-quote-arrow"></div>
                        <div class="modern-quote-content" onclick="${clickAction}">
                            <div class="modern-quote-avatar" style="background: ${replyAvatarBg}">${replyAvatarDisplay}</div>
                            <span class="modern-quote-name">@${replySender}</span>
                            <span class="modern-quote-text">${shortReplyText}</span>
                        </div>
                    </div>
                `;
            }
        }

        let safeContent = "";
        if (type === 'text') {
            safeContent = replyHTML + `<div class="msg-content">${utils.markdown(content)}</div>`;
        }
        else if (type === 'image') safeContent = `<img src="data:image/png;base64,${content.data}" style="max-width: 350px; max-height: 350px; border-radius: 12px; margin-top: 8px; cursor: pointer; border: 1px solid var(--glass-border); box-shadow: 0 4px 15px rgba(0,0,0,0.3);" onclick="window.open(this.src)">`;
        else if (type === 'file') safeContent = `<a href="data:application/octet-stream;base64,${content.data}" download="${content.name}" class="file-link" style="display:inline-flex; align-items:center; background:rgba(0,0,0,0.3); padding:10px 15px; border-radius:8px; border:1px solid var(--glass-border); text-decoration:none; margin-top:8px;"><span class="material-icons" style="margin-right:8px; color:var(--neon-cyan);">insert_drive_file</span><span style="color:var(--text-main); font-weight:600;">Stáhnout ${content.name}</span></a>`;
        else if (type === 'burn') {
            if (content.expired) safeContent = `<div style="color:var(--danger); font-style:italic;">🔥 ${content.text}</div>`;
            else {
                let encodedText = btoa(encodeURIComponent(content.text));
                safeContent = `<div id="burn-box-${id}" style="background:rgba(255,42,85,0.1); padding:12px; border-radius:8px; border:1px solid var(--danger); display:inline-block; margin-top:8px;"><button id="burn-btn-${id}" onclick="ui.igniteBurn('${id}', '${encodedText}', ${content.seconds})" style="background:var(--danger); color:white; border:none; padding:8px 16px; border-radius:8px; cursor:pointer; font-weight:bold;">Zobrazit tajnou zprávu (${content.seconds}s)</button><div id="burn-text-${id}" style="display:none; color:white; margin-top:8px;"></div></div>`;
            }
        }
        const msgHtml = `<div id="msg-${id}" class="msg-group" oncontextmenu="ctx.showMsg(event, '${id}', '${sender}', '${(typeof content === 'string' ? content.replace(/'/g, "\\'").replace(/"/g, "&quot;") : '')}')" style="${isWhisper ? 'background:rgba(188, 19, 254, 0.15); border-left:3px solid var(--neon-purple); padding-left:13px;' : ''}">
    <div class="msg-avatar-container" onclick="ctx.showSide(event, '${sender}')">
        <div class="msg-avatar" style="background:${finalBg}; overflow:hidden;">${avatarDisplay}</div>
    </div>
    <div style="display:flex; flex-direction:column; min-width:0; width:100%;">
        <div class="msg-header-line">
            <span class="msg-author" style="color:${isWhisper ? 'var(--neon-purple)' : ''};" onclick="ctx.showSide(event, '${sender}')">${sender}</span>
            <span class="msg-time">${time}</span>
        </div>
        ${safeContent}
    </div>
</div>`;

        box.insertAdjacentHTML('beforeend', msgHtml);
        box.scrollTop = box.scrollHeight;
    }
};

const admin = {
    kick: (u) => {
        ModernDialog.showInput("Kick Hráče", "Zadej důvod vyhození:").then(r => {
            if(r) net.sendText(`/kick ${u} ${r}`);
        });
    },
    muteModal: (u) => {
        state.targetUser = u;
        document.getElementById('mute-target-name').innerText = u;
        document.getElementById('modal-mute').style.display = 'flex';
    },
    confirmMute: () => {
        const t = document.getElementById('mute-duration').value || 60;
        const r = document.getElementById('mute-reason').value || "Nevhodné chování";
        net.sendText(`/mute ${state.targetUser} ${t} ${r}`);
        ui.closeModals();
    },
    banModal: (u) => {
        state.targetUser = u;
        document.getElementById('ban-target-name').innerText = u;
        document.getElementById('modal-ban').style.display = 'flex';
    },
    confirmBan: () => {
        const t = document.getElementById('ban-duration').value || 60;
        const r = document.getElementById('ban-reason').value || "Porušení pravidel";
        net.sendText(`/ban ${state.targetUser} ${t} ${r}`);
        ui.closeModals();
    },
    deleteRoom: (r) => {
        ModernDialog.showConfirm("Varování", `Opravdu smazat místnost <b>${r}</b>?`, true).then(res => {
            if(res) net.sendText("/deleteroom "+r);
        });
    },
    broadcastPrompt: () => {
        ModernDialog.showInput("Oznámení", "Zadej text plošného oznámení:").then(m => {
            if(m) net.sendText("/broadcast " + m);
        });
    }
};

const ctx = {
    menu: document.getElementById('context-menu'),

    adjustPosition: (e) => {
        let menuWidth = ctx.menu.offsetWidth;
        let menuHeight = ctx.menu.offsetHeight;
        let windowWidth = window.innerWidth;
        let windowHeight = window.innerHeight;

        let left = e.pageX;
        let top = e.pageY;

        if (left + menuWidth > windowWidth) {
            left = windowWidth - menuWidth - 10;
        }

        if (top + menuHeight > windowHeight) {
            top = e.pageY - menuHeight;
            if (top < 0) top = 10;
        }

        ctx.menu.style.left = left + "px";
        ctx.menu.style.top = top + "px";
    },

    showSide: (e, u) => {
        if(!ctx.menu || u === state.nick || u === "SYSTEM") return;
        state.activeContextUser = u;
        state.activeMessageId = null;
        state.activeMessageText = null;

        document.getElementById('ctx-del').style.display = 'none';
        document.getElementById('ctx-kick').style.display = state.isAdmin ? 'block' : 'none';
        document.getElementById('ctx-mute').style.display = state.isAdmin ? 'block' : 'none';
        document.getElementById('ctx-ban').style.display = state.isAdmin ? 'block' : 'none';

        ctx.menu.style.display = 'block';
        ctx.adjustPosition(e);
    },

    showMsg: (e, id, u, text) => {
        e.preventDefault();
        if(!ctx.menu) return;
        state.activeContextUser = u;
        state.activeMessageId = id;
        state.activeMessageText = text;

        document.getElementById('ctx-del').style.display = (state.isAdmin || u === state.nick) ? 'block' : 'none';
        document.getElementById('ctx-kick').style.display = state.isAdmin && u !== state.nick ? 'block' : 'none';
        document.getElementById('ctx-mute').style.display = state.isAdmin && u !== state.nick ? 'block' : 'none';
        document.getElementById('ctx-ban').style.display = state.isAdmin && u !== state.nick ? 'block' : 'none';

        ctx.menu.style.display = 'block';
        ctx.adjustPosition(e);
    },

    hide: () => {
        if(ctx.menu) ctx.menu.style.display = 'none';
    },

    reply: () => {
        if (state.activeContextUser && state.activeMessageText && state.activeMessageId) {
            ui.setReply(state.activeMessageId, state.activeContextUser, state.activeMessageText);
        }
        ctx.hide();
    },

    copyNick: () => { navigator.clipboard.writeText(state.activeContextUser); ctx.hide(); },
    mention: () => { const i = document.getElementById('msg-input'); i.value += `@${state.activeContextUser} `; i.focus(); ctx.hide(); },
    whisper: () => {
        state.privateTarget = state.activeContextUser;
        document.getElementById('private-mode-panel').style.display = 'flex';
        document.getElementById('private-mode-label').innerText = `🔒 Šeptáš: ${state.privateTarget}`;
        ctx.hide();
    },
    inviteToGame: () => { net.sendText(`/ttt start ${state.activeContextUser}`); ctx.hide(); },
    roomInvite: () => { net.sendText(`/roominvite ${state.activeContextUser}`); ctx.hide(); },
    deleteMsg: () => {
        if(state.activeMessageId) {
            ModernDialog.showConfirm("Potvrzení", "Opravdu chceš smazat tuto zprávu?", true).then(res => {
                if(res) net.sendText("/delmsg " + state.activeMessageId);
            });
        }
        ctx.hide();
    },
    kick: () => { admin.kick(state.activeContextUser); ctx.hide(); },
    mute: () => { admin.muteModal(state.activeContextUser); ctx.hide(); },
    ban: () => { admin.banModal(state.activeContextUser); ctx.hide(); }
};
window.cancelReply = ui.cancelReply;