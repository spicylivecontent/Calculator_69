# Calculator

Diskret miniräknar-app med en dold "vault" för vuxet innehåll (personlig användning).

## Bygg APK lokalt

Projektet innehåller Gradle Wrapper. Med JDK 17 och Android SDK Platform 36 installerat, kör från repots rot:

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

## Så fungerar det

1. **Sökningen** skrapar resultatlistor från alla valda sajter och skickar flera konkreta sökfrågor per sökning.
2. **Inläsningen** hämtar varje träffs detaljsida och läser ut *all* information som finns där: rubrik, taggar, kategorier, medverkande, kvalitet, längd, publiceringsdatum, visningar och beskrivningen – det vill säga så mycket av vad som händer i filmen som sajten själv beskriver. Sidor som inte svarar eller där innehållet tagits bort tas bort ur listan.
3. **Resultatlistan** visar små tumnaglar med rubrik, taggar, kvalitet och beskrivning – precis som förut, men nu med all skrapad information synlig och sökbar.
4. **Uppspelningen** sker på sajten i en inbyggd WebView med appens annonsblockering, i stället för att en stream-URL extraheras och spelas i en egen spelare.

## Uppspelning i WebView med annonsblockering

- **Blockerade domäner.** Samma lista över annons-, popunder- och trackingnätverk som används vid all annan hämtning (OkHttp) används också av WebView: begärda resurser till dessa domäner avbryts innan de lämnar enheten, och antalet blockerade förfrågningar visas i spelaren.
- **Popups och omdirigeringar.** Nya fönster blockeras helt (`window.open` neutraliseras och `onCreateWindow` nekar), länkar till andra sajter och app-scheman (`intent://`, `market://` …) stoppas, och klartext (http) tillåts aldrig.
- **Städning på sidan.** En injicerad regeluppsättning gömmer och tar bort annonsytor, popunder-element och annons-iframes, tar bort `target="_blank"` och ser till att filmens `<video>`-element får kontroller.
- **Helskärm för video.** Sajternas HTML5-helskärmsläge visas ovanpå WebView:n; appens egna kontroller döljs medan sajten spelar i helskärm, och Bakåt stänger helskärmsläget före sid-/spelarnavigering.
- **Tre nivåer.** Standard (rekommenderad), Strikt (blockerar allt externt) och Av. Nivån väljs i spelarens meny eller i Inställningar.
- **Samtycke och cookies.** Tredjepartscookies är avstängda, geolokalisering av och behörighetsförfrågningar nekas.
- **Samma kontroller som förut.** Knappar och dubbeltryck för att spola framåt/bakåt (5/10/15/30 s), pausa, nästa/föregående video i kön, tidslinje med position, samt återupptagning där du slutade (positionen skickas till filmens `<video>`-element).
- **Direktuppspelning (beta).** I spelarens meny finns möjligheten att i stället extrahera en mediaström och spela den i appens egen spelare – med kvalitetsval från strömmens egna spår, skrubbning och hastighetsval. Standardvägen är alltid WebView.

## Sökning med all skrapad information

- **Kategorier och underkategorier.** 12 kategorier med omkring 120 underkategorier (t.ex. *Utseende → Blond*, *Plats → Kök*, *Kamera & vinkel → POV*). Etiketterna är svenska, men sökningarna använder de engelska termer sajterna indexerar. Välj flera underkategorier samtidigt och kombinera dem med fritext.
- **Allt du söker kommer med.** Varje sökning blir flera konkreta frågor: en huvudsökning med alla positiva termer, plus varianter för valda kategorier, enskilda ord och kända synonymer (t.ex. *brunett* → `brunette`, *rött hår* → `redhead`/`red hair`). Citattecken håller ihop fraser, och "utan/inte/no/-ord" blir filter i stället för sökord.
- **Avancerad sökning i all information.** Ett eget sökfält matchar mot allt som skrapats in – rubrik, taggar, kategorier, medverkande och beskrivning – och sorterar träffarna efter hur väl de täcker sökningen.
- **Taggar att klicka på.** Varje träff visar sina taggar; ett klick filtrerar listan direkt och gör samtidigt en ny sökning på taggen på sajterna. Ett taggmoln över resultaten visar de vanligaste taggarna bland träffarna.
- **Filter och flöde.** Välj sajter, längd (kort/medel/lång), sortering (relevans/längst/kortast/källa), exkluderade ord, "ladda fler" för nästa sida, sparade sökningar och sökhistorik. En statusrad per sajt visar hur många träffar varje sajt gav.

## Bara resultat som fungerar

Varje träff kontrolleras innan den visas: appen hämtar detaljsidan, läser in informationen och markerar träffen som inläst ("Sidan svarar"). Träffar vars sida inte svarar, eller där innehållet tagits bort, döljs när filtret **"Bara sidor som svarar"** är på (standard). Under tiden listan fylls på visas de träffar som är under inläsning med status "Läser info".

## Sajter som kan rättas utan en ny APK

En sajt som byter adress eller layout kan rättas i `scrape-rules.json` i det här repot. Appen
hämtar filen högst en gång i dygnet (kan stängas av i inställningarna), och filen innehåller
**bara data** – adresser, URL-mönster och CSS-selektorer, aldrig kod. Allt valideras: HTTPS
krävs, versionen måste vara nyare än appens, och regler som inte klarar kontrollen hoppas
över. Misslyckas hämtningen fortsätter appen med de inbyggda reglerna.

## Atkomst och integritet

- Vid första start skapar du en huvudkod och en separat reservkod (4–12 siffror).
- Koderna lagras krypterade med AES/GCM och en nyckel från Android Keystore.
- Huvudkoden krävs tillsammans med biometrisk autentisering. Om biometri saknas förblir vaulten låst.
- En ålderbekräftelse visas innan vuxeninnehåll öppnas; det är endast en självbekräftelse, inte faktisk åldersverifiering.
- Sökningar och träffar filtreras mot en blocklista så att innehåll som kan avse minderåriga stoppas, och taxonomin innehåller inga sådana termer (verifieras i enhetstesterna).
- Uppspelning sker i en sandboxad WebView utan fil- eller innehållsåtkomst, utan tredjepartscookies och utan behörigheter, medan all egen hämtning går via HTTPS med annons- och trackingblockering.
- Sökresultat, favoriter och uppspelningshistorik lagras lokalt i appens privata lagring. **Hela Room-databasen är krypterad med SQLCipher** (om krypteringen inte kan användas visas ett lagringsfel; ingen klartextdatabas skapas), och **sökhistorik, uppspelningshistorik och positioner krypteras värde för värde** (AES/GCM) med nycklar i Android Keystore. Enklare inställningar (spolintervall, kvalitet, växlar) ligger i klartext, så appen ska inte ses som skydd mot en rootad eller komprometterad enhet.
- **Autolås** (valfritt): vaulten låser sig när appen lämnas eller efter 1/5 minuters inaktivitet, och nästa upplåsning kräver kod och biometri igen.

## Sajter

Eporner, SpankBang, HQPorner, XVideos, XNXX, xHamster, Pornhub, Beeg, YouPorn, PornTrex.

## Viktigt

Uppspelningen sker på sajternas egna sidor, så appen kan inte påverka vilken kvalitet eller vilka spelarkontroller en sajt erbjuder – men allt som går att spela upp går att titta på, oavsett om en separat ström kan extraheras eller inte. Annonsblockeringen är best-effort: nya annonsnätverk dyker upp löpande.

Scraping, inläsning och annonsblockering kan sluta fungera när sajterna ändrar sina sidor. Kontrollera att du följer lokal lagstiftning och respektive sajts villkor.
