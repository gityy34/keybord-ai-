package dev.raabta.keyboard.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ime.KeyboardView
import dev.raabta.keyboard.voice.VoiceState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w360dp-h800dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PolishLayoutTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun keyboard(theme:KeyboardTheme):KeyboardView {
        context.getSharedPreferences("keyboard_options",0).edit().clear().commit()
        KeyboardPreferences.save(context,KeyboardPreferences(theme=theme))
        return KeyboardView(context).apply { render(R.string.key_enter) }
    }
    private fun layout(view:View,width:Int=360,height:Int=480) {
        view.measure(View.MeasureSpec.makeMeasureSpec(context.dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(context.dp(height),View.MeasureSpec.AT_MOST))
        view.layout(0,0,view.measuredWidth,view.measuredHeight)
    }
    private fun screenshot(view:View,name:String) {
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
        val file=File("/workspace/artifacts/ui-0.7.0/$name.png"); file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    @Test fun everyThemeHasConsistentKeysAndIntegratedToolsResultsVoiceAndTranslation() {
        for(theme in listOf(KeyboardTheme.MIDNIGHT,KeyboardTheme.NEON,KeyboardTheme.PAPER,KeyboardTheme.SPECTRUM)) {
            val view=keyboard(theme); layout(view); val height=view.height; screenshot(view,"${theme.name}-keys")
            val palette=Palette.from(context); assertEquals(palette.onAccent,view.findViewWithTag<TextView>("Enter").currentTextColor)
            view.showSettings("Tools"); layout(view); assertEquals(height,view.height); screenshot(view,"${theme.name}-tools")
            val row=view.findViewWithTag<ViewGroup>("ToolRow:0"); assertEquals(3,row.childCount)
            assertEquals(row.getChildAt(0).width,row.getChildAt(1).width)
            view.showWritingTool("ai","ur","en","polite"); view.updateWritingTool("Draft","First option","Review your result.",false,listOf("First option","Second option","Third option"))
            layout(view); assertEquals(height,view.height); assertNotNull(view.findViewWithTag<View>("ResultCard:2")); screenshot(view,"${theme.name}-results")
            view.closeWritingTool(); view.showWritingTool("translate","ur","en","rewrite"); layout(view); assertEquals(height,view.height); screenshot(view,"${theme.name}-translate")
            view.closeWritingTool(); view.setVoiceState(VoiceState.LISTENING); view.voiceReady(); for(level in listOf(0f,2f,6f,3f,8f,1f,9f,4f,5f,8f,2f,7f,3f)) view.setVoiceLevel(level); layout(view); assertEquals(height,view.height); screenshot(view,"${theme.name}-voice")
            for(tag in listOf("VoiceDismiss","VoiceDelete","VoiceStop")) {
                val child=view.findViewWithTag<View>(tag); val rect=Rect(0,0,child.width,child.height); view.offsetDescendantRectToMyCoords(child,rect)
                assertTrue("$theme $tag outside keyboard: $rect",rect.left>=0 && rect.right<=view.width && rect.top>=0 && rect.bottom<=view.height)
                assertTrue(child.width>=context.dp(44) && child.height>=context.dp(44))
            }
        }
    }
    @Test fun narrowLargeFontWorkspacesKeepControlsInsideTheContainerAndBackRestoresTyping() {
        RuntimeEnvironment.setFontScale(1.5f)
        try {
            val view=keyboard(KeyboardTheme.NEON); layout(view,280,400); val height=view.height
            view.showSettings("Theme"); layout(view,280,400); assertEquals(height,view.height); screenshot(view,"theme-narrow")
            view.findViewWithTag<View>("SettingsBack").performClick(); assertNull(view.findViewWithTag<View>("ThemePicker"))
            view.showWritingTool("ai","ur","en","rewrite"); layout(view,280,400); assertEquals(height,view.height)
            view.toggleWritingKeyboard(); layout(view,280,400); assertEquals(height,view.height)
            val key=view.findViewWithTag<View>("Space"); val rect=Rect(0,0,key.width,key.height); view.offsetDescendantRectToMyCoords(key,rect)
            assertTrue(rect.bottom<=view.height); screenshot(view,"draft-edit-narrow")
            view.closeWritingTool(); layout(view,280,400); assertEquals(height,view.height)
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }
    @Test @Config(qualifiers="w640dp-h360dp-land") fun compactLandscapeResultsAndVoiceKeepUsableControlAreas() {
        val view=keyboard(KeyboardTheme.SPECTRUM)
        view.showWritingTool("ai","ur","en","rewrite"); view.updateWritingTool("Draft","Response","Review.",false,listOf("Response","Alternative"))
        layout(view,640,240)
        assertTrue(view.findViewWithTag<View>("ToolResults").height>=context.dp(100))
        assertEquals("Paraphrase",view.findViewWithTag<TextView>("tool:writing:groups").text.toString())
        assertTrue(view.findViewWithTag<View>("tool:writing:groups").isSelected)
        screenshot(view,"compact-results")
        view.closeWritingTool(); view.setVoiceState(VoiceState.LISTENING); view.voiceReady(); layout(view,640,240)
        val stop=view.findViewWithTag<View>("VoiceStop"); val bounds=Rect(0,0,stop.width,stop.height); view.offsetDescendantRectToMyCoords(stop,bounds)
        assertTrue(bounds.bottom<=view.height); screenshot(view,"compact-voice")
    }

}
