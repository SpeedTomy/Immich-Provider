package app.immich.provider.core

import java.net.URI

object ServerUrl {
    fun normalize(value: String): String {
        val candidate = value.trim().let { input ->
            if ("://" in input) input else "https://$input"
        }
        val uri = URI(candidate)
        require(uri.scheme == "https" || uri.scheme == "http") { "L'URL doit utiliser HTTP ou HTTPS." }
        require(!uri.host.isNullOrBlank()) { "L'URL doit inclure un nom d'hote." }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "L'URL ne doit pas contenir d'identifiants, de requete ou de fragment."
        }

        return uri.toString().trimEnd('/')
    }

    fun host(value: String): String = URI(value).host
        ?: throw IllegalArgumentException("L'URL doit inclure un nom d'hote.")
}
