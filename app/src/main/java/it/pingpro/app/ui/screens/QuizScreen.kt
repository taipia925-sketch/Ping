package it.pingpro.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pingpro.app.ui.components.Sparkline
import it.pingpro.app.ui.theme.Mono
import it.pingpro.app.ui.theme.Palette

/** Una domanda del quiz: un caso concreto, le risposte possibili e la spiegazione. */
data class QuizQuestion(
    val question: String,
    val visual: QuizVisual,
    val options: List<String>,
    val correct: Int,
    val explanation: String,
)

sealed interface QuizVisual {
    data class Prompt(val text: String) : QuizVisual
    data class Chart(val values: List<Double?>, val caption: String) : QuizVisual
    data class Facts(val rows: List<Pair<String, String>>) : QuizVisual
}

val quizQuestions = listOf(
    QuizQuestion(
        "Hai eseguito un test singolo verso una scheda. Come lo giudichi?",
        QuizVisual.Facts(listOf("Inviati" to "4", "Ricevuti" to "4", "Persi" to "0 (0%)", "Media" to "3,1 ms", "Massima" to "4,8 ms")),
        listOf("La scheda comunica correttamente", "La rete è lenta", "Il test è troppo breve per dire qualcosa"),
        0,
        "Tutte e 4 le richieste hanno avuto risposta, in pochi millisecondi. Per un controllo di comunicazione è il risultato ideale. " +
            "Se vuoi sorvegliare la scheda nel tempo (es. problemi che capitano ogni tanto) usa il test continuo.",
    ),
    QuizQuestion(
        "Il risultato è questo. Cosa significa?",
        QuizVisual.Prompt("Richiesta scaduta.\nRichiesta scaduta.\nRichiesta scaduta.\nRichiesta scaduta.\n\nPacchetti: Trasmessi = 4, Ricevuti = 0,\nPersi = 4 (100% persi)"),
        listOf("La scheda è sicuramente guasta", "Nessuna risposta: bisogna capire perché (indirizzo, rete, cavo, alimentazione)", "Il telefono non ha campo"),
        1,
        "100% persi vuol dire solo che non è arrivata nessuna risposta. Le cause possibili sono tante: indirizzo IP sbagliato, telefono su un'altra rete, " +
            "scheda spenta o scollegata, oppure scheda che non risponde al ping per configurazione. Il ping da solo non basta a dire che la scheda è guasta: " +
            "usa la Diagnosi guidata per restringere le cause.",
    ),
    QuizQuestion(
        "Test continuo di 5 minuti via Wi-Fi, 0% persi. Il grafico mostra tempi intorno a 3 ms con qualche picco isolato oltre 100 ms. È un problema?",
        QuizVisual.Chart(listOf(3.0, 3.2, 2.8, 3.1, 3.4, 140.0, 3.0, 2.9, 3.3, 3.1, 3.0, 2.8, 3.2, 3.6, 110.0, 3.1, 2.9, 3.0, 3.2, 3.1), "0% persi · media 3,5 ms · massima 140 ms"),
        listOf("Sì, la scheda si sta guastando", "Di solito no: picchi rari e senza perdite sono tipici del Wi-Fi del telefono", "Sì, il cavo della scheda è difettoso"),
        1,
        "Il Wi-Fi di un telefono ogni tanto rallenta per qualche istante (risparmio energetico, ricerca di altre reti, disturbi). Picchi isolati senza " +
            "pacchetti persi di solito non indicano un guasto. Diventa sospetto se i picchi sono frequenti, se ci sono perdite, o se capitano anche " +
            "con il telefono vicino all'access point. Un buon confronto: fai lo stesso test verso il gateway; se anche lì vedi picchi, la causa è il Wi-Fi.",
    ),
    QuizQuestion(
        "Durante un test continuo vedi questo andamento. Cosa è successo?",
        QuizVisual.Chart(listOf(2.0, 2.2, 1.9, 2.1, 2.0, null, null, null, null, null, null, 2.3, 2.0, 2.1, 1.9, 2.2), "6 pacchetti persi consecutivi, poi la scheda risponde di nuovo"),
        listOf("Una breve interruzione della comunicazione, poi ripristinata", "La scheda è lenta", "Nulla di importante"),
        0,
        "Una serie di pacchetti persi di fila è un'interruzione: per qualche decina di secondi la scheda non era raggiungibile (ogni richiesta persa " +
            "attende 4 secondi). Cause tipiche: falso contatto, riavvio della scheda, switch o access point che si riavvia. L'app lo registra negli " +
            "eventi con la durata, così puoi capire quando è successo.",
    ),
    QuizQuestion(
        "Il ping del gateway funziona, ma il ping della scheda (stessa rete del telefono) non ha risposte. Dove conviene cercare il problema?",
        QuizVisual.Facts(listOf("Gateway 192.168.1.1" to "4/4 risposte", "Scheda 192.168.1.100" to "0/4 risposte", "Telefono" to "192.168.1.23 /24")),
        listOf("Nel Wi-Fi del telefono", "Dal lato della scheda: alimentazione, cavo, indirizzo, configurazione", "Nel gateway"),
        1,
        "Il gateway risponde, quindi il telefono è collegato correttamente alla rete. La scheda è nella stessa rete, quindi il gateway non c'entra " +
            "(il traffico verso la scheda va diretto). Restano: scheda spenta o bloccata, cavo o porta dello switch, indirizzo IP diverso da quello " +
            "che pensi, oppure scheda che non risponde al ping per configurazione.",
    ),
    QuizQuestion(
        "Il telefono ha indirizzo 192.168.1.23 con subnet mask 255.255.255.0. Vuoi pingare una scheda con indirizzo 192.168.2.50. Cosa noti?",
        QuizVisual.Facts(listOf("IPv4 telefono" to "192.168.1.23", "Subnet mask" to "255.255.255.0", "Scheda" to "192.168.2.50")),
        listOf("È tutto normale", "La scheda è in un'altra rete: verifica l'indirizzo o il Wi-Fi a cui sei collegato", "La subnet mask è sbagliata"),
        1,
        "Con la subnet mask 255.255.255.0 la rete del telefono va da 192.168.1.1 a 192.168.1.254. 192.168.2.50 è fuori: il telefono può raggiungerla " +
            "solo passando dal gateway, e solo se la rete è configurata per farlo. Molto spesso è un indirizzo scritto male o il telefono collegato al Wi-Fi sbagliato.",
    ),
    QuizQuestion(
        "In Windows leggi «durata<1ms», nell'app «0,42 ms». Quale dei due è giusto?",
        QuizVisual.Prompt("Windows:  Risposta da 192.168.1.100: byte=32 durata<1ms TTL=64\nApp:      #1  Risposta da 192.168.1.100 … 0,42 ms"),
        listOf("Entrambi: dicono la stessa cosa con precisione diversa", "Solo Windows", "Solo l'app"),
        0,
        "Windows mostra i tempi in millisecondi interi e scrive «<1ms» per tutto ciò che è sotto il millisecondo. L'app misura con un orologio più " +
            "preciso e mostra i decimali. Ricorda però che i due tempi non sono mai identici: da telefono si passa dal Wi-Fi, quindi di solito " +
            "il telefono misura qualche millisecondo in più di un PC collegato con il cavo.",
    ),
    QuizQuestion(
        "Cosa indica il «Gateway predefinito» che vedi in ipconfig (scheda Rete)?",
        QuizVisual.Facts(listOf("Indirizzo IPv4" to "192.168.1.23", "Subnet mask" to "255.255.255.0", "Gateway predefinito" to "192.168.1.1")),
        listOf("Il dispositivo (di solito il router) a cui il telefono manda il traffico diretto fuori dalla propria rete", "L'indirizzo della scheda", "Il server che misura il ping"),
        0,
        "Il gateway è l'«uscita» della rete locale: il telefono gli affida i pacchetti destinati ad altre reti (es. Internet o un altro reparto). " +
            "Verso le schede della stessa rete invece comunica direttamente. Pingare il gateway è un ottimo primo controllo: se risponde, " +
            "il collegamento del telefono alla rete funziona.",
    ),
)

@Composable
fun QuizScreen(onBack: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackHeader("Mettiti alla prova", if (finished) null else "Domanda ${index + 1} di ${quizQuestions.size}", onBack)
        if (!finished) {
            val progress by animateFloatAsState((index + if (chosen != null) 1f else 0f) / quizQuestions.size, label = "pr")
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Palette.Surface3)) {
                Box(Modifier.fillMaxWidth(progress).height(6.dp).clip(CircleShape).background(Palette.Accent))
            }
        }
        AnimatedContent(if (finished) -1 else index, label = "q", modifier = Modifier.weight(1f)) { i ->
            if (i < 0) {
                QuizResult(score) { index = 0; chosen = null; score = 0; finished = false }
            } else {
                val q = quizQuestions[i]
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(q.question, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(14.dp))
                    QuizVisualView(q.visual)
                    Spacer(Modifier.height(16.dp))
                    q.options.forEachIndexed { k, opt ->
                        OptionRow(opt, k, chosen, q.correct) {
                            if (chosen == null) { chosen = k; if (k == q.correct) score++ }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    AnimatedVisibility(chosen != null) {
                        Column {
                            val right = chosen == q.correct
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                    .background((if (right) Palette.Green else Palette.Amber).copy(alpha = 0.12f)).padding(16.dp)
                            ) {
                                Text(if (right) "Esatto!" else "Non proprio.", style = MaterialTheme.typography.titleMedium,
                                    color = if (right) Palette.Green else Palette.Amber)
                                Spacer(Modifier.height(6.dp))
                                Text(q.explanation, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                            }
                            Spacer(Modifier.height(14.dp))
                            Box(
                                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Accent)
                                    .clickable {
                                        if (index == quizQuestions.lastIndex) finished = true else { index++; chosen = null }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(if (index == quizQuestions.lastIndex) "VEDI IL RISULTATO" else "DOMANDA SUCCESSIVA", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(text: String, k: Int, chosen: Int?, correct: Int, onClick: () -> Unit) {
    val state = when {
        chosen == null -> 0
        k == correct -> 1
        k == chosen -> 2
        else -> 3
    }
    val border by animateColorAsState(when (state) { 1 -> Palette.Green; 2 -> Palette.Red; else -> Palette.Outline }, label = "ob")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Surface)
            .border(1.5.dp, border, RoundedCornerShape(18.dp)).clickable(enabled = chosen == null, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Palette.Surface3), contentAlignment = Alignment.Center) {
            when (state) {
                1 -> Icon(Icons.Outlined.CheckCircle, null, tint = Palette.Green)
                2 -> Icon(Icons.Outlined.Cancel, null, tint = Palette.Red)
                else -> Text("ABC"[k].toString(), style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (state == 3) Palette.TextFaint else Palette.Text)
    }
}

@Composable
fun QuizVisualView(v: QuizVisual) {
    when (v) {
        is QuizVisual.Prompt -> Text(v.text, fontFamily = Mono, fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFFCCCCCC),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0C0C0C)).padding(14.dp))
        is QuizVisual.Chart -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Surface2).padding(14.dp)) {
            Sparkline(v.values, Palette.Accent, height = 64.dp)
            Spacer(Modifier.height(8.dp))
            Text(v.caption, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        is QuizVisual.Facts -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Surface2).padding(horizontal = 16.dp, vertical = 8.dp)) {
            v.rows.forEach { (k, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(k, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                }
            }
        }
    }
}

@Composable
private fun QuizResult(score: Int, onRestart: () -> Unit) {
    val total = quizQuestions.size
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(110.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF3D8BFF), Color(0xFF4FE3C1)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(54.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("$score su $total", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                score == total -> "Perfetto! Sai leggere i risultati del ping come un tecnico."
                score >= total - 2 -> "Ottimo risultato. Rileggi le spiegazioni delle domande sbagliate per completare il quadro."
                else -> "Buon inizio. Prova il Laboratorio per vedere dal vivo le situazioni del quiz, poi riprova."
            },
            style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Surface2).clickable(onClick = onRestart),
            contentAlignment = Alignment.Center) { Text("RIPROVA", fontWeight = FontWeight.Bold) }
    }
}
