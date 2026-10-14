# Underhållsgranskning – 2026-10-14

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
| #7 | Kotlin 2.0.21 → 2.4.21 | `kotlinOptions.jvmTarget` har blivit ett fel. DSL-felet rättas här, men KSP är fortfarande 2.0.21-1.0.25. En uppdatering av Kotlin ensam är inte en verifierad verktygskedja. |
| #8 | coroutines 1.9.0 → 1.11.0 | Kotlin-metadata 2.2.0 mot kompilatorns 2.0.0 i både coroutines och transitiv stdlib. Kräver samordnad Kotlin/KSP-uppgradering. |
| #9 | Compose BOM 2024.10.01 → 2026.09.00 | `checkDebugAarMetadata` misslyckades. Exakt SDK/AGP-krav kunde inte läsas från annotations; råloggarna var inte åtkomliga i granskningsmiljön. Kraven måste kontrolleras, inte antas. |
| #10 | Media3 1.5.0 → 1.11.1 | Transitiv stdlib har Kotlin-metadata 2.2.0 mot kompilatorns 2.0.0. Kräver samordnad uppgradering. |
| #11 | OkHttp 4.12.0 → 5.5.0 | `checkDebugAarMetadata` misslyckades. Exakt orsaksrad saknas i tillgängliga annotations; kompatibilitet och MockWebServer-API måste verifieras i ny CI-körning. |

Dependabot grupperar hädanefter Kotlin-pluginerna och KSP för att minska risken
för fristående, inkompatibla compiler-uppdateringar. Gruppering är inte en garanti
för kompatibilitet, och befintliga PR:er kan behöva ersättas efter nästa körning.

### Nästa steg för beroendena

1. Välj och dokumentera en kompatibel uppsättning AGP/Gradle/Kotlin/KSP/Room.
2. Uppdatera kompilator och compiler plugins tillsammans på arbetsgrenen.
3. Läs de nya AAR-kraven för Compose och nätverkskedjan ur fullständiga byggloggar.
4. Kör JVM-tester, lint, debug/release-byggen och instrumenterade tester.
5. Mergea först när alla relevanta kontroller är gröna. Stäng inte de gamla PR:erna
   som lösta förrän deras ändringar verkligen ingår i en verifierad ersättare.

## Testmiljö

Den lokala miljön saknar Java och Android SDK och kan inte hämta Androids
beroenderepositorier. Android-valideringen måste därför ske i GitHub Actions.
Slutlig test- och merge-status redovisas i pull requesten och i svaret, inte som
ett antaget resultat här.
