# Calculator

Diskret miniräknar-app

## Status
- ✅ Steg 1: Projektsetup
- ✅ Steg 2: Fullt fungerande miniräknare
- ✅ Steg 3: Hemlig PIN-unlock
- ✅ Steg 4: Biometrisk autentisering + decoy-PIN
- ✅ Steg 5: Diskretion & säkerhetsflaggor

## Säkerhetsfunktioner (Steg 5)
- **FLAG_SECURE** – blockerar skärmdumpar och gör att app-switchern visar blank/svart
- **Back-knapp = Panic** – när vault är öppen tar bakåt-knappen dig omedelbart tillbaka till miniräknaren
- **allowBackup=false** – ingen backup av appdata
- Röd "Lås igen (Panic)"-knapp i vaulten

## PIN-koder
- **6969** + biometri → Riktiga vaulten
- **1234** → Decoy-vault (oskyldig)

## Nästa steg
Steg 6: Navigation & grundläggande Vault-skal (riktiga skärmar istället för placeholders)
