package dev.raabta.keyboard.ime

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PersistableBundle
import dev.raabta.keyboard.setup.OptionalPermissionActivity
import dev.raabta.keyboard.ui.KeyboardOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[34])
class MediaAndPermissionTest {
    @Test fun partialPhotoPermissionIsRecognisedWithoutFullLibraryAccess() {
        val context=RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        assertTrue(OptionalPermissionActivity.hasImageAccess(context))
        val activity=Robolectric.buildActivity(OptionalPermissionActivity::class.java,Intent().putExtra("setting","screenshots")).create().get()
        activity.onRequestPermissionsResult(101,arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),intArrayOf(PackageManager.PERMISSION_DENIED,PackageManager.PERMISSION_GRANTED))
        assertTrue(KeyboardOptions(context).enabled("screenshots"))
    }
    @Test fun sensitiveClipboardContentIsExcluded() {
        val context=RuntimeEnvironment.getApplication()
        val clip=ClipData.newPlainText("Sensitive","private")
        clip.description.extras=PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE",true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        val preview=ClipboardPreview(context); preview.refresh()
        assertTrue(preview.candidates(true).isEmpty())
    }
    @Test fun originalGifPackDecodesAsTwoDifferentFrames() {
        for(shape in listOf("heart","smile")) {
            val bytes=LocalMediaProvider.gif(shape)
            // Android's test compile classpath hides desktop APIs; use the host JVM decoder as an independent oracle.
            val imageIo=Class.forName("javax.imageio.ImageIO")
            val readerType=Class.forName("javax.imageio.ImageReader")
            val imageType=Class.forName("java.awt.image.BufferedImage")
            val stream=imageIo.getMethod("createImageInputStream",Any::class.java).invoke(null,ByteArrayInputStream(bytes))
            val reader=(imageIo.getMethod("getImageReadersByFormatName",String::class.java).invoke(null,"gif") as Iterator<*>).next()
            try {
                readerType.getMethod("setInput",Any::class.java).invoke(reader,stream)
                assertEquals(2,readerType.getMethod("getNumImages",Boolean::class.javaPrimitiveType).invoke(reader,true))
                val a=readerType.getMethod("read",Int::class.javaPrimitiveType).invoke(reader,0)
                val b=readerType.getMethod("read",Int::class.javaPrimitiveType).invoke(reader,1)
                assertEquals(64,imageType.getMethod("getWidth").invoke(a))
                val rgb=imageType.getMethod("getRGB",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType)
                assertTrue((0 until 64).any { x -> (0 until 64).any { y -> rgb.invoke(a,x,y)!=rgb.invoke(b,x,y) } })
            } finally {
                readerType.getMethod("dispose").invoke(reader)
                (stream as java.io.Closeable).close()
            }
        }
    }
    @Test fun mediaProviderRejectsArbitraryCacheFiles() {
        val provider=Robolectric.buildContentProvider(LocalMediaProvider::class.java).create().get()
        try {
            provider.openFile(android.net.Uri.parse("content://dev.raabta.keyboard.media/secret.txt"),"r")
            fail("Arbitrary file accepted")
        } catch (_:IllegalArgumentException) { }
    }
}
