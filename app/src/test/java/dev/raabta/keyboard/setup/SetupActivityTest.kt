package dev.raabta.keyboard.setup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import dev.raabta.keyboard.voice.VoiceSettings
import dev.raabta.keyboard.ui.KeyboardPreferences
import dev.raabta.keyboard.ui.KeyboardHeight
import dev.raabta.keyboard.ui.KeyboardTheme
import android.widget.RadioButton
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import dev.raabta.keyboard.R

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28])
class SetupActivityTest {
    @Test fun setupLaunchesAndOpensAndroidKeyboardSettings() {
        val controller = Robolectric.buildActivity(SetupActivity::class.java).setup()
        val activity = controller.get()
        val button = findButton(activity.window.decorView, activity.getString(R.string.enable_keyboard))
        assertNotNull(button)
        button!!.performClick()
        assertEquals(Settings.ACTION_INPUT_METHOD_SETTINGS, shadowOf(activity).nextStartedActivity.action)
        controller.pause().stop().destroy()
    }

    @Test fun testTextIsNotSavedAndIsClearedOnClose() {
        val controller = Robolectric.buildActivity(SetupActivity::class.java).setup()
        val field = controller.get().findViewById<EditText>(R.id.test_message)
        assertFalse(field.isSaveEnabled)
        field.setText("private test")
        controller.pause().stop().destroy()
        assertEquals("", field.text.toString())
    }

    @Test fun manifestRegistersProtectedImeAndRequestsNoNetworkPermission() {
        val controller = Robolectric.buildActivity(SetupActivity::class.java).setup()
        val activity = controller.get()
        val manager = activity.packageManager
        val services = manager.queryIntentServices(Intent("android.view.InputMethod").setPackage(activity.packageName), PackageManager.GET_META_DATA)
        assertEquals(1, services.size)
        val service = services.single().serviceInfo
        assertEquals(Manifest.permission.BIND_INPUT_METHOD, service.permission)
        assertTrue(service.exported)
        assertTrue(service.metaData.containsKey("android.view.im"))
        assertEquals(PackageManager.PERMISSION_DENIED, manager.checkPermission(Manifest.permission.INTERNET, activity.packageName))
        controller.pause().stop().destroy()
    }

    @Test fun systemSpeechIsDefaultAndOnDeviceModeIsAnExplicitPersistentChoice() {
        val controller = Robolectric.buildActivity(SetupActivity::class.java).setup()
        val activity = controller.get()
        assertFalse(VoiceSettings.onDeviceOnly(activity))
        val toggle = activity.window.decorView.findViewWithTag<Switch>("OnDeviceOnly")
        assertFalse(toggle.isChecked)
        toggle.performClick()
        assertTrue(VoiceSettings.onDeviceOnly(activity))
        controller.pause().stop().destroy()
        val reopened = Robolectric.buildActivity(SetupActivity::class.java).setup()
        assertTrue(reopened.get().window.decorView.findViewWithTag<Switch>("OnDeviceOnly").isChecked)
        reopened.pause().stop().destroy()
    }

    @Test fun customizationControlsSaveOnlyPresentationChoices() {
        val controller = Robolectric.buildActivity(SetupActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.window.decorView
        root.findViewWithTag<Switch>("NumberRow").performClick()
        root.findViewWithTag<Switch>("Vibration").performClick()
        root.findViewWithTag<Switch>("Sound").performClick()
        root.findViewWithTag<RadioButton>("Height:TALL").performClick()
        assertEquals(KeyboardPreferences(KeyboardTheme.SYSTEM, KeyboardHeight.TALL, true, false, true), KeyboardPreferences.load(activity))
        root.findViewWithTag<RadioButton>("Theme:DARK").performClick()
        assertEquals(KeyboardTheme.DARK, KeyboardPreferences.load(activity).theme)
        controller.pause().stop().destroy()
    }

    private fun findButton(view: android.view.View, text: String): Button? {
        if (view is Button && view.text.toString() == text) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) findButton(view.getChildAt(i), text)?.let { return it }
        }
        return null
    }
}
