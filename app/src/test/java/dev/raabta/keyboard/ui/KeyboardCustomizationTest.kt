package dev.raabta.keyboard.ui

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ime.KeyboardState
import dev.raabta.keyboard.ime.KeyboardView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28])
class KeyboardCustomizationTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun preferencesPersistAndExplicitThemesOverrideSystemAppearance() {
        val dark = KeyboardPreferences(theme = KeyboardTheme.DARK, height = KeyboardHeight.TALL, numberRow = true, vibration = false, sound = true)
        KeyboardPreferences.save(context, dark)
        assertEquals(dark, KeyboardPreferences.load(context))
        assertTrue(Palette.isDark(context))
        val darkBackground = Palette.from(context).background
        KeyboardPreferences.save(context, dark.copy(theme = KeyboardTheme.LIGHT))
        assertFalse(Palette.isDark(context))
        assertNotEquals(darkBackground, Palette.from(context).background)
    }

    @Test @Config(qualifiers = "night") fun systemThemeFollowsPhoneNightModeAndInvalidValuesHaveSafeDefaults() {
        assertTrue(Palette.isDark(context))
        context.getSharedPreferences("keyboard_preferences", 0).edit().putString("theme", "invalid").putString("height", "invalid").apply()
        assertEquals(KeyboardTheme.SYSTEM, KeyboardPreferences.load(context).theme)
        assertEquals(KeyboardHeight.NORMAL, KeyboardPreferences.load(context).height)
        assertTrue(Palette.isDark(context))
    }

    private class Device : FeedbackDevice {
        var vibrations = 0
        val sounds = mutableListOf<FeedbackKey>()
        var fail = false
        override fun vibrate(view: View) { vibrations++; if (fail) throw IllegalStateException("no hardware") }
        override fun sound(key: FeedbackKey) { sounds.add(key); if (fail) throw IllegalStateException("audio unavailable") }
    }

    @Test fun feedbackTogglesOperateIndependentlyAndDefaultSoundIsOff() {
        val device = Device()
        KeyFeedback(context, KeyboardPreferences(), device).perform(View(context))
        assertEquals(1, device.vibrations)
        assertTrue(device.sounds.isEmpty())
        KeyFeedback(context, KeyboardPreferences(vibration = false, sound = true), device).perform(View(context), FeedbackKey.DELETE)
        assertEquals(1, device.vibrations)
        assertEquals(listOf(FeedbackKey.DELETE), device.sounds)
        KeyFeedback(context, KeyboardPreferences(vibration = false), device).perform(View(context))
        assertEquals(1, device.vibrations)
    }

    @Test fun hardwareFailuresDoNotBlockKeyCallbackOrRepeatFailingFeedback() {
        val device = Device().apply { fail = true }
        val feedback = KeyFeedback(context, KeyboardPreferences(sound = true), device)
        val typed = StringBuilder()
        val key = Button(context).apply { setOnClickListener { feedback.perform(this); typed.append('a') } }
        repeat(30) { key.performClick() }
        assertEquals("a".repeat(30), typed.toString())
        assertEquals(1, device.vibrations)
        assertEquals(1, device.sounds.size)
    }

    @Test @Config(qualifiers = "w320dp-h640dp-port") fun portraitNumberRowFitsAndLiveHeightScaleChangesMeasuredKeyboard() {
        val heights = listOf(75, 100, 135).map { scale ->
            context.getSharedPreferences("keyboard_options", 0).edit().putInt("height_scale", scale).commit()
            KeyboardPreferences.save(context, KeyboardPreferences(numberRow = true))
            val commands = mutableListOf<dev.raabta.keyboard.ime.Key>()
            val view = KeyboardView(context, KeyboardState(), commands::add)
            view.render(R.string.key_enter)
            measure(view, 320)
            checkButtonsFit(view)
            view.findViewWithTag<View>("text:1").performClick()
            assertEquals(listOf(dev.raabta.keyboard.ime.Key.Text("1")), commands)
            view.measuredHeight
        }
        assertTrue(heights[0] < heights[1])
        assertTrue(heights[1] < heights[2])
    }

    @Test @Config(qualifiers = "w1200dp-h700dp-land") fun wideLandscapeKeepsEveryKeyInsideCenteredReachableContent() {
        val view = KeyboardView(context)
        view.render(R.string.key_search)
        measure(view, 1200)
        checkButtonsFit(view)
        val first = view.findViewWithTag<View>("text:q")
        val last = view.findViewWithTag<View>("text:p")
        val bounds = Rect(0, 0, last.width, last.height)
        view.offsetDescendantRectToMyCoords(last, bounds)
        assertTrue(bounds.right < view.width)
        assertTrue(first.width > 0)
        assertTrue(view.matchesCurrentPreferences())
        KeyboardPreferences.save(context, KeyboardPreferences(theme = KeyboardTheme.DARK))
        assertFalse(view.matchesCurrentPreferences())
    }

    private fun measure(view: View, width: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(context.dp(width), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun checkButtonsFit(root: ViewGroup) {
        fun visit(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (view is Button && view !is dev.raabta.keyboard.ime.ToolbarButton) {
                val bounds = Rect(0, 0, view.width, view.height)
                root.offsetDescendantRectToMyCoords(view, bounds)
                assertTrue("Key outside keyboard: ${view.tag}, $bounds in ${root.width}×${root.height}", bounds.left >= 0 && bounds.top >= 0 && bounds.right <= root.width && bounds.bottom <= root.height)
                assertTrue(view.width > 0 && view.height > 0)
                assertFalse(view.isSoundEffectsEnabled)
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
    }
}
