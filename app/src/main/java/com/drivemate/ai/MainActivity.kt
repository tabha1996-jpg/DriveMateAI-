package com.drivemate.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : FragmentActivity() {
    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var ask: Button
    private lateinit var speak: Button
    private lateinit var maps: Button
    private lateinit var importModel: Button
    private lateinit var ai: OfflineAiEngine
    private val worker = Executors.newSingleThreadExecutor()
    private var tts: TextToSpeech? = null
    private var unlocked = false
    private var ready = false

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && unlocked) importModel(uri)
    }
    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (unlocked && result.resultCode == Activity.RESULT_OK) {
            val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            input.setText(words?.firstOrNull().orEmpty())
            process()
        }
    }
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed && unlocked) listen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(8192, 8192)
        ai = OfflineAiEngine(applicationContext)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale("en", "IN")
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 55, 35, 30)
        }
        layout.addView(TextView(this).apply { text = "DriveMate AI"; textSize = 30f })
        layout.addView(TextView(this).apply { text = "Offline AI · v0.2 (experimental)"; textSize = 15f })
        input = EditText(this).apply { hint = "Ask anything in Hinglish or English"; minLines = 2 }
        layout.addView(input)
        importModel = Button(this).apply {
            text = "Import .litertlm model"
            setOnClickListener { if (unlocked) modelPicker.launch(arrayOf("*/*")) }
        }
        layout.addView(importModel)
        speak = Button(this).apply {
            text = "🎙 Speak"
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        layout.addView(speak)
        ask = Button(this).apply { text = "Ask offline AI"; setOnClickListener { process() } }
        layout.addView(ask)
        maps = Button(this).apply {
            text = "Open Google Maps"
            setOnClickListener {
                if (!unlocked) return@setOnClickListener
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(input.text.toString().ifBlank { "restaurants" })))
                intent.setPackage("com.google.android.apps.maps")
                try { startActivity(intent) } catch (_: Exception) { output.text = "Google Maps not available." }
            }
        }
        layout.addView(maps)
        output = TextView(this).apply {
            textSize = 17f
            text = "Locked. Unlock to import an offline model."
        }
        layout.addView(output)
        setContentView(ScrollView(this).apply { addView(layout) })
        updateButtons()
        authenticate()
    }

    private fun updateButtons() {
        importModel.isEnabled = unlocked
        speak.isEnabled = unlocked
        maps.isEnabled = unlocked
        ask.isEnabled = unlocked && ready
        input.isEnabled = unlocked
    }

    private fun authenticate() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            output.text = "Device authentication unavailable. App is locked."
            return
        }
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { finish() }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlocked = true
                updateButtons()
                val saved = File(filesDir, "offline-model.litertlm")
                if (saved.exists()) loadModel(saved) else output.text = "Unlocked. Import a compatible .litertlm model to start offline AI."
            }
        }).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock DriveMate AI")
                .setSubtitle("Only you can open this app")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private fun importModel(uri: Uri) {
        ready = false
        updateButtons()
        output.text = "Importing model into private storage…"
        worker.execute {
            try {
                val destination = File(filesDir, "offline-model.litertlm")
                val temporary = File(filesDir, "offline-model.tmp")
                contentResolver.openInputStream(uri).use { source ->
                    requireNotNull(source) { "Unable to open selected model" }
                    temporary.outputStream().use { target -> source.copyTo(target) }
                }
                require(temporary.length() > 0) { "Empty model file" }
                if (destination.exists()) destination.delete()
                check(temporary.renameTo(destination)) { "Unable to save model" }
                runOnUiThread { output.text = "Model imported. Loading on CPU…" }
                loadModelOnWorker(destination)
            } catch (t: Throwable) {
                runOnUiThread { output.text = "Import failed: ${t.message}" }
            }
        }
    }

    private fun loadModel(file: File) {
        ready = false
        updateButtons()
        output.text = "Loading offline model (may take a while)…"
        worker.execute { loadModelOnWorker(file) }
    }

    private fun loadModelOnWorker(file: File) {
        try {
            ai.load(file)
            runOnUiThread {
                ready = true
                updateButtons()
                output.text = "Offline AI ready. Ask anything."
            }
        } catch (t: Throwable) {
            runOnUiThread {
                ready = false
                updateButtons()
                output.text = "Model load failed: ${t.message}. Check LiteRT-LM model compatibility."
            }
        }
    }

    private fun listen() {
        if (!unlocked) return
        try {
            voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        } catch (_: Exception) {
            output.text = "Speech recognition unavailable. Type your message."
        }
    }

    private fun process() {
        if (!unlocked || !ready) return
        val question = input.text.toString().trim()
        if (question.isEmpty()) return
        ask.isEnabled = false
        output.text = "You: $question\n\nThinking offline…"
        worker.execute {
            try {
                val answer = ai.reply(question)
                runOnUiThread {
                    output.text = "You: $question\n\nDriveMate: $answer"
                    ask.isEnabled = true
                    tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "reply")
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    output.text = "AI error: ${t.message}"
                    ask.isEnabled = true
                }
            }
        }
    }

    override fun onDestroy() {
        worker.execute { ai.close() }
        worker.shutdown()
        tts?.shutdown()
        super.onDestroy()
    }
}
