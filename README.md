# Calculator

Diskret miniräknar-app med en dold "vault" för vuxet innehåll (personlig användning).

## Bygg APK lokalt

Projektet innehåller Gradle Wrapper. Med JDK 17 och Android SDK Platform 35 installerat, kör från repots rot:

```bash
./gradlew assembleDebug
```

På Windows:

```bat
gradlew.bat assembleDebug
```

APK-filen hamnar i `app/build/outputs/apk/debug/app-debug.apk`.

Kör enhetstesterna (ren JVM-logik, ingen enhet behövs):

```bash
./gradlew testDebugUnitTest
```

## Bygg APK via GitHub Actions

Workflowen **Build APK** körs automatiskt vid push till valfri branch och kan också startas manuellt från **Actions**-fliken med **Run workflow**. Den kör enhetstesterna och `assembleDebug`; när jobbet är klart laddar du ner artifacten **Calculator-debug-apk** och packar upp `app-debug.apk`. Om något går fel listas både Kotlin-fel och misslyckade tester som annotations på körningen.

## Uppspelning

- **Bildkvalitet från källan.** Spelaren läser strömmens egna videospår och visar en riktig kvalitetsmeny: automatisk (adaptiv), högsta möjliga, "spara data" samt varje höjd som strömmen faktiskt erbjuder (t.ex. 1080p, 720p, 480p) med uppmätt bitrate. Valet styr ExoPlayers spårval direkt, så du ser vilken kvalitet som spelas och vad den ligger på i Mbps.
- **Adaptiva strömmar.** HLS-masterplaylists spelas upp adaptivt och kan låsas till en viss höjd; extraherade varianter rangordnas så att högsta kvalitet provas först.
- **Spola och hoppa.** Dra horisontellt var som helst i bilden för att skrubba med förhandsvisning av tiden, dubbeltryck till vänster/höger för att hoppa bakåt/framåt (intervallet ställs in i Inställningar: 5/10/15/30 s), knappar för samma hopp i mitten och en tidslinje som visar både spelad tid och buffrat innehåll. Skrubbning använder snabbare seek-parametrar under draget och exakt position när du släpper.
- **Mer kontroll.** Hastighetsval 0,5–2×, bildläge (anpassa/fyll skärmen/zooma), ljusstyrka med vertikalt drag på vänster sida och volym till höger, håll skärmen tänd, samt återupptagning där du slutade senast (med möjlighet att börja om).
- **Kö.** Nästa/föregående video i resultatlistan kan spelas direkt från spelaren; går en ström inte att spela upp försöker appen hämta en ny URL och tar annars bort träffen.

## Sökning

- **Kategorier och underkategorier.** 12 kategorier med omkring 120 underkategorier (t.ex. *Utseende → Blond*, *Plats → Kök*, *Kamera & vinkel → POV*). Etiketterna är svenska, men sökningarna använder de engelska termer sajterna indexerar. Välj flera underkategorier samtidigt och kombinera dem med fritext.
- **Allt du söker kommer med.** Varje sökning blir flera konkreta frågor: en huvudsökning med alla positiva termer, plus varianter för valda kategorier, enskilda ord och kända synonymer (t.ex. *brunett* → `brunette`, *rött hår* → `redhead`/`red hair`). Citattecken håller ihop fraser, och "utan/inte/no/-ord" blir filter i stället för sökord.
- **Filter och flöde.** Välj vilka sajter som ska användas, längd (kort/medel/lång), sortering (relevans/längst/kortast/källa), exkluderade ord, "ladda fler" för nästa sida, sparade sökningar och sökhistorik. En statusrad per sajt visar hur många träffar varje sajt gav.

## Bara spelbara resultat

Varje träff kontrolleras innan den visas: appen hämtar detaljsidan, plockar ut kandidatströmmar, rangordnar dem och gör en probe (Range-förfrågan) för att verifiera att svaret är video, HLS eller octet-stream – HLS-manifest måste dessutom börja med `#EXTM3U`. Träffar som redan kontrollerats sparas i en cache i 20 minuter.

Som standard visas **bara videor som verifierats som spelbara** (plus de som kontrolleras just nu, med status "Kontrollerar"). Okontrollerade träffar kan visas genom att stänga av "Bara spelbara" i sökningen eller i Inställningar.

## Atkomst och integritet

- Vid första start skapar du en huvudkod och en separat reservkod (4–12 siffror).
- Koderna lagras krypterade med AES/GCM och en nyckel från Android Keystore.
- Huvudkoden krävs tillsammans med biometrisk autentisering. Om biometri saknas förblir vaulten låst.
- En ålderbekräftelse visas innan vuxeninnehåll öppnas; det är endast en självbekräftelse, inte faktisk åldersverifiering.
- Sökningar och träffar filtreras mot en blocklista så att innehåll som kan avse minderåriga stoppas, och taxonomin innehåller inga sådana termer (verifieras i enhetstesterna).
- Sökresultat, favoriter och uppspelningshistorik lagras lokalt i appens privata lagring. **Room-databasen är inte krypterad**, så appen ska inte ses som skydd mot en rootad eller komprometterad enhet.
- Appen blockerar kända annons- och trackingdomäner och skickar inga uppgifter till tredje part.

## Sajter

Eporner, SpankBang, HQPorner, XVideos, XNXX, xHamster, Pornhub, Beeg, YouPorn, PornTrex.

## Viktigt

Kontrollen minskar antalet trasiga resultat, men kan inte garantera att en ström inte går ut eller blockeras efter kontrollen. Spelaren kan inte skapa högre kvalitet än källan erbjuder.

Scraping och stream-extraktion är best-effort och kan sluta fungera när sajterna ändrar sina sidor. Kontrollera att du följer lokal lagstiftning och respektive sajts villkor.
