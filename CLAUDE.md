# GatesPlus – instructies voor Claude

Fabric-mod voor **Minecraft 26.3** (Java 25, Fabric Loader 0.20.1, Loom 1.19). Spelers bouwen poorten van (bijna) elk blok die met redstone vloeiend open- en dichtgaan. `README.md` is de **gebruikershandleiding** (voor spelers/serverbeheerders); dit bestand is voor ontwikkelen.

Gebruik de project-skill **`gatesplus-dev`** (`.claude/skills/gatesplus-dev/`) bij elke code-wijziging.

## Werkafspraken (verplicht)

- **Taal:** communiceer met de gebruiker in het **Nederlands**. Code, commentaar en log-meldingen in het Engels; spelersmeldingen via de taalbestanden (nl + en).
- **Branches:** `main`, `staging`, `wip`. Ontwikkelwerk wordt gecommit en gepusht naar **`wip`**. Raak `main`/`staging` niet aan tenzij de gebruiker het vraagt. Controleer `git branch --show-current` vóór elke commit.
- **Commits:** na elke afgeronde wijziging die bouwt: commit (Nederlandse titel + korte opsomming) en push naar `origin wip`. Eindig met de Co-Authored-By-regel uit de systeeminstructies.
- **Geen EULA accepteren** in naam van de gebruiker en geen dedicated server starten die dat vereist. In-game testen doet de gebruiker met `./gradlew runClient`; zeg eerlijk wat niet in het spel getest is.
- **Twijfel over gedrag:** vraag het (AskUserQuestion) in plaats van te gokken. Meerdere keren is gebleken dat een korte vraag een verkeerde aanpak voorkomt (bv. sluitanimatie-richting, dev-modus).
- **Werkmap niet zomaar terugdraaien:** onverwachte wijzigingen eerst bekijken (`git diff`), niet overschrijven.
- **Documentatie bijhouden:** verandert gedrag, een commando, permissie of config-optie, werk dan `README.md` mee bij. Nieuwe ontwerpbeslissingen van de gebruiker komen in de lijst hieronder.

## Bouwen en testen

```bash
./gradlew build        # compileert, draait unit-tests, maakt build/libs/gatesplus-<versie>.jar
./gradlew test         # alleen tests (JUnit 6)
./gradlew runClient    # testclient, speler heet altijd "Dev" (vaste UUID), max 5 GB heap
```

- Tests: `src/test/java/...` – config, opslag (SQLite in tempdir), Gate-tellers, sessie-stack, animatie-timing, taalbestanden (`LangFilesTest` controleert dat elke gebruikte sleutel in nl.yml staat en nl/en dezelfde sleutels hebben).
- Testlogs komen in `logs/` (genegeerd door git). Dev-data: `run/config/gatesplus/` (database read-only bekijken mag, met `sqlite3 'file:...?mode=ro'` via Python).
- Minecraft 26.x is **niet geobfusceerd**: API opzoeken met `javap` op de jars in `~/.gradle/caches/fabric-loom/26.3/` – zie het script in de skill. Namen wijken af van oudere versies (bv. `EntityTypes.BLOCK_DISPLAY`, `Blocks.STAINED_GLASS.lime()`, `GuiGraphicsExtractor`, gizmos voor wereld-overlays, `ContainerInput` i.p.v. `ClickType`).

## Architectuur (`src/main/java/be/jorisclaes/gatesplus/`)

| Pakket | Rol |
|---|---|
| `GatesPlus` | Entrypoint: config, opslag, events, ticks, commando's. |
| `config/` | `config.yml` (SnakeYAML) → immutable `GatesPlusConfig`; standaardbestand in `resources/gatesplus/config.yml`. |
| `storage/` | `SqlGateStorage` (SQLite/MySQL via HikariCP), `SchemaMigrator` (genummerde migraties, tabelprefix `gp_`), `AsyncStorage` (alle DB-werk op één achtergrondthread). |
| `gate/` | `Gate` (pure data), `GateManager` (in-memory index: id, scope+naam, chunk-index, actieve set), `GateService` (alle wereldacties), `BlockFilter`, `Materials`, `ReturnChestTracker`, `InfoPanel`, `GateGeometry`. |
| `group/` | Groepen + rechtenregels (`canEdit`, `canRemove`). |
| `session/` | `CreateSession` (selectie + eigenschappen + undo-stack), `SessionManager` (klikken, markers, info-venster, timeout). |
| `animation/` | `GateAnimator`: BlockDisplay-entities, rij per rij. |
| `redstone/` | `GateTicker`: controleert trigger-blokken van actieve poorten. |
| `protection/` + `mixin/` | Onbreekbaar/vuurvast/vallende blokken/explosies/pistons; vergrendelde bijl en markers. |
| `marker/` | Eigen blokken `marker_top_left` / `marker_bottom_right`. |
| `network/` | Payloads naar de client (selectie, panelen). |
| `command/` | `/gp` en `/gp group`. |
| `util/` | `Messages` (nl/en), `Perms` (permissie-nodes), `DevStats`. |
| client (`src/client/...`) | `SelectionRenderer` (gizmos), `GatePanelsHud` (panelen rechtsboven). |

## Ontwerpbeslissingen van de gebruiker (niet zonder overleg wijzigen)

**Aanmaken en selecteren**
- Poort maken: `/gp create` → bijl (houten bijl met CustomData-tag) → hoeken → `/gp set trigger` → `/gp confirm <naam>`. Elke poort is altijd aanpasbaar met `/gp edit <id|naam>`; `/gp set ...` werkt alleen in een create/edit-sessie en wordt pas bij `/gp confirm` opgeslagen.
- Linkerklik op een gewoon blok = hoek op het **luchtblok ervoor**; linkerklik op een markerblok = hoek **op de marker**. Rechterklik = laatste punt weg (trigger → hoek 2 → hoek 1).
- Markerblokken: `/gp get markerblock` geeft er **2** (linksboven "1" goud = hoek 1, rechtsonder "2" rood = hoek 2), **alleen in een sessie**. Een marker **plaatsen zet meteen zijn hoek** (zoals klikken met de bijl); hoogstens één "1" en één "2" in de wereld: opnieuw plaatsen verhuist de marker (oude verdwijnt). Hoek zetten bij plaatsen is instelbaar in config (`session.marker-sets-corner`, standaard aan). In een edit-sessie mag een marker een blok van de bewerkte poort **tijdelijk vervangen** (rechtsklik op het poortblok); bij confirm/cancel/kapotslaan komt het poortblok terug, zonder betaling of "kapot". Marker kapotslaan haalt ook die hoek uit de selectie (trigger valt mee weg, zoals bij undo) en geeft het markeritem terug in de inventory van de sessie-eigenaar (geen drop; niet als die er van die hoek nog een heeft, bv. in creative). Einde sessie: markers uit inventory én wereld weg. Markers in de poort verdwijnen bij bouwen. **Client-mod is optioneel**: config `session.custom-marker-blocks` (standaard **uit**) = markers zijn vanilla gele/rode beton (item met CustomData-tag), in de wereld herkend aan hun **positie** in de sessie, met zwevend "1"/"2" (TextDisplay, `MarkerLabels`); kapotslaan (één klik, `AttackBlockCallback`) laat niets vallen, pistons/explosies laten ze staan (na een geweigerde sticky-piston-trek stuurt de server de blokken opnieuw, want de client voorspelt het trekken). **Met client-mod** toont de client de beton lokaal als het eigen markerblok (`MarkersPayload` + `MarkerSkin`) en krijgt hij het zwevende label niet (`EntityMixin.broadcastToPlayer`); ook het item krijgt daar het model van het eigen markerblok (client-mixin `ItemModelResolverMixin`, `gatesplus.client.mixins.json`). Aan = eigen mod-blokken ⇒ client-mod verplicht voor elke speler (registratie bij opstart; een fysieke client registreert ze altijd).
- Bijl en markers zijn vergrendeld (niet droppen, niet in containers, niet aan item frames); weg bij confirm/cancel/uitloggen/dood/timeout.
- Poort bouwt **alleen in lucht** (markers tellen als vrij); er wordt nooit een bestaand blok vervangen (anti-minen). Blokken in de weg worden **live** gemarkeerd tijdens de sessie (elke 10 ticks herberekend); de speler haalt ze zelf weg. Zonder client-mod: groen randglas nooit over een echt blok, blokken in de weg krijgen een rode gloei-omlijning (nep-BlockDisplay alleen voor die speler, `SessionManager.showHighlights`).
- Id globaal uniek; naam uniek per scope (persoonlijk of groep). `/gp remove` (niet `rm`).

**Werking**
- **Eén trigger-blok per poort**, max `max-trigger-distance` van de poort. Standaard **dicht** zonder stroom; `inverted` instelbaar (per poort, uit te zetten in config).
- Trigger vernietigd → poort gaat **open** (NO_TRIGGER). Blok teruggezet → werkt **meteen** weer.
- Spelers/mobs in de opening: config `redstone.entity-in-gate` = `suffocate` (standaard, sluit toch), `push` (uit de poort duwen) of `wait` (oud gedrag). Items/voertuigen houden de poort altijd tegen.
- Blok in de opening: de poort sluit **tot aan dat blok** (per lijn in sluitrichting, nooit vervangen) en sluit verder zodra het weg is; vastgehouden posities zitten in geheugen (`GateService.heldBack`), na herstart herberekend.
- Alleen poorten in **actieve (tickende) chunks** worden gecontroleerd (performantie).

**Animatie**
- Rij per rij; de **volgende rij start pas als de vorige klaar is** (openen én sluiten).
- Verticaal openen: van onder naar boven, elke rij trekt zich omhoog terug (gebruiker vindt dit goed).
- Verticaal sluiten: **van boven naar beneden**, 1,5× trager per rij dan openen; rijen verschijnen pas als ze aan de beurt zijn (een platgedrukte display is anders zichtbaar als vlak); elke rij wordt een echt blok zodra ze klaar is.
- Na elke rij 2 ticks "settle" voordat de display wordt vervangen/verwijderd. Bij sluiten blijft de display nog 3 ticks over het echte blok staan (iets groter, vaste lichtwaarde) tegen flikkeren.
- Animatie globaal uit te zetten in config (dan kan niemand ze aanzetten) en per poort.

**Kapot / onvolledig / blokken**
- Kapot: de poort blijft **staan zoals ze is** (gat blijft gat), werkt niet meer tot herstel. **Geen chatmelding** bij kapot (alleen in dev-modus); `/gp list` toont de naam **groen** (OK) of **rood** (niet OK).
- Onvolledig (te weinig blokken bij bouwen): het betaalde deel wordt gebouwd (onder → boven), werkt pas als het af is.
- `/gp repair [id|naam]` plaatst de ontbrekende blokken echt (onder → boven); zonder naam: de poort van je eigen edit-sessie. Gat met de hand vullen met het juiste blok telt ook.
- Betalen met blokken (uit te zetten). Alleen **betaalde** blokken komen terug (`paid_count`); **creative bouwt gratis en krijgt niets terug**. Meer dan 60 items terug → vanilla-kist die verdwijnt als ze leeg is (breekbaar, normale spellogica).
- Blokken die niet stabiel kunnen blijven, worden gefilterd; vallende blokken worden vastgezet (mixin).

**Bescherming**
- Onbreekbaar en vuurvast per poort (`/gp set indestructible|fireproof on|off`), standaard aan.
- Vuurvast: geen vuur tegen de poort mogelijk (aansteken, verspreiden, opbranden van buren).
- Het trigger-blok is **niet** beschermd.
- Melding "onbreekbaar" alleen in **dev-modus** (= `/gp devstats` aan voor die speler).

**Groepen en rechten**
- Groepsleden mogen bewerken en herstellen, **niet verwijderen**; alleen de poorteigenaar of admin verwijdert.
- Rechten via fabric-permissions-api (LuckPerms); nodes in `util/Perms.java` en de README-tabel.

**Info en dev**
- Zonder client-mod: selectie als nepblokken, info-venster en dev-paneel in de **scorebord-zijbalk** (alleen pakketten naar die speler, `util/Panels`); de zijbalk past maar 15 regels, dus tijdens een sessie toont ze alleen het info-venster, anders het dev-paneel.
- Info-venster rechtsboven tijdens create/edit (naam/id, blok, trigger, hoeken, blokken x / y, status, bescherming, animatie, redstone, groep).
- `/gp devstats` = dev-paneel + dev-modus (extra meldingen).

**Overig**
- Config in YAML, opslag SQLite of MySQL met schemaversie. Standaardtaal van de meldingen is **Engels** (`language: en`); `nl` en `auto` instelbaar.
- `runClient` gebruikt een vaste naam `Dev` en `-Xmx5G` (gekozen door de gebruiker, 15 GB RAM in de pc).
