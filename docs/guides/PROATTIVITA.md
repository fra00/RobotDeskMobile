# Proattività — panoramica

Il robot può intervenire in due modi distinti (oltre ai promemoria che **tu** chiedi esplicitamente):

1. **Predittività** — impara dalle attività che racconti (passeggiata, tapis roulant, pranzo fuori…) e, se un giorno salti un’abitudine, può chiedertelo con tatto.
2. **Wellness** — una volta al giorno (in condizioni precise) valuta come stai su pasti, lavoro, movimento, contatti e — se hai il **corpo ESP32** — anche ordine della scrivania/stanza.

Sono **canali separati**: spegnere uno non spegne l’altro. Hanno regole e interruttori diversi.

## Cosa non fa

- Non usa le foto che chiedi con «cosa vedi» per giudicare il disordine.
- Non ti interrompe a metà dialogo.
- Non parla in modalità lavoro / call / riunione / focus.

## Wellness — quando può partire

In sequenza:

1. **Anchor** — è passata almeno ~1 ora (configurabile) dalla **prima** accensione del microfono del giorno (così non parte appena accendi).
2. **Buffer dopo dialogo** — dall’ultimo turno vocale sono passati almeno ~5 minuti (evita di accavallarsi con una conversazione ancora “calda”).
3. **Presenza** — se c’è il corpo, prova a localizzarti; se non ti trova, serve un’interazione recente (finestra presenza, default ~15 min, comunque ≥ buffer). Senza corpo resta solo l’interazione recente.
4. **Sessione libera** — microfono attivo, non notte, non in thinking/speaking/ascolto attivo.
5. **Start** — al massimo un check al giorno; parla solo se qualcosa è chiaramente carente (altrimenti silenzio).

Se non vuoi più un ambito (es. pasti), disattivalo da **Gestisci domini** — non serve anti-ripetizione extra: parla al più una volta al giorno.

## Ordine ambientale

Se il corpo robot è configurato e raggiungibile, durante il check wellness il robot **deve** girare su **tre angoli** (sinistra / centro / destra) e scattare una foto per angolo — senza dirti nulla in quel momento. La valutazione in memoria è **oggettiva** (se è disordinato lo scrive chiaro); se poi ti parla, lo fa in modo **soft**, senza ordini né toni punitivi.

Senza corpo ESP32 il wellness resta su abitudini e log testuali, senza valutazione visiva dell’ordine.

## Predittività — esempio

Se fai spesso tapis verso le 19:00 e un giorno non risulta nel log, il robot può chiedere con tatto — solo se l’abitudine ha **confidence sufficiente** (più giorni ripetuti) e sei **presente** (parlato negli ultimi 10 minuti o il corpo ti trova). Ha un proprio tetto di interventi e cooldown, indipendente dal wellness.

I pattern si aggiornano dal **Log Day** con mining **incrementale** (all’apertura app, prima di cancellare episodi vecchi). Se dici che non fai più quell’abitudine, il pattern viene **eliminato**.

## Impostazioni

**Impostazioni → Proattività**:

- **Micro-tick** — look-around occhi/corpo in standby (niente LLM; mood loop). Indipendente da wellness/predittività.
- **Predittività** / **Wellness** — interruttori dei due canali.
- Minuti anchor / buffer dialogo / presenza; soglia `speak_confidence`.
- **Gestisci domini** — abilita/disabilita ambiti del check wellness (predefiniti + personalizzati).

## Spec tecnica

Per implementatori e agenti AI: [`docs/PROACTIVE_ARCHITECTURE.md`](../PROACTIVE_ARCHITECTURE.md).
