const ModernDialog = {
    init() {
        this.overlay = document.getElementById('modern-modal-overlay');
        this.title = document.getElementById('modern-modal-title');
        this.message = document.getElementById('modern-modal-message');
        this.input = document.getElementById('modern-modal-input');
        this.btnCancel = document.getElementById('modern-modal-btn-cancel');
        this.btnConfirm = document.getElementById('modern-modal-btn-confirm');

        document.getElementById('modern-modal-close').onclick = () => this.hide();
    },

    hide() {
        this.overlay.classList.add('modal-hidden');
        this.btnCancel.onclick = null;
        this.btnConfirm.onclick = null;
    },

    show({ title, message, type = 'alert', isError = false }) {
        return new Promise((resolve) => {
            this.title.textContent = title;
            this.title.style.color = isError ? '#ff2a55' : '#00f3ff';
            this.message.innerHTML = message;

            this.input.style.display = 'none';
            this.input.value = '';
            this.btnCancel.style.display = 'none';
            this.btnConfirm.className = 'modal-btn primary';
            if (isError) this.btnConfirm.classList.add('danger');

            if (type === 'alert') {
                this.btnConfirm.textContent = 'Rozumím';
                this.btnConfirm.onclick = () => { this.hide(); resolve(true); };
            }
            else if (type === 'confirm') {
                this.btnCancel.style.display = 'inline-block';
                this.btnCancel.textContent = 'Zrušit';
                this.btnConfirm.textContent = 'Potvrdit';
                if (isError) this.btnConfirm.className = 'modal-btn danger';

                this.btnCancel.onclick = () => { this.hide(); resolve(false); };
                this.btnConfirm.onclick = () => { this.hide(); resolve(true); };
            }
            else if (type === 'prompt') {
                this.input.style.display = 'block';
                this.btnCancel.style.display = 'inline-block';
                this.btnCancel.textContent = 'Zrušit';
                this.btnConfirm.textContent = 'Potvrdit';

                this.btnCancel.onclick = () => { this.hide(); resolve(null); };
                this.btnConfirm.onclick = () => { this.hide(); resolve(this.input.value); };

                // Umožní odeslání Entrem
                this.input.onkeydown = (e) => { if(e.key === 'Enter') this.btnConfirm.click(); };
            }

            this.overlay.classList.remove('modal-hidden');
            if (type === 'prompt') setTimeout(() => this.input.focus(), 100);
        });
    },

    showMessage(title, message, isError = false) {
        return this.show({ title, message, type: 'alert', isError });
    },
    showConfirm(title, message, isDanger = false) {
        return this.show({ title, message, type: 'confirm', isError: isDanger });
    },
    showInput(title, message) {
        return this.show({ title, message, type: 'prompt' });
    }
};

// Inicializace při načtení stránky
document.addEventListener('DOMContentLoaded', () => ModernDialog.init());