package dev.raabta.keyboard.setup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import dev.raabta.keyboard.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[28])
class ThemeImportActivityTest {
    @Test fun documentPickerImportsAndAppliesARealBoundedColourFile() {
        val controller=Robolectric.buildActivity(ThemeImportActivity::class.java).setup()
        val activity=controller.get()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT,shadowOf(activity).nextStartedActivityForResult.intent.action)
        val json="""{"version":1,"name":"Local theme","dark":true,"colors":{"background":"#080E1D","surface":"#131E34","text":"#EDF8FF","muted":"#AABED4","accent":"#63E5FF"}}"""
        val uri=Uri.parse("content://theme-test/colors.json")
        shadowOf(activity.contentResolver).registerInputStream(uri,json.byteInputStream())
        ReflectionHelpers.callInstanceMethod<Unit>(activity,"onActivityResult",ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,71),ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType,Activity.RESULT_OK),ReflectionHelpers.ClassParameter.from(Intent::class.java,Intent().setData(uri)))
        ReflectionHelpers.getField<ExecutorService>(activity,"worker").submit {}.get(5,TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.isFinishing); assertEquals("Local theme",ThemeImport.load(activity)!!.name)
        assertEquals(KeyboardTheme.CUSTOM,KeyboardPreferences.load(activity).theme)
        controller.pause().stop().destroy()
    }
}
