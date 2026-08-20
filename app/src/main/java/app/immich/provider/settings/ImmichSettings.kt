package app.immich.provider.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.immich.provider.core.ServerUrl

data class ImmichCredentials(val serverUrl: String, val apiKey: String)

class ImmichSettings(context: Context) {
    private val appContext = context.applicationContext
    private val serverPreferences = appContext.getSharedPreferences(SERVER_PREFERENCES, Context.MODE_PRIVATE)
    private val credentialPreferences = EncryptedSharedPreferences.create(
        appContext,
        CREDENTIAL_PREFERENCES,
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): ImmichCredentials? {
        val serverUrl = loadServerUrl() ?: return null
        val apiKey = credentialPreferences.getString(KEY_API_KEY, null) ?: return null
        return ImmichCredentials(serverUrl, apiKey)
    }

    fun loadServerUrl(): String? = serverPreferences.getString(KEY_SERVER_URL, null)

    fun save(serverUrl: String, apiKey: String) {
        require(apiKey.isNotBlank()) { "La cle API est obligatoire." }
        val normalizedServerUrl = ServerUrl.normalize(serverUrl)
        serverPreferences.edit().putString(KEY_SERVER_URL, normalizedServerUrl).apply()
        credentialPreferences.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    companion object {
        private const val SERVER_PREFERENCES = "immich_server"
        private const val CREDENTIAL_PREFERENCES = "immich_credentials"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"
    }
}
