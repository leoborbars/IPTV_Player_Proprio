package br.com.iptvlab

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class CatalogEntry(
    val id: Int,
    val title: String,
    val extension: String = "",
    val posterUrl: String? = null,
    val categoryId: Int? = null,
    val addedAt: Long? = null,
    val rating: String? = null
)

data class SeriesEntry(
    val id: Int,
    val title: String,
    val coverUrl: String? = null,
    val categoryId: Int? = null,
    val addedAt: Long? = null,
    val rating: String? = null
)

data class MovieDetails(
    val title: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val plot: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val duration: String? = null,
    val rating: String? = null
)

/** Xtream API para catálogo VOD autorizado. Não acessa endpoints de canais ao vivo. */
class XtreamClient(val server: String, val username: String, val password: String) {
    init {
        require(server.startsWith("https://") || server.startsWith("http://")) { "Servidor deve começar com https:// ou http://" }
        require(username.isNotBlank() && password.isNotBlank()) { "Informe usuário e senha" }
    }

    private fun endpoint(action: String? = null, extras: Map<String, String> = emptyMap()): String {
        val builder = Uri.parse(server.trimEnd('/') + "/player_api.php").buildUpon()
            .appendQueryParameter("username", username)
            .appendQueryParameter("password", password)
        if (action != null) builder.appendQueryParameter("action", action)
        extras.forEach { (key, value) -> builder.appendQueryParameter(key, value) }
        return builder.build().toString()
    }

    private fun loadJson(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 25000
        conn.instanceFollowRedirects = false // Não encaminhar credenciais a redirecionamentos
        conn.setRequestProperty("Accept", "application/json")
        try {
            val status = conn.responseCode
            if (status !in 200..299) throw IllegalStateException("Falha HTTP $status")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun authenticate() {
        val root = JSONObject(loadJson(endpoint()))
        val user = root.optJSONObject("user_info") ?: error("Resposta Xtream sem user_info")
        if (flexibleInt(user.opt("auth")) != 1) error("Credenciais não autorizadas pelo servidor")
    }

    fun movieCategories(): List<CatalogEntry> = parseCategories(loadArray(endpoint("get_vod_categories")))

    /** O filtro é opcional; alguns provedores aceitam a listagem completa. */
    fun movies(categoryId: Int? = null): List<CatalogEntry> {
        val extras = categoryId?.let { mapOf("category_id" to it.toString()) } ?: emptyMap()
        return parseMovies(loadArray(endpoint("get_vod_streams", extras)))
    }

    /** Detalhes sob demanda, usados somente para o item escolhido como destaque na Home. */
    fun movieDetails(movieId: Int): MovieDetails {
        val root = JSONObject(loadJson(endpoint("get_vod_info", mapOf("vod_id" to movieId.toString()))))
        val info = root.optJSONObject("info") ?: JSONObject()
        val movieData = root.optJSONObject("movie_data") ?: JSONObject()
        val backdrop = firstImage(info.opt("backdrop_path"))
            ?: firstImage(info.opt("backdrop"))
        val release = textValue(info.opt("releasedate"))
            ?: textValue(info.opt("release_date"))
            ?: textValue(info.opt("year"))
        val seconds = flexibleInt(info.opt("duration_secs"))
        val duration = textValue(info.opt("duration")) ?: seconds?.let(::formatDuration)
        return MovieDetails(
            title = textValue(info.opt("name")) ?: textValue(movieData.opt("name")),
            posterUrl = safeImageUrl(info.opt("movie_image")) ?: safeImageUrl(movieData.opt("stream_icon")),
            backdropUrl = backdrop,
            plot = textValue(info.opt("plot")),
            genre = textValue(info.opt("genre")),
            year = release?.let { YEAR_REGEX.find(it)?.value },
            duration = duration,
            rating = textValue(info.opt("rating"))
        )
    }

    fun seriesCategories(): List<CatalogEntry> = parseCategories(loadArray(endpoint("get_series_categories")))

    /** Mantém os dados de séries necessários à Home sem buscar detalhes/episódios em massa. */
    fun series(categoryId: Int? = null): List<SeriesEntry> {
        val extras = categoryId?.let { mapOf("category_id" to it.toString()) } ?: emptyMap()
        val array = loadArray(endpoint("get_series", extras))
        val result = ArrayList<SeriesEntry>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = flexibleInt(item.opt("series_id")) ?: continue
            val title = textValue(item.opt("name")).orEmpty().ifBlank { "Sem título" }
            result += SeriesEntry(
                id = id,
                title = title,
                coverUrl = safeImageUrl(item.opt("cover")) ?: safeImageUrl(item.opt("stream_icon")),
                categoryId = flexibleInt(item.opt("category_id")),
                addedAt = flexibleTimestamp(item.opt("last_modified")) ?: flexibleTimestamp(item.opt("added")),
                rating = textValue(item.opt("rating"))
            )
        }
        return result.sortedBy { it.title.lowercase() }
    }

    fun movieUrl(entry: CatalogEntry): String {
        val extension = entry.extension.trim().lowercase()
        require(extension.matches(Regex("[a-z0-9]{2,6}"))) { "Extensão do stream não reconhecida" }
        return "${server.trimEnd('/')}/movie/${Uri.encode(username)}/${Uri.encode(password)}/${entry.id}.$extension"
    }

    private fun loadArray(url: String): JSONArray {
        val body = loadJson(url).trim()
        if (body == "" || body == "null") return JSONArray()
        try {
            return JSONArray(body)
        } catch (_: Exception) {
            // Alguns clones envolvem a lista em uma propriedade; também aceitamos um único objeto.
            val root = runCatching { JSONObject(body) }.getOrNull()
                ?: throw IllegalStateException("Resposta JSON do catálogo inválida")
            listOf("data", "results", "categories", "streams", "series").forEach { key ->
                root.optJSONArray(key)?.let { return it }
            }
            if (root.length() == 0) return JSONArray()
            return JSONArray().put(root)
        }
    }

    private fun parseCategories(array: JSONArray): List<CatalogEntry> {
        val result = ArrayList<CatalogEntry>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = flexibleInt(item.opt("category_id")) ?: continue
            val title = textValue(item.opt("category_name")).orEmpty().ifBlank { "Sem categoria" }
            result += CatalogEntry(id = id, title = title, categoryId = id)
        }
        return result.sortedBy { it.title.lowercase() }
    }

    private fun parseMovies(array: JSONArray): List<CatalogEntry> {
        val result = ArrayList<CatalogEntry>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = flexibleInt(item.opt("stream_id")) ?: continue
            result += CatalogEntry(
                id = id,
                title = textValue(item.opt("name")).orEmpty().ifBlank { "Sem título" },
                extension = textValue(item.opt("container_extension")).orEmpty(),
                posterUrl = safeImageUrl(item.opt("stream_icon")),
                categoryId = flexibleInt(item.opt("category_id")),
                addedAt = flexibleTimestamp(item.opt("added")),
                rating = textValue(item.opt("rating"))
            )
        }
        return result
    }

    private fun safeImageUrl(value: Any?): String? {
        val candidate = textValue(value) ?: return null
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        return candidate.takeIf { uri.scheme.equals("http", true) || uri.scheme.equals("https", true) }
    }

    private fun firstImage(value: Any?): String? = when (value) {
        is JSONArray -> (0 until value.length()).asSequence()
            .mapNotNull { safeImageUrl(value.opt(it)) }
            .firstOrNull()
        else -> safeImageUrl(value)
    }

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
    }

    private fun textValue(value: Any?): String? = when (value) {
        null, JSONObject.NULL -> null
        else -> value.toString().trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
    }

    private fun flexibleInt(value: Any?): Int? = when (value) {
        is Number -> value.toInt().takeIf { value.toDouble() == it.toDouble() }
        else -> textValue(value)?.toIntOrNull()
    }

    private fun flexibleTimestamp(value: Any?): Long? {
        val raw = textValue(value) ?: return null
        val numeric = raw.toLongOrNull()
        if (numeric != null) {
            if (numeric <= 0L) return null
            return if (numeric > 10_000_000_000L) numeric else numeric * 1000L
        }
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
    }

    private companion object {
        val YEAR_REGEX = Regex("\\b(?:18|19|20|21)\\d{2}\\b")
    }
}
