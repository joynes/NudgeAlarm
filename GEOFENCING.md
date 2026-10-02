# Platsbundna påminnelser

## Så ställer användaren in det

1. Redigera en påminnelse och välj eller skapa en plats, exempelvis Hemma.
2. Spara aktuell position när du är där. Börja med en radie på 200–250 meter; Android rekommenderar minst 100 meter för tillförlitlig geofencing.
3. Under Androids appbehörigheter, välj exakt plats och Tillåt alltid. Telefonens Plats måste vara på och Google Play-tjänster tillgängliga.
4. Spara påminnelsen och låt påminnelsetjänsten vara igång.

Befintliga platser och påminnelselänkar används automatiskt. I Inställningar → Saved places finns guiden, övervakningsstatus och en knapp till behörigheter. Lägg till och aktivera en annan position under samma plats för att byta hem på resa, utan att länka om påminnelserna.

## Batteri och cache

Appens schemakontroll fortsätter var femte minut, men begär ingen GPS-position. Android/Google Play-tjänster skickar geofence-händelser vid in- och utträde. En registrering per aktiv plats delas mellan alla påminnelser som länkar till den. Oförändrade schemakontroller återanvänder registreringen och samma lokala cache.

Ingen separat GPS-kontroll var 30:e minut behövs. Aktuell position hämtas endast när användaren uttryckligen sparar en plats med “Use current location”. Batteriförbrukningen är inte noll och har inte mätts på en fysisk telefon.

Okänd platsstatus blockerar notifikationer. Utträde blockerar dem igen. Ändrad position eller radie gör gammal cache ogiltig, och sena händelser från gamla registreringar ignoreras. Cachen återskapas efter tjänstestart, appuppdatering och omstart och följer inte med säkerhetskopior. Saknade behörigheter, avstängd Plats eller fel blockerar platsbundna påminnelser; appen återförsöker registreringen.

Android kan leverera händelser med några minuters fördröjning i bakgrunden. Det är därför ingen omedelbar eller exakt gränskontroll. Högst 100 aktiva platser stöds; överskrids gränsen blockeras platsbundna påminnelser och status visar problemet.

## Verifiering

Kör alla enhets- och instrumenterade tester enligt upload.md. GeofenceMonitorIntegrationTest registrerar riktiga geofences i Google Play-tjänster på emulatorn och verifierar att flera påminnelser delar en registrering och cache. In-/utträde simuleras i cachetester; faktisk fysisk gränspassage och batteriförbrukning är separata manuella tester.

Referens: https://developer.android.com/develop/sensors-and-location/location/geofencing
