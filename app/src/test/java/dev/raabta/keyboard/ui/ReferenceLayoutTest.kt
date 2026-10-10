package dev.raabta.keyboard.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import dev.raabta.keyboard.R
import dev.raabta.keyboard.ime.*
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
class ReferenceLayoutTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun keyboard(language:Int=0):KeyboardView {
        context.getSharedPreferences("keyboard_options",0).edit().clear().commit()
        KeyboardOptions(context).setNumber("language",language)
        KeyboardOptions(context).setNumber("font_size",32)
        KeyboardPreferences.save(context,KeyboardPreferences(theme=KeyboardTheme.MIDNIGHT,numberRow=true))
        return KeyboardView(context).apply { render(R.string.key_enter) }
    }
    private fun measure(view:View,width:Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(context.dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        view.layout(0,0,view.measuredWidth,view.measuredHeight)
    }
    private fun visit(root:View,action:(View)->Unit) {
        if(root.visibility!=View.VISIBLE) return
        action(root)
        if(root is ViewGroup) for(i in 0 until root.childCount) visit(root.getChildAt(i),action)
    }
    private fun checkLabels(view:KeyboardView) {
        visit(view) { child ->
            if(child is KeyButton && child !is ToolbarButton) {
                val tag=child.tag?.toString().orEmpty()
                if(tag.startsWith("text:") || tag in listOf("Space","Enter","Delete","Shift") || tag.startsWith("mode:")) {
                    assertEquals("Uncentred $tag",Gravity.CENTER,child.gravity)
                    val layout=child.layout
                    assertNotNull("Missing text layout $tag",layout)
                    assertEquals("Truncated key label $tag",0,layout.getEllipsisCount(0))
                    val available=child.width-child.totalPaddingLeft-child.totalPaddingRight
                    assertTrue("$tag text wider than key: ${layout.getLineWidth(0)} > $available",layout.getLineWidth(0)<=available+1)
                    val bounds=Rect(); child.paint.getTextBounds(child.text.toString(),0,child.text.length,bounds)
                    val baseline=child.totalPaddingTop+layout.getLineBaseline(0)
                    assertTrue("$tag above key",baseline+bounds.top>=-1)
                    assertTrue("$tag below key",baseline+bounds.bottom<=child.height+1)
                    val textCentre=child.totalPaddingLeft+(layout.getLineLeft(0)+layout.getLineRight(0))/2f
                    assertTrue("$tag text at side ($textCentre in ${child.width})",kotlin.math.abs(textCentre-child.width/2f)<=2)
                }
            }
            if(child is Button) {
                val bounds=Rect(0,0,child.width,child.height); view.offsetDescendantRectToMyCoords(child,bounds)
                assertTrue("Button outside keyboard ${child.tag}: $bounds",bounds.left>=0 && bounds.right<=view.width && bounds.top>=0 && bounds.bottom<=view.height)
            }
        }
    }
    private fun screenshot(view:View,name:String) {
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val path=File("/workspace/artifacts/ui-0.5.0/$name.png"); path.parentFile?.mkdirs()
        path.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    @Test fun actualEnglishUrduAndRomanLabelsFitAndRemainCentredAtDifferentWidths() {
        for(language in 0..2) for(width in listOf(280,320,360,420)) {
            val view=keyboard(language); measure(view,width); checkLabels(view)
            if(width==360) screenshot(view,"keyboard-$language")
        }
    }
    @Test @Config(qualifiers="w320dp-h640dp-port") fun enlargedSystemFontStillFitsActualKeyText() {
        RuntimeEnvironment.setFontScale(1.5f)
        try { val view=keyboard(); measure(view,320); checkLabels(view) } finally { RuntimeEnvironment.setFontScale(1f) }
    }
    @Test fun microphoneUsesRealLevelPanelAndProcessingStillOffersCancel() {
        val view=keyboard(); view.setVoiceAllowed(true); view.setVoiceState(VoiceState.LISTENING); view.showVoiceLanguages(); view.setVoiceLevel(6f)
        measure(view,360)
        assertEquals(View.VISIBLE,view.findViewWithTag<View>("VoiceRecorder").visibility)
        screenshot(view,"microphone")
        view.setVoiceState(VoiceState.PROCESSING)
        assertFalse(view.findViewWithTag<View>("VoiceStop").isEnabled)
        assertTrue(view.findViewWithTag<View>("VoiceDismiss").isEnabled)
        view.setVoiceState(VoiceState.IDLE); assertEquals(View.GONE,view.findViewWithTag<View>("VoiceRecorder").visibility)
    }
    @Test fun writingResultsHaveReferenceCardsAndFitCompactWindowWithoutKeyRows() {
        val view=keyboard(); view.showWritingTool("ai","ur","en","humanise")
        view.updateWritingTool("hello","Hello there","Review the result.",false,listOf("Hello there","Hi there","Good morning"))
        measure(view,320); screenshot(view,"ai-results")
        assertNotNull(view.findViewWithTag<View>("ResultCard:2"))
        assertTrue(view.measuredHeight<context.dp(500))
        view.showWritingTool("translate","ur","en","rewrite"); measure(view,360); screenshot(view,"translation")
    }
    @Test fun fullSettingsUsesWholeActivityAndThemeCategoriesRenderWithInsets() {
        val controller=org.robolectric.Robolectric.buildActivity(dev.raabta.keyboard.setup.KeyboardSettingsActivity::class.java).setup()
        val activity=controller.get()
        val root=activity.findViewById<ViewGroup>(android.R.id.content)
        assertNotNull(root.findViewWithTag<View>("settings:Preferences"))
        root.measure(View.MeasureSpec.makeMeasureSpec(context.dp(360),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(context.dp(720),View.MeasureSpec.EXACTLY))
        root.layout(0,0,root.measuredWidth,root.measuredHeight)
        screenshot(root,"settings")
        assertTrue(root.findViewWithTag<View>("SettingsPanel").height>=context.dp(650))
        root.findViewWithTag<View>("settings:Preferences").performClick()
        root.measure(View.MeasureSpec.makeMeasureSpec(context.dp(360),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(context.dp(720),View.MeasureSpec.EXACTLY))
        root.layout(0,0,root.measuredWidth,root.measuredHeight)
        screenshot(root,"preferences")
        controller.pause().stop().destroy()
    }
}
