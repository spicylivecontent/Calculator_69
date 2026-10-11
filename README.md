# Calculator

Diskret miniräknar-app

## Status
- ✅ Steg 1–10: Setup → Multi-site scraping
- ✅ Steg 11: Avancerad filtrering

## Filtrering (Steg 11)

### Tillfälliga negativa filter
I Search-fliken finns ett rött fält:
```
Exkludera (kommaseparerat): anal, rough, ...
```
Alla resultat vars titel innehåller något av orden filtreras bort direkt.

### Permanent "Never show again"
Varje resultatkort har en röd block-ikon.
Tryck på den → ordet sparas i databasen som en permanent regel och försvinner från listan.

### Hur det fungerar
1. Sökningen hämtar rådata från Eporner + SpankBang + HQporner
2. Resultaten körs genom dina negativa regler (UI + databas)
3. Endast rena träffar visas

## PIN-koder
- **6969** + biometri → Riktiga vaulten
- **1234** → Decoy-vault

## Nästa steg
Steg 12: AI-sök & semantisk expansion (valfritt / mer avancerat)
