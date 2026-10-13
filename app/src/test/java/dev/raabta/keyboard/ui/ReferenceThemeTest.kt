package dev.raabta.keyboard.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ime.Candidate
import dev.raabta.keyboard.ime.Key
import dev.raabta.keyboard.ime.KeyboardView
import dev.raabta.keyboard.voice.VoiceState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], qualifiers="w360dp-h800dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReferenceThemeTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val themes = listOf(KeyboardTheme.GLASS, KeyboardTheme.CHARCOAL, KeyboardTheme.CLOUD)
    private fun keyboard(theme: KeyboardTheme, language: Int = 0, key: (Key) -> Unit = {}): KeyboardView {
        context.getSharedPreferences("keyboard_options", 0).edit().clear().commit()
        KeyboardOptions(context).setNumber("language", language)
        KeyboardPreferences.save(context, KeyboardPreferences(theme=theme))
        return KeyboardView(context, onKey=key).apply {
            render(R.string.key_enter)
            showCandidates(listOf(Candidate("the", "the"), Candidate("I", "I"), Candidate("you", "you")))
        }
    }
    private fun layout(view: View, width: Int = 360, height: Int = 500) {
        view.measure(View.MeasureSpec.makeMeasureSpec(context.dp(width), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(context.dp(height), View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
    private fun screenshot(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("/workspace/artifacts/ui-0.7.1/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun descendants(view: View): List<View> = listOf(view) + if(view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun themeSelectionPersistsWithoutChangingOtherPreferencesAndKeepsExistingThemes() {
        for(theme in themes) {
            val before = KeyboardPreferences(theme=KeyboardTheme.DARK, height=KeyboardHeight.TALL, numberRow=true, sound=true, vibration=false, repeatMillis=100)
            KeyboardPreferences.save(context, before)
            var changed = 0
            val picker = ThemePicker(context, { changed++ })
            picker.findViewWithTag<View>("choice:Theme:${theme.ordinal}").performClick()
            assertEquals(before.copy(theme=theme), KeyboardPreferences.load(context))
            assertEquals(1, changed)
            for(existing in listOf(KeyboardTheme.SYSTEM, KeyboardTheme.LIGHT, KeyboardTheme.DARK, KeyboardTheme.MIDNIGHT, KeyboardTheme.NEON, KeyboardTheme.PAPER, KeyboardTheme.SPECTRUM)) {
                assertNotNull(picker.findViewWithTag<View>("choice:Theme:${existing.ordinal}"))
            }
            assertTrue(ThemePicker(context).findViewWithTag<View>("choice:Theme:${theme.ordinal}").isSelected)
        }
    }
    @Test fun newSurfacesKeepReadableTextAndReferenceStyledToolbarIcons() {
        for(theme in themes) {
            val colors = Palette.forTheme(theme)
            assertNotEquals(SurfaceStyle.FLAT, colors.style)
            for(surface in listOf(colors.background, colors.surface, colors.key)) assertTrue("$theme text contrast", ColorUtils.calculateContrast(colors.text, surface) >= 4.5)
            assertTrue(ColorUtils.calculateContrast(colors.onAccent, colors.accent) >= 4.5)
            for(stop in listOf(colors.actionStart, colors.actionEnd)) assertTrue("$theme action contrast", ColorUtils.calculateContrast(colors.onAccent, stop) >= 4.5)
            assertTrue((0..6).map(colors::icon).distinct().size >= 4)
            for(index in 0..6) assertTrue("$theme icon $index", ColorUtils.calculateContrast(colors.icon(index), colors.surface) >= 3.0)
            assertTrue(colors.shape() is ThemeSurfaceDrawable)
        }
        assertEquals(SurfaceStyle.FLAT, Palette.forTheme(KeyboardTheme.NEON).style)
    }
    @Test fun builtInThemesUseMonochromeIconsAndBlueEnterActions() {
        for (theme in KeyboardTheme.entries.filter { it !in listOf(KeyboardTheme.CUSTOM, KeyboardTheme.SYSTEM, KeyboardTheme.GLASS, KeyboardTheme.CHARCOAL, KeyboardTheme.CLOUD) }) {
            val colors = Palette.forTheme(theme)
            assertEquals("$theme toolbar icon", colors.text, colors.icon(2))
            assertTrue("$theme icon contrast", ColorUtils.calculateContrast(colors.icon(2), colors.background) >= 4.5)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(colors.accent, hsv)
            assertTrue("$theme Enter accent is blue", hsv[0] in 190f..245f)
        }
        assertEquals(Palette.forTheme(KeyboardTheme.LIGHT).icon(), Palette.forTheme(KeyboardTheme.SYSTEM, false).icon())
        assertEquals(Palette.forTheme(KeyboardTheme.DARK).icon(), Palette.forTheme(KeyboardTheme.SYSTEM, true).icon())
    }
    @Test fun renderedKeysPanelsAndVoiceShareTheSelectedStyleAndSameKeyboardHeight() {
        for(theme in themes) {
            val view = keyboard(theme); layout(view); val height = view.height
            screenshot(view, "${theme.name}-keys")
            assertEquals(1, descendants(view).count { it.tag == "text:q" })
            view.setActiveTool("Tone")
            assertTrue(view.findViewWithTag<View>("Tone").isSelected)
            view.showSettings("Tools"); layout(view); assertEquals(height, view.height); screenshot(view, "${theme.name}-tools")
            view.findViewWithTag<View>("ToolsEdit").performClick(); layout(view)
            assertEquals(height, view.height); screenshot(view, "${theme.name}-tools-edit")
            view.findViewWithTag<View>("ToolsEdit").performClick()
            view.findViewWithTag<View>("SettingsBack").performClick(); layout(view); assertEquals(height, view.height)
            view.showWritingTool("ai", "ur", "en", "polite")
            view.updateWritingTool("Draft", "First option", "Review.", false, listOf("First option", "Second option", "Third option"))
            layout(view); assertEquals(height, view.height)
            assertTrue(view.findViewWithTag<View>("ResultCard:0").background is ThemeSurfaceDrawable)
            screenshot(view, "${theme.name}-ai")
            view.closeWritingTool(); view.showWritingTool("translate", "ur", "en", "rewrite")
            layout(view); assertEquals(height, view.height); screenshot(view, "${theme.name}-translate")
            view.closeWritingTool(); view.setVoiceAllowed(true); view.setVoiceState(VoiceState.LISTENING); view.voiceReady()
            for(level in listOf(0f, 3f, 7f, 2f, 8f, 4f, 1f, 6f)) view.setVoiceLevel(level)
            layout(view); assertEquals(height, view.height); screenshot(view, "${theme.name}-voice")
            view.setVoiceState(VoiceState.IDLE); view.showSettings("Theme")
            layout(view); assertEquals(height, view.height); screenshot(view, "${theme.name}-themes")
        }
    }
    @Test fun keyLabelsAndActionsStillFitNarrowEnglishUrduRomanAndLargeFonts() {
        RuntimeEnvironment.setFontScale(1.5f)
        try {
            for(theme in themes) for(language in 0..2) {
                val taps = mutableListOf<Key>()
                val view = keyboard(theme, language) { taps.add(it) }; layout(view, 280)
                val buttons = descendants(view).filterIsInstance<TextView>().filter { it.tag?.toString()?.startsWith("text:") == true }
                for(button in buttons) {
                    val rect = Rect(0, 0, button.width, button.height); view.offsetDescendantRectToMyCoords(button, rect)
                    assertTrue(rect.left >= 0 && rect.right <= view.width && rect.bottom <= view.height)
                    assertTrue(button.layout.getLineWidth(0) <= button.width + 1)
                }
                view.findViewWithTag<View>("Space").performClick(); view.findViewWithTag<View>("Enter").performClick()
                assertEquals(listOf(Key.Space, Key.Enter), taps)
                screenshot(view, "${theme.name}-narrow-$language")
            }
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }
    @Test fun suggestionPillKeepsConfigurableCountsAndRealTapActions() {
        for(count in 2..4) {
            KeyboardPreferences.save(context, KeyboardPreferences(theme=KeyboardTheme.GLASS))
            context.getSharedPreferences("keyboard_options", 0).edit().clear().commit()
            KeyboardOptions(context).setNumber("suggestion_count", count)
            val clicked = mutableListOf<Candidate>()
            val view = KeyboardView(context, onCandidate={ clicked.add(it) }).apply { render(R.string.key_enter) }
            val values = (1..4).map { Candidate("word$it", "word$it") }; view.showCandidates(values); layout(view)
            val row = view.findViewWithTag<ViewGroup>("SuggestionStrip")
            assertTrue(row.background is ThemeSurfaceDrawable)
            assertEquals(count, row.childCount)
            assertEquals(row.getChildAt(0).width, row.getChildAt(1).width)
            view.findViewWithTag<View>("suggestion:word1").performClick(); assertEquals(listOf(values.first()), clicked)
        }
    }
}
