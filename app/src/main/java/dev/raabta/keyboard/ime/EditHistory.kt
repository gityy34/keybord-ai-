package dev.raabta.keyboard.ime

import android.os.SystemClock
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper

/** In-memory, bounded, per-field history. No persistence; unsupported/large editors are never rewritten. */
class EditHistory {
    private data class Snapshot(val text: String, val start: Int, val end: Int)
    private data class Edit(val before: Snapshot, var after: Snapshot, val kind: String, val id: Long, var time: Long)
    private val undo = ArrayDeque<Edit>()
    private val redo = ArrayDeque<Edit>()
    private var expected: Snapshot? = null
    private var batchBefore: Snapshot? = null
    private var batchDepth = 0
    private var batchKind = "edit"
    private var serial = 0L
    private var merge = false
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    val lastEditId get() = undo.lastOrNull()?.id

    fun clear() { undo.clear(); redo.clear(); expected=null; batchBefore=null; batchDepth=0; merge=false }
    fun selectionChanged(start: Int, end: Int) {
        if (expected?.start != start || expected?.end != end) { expected=null; merge=false }
    }
    private fun read(connection: InputConnection): Snapshot? { return try {
        val extracted = connection.getExtractedText(ExtractedTextRequest().apply { hintMaxChars=MAX+1 },0)
        if (extracted?.text != null && extracted.startOffset==0 && extracted.partialStartOffset<0) {
            val text=extracted.text.toString()
            if(text.length>MAX || extracted.selectionStart !in 0..text.length || extracted.selectionEnd !in 0..text.length) null
            else Snapshot(text,extracted.selectionStart,extracted.selectionEnd)
        } else {
            val before=connection.getTextBeforeCursor(MAX+1,0)?.toString() ?: return null
            val selected=connection.getSelectedText(0)?.toString().orEmpty()
            val after=connection.getTextAfterCursor(MAX+1,0)?.toString() ?: return null
            if(before.length+selected.length+after.length>MAX) null else Snapshot(before+selected+after,before.length,before.length+selected.length)
        }
    } catch (_:RuntimeException) { null } }
    private fun current(connection: InputConnection) = expected ?: read(connection).also { expected=it }
    private fun record(before: Snapshot?, after: Snapshot?, kind: String, coalesce: Boolean) {
        expected=after
        if(before==null || after==null || after.text.length>MAX) { clear(); return }
        if(before.text==after.text) return
        val time=SystemClock.uptimeMillis(); val previous=undo.lastOrNull()
        if(coalesce && merge && previous?.kind==kind && previous.after==before && time-previous.time<900) {
            previous.after=after; previous.time=time
        } else { undo.addLast(Edit(before,after,kind,++serial,time)); if(undo.size>20) undo.removeFirst() }
        redo.clear(); merge=coalesce
    }
    fun <T> transaction(kind: String, connection: InputConnection, operation: () -> T): T {
        val before=current(connection); val previousKind=batchKind; batchKind=kind; batchDepth++
        try { return operation() } finally {
            batchDepth--; if(batchDepth==0) { record(before,expected,kind,false); batchBefore=null }; batchKind=previousKind
        }
    }
    fun undo(connection: InputConnection, expectedId: Long? = null, discard: Boolean = false): Boolean {
        val edit=undo.lastOrNull() ?: return false
        if(expectedId!=null && expectedId!=edit.id) return false
        if(!restore(connection,edit.after,edit.before)) return false
        undo.removeLast(); if(discard) redo.clear() else redo.addLast(edit); merge=false
        return true
    }
    fun redo(connection: InputConnection): Boolean {
        val edit=redo.lastOrNull() ?: return false
        if(!restore(connection,edit.before,edit.after)) return false
        redo.removeLast(); undo.addLast(edit); merge=false; return true
    }
    private fun restore(connection: InputConnection, original: Snapshot, replacement: Snapshot): Boolean {
        if(read(connection)?.text != original.text) { clear(); return false }
        connection.beginBatchEdit()
        return try {
            if(!connection.setSelection(0,original.text.length)) return false
            if(!connection.commitText(replacement.text,1)) { connection.setSelection(original.start,original.end); return false }
            connection.setSelection(replacement.start,replacement.end)
            expected=replacement; true
        } catch (_:RuntimeException) { clear(); false } finally { connection.endBatchEdit() }
    }
    fun wrap(connection: InputConnection): InputConnection = object:InputConnectionWrapper(connection,false) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val before=current(connection)
            val success=super.commitText(text,newCursorPosition)
            val value=text?.toString().orEmpty()
            val after=before?.let {
                val left=minOf(it.start,it.end); val right=maxOf(it.start,it.end)
                val result=it.text.substring(0,left)+value+it.text.substring(right)
                val cursor=(if(newCursorPosition>0) left+value.length+newCursorPosition-1 else left+newCursorPosition).coerceIn(0,result.length)
                if(result.length<=MAX) Snapshot(result,cursor,cursor) else null
            }
            if(!success) { expected=null; merge=false } else if(batchDepth>0) expected=after else {
                record(before,after,"typing",true)
                if(value.any { it.isWhitespace() }) merge=false
            }
            return success
        }
        private fun deletion(beforeLength: Int, afterLength: Int, codePoints: Boolean, operation: () -> Boolean): Boolean {
            val before=current(connection); val success=operation()
            val after=before?.let {
                val left=minOf(it.start,it.end); val right=maxOf(it.start,it.end)
                val start=if(codePoints) it.text.offsetByCodePoints(left,-minOf(beforeLength,it.text.codePointCount(0,left))) else (left-beforeLength).coerceAtLeast(0)
                val end=if(codePoints) it.text.offsetByCodePoints(right,minOf(afterLength,it.text.codePointCount(right,it.text.length))) else (right+afterLength).coerceAtMost(it.text.length)
                Snapshot(it.text.substring(0,start)+it.text.substring(left,right)+it.text.substring(end),start,start+right-left)
            }
            if(!success) { expected=null; merge=false } else if(batchDepth>0) expected=after else record(before,after,"delete",true)
            return success
        }
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int)=deletion(beforeLength,afterLength,false) { super.deleteSurroundingText(beforeLength,afterLength) }
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int)=deletion(beforeLength,afterLength,true) { super.deleteSurroundingTextInCodePoints(beforeLength,afterLength) }
        override fun setSelection(start: Int, end: Int): Boolean {
            val ok=super.setSelection(start,end)
            expected=expected?.takeIf { ok && start in 0..it.text.length && end in 0..it.text.length }?.copy(start=start,end=end)
            merge=false; return ok
        }
        override fun beginBatchEdit(): Boolean {
            if(batchDepth==0) batchBefore=current(connection)
            batchDepth++; return super.beginBatchEdit()
        }
        override fun endBatchEdit(): Boolean {
            val result=super.endBatchEdit()
            if(batchDepth>0 && --batchDepth==0) { record(batchBefore,expected,batchKind,false); batchBefore=null }
            return result
        }
        override fun performContextMenuAction(id: Int): Boolean {
            val before=current(connection); val success=super.performContextMenuAction(id)
            if(id==android.R.id.cut || id==android.R.id.paste) record(before,read(connection),"edit",false)
            else if(id!=android.R.id.copy) { expected=null; merge=false }
            return success
        }
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean { clear(); return super.setComposingText(text,newCursorPosition) }
        override fun sendKeyEvent(event: android.view.KeyEvent?): Boolean { clear(); return super.sendKeyEvent(event) }
    }
    companion object { private const val MAX=4096 }
}
