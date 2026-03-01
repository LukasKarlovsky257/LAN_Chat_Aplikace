/* =========================================
   1. CORE - Nastavení, Stav a Šifrování
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

const state = { ws: null, nick: "", isAdmin: false, currentRoom: "Lobby", activeContextUser: null, activeMessageId: null, privateTarget: null, users: [], avatars: {}, targetUser: "", isWindowActive: true, serverPublicKey: null, encryptor: new JSEncrypt(), pendingInviteId: null };

// 🔥 ZERO-KNOWLEDGE MOTOR
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

const utils = {
    playNotification: () => { if (!state.isWindowActive && appConfig.sound) { const a = document.getElementById('notify-sound'); if(a) a.play().catch(()=>{}); } },
    markdown: (text) => { return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/\*\*(.*?)\*\*/g, '<b style="color:#fff;">$1</b>').replace(/\*(.*?)\*/g, '<i>$1</i>').replace(/`(.*?)`/g, '<span style="background:#1e1f22; padding:3px 5px; border-radius:4px; font-family:monospace;">$1</span>').replace(/(https?:\/\/[^\s]+)/g, '<a href="$1" target="_blank" style="color:#00a8fc;">$1</a>'); }
};