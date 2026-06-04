# Sprute - Hendelsedrevet cronjobs

## Beskrivelse

Sender ut meldinger på rapiden om begivenheter knyttet til tid og dato, slik at apper som trenger utføre regelmessige oppgaver kan 
lese inn events fremfor å kjøre egne cronjobs.

## Motivasjon

Ved å basere regelmessige jobber på Kafka-meldinger i stedet for cron (eller annen tidsbasert logikk i hver app), oppnår man:

- **Ingen cron i hver app**  
  Man slipper å sette opp og vedlikeholde cron-jobber per tjeneste.

- **Ingen koordinering mellom pods**  
  Man trenger ikke håndtere at kun én instans skal utføre jobben i et distribuert miljø.

- **Mer robust kjøring**  
  Hvis en konsument er nede, vil Kafka sørge for at meldingene fortsatt blir lest når den kommer opp igjen.

- **Jobber kjøres på riktig datagrunnlag**  
  Tidshendelser ligger i samme strøm som forretningsdata.  
  Hvis en app er nede (f.eks. i to dager), kan den lese opp igjen alle hendelser – inkludert tidshendelser – og kjøre jobber basert på korrekt og oppdatert tilstand.

- **Bedre samsvar med hendelsesdrevet arkitektur**  
  Tid blir behandlet som en hendelse på lik linje med domenedata, som gir løsere kobling og mer fleksible løsninger.

## Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

### For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #team-sas-værsågod.
