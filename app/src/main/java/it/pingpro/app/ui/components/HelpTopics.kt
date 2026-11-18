package it.pingpro.app.ui.components

/** Spiegazioni brevi mostrate dalle icone "?" in tutta l'app. */
enum class HelpTopic(val title: String, val body: String) {
    ADDRESS(
        "Indirizzo IP della scheda",
        "È il \"numero di telefono\" della scheda elettronica sulla rete, ad esempio 192.168.1.100.\n\n" +
            "Lo trovi sull'etichetta della scheda, nel suo manuale o nel software di configurazione. " +
            "Puoi scrivere anche un nome di rete (es. scheda-01.local) se la rete lo supporta.\n\n" +
            "Il telefono deve essere collegato alla stessa rete Wi-Fi (o Ethernet) della scheda."
    ),
    MODE(
        "Test singolo o continuativo",
        "• Singolo: invia 4 richieste e si ferma, esattamente come il comando \"ping 192.168.1.100\" di Windows.\n\n" +
            "• Continuativo: invia una richiesta al secondo finché non premi INTERROMPI, come \"ping -t 192.168.1.100\".\n\n" +
            "Il continuativo è utile per sorvegliare una scheda nel tempo, ad esempio mentre si muove un cavo o si riavvia un apparato."
    ),
    STATUS(
        "Stato della connessione",
        "• ONLINE (verde): la scheda risponde regolarmente.\n" +
            "• LENTA (giallo): risponde, ma l'ultimo tempo supera la soglia impostata (100 ms di default).\n" +
            "• INSTABILE (giallo): almeno un pacchetto perso di recente.\n" +
            "• NON RISPONDE (rosso): 2 o più richieste consecutive senza risposta. Il telefono vibra per avvisarti.\n\n" +
            "Quando la scheda torna a rispondere viene registrato l'evento \"Connessione ripristinata\" con la durata dell'interruzione."
    ),
    LATENCY(
        "Latenza (tempo di risposta)",
        "È il tempo che passa tra l'invio della richiesta e l'arrivo della risposta della scheda: andata e ritorno.\n\n" +
            "Si misura in millisecondi (ms). Più è basso, meglio è.\n\n" +
            "Su una rete locale valori tra 1 e 20 ms sono normali. Tramite Wi-Fi è normale qualche variazione."
    ),
    MILLISECONDS(
        "Cosa sono i millisecondi",
        "Un millisecondo (ms) è un millesimo di secondo: 1000 ms = 1 secondo.\n\n" +
            "Esempio: 5 ms significa che la risposta è arrivata in 5 millesimi di secondo, un tempo velocissimo.\n\n" +
            "Windows arrotonda e scrive \"durata<1ms\" per valori sotto il millisecondo; questa app mostra anche i decimali (es. 0,42 ms)."
    ),
    AVG("Latenza media", "La media dei tempi di tutte le risposte ricevute. È il valore più rappresentativo: indica quanto risponde \"di solito\" la scheda. Corrisponde a \"Medio\" nelle statistiche di Windows."),
    MIN("Latenza minima", "Il tempo di risposta più veloce registrato durante il test. Corrisponde a \"Minimo\" di Windows."),
    MAX("Latenza massima", "Il tempo di risposta più lento registrato. Un massimo molto più alto della media indica rallentamenti occasionali (picchi). Corrisponde a \"Massimo\" di Windows."),
    JITTER(
        "Jitter (variazione)",
        "Indica quanto cambia il tempo di risposta da un pacchetto al successivo.\n\n" +
            "Jitter basso (pochi ms) = connessione stabile e regolare. Jitter alto = tempi irregolari, tipico di Wi-Fi disturbato o rete congestionata.\n\n" +
            "Windows non lo mostra: è un'informazione in più."
    ),
    SENT("Pacchetti inviati", "Quante richieste ICMP Echo il telefono ha inviato alla scheda. Corrisponde a \"Trasmessi\" di Windows."),
    RECEIVED("Pacchetti ricevuti", "Quante risposte sono tornate dalla scheda. In una rete sana sono uguali agli inviati."),
    LOST(
        "Pacchetti persi",
        "Richieste a cui la scheda non ha risposto entro il tempo massimo (4 secondi, come Windows). In Windows compaiono come \"Richiesta scaduta.\"\n\n" +
            "Possibili cause: scheda spenta o bloccata, cavo scollegato, indirizzo IP errato, Wi-Fi debole, firewall che blocca il ping."
    ),
    LOSS_PERCENT(
        "Percentuale di perdita",
        "Persi ÷ inviati × 100.\n\n• 0% = perfetto.\n• 1–2% su test lunghi = accettabile su Wi-Fi.\n• Oltre 5% = problema da indagare.\n• 100% = la scheda non risponde affatto."
    ),
    CHART(
        "Grafico della latenza",
        "Ogni punto è una risposta: più in alto = più lenta.\n\n" +
            "Le barre rosse verticali sono pacchetti persi; le zone rosse indicano interruzioni (più pacchetti persi di fila).\n\n" +
            "La linea tratteggiata è la media. Puoi scegliere di vedere gli ultimi 60 pacchetti o tutto il test."
    ),
    STRIP(
        "Mappa dei pacchetti",
        "Ogni quadratino è una richiesta, dalla più vecchia (sinistra) alla più recente (destra).\n\n" +
            "Verde = risposta veloce, giallo/arancio = risposta lenta, rosso = pacchetto perso. Ti permette di vedere a colpo d'occhio se ci sono stati problemi."
    ),
    EVENTS(
        "Cronologia eventi",
        "Registra automaticamente i momenti importanti: inizio e fine del test, pacchetti persi, interruzioni, ripristini e picchi di latenza, con l'orario esatto."
    ),
    TTL(
        "TTL",
        "Time To Live: un contatore presente in ogni pacchetto. Il valore nella risposta dipende dal sistema della scheda (spesso 64, 128 o 255) e diminuisce di 1 per ogni router attraversato. È lo stesso valore \"TTL=\" mostrato da Windows."
    ),
    NETWORK(
        "Rete utilizzata",
        "Indica da quale collegamento del telefono partono le richieste (Wi-Fi o Ethernet) e l'indirizzo IP del telefono su quella rete.\n\n" +
            "L'app sceglie automaticamente la rete che contiene l'indirizzo della scheda, anche se quel Wi-Fi non ha accesso a Internet, " +
            "così le richieste non vengono mai inviate tramite i dati mobili."
    ),
}
