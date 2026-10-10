package dev.raabta.keyboard.setup

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.TextView
import dev.raabta.keyboard.ui.KeyboardOptions

/** Only explicit settings toggles request optional access. Denial turns the setting back off. */
class OptionalPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val key=intent.getStringExtra("setting") ?: run { finish(); return }
        val permission=if(key=="contacts") Manifest.permission.READ_CONTACTS else imagePermission()
        setContentView(TextView(this).apply { text=if(key=="contacts") "Optional local contact suggestions" else "Optional recent screenshot previews" })
        if(if(key=="contacts") checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED else hasImageAccess(this)) finish()
        else if(savedInstanceState==null) requestPermissions(if(key!="contacts" && Build.VERSION.SDK_INT>=34) arrayOf(permission,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else arrayOf(permission),101)
    }
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        intent.getStringExtra("setting")?.let { KeyboardOptions(this).set(it,results.any { it==PackageManager.PERMISSION_GRANTED }) }
        finish()
    }
    companion object {
        fun hasImageAccess(context:android.content.Context) = context.checkSelfPermission(imagePermission())==PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT>=34 && context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED)
        fun imagePermission() = if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
