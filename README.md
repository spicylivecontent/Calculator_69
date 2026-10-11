# Calculator

Diskret miniräknar-app

## Status
- ✅ Steg 1–13
- ✅ Steg 14: Reklamblockering & nätverkssäkerhet

## Steg 14 – Nätverkssäkerhet

### AdBlockInterceptor
Blockerar kända reklam- och trackingdomäner (DoubleClick, JuicyAds, ExoClick, Google Analytics, Facebook Pixel, m.fl.).
Requests till dessa hostar når aldrig nätverket.

### PrivacyInterceptor
- Tar bort tracking-queryparametrar (`utm_*`, `fbclid`, `gclid`, m.fl.)
- Sätter `DNT: 1` och `Sec-GPC: 1`
- Använder en generisk mobil User-Agent
- Tar bort WebView-signaturer

### Network security
- `usesCleartextTraffic="false"` – endast HTTPS
- Egen `network_security_config.xml`
- Delad `NetworkClient` används av alla extractors

Eftersom appen **inte** laddar webbsidor i en WebView utan bara hämtar HTML för parsing och spelar rena stream-URL:er, syns nästan ingen reklam i praktiken. Interceptorerna är ändå ett extra skydd.

## PIN-koder
- **6969** + biometri → Riktiga vaulten
- **1234** → Decoy-vault

## Nästa steg
Steg 15: Polering, stream-extract (spela riktiga videor) & slutfinish
