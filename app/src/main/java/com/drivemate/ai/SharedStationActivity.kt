package com.drivemate.ai

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity

/**
 * User-initiated inbound Android text share.
 * DOES NOT read a provider app, store its private information or prove live status.
 */
class SharedStationActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val incoming = if (intent?.action == Intent.ACTION_SEND &&
            intent?.type?.startsWith("text/") == true) {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        } else ""
        val preview = incoming.take(1600)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        root.addView(TextView(this).apply {
            text = "Share to DriveMate"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "You chose to share the text below. Nothing is automatically stored or transmitted by DriveMate. Do not share OTPs, passwords, payment, wallet or banking information. Shared text is NOT verified real-time charger availability."
            textSize = 16f
        })
        root.addView(TextView(this).apply {
            text = if (preview.isBlank()) "No shared text was received." else preview
            textSize = 17f
            setPadding(0, 20, 0, 20)
        })
        root.addView(Button(this).apply {
            text = "Close without saving"
            setOnClickListener { finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
