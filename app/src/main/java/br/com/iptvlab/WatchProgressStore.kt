package br.com.iptvlab

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

data class WatchProgress(
    val mediaId: String,
    val title: String,
    val posterUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val completed: Boolean = false,
    val hiddenFromContinue: Boolean = false
)

/** Local progress remains available when the NAS cannot be reached. */
class WatchProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences("watch_progress", Context.MODE_PRIVATE)

    @Synchronized
    fun get(profileId: String, mediaId: String): WatchProgress? = decode(preferences.getString(key(profileId, mediaId), null))

    @Synchronized
    fun all(profileId: String): List<WatchProgress> = preferences.all.entries
        .asSequence()
        .filter { it.key.startsWith("$profileId:") }
        .mapNotNull { (key, value) -> decode(value as? String) }
        .sortedByDescending { it.updatedAt }
        .toList()

    fun list(profileId: String): List<WatchProgress> = all(profileId)
        .filterNot { it.completed || it.hiddenFromContinue }

    @Synchronized
    fun save(profileId: String, progress: WatchProgress) {
        preferences.edit().putString(key(profileId, progress.mediaId), encode(progress)).apply()
    }

    @Synchronized
    fun merge(profileId: String, remote: List<WatchProgress>) {
        val editor = preferences.edit()
        remote.forEach { item ->
            val current = get(profileId, item.mediaId)
            if (current == null || item.updatedAt > current.updatedAt ||
                (item.updatedAt == current.updatedAt && item.hiddenFromContinue && !current.hiddenFromContinue)) {
                editor.putString(key(profileId, item.mediaId), encode(item))
            }
        }
        editor.apply()
    }

    private fun key(profileId: String, mediaId: String) = "$profileId:$mediaId"

    private fun encode(item: WatchProgress) = JSONObject()
        .put("media_id", item.mediaId)
        .put("title", item.title)
        .put("poster_url", item.posterUrl)
        .put("position_ms", item.positionMs)
        .put("duration_ms", item.durationMs)
        .put("updated_at", item.updatedAt)
        .put("completed", item.completed)
        .put("hidden_from_continue", item.hiddenFromContinue)
        .toString()

    private fun decode(value: String?): WatchProgress? = runCatching {
        val json = JSONObject(value ?: return null)
        WatchProgress(
            mediaId = json.getString("media_id"),
            title = json.optString("title", "Sem título"),
            posterUrl = json.optString("poster_url").takeIf { it.isNotBlank() && it != "null" },
            positionMs = json.optLong("position_ms").coerceAtLeast(0),
            durationMs = json.optLong("duration_ms").coerceAtLeast(0),
            updatedAt = json.optLong("updated_at").coerceAtLeast(0),
            completed = json.optBoolean("completed", false),
            hiddenFromContinue = json.optBoolean("hidden_from_continue", false)
        )
    }.getOrNull()

    companion object {
        /** Same Xtream username on both devices produces the same profile even if server URLs differ. */
        fun profileId(username: String): String {
            val normalizedUser = username.trim().lowercase()
            val input = "lunastream-progress-v2\u0000$normalizedUser".toByteArray(Charsets.UTF_8)
            return MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it) }
        }

        /** Previous profile format, used once to migrate data already stored on the NAS and devices. */
        fun legacyProfileId(server: String, username: String): String {
            val normalizedServer = server.trim().trimEnd('/').lowercase()
            val normalizedUser = username.trim().lowercase()
            val input = "$normalizedServer\u0000$normalizedUser".toByteArray(Charsets.UTF_8)
            return MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it) }
        }
    }
}
