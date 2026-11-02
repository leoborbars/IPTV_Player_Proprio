package br.com.iptvlab

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Small LAN-only HTTP client for the self-hosted SQLite progress API. */
class ProgressSyncClient(private val settings: ProgressSyncSettings) {
    fun fetchLists(profileId: String): Pair<List<MediaListEntry>, List<MediaListItem>> {
        val query = URLEncoder.encode(profileId, Charsets.UTF_8.name())
        val body = JSONObject(request("GET", "/api/v1/lists?profile_id=$query", null))
        val lists = body.optJSONArray("lists") ?: JSONArray()
        val items = body.optJSONArray("items") ?: JSONArray()
        return (0 until lists.length()).mapNotNull { index -> parseList(lists.optJSONObject(index)) } to
            (0 until items.length()).mapNotNull { index -> parseListItem(items.optJSONObject(index)) }
    }

    fun upsertList(profileId: String, list: MediaListEntry) {
        request("PUT", "/api/v1/lists", JSONObject()
            .put("profile_id", profileId).put("list_id", list.listId)
            .put("name", list.name).put("updated_at", list.updatedAt)
            .put("active", list.active))
    }

    fun upsertListItem(profileId: String, item: MediaListItem) {
        request("PUT", "/api/v1/list-items", JSONObject()
            .put("profile_id", profileId).put("list_id", item.listId)
            .put("media_type", item.mediaType).put("media_id", item.mediaId)
            .put("title", item.title.take(500)).put("poster_url", item.posterUrl)
            .put("updated_at", item.updatedAt).put("active", item.active)
            .put("series_id", item.seriesId))
    }

    fun fetchPlaybackPreferences(profileId: String): PlaybackPreferences? {
        val query = URLEncoder.encode(profileId, Charsets.UTF_8.name())
        val body = JSONObject(request("GET", "/api/v1/playback-preferences?profile_id=$query", null))
        if (!body.has("next_prompt_seconds")) return null
        return PlaybackPreferences(
            body.getInt("next_prompt_seconds").coerceIn(5, 300),
            body.getLong("updated_at").coerceAtLeast(0L)
        )
    }

    fun upsertPlaybackPreferences(profileId: String, value: PlaybackPreferences) {
        val payload = JSONObject()
            .put("profile_id", profileId)
            .put("next_prompt_seconds", value.nextPromptSeconds)
            .put("updated_at", value.updatedAt)
        request("PUT", "/api/v1/playback-preferences", payload)
    }

    fun fetch(profileId: String): List<WatchProgress> {
        val query = URLEncoder.encode(profileId, Charsets.UTF_8.name())
        val body = request("GET", "/api/v1/progress?profile_id=$query", null)
        val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).mapNotNull { index -> parse(items.optJSONObject(index)) }
    }

    fun upsert(profileId: String, item: WatchProgress) {
        val payload = JSONObject()
            .put("profile_id", profileId)
            .put("media_id", item.mediaId)
            .put("title", item.title.take(500))
            .put("poster_url", item.posterUrl)
            .put("position_ms", item.positionMs.coerceAtLeast(0))
            .put("duration_ms", item.durationMs.coerceAtLeast(0))
            .put("updated_at", item.updatedAt)
            .put("completed", item.completed)
            .put("hidden_from_continue", item.hiddenFromContinue)
        request("PUT", "/api/v1/progress", payload)
    }

    fun fetchFavorites(profileId: String): List<FavoriteEntry> {
        val query = URLEncoder.encode(profileId, Charsets.UTF_8.name())
        val body = request("GET", "/api/v1/favorites?profile_id=$query", null)
        val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).mapNotNull { index -> parseFavorite(items.optJSONObject(index)) }
    }

    fun upsertFavorite(profileId: String, item: FavoriteEntry) {
        val payload = JSONObject()
            .put("profile_id", profileId)
            .put("media_type", item.mediaType)
            .put("media_id", item.mediaId)
            .put("title", item.title.take(500))
            .put("poster_url", item.posterUrl)
            .put("updated_at", item.updatedAt)
            .put("active", item.active)
        request("PUT", "/api/v1/favorites", payload)
    }

    private fun request(method: String, path: String, payload: JSONObject?): String {
        val base = settings.endpoint.trim().trimEnd('/')
        val connection = URL(base + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 2500
        connection.readTimeout = 3500
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer ${settings.token}")
        if (payload != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("NAS respondeu HTTP $code")
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(json: JSONObject?): WatchProgress? = runCatching {
        json ?: return null
        WatchProgress(
            mediaId = json.getString("media_id"),
            title = json.optString("title", "Sem título"),
            posterUrl = json.optString("poster_url").takeIf { it.isNotBlank() && it != "null" },
            positionMs = json.optLong("position_ms").coerceAtLeast(0),
            durationMs = json.optLong("duration_ms").coerceAtLeast(0),
            updatedAt = json.optLong("updated_at").coerceAtLeast(0),
            completed = storedBoolean(json, "completed"),
            hiddenFromContinue = storedBoolean(json, "hidden_from_continue")
        )
    }.getOrNull()

    private fun parseFavorite(json: JSONObject?): FavoriteEntry? = runCatching {
        json ?: return null
        val type = json.getString("media_type")
        if (type != "movie" && type != "series") return null
        FavoriteEntry(
            mediaType = type,
            mediaId = json.getInt("media_id"),
            title = json.optString("title", "Sem título"),
            posterUrl = json.optString("poster_url").takeIf { it.isNotBlank() && it != "null" },
            updatedAt = json.optLong("updated_at").coerceAtLeast(0),
            active = storedBoolean(json, "active")
        )
    }.getOrNull()

    private fun parseList(json: JSONObject?): MediaListEntry? = runCatching {
        json ?: return null
        MediaListEntry(json.getString("list_id"), json.getString("name"),
            json.getLong("updated_at"), storedBoolean(json, "active"))
    }.getOrNull()

    private fun parseListItem(json: JSONObject?): MediaListItem? = runCatching {
        json ?: return null
        MediaListItem(json.getString("list_id"), json.getString("media_type"),
            json.getInt("media_id"), json.getString("title"),
            json.optString("poster_url").takeIf { it.isNotBlank() && it != "null" },
            json.getLong("updated_at"), storedBoolean(json, "active"),
            if (json.isNull("series_id")) null else json.optInt("series_id"))
    }.getOrNull()

    private fun storedBoolean(json: JSONObject, key: String): Boolean = when (val value = json.opt(key)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.equals("true", true) || value == "1"
        else -> false
    }
}
