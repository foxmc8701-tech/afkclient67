package app.afkconsole

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.*

/** A timed chat message or /command sent every [everySec] seconds while connected. */
data class TimedMsg(val text: String, val everySec: Int)

/** When a chat line contains [trigger] (case-insensitive), reply with [reply]. */
data class AutoReply(val trigger: String, val reply: String)

data class Account(
    val id: String,
    val username: String,
    val host: String,
    val port: Int = 25565,
    val autoReconnect: Boolean = true,
    val reconnectDelaySec: Int = 10,
    val joinCommands: List<String> = emptyList(),
    val timed: List<TimedMsg> = emptyList(),
    val replies: List<AutoReply> = emptyList(),
)

object Store {
    private const val P = "afk"
    fun load(c: Context): List<Account> {
        val raw = c.getSharedPreferences(P, 0).getString("accounts", "[]")!!
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Account(
                o.getString("id"), o.getString("username"), o.getString("host"), o.optInt("port", 25565),
                o.optBoolean("autoReconnect", true), o.optInt("reconnectDelaySec", 10),
                o.optJSONArray("join").strings(),
                o.optJSONArray("timed").objs().map { TimedMsg(it.getString("t"), it.getInt("s")) },
                o.optJSONArray("replies").objs().map { AutoReply(it.getString("k"), it.getString("r")) },
            )
        }
    }
    fun save(c: Context, list: List<Account>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().put("id", a.id).put("username", a.username).put("host", a.host)
                .put("port", a.port).put("autoReconnect", a.autoReconnect)
                .put("reconnectDelaySec", a.reconnectDelaySec)
                .put("join", JSONArray(a.joinCommands))
                .put("timed", JSONArray(a.timed.map { JSONObject().put("t", it.text).put("s", it.everySec) }))
                .put("replies", JSONArray(a.replies.map { JSONObject().put("k", it.trigger).put("r", it.reply) })))
        }
        c.getSharedPreferences(P, 0).edit().putString("accounts", arr.toString()).apply()
    }
    private fun JSONArray?.strings() = if (this == null) emptyList() else (0 until length()).map { getString(it) }
    private fun JSONArray?.objs() = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}

/** Process-wide registry of runners; the service only keeps the process alive. */
object Hub {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runners = mutableMapOf<String, BotRunner>()
    fun runner(a: Account): BotRunner = runners.getOrPut(a.id) { BotRunner(a, scope) }
    fun forget(id: String) { runners.remove(id)?.stop() }
    fun stopAll() { runners.values.forEach { it.stop() } }
    fun anyActive() = runners.values.any { it.status.value != Status.OFFLINE }
}
