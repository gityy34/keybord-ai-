package dev.raabta.keyboard.ime

import android.content.Context
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import javax.crypto.spec.SecretKeySpec
import dev.raabta.keyboard.R
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class CustomizationTest {
    private lateinit var context: Context
    private val key=SecretKeySpec(ByteArray(32) { (it+1).toByte() },"AES")
    private var time=1_000_000L
    private fun history()=ClipboardHistory(context,{ time },{ key })
    @Before fun setup() {
        context=org.robolectric.RuntimeEnvironment.getApplication()
        context.getSharedPreferences("clipboard_history",0).edit().clear().commit()
        context.getSharedPreferences("tool_order",0).edit().clear().commit()
    }
    @Test fun seventhCopyMovesOldestToOldAndAgeingUsesOriginalCopyTime() {
        val h=history()
        (1..7).forEach { time+=1_000; assertTrue(h.add("clip $it")) }
        assertEquals(listOf("clip 7","clip 6","clip 5","clip 4","clip 3","clip 2"),h.items(ClipSection.RECENT).map(SavedClip::text))
        assertEquals(listOf("clip 1"),h.items(ClipSection.OLD).map(SavedClip::text))
        time+=ClipboardHistory.RECENT_MILLIS
        assertTrue(h.items(ClipSection.RECENT).isEmpty())
        assertEquals(7,h.items(ClipSection.OLD).size)
        time+=ClipboardHistory.OLD_MILLIS-ClipboardHistory.RECENT_MILLIS
        assertTrue(h.items(ClipSection.OLD).isEmpty())
    }
    @Test fun pinSurvivesRestartAndClearAndStoredTextIsEncrypted() {
        val h=history(); assertTrue(h.add("private copied text"))
        val id=h.items(ClipSection.RECENT).single().id
        assertTrue(h.pin(id,true))
        assertFalse(context.getSharedPreferences("clipboard_history",0).getString("entries","")!!.contains("private copied text"))
        assertEquals("private copied text",history().items(ClipSection.PINNED).single().text)
        assertTrue(history().clearUnpinned())
        time+=ClipboardHistory.OLD_MILLIS*2
        assertEquals("private copied text",history().items(ClipSection.PINNED).single().text)
        assertTrue(h.delete(id))
        assertTrue(history().items(ClipSection.PINNED).isEmpty())
    }
    @Test fun toolMovesAndReorderPersistWithoutDuplicatingTools() {
        val store=ToolOrder(context)
        assertTrue(store.toolbar().any { it.id=="Grammar" })
        store.move("Clipboard",true)
        store.reorder("Clipboard","AI",true)
        assertEquals("Clipboard",ToolOrder(context).toolbar().first().id)
        store.move("Grammar",false)
        assertTrue(ToolOrder(context).more().any { it.id=="Grammar" })
        val ids=store.toolbar().map(KeyboardTool::id)+store.more().map(KeyboardTool::id)
        assertEquals(ids.size,ids.distinct().size)
        assertEquals(KeyboardTools.all.size,ids.size)
    }
    @Test fun stationaryHoldsSwitchWithoutTypingButShortTapStillTypes() {
        val keyButton=GestureKeyButton(context)
        var taps=0; var switches=0
        keyButton.setOnClickListener { taps++ }
        keyButton.onStationaryHold={ switches++ }
        keyButton.stationaryHoldMillis=2_500
        fun event(action:Int)=MotionEvent.obtain(0,0,action,12f,12f,0)
        keyButton.onTouchEvent(event(MotionEvent.ACTION_DOWN))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(2_499))
        assertEquals(0,switches)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        keyButton.onTouchEvent(event(MotionEvent.ACTION_UP))
        assertEquals(1,switches); assertEquals(0,taps)
        keyButton.onTouchEvent(event(MotionEvent.ACTION_DOWN))
        keyButton.onTouchEvent(event(MotionEvent.ACTION_UP))
        assertEquals(1,taps)
    }
    @Test fun spaceAndGlobeHoldOpenSystemPickerAndEnterKeepsArrow() {
        val taps=mutableListOf<Key>(); var switchers=0
        val view=KeyboardView(context,onKey={ taps.add(it) },onSwitch={ switchers++ })
        view.render(R.string.key_enter)
        fun hold(tag:String,millis:Long) {
            val button=view.findViewWithTag<GestureKeyButton>(tag)
            assertNotNull(button)
            button.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,12f,12f,0))
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
            button.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_UP,12f,12f,0))
        }
        hold("Space",5_000); hold("Language",2_500)
        assertEquals(2,switchers); assertTrue(taps.isEmpty())
        assertEquals("↵",view.findViewWithTag<android.widget.TextView>("Enter").text.toString())
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun clipboardGridFitsKeyboardWorkspaceAndOldRemainsReachable() {
        val h=history(); (1..7).forEach { time+=1_000; h.add("copied item $it") }
        val view=KeyboardView(context)
        view.render(R.string.key_enter); view.showClipboard(h)
        view.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.AT_MOST))
        view.layout(0,0,view.measuredWidth,view.measuredHeight)
        assertNotNull(view.findViewWithTag<View>("ClipboardSectionSwitch"))
        assertNotNull(view.findViewWithTag<View>("ClipboardClear:Recent"))
        assertNotNull(view.findViewWithTag<View>("ClipboardClear:Old copied"))
        assertEquals(7,h.items(ClipSection.RECENT).size+h.items(ClipSection.OLD).size)
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("/workspace/artifacts/ui-0.8.0").mkdirs()
        File("/workspace/artifacts/ui-0.8.0/clipboard.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
