package br.com.iptvlab

import android.content.Context
import org.json.JSONObject
import java.util.UUID

data class MediaListEntry(val listId: String, val name: String, val updatedAt: Long, val active: Boolean)

data class MediaListItem(
    val listId: String,
    val mediaType: String,
    val mediaId: Int,
    val title: String,
    val posterUrl: String?,
    val updatedAt: Long,
    val active: Boolean,
    val seriesId: Int? = null
)

/** Per-account lists and tombstones. The stable Leandro list imports existing favorites. */
class MediaListStore(context: Context) {
    private val preferences = context.getSharedPreferences("media_lists", Context.MODE_PRIVATE)

    @Synchronized
    fun ensureLeandro(profileId: String) {
        if (getList(profileId, LEANDRO_ID) == null) {
            saveList(profileId, MediaListEntry(LEANDRO_ID, "Leandro", 1L, true))
        }
    }

    @Synchronized
    fun getList(profileId: String, listId: String): MediaListEntry? =
        decodeList(preferences.getString("list:$profileId:$listId", null))

    @Synchronized
    fun lists(profileId: String): List<MediaListEntry> = preferences.all.asSequence()
        .filter { it.key.startsWith("list:$profileId:") }
        .mapNotNull { decodeList(it.value as? String) }
        .sortedWith(compareBy<MediaListEntry> { it.listId != LEANDRO_ID }.thenBy { it.name.lowercase() })
        .toList()

    fun activeLists(profileId: String): List<MediaListEntry> = lists(profileId).filter { it.active }

    @Synchronized
    fun selected(profileId: String): MediaListEntry {
        ensureLeandro(profileId)
        val id = preferences.getString("selected:$profileId", LEANDRO_ID) ?: LEANDRO_ID
        val selected = getList(profileId, id)?.takeIf { it.active }
        if (selected != null) return selected
        val fallback = activeLists(profileId).first()
        preferences.edit().putString("selected:$profileId", fallback.listId).apply()
        return fallback
    }

    @Synchronized
    fun select(profileId: String, listId: String) {
        require(getList(profileId, listId)?.active == true)
        preferences.edit().putString("selected:$profileId", listId).apply()
    }

    fun newList(name: String): MediaListEntry =
        MediaListEntry(UUID.randomUUID().toString(), name.trim().take(80), System.currentTimeMillis(), true)

    @Synchronized
    fun saveList(profileId: String, list: MediaListEntry) {
        preferences.edit().putString("list:$profileId:${list.listId}", JSONObject()
            .put("list_id", list.listId).put("name", list.name)
            .put("updated_at", list.updatedAt).put("active", list.active).toString()).apply()
    }

    @Synchronized
    fun getItem(profileId: String, listId: String, type: String, id: Int): MediaListItem? =
        decodeItem(preferences.getString("item:$profileId:$listId:$type:$id", null))

    @Synchronized
    fun items(profileId: String): List<MediaListItem> = preferences.all.asSequence()
        .filter { it.key.startsWith("item:$profileId:") }
        .mapNotNull { decodeItem(it.value as? String) }
        .toList()

    fun activeItems(profileId: String, listId: String, type: String): List<MediaListItem> =
        items(profileId).filter { it.active && it.listId == listId && it.mediaType == type }

    @Synchronized
    fun saveItem(profileId: String, item: MediaListItem) {
        preferences.edit().putString("item:$profileId:${item.listId}:${item.mediaType}:${item.mediaId}", JSONObject()
            .put("list_id", item.listId).put("media_type", item.mediaType)
            .put("media_id", item.mediaId).put("title", item.title)
            .put("poster_url", item.posterUrl).put("updated_at", item.updatedAt)
            .put("active", item.active).put("series_id", item.seriesId).toString()).apply()
    }

    @Synchronized
    fun merge(profileId: String, remoteLists: List<MediaListEntry>, remoteItems: List<MediaListItem>) {
        remoteLists.forEach { remote ->
            val current = getList(profileId, remote.listId)
            if (current == null || remote.updatedAt > current.updatedAt) saveList(profileId, remote)
        }
        remoteItems.forEach { remote ->
            val current = getItem(profileId, remote.listId, remote.mediaType, remote.mediaId)
            if (current == null || remote.updatedAt > current.updatedAt) saveItem(profileId, remote)
        }
        ensureLeandro(profileId)
    }

    @Synchronized
    fun migrateFavorites(profileId: String, favorites: List<FavoriteEntry>) {
        ensureLeandro(profileId)
        if (getList(profileId, LEANDRO_ID)?.active != true) return
        favorites.forEach { favorite ->
            val current = getItem(profileId, LEANDRO_ID, favorite.mediaType, favorite.mediaId)
            if (current == null || favorite.updatedAt > current.updatedAt) {
                saveItem(profileId, MediaListItem(LEANDRO_ID, favorite.mediaType, favorite.mediaId,
                    favorite.title, favorite.posterUrl, favorite.updatedAt, favorite.active))
            }
        }
    }

    private fun decodeList(value: String?): MediaListEntry? = runCatching {
        val json = JSONObject(value ?: return null)
        MediaListEntry(json.getString("list_id"), json.getString("name"),
            json.getLong("updated_at"), json.getBoolean("active"))
    }.getOrNull()

    private fun decodeItem(value: String?): MediaListItem? = runCatching {
        val json = JSONObject(value ?: return null)
        MediaListItem(json.getString("list_id"), json.getString("media_type"),
            json.getInt("media_id"), json.getString("title"),
            json.optString("poster_url").takeIf { it.isNotBlank() && it != "null" },
            json.getLong("updated_at"), json.getBoolean("active"),
            if (json.isNull("series_id")) null else json.optInt("series_id"))
    }.getOrNull()

    companion object { const val LEANDRO_ID = "leandro" }
}
