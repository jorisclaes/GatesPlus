---
name: gatesplus-dev
description: Werkwijze voor elke wijziging aan de GatesPlus Fabric-mod (Minecraft 26.3) - code, commando's, animatie, bescherming, taalbestanden, config, opslag of documentatie. Gebruik deze skill vóór je code in dit project aanpast, een Minecraft/Fabric-API opzoekt, of commit/pusht.
---

# GatesPlus ontwikkelen

Lees eerst `CLAUDE.md` (werkafspraken + ontwerpbeslissingen van de gebruiker). Antwoord de gebruiker in het Nederlands.

## 1. Voor je begint

```bash
git branch --show-current   # moet "wip" zijn; anders eerst vragen
git status --short          # onverwachte wijzigingen? eerst `git diff` bekijken, niets overschrijven
```

- Is het gevraagde gedrag dubbelzinnig of strijdig met een ontwerpbeslissing in `CLAUDE.md`? Stel één korte vraag (AskUserQuestion) met concrete opties voordat je bouwt.

## 2. Minecraft- en Fabric-API opzoeken

Minecraft 26.x is niet geobfusceerd; gebruik javap in plaats van te gokken (veel namen verschillen van oudere versies):

```bash
S=.claude/skills/gatesplus-dev/scripts/mcjavap.sh
$S --find FireBlock                                   # klassenaam zoeken
$S net.minecraft.world.level.Level | grep hasChunk    # publieke methodes
$S -p net.minecraft.world.entity.Display | grep Inter # ook private (voor mixins/@Invoker)
$S -p -c net.minecraft.world.level.block.FireBlock    # bytecode: wat roept een methode aan
```

Controleer bij een mixin altijd de exacte signatuur (parameters, static, private) en of een setter `SynchedEntityData.set(..., true)` forceert.

Bekende 26.3-namen: `EntityTypes.BLOCK_DISPLAY`, `Blocks.STAINED_GLASS.lime()`, `ContainerInput` (was `ClickType`), `GuiGraphicsExtractor` + `HudElementRegistry` (HUD), `LevelRenderEvents.BEFORE_GIZMOS` + `Gizmos.cuboid/line` (wereld-overlays), `ResourceKey.identifier()`, `ChunkPos.pack(...)`, `Display`-setters zijn private (zie `mixin/DisplayAccessor`).

## 3. Code-regels

- Alle wereldacties via `GateService`; gegevens nooit rechtstreeks in de database vanuit de tick (gebruik `GateService.save` / `AsyncStorage`).
- Wijzig geometrie/naam/groep/blok van een poort alleen via `GateManager.update(gate, copy)` zodat de indexen kloppen.
- Hot paths (mixins, ticker) gebruiken `GateManager.gateAt` / `Protection.gateBlock` (één hash-lookup); geen lussen over alle poorten.
- Nieuwe spelersmelding: sleutel in **zowel** `src/main/resources/gatesplus/lang/nl.yml` als `en.yml`. Quote YAML-sleutels `"on"`, `"off"`, `"yes"`, `"no"`. Let op: in sed betekent `&` in de vervanging "de gevonden tekst" – gebruik Python om taalbestanden te bewerken.
- Nieuw commando / permissie / config-optie → ook `README.md` (gebruikershandleiding) bijwerken; nieuwe gebruikersbeslissing → lijst in `CLAUDE.md`.
- Nieuwe permissie: enum in `util/Perms.java` + tabel in `README.md`.
- Schema-wijziging: nieuwe migratie toevoegen aan `SchemaMigrator.MIGRATIONS` (nooit V1 aanpassen).

## 4. Bouwen en testen

```bash
./gradlew build                     # moet slagen; draait ook alle unit-tests
grep -h -o 'failures="[0-9]*" errors="[0-9]*"' build/test-results/test/*.xml | sort | uniq -c
```

- Voeg een unit-test toe voor pure logica (Gate-tellers, sessie-stack, animatie-timing, config, opslag).
- **Start geen dedicated server** en accepteer nooit de EULA. In-game testen doet de gebruiker met `./gradlew runClient` (speler `Dev`). Zeg in je antwoord eerlijk wat niet in het spel getest is en geef korte teststappen.

## 5. Afronden

```bash
git add -A
git commit -F - <<'MSG'
<Nederlandse titel>

- <wat en waarom, kort>

Co-Authored-By: <regel uit de systeeminstructies>
MSG
git push origin wip
```

Antwoord daarna kort in het Nederlands: wat er veranderd is, wat de speler merkt, wat (niet) getest is, en eventuele keuzes die de gebruiker nog kan maken.
