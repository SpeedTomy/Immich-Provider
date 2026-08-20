package app.immich.provider.immich

import android.net.Uri
import app.immich.provider.settings.ImmichCredentials
import app.immich.provider.settings.ImmichSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class ImmichAlbum(
    val id: String,
    val name: String,
    val assetCount: Int,
)

data class ImmichAsset(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val createdAtMillis: Long?,
)

private data class ImmichAssetPage(
    val assets: List<ImmichAsset>,
    val total: Int?,
)

private data class ImmichTimeBucket(
    val id: String,
    val count: Int,
)

interface ImmichMediaSource {
    fun listAlbums(): List<ImmichAlbum>
    fun listAssets(albumId: String, offset: Int, limit: Int): List<ImmichAsset>
    fun listRecentAssets(offset: Int, limit: Int): List<ImmichAsset>
    fun getAsset(assetId: String): ImmichAsset
    fun downloadOriginal(assetId: String, destination: File)
    fun downloadPreview(assetId: String, destination: File)
}

class HttpImmichMediaSource(private val settings: ImmichSettings) : ImmichMediaSource {
    override fun listAlbums(): List<ImmichAlbum> = jsonArray("/api/albums").map(::albumFromJson)

    override fun listAssets(albumId: String, offset: Int, limit: Int): List<ImmichAsset> {
        return assetsInAlbum(albumId).drop(offset).take(limit)
    }

    private fun assetsInAlbum(albumId: String): List<ImmichAsset> {
        val album = jsonObject("/api/albums/${Uri.encode(albumId)}")
        val assets = album.optJSONArray("assets") ?: JSONArray()
        return (0 until assets.length())
            .map { assetFromJson(assets.getJSONObject(it)) }
    }

    override fun listRecentAssets(offset: Int, limit: Int): List<ImmichAsset> {
        return try {
            timelineAssets(offset, limit)
        } catch (error: HttpStatusException) {
            if (error.statusCode !in setOf(400, 404)) throw error
            try {
                searchRecentAssets(offset, limit)
            } catch (searchError: HttpStatusException) {
                if (searchError.statusCode !in setOf(400, 404)) throw searchError
                recentAssetsFromAlbums(offset, limit)
            }
        }
    }

    override fun getAsset(assetId: String): ImmichAsset = assetFromJson(jsonObject("/api/assets/${Uri.encode(assetId)}"))

    override fun downloadOriginal(assetId: String, destination: File) {
        download("/api/assets/${Uri.encode(assetId)}/original", destination)
    }

    override fun downloadPreview(assetId: String, destination: File) {
        download("/api/assets/${Uri.encode(assetId)}/thumbnail?size=preview", destination)
    }

    private fun jsonArray(path: String): List<JSONObject> {
        val response = get(path)
        return JSONArray(response).let { array -> (0 until array.length()).map(array::getJSONObject) }
    }

    private fun timelineAssets(offset: Int, limit: Int): List<ImmichAsset> {
        if (limit <= 0) return emptyList()

        val endExclusive = offset.toLong() + limit.toLong()
        val results = mutableListOf<ImmichAsset>()
        var assetsBeforeBucket = 0L

        for (bucket in timelineBuckets()) {
            val assetsAfterBucket = assetsBeforeBucket + bucket.count
            if (assetsAfterBucket <= offset) {
                assetsBeforeBucket = assetsAfterBucket
                continue
            }

            results += timelineBucketAssets(bucket.id)
            if (assetsAfterBucket >= endExclusive) break
            assetsBeforeBucket = assetsAfterBucket
        }

        val skippedInsideFirstBucket = (offset.toLong() - assetsBeforeBucket).coerceAtLeast(0).toInt()
        return results.drop(skippedInsideFirstBucket).take(limit)
    }

    private fun timelineBuckets(): List<ImmichTimeBucket> {
        val response = JSONArray(get("/api/timeline/buckets?$TIMELINE_QUERY"))
        return (0 until response.length()).map { index ->
            response.getJSONObject(index).let { value ->
                ImmichTimeBucket(value.getString("timeBucket"), value.getInt("count"))
            }
        }.sortedByDescending(ImmichTimeBucket::id)
    }

    private fun timelineBucketAssets(timeBucket: String): List<ImmichAsset> {
        val path = "/api/timeline/bucket?$TIMELINE_QUERY&timeBucket=${Uri.encode(timeBucket)}"
        val response = JSONObject(get(path))
        val ids = response.getJSONArray("id")
        val fileCreatedAt = response.getJSONArray("fileCreatedAt")
        val isImage = response.getJSONArray("isImage")

        return (0 until ids.length()).map { index ->
            val id = ids.getString(index)
            ImmichAsset(
                id = id,
                name = id,
                mimeType = if (isImage.getBoolean(index)) "image/*" else "video/*",
                sizeBytes = null,
                createdAtMillis = fileCreatedAt.optString(index).toEpochMillisOrNull(),
            )
        }.sortedWith(compareByDescending<ImmichAsset> { it.createdAtMillis }.thenByDescending { it.id })
    }

    private fun searchRecentAssets(offset: Int, limit: Int): List<ImmichAsset> {
        val requested = offset.toLong() + limit.toLong()
        val results = mutableListOf<ImmichAsset>()
        var page = 1

        while (results.size.toLong() < requested && page <= MAX_SEARCH_PAGES) {
            val resultPage = metadataSearchPage(page++)
            val newAssets = resultPage.assets.filter { asset -> results.none { it.id == asset.id } }
            results += newAssets
            if (newAssets.isEmpty() || resultPage.assets.size < SEARCH_PAGE_SIZE) break
            if (resultPage.total != null && results.size >= resultPage.total) break
        }

        return results.drop(offset).take(limit)
    }

    private fun metadataSearchPage(page: Int): ImmichAssetPage {
        val payload = JSONObject()
            .put("order", "desc")
            .put("page", page)
            .put("size", SEARCH_PAGE_SIZE)
            .toString()
        val response = JSONObject(post("/api/search/metadata", payload))
        val assetsResponse = response.optJSONObject("assets")
        val values = assetsResponse?.optJSONArray("items")
            ?: response.optJSONArray("assets")
            ?: response.optJSONArray("items")
            ?: JSONArray()
        val resultPage = ImmichAssetPage(
            assets = (0 until values.length()).map { assetFromJson(values.getJSONObject(it)) },
            total = assetsResponse?.optInt("total", -1)?.takeIf { it >= 0 },
        )
        return resultPage
    }

    private fun jsonObject(path: String): JSONObject = JSONObject(get(path))

    private fun get(path: String): String = connection(path).use { connection ->
        checkSuccess(connection)
        connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun post(path: String, body: String): String = connection(path, "POST").use { connection ->
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.bufferedWriter().use { it.write(body) }
        checkSuccess(connection)
        connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun download(path: String, destination: File) {
        destination.parentFile?.mkdirs()
        try {
            connection(path).use { connection ->
                checkSuccess(connection)
                connection.inputStream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            }
        } catch (error: IOException) {
            destination.delete()
            throw error
        }
    }

    private fun connection(path: String, method: String = "GET"): HttpURLConnection {
        val credentials = credentials()
        return (URL(credentials.serverUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("x-api-key", credentials.apiKey)
            setRequestProperty("Accept", "*/*")
        }
    }

    private fun checkSuccess(connection: HttpURLConnection) {
        if (connection.responseCode !in 200..299) {
            throw HttpStatusException(connection.responseCode)
        }
    }

    private fun recentAssetsFromAlbums(offset: Int, limit: Int): List<ImmichAsset> = listAlbums()
        .flatMap { album -> assetsInAlbum(album.id) }
        .distinctBy(ImmichAsset::id)
        .sortedByDescending(ImmichAsset::createdAtMillis)
        .drop(offset)
        .take(limit)

    private fun credentials(): ImmichCredentials = settings.load()
        ?: throw FileNotFoundException("Configurez le serveur Immich dans l'application.")

    private fun albumFromJson(value: JSONObject): ImmichAlbum = ImmichAlbum(
        id = value.getString("id"),
        name = value.optString("albumName", "Album Immich"),
        assetCount = value.optInt("assetCount", value.optJSONArray("assets")?.length() ?: 0),
    )

    private fun assetFromJson(value: JSONObject): ImmichAsset = ImmichAsset(
        id = value.getString("id"),
        name = value.optString("originalFileName", value.getString("id")),
        mimeType = value.optString("originalMimeType", "application/octet-stream"),
        sizeBytes = value.optLong("exifInfo", -1).takeIf { it >= 0 },
        createdAtMillis = value.timelineMillis(),
    )

    private fun JSONObject.timelineMillis(): Long? = sequenceOf(
        "fileCreatedAt",
        "localDateTime",
        "createdAt",
        "updatedAt",
    ).map { key -> optString(key).toEpochMillisOrNull() }.firstOrNull { it != null }

    private fun String.toEpochMillisOrNull(): Long? = runCatching {
        Instant.parse(this).toEpochMilli()
    }.recoverCatching {
        LocalDateTime.parse(this).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    private inline fun <T : HttpURLConnection, R> T.use(block: (T) -> R): R = try {
        block(this)
    } finally {
        disconnect()
    }

    private class HttpStatusException(val statusCode: Int) : IOException("Immich a repondu HTTP $statusCode.")

    private companion object {
        const val SEARCH_PAGE_SIZE = 250
        const val MAX_SEARCH_PAGES = 100
        const val TIMELINE_QUERY =
            "order=desc&orderBy=takenAt&visibility=timeline&withPartners=true&withStacked=true"
    }
}
