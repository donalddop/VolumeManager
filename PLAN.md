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
- De sentinel moet de exit-code uit een scriptbestand schrijven, niet uit een Start-Process-argumentstring:
  daarin wordt `$LASTEXITCODE` ge-escaped en blijft de sentinel leeg (zie de ronde van 2026-11-21).
  Werkende vorm: script naar een .ps1 schrijven met `[IO.File]::WriteAllText`, dan `Start-Process powershell -ArgumentList '-File',$p`.
- Gedrag op een echt toestel kan vanaf hier niet getest worden — dat eerlijk benoemen, nooit "werkt" zeggen.
- Commit-messages BOM-vrij schrijven: `Set-Content -Encoding utf8` zet in PowerShell 5.1 een BOM
  voor de eerste regel, die dan in de commit-titel belandt (zie `c23f4c8` en `a5fe189`).
  Gebruik `git commit -m` of `[IO.File]::WriteAllText($p,$s,(New-Object System.Text.UTF8Encoding $false))`.
- Niet-ASCII in een PowerShell-commando komt via de MCP-route Latin-1-gedecodeerd aan: een letterlijke
  em dash belandt dubbel gecodeerd in het bestand. Bouw zulke tekens in het script op met
  ([char]0x2014).ToString() en controleer na het schrijven de codepoints, niet de console-weergave
  (die rendert soms Latin-1) (ronde 2026-11-25).
- String.Replace(string, char) kiest in PowerShell de (char,char)-overload: een string van meer dan een
  teken geeft dan $null terug en WriteAllText schrijft het bestand leeg. Zo liep PLAN.md op 2026-11-25
  even leeg (hersteld met git checkout). Geef twee strings mee en controleer dat de nieuwe lengte groeide.
- Elke ronde eindigt met een blok `## Nodig van Donald`: genummerde actiepunten die alleen hij kan
  afhandelen (keuze, oordeel, iets op een toestel testen), met tussen haakjes wat er gebeurt als hij
  niets zegt. Niets nodig? Dan letterlijk "Niets - je hoeft nu niets te doen." Zo gevraagd op 22-11-2026.
- Buildscripts, sentinels en ander scratch-materiaal buiten de repo zetten (bijv. in de TEMP-map): .build-sentinel in de projectmap maakte de tree vuil omdat het niet in .gitignore staat (ronde 2026-11-23).
- Lezen moet ook via [IO.File]::ReadAllText: Get-Content -Raw leest in PowerShell 5.1 een UTF-8-bestand
  zonder BOM als Windows-1252, waardoor een em dash dubbel gecodeerd in het bestand belandt.
  Zo ging het mis in de ronde van 2026-11-27 (hersteld met git checkout). Controleer na het schrijven
  altijd de codepoints op 0xC3, 0xE2 en 0xFFFD, niet de console-weergave.
- Pushen naar `origin`. Eén onderwerp per ronde; een gemiste dag wordt niet ingehaald.

## Logboek

Nieuwste bovenaan, één regel per ronde.

- 2026-11-27 — Geen code aangeraakt: Donald gevraagd wat hij verbeterd wilde hebben, geen antwoord tijdens de ronde (veertiende ronde op rij). Tree schoon en in sync met origin/main op 94c2982, geen stashes, niets halfafs. Baseline groen: assembleDebug en test BUILD SUCCESSFUL in 13s, sentinel exit=0 via de .ps1-route uit de huisregels; script, log en sentinel stonden in de TEMP-map, dus de tree bleef schoon. Laatste echte codewerk blijft 15-11; veertien stille rondes op rij — voorstel blijft deze dagelijkse ronde te pauzeren tot Donald zelf iets vraagt.
- 2026-11-26 — Geen code aangeraakt: Donald gevraagd wat hij verbeterd wilde hebben, geen antwoord tijdens de ronde (dertiende ronde op rij). Tree schoon en in sync met origin/main op 66f7f90, geen stashes, niets halfafs. Baseline groen: assembleDebug en test BUILD SUCCESSFUL in 14s, sentinel exit=0 via de .ps1-route uit de huisregels; script, log en sentinel stonden in de TEMP-map, dus de tree bleef schoon. Laatste echte codewerk blijft 15-11; dertien stille rondes op rij — voorstel blijft deze dagelijkse ronde te pauzeren tot Donald zelf iets vraagt.
- 2026-11-25 — Geen code aangeraakt: Donald gevraagd wat hij verbeterd wilde hebben, geen antwoord tijdens de ronde (twaalfde ronde op rij). Tree schoon en in sync met origin/main op 9ec9159, geen stashes, niets halfafs. Baseline groen: assembleDebug en test BUILD SUCCESSFUL in 12s, sentinel exit=0 via de .ps1-route uit de huisregels; buildscript, log en sentinel stonden in de TEMP-map, dus de tree bleef schoon. Laatste echte codewerk blijft 15-11; twaalf stille rondes op rij — voorstel blijft deze dagelijkse ronde te pauzeren tot Donald zelf iets vraagt.
- 2026-11-24 — Geen code aangeraakt: Donald gevraagd wat hij verbeterd wilde hebben, geen antwoord tijdens de ronde (elfde ronde op rij). Tree schoon en in sync met origin/main op 0d3ad7d, geen stashes, niets halfafs. Baseline groen: assembleDebug en test BUILD SUCCESSFUL in 16s, sentinel exit=0 via de .ps1-route uit de huisregels; buildscript, log en sentinel stonden in de TEMP-map, dus de tree bleef schoon. Laatste echte codewerk blijft 15-11; opnieuw voorgesteld deze dagelijkse ronde te pauzeren tot Donald zelf iets vraagt.
- 2026-11-23 — Geen code aangeraakt: Donald gevraagd wat hij verbeterd wilde hebben, geen antwoord tijdens de ronde (tiende ronde op rij). Tree schoon en in sync met origin/main op 3b452cd, geen stashes, niets halfafs. Baseline groen: assembleDebug en test BUILD SUCCESSFUL in 13s, sentinel exit=0 via de .ps1-route uit de huisregels. Wel een misstap: het sentinel-mapje .build-sentinel stond in de repo en is niet ge-ignoreerd, dus de tree was tijdelijk vuil; na de build verwijderd en huisregel toegevoegd om zulke scratch-bestanden buiten de repo te zetten. Laatste echte codewerk blijft 15-11; opnieuw voorgesteld deze dagelijkse ronde te pauzeren tot Donald zelf iets vraagt.
- 2026-11-22 — Geen code aangeraakt: Donald gevraagd wat hij verbeterd wilde hebben, geen antwoord tijdens de ronde (negende ronde op rij). Tree schoon en in sync met origin/main, geen stashes, niets halfafs. Baseline groen: assembleDebug en test BUILD SUCCESSFUL in 16s, en de sentinel klopte dit keer (exit=0) doordat het buildscript eerst naar een .ps1-bestand gaat in plaats van als argumentstring aan Start-Process; huisregel daarover toegevoegd. Opnieuw gevraagd of hij deze dagelijkse ronde wil pauzeren tot hij zelf iets vraagt; zonder zijn antwoord blijft de taak staan.
- 2026-11-21 — Geen code aangeraakt: Donald gevraagd wat hij verbeterd wilde hebben, geen antwoord tijdens de ronde (achtste ronde op rij). Tree schoon en in sync met origin/main, geen stashes, niets halfafs. Baseline gecontroleerd: assembleDebug en test groen (BUILD SUCCESSFUL in 15s). Let op: de exit-code-sentinel bleef leeg doordat LASTEXITCODE in de Start-Process-argumentstring werd ge-escaped en dus niet expandeerde; het buildlog is hier het bewijs, niet de sentinel. Ook gevraagd of hij deze dagelijkse ronde wil pauzeren tot hij zelf weer iets vraagt; zonder zijn antwoord blijft de taak staan.
- 2026-11-20 — Geen code aangeraakt: Donald gevraagd wat hij verbeterd wilde hebben, geen antwoord tijdens de ronde (zevende ronde op rij). Tree schoon en in sync met origin/main, niets halfafs, geen stashes. Baseline gecontroleerd: assembleDebug en test groen (BUILD SUCCESSFUL in 12s, exit 0). Zeven stille rondes op rij en het laatste echte codewerk is van 15-11: deze dagelijkse ronde pauzeren tot Donald zelf weer iets vraagt lijkt nu verstandiger dan doorgaan.
- 2026-11-19 — Geen code aangeraakt: Donald gevraagd wat hij verbeterd wilde hebben, geen antwoord tijdens de ronde (zesde ronde op rij). Tree schoon en in sync met origin/main, niets halfafs, geen stashes. Baseline gecontroleerd: assembleDebug en test groen (BUILD SUCCESSFUL in 14s, exit 0). Zes stille rondes op rij — pauzeren van deze dagelijkse ronde blijft een reële optie.
- 2026-11-18 — Geen code aangeraakt: Donald gevraagd wat hij verbeterd wilde hebben, geen antwoord tijdens de ronde (vijfde ronde op rij). Tree schoon en in sync met origin/main, niets halfafs, geen stashes. Baseline gecontroleerd: assembleDebug en test groen (BUILD SUCCESSFUL in 12s). Ter overweging: vijf stille rondes op rij — deze dagelijkse ronde pauzeren is ook een optie.
- 2026-11-17 — Geen code aangeraakt: Donald gevraagd wat hij verbeterd wilde hebben, geen antwoord tijdens de ronde (vierde ronde op rij). Tree schoon en in sync met origin/main, niets halfafs. Baseline gecontroleerd: assembleDebug en test groen (BUILD SUCCESSFUL).
- 2026-11-16 — Geen code aangeraakt: Donald gevraagd wat hij verbeterd wilde hebben, geen antwoord tijdens de ronde (derde ronde op rij). Tree schoon en in sync met origin/main, niets halfafs. Baseline wel gecontroleerd: assembleDebug en test groen (BUILD SUCCESSFUL).
- 2026-11-15 (2e ronde) — Geen code aangeraakt: Donald gevraagd wat hij verbeterd wilde hebben, geen antwoord tijdens de ronde. Wel veilig opgeruimd: ongedateerde duplicaatregel onderaan dit logboek weg (`5d1a97d`), en de regel die daarbij per ongeluk ook uit `Waar de app nu staat` verdween teruggezet (`429f9a7`). assembleDebug en test groen op deze tree.
- 2026-11-15 — Compact paneel gelijkgetrokken met One UI (`0713c99`): brede trackslider met het icoon in de onderkant (geen bolletje meer), paneel opent ingeklapt met een slider plus chevron, ringslider volgt de tril-/stilstand en is aan meldingen gelinkt, uitvoerkiezer en tandwiel naar de kop. assembleDebug en test groen; uiterlijk en belgedrag niet op een toestel getest.
- 2026-11-14 — PLAN.md aangelegd (bestond nog niet). Tree schoon, in sync met origin/main, niets halfafs. Donald gevraagd wat hij vandaag verbeterd wil hebben; geen antwoord tijdens de ronde, dus niets gebouwd.
- 2026-11-08 — `bc8b214` Media pauzeren tijdens gesprekken en pauze houden daarna.
- 2026-11-06 — `e60a5c9` Samsung-achtig geluidspaneel: sound/vibrate/silent, DND, ring/notificatie/media.
