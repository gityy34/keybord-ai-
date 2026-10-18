package dev.raabta.keyboard.ui

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ime.KeyboardInsetMetrics
import dev.raabta.keyboard.ime.KeyboardView
import dev.raabta.keyboard.setup.KeyboardSettingsActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-port")
class ToolsPanelReferenceTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun resetPreferences(theme: KeyboardTheme) {
        context.getSharedPreferences("tool_order", 0).edit().clear().commit()
        context.getSharedPreferences("keyboard_options", 0).edit().clear().commit()
        KeyboardPreferences.save(context, KeyboardPreferences(theme = theme))
    }

    private fun layout(view: View, width: Int = 360, height: Int = 480) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(context.dp(width), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(context.dp(height), View.MeasureSpec.AT_MOST)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test fun toolsUseTwoWideCardsAndKeepReferenceActions() {
        for (theme in listOf(KeyboardTheme.MIDNIGHT, KeyboardTheme.CLOUD, KeyboardTheme.GLASS)) {
            resetPreferences(theme)
            val panel = ToolsPanel(context)
            layout(panel)
            val grid = panel.findViewWithTag<ViewGroup>("ToolsGrid")
            assertNotNull("missing tools grid for $theme", grid)
            assertTrue("reference layout needs multiple rows", grid.childCount > 1)
            for (index in 0 until grid.childCount) {
                val row = grid.getChildAt(index) as ViewGroup
                assertEquals("two-column row $index for $theme", 2, row.childCount)
                assertTrue("wide card for $theme", row.getChildAt(0).measuredWidth > context.dp(120))
            }
            assertNotNull(panel.findViewWithTag<View>("ToolsReset"))
            assertNotNull(panel.findViewWithTag<View>("ToolsSearch"))
            assertNotNull(panel.findViewWithTag<View>("ToolsEdit"))
            assertNotNull(panel.findViewWithTag<View>("ToolbarPreview"))
            val firstRow = grid.getChildAt(0) as ViewGroup
            val firstCard = firstRow.getChildAt(0) as ViewGroup
            assertTrue("reference cards keep a real vector icon", firstCard.getChildAt(0) is ImageView)
        }
    }

    @Test fun bottomSafeSpacerIsSeparateAndNeverClickable() {
        resetPreferences(KeyboardTheme.MIDNIGHT)
        val view = KeyboardView(context).apply { render(R.string.key_enter) }
        layout(view)
        val spacer = view.findViewWithTag<View>("KeyboardBottomSafeSpacer")
        assertNotNull(spacer)
        assertFalse(spacer.isClickable)
        assertFalse(spacer.isFocusable)
        assertEquals(
            KeyboardInsetMetrics.bottomSpacerPx(
                context.resources.displayMetrics.density,
                0,
                context.resources.displayMetrics.heightPixels
            ),
            spacer.measuredHeight
        )
        assertTrue(spacer.top >= view.findViewWithTag<View>("Space").bottom)
    }

    @Test fun bottomInsetCalculationAddsNavigationSpaceWithoutChangingKeyGap() {
        assertEquals(8, KeyboardInsetMetrics.bottomSpacerPx(1f, 0))
        assertEquals(40, KeyboardInsetMetrics.bottomSpacerPx(2f, 24))
        assertEquals(16, KeyboardInsetMetrics.bottomSpacerPx(2f, -4))
    }

    @Test fun referenceBottomGapUsesDisplayRatioAndIncludesNavigationInset() {
        val gap=KeyboardInsetMetrics.bottomSpacerPx(1f,24,1530)
        assertEquals((1530*KeyboardInsetMetrics.REFERENCE_BOTTOM_GAP_RATIO+0.5f).toInt(),gap)
        assertTrue(KeyboardInsetMetrics.bottomSpacerPx(1f,220,1530)>=220+KeyboardInsetMetrics.OUTER_GAP_DP)
    }

    @Test fun settingsHomeShowsPassiveSearchLauncherAndTapOpensFocusedSearchPage() {
        resetPreferences(KeyboardTheme.MIDNIGHT)
        val controller = org.robolectric.Robolectric.buildActivity(KeyboardSettingsActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.findViewById<View>(android.R.id.content)
        val launcher = root.findViewWithTag<View>("SettingsSearchLauncher")
        assertNotNull(launcher)
        assertTrue(launcher.isClickable)
        assertNull(root.findViewWithTag<View>("SettingsSearchInput"))
        launcher.performClick()
        assertNotNull(root.findViewWithTag<View>("SettingsSearchInput"))
        controller.pause().stop().destroy()
    }

    @Test fun toolsSearchIconUsesDedicatedSearchAction() {
        resetPreferences(KeyboardTheme.MIDNIGHT)
        var action = ""
        val panel = ToolsPanel(context, action = { action = it })
        panel.findViewWithTag<View>("ToolsSearch").performClick()
        assertEquals("settings:search", action)
    }
}
