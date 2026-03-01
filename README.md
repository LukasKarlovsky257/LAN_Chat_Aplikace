# 🚀 LAN CHAT ULTIMATE v7.0

Moderní, plně synchronizovaná a bezpečná multiplatformní chatovací aplikace. Spojuje nativní **Java Desktop** klient a moderní **Webové rozhraní** do jednoho plynulého ekosystému s podporou real-time mini-her a Zero-Knowledge šifrováním.

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)
![WebSockets](https://img.shields.io/badge/WebSockets-010101?style=for-the-badge&logo=socket.io&logoColor=white)

---

## 🌟 Hlavní funkce

* **🔐 Zero-Knowledge (E2E) Šifrování:** Zprávy jsou šifrovány pomocí AES přímo u klienta. Klíčem je název místnosti. Server zprávy nedokáže přečíst, funguje pouze jako doručovatel. Přenos klíčů a příkazů je chráněn RSA.
* **💻 Multiplatformní podpora:** Plná synchronizace mezi nativní Java (Swing) aplikací a Webem (HTML/JS) v reálném čase.
* **🎨 Real-time Sdílené plátno (Whiteboard):** Kresli společně s ostatními uživateli. Podpora barev, gumování a plynulé synchronizace čar napříč platformami.
* **🎮 Piškvorky (Tic-Tac-Toe):** Vyzvi kohokoliv v místnosti na interaktivní partičku piškvorek přímo v chatu.
* **🔥 Samozničující zprávy:** Pošli tajnou zprávu s odpočtem, která po přečtení a uplynutí času trvale zmizí u všech klientů i na serveru.
* **📁 Sdílení médií:** Podpora posílání obrázků, GIFů (integrace Giphy) a souborů s vizuálními notifikacemi.
* **📺 YouTube integrace:** Vložení YouTube odkazu vygeneruje náhled přímo v chatu s možností přehrát video v okně (JavaFX).
* **🛡️ Robustní administrace:** Administrátoři mohou uživatele vyhazovat, umlčovat, banovat nebo mazat zprávy i celé místnosti.

---

## 🛠️ Architektura a Technologie

Aplikace je rozdělena na tři hlavní části:
1.  **WebSocket & HTTP Server (Java):** Stará se o routing zpráv, broadcast, ukládání historie do SQLite a hostování webového klienta na portu `8080`.
2.  **Desktopový Klient (Java Swing):** Nativní UI s podporou notifikací, custom bublin (`RoundedBubblePanel`), zalamování textu a JavaFX pro renderování webového obsahu.
3.  **Webový Klient (HTML/JS/CSS):** Lehký prohlížečový klient s plnou podporou WebSocketů, Crypto.js (pro AES), JSEncrypt (pro RSA) a interaktivním vykreslováním Canvasu (plátna).

---

## 💬 Příkazy v chatu

Uživatelé mohou v chatu využívat následující `/` příkazy:

| Příkaz | Popis |
| :--- | :--- |
| `/w [nick] [zpráva]` | Zašle soukromou zprávu (šepot) konkrétnímu uživateli. |
| `/join [místnost]` | Připojí tě do existující nebo vytvoří novou veřejnou místnost. |
| `/temproom [název]` | Vytvoří dočasnou místnost (smaže se, když všichni odejdou). |
| `/createprivate [název]`| Vytvoří uzamčenou privátní místnost. |
| `/roominvite [nick]` | Pozve uživatele do tvé privátní místnosti. |
| `/burn [sekundy] [text]`| Pošle zašifrovanou zprávu, která se po zadaném čase zničí. |
| `/ttt start [nick]` | Vyzve hráče na partičku Piškvorek. |
| `/wb start [nick]` | Otevře s daným hráčem sdílené plátno pro kreslení. |
| `/wb room` | Otevře volné plátno přístupné všem v místnosti. |

### 🛑 Admin příkazy
*(Vyžadují oprávnění administrátora v databázi)*
* `/kick [nick]` - Vyhodí hráče ze serveru.
* `/mute [nick] [sekundy]` - Ztlumí hráče na určitý čas.
* `/ban [nick] [důvod]` - Zablokuje uživateli přístup na server.
* `/delmsg [id]` - Odstraní konkrétní zprávu všem uživatelům.
* `/deleteroom [název]` - Kompletně smaže místnost a její historii.
* `/broadcast [zpráva]` - Rozešle globální systémové oznámení všem.

---

## 🚀 Jak spustit projekt

### 1. Server
1. Zkompilujte a spusťte hlavní třídu `Server.java`.
2. Server automaticky spustí WebSocket na portu `8887` a Webový HTTP server na portu `8080`.

### 2. Desktopový klient
1. Spusťte třídu `Klient.java`.
2. Vyplňte IP adresu serveru (např. `localhost`), jméno a heslo.
3. Chatujte a hrajte!

### 3. Webový klient
1. Otevřete webový prohlížeč a zadejte `http://localhost:8080` (nebo IP adresu serveru).
2. Přihlaste se svými údaji.
3. Webový klient je plně synchronizován s desktopovou verzí. Pro přístup k Admin logům navštivte `http://localhost:8080/admin`.
