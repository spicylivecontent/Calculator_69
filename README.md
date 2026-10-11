# Calculator

Diskret miniräknar-app

## Status
- ✅ Steg 1–11
- ✅ Steg 12: On-device AI-sökexpander

## On-device AI (Steg 12)

En **lättvikts, helt lokal** query-expander som:

- Tolkar naturligt språk (`brunette pov slow, no anal, no rough`)
- Extraherar **positiva** söktermer (+ enkla synonymer)
- Extraherar **negativa** termer automatiskt (`no X`, `utan X`, `never X`)
- Visar en kort förklaring under sökfältet
- Kör 100 % on-device – ingen molntjänst, ingen stor modell

### Exempel
```
Input:  brunette in kitchen pov, no anal, no rough
AI:     söker “brunette brown hair dark hair kitchen cooking pov ...”
        filtrerar bort: anal, rough
```

Strukturen är förberedd så att man senare kan byta ut `QueryExpander` mot en riktig TFLite/ONNX-modell utan att ändra resten av appen.

## PIN-koder
- **6969** + biometri → Riktiga vaulten
- **1234** → Decoy-vault

## Nästa steg
Steg 13: Unified Feed + Library + Rekommendationer (eller polering)
