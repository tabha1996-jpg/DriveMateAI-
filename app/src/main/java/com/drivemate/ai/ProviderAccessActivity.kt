package com.drivemate.ai

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity

/**
 * User-consented launch access for named EV apps.
 * Opening another app does NOT authorize reading its private data, sessions or guns.
 * No AccessibilityService, credentials, UPI, bank, screen scraping or hidden automation.
 */
class ProviderAccessActivity : FragmentActivity() {
    private data class ProviderApp(
        val label: String,
        val packages: List<String>,
        val description: String
    )

    private val providers = listOf(
        ProviderApp("HP eCharge", listOf("com.hpcl.ev"), "HPCL charging stations"),
        ProviderApp("IndianOil e-Charge", listOf("com.iocl.ev"), "IndianOil charging stations"),
        ProviderApp("BPCL eDrive", listOf("com.edrive.bpcl"), "BPCL charging stations"),
        ProviderApp("Tata Power EZ Charge", listOf("com.tatapower.evapp"), "Tata Power charging stations"),
        ProviderApp(
            "Jio-bp pulse",
            listOf("com.jiobp.pulse_charge", "com.namp.jiobppulse"),
            "Jio-bp pulse Charge Pro / older installed pulse app"
        ),
        ProviderApp("Tata Motors iRA.ev", listOf("com.tatamotors.evoneapp"), "Vehicle status and Tata EV tools")
    )

    private val prefs by lazy { getSharedPreferences("provider_launch_approvals", MODE_PRIVATE) }
    private lateinit var list: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (18 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        root.addView(TextView(this).apply {
            text = "Charging Apps • V2"
            textSize = 25f
        })
        root.addView(TextView(this).apply {
            text = "One dashboard for provider app launching. Each provider opens ONLY after your consent. This is not permission to read gun status, charging history or private data."
            textSize = 15f
        })
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 10, 0, 10)
        }
        root.addView(status)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        root.addView(Button(this).apply {
            text = "Revoke ALL saved launch approvals"
            setOnClickListener {
                AlertDialog.Builder(this@ProviderAccessActivity)
                    .setTitle("Revoke saved approvals?")
                    .setMessage("This resets only DriveMate's remembered launch decisions. It cannot alter permissions inside the provider apps.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Revoke") { _, _ ->
                        prefs.edit().clear().apply()
                        renderProviders()
                    }.show()
            }
        })
        root.addView(TextView(this).apply {
            text = "Gun 1 / Gun 2 = UNVERIFIED in DriveMate until provider-approved live APIs exist. Provider apps can contain payment screens, but DriveMate never operates those screens or reads their credentials. Share from a provider app to DriveMate only when the app offers Android text sharing; shared information is user-supplied, not a verified live feed."
            textSize = 13f
        })
        setContentView(ScrollView(this).apply { addView(root) })
        renderProviders()
        val requested = intent.getStringExtra("provider")
        if (!requested.isNullOrBlank()) {
            val candidate = providers.firstOrNull { p ->
                p.label.contains(requested, ignoreCase = true) ||
                    (requested.equals("HP", true) && p.label.startsWith("HP")) ||
                    (requested.equals("Jio", true) && p.label.startsWith("Jio")) ||
                    (requested.equals("Tata", true) && p.label.startsWith("Tata Power"))
            }
            if (candidate != null) {
                val installed = installedPackage(candidate)
                if (installed != null) requestLaunchConsent(candidate, installed)
                else confirmInstall(candidate)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) renderProviders()
    }

    private fun installedPackage(provider: ProviderApp): String? =
        provider.packages.firstOrNull { packageManager.getLaunchIntentForPackage(it) != null }

    private fun renderProviders() {
        list.removeAllViews()
        var count = 0
        for (p in providers) {
            val installed = installedPackage(p)
            if (installed != null) count++
            val approved = installed != null && prefs.getBoolean(installed, false)
            list.addView(TextView(this).apply {
                text = "\n${p.label}\n" +
                    if (installed == null) "Not installed • live feed NOT connected"
                    else "Installed • launch ${if (approved) "approved" else "requires consent"} • live feed NOT connected"
                textSize = 15f
            })
            list.addView(Button(this).apply {
                text = if (installed == null) "Install / view ${p.label} on Play Store"
                    else "Open ${p.label}"
                setOnClickListener {
                    if (installed == null) {
                        confirmInstall(p)
                    } else {
                        requestLaunchConsent(p, installed)
                    }
                }
            })
        }
        status.text = "$count of ${providers.size} supported apps installed. Data connections: 0 live verified. No provider can be marked available from app installation alone."
    }

    private fun confirmInstall(provider: ProviderApp) {
        AlertDialog.Builder(this)
            .setTitle("Open Google Play?")
            .setMessage("This opens the official listing for ${provider.label}. Installing it does not grant DriveMate access to its charging data.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open Play Store") { _, _ ->
                val id = provider.packages.first()
                val market = Uri.parse("market://details?id=$id")
                val web = Uri.parse("https://play.google.com/store/apps/details?id=$id")
                try { startActivity(Intent(Intent.ACTION_VIEW, market).setPackage("com.android.vending")) }
                catch (_: Exception) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, web)) }
                    catch (_: Exception) { status.text = "Play Store/browser unavailable." }
                }
            }.show()
    }

    private fun requestLaunchConsent(provider: ProviderApp, installedPackage: String) {
        if (prefs.getBoolean(installedPackage, false)) {
            launch(provider, installedPackage)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Allow DriveMate to open ${provider.label}?")
            .setMessage(
                "Scope: launch ${provider.label} only, when you tap its button. " +
                "DriveMate will NOT read private data, Gun 1/Gun 2, passwords, wallets, OTPs, payments or screens. " +
                "Android does not provide a general 'grant private app access' permission. " +
                "You can approve once or remember this app-launch choice."
            )
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Remember approval") { _, _ ->
                prefs.edit().putBoolean(installedPackage, true).apply()
                launch(provider, installedPackage)
                renderProviders()
            }
            .setPositiveButton("Open once") { _, _ ->
                launch(provider, installedPackage)
            }.show()
    }

    private fun launch(provider: ProviderApp, packageId: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageId)
        if (intent == null) {
            status.text = "${provider.label} is no longer installed. Refreshing."
            renderProviders()
            return
        }
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { status.text = "Could not launch ${provider.label}." }
        catch (_: SecurityException) { status.text = "Android blocked ${provider.label} from opening." }
    }
}
