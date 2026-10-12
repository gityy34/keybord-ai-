package dev.raabta.keyboard.ui

import android.content.Context
import android.view.Gravity
import android.widget.*

/** Compact previews use the same semantic palette as every real keyboard component. */
class ThemePicker @JvmOverloads constructor(context: Context, private val changed: () -> Unit = {}, import: () -> Unit = {}) : LinearLayout(context) {
    init {
        orientation = VERTICAL; tag = "ThemePicker"
        val active = KeyboardPreferences.load(context).theme
        val current = Palette.from(context)
        val themes = KeyboardTheme.entries.filter { it != KeyboardTheme.CUSTOM } +
            if (ThemeImport.load(context) != null) listOf(KeyboardTheme.CUSTOM) else emptyList()
        themes.chunked(2).forEach { items ->
            val row = LinearLayout(context)
            items.forEach { theme ->
                val colors = if (theme == KeyboardTheme.CUSTOM) ThemeImport.load(context)!!.palette else Palette.forTheme(theme, current.dark)
                val name = if (theme == KeyboardTheme.CUSTOM) ThemeImport.load(context)!!.name else theme.label
                val card = LinearLayout(context).apply {
                    orientation = VERTICAL; gravity = Gravity.CENTER; isClickable = true; isFocusable = true
                    tag = "choice:Theme:${theme.ordinal}"; isSelected = theme == active
                    contentDescription = "$name${if (isSelected) ", selected" else ""}"
                    setPadding(context.dp(8),context.dp(8),context.dp(8),context.dp(8))
                    background = colors.pressed(colors.background,context.dp(12).toFloat())
                    addView(TextView(context).apply { text = name; textSize = 13f; maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; setTextColor(colors.text); gravity=Gravity.CENTER })
                    addView(MiniKeyboardPreview(context,colors).apply { tag="ThemePreview:${theme.name}" },LayoutParams(LayoutParams.MATCH_PARENT,context.dp(130)).apply { topMargin=context.dp(6) })
                    addView(TextView(context).apply {
                        text=if(theme==active) "Selected ✓" else "Apply theme"; textSize=12f; gravity=Gravity.CENTER; setTextColor(colors.onAccent)
                        background=colors.shape(colors.accent,context.dp(5).toFloat(),false)
                    },LayoutParams(LayoutParams.MATCH_PARENT,context.dp(32)).apply { topMargin=context.dp(6) })
                    setOnClickListener { KeyboardPreferences.save(context,KeyboardPreferences.load(context).copy(theme=theme)); changed() }
                }
                row.addView(card,LayoutParams(0,context.dp(205),1f).apply { setMargins(context.dp(4),context.dp(4),context.dp(4),context.dp(4)) })
            }
            addView(row)
        }
        addView(Button(context).apply {
            setText(dev.raabta.keyboard.R.string.theme_import_title); tag="ThemeImport"; isAllCaps=false; setTextColor(current.text)
            background=current.pressed(radius=context.dp(10).toFloat()); setOnClickListener { import() }
        },LayoutParams(LayoutParams.MATCH_PARENT,context.dp(48)).apply { topMargin=context.dp(8) })
        addView(TextView(context).apply { setText(dev.raabta.keyboard.R.string.theme_import_help); textSize=12f; setTextColor(current.muted); setPadding(0,context.dp(8),0,0) })
    }
}
