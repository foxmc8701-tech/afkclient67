package app.afkconsole

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

private val Bg = Color(0xFF0B0F14)
private val Card = Color(0xFF131A22)
private val Accent = Color(0xFF3DDC97)
private val Muted = Color(0xFF8A97A6)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Card, onPrimary = Bg)) {
                Surface(Modifier.fillMaxSize(), color = Bg) { App() }
            }
        }
    }

    fun ensureService() = startForegroundService(Intent(this, AfkService::class.java))
    fun maybeStopService() { if (!Hub.anyActive()) stopService(Intent(this, AfkService::class.java)) }
}

@Composable
fun App() {
    val ctx = androidx.compose.ui.platform.LocalContext.current as MainActivity
    var accounts by remember { mutableStateOf(Store.load(ctx)) }
    var open by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Account?>(null) }
    fun persist(l: List<Account>) { accounts = l; Store.save(ctx, l) }

    val sel = accounts.find { it.id == open }
    when {
        editing != null -> Editor(editing!!, onSave = { a ->
            persist(if (accounts.any { it.id == a.id }) accounts.map { if (it.id == a.id) a else it } else accounts + a)
            Hub.forget(a.id); editing = null
        }, onBack = { editing = null })
        sel != null -> Console(sel, ctx, onBack = { open = null }, onEdit = { editing = sel },
            onDelete = { Hub.forget(sel.id); persist(accounts - sel); open = null; ctx.maybeStopService() })
        else -> Home(accounts, onOpen = { open = it.id }, onAdd = {
            editing = Account(UUID.randomUUID().toString(), "", "")
        })
    }
}

@Composable
private fun statusColor(s: Status) = when (s) {
    Status.ONLINE -> Accent; Status.OFFLINE -> Muted; else -> Color(0xFFFFB84D)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home(accounts: List<Account>, onOpen: (Account) -> Unit, onAdd: () -> Unit) {
    Scaffold(containerColor = Bg,
        floatingActionButton = { FloatingActionButton(onClick = onAdd, containerColor = Accent, contentColor = Bg) { Icon(Icons.Default.Add, null) } }) { pad ->
        Column(Modifier.padding(pad).padding(20.dp)) {
            Text("AFK Console", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Run all your accounts from one place", color = Muted, modifier = Modifier.padding(bottom = 20.dp))
            if (accounts.isEmpty()) Text("No accounts yet. Tap + to add one.", color = Muted)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(accounts, key = { it.id }) { a ->
                    val st by Hub.runner(a).status.collectAsState()
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card)
                        .clickable { onOpen(a) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(statusColor(st)))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.username, fontWeight = FontWeight.SemiBold)
                            Text("${a.host}:${a.port}", color = Muted, fontSize = 13.sp)
                        }
                        Text(st.name.lowercase(), color = statusColor(st), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Console(a: Account, ctx: MainActivity, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val r = Hub.runner(a)
    val st by r.status.collectAsState()
    val log by r.log.collectAsState()
    val list = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    LaunchedEffect(log.size) { if (log.isNotEmpty()) list.animateScrollToItem(log.size - 1) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            Column(Modifier.weight(1f)) { Text(a.username, fontWeight = FontWeight.Bold); Text(st.name.lowercase(), color = statusColor(st), fontSize = 12.sp) }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
        }
        Button(onClick = {
            if (st == Status.OFFLINE) { ctx.ensureService(); r.start() } else { r.stop(); ctx.maybeStopService() }
        }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(12.dp)) {
            Icon(if (st == Status.OFFLINE) Icons.Default.PlayArrow else Icons.Default.Stop, null)
            Spacer(Modifier.width(8.dp)); Text(if (st == Status.OFFLINE) "Start AFK" else "Stop")
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF080B0F)).padding(10.dp), state = list) {
            items(log.size) { Text(log[it], fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFC9D4E0)) }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Chat or /command") }, singleLine = true)
            IconButton(onClick = { if (input.isNotBlank()) { r.send(input); input = "" } }, enabled = st == Status.ONLINE) { Icon(Icons.Default.Send, null) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Editor(init: Account, onSave: (Account) -> Unit, onBack: () -> Unit) {
    var user by remember { mutableStateOf(init.username) }
    var host by remember { mutableStateOf(init.host) }
    var port by remember { mutableStateOf(init.port.toString()) }
    var recon by remember { mutableStateOf(init.autoReconnect) }
    var delay by remember { mutableStateOf(init.reconnectDelaySec.toString()) }
    var join by remember { mutableStateOf(init.joinCommands.joinToString("\n")) }
    var timed by remember { mutableStateOf(init.timed.joinToString("\n") { "${it.everySec}|${it.text}" }) }
    var replies by remember { mutableStateOf(init.replies.joinToString("\n") { "${it.trigger}|${it.reply}" }) }

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }; Text("Account & macros", fontSize = 22.sp, fontWeight = FontWeight.Bold) } }
        item { OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true) }
        item { OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), label = { Text("Server address") }, singleLine = true) }
        item { OutlinedTextField(port, { port = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Port") }, singleLine = true) }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Auto-reconnect", Modifier.weight(1f)); Switch(recon, { recon = it }) } }
        item { OutlinedTextField(delay, { delay = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Reconnect delay (seconds)") }, singleLine = true) }
        item { OutlinedTextField(join, { join = it }, Modifier.fillMaxWidth(), label = { Text("On join: commands (one per line)") }, minLines = 2) }
        item { OutlinedTextField(timed, { timed = it }, Modifier.fillMaxWidth(), label = { Text("Timed messages: seconds|text") }, minLines = 3) }
        item { OutlinedTextField(replies, { replies = it }, Modifier.fillMaxWidth(), label = { Text("Auto-reply: trigger|reply") }, minLines = 3) }
        item {
            Button(onClick = {
                onSave(init.copy(
                    username = user.trim(), host = host.trim(), port = port.toIntOrNull() ?: 25565,
                    autoReconnect = recon, reconnectDelaySec = delay.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                    joinCommands = join.lines().map { it.trim() }.filter { it.isNotEmpty() },
                    timed = timed.lines().mapNotNull { l -> l.split("|", limit = 2).takeIf { it.size == 2 && it[0].trim().toIntOrNull() != null }?.let { TimedMsg(it[1].trim(), it[0].trim().toInt().coerceAtLeast(1)) } },
                    replies = replies.lines().mapNotNull { l -> l.split("|", limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() }?.let { AutoReply(it[0].trim(), it[1].trim()) } },
                ))
            }, Modifier.fillMaxWidth(), enabled = user.isNotBlank() && host.isNotBlank(), shape = RoundedCornerShape(12.dp)) { Text("Save") }
        }
    }
}
