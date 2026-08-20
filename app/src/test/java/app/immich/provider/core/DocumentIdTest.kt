package app.immich.provider.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentIdTest {
    @Test
    fun `album identifiers round-trip`() {
        val documentId = DocumentId.album("album/with spaces")

        assertEquals(DocumentId.Album("album/with spaces"), DocumentId.parse(documentId))
    }

    @Test
    fun `asset identifiers keep their containing album`() {
        val documentId = DocumentId.asset("album-id", "asset-id")

        assertEquals(DocumentId.Asset("album-id", "asset-id"), DocumentId.parse(documentId))
    }

    @Test
    fun `recent collection has a stable identifier`() {
        assertEquals(DocumentId.Collection(DocumentId.RECENTS), DocumentId.parse(DocumentId.collection(DocumentId.RECENTS)))
    }

    @Test
    fun `malformed identifiers are rejected`() {
        assertEquals(null, DocumentId.parse("asset.not-base64"))
    }
}
