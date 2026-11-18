package it.pingpro.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TouchApp
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
import it.pingpro.app.ui.components.Pill
import it.pingpro.app.ui.components.Sparkline
import it.pingpro.app.ui.theme.Mono
import it.pingpro.app.ui.theme.Palette

private data class Example(val title: String, val verdict: String, val color: Color, val values: List<Double?>, val text: String)

private val examples = listOf(
    Example("Scheda collegata via cavo", "NORMALE", Palette.Green,
        listOf(0.6, 0.7, 0.5, 0.6, 0.8, 0.6, 0.5, 0.7, 0.6, 0.6, 0.7, 0.5, 0.6, 0.6, 0.7, 0.6, 0.5, 0.6),
        "Tempi bassissimi (sotto 1–2 ms), tutti simili tra loro, 0% persi. Situazione ideale."),
    Example("Scheda raggiunta tramite Wi-Fi", "NORMALE", Palette.Green,
        listOf(3.0, 4.2, 2.8, 6.1, 3.5, 3.1, 8.0, 3.3, 4.0, 2.9, 5.2, 3.4, 3.0, 4.8, 3.2, 3.6, 2.7, 4.1),
        "Tempi tra 2 e 15 ms con piccole oscillazioni: è il comportamento tipico del Wi-Fi del telefono. 0% persi."),
    Example("Segnale Wi-Fi debole o disturbato", "ANOMALO", Palette.Amber,
        listOf(5.0, 38.0, 12.0, null, 80.0, 9.0, 140.0, 22.0, 7.0, null, 60.0, 15.0, 95.0, 11.0, 30.0, 8.0, 120.0, 14.0),
        "Tempi molto irregolari (jitter alto), picchi oltre 100 ms e qualche pacchetto perso. Avvicinarsi all'access point o verificare interferenze."),
    Example("Cavo scollegato / interruzione", "ANOMALO", Palette.Red,
        listOf(1.0, 1.1, 0.9, 1.0, null, null, null, null, null, null, 1.2, 1.0, 0.9, 1.1, 1.0, 0.9, 1.0, 1.1),
        "La scheda risponde, poi una serie di pacchetti persi consecutivi (zona rossa), poi torna a rispondere. L'app segnala \"Interruzione\" e \"Connessione ripristinata\" con la durata."),
    Example("Scheda spenta o IP sbagliato", "GUASTO", Palette.Red,
        listOf(null, null, null, null, null, null, null, null, null, null, null, null),
        "Nessuna risposta: 100% persi (\"Richiesta scaduta.\"). Verificare alimentazione, cavo, indirizzo IP e che telefono e scheda siano sulla stessa rete."),
    Example("Scheda sovraccarica", "DA VERIFICARE", Palette.Amber,
        listOf(1.0, 1.2, 1.1, 45.0, 1.0, 1.3, 1.1, 1.0, 60.0, 1.2, 1.1, 1.0, 1.2, 52.0, 1.1, 1.0, 1.2, 1.1),
        "Tempi normalmente bassi ma con picchi isolati regolari: la scheda a volte è troppo impegnata per rispondere subito. Nessuna perdita."),
)

@Composable
fun GuideScreen(onStartTutorial: () -> Unit, onTryNow: () -> Unit) {
    var help by remember { mutableStateOf<HelpTopic?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 20.dp, start = 6.dp, bottom = 4.dp)) {
                Text("Guida", style = MaterialTheme.typography.headlineLarge)
                Text("Tutto quello che serve sapere, in parole semplici", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1B3A73), Color(0xFF0F1E3D))))
                    .clickable(onClick = onStartTutorial).padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tutorial introduttivo", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("5 schermate per imparare a usare l'app in un minuto", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                    }
                    Icon(Icons.Outlined.PlayCircle, null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }
        }

        item {
            GuideSection(Icons.Outlined.TouchApp, "Come eseguire un PING, passo dopo passo", Palette.Accent, initiallyOpen = true) {
                Step(1, "Collega il telefono alla rete della scheda", "Apri le impostazioni Wi-Fi del Samsung e collegati alla stessa rete a cui è collegata la scheda (anche se quella rete non ha Internet).")
                Step(2, "Scrivi l'indirizzo IP", "Nella schermata Test tocca il campo indirizzo e digita l'IP, ad esempio 192.168.1.100. Se lo usi spesso, tocca l'icona segnalibro per salvarlo con un nome.")
                Step(3, "Scegli la modalità", "Singolo = 4 richieste e stop (come \"ping 192.168.1.100\"). Continuo = va avanti finché non premi Interrompi (come \"ping -t 192.168.1.100\").")
                Step(4, "Premi AVVIA TEST", "Ogni secondo parte una richiesta. Lo stato in alto diventa verde se la scheda risponde, rosso se non risponde.")
                Step(5, "Leggi il risultato", "Guarda Latenza media e Perdita. 0% persi e pochi millisecondi = comunicazione perfetta.")
                Step(6, "Interrompi o salva", "Premi INTERROMPI quando vuoi. Il test viene salvato automaticamente nello Storico, da dove puoi copiarlo, esportarlo o ripeterlo.")
                Spacer(Modifier.height(4.dp))
                Text("Prova ora →", style = MaterialTheme.typography.labelLarge, color = Palette.AccentSoft,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onTryNow).padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }

        item {
            GuideSection(Icons.Outlined.School, "Concetti base", Palette.Teal) {
                Para("Cos'è il PING", "È come chiedere alla scheda \"ci sei?\" e aspettare che risponda \"sì\". Il telefono invia un piccolo pacchetto (richiesta ICMP Echo) e la scheda lo rimanda indietro (risposta Echo Reply). Se la risposta arriva, la comunicazione funziona.")
                Para("Cosa sono i millisecondi (ms)", "1 millisecondo è un millesimo di secondo. 1000 ms = 1 secondo. Se leggi \"5 ms\", la risposta è arrivata in 5 millesimi di secondo: velocissimo.")
                Para("Cos'è la latenza", "È il tempo di andata e ritorno del pacchetto. Su una rete locale: sotto 1–2 ms via cavo, 2–20 ms via Wi-Fi. Oltre 100 ms su rete locale è anomalo.")
                Para("Cosa sono i pacchetti persi", "Richieste a cui la scheda non ha risposto entro 4 secondi. Nessuna perdita è l'obiettivo. Perdite occasionali indicano disturbi; perdite consecutive indicano un'interruzione.")
                Para("Cos'è il jitter", "Quanto variano i tempi da un pacchetto all'altro. Basso = rete stabile. Alto = rete irregolare (tipico di Wi-Fi debole).")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    listOf(HelpTopic.MILLISECONDS, HelpTopic.LOST, HelpTopic.TTL).forEach { t ->
                        Text(t.title, style = MaterialTheme.typography.labelMedium, color = Palette.AccentSoft,
                            modifier = Modifier.clip(CircleShape).background(Palette.Surface2).clickable { help = t }.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }

        item {
            GuideSection(Icons.Outlined.Insights, "Esempi: risultati normali e anomali", Palette.Violet) {
                examples.forEach { ex ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Surface2).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ex.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
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
            GuideSection(Icons.Outlined.Widgets, "Spiegazione di ogni funzione", Palette.Amber) {
                Para("Schermata Test", "Indirizzo, modalità, pulsante AVVIA/INTERROMPI, stato della connessione, latenza attuale, statistiche, grafico, mappa pacchetti, elenco risposte e cronologia eventi. Ogni riquadro ha un'icona ? con la spiegazione.")
                Para("Stato colorato", "Verde = risponde. Giallo = lenta o instabile. Rosso = non risponde (2 richieste consecutive senza risposta): il telefono vibra.")
                Para("Grafico", "Linea della latenza, linea tratteggiata della media, barre rosse per i pacchetti persi, zone rosse per le interruzioni. Puoi vedere gli ultimi 60 pacchetti o tutto il test.")
                Para("Mappa pacchetti", "Gli ultimi 40 pacchetti come quadratini colorati: individui i problemi in un secondo.")
                Para("Schede", "Salva gli indirizzi con un nome (es. \"Quadro linea 3\") e avvia il test con un tocco. Vedi l'esito dell'ultimo test di ogni scheda.")
                Para("Storico", "Ogni test viene salvato. Toccalo per i dettagli; tieni premuto (o usa l'icona ⇄) per selezionarne due e confrontarli.")
                Para("Esporta e copia", "\"Copia\" mette negli appunti un report nello stesso formato del Prompt di Windows. \"Testo\" e \"CSV\" permettono di inviare i risultati via e-mail, WhatsApp, Drive, ecc. Il CSV si apre con Excel.")
                Para("Ripeti", "Riavvia lo stesso test con gli stessi parametri, dalla dashboard o dallo storico.")
                Para("Notifica", "Durante il test una notifica mostra lo stato in tempo reale: il test continua anche con lo schermo spento. Dalla notifica puoi interromperlo.")
                Para("Impostazioni", "Parametri avanzati equivalenti alle opzioni di Windows (-n, -l, -w, -i), soglia di latenza elevata, vibrazione e suono di allarme.")
            }
        }

        item {
            GuideSection(Icons.AutoMirrored.Outlined.CompareArrows, "Differenze rispetto al Prompt di Windows", Palette.AccentSoft) {
                Text("Stesso principio, stessi pacchetti:", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "C:\\> ping 192.168.1.100\n\nEsecuzione di Ping 192.168.1.100 con 32 byte di dati:\nRisposta da 192.168.1.100: byte=32 durata=1ms TTL=64",
                    fontFamily = Mono, fontSize = 12.sp, color = Palette.TextDim,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0C0C0C)).padding(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                Bullet("Identici a Windows: richiesta ICMP Echo, 32 byte di dati con lo stesso contenuto, 4 richieste nel test singolo, timeout di 4 secondi, circa 1 secondo tra le richieste, TTL in uscita 128, calcolo di minimo/massimo/medio/persi.")
                Bullet("Più preciso: Windows arrotonda al millisecondo (\"durata<1ms\"); qui vedi anche i decimali (es. 0,42 ms).")
                Bullet("Più chiaro: Windows a volte conta come \"ricevuto\" anche il messaggio \"Host di destinazione non raggiungibile\"; qui è sempre conteggiato come perso, perché la scheda non ha risposto.")
                Bullet("Il tempo misurato include anche il Wi-Fi del telefono: rispetto a un PC collegato via cavo è normale vedere qualche millisecondo in più. L'app disattiva il risparmio energetico del Wi-Fi durante il test per ridurre questo effetto.")
                Bullet("Android non permette alle app i socket \"raw\" usati da Windows: l'app usa i socket ICMP dedicati al ping messi a disposizione dal sistema. Il pacchetto che viaggia in rete è lo stesso.")
                Bullet("Se il Wi-Fi della scheda non ha Internet, Android potrebbe preferire i dati mobili: l'app invia comunque le richieste sulla rete che contiene la scheda.")
            }
        }

        item {
            GuideSection(Icons.Outlined.Build, "La scheda non risponde? Cosa controllare", Palette.Red) {
                Bullet("Il telefono è collegato al Wi-Fi giusto? Nel riquadro stato, la voce di rete deve essere verde (stessa rete della scheda). Se è gialla, il telefono è su un'altra rete.")
                Bullet("L'indirizzo è corretto? Le prime tre parti dell'IP di telefono e scheda di solito coincidono (es. 192.168.1.x).")
                Bullet("La scheda è accesa e il cavo di rete è collegato (LED della porta accesi)?")
                Bullet("Alcune reti Wi-Fi \"ospiti\" isolano i dispositivi tra loro (isolamento client): in quel caso il ping non può passare.")
                Bullet("Alcuni dispositivi hanno il ping (ICMP) disabilitato dal firewall: in questo caso non rispondono anche se funzionano.")
                Bullet("Disattiva temporaneamente VPN o \"Passa a dati mobili\" se il test continua a non partire.")
            }
        }

        item {
            GuideSection(Icons.Outlined.Lightbulb, "Valori di riferimento", Palette.Green) {
                RefRow("Latenza via cavo", "< 2 ms", Palette.Green)
                RefRow("Latenza via Wi-Fi", "2 – 20 ms", Palette.Green)
                RefRow("Latenza da verificare", "20 – 100 ms", Palette.Amber)
                RefRow("Latenza anomala (rete locale)", "> 100 ms", Palette.Red)
                RefRow("Perdita accettabile", "0%", Palette.Green)
                RefRow("Perdita da indagare", "> 1–2%", Palette.Amber)
                RefRow("Scheda non raggiungibile", "100%", Palette.Red)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = Palette.TextFaint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tocca le icone ? nell'app per spiegazioni rapide", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
            }
        }
    }
    help?.let { HelpSheet(it) { help = null } }
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
        Box(Modifier.size(30.dp).clip(CircleShape).background(Palette.Accent), contentAlignment = Alignment.Center) {
            Text("$n", color = Color.White, fontWeight = FontWeight.Bold)
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
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

