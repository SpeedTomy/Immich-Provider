package app.immich.provider.core

import java.nio.charset.StandardCharsets
import java.util.Base64

sealed interface DocumentId {
    data object Root : DocumentId
    data class Collection(val name: String) : DocumentId
    data class Album(val albumId: String) : DocumentId
    data class Asset(val albumId: String, val assetId: String) : DocumentId

    companion object {
        const val ROOT = "immich-root"
        const val RECENTS = "recents"
        const val ALBUMS = "albums"

        fun collection(name: String): String {
            require(name == RECENTS || name == ALBUMS) { "Collection inconnue." }
            return "collection.$name"
        }

        fun album(albumId: String): String = "album.${encode(albumId)}"

        fun asset(albumId: String, assetId: String): String =
            "asset.${encode(albumId)}.${encode(assetId)}"

        fun parse(documentId: String): DocumentId? = when {
            documentId == ROOT -> Root
            documentId == collection(RECENTS) -> Collection(RECENTS)
            documentId == collection(ALBUMS) -> Collection(ALBUMS)
            documentId.startsWith("album.") -> decode(documentId.removePrefix("album."))?.let(::Album)
            documentId.startsWith("asset.") -> {
                val parts = documentId.split('.', limit = 3)
                if (parts.size != 3) null else {
                    val albumId = decode(parts[1])
                    val assetId = decode(parts[2])
                    if (albumId == null || assetId == null) null else Asset(albumId, assetId)
                }
            }
            else -> null
        }

        private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

        private fun decode(value: String): String? = try {
            String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
