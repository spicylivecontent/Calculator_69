# Calculator – fullständig dokumentation

Detta dokument beskriver **allt** i appen: vad den gör, hur den är byggd, varje viktig
regel och konstant, hur man bygger den, vad som testas och vilka begränsningar som finns.

> Känsligt innehåll: appen är en diskret miniräknare med en dold "vault" för vuxet
> innehåll avsedd för personligt bruk. Läs avsnittet **Juridik och ansvar** sist i dokumentet.

---

## Innehåll

1. [Vad appen är](#1-vad-appen-är)
2. [Snabbfakta](#2-snabbfakta)
3. [Bygga och installera](#3-bygga-och-installera)
4. [Arkitektur och paketkarta](#4-arkitektur-och-paketkarta)
5. [Flöden steg för steg](#5-flöden-steg-för-steg)
6. [Kalkylatorn, låset och panikfunktionen](#6-kalkylatorn-låset-och-panikfunktionen)
7. [Sökningen i detalj](#7-sökningen-i-detalj)
8. [Inläsning av all information om filmen](#8-inläsning-av-all-information-om-filmen)
9. [Relevans, taggar och avancerad sökning](#9-relevans-taggar-och-avancerad-sökning)
10. [Uppspelning A: WebView-spelaren (standard)](#10-uppspelning-a-webview-spelaren-standard)
11. [Uppspelning B: den inbyggda spelaren (beta)](#11-uppspelning-b-den-inbyggda-spelaren-beta)
12. [Annonsblockering i detalj](#12-annonsblockering-i-detalj)
13. [Lagring och datamodell](#13-lagring-och-datamodell)
14. [Behörigheter, säkerhet och integritet](#14-behörigheter-säkerhet-och-integritet)
15. [Kvalitetssäkring: tester och CI](#15-kvalitetssäkring-tester-och-ci)
16. [Kända begränsningar](#16-kända-begränsningar)
17. [Alla viktiga konstanter](#17-alla-viktiga-konstanter)
18. [Utvecklingshistorik](#18-utvecklingshistorik)
19. [Ordlista](#19-ordlista)
20. [Juridik och ansvar](#20-juridik-och-ansvar)

---

## 1. Vad appen är

En Android-app i två lager:

1. **Kalkylatorn** – en fullt fungerande miniräknare (parenteser, procent, potens, minne)
   som är allt en utomstående ser.
2. **Vaulten** – en dold del som öppnas genom att skriva en hemlig **huvudkod** i
   kalkylatorns display (t.ex. `1234` följt av `=`). Vaulten innehåller:
   - en sökfunktion som skrapar resultatlistor från tio videotubesajter,
   - en inläsning som hämtar **all information** om varje träff (taggar, kategorier,
     medverkande, kvalitet, längd, datum, visningar och beskrivning),
   - en resultatlista med små tumnaglar, rubriker, taggar och beskrivning,
   - en **WebView-spelare** som spelar videon på sajten med appens egen
     annonsblockering, popup-skydd och omdirigeringsskydd,
   - bibliotek med favoriter, "titta senare", historik och sparade sökningar,
   - inställningar för spelare, sökning, annonsblockering och säkerhet.

Det finns också en **reservkod** (decoy) som öppnar en oskyldig tom anteckningsvy i
stället – för situationer där någon annan tittar.

---

## 2. Snabbfakta

| Egenskap | Värde |
|---|---|
| Paketnamn | `com.calculator.app` |
| Språk | 100 % Kotlin (Compose-UI), svenska texter hårdkodade i koden |
| Kotlin-kod | 13 567 rader i 94 filer (`app/src/main`) + 2 826 rader JVM-tester i 26 filer + 502 rader instrumenterade tester i 6 filer |
| minSdk / targetSdk / compileSdk | 26 / 35 / 37 |
| versionCode / versionName | 1 / 1.0 |
| Byggsystem | Gradle 9.8.1 (wrapper) + AGP 9.4.1 med inbyggt Kotlin-stöd + Kotlin 2.4.21 + KSP 2.3.12 |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3, material-icons-extended 1.7.8 (fryst) |
| Media | Media3/ExoPlayer 1.11.1 (`exoplayer`, `exoplayer-hls`, `ui`, `common`) |
| Databas | Room 2.8.5 + SQLCipher 4.19.1 (`calculator_vault_enc.db`, version 1, 6 tabeller) |
| Nätverk | OkHttp 4.12.0 + Jsoup 1.18.1 |
| Bilder | Coil 2.7.0 (crossfade) |
| Övrigt | exp4j 0.4.8 (matte), androidx.biometric 1.1.0, coroutines 1.11.0 |
| JVM-enhetstester | **190 testfall i 26 filer** (körs utan Android-enhet) |
| Instrumenterade tester | **17 testfall i 6 filer** (emulator eller Android-enhet krävs) |
| CI | GitHub Actions: enhetstester, lint, APK och androidTest-kompilering vid push; emulatorregressioner på appkod |
| APK (debug) | artifact **Calculator-debug-apk**; CI har ett tak på 40 MB |
| Sajter | 10 stycken (se avsnitt 7) |
| Kategorier / underkategorier | 12 / 106 |
| Blockerade annonsdomäner | 148 unika (109 trackers + 51 popunder), 16 app-scheman, 44 annonsvägar |

---

## 3. Bygga och installera

### Lokalt

Krav: JDK 17 och Android SDK Platform 35.

```bash
./gradlew assembleDebug            # bara APK
./gradlew testDebugUnitTest        # bara testerna
./gradlew test assembleDebug       # allt (det CI gör)
```

APK hamnar i `app/build/outputs/apk/debug/app-debug.apk`.

Windows: `gradlew.bat assembleDebug`.

### Via GitHub Actions

Workflowen **Build APK** (`.github/workflows/build-apk.yml`) körs vid **varje push**
och kan startas manuellt från **Actions → Run workflow**. Stegen:

1. Checkout
2. JDK 17 (temurin)
3. Android SDK + `platforms;android-36`, `build-tools;36.0.0`
4. Gradle (basic cache)
5. `./gradlew --no-daemon testDebugUnitTest assembleDebug --stacktrace --console=plain`
6. Vid fel: skriver Kotlin-fel, testresultat och vanliga felrader till jobbets
   **summary** *och* som **annotations** på körningen (så att felet syns utan att ladda ner loggen)
7. Laddar upp artifacten **Calculator-debug-apk** (14 dagars retention)

En grön körning tar ca 4–5 minuter.

### Installation på telefonen

Ladda ner artifacten från en grön körning, packa upp `app-debug.apk` och installera
(tillåt installation från okänd källa). Appen heter **Calculator** i launcher.

---

## 4. Arkitektur och paketkarta

### Översikt

```
MainActivity (FragmentActivity, FLAG_SECURE, edge-to-edge)
└── CalculatorTheme
    └── CalculatorScreen
        ├── PinSetupScreen            (första starten: huvudkod + reservkod)
        ├── CalculatorUI              (VaultMode.NONE – räknaren)
        └── VaultScreen               (VaultMode.REAL / DECOY)
            ├── VaultTheme + VaultGradient
            ├── åldersbekräftelse      (första gången i REAL)
            ├── WebPlayerScreen        (webPlaying != null)   ← standard
            ├── VideoPlayerScreen      (nowPlaying != null)   ← beta
            └── Scaffold med fyra flikar
                ├── HomeScreen      (Fortsätt titta, snabbsök, kategorier, senaste träffar)
                ├── SearchScreen    (sök, kategorier, sajter, filter, taggmoln, resultat)
                ├── LibraryScreen   (översikt, favoriter, watch later, historik, regler, presets)
                └── SettingsScreen  (spelare, annonsblockering, sökning, sajter, säkerhet)
```

### Lager

| Lager | Paket | Ansvar |
|---|---|---|
| UI | `ui/*` | Compose-skärmar och komponenter. Ingen nätverkskod. |
| Tillstånd | `ui/search/SearchViewModel.kt`, `ui/library/LibraryViewModel.kt` | Sökning, inläsning, kö, uppspelning, bibliotek. |
| Domänlogik (ren Kotlin) | `ai/`, `data/model/`, `data/taxonomy/`, `data/extractor/StreamSelection.kt`, `ui/web/WebAdBlock.kt` | Planering, relevans, tolkning, blockeringsregler – allt enhetstestbart. |
| Nätverk | `network/`, `data/extractor/Fetch.kt` | OkHttp-klient, annonsblock, privacy-filter, hämtning. |
| Data | `data/` | Room-databas, DAO:er, entities, SharedPreferences, uppspelningshistorik. |
| Säkerhet | `security/` | PIN-koder (`PinStore`), hemliga värden (`SecretCipher`) och databasens nyckel (`VaultDatabaseKey`) – AES/GCM med nycklar i Android Keystore. |

### Filkarta

**App och kalkylator**

| Fil | Rader | Innehåll |
|---|---|---|
| `MainActivity.kt` | 55 | `FLAG_SECURE`, edge-to-edge, `CalculatorScreen`, taskbeskrivning ("Calculator") när vaulten är öppen |
| `ui/calculator/CalculatorViewModel.kt` | 210 | Räknarlogik (exp4j), PIN-jämförelse, lås/upplås, biometri-flöde, `VaultMode` |
| `ui/calculator/CalculatorScreen.kt` | 244 | Räknarens UI, PIN-inmatning via displayen, biometrisk prompt, `BackHandler` = paniklås |
| `ui/calculator/CalculatorAction.kt` | 10 | Räknarens åtgärder |
| `ui/calculator/PinSetupScreen.kt` | 115 | Första starten: skapa huvudkod + reservkod (4–12 siffror) |

**Vaultens skal och tema**

| Fil | Rader | Innehåll |
|---|---|---|
| `ui/vault/VaultScreen.kt` | 540 | Flikar, åldersbekräftelse, decoy, byte av koder, kopplar in spelarna |
| `ui/theme/VaultTheme.kt` | 53 | Mörkt tema + palett (bakgrund, ytor, accent, gradienter) |
| `ui/theme/Theme.kt`, `Type.kt`, `Color.kt` | 73 | Kalkylatorns eget Material 3-tema (dynamiska färger på Android 12+) |

**Skärmar och komponenter**

| Fil | Rader | Innehåll |
|---|---|---|
| `ui/home/HomeScreen.kt` | 249 | Startsida: progressrad, "Fortsätt titta", 8 snabbval, kategorikort, senaste träffar, låsknapp |
| `ui/search/SearchScreen.kt` | 676 | Sökfält, filterrad, kategoripanel, avancerat sökfält, taggmoln, resultatlista, "ladda fler", spara sökning |
| `ui/search/SearchViewModel.kt` | 1 174 | Hela sök- och uppspelningsflödet (se avsnitt 5), `clearSensitiveState()` |
| `ui/search/SearchViewModelFactory.kt` | 17 | Skapar ViewModel med Application |
| `ui/library/LibraryScreen.kt` | 361 | Översikt + favoriter, watch later, historik, "never show again", sparade sökningar |
| `ui/library/LibraryViewModel.kt` (+ Factory) | 75 | Flöden från Room + borttagningsmetoder |
| `ui/settings/SettingsScreen.kt` | 360 | Standardkvalitet, spolintervall, skärm, gester, återupptagning, annonsblockering, "bara sidor som svarar", sökhistorik, skrapregler, säkerhet, paniklås |
| `ui/components/VaultComponents.kt` | 506 | ShimmerBox, Thumbnail, Pill, PlayabilityPill, SectionHeader, VideoListCard, RecentVideoCard, EmptyState, VaultCard, TagChip, StatBadge |

**WebView-spelaren (standard)**

| Fil | Rader | Innehåll |
|---|---|---|
| `ui/web/WebPlayerScreen.kt` | 886 | WebView, annonsblock, popup-/omdirigeringsskydd, injektion, kontroller, kö, återupptagning |
| `ui/web/WebPlayerHost.kt` | 94 | Visar HTML5-spelarens custom view i helskärm och återställer WebView:n säkert |
| `ui/web/WebAdBlock.kt` | 189 | Rena blockeringsregler (testade) + injicerad CSS/JS + nivåer |

**Inbyggd spelare (beta)**

| Fil | Rader | Innehåll |
|---|---|---|
| `ui/player/VideoPlayerScreen.kt` | 1 181 | ExoPlayer, kvalitetsmeny, gester, skrubbning, hastighet, felpanel |
| `ui/player/PlayerQuality.kt` | 63 | Kvalitetsval, beskrivning, bitrate-tak |
| `ui/player/PlayerSeekBar.kt` | 142 | Egen Canvas-tidslinje + tidsformatering |

**Sökpipeline**

| Fil | Rader | Innehåll |
|---|---|---|
| `ai/SearchPlanner.kt` | 405 | Gör om fritext + kategorier till flera sökfrågor, negativa filter, synonymer |
| `ai/AgeSafety.kt` | 45 | Blocklista för termer som kan avse minderåriga + svensk feltext |
| `data/taxonomy/SearchTaxonomy.kt` | 259 | 12 kategorier, 106 underkategorier, uppslag |
| `data/extractor/SiteExtractor.kt` | 74 | Interface + `BaseSiteExtractor` (sök-URL, strukturell parsning, reservparsning, `responds`) |
| `data/extractor/SiteUrlPolicy.kt` | 20 | HTTPS-hostkontroll för sajtens träfflänkar, regler och WebView-navigering |
| `data/extractor/ScrapeRule.kt` | 167 | Regler från nätet: modell, validering och tolkning (testad) |
| `data/extractor/RuleSiteExtractor.kt` | 38 | Sajt som styrs helt av en hämtad regel |
| `data/extractor/ScrapeRuleStore.kt` | 170 | Hämtning (max en gång i dygnet), cache och status för reglerna |
| `data/extractor/*Extractor.kt` (10 st) | 17–23 vardera | Sajtspecifik URL och listningskonfig |
| `data/extractor/ExtractorSupport.kt` | 310 | Listningsparsning, reservparsning, titelstädning, tumnagel/längd/kvalitet |
| `data/extractor/MultiSiteSearcher.kt` | 235 | Parallell sökning + inläsning i vågor, semafor och tidsgräns per sajt |
| `data/extractor/MetadataExtractor.kt` | 351 | Läser ut all information från en detaljsida |
| `data/extractor/MiniJson.kt` | 199 | Liten JSON-tolkare för `application/ld+json` |
| `data/extractor/Fetch.kt` | 225 | Gemensam cancellable HTTPS-hämtning (12 s, max 6 MB), omförsök, källsajtvaliderade redirects och injicerbar testklient |
| `data/extractor/StreamSelection.kt` | 260 | Url-normalisering, medietyper, HLS-varianter, kvalitetsgissning, `looksRemoved` |
| `data/extractor/StreamResolver.kt` | 241 | Extrahering och probe av mediaström (används av beta-spelaren), `clearCache()` |

**Modeller och logik**

| Fil | Rader | Innehåll |
|---|---|---|
| `data/model/SearchResult.kt` | 91 | Träff med `Playability`, taggar, beskrivning, kvalitet, längd, relevans |
| `data/model/VideoMetadata.kt` | 68 | All skrapad information + `coverage`/`matchedTerms` |
| `data/model/ResultRanker.kt` | 192 | Relevans, dubblettrensning, sortering, taggfilter, taggmoln, skrapad sökning |
| `data/model/SearchQuery.kt` | 36 | `SearchQuery`, `SortOrder`, `LengthFilter` |
| `data/model/DurationParser.kt` | 59 | "12:34", "1h 5min", "45 minuter" → sekunder |

**Data och nätverk**

| Fil | Rader | Innehåll |
|---|---|---|
| `data/AppPreferences.kt` | 138 | Alla inställningar (`calculator_app_prefs`), inkl. autolås och avstängda sajter |
| `data/PlaybackStore.kt` | 231 | Uppspelningshistorik och positioner, krypterade (`calculator_playback`) |
| `data/PlaybackResumePolicy.kt` | 14 | Respekterar resume-växeln vid återupptagning och ny uppspelning (testad) |
| `data/AppDatabase.kt` | 80 | Room-databas (SQLCipher-krypterad), 6 tabeller, version 1, filval |
| `data/VaultDatabasePlan.kt` | 38 | Ren logik: krypterat mål, legacy-migrering och fail-closed vid saknad kryptering (testad) |
| `data/LegacyVaultMigrator.kt` | 66 | Flyttar innehållet från den gamla okrypterade filen, en gång |
| `data/Repository.kt` | 46 | Tunn wrapper över DAO:erna |
| `data/dao/*.kt` (6 st) | 10–28 vardera | Video, favorit, watch later, dislike-regel, sökpreset, inställning |
| `data/entity/*.kt` (6 st) | 10–19 vardera | Tabellmodeller |
| `network/NetworkClient.kt` | 24 | Delad OkHttp-klient |
| `network/AdBlockInterceptor.kt` | 26 | Kastar `IOException` för blockerade värdar |
| `network/AdBlockHosts.kt` | 214 | Den delade domänlistan (148 domäner + 44 annonsvägar + 16 scheman) |
| `network/PrivacyInterceptor.kt` | 62 | Tar bort trackingparametrar och identitetsheaders |
| `network/NetworkCondition.kt` | 111 | Parallellitet, tidsgränser och batterisparläge efter Wi-Fi / mobildata / offline |
| `security/CipherPayload.kt` | 50 | Formatet för krypterade värden (ren logik, testad) |
| `security/SecureValue.kt` | 22 | Läser och skriver känsliga inställningar krypterat |
| `security/SqlCipherLoader.kt` | 24 | Laddar det inbyggda SQLCipher-biblioteket en gång; misslyckad laddning stänger databasen |
| `CalculatorApp.kt` | 55 | Applikationsklass: Coil-cache med fasta gränser + migrering vid start |

---

## 5. Flöden steg för steg

### 5.1 Låsa upp vaulten

1. Appen startar i `VaultMode.NONE` och visar räknaren.
2. Användaren skriver sin huvudkod i displayen och trycker `=`.
3. `CalculatorViewModel` jämför med den sparade huvudkoden. Vid träff sätts
   `pendingVaultMode = REAL` och en **biometrisk prompt** visas.
4. Vid lyckad biometri sätts `vaultMode = REAL` och `VaultScreen` visas. Vid
   misslyckande/avbrott låses allt igen (`pendingVaultMode = NONE`).
5. Skriver någon i stället **reservkoden** öppnas `VaultMode.DECOY` direkt (ingen biometri) –
   en tom "Private Notes"-vy, som aldrig rör sökning, databas eller historik.
6. **Panik:** `BackHandler` när vaulten är öppen låser den direkt, liksom knappen
   "Lås vaulten (Panic)" i inställningarna och låsikonen på startsidan.

### 5.2 Söka

```
Användaren trycker Sök (eller väljer snabbval/kategori/tagg)
        │
        ▼
SearchPlanner.plan(rawInput, categoryTerms, extraNegatives)
  ├─ AgeSafety: blockeras sökningen? → feltext, inga frågor
  ├─ tomt utan kategorier? → "Skriv ett sökord eller välj en kategori."
  ├─ citattecken → fraser, "no/utan/-ord" → negativa filter
  ├─ svenska/engelska ord slås upp i synonymtabellen
  └─ Plan(queries ≤ 5: huvudsökning + varianter, negatives, terms, explanation)
        │
        ▼
MultiSiteSearcher.search(queries, siteIds, startPage=1, pageCount=1, maxPerSite=36)
  ├─ en korutin per sajt (Dispatchers.IO), alla frågor × sidor
  ├─ semafor begränsar antalet samtidiga sajter (10 på Wi-Fi, 4 på mobildata)
  ├─ varje sajtfråga har egen tidsgräns (6 s Wi-Fi, 9 s mobildata) – en långsam
  │  sajt avbryts, rapporteras som fel och stoppar aldrig de andra
  ├─ matchedTerms slås ihop per träff, först-vinner-dedupe på "{sajt}_{id}"
  └─ onBatch → SearchViewModel.applyBatch (filtrerar bort blockerade ord,
     märker träffen CHECKING och publicerar direkt)
        │
        ▼
enrichPending: vågor om 10 träffar (max 6 vågor / 60 träffar per sökning)
  ├─ MultiSiteSearcher.enrichMetadata (8 samtidiga på Wi-Fi, 3 på mobildata, 1 offline)
  ├─ ger sajtens egen konfiguration noll träffar → reservparsning (parseListingsGeneric)
  ├─ MetadataExtractor läser rubrik, taggar, kategorier, medverkande,
  │  kvalitet, längd, datum, visningar och beskrivning
  ├─ sidan borta/osvarande  → UNPLAYABLE (döljs)
  └─ annars PLAYABLE med all metadata → applyEnriched → publishResults
        │
        ▼
publishResults: relevans → filter (längd, "bara sidor som svarar") → taggmoln
  → taggfilter → ev. avancerad sökning i skrapad text → sortering
```

"Ladda fler" upprepar samma sak med `startPage = nästa sida` och budget 30 träffar.
Max 140 kandidater hålls i minnet per sökning.

### 5.3 Spela upp

1. Ett tryck på ett kort anropar `SearchViewModel.playVideo(result)` →
   `openOnSite(...)`.
2. Träffen byts mot den inlästa versionen, kön sätts till alla inlästa träffar i
   aktuell ordning (så att nästa/föregående fungerar), videon sparas i
   uppspelningshistoriken och `webPlaying` sätts.
3. `WebPlayerScreen` tar över hela skärmen: WebView laddar `detailUrl`, injicerar
   blockerings- och hjälpscript, och pollar filmens `<video>` varje sekund.
4. Positionen sparas var 5:e sekund och vid stängning; nästa gång startar videon
   ungefär där du slutade (skriptet `__pcResume` sätter `currentTime` när längden är känd).
5. Stängning (`BackHandler` går först bakåt i webbläsarhistoriken, därefter stängs
   spelaren) → `closeWebPlayer()` → tillbaka till listan.
6. Väljer man **Direktuppspelning (beta)** i spelarens meny stängs WebView-vyn och
   `VideoPlayerScreen` öppnas med en extraherad ström i stället.

---

## 6. Kalkylatorn, låset och panikfunktionen

- **Räknaren** använder **exp4j** och hanterar tal, decimaltecken, operatorerna
  `+ - * / %`, teckenradering, rensa och `=` (fel visas som `Fel`). Displayen är också
  PIN-inmatningen, och huvud-/reservkoden skrivs in som ett vanligt tal före `=`.
- **PIN-koder**: 4–12 siffror, huvudkod och reservkod måste skilja sig åt. De sparas
  **krypterade** (AES/GCM/NoPadding, 256-bitars nyckel, 128-bitars GCM-tagg, slumpad IV
  per kryptering) med en nyckel i **Android Keystore** (alias
  `com.calculator.app.vault_pin_key`).
- **Biometri**: `androidx.biometric`; huvudkoden kräver biometri. Saknas biometri på
  enheten förblir vaulten låst (ingen bakväg).
- **Screenshots**: `FLAG_SECURE` i `MainActivity` – skärmdumpar blockeras och
  app-switchern visar en tom ruta. När vaulten är öppen sätts dessutom
  taskbeskrivningen till "Calculator".
- **Decoy**: egen reservkod → tom anteckningsvy. Decoy-läget instansierar aldrig
  sök- eller biblioteks-ViewModel.
- **Panik**: bakknappen, låsikonen i startsidan och knappen i inställningarna.
- **Åldersbekräftelse**: en engångsdialog första gången vaulten öppnas i REAL
  (sparas i `calculator_content_access` → `adult_content_confirmed`). Det är en
  självbekräftelse, inte en riktig åldersverifiering.
- **Byt koder**: från inställningarna, med bekräftelsefält och kontroll att koderna
  skiljer sig och är 4–12 siffror.

---

## 7. Sökningen i detalj

### 7.1 Taxonomin – 12 kategorier, 106 underkategorier

Varje kategori har svensk etikett, emoji, kort beskrivning och underkategorier med
**engelska söktermer** (det sajterna indexerar):

| Kategori | Id | Exempel på underkategorier |
|---|---|---|
| 💇 Utseende | `appearance` | Blond, Brunett, Rödhårig, Långt hår, Tatueringar & piercing, Glamour & smink |
| 💃 Kropp & figur | `body` | Kurvig, Slimmad, Atletisk, Petite, Yppig (BBW), Stor byst, Långa ben |
| 🌍 Etnicitet & stil | `ethnicity` | Asiatisk, Latina, Ebony, Indisk, Arabisk, Europeisk, Skandinavisk |
| 👥 Sällskap | `company` | Par, Trekant, Grupp, Lesbisk, Swinger, Orgie |
| 🔥 Aktivitet | `activity` | Massage, Ridande, Deep throat, Masturbation, Squirt, Hardcore |
| 📍 Plats | `location` | Sovrum, Kök, Badrum, Dusch, Bastu, Utomhus, Bil, Strand, Gym, Kontor, Hotell |
| 💫 Stämning | `mood` | Romantisk, Sensuell, Passionerad, Lekfull, Dominant, Underdånig, Långsam, Intensiv, Rough |
| 🎭 Rollspel | `roleplay` | Sjuksköterska, Polis, Chef, Sekreterare, Hemmafru, Brud, Underkläder, Strumpbyxor |
| 🎥 Kamera & vinkel | `camera` | POV, Närbild, Amatör, Handhållen, Spegel, Casting |
| 🎬 Produktion | `production` | 4K, HD, Långfilm, Retro, Vintage, Compilation |
| ⛓️ Fetisch | `fetish` | Fötter, Leksaker, Binda, Latex, Olja |
| 💦 Avslutning | `finish` | Cumshot, Creampie, Squirting, Flera avslut |

Uppslag i `SearchTaxonomy`: `categories`, `allSubcategories`, `category(id)`,
`subcategory(id)`, `subcategoriesFor(categoryId)`, `termsFor(ids)`, `labelsFor(ids)`,
`categoryIdsFor(ids)`. Ett enhetstest kontrollerar att **ingen** term i trädet kan
avse minderåriga.

### 7.2 Från fritext till flera sökfrågor (`SearchPlanner`)

- **Citat** `"på knäna"` hålls ihop som en fras (3–60 tecken).
- **Negationer**: `utan X`, `inte X`, `ingen X`, `aldrig X`, `no X`, `without X`,
  `-X` och fältet "Exkludera" → blir filter i stället för sökord.
- **Synonymtabellen** översätter svenska och vanliga engelska ord:
  *brunett*→`brunette`(+`brown hair`), *rött hår*→`redhead`(+`red hair`),
  *kök*→`kitchen`, *utomhus*→`outdoor`, *massage*→`massage`, *pov*→`pov`(+`point of view`),
  *milf*→`milf`(+`mature`), *amatör*→`amateur` … 112 nycklar i tabellen.
  Nycklarna är normaliserade (gemener, accenter borttagna) så att *rött hår* och
  *rott har* ger samma resultat.
- **Stoppord** (and, the, med, och, eller …) filtreras bort, och `or`/`eller`
  behandlas inte som ett eget sökord.
- **Resultat**: en huvudsökning med alla positiva termer (max 6) plus varianter i
  prioritetsordning (fraser → valda kategorier → enskilda ord ≥ 4 tecken →
  synonymer), **max 5 frågor** totalt. `explanation` beskriver vad som söks och vad
  som filtreras bort och visas under resultaten.
- **Säkerhet**: `AgeSafety` blockerar hela sökningen om texten (eller en vald
  kategori) innehåller termer som kan avse minderåriga – t.ex. *teen, young, youth,
  schoolgirl, barely legal, minderårig, tonåring, barn* – och visar en svensk
  förklaring. Reglerna matchar hela ord (så "kinder" och "youthful" går fria).

### 7.3 De tio sajterna

| Sajt | Sökväg | Sidnumrering |
|---|---|---|
| Eporner | `https://www.eporner.com/search/{q}/{page}/` | ja |
| SpankBang | `https://spankbang.com/s/{q}/{page}/` | ja |
| HQPorner | `https://hqporner.com/?q={q}` | nej (en sida) |
| XVideos | `https://www.xvideos.com/?k={q}` | nej (en sida) |
| XNXX | `https://www.xnxx.com/search/{q}/{page}` | ja |
| xHamster | `https://xhamster.com/search/{q}?page={page}` | ja |
| Pornhub | `https://www.pornhub.com/video/search?search={q}&page={page}` | ja |
| Beeg | `https://beeg.com/search?q={q}` | nej |
| YouPorn | `https://www.youporn.com/search/?query={q}&page={page}` | ja |
| PornTrex | `https://www.porntrex.com/search/{q}/{page}/` | ja |
| LuxureTV | `https://en.luxuretv.com/search/{q}/{page}/` | ja |

Sökningen sker parallellt på alla valda sajter (alla om inget valts), och varje sajt
rapporterar antal träffar (`SiteSearchReport`) – inklusive vilka sajter som felade, med
möjlighet att utesluta dem och söka igen.

### 7.4 Listningsparsningen (`ExtractorSupport`)

I stället för att lita på varje sajts klassnamn letas **alla länkar** upp som matchar
sajtens video-URL-mönster, och informationen plockas från närmaste omgivande block
(`div, li, article, figure, section`):

- **Id**: från URL-mönstret (annars sista vägsegmentet); nyckeln blir `{sajt}_{id}`.
- **Titel**: `title`-attribut → kända titelselektorer → `img[alt]` → länktexten, och
  till sist en läsbar titel från URL-sluggen (`blonde_in_the_kitchen_2024` →
  "Blonde in the kitchen"). Varumärkessuffix som " | Pornhub" klipps bort.
- **Tumnagel**: `data-src`, `data-original`, `data-thumb`, `data-lazy-src`, `srcset`
  med flera, normaliserade till HTTPS.
- **Längd**: kända längdselektorer, annars första `mm:ss`/`h:mm:ss` i blockets text.
- **Kvalitet**: "4K"/"2160"/"1080"/"720"/"HD" i titeln.
- **Begränsning**: 36–42 träffar per sajt och sökning, 120 nyckelordskandidater.

**Reservparsning (`parseListingsGeneric`)** tar över när sajtens egen konfiguration inte
hittar något alls. Då letas i stället alla länkar upp som *ser ut* som videosidor
(`looksLikeVideoHref`: en slug med minst två ord, en siffer-id eller en känd
videofiländelse) och titel, tumnagel och längd plockas från närmaste omgivande block.
Länkar till taggar, kategorier, modellistor och rena mediafiler filtreras bort. Tack vare
det ger en sajt som bytt klassnamn eller layout ändå träffar i stället för tomt resultat.

**Tidsgräns per sajt**: varje sajtfråga körs i `withTimeoutOrNull` (6 sekunder på Wi-Fi,
9 på mobildata). Svarar sajten inte i tid rapporteras den i felpanelen
("Tog för lång tid (>6 s) – hoppades över"), resten av sökningen fortsätter.

### 7.5 Skrapregler som kan uppdateras utan en ny APK

En sajt som byter adress, sökväg eller klassnamn kan rättas i efterhand. Reglerna ligger i
`scrape-rules.json` i appens öppna repo och beskriver per sajt vilken sök-URL som gäller,
vilket URL-mönster som pekar ut videor och vilka CSS-selektorer som ger titel, tumnagel och
längd:

```json
{
  "version": 4,
  "sites": {
    "eporner": {
      "baseUrl": "https://www.eporner.com",
      "searchUrl": "https://www.eporner.com/search/{query}/{page}/",
      "hrefPattern": "/video-([A-Za-z0-9]+)",
      "requireHrefContains": "/video-",
      "titleSelectors": ["h2", ".title"],
      "durationSelectors": [".duration"],
      "limit": 36
    }
  }
}
```

* **Bara data, aldrig kod.** Filen kan innehålla adresser, mönster och selektorer – inga
  skript, inga uttryck, inga kommandon.
* **Allt valideras** (`ScrapeRules.parse`): HTTPS krävs för både bas och sök-URL, sök-URL:en
  måste innehålla `{query}`, URL-mönstret måste gå att kompilera, och det finns tak för
  antal sajter (40), selektorer (20 per lista, 80 tecken), mönsterlängd (240) och träffar
  (60). En regel som inte klarar kontrollen hoppas över – de övriga används.
* **Versionen måste öka.** Är filens `version` inte högre än den appen redan har ignoreras
  den, så en gammal eller manipulerad fil kan inte rulla tillbaka en fungerande konfiguration.
* **Hämtas högst en gång i dygnet** (8 sekunders tidsgräns, 200 kB tak), bara över HTTPS och
  bara från `ScrapeRuleStore.RULES_URL`. Senaste godkända filen sparas lokalt och används
  även offline.
* **Graceful degradation:** misslyckas hämtningen, är filen tom eller trasig, eller är
  funktionen avstängd, används den sparade versionen – och finns ingen sådan de inbyggda
  reglerna. Sökningen påverkas aldrig av en långsam hämtning; den sker i bakgrunden och
  slår igenom först när den lyckats.
* **En regel ersätter** den inbyggda sajten med samma id, eller **lägger till** en helt ny
  sajt i slutet av sajtlistan. Regler för nya sajter ärver annars allt från de inbyggda.
* **I inställningarna** finns växeln "Hämta uppdaterade regler", en statusrad
  ("Inbyggda regler (10 sajter)" eller "Hämtade regler v4 · 11 sajter · för 2 h sedan") och
  knapparna **Uppdatera nu** och **Använd inbyggda**.

---

## 8. Inläsning av all information om filmen

`MetadataExtractor.parse(html, detailUrl, siteName)` läser detaljsidan i denna
prioritetsordning och fyller `VideoMetadata`:

| Fält | Källor (i ordning) |
|---|---|
| **Rubrik** | `og:title` → `twitter:title` → `ld+json name` → `<h1>` → `<title>` |
| **Beskrivning** | `og:description` → `meta[description]` → `twitter:description` → `ld+json description` → vanliga beskrivningsbehållare (max 1 500 tecken, blanksteg städat) |
| **Taggar** | länkar med taggmönster (`/tag/`, `tag=`, `/keyword` …) → `ld+json keywords` → typiska taggbehållare → `meta[name=keywords]` |
| **Kategorier** | länkar med `/category`, `/channel`, `/genre` → `ld+json genre` |
| **Medverkande** | länkar med `/pornstar`, `/model`, `/actress`, `/performer` → `ld+json actor` och `director` |
| **Kvalitet** | `og:video:height` → `itemprop=height` → `<video height>` → siffran i rubriken → korta kvalitetsetiketter ("1080p", "HD 720p") |
| **Längd** | `video:duration`/`og:video:duration`/`itemprop=duration` (sekunder) → `ld+json duration` i ISO 8601 (`PT1H2M3S`) → längd i rubriken |
| **Datum** | `itemprop=uploadDate` → `ld+json uploadDate` → `<time datetime>` |
| **Visningar** | `itemprop=interactionCount` → element med "views" |
| **Borta?** | `StreamSelection.looksRemoved` (t.ex. "video has been removed", "no longer available") → `pageMissing = true` |

Dessutom: navigationsord (Home, Log in, Next, Sök, Nästa …) filtreras bort från
nyckelord, dubbletter tas bort (skiftlägesokänsligt) och listorna kapas till 40 taggar,
40 medverkande och 40 kategorier (max 60 nyckelord totalt i `VideoMetadata.keywords`).

`MiniJson` är en liten egen JSON-tolkare (objekt, listor, strängar med escape-sekvenser,
tal, boolean, null) som används för `application/ld+json`. Den klarar trasig JSON utan
att krascha (returnerar `null`) och har tester för escapes, nästlade strukturer och
felaktig indata.

**Resultatet** blir `SearchResult` med `tags` (kombinerade nyckelord), `description`,
`tagCount`, `qualityLabel`, `durationSeconds` och `playability = PLAYABLE`.

---

## 9. Relevans, taggar och avancerad sökning

### 9.1 Relevanspoäng (`ResultRanker`)

| Regel | Poäng |
|---|---|
| Fras finns i rubriken | +40 |
| Ordmatchning i rubriken (per ord) | +14 |
| Delsträngsmatch i rubriken | +6 |
| Term finns i taggar/kategorier | +18 per term |
| Term finns i beskrivningen | +9 per term |
| Alla söktermer finns i rubrik+taggar+beskrivning | +30 (annars proportionellt mot täckningen) |
| Träffen har inläst information | +4 |
| Träffen är inläst (PLAYABLE) | +8 |
| Kvalitetsord i rubriken (4k, 1080p, hd …) | +4 |
| Rubrik kortare än 12 tecken, eller skräpord (trailer, preview, photos …) | −6 |

Dubbletter tas bort på normaliserad titel (skiftlägesokänsligt, skiljetecken bort) och
den **inlästa** versionen vinner.

### 9.2 Sortering och filter

- **Sortering**: Relevans, Längst först, Kortast först, Källa (träffar utan längd sist).
- **Längdfilter**: Alla längder, Under 5 min, 5–20 min, Över 20 min.
- **"Bara sidor som svarar"** (på som standard): döljer träffar vars detaljsida inte
  kunde läsas in eller där innehållet tagits bort. Träffar som läses in just nu visas
  ändå, med status "Läser info".
- **Taggfilter**: klick på en tagg filtrerar listan (alla valda taggar måste finnas)
  och visar "Filtrerar på: …" med en rensa-knapp.
- **Taggmoln**: de 24 vanligaste taggarna bland aktuella träffar, med antal.
- **Avancerat sökfält**: matchar fritext mot **all** skrapad text (rubrik, taggar,
  kategorier, medverkande, beskrivning); träffar som innehåller *alla* orden lyfts
  först, därefter relevansordning.
- **Negativa filter**: ord från söktexten och från "Never show again"-regler tas bort
  både vid insamling och i efterhand (via `Nästa`-menyns blockeringsikon på korten).
- **Sparade sökningar** och **sökhistorik** (max 12): spara aktuell sökning med namn,
  återanvänd med ett tryck.

---

## 10. Uppspelning A: WebView-spelaren (standard)

Fil: `ui/web/WebPlayerScreen.kt` + `ui/web/WebAdBlock.kt`.

### 10.1 WebView-konfiguration

| Inställning | Värde | Varför |
|---|---|---|
| JavaScript | på | Sajterna kräver det |
| DOM storage | på | Sajternas spelare använder det |
| `mediaPlaybackRequiresUserGesture` | av | Videon ska kunna starta direkt |
| `javaScriptCanOpenWindowsAutomatically` | av | Popups |
| `setSupportMultipleWindows` | på; `onCreateWindow` nekar nya fönster | Popup-förfrågningar hanteras centralt och blockeras |
| Tredjepartscookies | av | Integritet |
| `mixedContentMode` | `MIXED_CONTENT_NEVER_ALLOW` | Ingen klartext |
| `allowFileAccess` / `allowContentAccess` | av | Sandbox |
| Geolokalisering | av, och behörighetsförfrågningar nekas | Integritet |
| Safe Browsing (API 26+) | på | Skadliga sidor |
| User-Agent | Chrome 124 på Android | Sajterna ger mobil-/desktopvariant |
| Bakgrund | svart | Ingen vit blinkning |

### 10.2 Vad spelaren gör

- **Laddar** `detailUrl` och visar en tunn progressbar högst upp.
- **Helskärm.** `WebChromeClient.onShowCustomView` monterar videons helskärmsvy i `WebPlayerHost`; WebView:n döljs tills `onHideCustomView`. Appens överlagrade knappar tas bort så webbspelarens kontroller får touch, och Bakåt avslutar helskärmen först.
- **Blockerar** resurser till annonsdomäner (`shouldInterceptRequest`) och räknar dem.
- **Stoppar** navigering: popups (`onCreateWindow`), annonslänkar, länkar till andra
  sajter och app-scheman (`intent://`, `market://`, `tg://`, …). Klartext (http) nekas.
- **Injicerar** vid varje sidladdning ett script som:
  - ersätter `window.open` med en no-op och tar bort `target="_blank"`,
  - lägger in CSS som gömmer annonsytor och tar bort annons-/popunder-noder
    (vid 0,8 s, 2,5 s och 6 s),
  - exponerar hjälpfunktioner: `__pcVideo()` (största videon), `__pcState()`
    (position, längd, pausad, upplösning), `__pcSeekBy(±s)`, `__pcToggle()`,
    `__pcControls(on)` och `__pcResume(sekunder)`,
  - ser till att videon får `playsinline` och `controls`.
- **Pollning** varje sekund: position, längd, paus-status och blockeringsräknare.
  Positionen sparas var 5:e sekund och vid stängning.
- **Återupptagning**: om historiken har en position > 15 s skickas den till
  `__pcResume`, som sätter `currentTime` så snart längden är känd (> position + 20 s).
- **Kontroller** (döljs efter 4,5 s när videon spelar):
  - topprad: tillbaka, rubrik, sajt · kvalitet · längd · antal blockerade, meny (…)
    med *Ladda om*, *Öppna i webbläsare*, *Visa videokontroller*, *Direktuppspelning
    (beta)* och val av blockeringsnivå,
  - taggchips (de första 18) – tryck för att söka på taggen och gå till sökvyn,
  - beskrivning i två rader,
  - mitt: föregående, −X s, paus/spela, +X s, nästa (X = inställt spolintervall),
  - botten: tid, tidslinje, längd och blockeringsstatus,
  - dubbeltryck: vänster halva = −X s, höger halva = +X s (visar också kontrollerna).
- **Fel**: om huvudsidan inte kan laddas visas en panel med "Försök igen" och
  "Öppna i webbläsare".
- **Stängning**: `BackHandler` går bakåt i webbläsarhistoriken först, därefter stängs
  spelaren och WebView:n förstörs (`stopLoading`, `about:blank`, `destroy`).
- **Skärmen** hålls tänd (om inställningen är på) och systemfälten döljs under tiden.

### 10.3 Blockeringsnivåer

| Nivå | Beteende |
|---|---|
| **Standard** (rekommenderad) | Annons-/trackingdomäner och annonsvägar blockeras, popups och omdirigeringar stoppas, mediaspelare från andra domäner tillåts |
| **Strikt** | Som standard, men **all** navigering utanför sajten blockeras (även mediaservrar på andra domäner) |
| **Av** | Ingen blockering i spelaren (endast användbart för felsökning) |

Nivån väljs i spelarens meny eller i Inställningar och sparas i `AppPreferences`
(`adblock_level`). Antalet blockerade förfrågningar visas i topp- och bottenraden.

---

## 11. Uppspelning B: den inbyggda spelaren (beta)

Fil: `ui/player/VideoPlayerScreen.kt`. Öppnas via **Direktuppspelning (beta)** i
WebView-spelarens meny, och kräver att en mediaström kan extraheras (`StreamResolver`).

### 11.1 Extrahering (`StreamResolver` + `StreamSelection`)

1. Detaljsidan hämtas (7 s) och kontrolleras för borttaget innehåll.
2. Kandidater samlas: `<video>`/`<source>`, råa URL:er i script, relativa mediasökvägar,
   samt sajtspecifika källor (Beegs API v6).
3. Kandidater rangordnas (HLS +60, MP4 +40, höjdbonus, HD +14, "preview/trailer/sample"
   −70) och de sex bästa provas: en `Range: bytes=0-2047`-förfrågan måste svara med
   video-, HLS- eller octet-stream-typ (text/html och JSON avvisas).
4. HLS-manifest (max 512 KB) måste börja med `#EXTM3U`; bästa variant läses ut för
   kvalitetsetiketten ("upp till 4K" / "Adaptiv").
5. Resultatet cachas i 20 minuter (max 400 poster) och kan tvingas fram på nytt.

### 11.2 Spelaren

- **ExoPlayer** med egen `DefaultTrackSelector`, `DefaultLoadControl`
  (15 s min / 60 s max buffert, 1,5 s före uppspelning, 3 s efter ombuffring) och
  decoder-fallback. Egen `DefaultHttpDataSource` med UA, `Referer`/`Origin` från
  detaljsidan, 15 s connect / 20 s read och tillåtna korsprotokollsomdirigeringar.
- **Kvalitetsmeny** från strömmens egna spår: Automatisk, Högsta möjliga,
  Spara data (max 480p / 900 kbps) samt varje höjd (1080p · 3,2 Mbps …) via
  track selector-parametrar (`clearVideoSizeConstraints`, `setMaxVideoSize`,
  `setMaxVideoBitrate`, `setForceHighestSupportedBitrate`).
- **Skrubbning**: egen Canvas-tidslinje (spelat, buffrat, tumme) där tryck hoppar
  direkt och drag skrubbar; under drag används `SeekParameters.CLOSEST_SYNC` med
  förhandsvisning var 180:e ms, och exakt position sätts när du släpper.
- **Gester**: tryck = visa/dölj kontroller; dubbeltryck vänster (<40 %) = −X s,
  höger (>60 %) = +X s, mitten = paus/spela (med haptik); horisontellt drag var som
  helst = skrubbning (känslighet 0,45 av skärmbredden per drag); vertikalt drag
  vänster = ljusstyrka, höger = volym (HUD som visas i 0,9 s).
- **Övrigt**: hastigheter 0,5–2×, bildläge (anpassa/fyll/zooma), buffringsspinner,
  tidsvisning (12:34 / 1:02:03), "Fortsätter från …" med "Börja om", felpanel med
  "Försök igen" och "Nästa video", samt kö-knappar för nästa/föregående.
- **Progress** sparas var 5:e sekund och vid stängning.

---

## 12. Annonsblockering i detalj

### 12.1 Den delade listan (`network/AdBlockHosts.kt`)

- **109 tracking-/annonsdomäner**: Google (doubleclick, googlesyndication,
  google-analytics, googletagmanager, 2mdn), Facebook/FB-CDN, Amazon-adsystem,
  Criteo, PubMatic, OpenX, Rubicon, Casale, Adform, Smartadserver, Taboola, Outbrain,
  MGID, RevContent, analysverktyg (Hotjar, Mouseflow, FullStory, New Relic, Segment,
  Mixpanel, Amplitude, Sentry), statistik (StatCounter, Histats, Yandex Metrika),
  cam-nätverk (LiveJasmin, BongaCams, Stripchat, Chaturbate, CamSoda, Streamate …)
  och push-notisnätverk (OneSignal, Notix, SendPulse …).
- **51 popunder-/redirectnätverk**: JuicyAds, ExoClick, TrafficJunky, TrafficStars,
  Adsterra, Clickadu, PopAds, PopCash, PropellerAds, HilltopAds, AdCash, Adnium,
  TSyndicate, OnclickAds, Realsrv, AdTNG, Zangio, Admaven, PlugRush, Monetag,
  Galaksion, ProfitablerateCPM med flera.
- **16 app-scheman** som inte får öppnas: `intent`, `market`, `play`, `app`, `tg`,
  `whatsapp`, `sms`, `tel`, `mailto`, `bitcoin`, `viber`, `line`, `snapchat`, `fb`,
  `instagram` med flera.
- **44 annonsvägar** som blockeras även på en tillåten domän: `/popunder`, `pop.js`,
  `/ads.js`, `/ads/`, `/ad/`, `/vast`, `/vpaid`, `/preroll`, `/banner/`, `/promo/`,
  `/track.php`, `/click.php`, `/push.js`, `/redirect?`, `/out.php`, `syndication.php`,
  `/adserver`, `/sponsor/` med flera.

### 12.2 Tre lager av skydd

1. **Appens egen hämtning** (`network/AdBlockInterceptor`): förfrågningar till
   blockerade värdar kastar `IOException` och når aldrig nätet. `PrivacyInterceptor`
   tar samtidigt bort trackingparametrar (`utm_*`, `fbclid`, `gclid`, `msclkid`,
   `dclid`, `twclid`) och identitetsheaders (utom vid Range-prober mot medieservrar,
   som behöver dem för att fungera).
2. **WebView-spelaren**: `shouldInterceptRequest` svarar med ett tomt svar för
   blockerade resurser, `shouldOverrideUrlLoading` stoppar popunder-/annonsnavigering,
   `onCreateWindow` nekar nya fönster.
3. **På sidan**: injicerad CSS + script som gömmer och tar bort annonselement direkt
   och löpande.

Allt detta är **best-effort**: nya annonsnätverk tillkommer löpande och listan i
`AdBlockHosts.kt` är den enda platsen som behöver uppdateras (den används av båda
spelarna och all hämtning).

---

## 13. Lagring och datamodell

### 13.1 Room-databasen

`calculator_vault_enc.db` (krypterad), **version 1**. Nya tabellversioner kräver en
migrering. Den gamla okrypterade filen hette `calculator_vault.db` och flyttas in
automatiskt en gång (se 13.3).

| Tabell | Fält |
|---|---|
| `videos` | `id` (PK), titel, tumnagel, längd, sajt, detalj-URL, stream-URL, taggar, kvalitet, `addedAt`, `lastWatchedAt` |
| `favorites` | `videoId` (PK), `addedAt` |
| `watch_later` | `videoId` (PK), `addedAt` |
| `dislike_rules` | `id` (auto), `type` (keyword/tag/performer/video/site), `value`, `createdAt` |
| `search_presets` | `id` (auto), `name`, `query`, `negativeKeywords`, `createdAt` |
| `app_settings` | `key` (PK), `value` |

Favoriter och watch later är kopplade till `videos` via vyer som läser in video-raden,
och räknare finns för alla listor (används av bibliotekets översikt).

Varje DAO har dessutom en engångsläsning (`getAllVideosOnce`, `getAllFavoriteIds` med
flera) som bara används av migreringen.

### 13.2 SharedPreferences

| Fil | Nycklar | Innehåll |
|---|---|---|
| `calculator_app_prefs` | `only_playable` (true), `skip_seconds` (10, 5–60), `keep_screen_on` (true), `gesture_seeking` (true), `resume_playback` (true), `preferred_quality` (`auto`), `adblock_level` (`standard`), `remote_rules_enabled` (true), `auto_lock` (`background`), `disabled_sites`, `search_history` | Alla inställningar + sökhistorik (max 12, radseparerad, **krypterad**) |
| `calculator_playback` | `recent_entries` (JSON, max 40), `positions` (JSON-karta, endast id:n i historiken) | Uppspelningshistorik och positioner per video, **båda krypterade** |
| `calculator_content_access` | `adult_content_confirmed` | Åldersbekräftelsen |
| `calculator_vault_secret` | `db_passphrase` | Databasens nyckel, krypterad (se 13.3) |
| `calculator_scrape_rules` | `rules_json`, `rules_version`, `rules_fetched_at` | Hämtade skrapregler + när de hämtades |

**"Fortsätt titta"** visas för videor som spelats mer än 15 sekunder och som inte är
inom 30 sekunder från slutet. Historiken kan rensas från biblioteket. Positionerna sparas
var femte sekund i den egna kartan `positions` (`video-id` → `[position, längd]`), och
kartan sammanfogas med historiken när den läses – annars skulle "Fortsätt titta" stå still
på noll. Positioner rensas när motsvarande video faller ur den senaste historiken, så även
kartans storlek är begränsad till högst 40 id:n. `positions` ligger krypterad, och de gamla
nycklarna `pos_<id>`/`dur_<id>` flyttas in i kartan en gång och raderas därefter.

### 13.3 Kryptering

**PIN-koderna** ligger AES/GCM-krypterade med en nyckel i **Android Keystore** (alias
`com.calculator.app.vault_pin_key`) – se avsnitt 6 och `security/PinStore.kt`.

**Hela databasen är krypterad** med **SQLCipher** (`net.zetetic:sqlcipher-android`,
version 4.19.1). Room kopplas ihop med `SupportOpenHelperFactory`, och det inbyggda
biblioteket laddas med `System.loadLibrary("sqlcipher")` innan filen öppnas.

* **Nyckeln** är 32 slumpade byte (`SecureRandom`) som skapas första gången appen körs.
  Den sparas **krypterad** (AES/GCM, Keystore-nyckel `com.calculator.app.secret_cipher_key`)
  i `SharedPreferences`-filen `calculator_vault_secret`, alltså aldrig i klartext på
  disken. Den är *inte* härledd från PIN-koden – byter användaren kod fortsätter
  biblioteket att fungera.
* **Filvalet** (`VaultDatabasePlan`) är medvetet enkelt att resonera om:
  krypterad fil `calculator_vault_enc.db`, gammal fil `calculator_vault.db`. Finns den
  gamla filen men inte den krypterade körs `LegacyVaultMigrator` en gång vid start:
  videor, favoriter, titta senare, regler och sparade sökningar läses in, skrivs till den
  krypterade databasen och **först därefter** raderas originalet (inklusive `-wal`,
  `-shm`, `-journal`). Misslyckas något lämnas originalet kvar.
* **Fail closed**: kan SQLCipher eller Keystore inte användas (till exempel om det
  inbyggda biblioteket saknas) öppnas ingen vaultdatabas. Vaultens lagringskontroll visar
  ett fel, och en eventuell gammal klartextfil lämnas orörd för att migreringen ska kunna
  försökas igen när krypteringen fungerar. Den används aldrig som fallback.
* **PIN-återställning**: om PIN-nycklar finns men inte kan dekrypteras visas inte
  nykonfiguration; vaulten förblir låst så att korrupt eller otillgängligt nyckelmaterial
  inte kan skrivas över av misstag. Återställning kräver att appdata rensas, vilket även
  tar bort det lokala innehållet.
* **Sökhistorik, uppspelningshistorik och positioner** ligger sedan `82dbbc1` också
  krypterade, värde för värde, med samma Keystore-nyckel. Formatet är
  `enc:` + Base64(`[IV-längd][IV][chiffertext]`) och sköts av `security/CipherPayload.kt`
  (ren logik, enhetstestad) via `CipherPayload`/`SecureValue`. Ett värde som inte går att
  dekryptera läses som `null` i stället för att krascha – historiken framstår då som tömd.
* **Äldre klartextvärden** läses som de är och krypteras nästa gång de skrivs, så ingen
  historik tappas vid uppdateringen. Övriga inställningar (kvalitet, spolintervall,
  växlar) ligger kvar i klartext – de innehåller inget känsligt.
* **Autolås** valfritt i inställningarna: `background` (standard, låser när appen lämnas,
  via `onStop` och inte vid en rotationsändring), `1min`, `5min` eller `never`.
  Inaktivitetskollen ligger i `VaultScreen` (`rememberAutoLockModifier`: en räknare som
  nollställs av varje pekning och kontrolleras var femte sekund) och konsumerar inga
  pekningar, så spelare och listor fungerar som vanligt. Vid låsning töms känsliga data
  ur minnet (`clearSensitiveState`) och nästa upplåsning kräver kod igen.

---

## 14. Behörigheter, säkerhet och integritet

- **Behörigheter**: endast `INTERNET` och `ACCESS_NETWORK_STATE`. Inga kamera-,
  mikrofon-, lagrings- eller platsbehörigheter.
- **Klartext**: `usesCleartextTraffic="false"` + `network_security_config.xml` som
  tillåter HTTPS och systemets certifikat. All hämtning kontrollerar dessutom
  `isHttps` innan en förfrågan byggs.
- **Screenshots och app-switcher**: `FLAG_SECURE`.
- **Krypterad databas**: hela Room-filen är SQLCipher-krypterad, med en slumpad nyckel
  som i sin tur ligger krypterad i Android Keystore.
- **Minnesrensning vid panik/lås**: träffar, frågor, taggar, kö och strömcache nollställs
  innan appen lämnar vaulten.
- **Ingen kontoinloggning** och inga uppgifter skickas någonstans: all logik körs
  lokalt, sökningar går direkt till sajterna.
- **Fjärrreglerna** är den enda förfrågan som inte går till en sajt användaren själv söker
  på: en GET av `scrape-rules.json` från appens eget repo, högst en gång i dygnet, utan
  identifierare. Den kan stängas av i inställningarna. Filen innehåller bara data och allt
  valideras innan det används.
- **Sandboxad WebView**: ingen fil-/innehållsåtkomst, inga tredjepartscookies, inga
  behörigheter, ingen geolokalisering.
- **Annonsblockering** som även döljer delar av sajternas spårning.
- **Åldersblockering**: `AgeSafety` filtrerar både sökord och träffrubriker, och
  taxonomin innehåller inga sådana termer (testas).

---

## 15. Kvalitetssäkring: tester och CI

### 15.1 Enhetstester (JVM)

| Fil | Testar |
|---|---|
| `ai/AgeSafetyTest.kt` | Blocklistan i båda språken, hela ord, att vanliga ord inte fastnar, blockerade planer |
| `ai/SearchPlannerTest.kt` | Huvudsökning, varianter, negativa filter, svenska → engelska, citat, kategorier, frågetak (även noll), förklaringstext |
| `data/taxonomy/SearchTaxonomyTest.kt` | Unika id:n, etiketter, gemena engelska termer, uppslag, inga blockerade ålderstermer |
| `data/extractor/ExtractorSupportTest.kt` | Listningsparsning, titelstädning, slug-fallback, kvalitet, HTTPS- och källsajtkrav för detaljlänkar |
| `data/extractor/MetadataCacheTest.kt` | TTL-gräns, ersättning av samma URL, rensning av äldsta fjärdedelen vid full cache |
| `data/extractor/LuxureTvExtractorTest.kt` | Sök-URL:ens format och listningsparsning för LuxureTV |
| `network/HostRateLimiterTest.kt` | Mellanrum mellan startider per värd, tak på samtidiga anrop, isolering mellan värdar, släpp av tillstånd vid fel |
| `data/extractor/GenericListingTest.kt` | Reservparsning: videolänkar utan klassnamn, filtrering av tagg-/kategorilänkar, dedup, HTTPS |
| `data/extractor/SiteConfigTest.kt` | Alla elva sajter: unika id:n, HTTPS-sök-URL med orden kvar, att mönstret matchar sajtens egna länkar, konsekvent konfiguration |
| `data/extractor/SiteUrlPolicyTest.kt` | Källsajtens HTTPS-hostkontroll, subdomäner, lookalike-värdar, portar och inloggningsuppgifter |
| `data/extractor/ScrapeRuleTest.kt` | Fjärrregler: tolkning, HTTPS- och källvärdskrav, `{query}`, trasig regex, versionskrav, tak för selektorer/limit, nya sajter, namnvalidering |
| `data/extractor/FetchRetryTest.kt` | Omförsökspolicyn: nätverksfel och 5xx/429 görs om, 404/403 och klartext aldrig, antalet försök respekteras |
| `security/CipherPayloadTest.kt` | Formatet för krypterade värden: in- och utpackning, tomma värden, klartext, trasiga och trunkerade värden |
| `data/extractor/PipelineMockWebServerTest.kt` | Hela kedjan mot en lokal HTTPS-server (MockWebServer): lista → träffar, detaljsida → metadata, borttagen sida, serverfel, omförsök vid tillfälligt fel, att en 404 inte görs om, avbrytning av pågående anrop, säker omdirigering (extern värd nekas), iso-8859-1, att klartext aldrig hämtas |
| `data/extractor/MetadataExtractorTest.kt` | Rubrik, beskrivning, taggar, medverkande, kategorier, kvalitet, längd (inkl. ISO 8601), borttagna sidor, nyckelordstak, täckning |
| `data/extractor/MiniJsonTest.kt` | JSON-parsning, escapes/unicode, trasig indata, nästlad sökning, listor |
| `data/extractor/StreamSelectionTest.kt` | HTTPS-krav, medietyper, rangordning, HLS-varianter, borttagna sidor, innehållstyper |
| `data/model/DurationParserTest.kt` | Kolon- och ordformat, ogiltig indata, formatering |
| `data/VaultDatabasePlanTest.kt` | Filval: den krypterade databasen är enda mål, legacyfilen är migrationskälla och krypteringsfel stänger vaulten |
| `data/PlaybackResumePolicyTest.kt` | Resume-växeln gäller för sparade positioner, återöppnad historik och ny uppspelning |
| `network/NetworkConditionTest.kt` | Parallellitet och tidsgränser för Wi-Fi, mobildata, offline och okänt läge, batterisparlägets halvering |
| `data/model/ResultRankerTest.kt` | Relevans (rubrik/taggar/beskrivning), täckning, dubbletter, sortering, taggfilter, taggmoln, skrapad sökning, snapshot av rangordningen |
| `ui/web/WebAdBlockTest.kt` | Blockerade domäner/vägar, navigeringsbeslut, samma-sajt-logik (inkl. `co.uk`), att Av-nivån fortfarande spärrar extern navigation, injektionsscript, JS-escape |

Kör dem med `./gradlew testDebugUnitTest`. Testerna kräver inget internet: den enda som
talar med en server gör det mot en lokal MockWebServer med eget certifikat.

### 15.2 Instrumenterade regressionstester (17 testfall)

`CalculatorUiTest` innehåller åtta Compose-tester för kalkylatorn och PIN-flödet:

| Test | Vad det kontrollerar |
|---|---|
| `pinSetupRejectsMismatchedConfirmation` | Fel bekräftelse ger felmeddelande och lämnar användaren på kodvalet |
| `pinSetupRejectsEqualCodes` | Huvudkod och reservkod får inte vara lika |
| `savingThePinsShowsTheCalculator` | Sparade koder lämnar kodvalet och visar kalkylatorn |
| `typedDigitsCanBeClearedAndDeleted` | Displayen, `C` och backsteg reagerar korrekt |
| `aCalculationShowsItsResult` | `6*7=` ger 42 i både resultatraden och historiken |
| `aWrongCodeIsCalculatedInsteadOfOpeningTheVault` | Fel kod räknas som ett tal och öppnar inte vaulten |
| `theMainCodeIsNotCalculatedButStartsTheUnlock` | Rätt huvudkod töms utan att visas som ett beräknat resultat |
| `negativeNumbersAndRecoveryAfterDivisionByZeroWork` | Negativa tal fungerar och räknaren återhämtar sig efter division med noll |

`CleanAppStateRule` rensar inställnings- och databasfiler före varje test i
`CalculatorUiTest`. Regeln har ordning 0 och kör därför före Compose-regeln, innan
aktiviteten hinner läsa ett gammalt tillstånd. Keystore-nycklar lämnas kvar med flit.
Displayen använder testtaggarna `calculator_input` och `calculator_result`, eftersom samma
siffertext annars också kan finnas på knapparna.

De övriga nio testfallen är fördelade så här:

| Testfil | Antal | Vad den kontrollerar |
|---|---:|---|
| `LibraryUiTest` | 1 | Favoriter och Watch Later öppnar den valda videon |
| `PinStoreRegressionTest` | 1 | Oläsbara lagrade PIN-koder stänger säkert i stället för att tillåta återställning |
| `VaultRegressionTest` | 6 | Oberoende kopior av databasnyckeln, avbruten migrering, rensning och begränsning av positioner samt fjärrregelomförsök och coroutine-avbrott |
| `WebPlayerHostTest` | 1 | HTML5-helskärmsvyn ersätter och återställer WebView:n samt notifierar Chromium |

Kör dem med `./gradlew connectedDebugAndroidTest` på en emulator eller ansluten enhet.
De är riktade regressionstester och hämtar inget från riktiga videosajter.

### 15.3 CI

| Workflow | När | Vad den verifierar och sparar |
|---|---|---|
| `Build APK` | Varje push eller manuell start | JDK 17 och Android SDK 36; `testDebugUnitTest`, `lintDebug`, `assembleDebug` och `assembleDebugAndroidTest`; APK med 40 MB-tak samt enhets- och lintrapporter |
| `Android UI tests` | Ändringar i app/Gradle/workflow eller manuell start | KVM-accelererad Pixel 5-emulator med API 34/Google APIs; `connectedDebugAndroidTest`; instrumenterad testrapport |

Det snabba jobbet kompilerar alltså de instrumenterade testerna även när emulatorjobbet inte
triggas, exempelvis vid en ren dokumentationsändring. Vid fel skrivs Kotlin-, test- och
lintdiagnostik som **annotations** och i jobbets **summary**. På aktuell `main` (`9fb6ba2`)
är denna dokumentation kontrollerades var både **Build APK 38465146508** och
**Android UI tests 38465146506** gröna.

---

## 16. Kända begränsningar

1. **Uppspelning sker på sajternas sidor.** Kvalitet och spelarkontroller beror därför
   på sajten. Appen kan inte tvinga fram högre kvalitet än sajten erbjuder, och kan inte
   styra sajtens egen spelare mer än via filmens `<video>`-element (spolning, paus,
   återupptagning fungerar; kvalitetsval gör det bara i beta-spelaren).
2. **"Allt som händer i filmen"** betyder allt sajten själv skriver: rubrik, taggar,
   kategorier, medverkande och beskrivning. Ingen bild- eller ljudanalys görs.
3. **Scraping är best-effort.** Ändrar en sajt sin HTML eller sina URL:er slutar
   parsningen att ge träffar tills konfigurationen uppdateras – sedan 7.5 räcker det med
   en ändring i `scrape-rules.json`, alltså ingen ny APK.
4. **Annonsblockeringen är best-effort.** Nya nätverk och nya trick dyker upp; listan
   behöver underhållas.
5. **Sidor som kräver inloggning eller har aggressiva captchas** kan visa sig sämre i
   WebView:n, särskilt i strikt läge.
6. **Det känsliga innehållet är krypterat, men inte allt.** Databasen (SQLCipher) samt
   sökhistorik, uppspelningshistorik och positioner (AES/GCM per värde) är skyddade.
   Kvar i klartext ligger ofarliga inställningar som spolintervall, kvalitet och växlar.
   Databasen har version 1 och därmed ingen schemamigrering ännu.
7. **Krypteringen faller aldrig tillbaka på okrypterat.** Om SQLCipher eller Android
   Keystore inte kan användas visas ett lagringsfel och vaultdatabasen öppnas inte.
   Eventuell äldre klartextdata lämnas orörd tills en krypterad migrering kan göras.
8. **Fjärrreglerna pekar på repots `main`-branch**, så mekanismen är aktiv först när
   `scrape-rules.json` finns där. Byter filen adress måste `ScrapeRuleStore.RULES_URL` byggas om.
9. **UI-testningen är riktad, inte en full sajt-till-video-end-to-end-körning.** Det finns
   emulatorregressioner för kalkylator/PIN, bibliotek, lagrings-/migreringsflöden och
   WebPlayerHost, men inga tester som är beroende av riktiga videosajter eller nätinnehåll.
10. **Inga tester mot riktiga sajter** (medvetet): testerna får aldrig kräva internet.
11. **Språket är hårdkodat på svenska** i koden (`strings.xml` innehåller bara appnamnet).
12. **Ålderskontrollen är en självbekräftelse**, inte en verifiering.

### 16.1 Medvetet valt bort

| Förslag | Varför inte |
|---|---|
| Bild-i-bild (PiP) | En flytande videoruta ovanpå hemskärmen avslöjar appen för vem som helst som tittar på telefonen – rakt emot idén med en räknare som fasad |
| Nedladdning av klipp | Juridiskt riskabelt och tekniskt stort (HLS-segment ska sättas ihop); appen visar sajternas egna sidor i stället |
| Bakgrundsjobb (`WorkManager`) som letar nya videor | Väcker nätverket, drar batteri och lämnar spår i bakgrunden – samma avslöjanderisk som PiP |
| Root- och jailbreak-detektering | Går att kringgå på några sekunder, ger falska larm och skyddar inget som krypteringen inte redan skyddar |
| Rotation av User-Agent | Ser mer ut som ett försök att kringgå sajternas skydd än som artighet; fast, ärlig UA plus tidsgränser och omförsök räcker |
| Diskcache (TTL) av hämtade sidor och sökresultat | Skulle skriva sökningar och titlar i klartext på disken och underminera den krypterade databasen; hämtningar cachas i minnet och strömadresser i 20 minuter |
| Automatisk tömning av urklipp | Appen kopierar själv inget till urklipp (verifierat: ingen `ClipboardManager`-kod finns), och att tömma urklippet skulle radera användarens egen text |
| Uppdelning i moduler (`:core`, `:search`, …) | Byggtiden är redan ~4 minuter i CI; vinsten står inte i proportion till ombyggnaden i den här storleken |

---

## 17. Alla viktiga konstanter

| Område | Konstant | Värde |
|---|---|---|
| Sökning | Max kandidater i minnet | 140 |
| Sökning | Vågstorlek vid inläsning | 10 träffar |
| Sökning | Mål för inlästa träffar per sökning | 20 |
| Sökning | Max vågor / budget per sökning | 6 vågor / 60 träffar |
| Sökning | Budget för "Ladda fler" | 30 träffar |
| Sökning | Max frågor per sökning | 5 |
| Sökning | Samtidiga hämtningar vid inläsning | 8 |
| Sökning | Max träffar per sajt och fråga | 36 (vissa 42) |
| Sökning | Taggmolnets storlek | 24 taggar |
| Nätverk | Timeout vid sidhämtning | 12 s, max 6 MB |
| Nätverk | Tidsgräns per sajt och fråga | 6 s (Wi-Fi) / 9 s (mobildata) |
| Nätverk | Samtidiga sajter | 10 (Wi-Fi) / 4 (mobildata) |
| Nätverk | Samtidiga inläsningar | 8 (Wi-Fi) / 3 (mobildata) / 1 (offline) |
| Nätverk | Omförsök | 2 försök per hämtning, 450 ms paus, bara vid nätverksfel/429/5xx |
| Nätverk | Batterisparläge | under 20 % utan laddare halveras antalet samtidiga anrop (minst 1) |
| Säkerhet | Autolås | när appen lämnas (`background`), 1 min eller 5 min inaktivitet; kontrolleras var 5:e s |
| Sajter | Avstängda sajter | sparas i `disabled_sites`, används inte i sökningar |
| Historik | Positioner | sparas var 5:e sekund, krypterade |
| CI | Tak för APK-storleken | 40 MB (annars misslyckas körningen) |
| Säkerhet | Databasnyckel | 32 slumpade byte, AES/GCM i Keystore |
| Skrapregler | Adress | `raw.githubusercontent.com/.../Calculator_69/main/scrape-rules.json` |
| Skrapregler | Hämtintervall / tidsgräns / tak | 24 h / 8 s / 200 kB |
| Skrapregler | Gränser i valideringen | 40 sajter, 20 selektorer × 80 tecken, mönster 240 tecken, 60 träffar |
| Bilder | Coil-cache | 20 % av minnet, 64 MB på disk, tumnaglar 384×240 |
| Nätverk | OkHttp-timeouts | 20 s anrop, 12 s connect/read/write |
| Metadata | Beskrivning | max 1 500 tecken, 40 per lista, 60 nyckelord |
| Ström | Cachelagring | 20 minuter, 400 poster |
| Ström | Prober | 6 kandidater, `bytes=0-2047`, 4,5 s, HLS max 512 KB |
| Beta-spelare | Buffert | 15 s min / 60 s max, 1,5 s / 3 s |
| Beta-spelare | Skrubbning | känslighet 0,45, förhandsvisning var 180:e ms |
| Beta-spelare | Progress | var 500:e ms, sparas var 5:e s |
| Beta-spelare | Återupptagning | > 15 s, marginal 20 s från slutet |
| Beta-spelare | Dataspararläge | 480p, 900 kbps |
| Beta-spelare | Hastigheter | 0,5 / 0,75 / 1 / 1,25 / 1,5 / 1,75 / 2× |
| WebView-spelare | Pollning / sparning | 1 s / var 5:e s |
| WebView-spelare | Kontroller döljs efter | 4,5 s |
| Historik | Max poster / tröskel | 40 / 15 s (slutmarginal 30 s) |
| Historik | Sökhistorik | max 12 |
| Spolintervall | Valfria värden | 5, 10, 15, 30 s |

---

## 18. Utvecklingshistorik

Den ursprungliga featuregrenen `arena/a0d9d445-calculator-69` utgick från `main` @
`03ccd07` och hade 34 commits. PR #4 utgick efter de första 33 (vid `e04bc46`) och lade
till sex review-fixar, totalt 39 commits som mergades i `main`. PR #3 stängdes därefter
som ersatt, men dess sista dokumentationscommit `ec0a7ca` fanns inte i PR #4. De delar som
fortfarande är aktuella har återförts i den här dokumentationen; inaktuella antal och
körnings-ID:n har inte återställts. Tabellen är ett urval av viktiga milstolpar.

| Commit | Innehåll |
|---|---|
| `8409e31` | Ny sökpipeline: taxonomi, frågeplanering, verifiering av strömmar |
| `8981589` | Fix av `list.first()`-anrop + första versionen av nya spelaren |
| `5693b94` | Nytt UI-lager: tema, komponenter, startsida, sökskärm, bibliotek, inställningar, ny `VaultScreen` |
| `efb35d9` | Spelaren: `keepScreenOn` via `this` i `PlayerView.apply` |
| `d92abcc` | Enhetstester för taxonomi, strömval, rankning, längdtolkning; CI visar testfel som annotations |
| `a67790b` | Bara inlästa träffar som standard + uppdaterad README |
| `687b055` | Dynamiska hoppikoner, haptik, skydd mot smal tidslinje |
| `414a149` | Sökningen visar vilka sajter som felar och hur man utesluter dem |
| `9aa2c7b` | **WebView-spelare med annonsblockering + inläsning av all filminformation** |
| `d100173` | `PlayerState` blir `internal` (publik funktion läckte privat typ) |
| `6696bf3` | Saknad `assertFalse`-import i testet |
| `4065589` | Kvalitetsavläsning av korta etiketter + testfall med identiska rubriker |
| `01dfa82` | **DOKUMENTATION.md** – den här filen |
| `85b90ad` | Robusthet och prestanda: tidsgräns och semafor per sajt, reservparsning, `NetworkCondition`, Coil-cache, `clearSensitiveState`, tre nya testfiler |
| `3b40712` | Kompileringsfixar (`continue` i inline-lambda, deklarationsordning i `VaultScreen`) |
| `b292d64` | **SQLCipher: krypterad databas**, Keystore-skyddad nyckel, engångsmigrering + `VaultDatabasePlanTest` |
| `4165dea` | compileSdk 36 (krav från SQLCipher-aaret) + CI installerar SDK 36 |
| `68ef59a` | Tre testfixar: slug-fallback i reservparsningen, nätverkstestet beskriver rätt beteende |
| `0f47ec4` | **Fjärrkonfigurerbara skrapregler** (`scrape-rules.json`, validering, cache, inställningar) + `ScrapeRuleTest` |
| `34c8f04` | **MockWebServer-tester** över TLS + injicerbar klient i `Fetch`/`MetadataExtractor` |
| `c677a06`, `6f39e04`, `7fe21b9` | Dokumentationen uppdaterad (kryptering, robusthet, fjärrregler, tester) |
| `4056921` | **Instrumenterade UI-tester**: sju första Compose-tester, `CleanAppStateRule`, testtaggar och egen emulatorworkflow |
| `95fcaa5`, `ef94c0f`, `e04bc46` | CI-/testfixar: POSIX-säkert emulatorskript, `assembleDebugAndroidTest` i snabbjobbet och rättad Compose-testimport |
| `ec0a7ca` | Dokumenterade UI-testerna och CI; låg endast kvar på den stängda PR #3-grenen och återfanns vid grenrevisionen |
| `82dbbc1` | **Säkerhet**: krypterad sökhistorik och uppspelningshistorik (`CipherPayload`, `SecureValue`), autolås vid bakgrund och inaktivitet + `CipherPayloadTest` |
| `612f66a` | **Stabilitet och UX**: omförsök med backoff i `Fetch`, batterisparläge, sajter som kan stängas av permanent, rättad "Fortsätt titta" (positionerna sammanfogas igen), Dependabot, APK-storlekskontroll, snapshot-test av rangordningen + `FetchRetryTest` |
| `76b597e`, `9b68218` | Reviewfixar för vaultens livscykel, avbrytning av sökning, legacy-migrering, fail-closed-lagring, PIN-material och återupptagning |
| `6de9a7a`, `b89b0e2`, `d36964d`, `6e89b77` | Lintfixar, WebView-navigeringsregressioner, spelargester/sökning/ljusstyrka och Media3 opt-in |
| `650fc10` | Merge av PR #4 till `main` |
| `fb47444`, `5a8c27b` | PR #5: källsajtbegränsade sökomdirigeringar, detaljlänkar, HTML5-helscreen-host, bounded resume-data och regressionsfixar |
| `963674e` (PR #17, merge) | **Samordnad verktygskedja**: Gradle 9.8.1, AGP 9.4.1 med inbyggt Kotlin-stöd (kotlin-android tillämpas inte längre), Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00, Room 2.8.5, Media3 1.11.1, coroutines 1.11.0; compileSdk 37 (krav från Compose 1.12), material-ikoner frysta på 1.7.8, CI installerar SDK 37; ersätter Dependabot-PR #9, #10, #15 och #16 – okhttp (#11) ligger kvar på 4.12.0 och är uppskjuten |

Efter merge av PR #5 verifierades `main` @ `9fb6ba2` grönt av snabbworkflow
**38465146508** (JVM-tester, lint, APK och kompilering av instrumenterade tester) och
emulatorworkflow **38465146506** (17 instrumenterade regressioner). På PR #5-grenen
fångade den första appkodskörningen (`38462442461`) ett Kotlin-kompileringsfel i testets
OkHttp DNS-resolver; det rättades i `5a8c27b`.

Efter merge av PR #17 verifierades `main` @ `963674e` grönt av både snabbworkflow
**38504473822** (190 JVM-testfall, lint, debug/release-APK) och emulatorworkflow
**38504473848** (17 instrumenterade regressioner). PR #17 slog också ihop de fem
enhetstestfel som PR #18 lämnade efter sig på `main`: rare-term-synonymer i
`ResultRanker`, tre nya konceptkluster i `OnnxEmbeddingEngine`, citatfrasordning i
`SearchPlanner` samt `WebAdBlockTest` tillbaka på okhttp 4.12:s klassvägsbaserade
public suffix-lista (testberoendena justerades tillbaka till 4.12.0). Dependabot-PR
#9/#10/#15/#16 stängdes som ersatta och #11 (okhttp 5.5.0) som uppskjuten –
nätverkskedjan ligger samlad på 4.12.0 i väntan på en egen samordnad migrering.

---

## 19. Ordlista

| Term | Betydelse |
|---|---|
| **Vault** | Den dolda delen av appen bakom PIN + biometri |
| **Decoy** | Reservkoden som öppnar en oskyldig vy i stället |
| **Träff / resultat** | En video i resultatlistan (`SearchResult`) |
| **Inläsning / enrich** | Hämtning av detaljsidan för att läsa ut all metadata |
| **Playability** | `PLAYABLE` = sidan svarar och innehållet finns; `CHECKING` = läses in nu; `UNPLAYABLE` = borta/osvarar; `UNVERIFIED` = ej påbörjad |
| **Taggmoln** | De vanligaste taggarna bland aktuella träffar, klickbara |
| **Kö** | Träffarna i aktuell ordning, så att nästa/föregående fungerar i spelaren |
| **Blockeringsnivå** | Standard / Strikt / Av för WebView-spelaren |
| **Beta-spelare** | Den inbyggda ExoPlayer-spelaren som använder en extraherad ström |

---

## 20. Juridik och ansvar

- Appen är avsedd för **personligt bruk**.
- Allt innehåll tillhör respektive sajt och rättighetsinnehavare. Appen lagrar inget
  innehåll, utan visar sajternas egna sidor i en webbläsare och listar deras offentliga
  sökresultat.
- Scraping kan strida mot en sajts användarvillkor – kontrollera dem och följ dem.
- **Kontrollera att du följer lokal lagstiftning** där du befinner dig, inklusive regler
  om vuxet material, upphovsrätt och ålder.
- Appen innehåller filter mot innehåll som kan avse minderåriga, men **ingen teknisk
  åldersverifiering** av medverkande eller användare.
- Författaren till appen ansvarar inte för hur den används.

---

*Dokumentet är den aktuella tekniska översikten och uppdateras tillsammans med koden.
Ändras koden ändras detaljerna; verifiera JVM- och emulatorregressionerna samt byggresultatet
i GitHub Actions innan en release.*
