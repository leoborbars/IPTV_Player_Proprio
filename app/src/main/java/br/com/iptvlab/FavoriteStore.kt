package br.com.iptvlab

import android.content.Context
import org.json.JSONObject

data class FavoriteEntry(
    val mediaType: String,
    val mediaId: Int,
    val title: String,
    val posterUrl: String?,
    val updatedAt: Long,
    val active: Boolean
)

/** Tombstones keep removals synchronized when another device was offline. */
class FavoriteStore(context: Context) {
    private val preferences = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    @Synchronized
    fun get(profileId: String, mediaType: String, mediaId: Int): FavoriteEntry? =
        decode(preferences.getString(key(profileId, mediaType, mediaId), null))

    @Synchronized
    fun all(profileId: String): List<FavoriteEntry> = preferences.all.entries.asSequence()
        .filter { it.key.startsWith("$profileId:") }
        .mapNotNull { decode(it.value as? String) }
        .sortedByDescending { it.updatedAt }
        .toList()

    fun active(profileId: String, mediaType: String): List<FavoriteEntry> =
        all(profileId).filter { it.active && it.mediaType == mediaType }

    @Synchronized
    fun save(profileId: String, item: FavoriteEntry) {
        preferences.edit().putString(key(profileId, item.mediaType, item.mediaId), encode(item)).apply()
    }

    @Synchronized
    fun merge(profileId: String, remote: List<FavoriteEntry>) {
        val editor = preferences.edit()
        remote.forEach { item ->
            val current = get(profileId, item.mediaType, item.mediaId)
            if (current == null || item.updatedAt > current.updatedAt) {
                editor.putString(key(profileId, item.mediaType, item.mediaId), encode(item))
            }
        }
        editor.apply()
    }

    private fun key(profileId: String, mediaType: String, mediaId: Int) = "$profileId:$mediaType:$mediaId"

    private fun encode(item: FavoriteEntry) = JSONObject()
        .put("media_type", item.mediaType)
        .put("media_id", item.mediaId)
        .put("title", item.title)
        .put("poster_url", item.posterUrl)
        .put("updated_at", item.updatedAt)
        .put("active", item.active)
        .toString()

    private fun decode(value: String?): FavoriteEntry? = runCatching {
        val json = JSONObject(value ?: return null)
        val type = json.getString("media_type")
        if (type != "movie" && type != "series") return null
        FavoriteEntry(
            mediaType = type,
            mediaId = json.getInt("media_id"),
            title = json.optString("title", "Sem título"),
            posterUrl = json.optString("poster_url").takeIf { it.isNotBlank() && it != "null" },
            updatedAt = json.optLong("updated_at").coerceAtLeast(0),
            active = json.optBoolean("active", true)
        )
    }.getOrNull()
}
