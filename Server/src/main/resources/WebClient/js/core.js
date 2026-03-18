// --- BEZPEČNOSTNÍ KONTROLA STRÁNKY ---
// Pokud skript běží na indexu nebo jiné stránce bez auth-screen, inicializujeme aspoň základ
if (!document.getElementById('auth-screen') && window.location.pathname.includes('app.html')) {
    console.warn("Varování: Prvky rozhraní nenalezeny.");
}

// --- KONFIGURACE KLIENTA ---
const appConfig = {
    sound: true,
    enterToSend: true,
    load: () => {
        let saved = localStorage.getItem('lanChatConfig');
        if(saved) {
            try {
                let parsed = JSON.parse(saved);
                appConfig.sound = parsed.sound !== undefined ? parsed.sound : true;
                appConfig.enterToSend = parsed.enterToSend !== undefined ? parsed.enterToSend : true;
            } catch(e) { console.error("Chyba při načítání configu"); }
        }
    },
    save: () => {
        localStorage.setItem('lanChatConfig', JSON.stringify({
            sound: appConfig.sound,
            enterToSend: appConfig.enterToSend
        }));
    }
};
appConfig.load();

// --- GLOBÁLNÍ STAV (STATE) ---
// Inicializujeme všechna pole, aby ui.js a network.js nikdy nenarazily na undefined
const state = {
    ws: null,
    nick: "",
    isAdmin: false,
    currentRoom: "Lobby",
    roomKeys: {},        // KLÍČOVÉ: Zde budou uloženy AES klíče místností
    activeContextUser: null,
    activeMessageId: null,
    activeMessageText: null,
    privateTarget: null,
    users: [],
    avatars: {},
    targetUser: "",
    isWindowActive: true,
    serverPublicKey: null,
    encryptor: new JSEncrypt(),
    pendingInviteId: null,
    myRsa: null          // Bude naplněno v app.js
};

// --- POMOCNÉ FUNKCE ---
const utils = {
    playNotification: () => {
        if (!state.isWindowActive && appConfig.sound) {
            const a = document.getElementById('notify-sound');
            if(a) a.play().catch(()=>{});
        }
    },
    markdown: (text) => {
        if (!text) return "";
        // Základní ošetření XSS a formátování
        return text
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/\*\*(.*?)\*\*/g, '<b style="color:#fff;">$1</b>')
            .replace(/\*(.*?)\*/g, '<i>$1</i>')
            .replace(/`(.*?)`/g, '<span style="background:#1e1f22; padding:3px 5px; border-radius:4px; font-family:monospace;">$1</span>')
            .replace(/(https?:\/\/[^\s]+)/g, '<a href="$1" target="_blank" style="color:#00a8fc;">$1</a>');
    }
};

// Pojistka pro starší prohlížeče nebo asynchronní načítání
window.state = state;