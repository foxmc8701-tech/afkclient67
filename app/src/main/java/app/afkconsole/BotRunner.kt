package app.afkconsole

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.geysermc.mcprotocollib.network.Session
import org.geysermc.mcprotocollib.network.event.session.*
import org.geysermc.mcprotocollib.network.packet.Packet
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.time.Instant
import java.util.BitSet

enum class Status { OFFLINE, CONNECTING, ONLINE, RECONNECTING }

/** One headless Minecraft connection. No movement or look packets are ever sent. */
class BotRunner(private val acc: Account, private val scope: CoroutineScope) {
    val status = MutableStateFlow(Status.OFFLINE)
    val log = MutableStateFlow<List<String>>(emptyList())

    private var session: Session? = null
    private var job: Job? = null
    private var stopped = true

    private fun say(s: String) { log.value = (log.value + s).takeLast(400) }

    fun start() {
        if (job?.isActive == true) return
        stopped = false
        job = scope.launch(Dispatchers.IO) {
            while (isActive && !stopped) {
                connectOnce()
                if (stopped || !acc.autoReconnect) break
                status.value = Status.RECONNECTING
                say("Reconnecting in ${acc.reconnectDelaySec}s…")
                delay(acc.reconnectDelaySec * 1000L)
            }
            status.value = Status.OFFLINE
        }
    }

    fun stop() {
        stopped = true
        session?.disconnect("Stopped by user")
        job?.cancel()
        status.value = Status.OFFLINE
        say("Stopped.")
    }

    fun send(text: String) {
        val s = session ?: return
        if (text.startsWith("/")) s.send(ServerboundChatCommandPacket(text.drop(1)))
        else s.send(ServerboundChatPacket(text, Instant.now().toEpochMilli(), 0L, null, 0, BitSet()))
        say("> $text")
    }

    private suspend fun connectOnce() = coroutineScope {
        status.value = Status.CONNECTING
        say("Connecting to ${acc.host}:${acc.port} as ${acc.username}")
        val done = CompletableDeferred<Unit>()
        val s = TcpClientSession(acc.host, acc.port, MinecraftProtocol(acc.username))
        session = s
        val timers = mutableListOf<Job>()
        s.addListener(object : SessionAdapter() {
            override fun packetReceived(session: Session, packet: Packet) {
                when (packet) {
                    is ClientboundLoginPacket -> {
                        status.value = Status.ONLINE
                        say("Joined server.")
                        launch { onJoin(timers) }
                    }
                    is ClientboundSystemChatPacket -> onChat(PlainTextComponentSerializer.plainText().serialize(packet.content))
                    is ClientboundPlayerChatPacket -> onChat(packet.content)
                }
            }
            override fun disconnected(event: DisconnectedEvent) {
                say("Disconnected: ${event.reason}")
                timers.forEach { it.cancel() }
                done.complete(Unit)
            }
        })
        s.connect()
        try { done.await() } finally { timers.forEach { it.cancel() }; session = null }
    }

    private fun CoroutineScope.onJoin(timers: MutableList<Job>) {
        launch {
            acc.joinCommands.forEach { delay(1500); send(it) }
        }
        acc.timed.forEach { t ->
            timers += launch {
                while (isActive) { delay(t.everySec * 1000L); send(t.text) }
            }
        }
    }

    private fun onChat(line: String) {
        say(line)
        acc.replies.firstOrNull { line.contains(it.trigger, ignoreCase = true) }?.let { send(it.reply) }
    }
}
