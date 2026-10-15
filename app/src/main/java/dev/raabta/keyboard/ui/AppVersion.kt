package dev.raabta.keyboard.ui

import android.content.Context

/** Reads the installed package version so About screens always match the APK being run. */
@Suppress("DEPRECATION")
fun Context.appVersionName(): String = runCatching {
    packageManager.getPackageInfo(packageName, 0).versionName
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"
