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

const utils = {
    playNotification: () => { if (!state.isWindowActive && appConfig.sound) { const a = document.getElementById('notify-sound'); if(a) a.play().catch(()=>{}); } },
    markdown: (text) => { return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/\*\*(.*?)\*\*/g, '<b style="color:#fff;">$1</b>').replace(/\*(.*?)\*/g, '<i>$1</i>').replace(/`(.*?)`/g, '<span style="background:#1e1f22; padding:3px 5px; border-radius:4px; font-family:monospace;">$1</span>').replace(/(https?:\/\/[^\s]+)/g, '<a href="$1" target="_blank" style="color:#00a8fc;">$1</a>'); }
};