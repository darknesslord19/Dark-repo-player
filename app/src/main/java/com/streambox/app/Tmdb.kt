package com.streambox.app

import kotlinx.coroutines.async
import org.json.JSONObject
import java.net.URLEncoder

/** Afişli katalog ve Nuvio eklentileri için TMDB kimlikleri. */
data class TmdbItem(
    val id: Int,
    val type: String, // "movie" | "tv"
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val overview: String,
    val year: String?,
    val rating: Double?,
)

data class TmdbSeason(val number: Int, val name: String, val episodeCount: Int)
data class TmdbEpisode(val number: Int, val name: String, val overview: String, val still: String?)
data class TmdbDetails(val item: TmdbItem, val genres: List<String>, val seasons: List<TmdbSeason>, val runtime: Int?)

object Tmdb {
    private const val BASE = "https://api.themoviedb.org/3"
    fun img(path: String?, size: String = "w342") =
        if (path.isNullOrBlank() || path == "null") null else "https://image.tmdb.org/t/p/$size$path"

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject {
        val key = Settings.tmdbKey
        val q = (params + mapOf("language" to Settings.language) +
            (if (key.length <= 40) mapOf("api_key" to key) else emptyMap()))
            .entries.joinToString("&") { it.key + "=" + URLEncoder.encode(it.value, "UTF-8") }
        val url = "$BASE$path?$q"
        val text = if (key.length > 40) Net.getText(url, mapOf("Authorization" to "Bearer $key")) else Net.getText(url)
        return JSONObject(text)
    }

    private fun parse(o: JSONObject, forceType: String? = null): TmdbItem? {
        val type = forceType ?: o.optString("media_type")
        if (type != "movie" && type != "tv") return null
        val title = o.optString(if (type == "movie") "title" else "name").ifBlank { return null }
        val date = o.optString(if (type == "movie") "release_date" else "first_air_date")
        return TmdbItem(
            id = o.getInt("id"), type = type, title = title,
            poster = img(o.optString("poster_path")),
            backdrop = img(o.optString("backdrop_path"), "w780"),
            overview = o.optString("overview"),
            year = date.take(4).ifBlank { null },
            rating = o.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
        )
    }

    private suspend fun list(path: String, type: String?, params: Map<String, String> = emptyMap()): List<TmdbItem> {
        val arr = get(path, params).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parse(arr.getJSONObject(it), type) }
    }

    /** Bir raf: başlık + ilk sayfa + sonraki sayfaları getirmek için yol bilgisi. */
    data class Shelf(val title: String, val items: List<TmdbItem>, val path: String, val type: String?, val params: Map<String, String>)

    private val shelves = listOf(
        Triple("Trend filmler", "/trending/movie/week", "movie") to emptyMap<String, String>(),
        Triple("Trend diziler", "/trending/tv/week", "tv") to emptyMap(),
        Triple("Popüler filmler", "/movie/popular", "movie") to emptyMap(),
        Triple("Popüler diziler", "/tv/popular", "tv") to emptyMap(),
        Triple("Türk dizileri", "/discover/tv", "tv") to mapOf("with_original_language" to "tr", "sort_by" to "popularity.desc"),
        Triple("Türk filmleri", "/discover/movie", "movie") to mapOf("with_original_language" to "tr", "sort_by" to "popularity.desc"),
        Triple("En iyi filmler", "/movie/top_rated", "movie") to emptyMap(),
        Triple("En iyi diziler", "/tv/top_rated", "tv") to emptyMap(),
        Triple("Animasyon", "/discover/movie", "movie") to mapOf("with_genres" to "16", "sort_by" to "popularity.desc"),
        Triple("Anime", "/discover/tv", "tv") to mapOf("with_genres" to "16", "with_original_language" to "ja", "sort_by" to "popularity.desc"),
        Triple("Kore dizileri", "/discover/tv", "tv") to mapOf("with_original_language" to "ko", "sort_by" to "popularity.desc"),
        Triple("Belgeseller", "/discover/movie", "movie") to mapOf("with_genres" to "99", "sort_by" to "popularity.desc"),
    )

    /** Ana sayfa rafları (paralel yüklenir). */
    suspend fun home(): List<Shelf> = kotlinx.coroutines.coroutineScope {
        shelves.map { (t, params) ->
            async {
                val (title, path, type) = t
                runCatching { Shelf(title, list(path, type, params), path, type, params) }.getOrNull()
            }
        }.mapNotNull { it.await() }.filter { it.items.isNotEmpty() }
    }

    /** Bir rafın belirli sayfası. */
    suspend fun page(shelf: Shelf, page: Int): List<TmdbItem> = list(shelf.path, shelf.type, shelf.params + ("page" to page.toString()))

    suspend fun search(query: String): List<TmdbItem> = list("/search/multi", null, mapOf("query" to query))

    suspend fun details(type: String, id: Int): TmdbDetails {
        val o = get("/$type/$id")
        val item = parse(o, type)!!
        val genres = o.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } } ?: emptyList()
        val seasons = o.optJSONArray("seasons")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }
                .filter { it.optInt("season_number") > 0 }
                .map { TmdbSeason(it.optInt("season_number"), it.optString("name"), it.optInt("episode_count")) }
        } ?: emptyList()
        val runtime = o.optInt("runtime").takeIf { it > 0 }
        return TmdbDetails(item, genres, seasons, runtime)
    }

    suspend fun season(id: Int, number: Int): List<TmdbEpisode> {
        val arr = get("/tv/$id/season/$number").optJSONArray("episodes") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            TmdbEpisode(it.optInt("episode_number"), it.optString("name"), it.optString("overview"), img(it.optString("still_path"), "w300"))
        }
    }
}
