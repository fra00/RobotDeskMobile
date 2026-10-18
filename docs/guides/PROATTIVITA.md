# Proattività — panoramica

Il robot può intervenire in due modi distinti (oltre ai promemoria che **tu** chiedi esplicitamente):

1. **Predittività** — impara dalle attività che racconti (passeggiata, tapis roulant, pranzo fuori…) e, se un giorno salti un’abitudine, può chiedertelo con tatto (**a voce**).
2. **Wellness** — una volta al giorno valuta pasti, lavoro, movimento, contatti e — se hai il **corpo ESP32** — anche ordine della scrivania/stanza. **Calcolo e comunicazione sono separati** (v2): il check salva uno **stato**; se qualcosa richiede attenzione arriva una **notifica**; a voce parla solo se **glielo chiedi**.

Sono **canali separati**: spegnere uno non spegne l’altro. Hanno regole e interruttori diversi.

## Cosa non fa

- Non usa le foto che chiedi con «cosa vedi» per giudicare il disordine.
- Non ti interrompe a metà dialogo per il wellness (niente TTS proattivo del check).
- In profilo lavoro / call / riunione / focus: può ancora **calcolare**, ma **non** manda la notifica wellness.

## Wellness v2 — calcolo vs comunicazione

| Fase | Cosa succede |
|------|----------------|
| **Calcolo** | Con gate leggeri (toggle on, LLM libero, ~15 min dopo il primo mic del giorno, max 1×/giorno). **Non** aspetta 5 min di silenzio dopo che hai parlato, né che il corpo ti “veda”. |
| **Salvataggio** | Risultato strutturato per dominio (ok / attenzione / critico + breve motivo) = **unica fonte di verità**. |
| **Notifica** | Solo se almeno un dominio è in attenzione/critico (max 1 notifica/giorno), tono soft. |
| **Voce** | Tu chiedi («come sto?», «wellness», «i pasti?») → risponde dallo **stato salvato**. |
| **UI** | Pagina riepilogo domini (stesso stato). |

### Ordine ambientale

Se il corpo è configurato e raggiungibile e il dominio ordine è attivo, durante il calcolo il robot può girare su **tre angoli** e scattare foto **senza parlarti**. In memoria l’ordine è **oggettivo**; notifica e risposte vocali restano soft.

Senza ESP32 il wellness resta su abitudini e log testuali.

### Runtime

v2 è attivo: check silenzioso → snapshot → eventuale notifica; voce solo se chiedi. Spec: [`PROACTIVE_ARCHITECTURE.md`](../PROACTIVE_ARCHITECTURE.md).

## Predittività — esempio

Se fai spesso tapis verso le 19:00 e un giorno non risulta nel log, il robot può chiedere con tatto — solo se l’abitudine ha **confidence sufficiente** e sei **presente** (parlato negli ultimi 10 minuti o il corpo ti trova). Ha un proprio tetto di interventi e cooldown, **indipendente** dal wellness.

I pattern si aggiornano dal **Log Day** con mining **incrementale**. Le scene di svago del robot (`[robot]`) non entrano nel tuo Log Day / abitudini.

## Impostazioni

**Impostazioni → Proattività**:

- **Micro-tick** — look-around occhi/corpo (niente LLM). Indipendente.
- **Predittività** / **Wellness** — interruttori dei due canali.
- **Sincronizza allenamenti (Health Connect)** — esercizi → Log Day come attività fatte.
- Minuti anchor wellness (v2 default più basso del vecchio 60′); soglie predittività.
- **Abitudini dedotte** — pattern Log Day.
- **Gestisci domini** — ambiti del check wellness.
- **(v2)** Riepilogo stato wellness — dalla snapshot salvata.

## Spec tecnica

Per implementatori e agenti AI: [`docs/PROACTIVE_ARCHITECTURE.md`](../PROACTIVE_ARCHITECTURE.md) (Channel 2 — Wellness v2).
