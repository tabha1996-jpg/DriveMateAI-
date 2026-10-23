package com.drivemate.ai

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.InputType
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.Locale

/** Guided V1. No live charger/restaurant provider or Tata telemetry is connected. */
class DrivingActivity : FragmentActivity() {
    private lateinit var question: EditText
    private lateinit var destination: EditText
    private lateinit var soc: EditText
    private lateinit var rangeKm: EditText
    private lateinit var answer: TextView
    private lateinit var controls: MutableList<Button>
    private var unlocked = false
    private var tts: TextToSpeech? = null

    private val recognizer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (unlocked && result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull().orEmpty()
            question.setText(text)
            ask()
        }
    }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && unlocked) listen()
        else if (!granted) reply("Microphone permission nahi mili. Command type kar sakte ho.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) tts?.language = Locale("en", "IN")
        }
        controls = mutableListOf()
        val prefs = getSharedPreferences("trip", MODE_PRIVATE)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (18 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        column.addView(TextView(this).apply { text = "DriveMate • Driving"; textSize = 27f })
        column.addView(TextView(this).apply {
            text = "V2 • provider app consent + Maps / voice • no live gun feed"
            textSize = 14f
        })
        destination = field(column, "Destination (e.g. Kolkata)", prefs.getString("destination", "").orEmpty())
        soc = field(column, "Battery % (manual)", prefs.getString("soc", "").orEmpty(), true)
        rangeKm = field(column, "Search limit in km (default 20)", prefs.getString("radius", "20").orEmpty(), true)
        question = field(column, "Bolo: 30 percent battery, 20 km mein DC charger aur restaurant", "")
        button(column, "🎙 Speak Hinglish") {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED) listen()
            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        button(column, "Ask DriveMate") { ask() }
        button(column, "Open route to destination") { navigate() }
        button(column, "Search DC chargers in Maps") { findChargers() }
        button(column, "EV Apps • Request launch access (V2)") {
            startActivity(Intent(this, ProviderAccessActivity::class.java))
        }
        button(column, "All charging networks • connection status") {
            val registry = ChargingProviderRegistry()
            reply("Ek jagah sab providers ka status:\n" + registry.connectionSummary() +
                "\n\nAbhi koi provider-level gun feed connected nahi hai. " +
                "Generic Google Maps charger search sab networks ke results la sakta hai, " +
                "lekin har connector ka live status automatically confirm nahi karta.")
        }
        button(column, "Search restaurants in Maps") { findRestaurants() }
        button(column, "Offline AI (experimental)") {
            startActivity(Intent(this, MainActivity::class.java))
        }
        answer = TextView(this).apply {
            text = "Locked. Unlock to use DriveMate."
            textSize = 17f
            setPadding(0, 18, 0, 22)
        }
        column.addView(answer)
        column.addView(TextView(this).apply {
            text = "Important: Google Maps search is NOT a verified same-side, 20-km-ahead or availability filter. Check legal highway entry, actual detour and individual gun status before deciding. Never use the phone while driving. Payments are out of scope."
            textSize = 13f
        })
        setContentView(ScrollView(this).apply { addView(column) })
        lockControls()
        authenticate()
    }

    private fun field(parent: LinearLayout, placeholder: String, value: String, number: Boolean = false) =
        EditText(this).also {
            it.hint = placeholder
            it.setText(value)
            if (number) it.inputType = InputType.TYPE_CLASS_NUMBER
            parent.addView(it)
        }

    private fun button(parent: LinearLayout, title: String, action: () -> Unit) {
        val b = Button(this).apply { text = title; setOnClickListener { if (unlocked) action() } }
        parent.addView(b)
        controls.add(b)
    }

    private fun lockControls() {
        controls.forEach { it.isEnabled = unlocked }
        destination.isEnabled = unlocked
        soc.isEnabled = unlocked
        rangeKm.isEnabled = unlocked
        question.isEnabled = unlocked
    }

    private fun authenticate() {
        val methods = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(methods) != BiometricManager.BIOMETRIC_SUCCESS) {
            answer.text = "Device screen lock / biometrics required. Driving mode locked."
            return
        }
        BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                    lockControls()
                    answer.text = "Unlocked. Battery, destination aur request batao."
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    unlocked = false
                    lockControls()
                    answer.text = "Unlock cancelled. Restart to retry."
                }
            }).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock DriveMate")
                    .setSubtitle("Your trip details stay on this device")
                    .setAllowedAuthenticators(methods)
                    .build()
            )
    }

    private fun saveTrip() {
        getSharedPreferences("trip", MODE_PRIVATE).edit()
            .putString("destination", destination.text.toString().trim())
            .putString("soc", soc.text.toString().trim())
            .putString("radius", rangeKm.text.toString().trim())
            .apply()
    }

    private fun listen() {
        try {
            recognizer.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "DriveMate se Hinglish mein baat karo")
            })
        } catch (_: ActivityNotFoundException) {
            reply("Speech recognizer phone par available nahi hai. Type karke Ask dabao.")
        } catch (_: SecurityException) {
            reply("Mic access blocked hai. App settings mein permission allow karo.")
        }
    }

    private fun ask() {
        val text = question.text.toString().trim()
        if (text.isEmpty()) { reply("Charger, restaurant ya route kya chahiye?"); return }
        Regex("(\\d{1,3})\\s*(?:%|percent|per cent)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 }
            ?.let { soc.setText(it.toString()) }
        Regex("(\\d{1,3})\\s*(?:km|kilometre|kilometer|kilometres|kilometers)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..200 }
            ?.let { rangeKm.setText(it.toString()) }
        saveTrip()
        val providerRequest = listOf("app", "application", "kholo", "open", "access", "permission", "connect")
            .any { text.contains(it, true) }
        val provider = when {
            text.contains("jio", true) || text.contains("pulse", true) -> "Jio"
            text.contains("indian oil", true) || text.contains("indianoil", true) || text.contains("iocl", true) -> "IndianOil"
            text.contains("bpcl", true) || text.contains("bharat petroleum", true) -> "BPCL"
            text.contains("hpcl", true) || text.contains("hp echarge", true) -> "HP"
            text.contains("tata motors", true) || text.contains("ira", true) -> "iRA"
            text.contains("tata power", true) || text.contains("ez charge", true) -> "Tata Power"
            else -> null
        }
        if (providerRequest && provider != null) {
            reply("${provider} app open karne ke liye pehle permission puchunga. " +
                "Ye app launch permission hai, private charger gun data ka access nahi.")
            startActivity(Intent(this, ProviderAccessActivity::class.java).putExtra("provider", provider))
            return
        }
        val charger = listOf("charg", "dc", "चार्ज").any { text.contains(it, true) }
        val food = listOf("restaurant", "food", "khana", "khaana", "hotel", "खाना", "भूख")
            .any { text.contains(it, true) }
        val route = listOf("navigate", "route", "direction", "raasta", "रास्ता")
            .any { text.contains(it, true) }
        when {
            charger -> findChargers(food)
            food -> findRestaurants()
            route -> navigate()
            text.contains("battery", true) || text.contains("percent", true) ->
                reply("Tumhari entered battery ${batteryText()} hai. Tata iRA.ev live feed connected nahi hai, isliye actual battery main independently confirm nahi kar sakta.")
            else -> reply("V2 mein navigation, Maps search aur approved provider app launching available hai. Live connector feeds abhi connected nahi hain.")
        }
    }

    private fun batteryText(): String =
        soc.text.toString().toIntOrNull()?.takeIf { it in 0..100 }?.let { "${it}%" }
            ?: "unknown"

    private fun radiusText(): Int =
        rangeKm.text.toString().toIntOrNull()?.takeIf { it in 1..200 } ?: 20

    private fun findChargers(alsoFood: Boolean = false) {
        saveTrip()
        val restaurantNote = if (alsoFood) " Restaurant ke liye neeche separate search button hai." else ""
        reply("Battery ${batteryText()}, requested radius ${radiusText()} km. Main Maps mein CCS2 DC chargers ka search khol raha hoon. " +
            "Dhyan rahe: yeh search sirf location-based hai; ${radiusText()} km aage, same-side, detour aur gun availability verified nahi hain." +
            restaurantNote + " Unverified charger ko AVAILABLE nahi maanenge.")
        openSearch("CCS2 DC fast charging station")
    }

    private fun findRestaurants() {
        saveTrip()
        reply("Maps mein restaurant search khol raha hoon. 4.2+ rating ke saath substantial review count prefer karenge. " +
            "Highway ke same-side access, actual driving distance aur detour ko confirm karna zaroori hai. V1 yeh automatic check nahi karta.")
        openSearch("restaurants near me")
    }

    private fun navigate() {
        saveTrip()
        val dest = destination.text.toString().trim()
        if (dest.isBlank()) {
            reply("Destination field mein city ya address likho, phir route kholunga.")
            return
        }
        reply("${dest} ke liye Google Maps driving directions khol raha hoon.")
        val url = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(dest)}&travelmode=driving")
        openMaps(url, url)
    }

    private fun openSearch(text: String) {
        openMaps(Uri.parse("geo:0,0?q=${Uri.encode(text)}"),
            Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(text)}"))
    }

    private fun openMaps(preferred: Uri, webFallback: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, preferred).setPackage("com.google.android.apps.maps"))
        } catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, webFallback)) }
            catch (_: Exception) { reply("Maps/browser nahi khul raha. Google Maps install karke retry karo.") }
        }
    }

    private fun reply(message: String) {
        answer.text = message
        if (unlocked) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "drive-reply")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
