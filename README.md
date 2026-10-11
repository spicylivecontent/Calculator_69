# Calculator

Diskret miniraknar-app (personlig anvandning)

## Bygg APK lokalt

Projektet innehaller Gradle Wrapper. Med JDK 17 och Android SDK Platform 35 installerat, kor fran repots rot:

```bash
./gradlew assembleDebug
```

Pa Windows:

```bat
gradlew.bat assembleDebug
```

APK-filen hamnar i `app/build/outputs/apk/debug/app-debug.apk`.

## Bygg APK via GitHub Actions

Workflowen **Build APK** kors automatiskt vid push till valfri branch och kan ocksa startas manuellt fran **Actions**-fliken med **Run workflow**. Nar jobbet ar klart laddar du ner artifacten **Calculator-debug-apk** och packar upp `app-debug.apk`.

## Atkomst och integritet

- Vid forsta start skapar du en huvudkod och en separat reservkod (4–12 siffror).
- Koderna lagras krypterade med AES/GCM och en nyckel fran Android Keystore.
- Huvudkoden kravs tillsammans med biometrisk autentisering. Om biometri saknas forblir vaulten last.
- En alderbekraftelse visas innan vuxeninnehall oppnas; det ar endast en sjalvbekraftelse, inte faktisk aldersverifiering.
- Sokresultat och favoriter lagras lokalt i Room i appens privata lagring. **Room-databasen ar inte krypterad**, sa appen ska inte ses som skydd mot en rootad eller komprometterad enhet.

## Sajter

Eporner, SpankBang, HQporner, XVideos, XNXX, xHamster, Pornhub, Beeg, YouPorn, PornTrex.

## Viktigt

Scraping och stream-extraktion ar best-effort och kan sluta fungera nar sajterna andrar sina sidor. Kontrollera att du foljer lokal lagstiftning och respektive sajts villkor.
