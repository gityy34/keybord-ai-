package dev.raabta.keyboard.ui

import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-port")
class ToolsPanelLayoutTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun resetPreferences() {
        context.getSharedPreferences("tool_order", 0).edit().clear().commit()
        KeyboardPreferences.save(context, KeyboardPreferences(theme = KeyboardTheme.MIDNIGHT))
    }

    private fun layout(view: View, width: Int, height: Int = 480) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(context.dp(width), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(context.dp(height), View.MeasureSpec.AT_MOST)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test fun cardsKeepFixedSlotsAcrossNarrowAndWideWidths() {
        resetPreferences()
        for (width in listOf(280, 360, 640)) {
            val panel = ToolsPanel(context)
            layout(panel, width)
            val grid = panel.findViewWithTag<ViewGroup>("ToolsGrid")
            assertNotNull(grid)
            for (rowIndex in 0 until grid.childCount) {
                val row = grid.getChildAt(rowIndex) as ViewGroup
                assertEquals(2, row.childCount)
                val card = row.getChildAt(0) as FrameLayout
                val icon = card.findViewWithTag<View>("ToolIcon:${card.tag.toString().substringAfter(':')}")
                assertNotNull(icon)
                assertNotNull(card.findViewWithTag<View>("ToolChevron:${card.tag.toString().substringAfter(':')}"))
                val label = card.children().filterIsInstance<TextView>().firstOrNull { it.tag?.toString()?.startsWith("ToolLabel:") == true }
                assertNotNull(label)
                assertEquals(1, label!!.maxLines)
                assertTrue("card remains a comfortable target at $width", card.height >= context.dp(48))
                assertTrue("card remains inside the grid at $width", card.right <= row.width || row.width == 0)
            }
        }
    }

    @Test fun onlyOverflowingLabelsUseMarqueeAndFitLabelsStayStatic() {
        resetPreferences()
        val narrow = ToolsPanel(context)
        layout(narrow, 280)
        val longLabel = narrow.findViewWithTag<TextView>("ToolLabel:Stickers / GIFs")
        assertNotNull(longLabel)
        assertTrue("long label is measured as one line", longLabel!!.maxLines == 1)
        assertTrue(
            "long label uses overflow handling",
            longLabel.ellipsize == TextUtils.TruncateAt.MARQUEE || longLabel.ellipsize == TextUtils.TruncateAt.END
        )

        val wide = ToolsPanel(context)
        layout(wide, 640)
        val shortLabel = wide.findViewWithTag<TextView>("ToolLabel:Settings")
        assertNotNull(shortLabel)
        assertNull("short label should not animate", shortLabel!!.ellipsize)
        assertFalse("short label should not be selected for marquee", shortLabel.isSelected)
    }

    @Test fun wholeCardsKeepExistingCommands() {
        resetPreferences()
        val commands = mutableListOf<String>()
        val panel = ToolsPanel(context, action = { commands += it })
        layout(panel, 360)
        panel.findViewWithTag<View>("settings:Settings").performClick()
        panel.findViewWithTag<View>("settings:Undo").performClick()
        panel.findViewWithTag<View>("settings:Redo").performClick()
        assertEquals(listOf("settings", "edit:undo", "edit:redo"), commands)
    }

    private fun ViewGroup.children(): Sequence<View> = sequence {
        for (index in 0 until childCount) yield(getChildAt(index))
    }
}
