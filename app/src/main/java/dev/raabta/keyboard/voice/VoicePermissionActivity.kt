package dev.raabta.keyboard.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ui.dp
import dev.raabta.keyboard.ui.Palette

/** Permission is requested from an activity; granting it never starts recording automatically. */
class VoicePermissionActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var allow: Button
    private var requested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (Palette.isDark(this)) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)
        super.onCreate(savedInstanceState)
        requested = savedInstanceState?.getBoolean("requested") ?: false
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        content.addView(TextView(this).apply { setText(R.string.voice_permission_title); textSize = 22f })
        content.addView(TextView(this).apply { setText(R.string.voice_privacy); setPadding(0, dp(16), 0, dp(16)) })
        status = TextView(this)
        content.addView(status)
        allow = Button(this).apply {
            setText(R.string.voice_allow)
            isAllCaps = false
            setOnClickListener {
                requested = true
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            }
        }
        content.addView(allow)
        content.addView(Button(this).apply {
            setText(R.string.voice_permission_settings)
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        })
        content.addView(Button(this).apply {
            setText(R.string.voice_return)
            isAllCaps = false
            setOnClickListener { finish() }
        })
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onResume() {
        super.onResume()
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        status.setText(if (granted) R.string.voice_permission_granted else if (requested) R.string.voice_permission_denied else R.string.voice_permission_explanation)
        allow.isEnabled = !granted
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            status.setText(if (granted) R.string.voice_permission_granted else R.string.voice_permission_denied)
            allow.isEnabled = !granted
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("requested", requested)
        super.onSaveInstanceState(outState)
    }

    companion object { private const val REQUEST_AUDIO = 100 }
}
