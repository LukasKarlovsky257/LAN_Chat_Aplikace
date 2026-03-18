// --- Sjednocený a opravený app.js ---

// 1. INICIALIZACE GLOBÁLNÍHO STAVU
if (typeof window.state === 'undefined') window.state = {};
if (!window.state.roomKeys) window.state.roomKeys = {};

// 2. KRYPTOGRAFICKÝ ENGINE (RSA + AES)
// Ujistíme se, že JSEncrypt je dostupný
if (typeof JSEncrypt !== 'undefined') {
    state.myRsa = new JSEncrypt({ default_key_size: 1024 });
    state.myRsa.getKey();
    state.myPublicKey = state.myRsa.getPublicKeyB64();
}

const cryptoAES = {
    generateKey: (password) => CryptoJS.SHA256(password),
    encrypt: (plainText, password) => {
        try {
            const key = cryptoAES.generateKey(password);
            const iv = CryptoJS.lib.WordArray.random(16);
            const encrypted = CryptoJS.AES.encrypt(plainText, key, { iv: iv, mode: CryptoJS.mode.CBC, padding: CryptoJS.pad.Pkcs7 });
            return CryptoJS.enc.Base64.stringify(iv) + ":" + encrypted.toString();
        } catch(e) { return null; }
    },
    decrypt: (encryptedPayload, password) => {
        try {
            const parts = encryptedPayload.split(":");
            if(parts.length !== 2) return null;
            const iv = CryptoJS.enc.Base64.parse(parts[0]);
            const cipherParams = CryptoJS.lib.CipherParams.create({ ciphertext: CryptoJS.enc.Base64.parse(parts[1]) });
            const key = cryptoAES.generateKey(password);
            const decrypted = CryptoJS.AES.decrypt(cipherParams, key, { iv: iv, mode: CryptoJS.mode.CBC, padding: CryptoJS.pad.Pkcs7 });
            return decrypted.toString(CryptoJS.enc.Utf8);
        } catch(e) { return null; }
    },
    generateRandomPassword: () => {
        return CryptoJS.lib.WordArray.random(16).toString(CryptoJS.enc.Hex);
    }
};

// 3. LOGIKA CHATU (Přiřazeno k window pro opravu ReferenceError)
let muteInterval = null;

window.chat = {
    join: (r) => {
        if (typeof net !== 'undefined') net.sendText("/join " + r);
    },

    createRoom: () => {
        ModernDialog.showInput("Nová místnost", "Název místnosti:").then(r => {
            if(r && typeof net !== 'undefined') net.sendText("/create " + r);
        });
    },

    createTempRoom: () => {
        ModernDialog.showInput("Dočasná Místnost", "Název DOČASNÉ místnosti:").then(r => {
            if (r) {
                // Pro jistotu ověříme existenci i zde
                if (!window.state.roomKeys) window.state.roomKeys = {};

                // Vygeneruje AES heslo
                window.state.roomKeys[r] = cryptoAES.generateRandomPassword();

                if (typeof net !== 'undefined') {
                    net.sendText("/temproom " + r);
                }
            }
        });
    },

    createPrivateRoom: () => {
        ModernDialog.showInput("Soukromá místnost", "Název SOUKROMÉ místnosti:").then(r => {
            if (r) {
                // Pro jistotu ověříme existenci i zde
                if (!window.state.roomKeys) window.state.roomKeys = {};

                // Vygeneruje AES heslo
                window.state.roomKeys[r] = cryptoAES.generateRandomPassword();

                if (typeof net !== 'undefined') {
                    net.sendText("/createprivate " + r);
                }
            }
        });
    },

    setMuted: (seconds) => {
        const input = document.getElementById('msg-input');
        const sendBtn = document.getElementById('send-btn') || document.querySelector('.input-wrapper .btn-icon:last-child');
        const addBtn = document.getElementById('add-btn-trigger');

        if (!input) return;
        if (muteInterval) clearInterval(muteInterval);

        input.disabled = true;
        if (sendBtn) sendBtn.style.pointerEvents = 'none';
        if (addBtn) addBtn.style.pointerEvents = 'none';

        input.style.backgroundColor = "rgba(255, 42, 85, 0.15)";
        input.style.color = "var(--danger)";

        let timeLeft = seconds;
        input.value = `⛔ JSI ZTLUMEN! Zbývá: ${timeLeft}s`;

        muteInterval = setInterval(() => {
            timeLeft--;
            input.value = `⛔ JSI ZTLUMEN! Zbývá: ${timeLeft}s`;
            if (timeLeft <= 0) {
                clearInterval(muteInterval);
                window.chat.unmuteUI();
            }
        }, 1000);
    },

    unmuteUI: () => {
        const input = document.getElementById('msg-input');
        const sendBtn = document.getElementById('send-btn') || document.querySelector('.input-wrapper .btn-icon:last-child');
        const addBtn = document.getElementById('add-btn-trigger');

        if (!input) return;
        input.disabled = false;
        if (sendBtn) sendBtn.style.pointerEvents = 'auto';
        if (addBtn) addBtn.style.pointerEvents = 'auto';

        input.style.backgroundColor = "";
        input.style.color = "";
        input.value = "";
        input.placeholder = `Přenést data do #${state.currentRoom || 'Lobby'}...`;
        input.focus();
    },

    send: () => {
        const input = document.getElementById('msg-input');
        if (!input || input.disabled) return;

        let txt = input.value.trim();
        if(!txt) return;

        // Podpora pro REPLY
        if (state.replySender && state.replyText) {
            const replyId = state.replyId ? state.replyId : "0";
            txt = `REPLY|${replyId}|${state.replySender}|${state.replyText}|${txt}`;
            if (typeof ui !== 'undefined') ui.cancelReply();
        }

        if (txt.length > 1000) {
            ModernDialog.showMessage("Chyba", "Zpráva je příliš dlouhá! (Maximum je 1000 znaků).", true);
            return;
        }

        input.value = "";
        let roomSecret = state.roomKeys[state.currentRoom] || state.currentRoom;

        if (state.privateTarget && !txt.startsWith("/")) {
            net.sendText(`/w ${state.privateTarget} ${txt}`);
        } else if (txt.startsWith("/")) {
            // Podpora pro /burn
            if (txt.toLowerCase().startsWith("/burn ")) {
                let parts = txt.split(" ");
                if (parts.length >= 3) {
                    let secretText = parts.slice(2).join(" ");
                    let encryptedMsg = cryptoAES.encrypt(secretText, roomSecret);
                    if (encryptedMsg) net.sendText(parts[0] + " " + parts[1] + " ZK:" + encryptedMsg);
                } else { net.sendText(txt); }
            } else { net.sendText(txt); }
        } else {
            // Standardní šifrovaná zpráva
            let encryptedMsg = cryptoAES.encrypt(txt, roomSecret);
            if (encryptedMsg) net.sendText("ZK:" + encryptedMsg);
        }
    },

    startTicTacToe: () => {
        const nameInput = document.getElementById('game-opponent-name');
        const name = nameInput ? nameInput.value.trim() : "";
        if (!name) { ModernDialog.showMessage("Chyba", "Musíš zadat jméno soupeře!", true); return; }
        if (name.toLowerCase() === (state.nick || "").toLowerCase()) { ModernDialog.showMessage("Chyba", "Nemůžeš vyzvat sám sebe!", true); return; }
        net.sendText(`/ttt start ${name}`);
        if (nameInput) nameInput.value = "";
        if (typeof ui !== 'undefined') ui.closeModals();
    },

    startWhiteboard: () => {
        const nameInput = document.getElementById('game-opponent-name');
        const name = nameInput ? nameInput.value.trim() : "";
        if (!name) net.sendText(`/wb room`);
        else {
            if (name.toLowerCase() === (state.nick || "").toLowerCase()) { ModernDialog.showMessage("Chyba", "Nech pole prázdné pro volné plátno!", true); return; }
            net.sendText(`/wb start ${name}`);
        }
        if (nameInput) nameInput.value = "";
        if (typeof ui !== 'undefined') ui.closeModals();
    },

    uploadFile: () => {
        const fInput = document.getElementById('file-input');
        const f = fInput ? fInput.files[0] : null;
        if(f) window.chat.uploadFileObj(f);
    },

    uploadFileObj: (f) => {
        const input = document.getElementById('msg-input');
        if (input && input.disabled) return;

        if(f.size > 5 * 1024 * 1024) { ModernDialog.showMessage("Chyba", "Soubor je příliš velký (Max 5MB)", true); return; }
        const r = new FileReader();
        r.onload = (e) => {
            const prefix = f.type.startsWith('image/') ? 'IMG' : 'FILE';
            if (state.ws) state.ws.send(`${prefix}:${state.nick}:${f.name}:${e.target.result.split(',')[1]}`);
        };
        r.readAsDataURL(f);
    }
};

// 4. EVENT LISTENERY
window.addEventListener('load', () => {
    window.onfocus = () => { state.isWindowActive = true; };
    window.onblur = () => { state.isWindowActive = false; };

    window.addEventListener('click', (event) => {
        if (event.target.classList.contains('modal')) {
            if (typeof ui !== 'undefined') ui.closeModals();
        }
        if (typeof ctx !== 'undefined' && ctx.hide) ctx.hide();
    });

    // Delegace Enteru pro vstupy (Login/Register)
    const inputs = [
        {ids: ['inp-ip', 'inp-user', 'inp-pass'], action: 'login'},
        {ids: ['reg-ip', 'reg-user', 'reg-pass', 'reg-code'], action: 'register'}
    ];

    inputs.forEach(group => {
        group.ids.forEach(id => {
            const el = document.getElementById(id);
            if(el && typeof auth !== 'undefined') {
                el.addEventListener('keyup', (ev) => {
                    if(ev.key === 'Enter') auth[group.action]();
                });
            }
        });
    });

    // Enter pro odesílání zpráv
    const msgInput = document.getElementById('msg-input');
    if (msgInput) {
        msgInput.addEventListener("keydown", (e) => {
            if(e.key === "Enter" && !e.shiftKey) {
                if (typeof appConfig !== 'undefined' && appConfig.enterToSend) {
                    e.preventDefault();
                    window.chat.send();
                }
            }
        });
    }
});