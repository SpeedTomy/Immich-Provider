package app.immich.provider

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.immich.provider.settings.ImmichSettings

class MainActivity : Activity() {
    private lateinit var settings: ImmichSettings
    private lateinit var serverUrl: EditText
    private lateinit var apiKey: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = ImmichSettings(this)

        val padding = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val title = TextView(this).apply {
            text = "Immich Provider"
            textSize = 26f
        }
        val description = TextView(this).apply {
            text = "Expose vos albums Immich dans le selecteur de fichiers Android."
            setPadding(0, padding / 2, 0, padding)
        }
        serverUrl = EditText(this).apply {
            hint = "URL du serveur Immich"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        apiKey = EditText(this).apply {
            hint = "Cle API Immich"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val save = Button(this).apply {
            text = "Enregistrer"
            setOnClickListener { saveSettings() }
        }
        status = TextView(this)

        listOf(title, description, serverUrl, apiKey, save, status).forEach { view ->
            layout.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(layout)
        refreshFromSettings()
    }

    override fun onResume() {
        super.onResume()
        refreshFromSettings()
    }

    private fun saveSettings() {
        try {
            settings.save(serverUrl.text.toString(), apiKey.text.toString())
            apiKey.text?.clear()
            status.text = "Configuration enregistree. Immich est disponible dans le selecteur de fichiers."
        } catch (error: IllegalArgumentException) {
            status.text = error.message
        }
    }

    private fun refreshFromSettings() {
        val credentials = settings.load()
        if (credentials == null) {
            status.text = "Renseignez l'URL et la cle API de votre serveur."
            return
        }
        serverUrl.setText(credentials.serverUrl)
        status.text = "Serveur configure: ${credentials.serverUrl}"
    }
}
