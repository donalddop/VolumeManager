# PLAN.md — VolumeManager

Projectgeheugen. Elke sessie begint bij nul en leest dit bestand eerst.

## Wat is dit

Android-app (Kotlin, Jetpack Compose) om het volume van elke app apart te regelen.
Gebruikt Shizuku voor toegang tot privileged audio-APIs. Vereist Android 13 (minSdk 33).

- Fork van `yume-chan/VolumeManager` (remote `upstream`); Donalds werk staat op `origin` = `donalddop/VolumeManager`.
- Package: `moe.chensi.volume` · compileSdk 36 · targetSdk 35
- Build: `.\gradlew assembleDebug` en `.\gradlew test` (Gradle wrapper, Java 21)
- CI: `.github/workflows/build.yml`

## Waar de app nu staat

Werkend en in gebruik. Opgebouwd bovenop de upstream-app:

- Perceptuele volumecurve (`data/VolumeCurve.kt`, 40 dB slider-curve)
- Instellingenscherm: paneel, positie, volumetoetsen, zwevende knop (`SettingsActivity.kt`)
- Compact paneel in One UI-stijl: brede tracksliders met het icoon in de track, opent ingeklapt met een chevron, ringslider volgt de tril-/stilstand en is aan meldingen gelinkt, uitvoerkiezer en tandwiel in de kop (`compose/CompactVolumePanel.kt`)
- Samsung-achtig geluidspaneel: sound/vibrate/silent, DND, ring/notificatie/media (`compose/SystemVolumePanel.kt`)
- Quick Settings-tile als ingang (`QuickSettingsTileService.kt`)
- Media pauzeren tijdens gesprekken en pauze houden daarna (`calls/CallMediaPauser.kt`, `calls/MediaListenerService.kt`)

## Waar het naartoe gaat

Donald bepaalt per ronde wat er gebeurt; er staat géén vaste roadmap.
Wat nog niet geïmplementeerd is (uit de README, ter inspiratie, niet besloten):

- Uitvoerapparaat per app instellen (API bestaat, niet gebouwd)
- Links-rechtsbalans (andere API, niet gebouwd)
- Equalizer
- Er zijn nog geen echte tests: alleen de gegenereerde `ExampleUnitTest` / `ExampleInstrumentedTest`

## Huisregels

- Commits en codecommentaar in het Engels; uitleg aan Donald in het Nederlands.
- Commentaar legt uit *waarom* iets zo is, inclusief wat geprobeerd is en niet werkte.
- Nooit secrets, keystores, `local.properties` of `.env` committen.
- Verifiëren voor "klaar": `.\gradlew assembleDebug` én `.\gradlew test` moeten slagen.
- Gradle-builds duren lang en de verbinding met de pc knipt rond een minuut af:
  start ze losgekoppeld (`Start-Process powershell`) met een sentinel-bestand dat je daarna pollt,
  niet wachten binnen één call.
- Gedrag op een echt toestel kan vanaf hier niet getest worden — dat eerlijk benoemen, nooit "werkt" zeggen.
- Pushen naar `origin`. Eén onderwerp per ronde; een gemiste dag wordt niet ingehaald.

## Logboek

Nieuwste bovenaan, één regel per ronde.

- 2026-11-15 — Compact paneel gelijkgetrokken met One UI (`0713c99`): brede trackslider met het icoon in de onderkant (geen bolletje meer), paneel opent ingeklapt met een slider plus chevron, ringslider volgt de tril-/stilstand en is aan meldingen gelinkt, uitvoerkiezer en tandwiel naar de kop. assembleDebug en test groen; uiterlijk en belgedrag niet op een toestel getest.
- 2026-11-14 — PLAN.md aangelegd (bestond nog niet). Tree schoon, in sync met origin/main, niets halfafs. Donald gevraagd wat hij vandaag verbeterd wil hebben; geen antwoord tijdens de ronde, dus niets gebouwd.
- 2026-11-08 — `bc8b214` Media pauzeren tijdens gesprekken en pauze houden daarna.
- 2026-11-06 — `e60a5c9` Samsung-achtig geluidspaneel: sound/vibrate/silent, DND, ring/notificatie/media.
- Compact paneel in One UI-stijl: brede tracksliders met het icoon in de track, opent ingeklapt met een chevron, ringslider volgt de tril-/stilstand en is aan meldingen gelinkt, uitvoerkiezer en tandwiel in de kop (`compose/CompactVolumePanel.kt`)
