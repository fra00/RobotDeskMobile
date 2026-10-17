# Proxy notifiche (guida)

> Spec tecnica: [`../NOTIFICATION_PROXY.md`](../NOTIFICATION_PROXY.md) — **implementato (v1)**.  
> Bridge capability (esercizi HC, rubrica, reminder duali): [`../PHONE_CAPABILITY_BRIDGE.md`](../PHONE_CAPABILITY_BRIDGE.md).

## A cosa serve

Il robot sta su un Android dedicato. Il tuo cellulare, in **Mode Bot → Proxy**, inoltra le notifiche (WhatsApp, Teams, …) sulla Wi‑Fi di casa. Cambia solo la **fonte**; sul robot valgono le stesse regole di sempre (mic off, notte, …).

Con lo stesso collegamento il robot può anche **leggere** dal cell (esercizi Health Connect, rubrica) e, se attivi l’opzione, **mandare i reminder** anche sul telefono.

Niente coda sul cell: se il robot non è raggiungibile, quell’invio si perde.

## Mode Bot (Impostazioni)

Voce di menu **Mode Bot** → dialog:

| Se scegli **Robot** | Se scegli **Proxy** |
|---------------------|---------------------|
| App completa | Proxy: notifiche + capability HTTP |
| Mostra QR per il pair, lista proxy, elimina pair | Scansiona QR, togli pair, whitelist app, aiuto batteria |
| Checkbox «Invia anche i reminder al telefono collegato» (default off) | — |

Cambiando modalità l’app **si riavvia una volta** per applicare il ruolo in modo pulito. La scelta resta salvata: se riapri l’app (anche dopo reboot del telefono) torna in quella modalità — ma dopo un reboot del cell **devi aprire tu** l’app (non parte da sola in background).

## Accoppiamento (QR)

1. Sul **robot** (Mode Bot → Robot): mostra il QR.  
2. Sul **proxy**: scansiona.  
3. Fatto.

Se cambia l’IP del robot → ri-scansiona.  
Puoi collegare **più telefoni** allo stesso robot e revocarli uno per uno.

## Whitelist

Solo sul **proxy**. Il robot riceve già filtrato.

## Regole importanti

- Robot **senza** proxy → notifiche locali come oggi.
- Robot **con** proxy → non legge le notifiche del tablet; solo quelle remote.
- Solo **Wi‑Fi** (niente invio/ascolto su rete mobile).
- Notifica fissa sul cell in proxy (“proxy attivo”).

## Capability bridge (oltre le notifiche)

Dopo il pair, sul **robot**:

- Sync esercizi Health Connect dal cell → Log Day (se HC e permessi sul cell).
- Resolve contatti / WhatsApp può usare la rubrica del cell se sul robot manca.
- Con checkbox dual reminder ON: allo scatto, notifica anche sul cell (niente seconda sveglia sul telefono).

## Cosa non fa (v1)

Coda sul proxy, cloud/VPN, TTS reminder sul cell, controllo dialer/WhatsApp/Spotify **del telefono** dal robot.

## Smoke test

1. Pair QR sulla stessa Wi‑Fi.  
2. WhatsApp in whitelist → robot reagisce.  
3. App fuori whitelist → niente.  
4. Mic off sul robot → drop sul robot.  
5. Mode Bot Proxy↔Robot → un riavvio, poi ruolo corretto.  
6. (Capability) esercizio sul cell → episodio Log Day sul robot.  
7. (Capability) dual reminder ON → notifica su robot e cell.
