# Proattività — panoramica

Il robot può intervenire in due modi distinti (oltre ai promemoria che **tu** chiedi esplicitamente):

1. **Predittività** — impara dalle attività che racconti (passeggiata, tapis roulant, pranzo fuori…) e, se un giorno salti un’abitudine, può chiedertelo con tatto.
2. **Wellness** — una volta al giorno (in condizioni precise) valuta come stai su pasti, lavoro, movimento, contatti e — se hai il **corpo ESP32** — anche ordine della scrivania/stanza.

## Cosa non fa

- Non usa le foto che chiedi con «cosa vedi» per giudicare il disordine.
- Non ti interrompe appena accendi il microfono.
- Non parla in modalità lavoro / call / riunione / focus.

## Wellness — quando può parlare

Circa **un’ora** (configurabile) dopo la **prima accensione del microfono** del giorno, se:

- il microfono è ancora attivo;
- non sei in modalità lavoro / call / riunione / focus silenzioso;
- dall’**ultimo turno vocale** sono passati almeno **~5 minuti** (buffer: evita il check subito dopo un dialogo; non è «mic acceso da 5 min»).

Se il check trova qualcosa da dire, parla solo se risulti ancora **presente**: interazione nelle ultime **~45 minuti** **oppure** volto in camera / corpo che ti localizza (finestra **W**, configurabile). Altrimenti resta in silenzio.

Al massimo **una frase** al giorno, solo se un ambito (es. pranzo saltato, scrivania molto disordinata) è chiaramente «carente».

## Ordine ambientale

Se il corpo robot è configurato e raggiungibile, durante il check wellness il robot può **girare e scattare foto** con un prompt dedicato solo all’ordine — senza dirti nulla in quel momento. Il risultato serve al wellness (e alla memoria interna), non è un canale a parte.

Senza corpo ESP32 il wellness resta su abitudini e log testuali, senza valutazione visiva dell’ordine.

## Predittività — esempio

Se fai spesso tapis verso le 19:00 e un giorno non risulta nel log, il robot può chiedere con tatto — solo se l’abitudine ha **confidence sufficiente** (più giorni ripetuti) e sei **presente** (parlato negli ultimi 10 minuti o il corpo ti trova).

I pattern si aggiornano dal **Log Day** con mining **incrementale** (all’apertura app, prima di cancellare episodi vecchi): stessa attività alla stessa ora in **giorni diversi** aumenta la confidence. Se dici che non fai più quell’abitudine, il pattern viene **eliminato**.

## Impostazioni

**Impostazioni → Proattività** — attiva/disattiva heartbeat, predittività e wellness; minuti anchor/idle/presenza; soglia confidenza. **Gestisci domini** abilita/disabilita gli ambiti del check wellness (predefiniti + personalizzati). I domini custom sono uguali agli altri: scrivi nome e descrizione; partono con le stesse regole di ingaggio del wellness (niente orario/evento separato). L’ordine, se attivo e col corpo ESP32, viene valutato in silenzio **prima** dello score. La stanza corrente non è un dominio: si aggiorna con i tool spaziali.

## Spec tecnica

Per implementatori e agenti AI: [`docs/PROACTIVE_ARCHITECTURE.md`](../PROACTIVE_ARCHITECTURE.md).
