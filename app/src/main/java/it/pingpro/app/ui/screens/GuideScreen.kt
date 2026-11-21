package it.pingpro.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pingpro.app.ui.components.AppCard
import it.pingpro.app.ui.components.HelpSheet
import it.pingpro.app.ui.components.HelpTopic
import it.pingpro.app.ui.components.IconBadge
import it.pingpro.app.ui.components.LatencyAnimation
import it.pingpro.app.ui.components.Pill
import it.pingpro.app.ui.components.Sparkline
import it.pingpro.app.ui.theme.Mono
import it.pingpro.app.ui.components.screenHeader
import it.pingpro.app.ui.theme.Palette

private data class Example(val title: String, val verdict: String, val color: Color, val values: List<Double?>, val text: String)

/** Esempi realistici misurati da un telefono collegato in Wi-Fi. */
private val examples = listOf(
    Example("Scheda che funziona, telefono vicino all'access point", "NORMALE", Palette.Green,
        listOf(3.0, 2.6, 4.1, 2.9, 3.4, 2.8, 5.2, 3.1, 2.7, 3.3, 3.0, 4.4, 2.9, 3.2, 2.8, 3.6, 3.0, 2.9),
        "Tempi di pochi millisecondi, simili tra loro, 0% persi. È il risultato tipico di una scheda raggiunta da un telefono in Wi-Fi."),
    Example("Scheda che funziona, con qualche picco isolato", "NORMALE", Palette.Green,
        listOf(3.1, 2.8, 3.3, 2.9, 96.0, 3.0, 3.2, 2.7, 3.4, 3.0, 2.9, 3.1, 3.3, 71.0, 2.8, 3.0, 3.2, 2.9),
        "Tempi bassi con rari picchi e nessuna perdita. Il Wi-Fi del telefono ogni tanto rallenta per un istante: se i picchi sono rari non è un guasto."),
    Example("Segnale Wi-Fi debole o disturbato", "DA VERIFICARE", Palette.Amber,
        listOf(5.0, 38.0, 12.0, null, 80.0, 9.0, 140.0, 22.0, 7.0, null, 60.0, 15.0, 95.0, 11.0, 30.0, 8.0, 120.0, 14.0),
        "Tempi molto irregolari (jitter alto), picchi frequenti e qualche pacchetto perso. Avvicinati all'access point e ripeti: se migliora, la causa era il Wi-Fi."),
    Example("Interruzione temporanea", "ANOMALO", Palette.Red,
        listOf(3.0, 3.1, 2.9, 3.0, null, null, null, null, null, null, 3.2, 3.0, 2.9, 3.1, 3.0, 2.9, 3.0, 3.1),
        "La scheda risponde, poi diversi pacchetti persi di fila (zona rossa), poi torna a rispondere. Tipico di falso contatto, riavvio della scheda o di un apparato di rete. L'app registra inizio e durata nella Cronologia eventi."),
    Example("Nessuna risposta", "NON RAGGIUNGIBILE", Palette.Red,
        listOf(null, null, null, null, null, null, null, null, null, null, null, null),
        "100% persi («Richiesta scaduta.»). Non significa per forza scheda guasta: può essere spenta o scollegata, l'indirizzo può essere sbagliato, il telefono può essere su un'altra rete, o la scheda può non rispondere al ping per configurazione. Usa la Diagnosi guidata."),
    Example("Rallentamenti regolari solo verso la scheda", "DA VERIFICARE", Palette.Amber,
        listOf(3.0, 3.2, 3.1, 45.0, 3.0, 3.3, 3.1, 3.0, 60.0, 3.2, 3.1, 3.0, 3.2, 52.0, 3.1, 3.0, 3.2, 3.1),
        "Picchi ripetuti senza perdite. Confronta con un test verso il gateway: se il gateway non mostra picchi, è più probabile che sia la scheda a rispondere in ritardo quando è impegnata; se li mostra anche il gateway, la causa è il Wi-Fi."),
)

@Composable
fun GuideScreen(
    onStartTutorial: () -> Unit,
    onTryNow: () -> Unit,
    onStartTour: () -> Unit = {},
    onOpenLab: () -> Unit = {},
    onOpenQuiz: () -> Unit = {},
    onOpenDiagnosis: () -> Unit = {},
) {
    var help by remember { mutableStateOf<HelpTopic?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().screenHeader()) {
                Text("Guida", style = MaterialTheme.typography.headlineLarge)
                Text("Impara facendo: prova, sbaglia, capisci", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
        }

        // ---- Strumenti interattivi ----
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                    .background(Palette.DangerBg)
                    .border(1.dp, Palette.Red.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    .clickable(onClick = onOpenDiagnosis).padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("La scheda non risponde?", style = MaterialTheme.typography.titleLarge, color = Palette.OnDangerBg)
                        Spacer(Modifier.height(4.dp))
                        Text("Diagnosi guidata passo passo, con test reali: ti dice dove cercare il problema.",
                            style = MaterialTheme.typography.bodyMedium, color = Palette.OnDangerBg.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Outlined.TravelExplore, null, tint = Palette.Red, modifier = Modifier.size(40.dp))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolCard(Icons.Outlined.PlayCircle, "Tutorial", "Le basi in 5 schermate", Palette.Accent, Modifier.weight(1f), onStartTutorial)
                    ToolCard(Icons.Outlined.TouchApp, "Tour dell'app", "Ti mostro ogni pulsante sullo schermo vero", Palette.Teal, Modifier.weight(1f), onStartTour)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolCard(Icons.Outlined.Science, "Laboratorio", "Scheda simulata: stacca il cavo e guarda cosa succede", Palette.Violet, Modifier.weight(1f), onOpenLab)
                    ToolCard(Icons.Outlined.Quiz, "Quiz", "8 casi reali da interpretare", Palette.Amber, Modifier.weight(1f), onOpenQuiz)
                }
            }
        }

        // ---- Contenuti ----
        item { SectionLabel("Capire il ping") }
        item {
            GuideSection(Icons.Outlined.School, "Come funziona il ping", Palette.Teal, initiallyOpen = true) {
                Para("L'idea", "Il ping è come chiedere alla scheda «ci sei?» e aspettare che risponda «sì». Il telefono invia un piccolo pacchetto (una richiesta ICMP Echo) e la scheda lo rimanda indietro (una risposta Echo Reply). Se la risposta arriva, telefono e scheda comunicano.")
                Spacer(Modifier.height(4.dp))
                LatencyAnimation()
                Spacer(Modifier.height(8.dp))
                Para("La latenza", "È il tempo di andata e ritorno: dall'invio della richiesta all'arrivo della risposta. Si misura in millisecondi (ms): 1 ms è un millesimo di secondo, 1000 ms fanno 1 secondo.")
                Para("I pacchetti persi", "Se la risposta non arriva entro 4 secondi (il «timeout», lo stesso di Windows) la richiesta è considerata persa: in Windows compare «Richiesta scaduta.». Per questo, quando la scheda non risponde, le righe arrivano ogni 4 secondi invece che ogni secondo.")
                Para("Il jitter", "Quanto cambiano i tempi da una risposta all'altra. Basso = collegamento regolare. Alto = tempi irregolari, tipico di un Wi-Fi disturbato.")
                Para("Il primo pacchetto", "A volte la prima risposta è un po' più lenta delle altre: il telefono deve prima «trovare» la scheda sulla rete. È normale, succede anche con Windows.")
                TopicChips(listOf(HelpTopic.MILLISECONDS, HelpTopic.LOST, HelpTopic.JITTER)) { help = it }
            }
        }
        item {
            GuideSection(Icons.Outlined.Lan, "Gateway, subnet e ipconfig", Palette.Accent) {
                Para("Dove trovarli", "Nella scheda Rete (o con il pulsante «ipconfig» nella schermata Test) vedi gli stessi dati del comando ipconfig di Windows: indirizzo IPv4 del telefono, subnet mask e gateway predefinito, più DNS, server DHCP e segnale Wi-Fi.")
                NetDiagram()
                Para("Il gateway predefinito", "È il dispositivo, di solito il router, a cui il telefono consegna i pacchetti diretti fuori dalla propria rete locale (verso Internet o verso altre reti). Verso le schede della stessa rete, invece, il telefono comunica direttamente: quei pacchetti non passano dal gateway.")
                Para("Perché pingare il gateway", "È il primo controllo da fare. Se il gateway risponde, il collegamento del telefono alla rete funziona. Se poi una scheda della stessa rete non risponde, il problema va cercato dalla parte della scheda (alimentazione, cavo, indirizzo, configurazione). Se non risponde nemmeno il gateway, il problema è più probabilmente nel collegamento del telefono. Attenzione: alcuni gateway sono configurati per non rispondere al ping.")
                Para("La subnet mask", "Dice quanto è grande la rete locale. Con la più comune, 255.255.255.0, la rete comprende gli indirizzi che hanno le prime tre parti uguali: ad esempio da 192.168.1.1 a 192.168.1.254. L'app calcola l'intervallo per te e lo mostra come «Indirizzi della rete».")
                Para("Cerca dispositivi", "Nella scheda Rete trovi «Cerca dispositivi»: controlla una volta tutti gli indirizzi della rete del telefono (da .1 a .254) e mostra solo quelli che rispondono, con il nome quando la rete lo fornisce. È il modo giusto per scoprire quali apparecchi ci sono; poi li aggiungi a «Più schede» per sorvegliarli. Ricorda: chi non risponde al ping non compare, anche se è collegato.")
                Para("Stessa rete o no?", "Se l'indirizzo della scheda è dentro l'intervallo della rete del telefono, il telefono la raggiunge direttamente. Se è fuori, serve il gateway e una rete configurata per far passare il traffico: spesso un indirizzo «fuori rete» è semplicemente scritto male. La Diagnosi guidata fa questo controllo in automatico.")
                TopicChips(listOf(HelpTopic.GATEWAY, HelpTopic.SUBNET, HelpTopic.DHCP)) { help = it }
            }
        }
        item {
            GuideSection(Icons.Outlined.Insights, "Esempi: risultati normali e anomali", Palette.Violet) {
                Text("Valori tipici misurati da un telefono in Wi-Fi. Per vederli dal vivo apri il Laboratorio.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                examples.forEach { ex ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Surface2).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ex.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Pill(ex.verdict, ex.color)
                        }
                        Spacer(Modifier.height(10.dp))
                        Sparkline(ex.values, ex.color, height = 40.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(ex.text, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                    }
                }
            }
        }
        item {
            GuideSection(Icons.Outlined.Lightbulb, "Valori di riferimento", Palette.Green) {
                Text("Indicazioni orientative per un telefono collegato in Wi-Fi a una rete locale. Ogni impianto è diverso: il confronto più utile è con i test precedenti della stessa scheda (Storico › Confronta).",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(bottom = 6.dp))
                RefRow("Latenza tipica", "1 – 20 ms", Palette.Green)
                RefRow("Picchi rari fino a ~100 ms", "normali in Wi-Fi", Palette.Green)
                RefRow("Latenza sempre sopra 50 ms", "da verificare", Palette.Amber)
                RefRow("Perdita", "0% è l'obiettivo", Palette.Green)
                RefRow("Pochi persi isolati in un test lungo", "tenere d'occhio", Palette.Amber)
                RefRow("Persi consecutivi o perdite frequenti", "da indagare", Palette.Red)
                RefRow("100% persi", "nessuna risposta", Palette.Red)
                Text("Per confronto: un PC collegato via cavo alla stessa rete misura di solito meno di 1 ms («durata<1ms» in Windows). Il telefono misura qualche millisecondo in più perché passa dal Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(top = 6.dp))
            }
        }

        item { SectionLabel("Usare l'app") }
        item {
            GuideSection(Icons.Outlined.TouchApp, "Eseguire un ping, passo dopo passo", Palette.Accent) {
                Step(1, "Collega il telefono alla rete della scheda", "Impostazioni › Connessioni › Wi-Fi, e scegli la rete a cui è collegata la scheda. Va bene anche se quella rete non ha Internet: l'app invia comunque i test sulla rete giusta.")
                Step(2, "Scegli l'indirizzo", "Tocca «Gateway …» per usare il gateway predefinito senza scriverlo, scegli una scheda salvata, oppure scrivi l'IP (es. 192.168.1.100).")
                Step(3, "Scegli la modalità", "Singolo: 4 richieste e stop, come «ping 192.168.1.100». Continuo: va avanti finché non premi Interrompi, come «ping -t 192.168.1.100».")
                Step(4, "Premi «Avvia test»", "Parte circa una richiesta al secondo. Lo stato diventa verde se la scheda risponde, giallo se ci sono rallentamenti o perdite, rosso se non risponde.")
                Step(5, "Leggi il risultato", "Guarda Perdita e Latenza media. 0% persi e pochi millisecondi: la comunicazione funziona.")
                Step(6, "Interrompi, salva, condividi", "Premi «Interrompi test» quando vuoi. Il test è salvato nello Storico: puoi copiarlo, esportarlo, ripeterlo o confrontarlo.")
                Spacer(Modifier.height(4.dp))
                Text("Prova ora →", style = MaterialTheme.typography.labelLarge, color = Palette.AccentSoft,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onTryNow).padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        item {
            GuideSection(Icons.Outlined.Widgets, "Tutte le funzioni", Palette.Amber) {
                Para("Test", "Indirizzo, modalità, Avvia/Interrompi e, durante il test: stato, latenza, statistiche, grafico, mappa pacchetti, elenco risposte e cronologia eventi. Il «Tour dell'app» li mostra uno per uno.")
                Para("Stato colorato", "Verde: risponde. Giallo «Lenta»: l'ultima risposta supera la soglia (100 ms, modificabile). Giallo «Instabile»: almeno un pacchetto perso negli ultimi 10. Rosso «Non risponde»: 2 o più richieste di fila senza risposta; il telefono vibra (e suona, se attivato).")
                Para("Grafico", "Linea della latenza, linea tratteggiata della media, barre rosse per i pacchetti persi e zone rosse per le interruzioni. Puoi vedere gli ultimi 60 pacchetti o tutto il test.")
                Para("Mappa pacchetti", "Gli ultimi 40 pacchetti come quadratini colorati: verde/azzurro risposte veloci, giallo/arancio lente, rosso perse.")
                Para("Più schede insieme", "In alto nella schermata Test scegli «Più schede». Componi l'elenco con le schede salvate, con un intervallo (es. da 192.168.1.100 a 192.168.1.120) o incollando gli indirizzi, e salvalo come gruppo con il nome dell'apparato. Ogni scheda ha il proprio ping indipendente, come più finestre del Prompt aperte insieme. I contatori in alto dicono quante rispondono, quante sono instabili e quante non rispondono; le schede con problemi compaiono per prime. Tocca una scheda per i dettagli. Nello Storico il test appare come un unico blocco che si apre sulle singole schede.")
                Para("Rete (ipconfig)", "Configurazione IP del telefono con gateway in evidenza e pulsante «Ping gateway». La «Vista Prompt» la mostra come nella finestra di Windows e permette di copiarla.")
                Para("Schede", "Indirizzi salvati con un nome (es. «Quadro linea 3») e note. Il test parte con un tocco e vedi l'esito dell'ultimo controllo.")
                Para("Storico e confronto", "Ogni test è salvato automaticamente. Tocca un test per i dettagli; tieni premuto (o usa ⇄) per sceglierne due e confrontarli.")
                Para("Esporta e copia", "«Copia» mette negli appunti un report nello stesso formato del Prompt di Windows. «Testo» e «CSV» lo inviano via e-mail, chat o Drive; il CSV si apre con Excel.")
                Para("In background", "Durante il test una notifica mostra lo stato: il test continua anche a schermo spento e si può interrompere dalla notifica.")
                Para("Impostazioni", "Parametri equivalenti alle opzioni di Windows (-n numero richieste, -l dimensione, -w timeout, -i TTL), con i valori di Windows già impostati; soglia di latenza, vibrazione, suono, schermo acceso.")
            }
        }
        item {
            GuideSection(Icons.AutoMirrored.Outlined.CompareArrows, "Differenze rispetto al Prompt di Windows", Palette.AccentSoft) {
                Text(
                    "C:\\> ping 192.168.1.100\n\nEsecuzione di Ping 192.168.1.100 con 32 byte di dati:\nRisposta da 192.168.1.100: byte=32 durata=3ms TTL=64",
                    fontFamily = Mono, fontSize = 12.sp, color = Palette.TextDim,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color(0xFF0C0C0C)).padding(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                Bullet("Uguale: stesso tipo di pacchetto (ICMP Echo), 32 byte di dati con lo stesso contenuto, 4 richieste nel test singolo, attesa massima di 4 secondi per ogni risposta, TTL in uscita 128, stesso modo di calcolare minimo, massimo, medio e persi.")
                Bullet("Più preciso: Windows mostra millisecondi interi («durata<1ms» sotto il millisecondo); l'app mostra anche i decimali (es. 0,42 ms). Il report esportato riporta entrambe le forme.")
                Bullet("Tempi un po' più alti: dal telefono il pacchetto passa dal Wi-Fi, quindi è normale misurare qualche millisecondo in più di un PC collegato via cavo. Durante il test l'app disattiva il risparmio energetico del Wi-Fi per misure più stabili.")
                Bullet("«Host di destinazione non raggiungibile»: in quel caso Windows può contare il pacchetto come ricevuto (la risposta però arriva dal PC o da un router, non dalla scheda). L'app lo conta sempre come perso.")
                Bullet("Funzionamento interno: Android non permette alle app di costruire pacchetti «grezzi» come fa Windows; l'app usa il canale ICMP che Android mette a disposizione proprio per il ping. In rete viaggia lo stesso pacchetto.")
                Bullet("Rete giusta: se il Wi-Fi delle schede non ha Internet, Android potrebbe preferire i dati mobili; per gli indirizzi della rete locale l'app invia comunque i test sul Wi-Fi/Ethernet.")
            }
        }
        item {
            GuideSection(Icons.Outlined.Build, "La scheda non risponde: cosa controllare", Palette.Red) {
                Bullet("Usa la Diagnosi guidata: fa i controlli in ordine e ti dice dove guardare.")
                Bullet("Il telefono è sul Wi-Fi giusto? Nel riquadro di stato la rete è verde se la scheda è nella stessa rete del telefono, gialla se non lo è.")
                Bullet("L'indirizzo è corretto? Confrontalo con l'intervallo «Indirizzi della rete» nella scheda Rete.")
                Bullet("La scheda è accesa e i LED della porta di rete sono attivi? Il cavo e la porta dello switch sono a posto?")
                Bullet("Le reti Wi-Fi «ospiti» spesso impediscono di comunicare con gli altri dispositivi: collegati alla rete aziendale/tecnica.")
                Bullet("Alcuni apparecchi sono configurati per non rispondere al ping: in quel caso il ping fallisce anche se l'apparecchio funziona.")
                Bullet("Se hai una VPN attiva sul telefono, disattivala temporaneamente.")
            }
        }
        item {
            GuideSection(Icons.AutoMirrored.Outlined.MenuBook, "Glossario", Palette.TextDim) {
                Text("Tocca una parola per la spiegazione.", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                Spacer(Modifier.height(8.dp))
                TopicChips(HelpTopic.entries.sortedBy { it.title }) { help = it }
            }
        }
    }
    help?.let { HelpSheet(it) { help = null } }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = Palette.TextFaint, modifier = Modifier.padding(start = 6.dp, top = 10.dp))
}

@Composable
private fun ToolCard(icon: ImageVector, title: String, text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(150.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Surface)
            .clickable(onClick = onClick).padding(16.dp)
    ) {
        IconBadge(icon, color, size = 42.dp)
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 2)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicChips(topics: List<HelpTopic>, onClick: (HelpTopic) -> Unit) {
    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        topics.forEach { t -> TopicChip(t) { onClick(t) } }
    }
}

@Composable
private fun TopicChip(t: HelpTopic, onClick: () -> Unit) {
    Text(t.title, style = MaterialTheme.typography.labelMedium, color = Palette.AccentSoft,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Palette.Surface2).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp))
}

/** Schema semplice: scheda e telefono nella stessa rete, il gateway porta verso le altre reti. */
@Composable
private fun NetDiagram() {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Surface2).padding(14.dp)) {
        Text("RETE LOCALE  192.168.1.1 – 192.168.1.254", style = MaterialTheme.typography.labelSmall, color = Palette.Teal)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            DiagramNode("Telefono", "192.168.1.23")
            Text("⇄ diretto ⇄", style = MaterialTheme.typography.bodySmall, color = Palette.Green, modifier = Modifier.align(Alignment.CenterVertically))
            DiagramNode("Scheda", "192.168.1.100")
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DiagramNode("Gateway", "192.168.1.1")
            Text("  →  altre reti / Internet", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
    }
}

@Composable
private fun DiagramNode(name: String, ip: String) {
    Column(Modifier.clip(RoundedCornerShape(4.dp)).background(Palette.Surface3).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(name, style = MaterialTheme.typography.labelMedium)
        Text(ip, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = Palette.TextDim)
    }
}

@Composable
private fun GuideSection(icon: ImageVector, title: String, color: Color, initiallyOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "rot")
    AppCard(padding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color, size = 42.dp)
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, null, tint = Palette.TextDim, modifier = Modifier.rotate(rot))
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) { content() }
        }
    }
}

@Composable
private fun Step(n: Int, title: String, text: String) {
    Row(Modifier.padding(vertical = 8.dp)) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Accent), contentAlignment = Alignment.Center) {
            Text("$n", color = Palette.OnAccent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        }
    }
}

@Composable
private fun Para(title: String, text: String) {
    Column(Modifier.padding(vertical = 7.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
        Spacer(Modifier.height(2.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(Palette.AccentSoft))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
    }
}

@Composable
private fun RefRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}
