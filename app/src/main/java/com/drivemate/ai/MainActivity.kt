package com.drivemate.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.net.Uri
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : FragmentActivity() {
    private lateinit var output: TextView
    private lateinit var input: EditText
    private var tts: TextToSpeech? = null
    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            input.setText(words?.firstOrNull() ?: "")
            process()
        }
    }
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> if (allowed) listen() }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(8192, 8192) // FLAG_SECURE: reduce screen capture
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("en", "IN") }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(35, 55, 35, 30) }
        layout.addView(TextView(this).apply { text = "DriveMate AI"; textSize = 30f })
        layout.addView(TextView(this).apply { text = "Private voice-first travel companion · v0.1"; textSize = 15f })
        input = EditText(this).apply { hint = "Ask for food, charging, rest stops..."; minLines = 2 }
        layout.addView(input)
        val speak = Button(this).apply { text = "🎙 Speak"; setOnClickListener { if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen() else micPermission.launch(Manifest.permission.RECORD_AUDIO) } }
        layout.addView(speak)
        layout.addView(Button(this).apply { text = "Ask"; setOnClickListener { process() } })
        layout.addView(Button(this).apply { text = "Open Google Maps"; setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(input.text.toString().ifBlank { "restaurants" }))).apply { setPackage("com.google.android.apps.maps") }) } })
        output = TextView(this).apply { textSize = 18f; text = "Ready. No cloud AI, route-reading, or external API is enabled in this starter build." }
        layout.addView(output)
        setContentView(ScrollView(this).apply { addView(layout) })
        authenticate()
    }
    private fun authenticate() {
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
            output.text = "Device authentication unavailable. App is locked."; return
        }
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { finish() }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { output.text = "Unlocked. Tell me what you need." }
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock DriveMate AI").setSubtitle("Only you can open this app").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build())
    }
    private fun listen() {
        try { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN") }) }
        catch (_: Exception) { output.text = "Speech recognition not available on this phone." }
    }
    private fun process() {
        val request = input.text.toString().trim()
        if (request.isEmpty()) return
        val reply = when {
            request.contains("charg", true) || request.contains("चार्ज") -> "Charging request understood. Live charger discovery will be added in the next build."
            request.contains("food", true) || request.contains("restaurant", true) || request.contains("भूख") || request.contains("biryani", true) -> "Food request understood. Route-aware restaurant search is not connected yet. Tap Open Google Maps for a basic search."
            else -> "I heard: $request. This starter build has local intent detection only."
        }
        output.text = reply
        tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}
