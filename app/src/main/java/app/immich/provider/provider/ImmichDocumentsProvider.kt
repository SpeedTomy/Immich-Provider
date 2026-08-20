package app.immich.provider.provider

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import app.immich.provider.R
import app.immich.provider.core.DocumentId
import app.immich.provider.core.ServerUrl
import app.immich.provider.immich.HttpImmichMediaSource
import app.immich.provider.immich.ImmichAlbum
import app.immich.provider.immich.ImmichAsset
import app.immich.provider.immich.ImmichMediaSource
import app.immich.provider.settings.ImmichSettings
import java.io.File
import java.io.FileNotFoundException

class ImmichDocumentsProvider : DocumentsProvider() {
    private lateinit var source: ImmichMediaSource
    private lateinit var settings: ImmichSettings

    override fun onCreate(): Boolean {
        val providerContext = context ?: return false
        settings = ImmichSettings(providerContext)
        source = HttpImmichMediaSource(settings)
        return true
    }

    override fun queryRoots(projection: Array<String>?): Cursor = MatrixCursor(resolveRootProjection(projection)).apply {
        newRow()
            .add(DocumentsContract.Root.COLUMN_ROOT_ID, DocumentId.ROOT)
            .add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, DocumentId.ROOT)
            .add(DocumentsContract.Root.COLUMN_TITLE, "Immich")
            .add(
                DocumentsContract.Root.COLUMN_SUMMARY,
                settings.loadServerUrl()?.let(ServerUrl::host) ?: "Serveur non configure",
            )
            .add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD)
            .add(DocumentsContract.Root.COLUMN_ICON, R.drawable.ic_immich)
            .add(DocumentsContract.Root.COLUMN_MIME_TYPES, "image/*\nvideo/*")
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor = documentCursor(projection).apply {
        when (val parsed = DocumentId.parse(documentId) ?: throw FileNotFoundException(documentId)) {
            DocumentId.Root -> addRootRow(this)
            is DocumentId.Collection -> addCollectionRow(this, parsed.name)
            is DocumentId.Album -> source.listAlbums().firstOrNull { it.id == parsed.albumId }
                ?.let { addAlbumRow(this, it) } ?: throw FileNotFoundException(documentId)
            is DocumentId.Asset -> addAssetRow(this, parsed.albumId, source.getAsset(parsed.assetId))
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?,
    ): Cursor = queryChildDocumentsInternal(parentDocumentId, projection, null)

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        queryArgs: Bundle?,
    ): Cursor = queryChildDocumentsInternal(parentDocumentId, projection, queryArgs)

    private fun queryChildDocumentsInternal(
        parentDocumentId: String,
        projection: Array<String>?,
        queryArgs: Bundle?,
    ): Cursor = documentCursor(projection).apply {
        when (val parsed = DocumentId.parse(parentDocumentId) ?: throw FileNotFoundException(parentDocumentId)) {
            DocumentId.Root -> {
                val offset = queryArgs?.getInt(QUERY_ARG_OFFSET, 0) ?: 0
                val limit = queryArgs?.getInt(QUERY_ARG_LIMIT, DEFAULT_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE
                val assets = source.listRecentAssets(offset, limit)
                assets.forEach { addAssetRow(this, DocumentId.RECENTS, it) }
                if (offset == 0) addCollectionRow(this, DocumentId.ALBUMS)
            }
            is DocumentId.Collection -> when (parsed.name) {
                DocumentId.RECENTS -> {
                    val offset = queryArgs?.getInt(QUERY_ARG_OFFSET, 0) ?: 0
                    val limit = queryArgs?.getInt(QUERY_ARG_LIMIT, DEFAULT_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE
                    source.listRecentAssets(offset, limit).forEach { addAssetRow(this, DocumentId.RECENTS, it) }
                }
                DocumentId.ALBUMS -> source.listAlbums().forEach { addAlbumRow(this, it) }
            }
            is DocumentId.Album -> {
                val offset = queryArgs?.getInt(QUERY_ARG_OFFSET, 0) ?: 0
                val limit = queryArgs?.getInt(QUERY_ARG_LIMIT, DEFAULT_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE
                source.listAssets(parsed.albumId, offset, limit).forEach { addAssetRow(this, parsed.albumId, it) }
            }
            is DocumentId.Asset -> throw FileNotFoundException("Un media ne contient pas d'enfant.")
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Seule la lecture est prise en charge.")
        val assetId = (DocumentId.parse(documentId) as? DocumentId.Asset)?.assetId
            ?: throw FileNotFoundException(documentId)
        val destination = cacheFile("original")
        source.downloadOriginal(assetId, destination)
        return ParcelFileDescriptor.open(destination, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?,
    ): AssetFileDescriptor {
        val assetId = (DocumentId.parse(documentId) as? DocumentId.Asset)?.assetId
            ?: throw FileNotFoundException(documentId)
        val destination = cacheFile("preview")
        source.downloadPreview(assetId, destination)
        return AssetFileDescriptor(
            ParcelFileDescriptor.open(destination, ParcelFileDescriptor.MODE_READ_ONLY),
            0,
            AssetFileDescriptor.UNKNOWN_LENGTH,
        )
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        when (val document = DocumentId.parse(documentId)) {
            is DocumentId.Collection -> parentDocumentId == DocumentId.ROOT && document.name == DocumentId.ALBUMS
            is DocumentId.Album -> parentDocumentId == DocumentId.collection(DocumentId.ALBUMS)
            is DocumentId.Asset -> when (document.albumId) {
                DocumentId.RECENTS -> parentDocumentId == DocumentId.ROOT || parentDocumentId == DocumentId.collection(DocumentId.RECENTS)
                else -> parentDocumentId == DocumentId.album(document.albumId)
            }
            else -> false
        }

    private fun documentCursor(projection: Array<String>?): MatrixCursor = MatrixCursor(resolveDocumentProjection(projection))

    private fun addRootRow(cursor: MatrixCursor) {
        cursor.newRow()
            .add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentId.ROOT)
            .add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "Immich")
            .add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
            .add(DocumentsContract.Document.COLUMN_FLAGS, TIMELINE_DIRECTORY_FLAGS)
    }

    private fun addAlbumRow(cursor: MatrixCursor, album: ImmichAlbum) {
        cursor.newRow()
            .add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentId.album(album.id))
            .add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, album.name)
            .add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
            .add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            .add(DocumentsContract.Document.COLUMN_SIZE, album.assetCount)
    }

    private fun addCollectionRow(cursor: MatrixCursor, name: String) {
        val displayName = when (name) {
            DocumentId.RECENTS -> "Recents"
            DocumentId.ALBUMS -> "Albums"
            else -> throw IllegalArgumentException("Collection inconnue.")
        }
        cursor.newRow()
            .add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentId.collection(name))
            .add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
            .add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
            .add(
                DocumentsContract.Document.COLUMN_FLAGS,
                if (name == DocumentId.RECENTS) TIMELINE_DIRECTORY_FLAGS else 0,
            )
    }

    private fun addAssetRow(cursor: MatrixCursor, parentId: String, asset: ImmichAsset) {
        cursor.newRow()
            .add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentId.asset(parentId, asset.id))
            .add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, asset.name)
            .add(DocumentsContract.Document.COLUMN_MIME_TYPE, asset.mimeType)
            .add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL)
            .add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, asset.createdAtMillis)
            .add(DocumentsContract.Document.COLUMN_SIZE, asset.sizeBytes)
    }

    private fun cacheFile(kind: String): File = File.createTempFile("immich-$kind-", ".cache", requireNotNull(context).cacheDir)

    private fun resolveRootProjection(projection: Array<String>?): Array<String> = projection ?: DEFAULT_ROOT_PROJECTION

    private fun resolveDocumentProjection(projection: Array<String>?): Array<String> = projection ?: DEFAULT_DOCUMENT_PROJECTION

    private companion object {
        const val DEFAULT_PAGE_SIZE = 250
        const val QUERY_ARG_OFFSET = "android:query-arg-offset"
        const val QUERY_ARG_LIMIT = "android:query-arg-limit"
        const val TIMELINE_DIRECTORY_FLAGS =
            DocumentsContract.Document.FLAG_DIR_PREFERS_GRID or
                DocumentsContract.Document.FLAG_DIR_PREFERS_LAST_MODIFIED

        val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
        )
        val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}
