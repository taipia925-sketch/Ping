package it.pingpro.app.ui.components

/** Spiegazioni brevi mostrate dalle icone "?" in tutta l'app. */
enum class HelpTopic(val title: String, val body: String) {
    ADDRESS(
        "Indirizzo IP della scheda",
        "È il \"numero di telefono\" della scheda elettronica sulla rete, ad esempio 192.168.1.100.\n\n" +
            "Lo trovi sull'etichetta della scheda, nel suo manuale o nel software di configurazione. " +
            "Puoi scrivere anche un nome di rete (es. scheda01.azienda.it) se il DNS della rete lo conosce.\n\n" +
            "Il telefono deve essere collegato alla stessa rete Wi-Fi (o Ethernet) della scheda."
    ),
    MODE(
        "Test singolo o continuativo",
        "• Singolo: invia 4 richieste e si ferma, esattamente come il comando \"ping 192.168.1.100\" di Windows.\n\n" +
            "• Continuativo: invia circa una richiesta al secondo finché non premi INTERROMPI, come \"ping -t 192.168.1.100\".\n\n" +
            "Il continuativo è utile per sorvegliare una scheda nel tempo, ad esempio mentre si muove un cavo o si riavvia un apparato."
    ),
    STATUS(
        "Stato della connessione",
        "• ONLINE (verde): la scheda risponde regolarmente.\n" +
            "• LENTA (giallo): risponde, ma l'ultimo tempo supera la soglia impostata (100 ms di default).\n" +
            "• INSTABILE (giallo): almeno un pacchetto perso negli ultimi 10.\n" +
            "• NON RISPONDE (rosso): 2 o più richieste consecutive senza risposta. Il telefono vibra per avvisarti.\n\n" +
            "Quando la scheda torna a rispondere viene registrato l'evento \"Connessione ripristinata\" con la durata dell'interruzione."
    ),
    LATENCY(
        "Latenza (tempo di risposta)",
        "È il tempo che passa tra l'invio della richiesta e l'arrivo della risposta della scheda: andata e ritorno.\n\n" +
            "Si misura in millisecondi (ms). Più è basso, meglio è.\n\n" +
            "Dal telefono in Wi-Fi verso una scheda della rete locale sono tipici valori tra 1 e 20 ms, con qualche picco occasionale. Un PC collegato via cavo misura di solito meno di 1 ms."
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
            "Possibili cause: scheda spenta o bloccata, cavo scollegato, indirizzo IP errato, telefono su un'altra rete, Wi-Fi debole, oppure scheda configurata per non rispondere al ping."
    ),
    LOSS_PERCENT(
        "Percentuale di perdita",
        "Persi ÷ inviati × 100.\n\n• 0% = l'obiettivo su una rete locale.\n• Pochi pacchetti persi isolati in un test lungo via Wi-Fi: da tenere d'occhio, spesso dipendono dal Wi-Fi del telefono.\n• Perdite frequenti o pacchetti persi di fila: da indagare.\n• 100% = nessuna risposta dalla scheda."
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
            "Verde: la scheda è nella stessa rete del telefono. Giallo: è fuori dalla rete del telefono (controlla indirizzo e Wi-Fi).\n\n" +
            "Per gli indirizzi della rete locale l'app usa automaticamente il Wi-Fi/Ethernet, anche se quel Wi-Fi non ha accesso a Internet, " +
            "invece dei dati mobili."
    ),
    GATEWAY(
        "Gateway predefinito",
        "È il dispositivo (di solito il router) a cui il telefono consegna i pacchetti diretti fuori dalla propria rete locale. È lo stesso valore che leggi in \"Gateway predefinito\" con ipconfig su Windows.\n\n" +
            "Verso le schede della stessa rete il telefono comunica direttamente, senza passare dal gateway.\n\n" +
            "Pingare il gateway è un ottimo primo controllo: se risponde, il collegamento del telefono alla rete funziona. Se non risponde, il problema è più probabilmente nel collegamento del telefono (oppure il gateway è configurato per non rispondere al ping).\n\n" +
            "Tocca \"Ping gateway\" per provarlo subito, oppure \"Usa\" per inserirlo come indirizzo da testare."
    ),
    PHONE_IP(
        "Indirizzo IPv4 del telefono",
        "L'indirizzo che il telefono ha ricevuto su questa rete. Con la subnet mask più comune (255.255.255.0) le schede raggiungibili direttamente hanno le prime tre parti uguali (es. telefono 192.168.1.23 → schede 192.168.1.x)."
    ),
    SUBNET(
        "Subnet mask",
        "Indica quanto è grande la rete locale. Con 255.255.255.0 (detta anche /24) la rete comprende gli indirizzi con le prime tre parti uguali, da x.x.x.1 a x.x.x.254. Le schede con un indirizzo in questo intervallo sono nella stessa rete del telefono e si raggiungono direttamente. Con altre subnet mask l'intervallo cambia: l'app lo calcola e lo mostra in \"Indirizzi della rete\"."
    ),
    DNS(
        "Server DNS",
        "Il servizio che traduce i nomi (es. scheda01.azienda.it o www.sito.it) in indirizzi IP. Spesso coincide con il gateway."
    ),
    DHCP(
        "Server DHCP",
        "L'apparato che ha assegnato automaticamente l'indirizzo IP al telefono. Spesso è il router/gateway stesso."
    ),
    SIGNAL(
        "Segnale Wi-Fi",
        "Potenza del segnale ricevuto in dBm: più vicino a 0, migliore. Da 0 a -55 ottimo, fino a -67 buono, fino a -75 discreto, fino a -85 debole, sotto -85 molto debole. Con segnale debole aumentano tempi e pacchetti persi."
    ),
    MULTI(
        "Più schede insieme",
        "Controlla fino a 100 schede contemporaneamente, ad esempio tutte quelle di un apparato.\n\n" +
            "Ogni scheda ha il proprio ping indipendente, con la stessa cadenza di Windows (circa una richiesta al secondo): è come aprire più finestre \"ping\" o \"ping -t\" insieme.\n\n" +
            "Puoi comporre l'elenco con le schede salvate, con un intervallo di indirizzi (es. da 192.168.1.100 a 192.168.1.120) o incollando un elenco, e salvarlo come gruppo con il nome dell'apparato.\n\n" +
            "Le schede che non rispondono vengono mostrate per prime e in rosso; tocca una scheda per vederne tutti i dettagli."
    ),
}
