package dev.raabta.keyboard.ui

import android.view.View
import android.widget.*
import dev.raabta.keyboard.ime.KeyButton
import dev.raabta.keyboard.ime.KeyboardView

/** A theme-consistent bounded chooser anchored within the keyboard's own rectangle. */
object ThemedChoices {
    fun show(anchor:View,labels:List<String>,selected:Int=-1,pick:(Int)->Unit) {
        val context=anchor.context; val palette=Palette.from(context)
        val root=generateSequence(anchor.parent as? View) { it.parent as? View }.firstOrNull { it is KeyboardView } ?: anchor.rootView
        val bounds=android.graphics.Rect(0,0,anchor.width,anchor.height); (root as? android.view.ViewGroup)?.offsetDescendantRectToMyCoords(anchor,bounds)
        val list=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        val scroll=ScrollView(context).apply { addView(list); background=palette.shape(radius=context.dp(12).toFloat()) }
        val width=(root.width-context.dp(12)).coerceAtLeast(context.dp(180))
        val height=minOf(context.dp(220),(root.height-bounds.bottom-context.dp(6)).coerceAtLeast(context.dp(48)),context.dp(48)*labels.size)
        val popup=PopupWindow(scroll,width,height,true).apply { isOutsideTouchable=true; elevation=context.dp(5).toFloat(); setBackgroundDrawable(palette.shape(radius=context.dp(12).toFloat())) }
        labels.forEachIndexed { index,label -> list.addView(KeyButton(context).apply {
            text=if(index==selected) context.getString(dev.raabta.keyboard.R.string.menu_choice_selected,label) else label; textSize=14f; fitLabel(14f); isSelected=index==selected
            setTextColor(if(isSelected) palette.onAccent else palette.text)
            background=palette.pressed(if(isSelected) palette.accent else palette.surface,context.dp(8).toFloat())
            setOnClickListener { popup.dismiss(); pick(index) }
        },LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,context.dp(48))) }
        popup.showAsDropDown(anchor,context.dp(6)-bounds.left,0)
    }
}
