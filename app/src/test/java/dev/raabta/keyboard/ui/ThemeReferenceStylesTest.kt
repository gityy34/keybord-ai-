package dev.raabta.keyboard.ui

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import dev.raabta.keyboard.setup.KeyboardSettingsActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-port")
class ThemeReferenceStylesTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun referenceStylesAreVisibleAndOpenAFullPreview() {
        KeyboardPreferences.save(context, KeyboardPreferences(theme = KeyboardTheme.MIDNIGHT))
        val activity = Robolectric.buildActivity(KeyboardSettingsActivity::class.java).setup().get()
        val picker = ThemePicker(activity)
        val section = picker.findViewWithTag<View>("ReferenceThemes")
        assertNotNull(section)
        val names = listOf("reference-black", "reference-white", "reference-neon")
        names.forEach { id ->
            val card = picker.findViewWithTag<View>("choice:Theme:$id")
            assertNotNull("missing reference theme $id", card)
            assertTrue(card.isClickable)
        }
        picker.findViewWithTag<View>("choice:Theme:reference-black").performClick()
        assertNotNull(picker.findViewWithTag<View>("ThemeDetail"))
        assertNotNull(picker.findViewWithTag<View>("ThemeBorders"))
        assertNotNull(picker.findViewWithTag<View>("ThemeCancel"))
        assertNotNull(picker.findViewWithTag<View>("ThemeApply"))
        assertEquals(KeyboardTheme.MIDNIGHT, KeyboardPreferences.load(context).theme)
        activity.finish()
    }
}
