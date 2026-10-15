package dev.raabta.keyboard.ui

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import androidx.core.graphics.toColorInt

private data class ThemePreset(
    val id: String,
    val name: String,
    val palette: Palette,
    val category: String,
    val builtIn: KeyboardTheme? = null,
)

/** Local vector presets. Keeping these as colour stops makes previews small and works offline. */
private object ThemeCatalog {
    private fun c(value: String) = value.toColorInt()
    private fun preset(
        id: String, name: String, category: String,
        background: String, surface: String, text: String, muted: String, accent: String,
        key: String = surface, border: String = surface, dark: Boolean = true,
        stops: List<String>? = null, keyStops: List<String>? = null,
    ) = ThemePreset(id, name, Palette(
        c(background), c(surface), c(text), c(muted), c(accent), c(key), c(border), c(accent), dark,
        colorful = true, actionStart = c(accent), actionEnd = c(accent),
        backgroundGradient = stops?.map(::c), keyGradient = keyStops?.map(::c),
    ), category)

    val colours = listOf(
        preset("mint", "Mint", "Colours", "#F4FAF8", "#FFFFFF", "#17252A", "#5B6B70", "#36B7A4", "#F1F7F5", "#D4E3DF", false),
        preset("slate", "Slate", "Colours", "#20242C", "#2B313B", "#F5F7FB", "#B5BECC", "#77A8FF", "#303844", "#465365"),
        preset("ocean", "Ocean", "Colours", "#071E2C", "#0D3043", "#F1FCFF", "#9FC5D1", "#28B8D6", "#104056", "#1B6378"),
        preset("ruby", "Ruby", "Colours", "#280E19", "#421525", "#FFF3F7", "#D4A8B9", "#FF647C", "#521C30", "#7E3450"),
        preset("forest", "Forest", "Colours", "#0E211D", "#16352E", "#F1FFF8", "#A8C8BD", "#54C7A1", "#1D4439", "#2B6C5B"),
        preset("teal", "Teal", "Colours", "#092A2A", "#123E3E", "#F0FFFF", "#A9D1CE", "#55D2C8", "#174B4A", "#2A7772"),
        preset("indigo", "Indigo", "Colours", "#151A3A", "#222A55", "#F5F6FF", "#B4BDE5", "#869BFF", "#2C3670", "#4B5BA6"),
        preset("sky", "Sky", "Colours", "#EAF7FF", "#FFFFFF", "#102433", "#587483", "#2389D8", "#EDF8FF", "#C6E1F4", false),
        preset("amber", "Amber", "Colours", "#2B2110", "#47351A", "#FFF9E8", "#D7C18A", "#F3BB4D", "#59431E", "#80642A"),
    )

    val landscapes = listOf(
        preset("lake-dawn", "Lake dawn", "Landscapes", "#16213A", "#253451", "#FFFFFF", "#B6C7E3", "#FF9DAA", "#304362", "#5B7197", stops=listOf("#FF8A98", "#5B6FEA", "#1A345D"), keyStops=listOf("#263A61", "#172640")),
        preset("coastal", "Coastal", "Landscapes", "#09233C", "#123A5B", "#F7FDFF", "#B2D0E4", "#42C7D9", "#164C70", "#317A9D", stops=listOf("#F4A997", "#2B86B8", "#113154"), keyStops=listOf("#1E5575", "#102F52")),
        preset("green-valley", "Green valley", "Landscapes", "#122D2A", "#1E453E", "#F2FFF8", "#B4D4C3", "#6ED9A1", "#255447", "#3D8066", stops=listOf("#88D399", "#357F69", "#112A37"), keyStops=listOf("#2F6454", "#173A3E")),
        preset("cliffside", "Cliffside", "Landscapes", "#211A21", "#3D2B30", "#FFF7F2", "#D2B9B1", "#F18B65", "#4F3436", "#80534B", stops=listOf("#F5B07A", "#956158", "#252238"), keyStops=listOf("#623F40", "#322633")),
        preset("mountain", "Mountain", "Landscapes", "#192636", "#2E3C4C", "#F6FCFF", "#B6C5D1", "#74B6D6", "#344A5D", "#557188", stops=listOf("#D6E5F1", "#718DA6", "#243348"), keyStops=listOf("#3D5366", "#253648")),
        preset("desert", "Desert", "Landscapes", "#38251A", "#59402A", "#FFF9EF", "#DFC39B", "#F2A15E", "#684A30", "#966C43", stops=listOf("#EAA777", "#B86B51", "#35213A"), keyStops=listOf("#744A38", "#472D2B")),
        preset("open-ocean", "Open ocean", "Landscapes", "#071D3B", "#103E68", "#F1FBFF", "#A8C9E3", "#46A9F2", "#154D7C", "#2B76AB", stops=listOf("#6AD8FF", "#277BCB", "#111F63"), keyStops=listOf("#205F9B", "#102E65")),
        preset("sunset", "Sunset", "Landscapes", "#331829", "#54233D", "#FFF7FC", "#D6AFC5", "#FF819D", "#6B2B4B", "#A44E6B", stops=listOf("#FFB66C", "#E75E89", "#3E256F"), keyStops=listOf("#7B345C", "#40264B")),
        preset("aurora", "Aurora", "Landscapes", "#081B2B", "#123448", "#F2FFFF", "#A8CDD0", "#6DE5D2", "#174A58", "#2A7E80", stops=listOf("#65E6C7", "#3475D1", "#181C58"), keyStops=listOf("#225E70", "#122B54")),
    )

    val lightGradients = listOf(
        preset("rose-mist", "Rose mist", "Light gradient", "#FFE0E5", "#FFF7F8", "#35212A", "#80636C", "#D85D87", "#FFF0F2", "#E8B5C3", false, listOf("#FFD9E2", "#DCE5FF"), listOf("#FFF7FA", "#F3E5F4")),
        preset("sage-cloud", "Sage cloud", "Light gradient", "#DCEFE6", "#F6FCF8", "#193128", "#5E776B", "#3CA989", "#EEF9F2", "#BDDCCE", false, listOf("#DDF5E8", "#E0EBFF"), listOf("#FAFFFC", "#E6F4EE")),
        preset("blue-haze", "Blue haze", "Light gradient", "#DCEBFF", "#F8FBFF", "#15233A", "#61728C", "#4387D8", "#EFF6FF", "#C2D8F0", false, listOf("#E6F0FF", "#E5DBFF"), listOf("#FFFFFF", "#E8F2FF")),
        preset("pink-sky", "Pink sky", "Light gradient", "#FFE4EF", "#FFF9FC", "#3A2330", "#866778", "#DF5F9A", "#FFF1F7", "#EBC1D4", false, listOf("#FFE0EE", "#FFF1C8"), listOf("#FFF8FC", "#FFE8F2")),
        preset("peach", "Peach", "Light gradient", "#FFE4D6", "#FFF9F5", "#3B2922", "#8A6D61", "#E98255", "#FFF0E8", "#E9C4B2", false, listOf("#FFE3D5", "#FFF3C9"), listOf("#FFF9F2", "#FDE9D7")),
        preset("sunlit", "Sunlit", "Light gradient", "#FFF0C7", "#FFFCF2", "#342B14", "#85784D", "#D49724", "#FFF7DC", "#E6D39A", false, listOf("#FFE9AE", "#E5F4D5"), listOf("#FFFCF1", "#F4F3D7")),
    )

    val darkGradients = listOf(
        preset("violet-night", "Violet night", "Dark gradient", "#1A1233", "#2E1C50", "#FAF5FF", "#C0AFD4", "#B77CFF", "#392465", "#6A4C9F", stops=listOf("#401A68", "#152F68", "#0D1D3A"), keyStops=listOf("#392262", "#182D55")),
        preset("deep-blue", "Deep blue", "Dark gradient", "#091A36", "#112C54", "#F3F8FF", "#A8BBD9", "#5C9BFF", "#173B6B", "#315D96", stops=listOf("#214D9C", "#102C62", "#101A38"), keyStops=listOf("#1B427E", "#112951")),
        preset("deep-teal", "Deep teal", "Dark gradient", "#071F24", "#0E3A40", "#EEFFFF", "#A1C9C9", "#43D0C5", "#155159", "#2D7D7D", stops=listOf("#116A71", "#073B4C", "#0B2031"), keyStops=listOf("#145A64", "#0D303C")),
        preset("berry", "Berry", "Dark gradient", "#2B1229", "#4B1C45", "#FFF3FE", "#D1A8C9", "#F17CC9", "#5B2455", "#8F417C", stops=listOf("#6E1F69", "#30215F", "#171A3B"), keyStops=listOf("#5C275A", "#2B214A")),
        preset("ember", "Ember", "Dark gradient", "#2D170F", "#4B2417", "#FFF8F1", "#D5B4A3", "#FF9C5B", "#5E2D1E", "#92523A", stops=listOf("#B44A30", "#4D2430", "#1B2037"), keyStops=listOf("#69321F", "#372131")),
        preset("aurora-dark", "Aurora dark", "Dark gradient", "#091A28", "#123143", "#F1FFFF", "#A3C5CB", "#64E2D1", "#184958", "#2B7A78", stops=listOf("#155E68", "#142F66", "#17183E"), keyStops=listOf("#1C5665", "#15294F")),
    )

    fun builtIns(phoneDark: Boolean) = KeyboardTheme.entries.filter { it != KeyboardTheme.CUSTOM }.map {
        ThemePreset(it.name, it.label, Palette.forTheme(it, phoneDark), "Default", it)
    }
}

/** Settings theme browser with real keyboard previews and an apply/cancel detail state. */
@Suppress("SetTextI18n")
class ThemePicker @JvmOverloads constructor(
    context: Context,
    private val changed: () -> Unit = {},
    private val importAction: () -> Unit = {},
) : LinearLayout(context) {
    private val fullSettings = context is Activity
    private var selected: ThemePreset? = null
    private var draftBorders = KeyboardPreferences.load(context).keyBorders
    private var previousTheme = KeyboardPreferences.load(context).theme
    private var previousBorders = draftBorders

    init {
        orientation = VERTICAL
        tag = "ThemePicker"
        setPadding(context.dp(14), context.dp(10), context.dp(14), context.dp(20))
        rebuild()
    }

    private fun currentTheme() = KeyboardPreferences.load(context).theme
    private fun custom() = ThemeImport.load(context)
    private fun active(preset: ThemePreset) = preset.builtIn == currentTheme() ||
        (preset.builtIn == null && currentTheme() == KeyboardTheme.CUSTOM && custom()?.name == preset.name)

    private fun rebuild() {
        removeAllViews()
        val palette = Palette.from(context)
        background = palette.backgroundDrawable()
        addView(TextView(context).apply { text = "Theme"; textSize = 25f; setTextColor(palette.text); gravity = Gravity.CENTER_VERTICAL }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(48)))
        addView(section("My themes"))
        addView(myThemes(palette))
        selected?.let { addView(detail(it), 2) }
        addView(section("Default"))
        addView(grid(ThemeCatalog.builtIns(palette.dark), 112))
        addView(section("Colours", true)); addView(grid(ThemeCatalog.colours, 112))
        addView(section("Landscapes", true)); addView(grid(ThemeCatalog.landscapes, 112))
        addView(section("Light gradient", true)); addView(grid(ThemeCatalog.lightGradients, 112))
        addView(section("Dark gradient", true)); addView(grid(ThemeCatalog.darkGradients, 112))
        addView(TextView(context).apply { text = "Add a custom theme from a local JSON file"; textSize = 13f; setTextColor(palette.muted); setPadding(0, context.dp(18), 0, context.dp(8)) })
        addView(actionCard("＋  Add / import theme", palette) { importAction() }.apply { tag = "ThemeImport" })
    }

    private fun section(value: String, collapsedStyle: Boolean = false) = TextView(context).apply {
        text = if (collapsedStyle) "$value  ˅" else value; textSize = 18f; setTextColor(Palette.from(context).text)
        setPadding(0, context.dp(16), 0, context.dp(8))
    }

    private fun myThemes(palette: Palette): LinearLayout {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.TOP }
        addCard(row, null, 148) { importAction() }
        custom()?.let { value -> addCard(row, ThemePreset("custom", value.name, value.palette, "My themes"), 148) { open(ThemePreset("custom", value.name, value.palette, "My themes")) } }
        return row
    }

    private fun grid(items: List<ThemePreset>, previewHeight: Int): LinearLayout {
        val host = LinearLayout(context).apply { orientation = VERTICAL }
        items.chunked(3).forEach { chunk ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.TOP }
            chunk.forEach { preset -> addCard(row, preset, previewHeight) { open(preset) } }
            repeat(3 - chunk.size) { row.addView(Space(context), LayoutParams(0, context.dp(previewHeight + 52), 1f)) }
            host.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(previewHeight + 56)))
        }
        return host
    }

    private fun addCard(row: LinearLayout, preset: ThemePreset?, height: Int, onClick: () -> Unit) {
        val palette = Palette.from(context)
        val card = LinearLayout(context).apply {
            orientation = VERTICAL; gravity = Gravity.CENTER; isClickable = true; isFocusable = true
            setPadding(context.dp(3), context.dp(3), context.dp(3), context.dp(3)); setOnClickListener { onClick() }
        }
        if (preset == null) {
            card.tag = "ThemeAdd"
            card.background = palette.shape(palette.surface, context.dp(14).toFloat(), true)
            card.addView(TextView(context).apply { text = "+"; textSize = 43f; setTextColor(palette.accent); gravity = Gravity.CENTER }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(height)))
            card.addView(TextView(context).apply { text = "Add"; textSize = 12f; setTextColor(palette.text); gravity = Gravity.CENTER })
        } else {
            card.tag = "choice:Theme:${preset.builtIn?.ordinal ?: KeyboardTheme.CUSTOM.ordinal}"
            card.isSelected = active(preset)
            card.contentDescription = "${preset.name}${if (card.isSelected) ", selected" else ""}"
            card.addView(MiniKeyboardPreview(context, preset.palette).apply { tag = "ThemePreview:${preset.builtIn?.name ?: preset.name}" }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(height)))
            card.addView(TextView(context).apply {
                text = if (card.isSelected) "✓ ${preset.name}" else preset.name; textSize = 12f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(palette.text); gravity = Gravity.CENTER
            }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(34)))
        }
        row.addView(card, LayoutParams(0, context.dp(height + 49), 1f).apply { setMargins(context.dp(3), context.dp(3), context.dp(3), context.dp(3)) })
    }

    private fun actionCard(label: String, palette: Palette, action: () -> Unit) = TextView(context).apply {
        text = label; textSize = 15f; gravity = Gravity.CENTER; isClickable = true; isFocusable = true; setTextColor(palette.text)
        background = palette.shape(palette.surface, context.dp(14).toFloat(), true); setPadding(context.dp(14), context.dp(13), context.dp(14), context.dp(13)); setOnClickListener { action() }
    }

    private fun detail(preset: ThemePreset): LinearLayout {
        val palette = preset.palette.copy(keyBorders = draftBorders)
        return LinearLayout(context).apply {
            orientation = VERTICAL; tag = "ThemeDetail"; setPadding(0, context.dp(10), 0, context.dp(10))
            addView(MiniKeyboardPreview(context, palette), LayoutParams(LayoutParams.MATCH_PARENT, context.dp(230)))
            addView(Switch(context).apply {
                text = "Key borders"; tag = "ThemeBorders"; textSize = 15f; setTextColor(palette.text); isChecked = draftBorders
                setOnCheckedChangeListener { _, value -> draftBorders = value; rebuild() }
            })
            val actions = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.END }
            if (preset.id == "custom") actions.addView(actionButton("Delete", palette, "ThemeDelete") { deleteCustom() }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(46)).apply { setMargins(0, context.dp(4), context.dp(5), context.dp(4)) })
            actions.addView(actionButton("Cancel", palette, "ThemeCancel") { cancelDetail() }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(46)).apply { setMargins(0, context.dp(4), context.dp(5), context.dp(4)) })
            actions.addView(actionButton("Apply", palette, "ThemeApply") { apply(preset) }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(46)).apply { setMargins(0, context.dp(4), 0, context.dp(4)) })
            addView(actions)
        }
    }

    private fun actionButton(label: String, palette: Palette, id: String, onClick: () -> Unit) = TextView(context).apply {
        text = label; tag = id; textSize = 14f; gravity = Gravity.CENTER; isClickable = true; isFocusable = true
        setTextColor(if (label == "Apply") palette.onAccent else palette.text)
        background = if (label == "Apply") palette.shape(palette.accent, context.dp(18).toFloat(), false) else palette.shape(palette.surface, context.dp(18).toFloat(), true)
        setPadding(context.dp(17), 0, context.dp(17), 0); setOnClickListener { onClick() }
    }

    private fun open(preset: ThemePreset) {
        if (!fullSettings) { apply(preset); return }
        previousTheme = currentTheme(); previousBorders = KeyboardPreferences.load(context).keyBorders; draftBorders = previousBorders; selected = preset; rebuild()
    }

    private fun apply(preset: ThemePreset) {
        if (preset.builtIn != null) {
            KeyboardPreferences.save(context, KeyboardPreferences.load(context).copy(theme = preset.builtIn, keyBorders = draftBorders))
        } else if (preset.id == "custom") {
            KeyboardPreferences.save(context, KeyboardPreferences.load(context).copy(theme = KeyboardTheme.CUSTOM, keyBorders = draftBorders))
        } else {
            val definition = ThemeImport.definition(preset.name, preset.palette.copy(keyBorders = draftBorders))
            ThemeImport.save(context, ImportedTheme(preset.name, preset.palette.copy(keyBorders = draftBorders), definition))
        }
        selected = null; changed(); rebuild()
    }

    private fun cancelDetail() {
        if (fullSettings) KeyboardPreferences.save(context, KeyboardPreferences.load(context).copy(theme = previousTheme, keyBorders = previousBorders))
        selected = null; draftBorders = KeyboardPreferences.load(context).keyBorders; rebuild()
    }

    private fun deleteCustom() {
        ThemeImport.delete(context)
        if (currentTheme() == KeyboardTheme.CUSTOM) KeyboardPreferences.save(context, KeyboardPreferences.load(context).copy(theme = KeyboardTheme.MIDNIGHT))
        selected = null; changed(); rebuild()
    }
}
