package it.pingpro.app.ui

import it.pingpro.app.ui.components.TourStep

/** Passi del tour della schermata Test: ogni passo evidenzia l'elemento reale. */
val testScreenTour = listOf(
    TourStep(null, "Tour dell'app", "Ti mostro a cosa serve ogni elemento della schermata principale. Tocca «Avanti» per proseguire, «Chiudi» per uscire in qualsiasi momento."),
    TourStep("multi", "Una scheda o più schede", "Con «Più schede» controlli molte schede contemporaneamente, ad esempio tutte quelle di un apparato: ognuna ha il proprio ping, come più finestre del Prompt aperte insieme."),
    TourStep("address", "Indirizzo da controllare", "Qui va l'indirizzo IP della scheda, ad esempio 192.168.1.100. È quello che in Windows scriveresti dopo la parola «ping»."),
    TourStep("chips", "Scelte rapide", "Il pulsante verde «Gateway» inserisce il gateway predefinito senza doverlo scrivere. Seguono le schede salvate e gli indirizzi usati di recente: un tocco e l'indirizzo è pronto."),
    TourStep("ipconfig", "ipconfig", "Apre la scheda Rete: indirizzo del telefono, subnet mask e gateway predefinito, come il comando ipconfig di Windows."),
    TourStep("save", "Salva la scheda", "Dà un nome all'indirizzo (es. «Quadro linea 3»), così lo ritrovi nella sezione Schede e tra le scelte rapide."),
    TourStep("mode", "Singolo o continuo", "Singolo: 4 richieste e poi si ferma, come «ping». Continuo: va avanti finché non premi Interrompi, come «ping -t». Usa il continuo per sorvegliare una scheda nel tempo."),
    TourStep("command", "Comando equivalente", "Il comando che scriveresti nel Prompt di Windows per fare lo stesso test. È solo un riferimento: non devi scriverlo."),
    TourStep("start", "Avvia e interrompi", "Avvia il test. Mentre è in corso il pulsante diventa rosso: premilo per fermare. Qui sotto compariranno stato colorato, latenza, statistiche, grafico ed eventi."),
    TourStep("nav", "Le sezioni", "Test: questa schermata. Rete: ipconfig e gateway. Schede: indirizzi salvati. Storico: tutti i test, da rivedere, esportare e confrontare. Guida: spiegazioni, Laboratorio, Quiz e Diagnosi guidata."),
    TourStep(null, "Fatto!", "Ogni riquadro ha un'icona «?» con una spiegazione breve. Per vedere come appaiono i risultati senza collegarti a una scheda, apri il Laboratorio nella Guida."),
)
