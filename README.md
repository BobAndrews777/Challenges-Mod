# Challenges – Fabric-Mod für Minecraft 26.3

Challenges im Stil der bekannten YouTube-Challenge-Videos für deinen privaten Fabric-Server.
Die normalen Challenges laufen rein auf dem Server. Für die **Split-Challenge** (Übersicht oben rechts,
Stack-Limit, Platzier-Reihenfolge) muss die Mod **auch bei allen Spielern** installiert sein
(zusammen mit Fabric API).

## Was drin ist

| Bereich | Challenges |
|---|---|
| Grundfunktionen | Timer in der Actionbar, Ziel „Enderdrache besiegen“, „Ein Tod = Ende“ (alle werden Zuschauer), Pause-Schutz, UHC (keine natürliche Regeneration), geteilte Herzen |
| Zufall | Zufällige Block-Drops, zufällige Mob-Drops (jeweils immer gleich pro Item), alle X Sekunden ein Zufalls-Item |
| Schaden | Schaden bei jedem Sprung (einstellbar), Tod beim Schleichen |
| Force | Force Block, Force Mob, Force Item – mit einstellbarem Zeitlimit |
| Welt | `/challenge reset` löscht die Welt und erstellt beim nächsten Start eine neue |
| Split-Challenge | 2–4 Spieler in Rot/Gelb/Blau/Grün, Übersicht oben rechts, Split im zufälligen Zeitfenster mit 5-Sekunden-Countdown, seltene Joker statt eines Splits, ein Tod = alle tot. Fertige Splits: Level, Herzen, Hunger, Mob Damage, Item-Loot, Betten, Platzieren, Atemluft, Waffen, Netherportale, Chunks, Stacks, Werkzeuge, Enderperlen |

## Befehle (OP nötig)

- `/challenge` – öffnet das Einstellungs-Menü (Truhen-GUI)
- `/challenge status` – zeigt Timer und aktive Challenges
- `/challenge reset` – neue Welt (mit Bestätigung, stoppt den Server)
- `/timer start` · `/timer pause` · `/timer reset`
- `/split an` · `/split aus` · `/split status`
- `/split zeit <min> <max>` – Zeitfenster für neue Splits in Minuten
- `/split jokerchance <prozent>` – wie oft statt eines Splits ein Joker kommt (Standard 10 %)
- `/split farben` – Farben neu verteilen
- `/split test <split>` – einen Split sofort aktivieren (zum Ausprobieren)
- `/joker` – aktive Splits mit Joker-Knopf · `/joker <split>` – Split für immer abschalten

Mach dich vorher in der Server-Konsole zum OP: `op DeinName`

## Die .jar bauen

### Weg A: über GitHub (kein Java auf dem PC nötig)
1. Auf github.com einloggen → **New repository** → Name z. B. `challenges` → *Create*.
2. Auf der leeren Repo-Seite **uploading an existing file** anklicken und den **Inhalt** dieses Ordners
   (inklusive `.github`, `gradle`, `src` usw.) am PC per Drag & Drop hineinziehen → *Commit changes*.
3. Oben auf **Actions** klicken → der Lauf „Mod bauen“ startet von selbst (ca. 3–5 Minuten).
4. Wenn er grün ist: Lauf anklicken → unten unter *Artifacts* **challenges-mod** herunterladen.
   Darin steckt `challenges-1.0.0.jar`.

### Weg B: lokal am PC
1. Java 25 installieren (z. B. *Eclipse Temurin 25* von adoptium.net).
2. Im Ordner ein Terminal öffnen und `gradlew.bat build` (Windows) bzw. `./gradlew build` ausführen.
3. Die fertige Mod liegt in `build/libs/challenges-1.0.0.jar`.

## Auf dem Server installieren
In den `mods`-Ordner des Fabric-Servers (26.3) gehören:
- **Fabric API** für 26.3 (von Modrinth)
- **challenges-1.0.0.jar**

Server starten, joinen, `/challenge` eingeben.

## Hinweise
- Nach `/challenge reset` stoppt der Server. Startet dein Hoster nicht automatisch neu, starte ihn einfach von Hand.
- Einstellungen und Timer stehen in `config/challenges.json`.
- Bukkit-/Spigot-/Paper-Plugins laufen grundsätzlich **nicht** auf einem Fabric-Server.
