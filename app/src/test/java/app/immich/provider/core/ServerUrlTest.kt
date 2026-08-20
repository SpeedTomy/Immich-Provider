package app.immich.provider.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlTest {
    @Test
    fun `normalizes a bare server address`() {
        assertEquals("https://immich.example.com", ServerUrl.normalize(" immich.example.com/ "))
    }

    @Test
    fun `keeps a local http server`() {
        assertEquals("http://192.168.1.20:2283", ServerUrl.normalize("http://192.168.1.20:2283/"))
    }

    @Test
    fun `extracts the server ip without port`() {
        assertEquals("192.168.1.20", ServerUrl.host("http://192.168.1.20:2283"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects URL query parameters`() {
        ServerUrl.normalize("https://immich.example.com?token=secret")
    }
}
