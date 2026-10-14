# Underhållsgranskning – 2026-10-14

> **Status: AVSLUTAD 2026-10-14.** PR #17 (verktygskedja + innehållsport +
> testfixar) är mergad till `main` med grönt CI; Dependabot-PR #9/#10/#15/#16 är
> stängda som ersatta och #11 som uppskjuten. Alla objekt nedan är avhandlade.

## Omfattning

Granskningen avser generella bygg-, säkerhets- och beroendeändringar. Ingen ny
innehållskälla, sökfunktion, skrapning, metadata-cache eller värdbegränsning ingår.
`fix/search-precision` och de innehållsspecifika delarna av
`fix/priority-improvements` har inte mergats.

## Säkerhet och bygg från `fix/priority-improvements`

- **Biometri:** hjälparen är inkopplad i kalkylatorskärmen. Tillgänglighetskontroll
  och prompt använder samma autentiseringsnivå. PIN krävs fortfarande först;
  avsaknad av biometri eller avbrott ger aldrig tillträde. Prompten avbryts när
  skärmen tas bort. En engångsgrind ignorerar sena och dubbla callbacks, med JVM-tester.
- **Autolås:** main har redan en aktiv timer med `SystemClock.elapsedRealtime()`.
  Den oanvända hjälparen från Groks gren använder ändringsbar väggklocka och
  tillför inget; den importeras därför inte. Befintlig policy ändras inte.
- **Room:** schemaexport är aktiverad och KSP har en faktisk `schemaLocation`.
  JSON sparas som CI-artifact. Databasversionen är fortfarande 1; ingen destruktiv
  migration eller ändring av SQLCipher-konfigurationen införs.
- **Release:** R8 och resurskrympning är aktiverade. Breda keep/dontwarn-regler
  från förslaget ersätts av begränsade regler för JNI och uttrycksmotorn;
  bibliotekens egna consumer rules används i övrigt. CI bygger nu även release.
- **Bygg-API:** Kotlin använder `compilerOptions` i stället för den utfasade
  `kotlinOptions.jvmTarget`. Java och Kotlin behåller JVM 11.

Schemaexport kräver inte lagringsbehörighet. Baslinjeschemat ska hämtas från
CI och checkas in innan en framtida versionsökning; se `app/schemas/README.md`.
R8-kompilering verifierar inte ensam alla körningsvägar i en release-APK.

## Öppna beroendeuppdateringar

De fem befintliga PR:erna hade röda bygg- och UI-kontroller vid granskningen.
Deras ursprungliga testkörningar baseras på äldre commits, inte denna ändring.
De mergas därför inte automatiskt och lämnas öppna för samordnad uppgradering.

| PR | Uppdatering | Observerat fel / kvarstående kontroll |
| --- | --- | --- |
| #15 | Kotlin 2.0.21 → 2.4.21 + KSP 2.3.12 + coroutines 1.11.0 | Bygg meddelade inga kompilationsrader i annotations; kräver AGP 9:s inbyggda Kotlin-stöd och Gradle 9.6+. |
| #16 | Room 2.6.1 → 2.8.5 | Körs i kombination med KSP 2.3.12; kräver verifiering av schemaexport och SQLCipher-samarbete. |
| #9 | Compose BOM 2024.10.01 → 2026.09.00 | `checkDebugAarMetadata` misslyckades. Compose 1.12 kräver compileSdk 37 och minst AGP 9.1.1. |
| #10 | Media3 1.5.0 → 1.11.1 | Transitiv stdlib har Kotlin-metadata 2.2.0 mot kompilatorns 2.0.0. Kräver samordnad uppgradering. |
| #11 | OkHttp 4.12.0 → 5.5.0 | Uppskjuten. okhttp 5 läser public suffix-listan från appens assets på Android, vilket kräver Android-kontext även i JVM-tester; dessutom drar MockWebServer 5.5.0 in okhttp 5 på testklassvägen medan appen ligger kvar på 4.12.0. PR #17 håller hela nätverkskedjan samlad på 4.12.0 i stället. |

Dependabot grupperar hädanefter Kotlin-pluginerna och KSP för att minska risken
för fristående, inkompatibla compiler-uppdateringar. Gruppering är inte en garanti
för kompatibilitet, och befintliga PR:er kan behöva ersättas efter nästa körning.

### Samordnad ersättare (landad: PR #17, merge `963674e`)

Fem enskilda uppdateringar kan inte bli gröna var för sig; de låser varandras
krav. En gemensam gren uppgraderar hela kedjan samtidigt:

| Del | Från | Till | Skäl |
| --- | --- | --- | --- |
| Gradle (wrapper) | 8.9 | 9.8.1 | AGP 9.4 kräver Gradle 9.6.0+. |
| AGP | 8.7.3 | 9.4.1 | Inbyggt Kotlin-stöd (kotlin-android-pluginen tillämpas inte längre); stödjer compileSdk 37. |
| Kotlin | 2.0.21 | 2.4.21 | media3 1.11.1 och coroutines 1.11.0 kräver Kotlin-metadata 2.2+. |
| KSP | 2.0.21-1.0.25 | 2.3.12 | KSP 2.3.12 stödjer AGP 9:s inbyggda Kotlin; versioneras numera fristående från Kotlin. |
| Compose BOM | 2024.10.01 | 2026.09.00 | Målet för PR #9; kräver compileSdk 37. |
| material-icons-extended | via BOM | 1.7.8 (fryst) | Ingår inte i BOM:ens 2026-rad. |
| Room | 2.6.1 | 2.8.5 | Målet för PR #16. |
| Media3 | 1.5.0 | 1.11.1 | Målet för PR #10. |
| OkHttp (+ mockwebserver, okhttp-tls) | 4.12.0 | 4.12.0 (oförändrad) | PR #11 uppskjuten (se Dependabot-tabellen); testberoendena hålls på samma version som appen för en konsekvent klassväg. |
| coroutines | 1.9.0 | 1.11.0 | Målet i PR #15. |
| compileSdk | 36 | 37 | Krav från Compose 1.12. targetSdk (35) och minSdk (26) ändras inte. |

JVM-målet för Java och Kotlin förblir 11. Databasversionen är fortfarande 1 och
SQLCipher-konfigurationen är oförändrad; Room 2.8.5 exporterar samma schema.

Grenen tar dessutom med de innehållsspecifika delar av `fix/priority-improvements`
som granskningen medvetet skjutit upp, nu omskrivna mot aktuell main i stället för
att merga grenens äldre utkast (som saknade dokumentationskommentarer och
innehöll redan ersatt säkerhets-/bygglogik):

- **LuxureTV** (en.luxuretv.com) som elfte sajt, med exemplelänk i `SiteConfigTest`
  och ett eget listningstest.
- **MetadataCache**: kort TTL-minnecache (20 min, max 256 poster) kopplad till
  `MetadataExtractor.fetch`, deterministiskt testad med injicerad klocka.
- **HostRateLimiter**: per-värd-begränsning (280 ms mellan startider, högst två
  samtidiga) kring `Fetch.page`, med tester för mellanrum, tak och släpp vid fel.

OkHttp får ligga kvar på 4.12.0 i denna gren: 5.x läser public suffix-listan från
appens assets på Android och kräver därmed Android-kontext i JVM-testerna, vilket
inte motiverar migreringsarbetet här (Dependabot #11 stängs som uppskjuten).
Säkerhets- och byggdelarna på `fix/priority-improvements` ingår inte: de ersattes
redan av PR #14 (smalare Proguard-regler, aktiv autolåstimer, biometri-hjälpare).

### Nästa steg för beroendena

1. ~~Välj och dokumentera en kompatibel uppsättning AGP/Gradle/Kotlin/KSP/Room.~~ (tabellen ovan)
2. ~~Uppdatera kompilator och compiler plugins tillsammans på arbetsgrenen.~~ (gjort på arbetsgrenen)
3. ~~Läs de nya AAR-kraven för Compose och nätverkskedjan ur fullständiga byggloggar.~~ (Compose 1.12: compileSdk 37 + AGP 9.1.1; okhttp-android 5.5.0: minCompileSdk 37)
4. ~~Kör JVM-tester, lint, debug/release-byggen och instrumenterade tester.~~ (grönt på arbetsgrenen och på `main` efter merge)
5. ~~Mergea först när alla relevanta kontroller är gröna. Stäng inte de gamla PR:erna
   som lösta förrän deras ändringar verkligen ingår i en verifierad ersättare.~~
   (PR #17 mergad till `main` 2026-10-14, `963674e`; #9/#10/#15/#16 stängda som ersatta,
   #11 som uppskjuten; `main` grönt i både Build APK och UI-tester)

Utöver beroendena landade även de fem enhetstestfel som PR #18 lämnade efter sig på
`main` (rare-term-synonymer i `ResultRanker`, tre nya konceptkluster i
`OnnxEmbeddingEngine`, citatfrasordning i `SearchPlanner`) – se PR #17:s beskrivning.

## Testmiljö

Den lokala miljön saknar Java och Android SDK och kan inte hämta Androids
beroenderepositorier. Android-valideringen måste därför ske i GitHub Actions.
Slutlig test- och merge-status redovisas i pull requesten och i svaret, inte som
ett antaget resultat här.
