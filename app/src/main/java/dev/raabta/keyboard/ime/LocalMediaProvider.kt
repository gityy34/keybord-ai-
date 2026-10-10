package dev.raabta.keyboard.ime

import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File

/** Original, small offline media pack; only these generated cache files can be shared. */
class LocalMediaProvider:ContentProvider() {
    override fun onCreate()=true
    override fun getType(uri:Uri)=if(uri.lastPathSegment?.endsWith(".gif")==true) "image/gif" else "image/png"
    private fun file(uri:Uri):File {
        val name=uri.lastPathSegment ?: throw IllegalArgumentException("Missing media")
        require(name.matches(Regex("(sticker|anim)-(smile|heart)\\.(png|gif)")))
        return File(context!!.cacheDir,"media/$name")
    }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        require(mode=="r"); return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor {
        val f=file(uri)
        val columns=projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(columns).apply { addRow(columns.map { if(it==OpenableColumns.DISPLAY_NAME) f.name else if(it==OpenableColumns.SIZE) f.length() else null }.toTypedArray<Any?>()) }
    }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
    override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?):Int=throw UnsupportedOperationException()
    override fun delete(uri:Uri,selection:String?,args:Array<out String>?):Int=throw UnsupportedOperationException()
    companion object {
        fun create(context:Context,kind:String,shape:String):Uri {
            require(kind in listOf("sticker","anim") && shape in listOf("smile","heart"))
            val file=File(context.cacheDir,"media/$kind-$shape.${if(kind=="anim") "gif" else "png"}")
            file.parentFile!!.mkdirs()
            if(file.exists() && file.length()>0) return "content://${context.packageName}.media/${file.name}".toUri()
            val temporary=File(file.parentFile,file.name+".tmp")
            if(kind=="anim") temporary.writeBytes(gif(shape)) else {
                val image=createBitmap(256,256)
                val c=Canvas(image); val p=Paint(Paint.ANTI_ALIAS_FLAG)
                if(shape=="smile") {
                    p.color=Color.rgb(255,193,45); c.drawCircle(128f,128f,108f,p)
                    p.color=Color.rgb(40,30,30); c.drawCircle(90f,100f,12f,p); c.drawCircle(165f,100f,12f,p)
                    p.style=Paint.Style.STROKE; p.strokeWidth=10f; c.drawArc(70f,100f,187f,190f,10f,160f,false,p)
                } else {
                    p.color=Color.rgb(240,55,80)
                    val path=android.graphics.Path().apply {
                        moveTo(128f,224f); cubicTo(30f,154f,15f,75f,65f,45f); cubicTo(98f,28f,120f,51f,128f,70f)
                        cubicTo(146f,31f,185f,27f,212f,56f); cubicTo(254f,115f,180f,184f,128f,224f)
                    }; c.drawPath(path,p)
                }
                temporary.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG,100,it)) }; image.recycle()
            }
            check(temporary.renameTo(file))
            return "content://${context.packageName}.media/${file.name}".toUri()
        }
        /** GIF89a, two looping frames, original four-colour pixel art. Fixed-size clear/pixel LZW codes. */
        fun gif(shape:String):ByteArray {
            val out=ByteArrayOutputStream()
            fun bytes(vararg b:Int) { b.forEach { out.write(it) } }
            fun short(v:Int) { bytes(v and 255,v shr 8) }
            out.write("GIF89a".toByteArray(Charsets.US_ASCII)); short(64); short(64); bytes(0xF1,0,0)
            bytes(0,0,0,255,193,45,240,55,80,40,30,30)
            bytes(0x21,0xFF,11); out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII)); bytes(3,1,0,0,0)
            repeat(2) { frame ->
                bytes(0x21,0xF9,4,9); short(18); bytes(0,0)
                bytes(0x2C); short(0); short(0); short(64); short(64); bytes(0,2)
                val data=ByteArrayOutputStream(); var buffer=0; var bits=0
                fun code(value:Int) { buffer=buffer or (value shl bits); bits+=3; while(bits>=8) { data.write(buffer and 255); buffer=buffer ushr 8; bits-=8 } }
                for(y in 0..63) for(x in 0..63) {
                    val dx=x-32; val dy=y-32
                    val pixel=if(shape=="heart") {
                        val scale=if(frame==0) 1.0 else 0.85
                        val a=dx/(25.0*scale); val b=-(dy-4)/(25.0*scale)
                        if(Math.pow(a*a+b*b-1,3.0)-a*a*b*b*b<=0) 2 else 0
                    } else if(dx*dx+dy*dy>27*27) 0
                    else if((x in 19..23 || x in 41..45) && y in (if(frame==0) 23..28 else 25..26)) 3
                    else if(y in 41..44 && x in 22..42) 3 else 1
                    code(4); code(pixel)
                }
                code(5); if(bits>0) data.write(buffer)
                val array=data.toByteArray(); var index=0
                while(index<array.size) { val size=(array.size-index).coerceAtMost(255); out.write(size); out.write(array,index,size); index+=size }; bytes(0)
            }
            bytes(0x3B); return out.toByteArray()
        }
    }
}
