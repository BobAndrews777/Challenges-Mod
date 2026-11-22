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
| Mob-Challenge | Beim Timer-Start wird jeder nacheinander in einen zufälligen Mob verwandelt – vorher läuft 5 Sekunden ein Glücksrad mit den Mob-Namen. Leben, Größe/Hitbox, Essen, Fallschaden, Fliegen, Schwächen, Fähigkeiten auf R/G. Alle 22 Mobs inkl. Reiten (Sattel), Esel-Kiste (Schleichen + Rechtsklick), Magma-Hüpfen und -Teilen, Lava-Laufen (Schreiter/Magma Cube), Ziegen-Dash (R), Kamel-Dash (Leertaste/R). **Aussehen:** Die anderen sehen einen echten Mob statt des Spielers (Kostüm-Mob mit Namensschild, Treffer darauf gehen an den Spieler) |
| Mob Army Battle | 2 Teams (Rot/Blau, auch 1 gegen 2) farmen in eigenen Parallelwelten (gleicher Seed, eigener Nether) Mobs – Standard 2 Std. Danach baut jedes Team im Menü (`/wellen`) aus seinen Kills 3 Wellen für das andere Team (Rechtsklick +1, Linksklick −1, Reihenfolge = Spawn-Reihenfolge). Kampf gleichzeitig in zwei Arenen, Mobs spawnen nacheinander an verschiedenen Punkten. Wer zuerst alle 3 Wellen schafft, gewinnt. Tod im Kampf = 2 Min. warten (Inventar bleibt erhalten). Wer fertig ist, kommt zum Spawn und kann mit `/zuschauen` dem anderen Team zusehen. Den Fortschritt sieht jedes Team nur von sich selbst. Enderdrache zählt nicht, das End ist gesperrt. Eigene Arena-Map möglich (Konstruktionsblock). Im Kampf zerstören weder Spieler noch Explosionen (TNT, Creeper, Wither) Blöcke. Alles einstellbar im Challenge-Menü unter „Mob Army Battle“. Beim Abbauen droppen mit einstellbarer Chance Spawn-Eier |
| Split-Challenge | 2–4 Spieler in Rot/Gelb/Blau/Grün, Übersicht oben rechts, Split im zufälligen Zeitfenster mit 5-Sekunden-Countdown, seltene Joker statt eines Splits, ein Tod = alle tot. Fertige Splits: alle 24 |

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
- `/mobs an` · `/mobs aus` · `/mobs status` · `/mobs neu` · `/mobs setze <spieler> <mob>` (zum Testen)
- `/army team <spieler> rot|blau` · `/army start` · `/army weiter` (nächste Phase) · `/army stop` · `/army zeit <min>` · `/army wellenzeit <min>` · `/army eier <prozent>` · `/army kills` · `/army arena <name>` / `/army arena standard`
- `/wellen` – Wellen-Menü (für alle Spieler)
- `/zuschauen` · `/spawn` – nach dem eigenen Kampf dem anderen Team zusehen bzw. zurück zum Spawn
- `/backpack` (auch `/bp`, `/rucksack`) – gemeinsamer Team-Rucksack mit 27 Plätzen im Mob Army Battle (für alle Spieler)
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
- Mob-Challenge: Lava-Laufen, Dashes, Glücksrad und das Aussehen brauchen die Mod **bei allen Spielern in derselben Version** wie auf dem Server.
- Das eigene Mob-Modell sieht man in F5; in der Ich-Perspektive ist es ausgeblendet.
- Mob Army Battle nutzt eigene Welten der Mod. Im Einzelspieler zeigt Minecraft beim Laden evtl. eine Warnung zu „experimentellen Einstellungen“ – einfach fortfahren.

## Eigene Arena-Map (Mob Army Battle)
1. Arena bauen (am besten im Kreativmodus) und mit einem **Konstruktionsblock** (`/give @s structure_block`) im Modus „Speichern“ unter einem Namen speichern, z. B. `arena` (max. 48 × 48 × 48 Blöcke).
2. **Lapislazuliblock** = Mob-Spawnpunkt (die Mobs erscheinen darauf), **Diamantblock** = Startpunkt der Spieler. Im Kampf kann in der Arena nichts abgebaut oder gesprengt werden, Platzieren geht.
3. An Dach und Licht denken – Zombies und Skelette verbrennen sonst in der Sonne.
4. `/army arena arena` einstellen. Gebaut auf einem anderen Server/Einzelspieler? Die Datei liegt in `world/generated/minecraft/structures/arena.nbt` – auf dem Server an dieselbe Stelle kopieren.

**Mitgeliefert:** Die Arenen `desert` (klein) und `paris` (groß) stecken schon in der Mod – nichts kopieren, einfach im Menü auswählen.
