const chat = {
    join: (r) => net.sendText("/join " + r),
    createRoom: () => {
        ModernDialog.showInput("Nová Místnost", "Název místnosti:").then(r => {
            if(r) net.sendText("/create " + r);
        });
    },
    createTempRoom: () => {
        ModernDialog.showInput("Dočasná Místnost", "Název dočasné místnosti:").then(r => {
            if(r) net.sendText("/temproom " + r);
        });
    },
    createPrivateRoom: () => {
        ModernDialog.showInput("Soukromá Místnost", "Název SOUKROMÉ místnosti:").then(r => {
            if(r) net.sendText("/createprivate " + r);
        });
    },

    send: () => {
        const input = document.getElementById('msg-input');
        let txt = input.value.trim();

        if(!txt) return;

        if (state.replySender && state.replyText) {
            const replyId = state.replyId ? state.replyId : "0";
            txt = `REPLY|${replyId}|${state.replySender}|${state.replyText}|${txt}`;
            ui.cancelReply();
        }

        if (txt.length > 1000) {
            ModernDialog.showMessage("Chyba", "Zpráva je příliš dlouhá! (Maximum je 1000 znaků).", true);
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
        if (!name) { ModernDialog.showMessage("Chyba", "Musíš zadat jméno soupeře!", true); return; }
        if (name.toLowerCase() === state.nick.toLowerCase()) { ModernDialog.showMessage("Chyba", "Nemůžeš vyzvat sám sebe!", true); return; }
        net.sendText(`/ttt start ${name}`);
        document.getElementById('game-opponent-name').value = "";
        ui.closeModals();
    },
    startWhiteboard: () => {
        const name = document.getElementById('game-opponent-name').value.trim();
        if (!name) net.sendText(`/wb room`);
        else {
            if (name.toLowerCase() === state.nick.toLowerCase()) { ModernDialog.showMessage("Chyba", "Nech pole prázdné pro volné plátno!", true); return; }
            net.sendText(`/wb start ${name}`);
        }
        document.getElementById('game-opponent-name').value = "";
        ui.closeModals();
    },
    uploadFile: () => { const f = document.getElementById('file-input').files[0]; if(f) chat.uploadFileObj(f); },
    uploadFileObj: (f) => {
        if(f.size > 5 * 1024 * 1024) { ModernDialog.showMessage("Chyba", "Soubor je příliš velký (Max 5MB)", true); return; }
        const r = new FileReader();
        r.onload = (e) => {
            const prefix = f.type.startsWith('image/') ? 'IMG' : 'FILE';
            state.ws.send(`${prefix}:${state.nick}:${f.name}:${e.target.result.split(',')[1]}`);
        };
        r.readAsDataURL(f);
    }
};

window.onfocus = () => { state.isWindowActive = true; };
window.onblur = () => { state.isWindowActive = false; };
window.addEventListener('click', (event) => {
    if (event.target.classList.contains('modal')) ui.closeModals();
    ctx.hide();
});

['inp-ip', 'inp-user', 'inp-pass'].forEach(id => { let e = document.getElementById(id); if(e) e.addEventListener('keyup', (ev) => { if(ev.key === 'Enter') auth.login(); }); });
['reg-ip', 'reg-user', 'reg-pass', 'reg-code'].forEach(id => { let e = document.getElementById(id); if(e) e.addEventListener('keyup', (ev) => { if(ev.key === 'Enter') auth.register(); }); });

document.getElementById('msg-input').addEventListener("keydown", (e) => {
    if(e.key === "Enter" && appConfig.enterToSend) {
        e.preventDefault();
        chat.send();
    }
});