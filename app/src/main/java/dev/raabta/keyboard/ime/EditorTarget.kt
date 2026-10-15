package dev.raabta.keyboard.ime

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/** Explicit tool activation only. Bounded selected/current sentence, kept in memory, never logged. */
data class EditorTarget(val original:String,val beforeCount:Int,val afterCount:Int,val selected:Boolean,
    val caretStart:Int?,val caretEnd:Int?) {
    fun matches(connection:InputConnection):Boolean = try {
        val positions=connection.getExtractedText(ExtractedTextRequest(),0)
        if(caretStart!=null && positions!=null && (positions.selectionStart!=caretStart || positions.selectionEnd!=caretEnd)) false
        else if(selected) connection.getSelectedText(0)?.toString()==original
        else connection.getSelectedText(0).isNullOrEmpty() &&
            connection.getTextBeforeCursor(beforeCount,0)?.toString().orEmpty()==original.take(beforeCount) &&
            connection.getTextAfterCursor(afterCount,0)?.toString().orEmpty()==original.takeLast(afterCount)
    } catch (_:RuntimeException) { false }
    fun replace(connection:InputConnection,result:String):Boolean {
        if(!matches(connection)) return false
        val replacement=original.takeWhile { it.isWhitespace() }+result.trim()+original.takeLastWhile { it.isWhitespace() }
        if(selected) return connection.commitText(replacement,1)
        connection.beginBatchEdit()
        return try {
            if(!connection.deleteSurroundingText(beforeCount,afterCount)) false
            else if(connection.commitText(replacement,1)) true
            else { connection.commitText(original,1); false }
        } finally { connection.endBatchEdit() }
    }
    companion object {
        fun capture(connection:InputConnection):EditorTarget? = try {
            val positions=connection.getExtractedText(ExtractedTextRequest(),0)
            val selected=connection.getSelectedText(0)?.toString().orEmpty()
            if(selected.isNotEmpty()) {
                if(selected.length>2000) null else EditorTarget(selected,0,0,true,positions?.selectionStart,positions?.selectionEnd)
            } else {
                val before=connection.getTextBeforeCursor(2001,0)?.toString().orEmpty()
                val after=connection.getTextAfterCursor(2001,0)?.toString().orEmpty()
                val left=before.substringAfterLast('\n').let { line ->
                    val trimmed=line.trimEnd()
                    val boundary=if(trimmed.lastOrNull()?.let { it in ".!?؟۔" }==true) trimmed.dropLast(1).indexOfLast { it in ".!?؟۔" } else line.indexOfLast { it in ".!?؟۔" }
                    line.substring(boundary+1).trimStart()
                }
                val end=after.indexOfFirst { it in ".!?؟۔\n" }
                val right=(if(end<0) after else after.substring(0,end+if(after[end]=='\n') 0 else 1)).trimEnd()
                val text=left+right
                if(text.isBlank() || text.length>2000) null else EditorTarget(text,left.length,right.length,false,positions?.selectionStart,positions?.selectionEnd)
            }
        } catch (_:RuntimeException) { null }
    }
}
