# Calculator

Diskret miniräknar-app

## Status
- ✅ Steg 1–6: Setup, miniräknare, PIN, biometri, diskretion, vault-navigation
- ✅ Steg 7: Lokal databas (Room)

## Databas (Steg 7)

Room-databas med följande tabeller:

| Tabell | Syfte |
|--------|------|
| `videos` | Metadata för videor (titel, thumbnail, källa, stream-url, taggar) |
| `favorites` | Sparade favoriter |
| `watch_later` | Titta senare |
| `dislike_rules` | Never-show-again regler (keyword/tag/performer/video/site) |
| `search_presets` | Sparade sökfilter |
| `app_settings` | Key-value för PIN och andra inställningar |

Repository-klass finns redo att användas från ViewModels.

## PIN-koder
- **6969** + biometri → Riktiga vaulten
- **1234** → Decoy-vault

## Nästa steg
Steg 8: ExoPlayer + grundläggande videoplayer
