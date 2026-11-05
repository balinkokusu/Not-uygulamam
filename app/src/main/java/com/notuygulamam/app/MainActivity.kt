package com.notuygulamam.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.text.InputFilter
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val penPalette = intArrayOf(
        Color.BLACK, Color.rgb(90, 90, 90), Color.rgb(25, 90, 200), Color.rgb(200, 40, 40),
        Color.rgb(30, 140, 60), Color.rgb(230, 130, 0), Color.rgb(130, 50, 170),
        Color.rgb(120, 72, 40), Color.rgb(0, 140, 150), Color.rgb(230, 80, 150)
    )
    private val hlPalette = intArrayOf(
        Color.rgb(255, 235, 0), Color.rgb(120, 230, 80), Color.rgb(255, 120, 190),
        Color.rgb(255, 170, 60), Color.rgb(90, 200, 255), Color.rgb(190, 130, 255)
    )
    private val recentColors = ArrayList<Int>()
    private val tabPalette = intArrayOf(
        Color.rgb(236, 196, 244), Color.rgb(246, 196, 226), Color.rgb(250, 186, 196),
        Color.rgb(252, 208, 184), Color.rgb(252, 232, 170), Color.rgb(232, 244, 180),
        Color.rgb(200, 236, 190), Color.rgb(186, 238, 222), Color.rgb(190, 226, 246),
        Color.rgb(196, 208, 246), Color.rgb(212, 198, 244), Color.rgb(226, 196, 240)
    )

    private lateinit var nb: Notebook
    private lateinit var draw: DrawView
    private lateinit var preview: PreviewView
    private lateinit var info: TextView
    private lateinit var sliderLabel: TextView
    private lateinit var pageLabel: TextView
    private lateinit var seek: SeekBar
    private lateinit var swatchRow: LinearLayout
    private lateinit var fingerBtn: IconButton
    private val toolButtons = HashMap<Tool, IconButton>()
    private var sliderMin = 1
    private var toolsVisible = true
    private var settingsPopup: PopupWindow? = null
    private var versionText = ""
    private var spread = false
    private lateinit var spreadBtn: IconButton
    private lateinit var presetRow: LinearLayout
    private lateinit var kindRow: LinearLayout
    private lateinit var itemBar: LinearLayout
    private lateinit var layerBtn: TextView
    private var autoTabs = true

    class PageTemplate(val name: String, val paper: Paper, val header: String, val withDate: Boolean, val builtin: Boolean)
    private var d = 1f

    private val handler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { saveNow() }

    private var itemId: String = ""
    private fun libraryDir(): File = File(filesDir, "library")
    private fun notebookFile(): File =
        if (itemId.isEmpty()) File(filesDir, "defter.bin") else File(libraryDir(), "nb_$itemId.bin")
    private fun pdfDir(): File = File(filesDir, "pdfs")

    private fun dp(v: Float): Int = (v * d).toInt()

    /** Fildişi, ince çerçeveli yuvarlak çubuk zemini. */
    private fun bar(radius: Float): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Look.bar)
        g.cornerRadius = radius
        g.setStroke(dp(1f), Look.hairline)
        return g
    }

    private fun scheduleSave() {
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 2500L)
    }

    private fun saveNow() {
        try {
            Storage.save(nb, notebookFile())
        } catch (e: Exception) {
            // Kaydedilemezse uygulama çalışmaya devam eder.
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(saveRunnable)
        saveNow()
        if (itemId.isNotEmpty()) {
            val st = LibraryStore(libraryDir())
            st.load()
            st.touch(itemId)
        }
    }

    private fun iconBtn(icon: Icon, label: String, onClick: () -> Unit): IconButton {
        val b = IconButton(this, icon, label, true)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(2f), dp(2f), dp(2f), dp(2f))
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        b.setOnLongClickListener {
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
            true
        }
        return b
    }

    private fun separator(): View {
        val v = View(this)
        v.setBackgroundColor(Look.hairline)
        val lp = LinearLayout.LayoutParams(dp(1f), dp(28f))
        lp.setMargins(dp(6f), dp(6f), dp(6f), dp(6f))
        v.layoutParams = lp
        return v
    }

    /** Kaydırıcının hangi aracın değerini ayarladığı; damlalık seçiliyken kalem değerleri gösterilir. */
    private fun sliderTool(): Tool = if (draw.tool == Tool.PICKER) Tool.PEN else draw.tool

    private fun applyColor(c: Int) {
        if (draw.tool == Tool.TEXT || draw.tool == Tool.OBJECT) {
            val sel = draw.selectedItem
            if (sel != null && sel.type == PageItem.TEXT) {
                sel.color = c
                draw.itemEdited()
            }
            draw.penColor = c
            refresh()
            return
        }
        if (draw.tool == Tool.HIGHLIGHTER) {
            draw.hlColor = c
        } else {
            draw.penColor = c
            if (draw.tool == Tool.ERASER || draw.tool == Tool.PICKER) draw.tool = Tool.PEN
        }
        refresh()
    }

    private fun rememberColor(c: Int) {
        recentColors.remove(c)
        recentColors.add(0, c)
        while (recentColors.size > 8) recentColors.removeAt(recentColors.size - 1)
    }

    private fun swatch(c: Int, selected: Boolean): View {
        val v = View(this)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(c)
        if (selected) g.setStroke(dp(3f), Look.accent)
        else g.setStroke(dp(1f), Look.hairline)
        v.background = g
        val lp = LinearLayout.LayoutParams(dp(28f), dp(28f))
        lp.setMargins(dp(4f), dp(3f), dp(4f), dp(3f))
        v.layoutParams = lp
        v.setOnClickListener { applyColor(c) }
        return v
    }

    private fun rebuildSwatches() {
        swatchRow.removeAllViews()
        val highlighter = draw.tool == Tool.HIGHLIGHTER
        val selected = if (highlighter) draw.hlColor else draw.penColor
        swatchRow.addView(colorButton())
        for (c in recentColors) swatchRow.addView(swatch(c, c == selected))
        if (recentColors.isNotEmpty()) swatchRow.addView(separator())
        val palette = if (highlighter) hlPalette else penPalette
        for (c in palette) swatchRow.addView(swatch(c, c == selected))
    }

    private fun kindChip(label: String, hl: Boolean, kind: Int, on: Boolean): View {
        val color = if (hl) draw.hlColor else draw.penColor
        val chip = object : View(this) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val rend = PageRenderer()
            override fun onMeasure(w: Int, h: Int) {
                setMeasuredDimension(dp(92f), dp(60f))
            }

            override fun onDraw(c: android.graphics.Canvas) {
                if (on) {
                    paint.style = android.graphics.Paint.Style.FILL
                    paint.setColor(Look.accentSoft)
                    c.drawRoundRect(3f * d, 3f * d, width - 3f * d, height - 3f * d, 10f * d, 10f * d, paint)
                }
                val k = d / DrawView.UNIT
                val len = (width - 28f * d) / k
                val st = PreviewView.sampleStroke(hl, color, (if (hl) 9f else 3f) * DrawView.UNIT, kind, 0.85f, len)
                c.save()
                c.translate(14f * d, height * 0.4f)
                c.scale(k, k)
                rend.drawStroke(c, st)
                c.restore()
                paint.style = android.graphics.Paint.Style.FILL
                paint.setColor(if (on) Look.accent else Look.ink)
                paint.textSize = 11f * d
                paint.textAlign = android.graphics.Paint.Align.CENTER
                c.drawText(label, width / 2f, height - 9f * d, paint)
            }
        }
        val lp = LinearLayout.LayoutParams(dp(92f), dp(60f))
        lp.setMargins(dp(2f), 0, dp(2f), dp(4f))
        chip.layoutParams = lp
        chip.setOnClickListener {
            if (hl) draw.hlKind = kind else draw.penKind = kind
            refresh()
        }
        return chip
    }

    /** Kalem ve fosforlu için tür seçimi; her türün örnek çizgisi gösterilir. */
    private fun rebuildKinds() {
        kindRow.removeAllViews()
        val st = sliderTool()
        if (st == Tool.ERASER) {
            kindRow.visibility = View.VISIBLE
            val lbl = TextView(this)
            lbl.text = "Silinecek katmanlar:"
            lbl.textSize = 12f
            lbl.setTextColor(Look.inkSoft)
            lbl.setPadding(dp(8f), 0, dp(6f), 0)
            lbl.gravity = Gravity.CENTER_VERTICAL
            kindRow.addView(lbl)
            fun chip(name: String, on: Boolean, flip: () -> Unit) {
                val t = TextView(this)
                t.text = name
                t.textSize = 13f
                t.setPadding(dp(12f), dp(7f), dp(12f), dp(7f))
                t.setTextColor(if (on) Look.accent else Look.inkSoft)
                val g = GradientDrawable()
                g.cornerRadius = dp(12f).toFloat()
                g.setColor(if (on) Look.accentSoft else Color.TRANSPARENT)
                g.setStroke(dp(1f), if (on) Look.accent else Look.hairline)
                t.background = g
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(dp(3f), dp(4f), dp(3f), dp(6f))
                t.layoutParams = lp
                t.setOnClickListener {
                    flip()
                    refresh()
                }
                kindRow.addView(t)
            }
            chip("Çizim", draw.eraseInk) { draw.eraseInk = !draw.eraseInk }
            chip("Yazı", draw.eraseText) { draw.eraseText = !draw.eraseText }
            chip("Çıkartma", draw.eraseSticker) { draw.eraseSticker = !draw.eraseSticker }
            chip("Resim", draw.eraseImage) { draw.eraseImage = !draw.eraseImage }
            return
        }
        if (st != Tool.PEN && st != Tool.HIGHLIGHTER) {
            kindRow.visibility = View.GONE
            return
        }
        kindRow.visibility = View.VISIBLE
        if (st == Tool.PEN) {
            val order = listOf(Pair("Dolma", 0), Pair("Tükenmez", 1), Pair("Keçeli", 4), Pair("Fırça", 3), Pair("Kurşun", 2))
            for ((n, k) in order) kindRow.addView(kindChip(n, false, k, draw.penKind == k))
        } else {
            val names = listOf("Düz", "Pırıltılı", "Yıldız", "Kalp")
            for ((i, n) in names.withIndex()) kindRow.addView(kindChip(n, true, i, draw.hlKind == i))
        }
    }

    private fun todayKey(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 10000 + (cal.get(java.util.Calendar.MONTH) + 1) * 100 +
            cal.get(java.util.Calendar.DAY_OF_MONTH)
    }

    /** Bir günün sayfaları için tek bir otomatik ayraç oluşturur; elle düzenlenmiş ayraçlara dokunmaz. */
    private fun ensureDateTab(pg: PageData) {
        if (!autoTabs || pg.dateKey == 0) return
        val first = nb.pages.firstOrNull { it.dateKey == pg.dateKey } ?: return
        val existing = nb.tabs.firstOrNull { it.target.dateKey == pg.dateKey }
        if (existing != null) {
            if (existing.auto && existing.target !== first) existing.target = first
            return
        }
        val mo = pg.dateKey / 100 % 100
        val day = pg.dateKey % 100
        if (mo !in 1..12) return
        nb.tabs.add(Tab("$day ${Planner.SHORT[mo - 1]}", tabPalette[(mo - 1) % tabPalette.size], first, true))
    }

    /** Kuru bir sayfaya ilk kez yazılınca sayfaya o günün tarihini verir ve gün ayracını oluşturur. */
    private fun markWritten() {
        val pg = draw.page
        if (autoTabs && pg.dateKey == 0 && pg.pdfName == null && pg.paper != Paper.YEAR && pg.paper != Paper.MONTHLY) {
            pg.dateKey = todayKey()
            ensureDateTab(pg)
            rebuildTabs()
        }
    }

    // ---- Sayfa şablonlarım ----

    private fun builtinTemplates() = listOf(
        PageTemplate("Günlük not", Paper.RULED, "Günlük not", true, true),
        PageTemplate("Alışveriş listesi", Paper.TODO, "Alışveriş listesi", true, true),
        PageTemplate("Toplantı notu", Paper.CORNELL, "Toplantı notu", true, true),
        PageTemplate("Günlük ajanda", Paper.DAILY, "", true, true),
        PageTemplate("Boş sayfa", Paper.BLANK, "", false, true)
    )

    private fun customTemplates(): List<PageTemplate> {
        val raw = getSharedPreferences("sablon", MODE_PRIVATE).getString("liste", "") ?: ""
        val out = ArrayList<PageTemplate>()
        for (line in raw.split('\n')) {
            val f = line.split('\u001f')
            if (f.size < 4) continue
            val po = f[1].toIntOrNull() ?: continue
            if (po !in 0 until Paper.values().size) continue
            out.add(PageTemplate(f[0], Paper.values()[po], f[2], f[3] == "1", false))
        }
        return out
    }

    private fun saveCustomTemplates(list: List<PageTemplate>) {
        val raw = list.joinToString("\n") { "${it.name.replace('\n', ' ')}\u001f${it.paper.ordinal}\u001f${it.header.replace('\n', ' ')}\u001f${if (it.withDate) 1 else 0}" }
        getSharedPreferences("sablon", MODE_PRIVATE).edit().putString("liste", raw).apply()
    }

    private fun addTemplatePage(t: PageTemplate) {
        val cur = draw.page
        val pdf = cur.pdfName != null
        val pg = PageData(if (pdf) 1200f else cur.width, if (pdf) 1697f else cur.height, t.paper)
        if (!pdf) pg.paperColor = cur.paperColor
        if (t.withDate) pg.dateKey = todayKey()
        val title = StringBuilder(t.header)
        if (t.withDate) {
            val fmt = SimpleDateFormat("d MMMM yyyy, EEEE", Locale("tr"))
            if (title.isNotEmpty()) title.append("  ·  ")
            title.append(fmt.format(Date()))
        }
        if (title.isNotEmpty()) {
            val it = PageItem(PageItem.TEXT, 0f, 0f)
            it.text = title.toString()
            it.size = 30f
            it.color = Color.rgb(120, 108, 110)
            it.w = pg.width - 140f
            PageRenderer().measureItem(it)
            it.x = 70f + it.w / 2f
            it.y = 14f + it.h / 2f
            pg.items.add(it)
        }
        val at = nb.pages.indexOf(cur).let { if (it < 0) nb.current else it } + 1
        nb.pages.add(at, pg)
        gotoPage(at)
        if (t.withDate) {
            ensureDateTab(pg)
            refresh()
        }
    }

    private fun showNewPageMenu(anchor: View) {
        val m = android.widget.PopupMenu(this, anchor)
        m.menu.add(0, 1000, 0, "Aynı sayfa gibi yeni sayfa")
        val all = builtinTemplates() + customTemplates()
        for ((i, t) in all.withIndex()) m.menu.add(0, 2000 + i, 1 + i, t.name + "  ·  " + t.paper.label)
        m.menu.add(0, 1001, 100, "Kâğıt şablonlarını gör…")
        m.menu.add(0, 1002, 101, "Şablonlarım: yeni oluştur / sil…")
        m.menu.add(0, 1003, 102, "Planlayıcıyı bu deftere ekle…")
        m.setOnMenuItemClickListener { mi ->
            when {
                mi.itemId == 1000 -> addPage()
                mi.itemId == 1001 -> showTemplatePageDialog()
                mi.itemId == 1002 -> showTemplateManager()
                mi.itemId == 1003 -> showAddPlannerDialog()
                mi.itemId >= 2000 -> all.getOrNull(mi.itemId - 2000)?.let { addTemplatePage(it) }
            }
            true
        }
        m.show()
    }

    private fun showAddPlannerDialog() {
        val now = java.util.Calendar.getInstance()
        val y = now.get(java.util.Calendar.YEAR) + if (now.get(java.util.Calendar.MONTH) >= 8) 1 else 0
        val years = intArrayOf(y, y + 1, y - 1)
        AlertDialog.Builder(this)
            .setTitle("Hangi yılın planlayıcısı eklensin?")
            .setItems(years.map { "Planlayıcı $it" }.toTypedArray()) { _, which ->
                val cur = draw.page
                val built = Planner.build(years[which])
                for (pg in built.pages) {
                    if (cur.pdfName == null) {
                        pg.width = cur.width
                        pg.height = cur.height
                        pg.paperColor = cur.paperColor
                    }
                }
                val start = nb.pages.size
                nb.pages.addAll(built.pages)
                nb.tabs.addAll(built.tabs)
                gotoPage(start)
                Toast.makeText(this, "Planlayıcı defterin sonuna eklendi (sayfa ${start + 1})", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun showTemplateManager() {
        val custom = customTemplates()
        val labels = custom.map { it.name + "  ·  " + it.paper.label + "  (silmek için dokunun)" } + "＋ Yeni şablon oluştur"
        AlertDialog.Builder(this)
            .setTitle("Şablonlarım")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == custom.size) {
                    showNewTemplateDialog()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("\"${custom[which].name}\" silinsin mi?")
                        .setPositiveButton("Sil") { _, _ ->
                            saveCustomTemplates(custom.filterIndexed { i, _ -> i != which })
                        }
                        .setNegativeButton("Vazgeç", null)
                        .show()
                }
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun showNewTemplateDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
        val nameEdit = EditText(this)
        nameEdit.hint = "Şablon adı (örneğin Proje toplantısı)"
        nameEdit.setSingleLine(true)
        val headEdit = EditText(this)
        headEdit.hint = "Sayfa başlığı (boş bırakılabilir)"
        headEdit.setSingleLine(true)
        val dateBox = CheckBox(this)
        dateBox.text = "Başlığa bugünün tarihini yaz, gün ayracına bağla"
        dateBox.isChecked = true
        box.addView(nameEdit)
        box.addView(headEdit)
        box.addView(dateBox)
        var paperIdx = Paper.RULED.ordinal
        val grid = PaperGrid(this, Paper.values().map { Pair(it.label, it) }, paperIdx, draw.page.paperColor) { paperIdx = it }
        box.addView(grid)
        val sv = ScrollView(this)
        sv.addView(box)
        AlertDialog.Builder(this)
            .setTitle("Yeni sayfa şablonu")
            .setView(sv)
            .setPositiveButton("Kaydet") { _, _ ->
                val name = nameEdit.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Şablona bir ad vermelisiniz", Toast.LENGTH_SHORT).show()
                } else {
                    saveCustomTemplates(
                        customTemplates() + PageTemplate(name, Paper.values()[paperIdx], headEdit.text.toString().trim(), dateBox.isChecked, false)
                    )
                    Toast.makeText(this, "Şablon kaydedildi; artık + düğmesindeki listede", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    // ---- Metin ve çıkartma ----

    private fun showTextDialog(item: PageItem?, x: Float, y: Float) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(18f), dp(8f), dp(18f), dp(4f))
        val et = EditText(this)
        et.setText(item?.text ?: "")
        et.hint = "Yazınızı yazın (klavye ya da klavyenizin el yazısı girişi)"
        et.minLines = 3
        et.gravity = Gravity.TOP
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            try {
                et.setAutoHandwritingEnabled(true)
            } catch (e: Throwable) {
                // Bu cihazda desteklenmiyorsa klavye girişi yine çalışır.
            }
        }
        box.addView(et)
        var fontIdx = (item?.kind ?: 0) and 255
        val boldBox = CheckBox(this)
        boldBox.text = "Kalın"
        boldBox.isChecked = item != null && (item.kind and 256) != 0
        val italicBox = CheckBox(this)
        italicBox.text = "İtalik"
        italicBox.isChecked = item != null && (item.kind and 512) != 0
        val fontRow = LinearLayout(this)
        fontRow.orientation = LinearLayout.HORIZONTAL
        val fontChips = ArrayList<TextView>()
        fun paintFonts() {
            for ((i, t) in fontChips.withIndex()) {
                val g = GradientDrawable()
                g.cornerRadius = dp(10f).toFloat()
                g.setColor(if (i == fontIdx) Look.accentSoft else Color.TRANSPARENT)
                g.setStroke(dp(if (i == fontIdx) 2f else 1f), if (i == fontIdx) Look.accent else Look.hairline)
                t.background = g
            }
        }
        for (i in Fonts.names.indices) {
            val t = TextView(this)
            t.text = "Aa Ğş\n" + Fonts.names[i]
            t.gravity = Gravity.CENTER
            t.textSize = 15f
            t.typeface = Fonts.get(i, android.graphics.Typeface.NORMAL)
            t.setTextColor(Look.ink)
            t.setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(3f), dp(8f), dp(3f), dp(4f))
            t.layoutParams = lp
            t.setOnClickListener {
                fontIdx = i
                paintFonts()
            }
            fontChips.add(t)
            fontRow.addView(t)
        }
        paintFonts()
        val fhs = HorizontalScrollView(this)
        fhs.addView(fontRow)
        box.addView(fhs)
        val styleRow = LinearLayout(this)
        styleRow.orientation = LinearLayout.HORIZONTAL
        styleRow.addView(boldBox)
        styleRow.addView(italicBox)
        box.addView(styleRow)
        val sizes = intArrayOf(32, 44, 60, 90)
        val sizeNames = arrayOf("Küçük", "Orta", "Büyük", "Çok büyük")
        val spin = Spinner(this)
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sizeNames)
        var si = 1
        if (item != null) {
            var best = 1e9f
            for ((i, s) in sizes.withIndex()) if (Math.abs(item.size - s) < best) { best = Math.abs(item.size - s); si = i }
        }
        spin.setSelection(si)
        box.addView(spin)
        var chosen = item?.color ?: draw.penColor
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val dots = ArrayList<Pair<View, Int>>()
        fun paintDots() {
            for ((v, c) in dots) {
                val g = GradientDrawable()
                g.shape = GradientDrawable.OVAL
                g.setColor(c)
                if (c == chosen) g.setStroke(dp(3f), Look.accent) else g.setStroke(dp(1f), Look.hairline)
                v.background = g
            }
        }
        for (c in penPalette) {
            val v = View(this)
            val lp = LinearLayout.LayoutParams(dp(30f), dp(30f))
            lp.setMargins(dp(3f), dp(8f), dp(3f), dp(4f))
            v.layoutParams = lp
            v.setOnClickListener {
                chosen = c
                paintDots()
            }
            dots.add(Pair(v, c))
            row.addView(v)
        }
        paintDots()
        val hs = HorizontalScrollView(this)
        hs.addView(row)
        box.addView(hs)
        val b = AlertDialog.Builder(this)
            .setTitle(if (item == null) "Metin kutusu" else "Metni düzenle")
            .setView(box)
            .setPositiveButton("Tamam") { _, _ ->
                val txt = et.text.toString()
                if (txt.isNotBlank()) {
                    if (item == null) {
                        val it = PageItem(PageItem.TEXT, x, y)
                        it.text = txt
                        it.size = sizes[spin.selectedItemPosition].toFloat()
                        it.color = chosen
                        it.kind = fontIdx or (if (boldBox.isChecked) 256 else 0) or (if (italicBox.isChecked) 512 else 0)
                        PageRenderer().fitText(it, minOf(640f, draw.page.width - x - 30f).coerceAtLeast(300f))
                        draw.addItem(it, true)
                    } else {
                        item.text = txt
                        item.size = sizes[spin.selectedItemPosition].toFloat()
                        item.color = chosen
                        item.kind = fontIdx or (if (boldBox.isChecked) 256 else 0) or (if (italicBox.isChecked) 512 else 0)
                        PageRenderer().fitText(item, maxOf(item.w, 400f).coerceAtMost(draw.page.width * 0.9f))
                        draw.itemEdited()
                    }
                }
            }
            .setNegativeButton("Vazgeç", null)
        if (item != null) b.setNeutralButton("Sil") { _, _ -> draw.deleteSelected() }
        val dlg = b.create()
        dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }

    private fun showStickerDialog() {
        var dlg: AlertDialog? = null
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        grid.setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
        var row: LinearLayout? = null
        for (k in Stickers.names.indices) {
            if (k % 5 == 0) {
                row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                grid.addView(row)
            }
            val v = object : View(this) {
                override fun onMeasure(w: Int, h: Int) {
                    setMeasuredDimension(dp(86f), dp(86f))
                }

                override fun onDraw(c: android.graphics.Canvas) {
                    val asp = Stickers.aspect(k)
                    val box = 62f * d
                    val sw = if (asp >= 1f) box else box * asp
                    val sh = if (asp >= 1f) box / asp else box
                    c.save()
                    c.translate(width / 2f, height / 2f - 6f * d)
                    Stickers.draw(c, k, sw, sh)
                    c.restore()
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    p.setColor(Look.inkSoft)
                    p.textSize = 10f * d
                    p.textAlign = android.graphics.Paint.Align.CENTER
                    c.drawText(Stickers.names[k], width / 2f, height - 4f * d, p)
                }
            }
            v.setOnClickListener {
                val asp = Stickers.aspect(k)
                var w = 240f * asp
                var h = 240f
                if (w > 560f) {
                    w = 560f
                    h = w / asp
                }
                val c = draw.viewCenterOnPage()
                val it = PageItem(PageItem.STICKER, c[0], c[1])
                it.kind = k
                it.w = w
                it.h = h
                draw.tool = Tool.OBJECT
                draw.addItem(it)
                refresh()
                dlg?.dismiss()
            }
            row?.addView(v)
        }
        val sv = ScrollView(this)
        sv.addView(grid)
        dlg = AlertDialog.Builder(this)
            .setTitle("Çıkartmalar")
            .setView(sv)
            .setNeutralButton("Galeriden resim ekle") { _, _ -> pickImage() }
            .setPositiveButton("Seç ve taşı") { _, _ ->
                draw.tool = Tool.OBJECT
                refresh()
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        startActivityForResult(intent, REQ_IMAGE)
    }

    private fun addImageFrom(uri: Uri) {
        Thread {
            try {
                val mime = contentResolver.getType(uri) ?: ""
                val bounds = android.graphics.BitmapFactory.Options()
                bounds.inJustDecodeBounds = true
                contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
                val opts = android.graphics.BitmapFactory.Options()
                opts.inSampleSize = sample
                var bmp = contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                    ?: throw java.io.IOException("Resim açılamadı")
                val maxSide = 1100f
                val sc = maxSide / maxOf(bmp.width, bmp.height)
                if (sc < 1f) {
                    bmp = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt(), (bmp.height * sc).toInt(), true)
                }
                val png = mime.contains("png") || mime.contains("webp") || mime.contains("gif")
                val bos = java.io.ByteArrayOutputStream()
                if (png) bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
                else bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, bos)
                val bytes = bos.toByteArray()
                val b = bmp
                runOnUiThread {
                    val c = draw.viewCenterOnPage()
                    val it = PageItem(PageItem.IMAGE, c[0], c[1])
                    val asp = b.width.toFloat() / b.height
                    it.w = if (asp >= 1f) 520f else 520f * asp
                    it.h = it.w / asp
                    it.data = bytes
                    it.bmp = b
                    draw.tool = Tool.OBJECT
                    draw.addItem(it)
                    refresh()
                }
            } catch (e: Throwable) {
                runOnUiThread { Toast.makeText(this, "Resim eklenemedi", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    override fun dispatchKeyEvent(e: android.view.KeyEvent): Boolean {
        if (e.action == android.view.KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
            val k = e.keyCode
            if (k !in intArrayOf(android.view.KeyEvent.KEYCODE_BACK, 24, 25)) {
                info.text = "v$versionText · son tuş kodu: $k (kalem=" + (if (draw.tool == Tool.ERASER) "silgi" else "kalem") + ")"
            }
            // 308-311: kalem düğmesi/dokunuşu tuş olayları (STYLUS_BUTTON_PRIMARY... TAP)
            // 718: bu cihazda kalemin çift dokunuşu/düğmesi olarak görülen tuş kodu
            if ((k in 308..311 || k == 718) && draw.penDoubleTap) {
                draw.togglePenEraser()
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    private fun toggleSettings(anchor: View) {
        val p = settingsPopup ?: return
        if (p.isShowing) p.dismiss() else p.showAsDropDown(anchor, 0, dp(2f))
    }

    private fun setToolWidth(v: Float) {
        when (sliderTool()) {
            Tool.HIGHLIGHTER -> draw.hlWidthDp = v
            Tool.ERASER -> draw.eraserRadiusDp = v
            else -> draw.penWidthDp = v
        }
        refresh()
    }

    /** Kalınlık seviyelerini, seçili aracın altında açılan listede hızlı seçim olarak gösterir. */
    private fun rebuildPresets() {
        presetRow.removeAllViews()
        val st = sliderTool()
        val values = when (st) {
            Tool.HIGHLIGHTER -> floatArrayOf(8f, 14f, 22f, 32f)
            Tool.ERASER -> floatArrayOf(8f, 14f, 22f, 30f)
            else -> floatArrayOf(1f, 2f, 4f, 7f, 12f, 20f)
        }
        val cur = when (st) {
            Tool.HIGHLIGHTER -> draw.hlWidthDp
            Tool.ERASER -> draw.eraserRadiusDp
            else -> draw.penWidthDp
        }
        val color = when (st) {
            Tool.HIGHLIGHTER -> draw.hlColor
            Tool.ERASER -> Color.rgb(120, 124, 132)
            else -> draw.penColor
        }
        for (v in values) {
            val on = Math.abs(cur - v) < 0.5f
            val chip = object : View(this) {
                private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(c: android.graphics.Canvas) {
                    val dia = (4f + v * 1.1f).coerceAtMost(28f) * d
                    val cx = width / 2f
                    val cy = height / 2f
                    if (on) {
                        paint.style = android.graphics.Paint.Style.FILL
                        paint.setColor(Look.accentSoft)
                        c.drawRoundRect(3f * d, 3f * d, width - 3f * d, height - 3f * d, 10f * d, 10f * d, paint)
                    }
                    paint.style = android.graphics.Paint.Style.FILL
                    paint.setColor(color)
                    c.drawCircle(cx, cy, dia / 2f, paint)
                }
            }
            val lp = LinearLayout.LayoutParams(dp(48f), dp(44f))
            lp.setMargins(dp(2f), 0, dp(2f), dp(4f))
            chip.layoutParams = lp
            chip.setOnClickListener { setToolWidth(v) }
            presetRow.addView(chip)
        }
    }

    private fun colorButton(): View {
        val t = TextView(this)
        t.text = "Renk seç"
        t.textSize = 13f
        t.setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
        t.setTextColor(Look.accent)
        val g = GradientDrawable()
        g.setColor(Look.accentSoft)
        g.cornerRadius = dp(10f).toFloat()
        t.background = g
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(6f), dp(4f), dp(6f), dp(4f))
        t.layoutParams = lp
        t.setOnClickListener { showColorDialog() }
        return t
    }

    private fun showColorDialog() {
        val initial = if (draw.tool == Tool.HIGHLIGHTER) draw.hlColor else draw.penColor
        val picker = ColorPickerView(this, initial)
        AlertDialog.Builder(this)
            .setTitle("Renk seç")
            .setView(picker)
            .setPositiveButton("Seç") { _, _ ->
                rememberColor(picker.color)
                applyColor(picker.color)
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun refresh() {
        for ((t, b) in toolButtons) b.active = draw.tool == t
        fingerBtn.active = draw.fingerDraw
        pageLabel.text = if (spread && nb.current + 1 < nb.pages.size)
            " ${nb.current + 1}–${nb.current + 2} / ${nb.pages.size} "
        else " ${nb.current + 1} / ${nb.pages.size} "
        spreadBtn.active = spread
        rebuildPresets()
        rebuildKinds()
        refreshItemBar()
        rebuildTabs()

        val st = sliderTool()
        val range = when (st) {
            Tool.HIGHLIGHTER -> Pair(6, 40)
            Tool.ERASER -> Pair(6, 30)
            else -> Pair(1, 24)
        }
        val cur = when (st) {
            Tool.HIGHLIGHTER -> draw.hlWidthDp
            Tool.ERASER -> draw.eraserRadiusDp
            else -> draw.penWidthDp
        }
        sliderMin = range.first
        seek.max = range.second - range.first
        seek.progress = (cur - range.first).toInt()
        sliderLabel.text = sliderText(cur)

        rebuildSwatches()
        preview.invalidate()
    }

    private fun sliderText(v: Float): String {
        val name = if (sliderTool() == Tool.ERASER) "Silgi boyutu: " else "Kalınlık: "
        return name + v.toInt()
    }

    private fun step(): Int = if (spread) 2 else 1

    /** Açık sayfa(lar)ı çizim alanına yükler; çift sayfa görünümünde çift numaralı sayfadan başlayan ikiliyi gösterir. */
    private fun showCurrent() {
        if (spread) {
            nb.current -= nb.current % 2
            if (nb.current + 1 >= nb.pages.size) {
                // Çift sayfa görünümünde her zaman bir ikili olsun diye boş eş sayfa eklenir.
                val l = nb.pages[nb.current]
                val np = if (l.pdfName == null) PageData(l.width, l.height, l.paper) else PageData(1200f, 1697f, Paper.BLANK)
                if (l.pdfName == null) np.paperColor = l.paperColor
                nb.pages.add(np)
                scheduleSave()
            }
        }
        val left = nb.pages[nb.current]
        val right = if (spread && nb.current + 1 < nb.pages.size) nb.pages[nb.current + 1] else null
        draw.showPage(left, right)
    }

    private fun gotoPage(i: Int) {
        if (i < 0 || i >= nb.pages.size) return
        nb.current = i
        showCurrent()
        refresh()
        scheduleSave()
    }

    private fun addPage() {
        val cur = draw.page
        val at = nb.pages.indexOf(cur).let { if (it < 0) nb.current else it } + 1
        val np = PageData(cur.width, cur.height, cur.paper)
        np.paperColor = cur.paperColor
        nb.pages.add(at, np)
        gotoPage(at)
    }

    private fun deleteCurrentPage() {
        val idx = nb.pages.indexOf(draw.page).let { if (it < 0) nb.current else it }
        if (!removePageAt(idx)) return
        showCurrent()
        refresh()
        scheduleSave()
    }

    private fun pickPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/pdf"
        startActivityForResult(intent, REQ_PDF)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_PDF -> importPdfFrom(uri)
            REQ_EXPORT -> exportPdfTo(uri)
            REQ_BACKUP -> backupTo(uri)
            REQ_RESTORE -> restoreFrom(uri)
            REQ_IMAGE -> addImageFrom(uri)
        }
    }

    private fun importPdfFrom(uri: Uri) {
        Toast.makeText(this, "PDF ekleniyor...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val name = PdfStore.importPdf(this, uri, pdfDir())
                val src = PdfStore.source(pdfDir(), name)
                val sizes = ArrayList<Pair<Int, Int>>()
                for (i in 0 until src.pageCount()) sizes.add(src.pageSize(i))
                runOnUiThread { insertPdfPages(name, sizes) }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "PDF açılamadı (şifreli ya da bozuk olabilir)", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun saveWithMessage() {
        handler.removeCallbacks(saveRunnable)
        try {
            Storage.save(nb, notebookFile())
            val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            Toast.makeText(
                this,
                "Defter uygulamanın içine kaydedildi ($t). Dosya olarak almak için \"PDF kaydet\" ya da \"Yedek\" düğmesini kullanın.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Kaydedilemedi", Toast.LENGTH_LONG).show()
        }
    }

    private fun showBackupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Yedek")
            .setItems(arrayOf("Yedek dosyası oluştur (yeri siz seçersiniz)", "Yedek dosyasından geri yükle")) { _, which ->
                if (which == 0) {
                    saveNow()
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.type = "application/zip"
                    intent.putExtra(Intent.EXTRA_TITLE, "NotUygulamam-yedek.zip")
                    startActivityForResult(intent, REQ_BACKUP)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Geri yükle")
                        .setMessage("Seçtiğiniz yedek şu anki defterin yerine geçecek. Şu anki defter uygulama içinde bir önceki sürüm olarak saklanır. Devam edilsin mi?")
                        .setPositiveButton("Dosya seç") { _, _ ->
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                            intent.addCategory(Intent.CATEGORY_OPENABLE)
                            intent.type = "application/zip"
                            startActivityForResult(intent, REQ_RESTORE)
                        }
                        .setNegativeButton("Vazgeç", null)
                        .show()
                }
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun backupTo(uri: Uri) {
        Toast.makeText(this, "Yedek hazırlanıyor...", Toast.LENGTH_SHORT).show()
        val nbFile = notebookFile()
        val dir = pdfDir()
        Thread {
            try {
                val out = contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Dosya açılamadı")
                out.use { Backup.write(nbFile, dir, it) }
                runOnUiThread { Toast.makeText(this, "Yedek kaydedildi", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Yedek kaydedilemedi", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun restoreFrom(uri: Uri) {
        Toast.makeText(this, "Yedek yükleniyor...", Toast.LENGTH_SHORT).show()
        val nbFile = notebookFile()
        val dir = pdfDir()
        Thread {
            var ok = false
            try {
                val input = contentResolver.openInputStream(uri) ?: throw java.io.IOException("Dosya açılamadı")
                ok = input.use { Backup.restore(it, nbFile, dir) }
            } catch (e: Exception) {
                ok = false
            }
            runOnUiThread {
                val loaded = if (ok) Storage.load(nbFile) else null
                if (loaded != null) {
                    nb = loaded
                    showCurrent()
                    refresh()
                    Toast.makeText(this, "Yedek yüklendi", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Yedek yüklenemedi", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun startExport() {
        saveNow()
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/pdf"
        intent.putExtra(Intent.EXTRA_TITLE, "defter.pdf")
        startActivityForResult(intent, REQ_EXPORT)
    }

    private fun exportPdfTo(uri: Uri) {
        Toast.makeText(this, "PDF hazırlanıyor, sayfa sayısına göre biraz sürebilir...", Toast.LENGTH_LONG).show()
        val snapshot = nb.pages.map { it.snapshot() }
        val dir = pdfDir()
        Thread {
            try {
                val out = contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Dosya açılamadı")
                out.use { PdfExporter.export(snapshot, dir, it) }
                runOnUiThread { Toast.makeText(this, "PDF kaydedildi", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "PDF kaydedilemedi", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun insertPdfPages(name: String, sizes: List<Pair<Int, Int>>) {
        if (sizes.isEmpty()) return
        val at = nb.current + 1
        for (i in sizes.indices) {
            val (w, h) = sizes[i]
            val pg = PageData(1200f, 1200f * h / w, Paper.BLANK)
            pg.pdfName = name
            pg.pdfIndex = i
            nb.pages.add(at + i, pg)
        }
        gotoPage(at)
        Toast.makeText(this, "${sizes.size} sayfa eklendi", Toast.LENGTH_SHORT).show()
    }

    private fun rebuildTabs() {
        val sorted = nb.tabs.sortedBy { nb.pages.indexOf(it.target) }
        var active: Tab? = null
        for (t in sorted) if (nb.pages.indexOf(t.target) <= nb.current) active = t
        draw.setTabs(sorted, active)
    }

    private fun showTabDialog(existing: Tab?) {
        val pad = dp(16f)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, dp(8f), pad, 0)

        val nameEdit = EditText(this)
        nameEdit.setSingleLine(true)
        nameEdit.hint = "Ayraç adı (kısa olursa iyi görünür)"
        nameEdit.filters = arrayOf(InputFilter.LengthFilter(24))
        nameEdit.setText(existing?.label ?: (nb.tabs.size + 1).toString())
        nameEdit.setSelectAllOnFocus(true)
        box.addView(nameEdit)

        var chosen = existing?.color ?: tabPalette[nb.tabs.size % tabPalette.size]
        val swatches = ArrayList<Pair<View, Int>>()
        fun paint() {
            for ((v, c) in swatches) {
                val g = GradientDrawable()
                g.shape = GradientDrawable.OVAL
                g.setColor(c)
                if (c == chosen) g.setStroke(dp(4f), Look.accent) else g.setStroke(dp(1f), Color.LTGRAY)
                v.background = g
            }
        }
        val colorLabel = TextView(this)
        colorLabel.text = "Renk"
        colorLabel.setPadding(0, dp(12f), 0, dp(2f))
        box.addView(colorLabel)
        for (rowStart in 0 until tabPalette.size step 6) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (k in rowStart until minOf(rowStart + 6, tabPalette.size)) {
                val c = tabPalette[k]
                val v = View(this)
                val lp = LinearLayout.LayoutParams(dp(38f), dp(38f))
                lp.setMargins(dp(4f), dp(4f), dp(4f), dp(4f))
                v.layoutParams = lp
                v.setOnClickListener {
                    chosen = c
                    paint()
                }
                swatches.add(Pair(v, c))
                row.addView(v)
            }
            box.addView(row)
        }
        paint()

        val pageLabelText = TextView(this)
        pageLabelText.text = "Gideceği sayfa"
        pageLabelText.setPadding(0, dp(12f), 0, dp(2f))
        box.addView(pageLabelText)
        val spin = Spinner(this)
        val names = nb.pages.mapIndexed { i, _ -> "Sayfa ${i + 1}" }
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        val startIdx = if (existing != null) nb.pages.indexOf(existing.target) else nb.current
        spin.setSelection(startIdx.coerceIn(0, nb.pages.size - 1))
        box.addView(spin)

        val scroll = ScrollView(this)
        scroll.addView(box)
        val b = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Yeni ayraç" else "Ayracı düzenle")
            .setView(scroll)
            .setPositiveButton(if (existing == null) "Ekle" else "Kaydet") { _, _ ->
                val label = nameEdit.text.toString().trim().ifEmpty { (nb.tabs.size + 1).toString() }
                val target = nb.pages[spin.selectedItemPosition.coerceIn(0, nb.pages.size - 1)]
                if (existing == null) {
                    nb.tabs.add(Tab(label, chosen, target))
                } else {
                    existing.label = label
                    existing.color = chosen
                    existing.target = target
                }
                refresh()
                scheduleSave()
            }
            .setNegativeButton("Vazgeç", null)
        if (existing != null) {
            b.setNeutralButton("Ayracı sil") { _, _ ->
                nb.tabs.remove(existing)
                refresh()
                scheduleSave()
            }
        }
        b.show()
    }

    private fun showMoreMenu(anchor: View) {
        val m = android.widget.PopupMenu(this, anchor)
        val names = arrayOf(
            "PDF ekle", "Şablonla yeni sayfa", "Kaydet", "PDF olarak dışa aktar",
            "Bu defterin yedeği", "Bu sayfadaki çizimleri temizle", "Bu sayfayı sil",
            "Sayfa boyutu ve kâğıt rengi",
            "Otomatik tarih ayracı: " + (if (autoTabs) "açık (kapatmak için dokunun)" else "kapalı (açmak için dokunun)"),
            "Kalemle çift dokunuş (kalem ↔ silgi): " + (if (draw.penDoubleTap) "açık" else "kapalı"),
            "Şablonlarım",
            "Planlayıcıyı bu deftere ekle (yıllık takvim, 12 ay, ayraçlar)",
            "Cetvel: " + (if (draw.rulerOn) "açık (kapatmak için dokunun)" else "kapalı (açmak için dokunun)"),
            "Tablo ekle (satır ve sütun sayısı seçerek)",
            "Yapışkan not ekle"
        )
        for ((i, n) in names.withIndex()) m.menu.add(0, i, i, n)
        m.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                0 -> pickPdf()
                1 -> showNewPageMenu(anchor)
                2 -> saveWithMessage()
                3 -> startExport()
                4 -> showBackupDialog()
                5 -> draw.clearAll()
                6 -> confirmDeletePage()
                7 -> showPageStyleDialog()
                8 -> {
                    autoTabs = !autoTabs
                    getSharedPreferences("ayar", MODE_PRIVATE).edit().putBoolean("otoayrac_$itemId", autoTabs).apply()
                    Toast.makeText(this, if (autoTabs) "Gün ayraçları otomatik atanacak" else "Otomatik ayraç kapatıldı", Toast.LENGTH_SHORT).show()
                }
                9 -> {
                    draw.penDoubleTap = !draw.penDoubleTap
                    getSharedPreferences("ayar", MODE_PRIVATE).edit().putBoolean("kalem_cift", draw.penDoubleTap).apply()
                }
                10 -> showTemplateManager()
                11 -> showAddPlannerDialog()
                12 -> {
                    val on = draw.toggleRuler()
                    if (on) Toast.makeText(this, "Cetvel: parmakla taşıyın, uçlarından döndürün. Kalemle yakınından çizerseniz çizgi cetvele oturur.", Toast.LENGTH_LONG).show()
                }
                13 -> showTableDialog()
                14 -> showStickyDialog()
            }
            true
        }
        m.show()
    }

    private fun showTableDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20f), dp(8f), dp(20f), dp(8f))
        fun spin(label: String, max: Int, def: Int): android.widget.NumberPicker {
            val t = TextView(this); t.text = label; box.addView(t)
            val np = android.widget.NumberPicker(this)
            np.minValue = 1; np.maxValue = max; np.value = def
            box.addView(np)
            return np
        }
        val rows = spin("Satır sayısı", 20, 4)
        val cols = spin("Sütun sayısı", 10, 3)
        AlertDialog.Builder(this).setTitle("Tablo ekle").setView(box)
            .setPositiveButton("Ekle") { _, _ -> draw.addTable(rows.value, cols.value) }
            .setNegativeButton("Vazgeç", null).show()
    }

    private fun showStickyDialog() {
        val colors = intArrayOf(
            Color.rgb(255, 238, 140), Color.rgb(255, 205, 210), Color.rgb(200, 230, 201), Color.rgb(187, 222, 251)
        )
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20f), dp(8f), dp(20f), dp(8f))
        val et = EditText(this)
        et.hint = "Not metni (boş bırakıp kalemle de yazabilirsiniz)"
        et.minLines = 3
        box.addView(et)
        val grp = android.widget.RadioGroup(this)
        grp.orientation = LinearLayout.HORIZONTAL
        val names = arrayOf("Sarı", "Pembe", "Yeşil", "Mavi")
        for (i in names.indices) {
            val rb = android.widget.RadioButton(this); rb.text = names[i]; rb.id = 100 + i
            grp.addView(rb)
        }
        grp.check(100)
        box.addView(grp)
        AlertDialog.Builder(this).setTitle("Yapışkan not").setView(box)
            .setPositiveButton("Ekle") { _, _ ->
                val col = colors[(grp.checkedRadioButtonId - 100).coerceIn(0, 3)]
                val bmp = makeStickyBitmap(et.text.toString(), col)
                val bos = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
                val c = draw.viewCenterOnPage()
                val it = PageItem(PageItem.IMAGE, c[0], c[1])
                it.w = 460f; it.h = 460f
                it.data = bos.toByteArray(); it.bmp = bmp; it.above = false
                draw.tool = Tool.OBJECT
                draw.addItem(it)
                refresh()
            }
            .setNegativeButton("Vazgeç", null).show()
    }

    private fun makeStickyBitmap(text: String, color: Int): android.graphics.Bitmap {
        val s = 460
        val bmp = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = Color.argb(60, 0, 0, 0)
        c.drawRect(22f, 26f, s - 6f, s - 4f, p)
        p.color = color
        c.drawRect(14f, 14f, s - 14f, s - 14f, p)
        p.color = Color.argb(40, 0, 0, 0)
        val fold = android.graphics.Path()
        fold.moveTo(s - 14f, s - 90f); fold.lineTo(s - 90f, s - 14f); fold.lineTo(s - 14f, s - 14f); fold.close()
        c.drawPath(fold, p)
        if (text.isNotBlank()) {
            val tp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            tp.color = Color.rgb(60, 50, 40); tp.textSize = 36f
            tp.typeface = Fonts.get(2, 0)
            val lay = android.text.StaticLayout.Builder.obtain(text, 0, text.length, tp, s - 80).build()
            c.save(); c.translate(40f, 40f); lay.draw(c); c.restore()
        }
        return bmp
    }

    private fun showPageStyleDialog() {
        val cur = draw.page
        val chooser = PageStyleChooser(this, cur.width, cur.height, cur.paperColor)
        val all = CheckBox(this)
        all.text = "Defterdeki tüm sayfalara uygula (PDF sayfaları hariç)"
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20f), dp(8f), dp(20f), dp(8f))
        box.addView(chooser)
        box.addView(all)
        val sv = ScrollView(this)
        sv.addView(box)
        AlertDialog.Builder(this)
            .setTitle("Sayfa boyutu ve kâğıt rengi")
            .setView(sv)
            .setPositiveButton("Uygula") { _, _ ->
                val targets = if (all.isChecked) nb.pages else arrayListOf(cur)
                for (pg in targets) {
                    if (pg.pdfName != null) continue
                    pg.width = chooser.pageW
                    pg.height = chooser.pageH
                    pg.paperColor = chooser.pageColor
                }
                showCurrent()
                refresh()
                scheduleSave()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun confirmDeletePage() {
        if (nb.pages.size <= 1) {
            Toast.makeText(this, "Defterde tek sayfa kaldı, silinemez", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Bu sayfa silinsin mi?")
            .setMessage("Sayfa ${nb.current + 1} ve üstündeki yazılar silinecek. Bu işlem geri alınamaz.")
            .setPositiveButton("Sil") { _, _ -> deleteCurrentPage() }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun showTemplatePageDialog() {
        var dlg: AlertDialog? = null
        val entries = Paper.values().map { Pair(it.label, it) }
        val grid = PaperGrid(this, entries, -1, draw.page.paperColor) { which ->
            dlg?.dismiss()
            run {
                val cur = draw.page
                val pg = if (cur.pdfName == null) PageData(cur.width, cur.height, Paper.values()[which])
                else PageData(1200f, 1697f, Paper.values()[which])
                if (cur.pdfName == null) pg.paperColor = cur.paperColor
                val at = nb.pages.indexOf(cur).let { if (it < 0) nb.current else it } + 1
                nb.pages.add(at, pg)
                gotoPage(at)
            }
        }
        val sv = android.widget.ScrollView(this)
        sv.addView(grid)
        dlg = AlertDialog.Builder(this)
            .setTitle("Yeni sayfa için şablon")
            .setView(sv)
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun refreshItemBar() {
        if (!::itemBar.isInitialized) return
        val sel = draw.selectedItem
        val show = sel != null && (draw.tool == Tool.TEXT || draw.tool == Tool.OBJECT)
        itemBar.visibility = if (show) View.VISIBLE else View.GONE
        if (sel != null) layerBtn.text = if (sel.above) "Çizginin üstünde" else "Çizginin altında"
    }

    private fun buildItemBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = bar(dp(22f).toFloat())
        bar.elevation = dp(8f).toFloat()
        bar.setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
        fun chip(label: String, action: () -> Unit): TextView {
            val t = TextView(this)
            t.text = label
            t.textSize = 13f
            t.setTextColor(Look.ink)
            t.setPadding(dp(12f), dp(9f), dp(12f), dp(9f))
            t.setOnClickListener {
                action()
                refreshItemBar()
            }
            bar.addView(t)
            return t
        }
        chip("En arkaya") { draw.itemToBack() }
        chip("Arkaya") { draw.itemBackward() }
        chip("Öne") { draw.itemForward() }
        chip("En öne") { draw.itemToFront() }
        layerBtn = chip("Çizginin altında") { draw.toggleAbove() }
        layerBtn.setTextColor(Look.accent)
        chip("Çoğalt") { draw.duplicateSelected() }
        chip("Sil") { draw.deleteSelected() }.setTextColor(Color.rgb(176, 60, 60))
        bar.visibility = View.GONE
        return bar
    }

    private fun removePageAt(idx: Int): Boolean {
        if (nb.pages.size <= 1 || idx !in nb.pages.indices) return false
        val gone = nb.pages[idx]
        nb.pages.removeAt(idx)
        if (nb.current >= nb.pages.size) nb.current = nb.pages.size - 1
        val heir = nb.pages[minOf(idx, nb.pages.size - 1)]
        val dead = ArrayList<Tab>()
        for (t in nb.tabs) {
            if (t.target !== gone) continue
            if (t.auto) {
                val same = if (gone.dateKey != 0) nb.pages.firstOrNull { it.dateKey == gone.dateKey } else null
                if (same != null) t.target = same else dead.add(t)
            } else {
                t.target = heir
            }
        }
        nb.tabs.removeAll(dead)
        return true
    }

    /** Sayfaları küçük resimleriyle listeler; yukarı/aşağı okla sıralanır, çoğaltılır ve silinir. */
    private fun showPagesDialog() {
        val keep = draw.page
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
        var dlg: AlertDialog? = null
        lateinit var rebuild: () -> Unit
        fun glyph(label: String, on: Boolean, action: () -> Unit): TextView {
            val t = TextView(this)
            t.text = label
            t.textSize = 18f
            t.gravity = Gravity.CENTER
            t.setTextColor(if (on) Look.accent else Look.hairline)
            val lp = LinearLayout.LayoutParams(dp(44f), dp(44f))
            t.layoutParams = lp
            if (on) t.setOnClickListener {
                action()
                scheduleSave()
                rebuild()
            }
            return t
        }
        rebuild = {
            list.removeAllViews()
            for ((i, pg) in nb.pages.withIndex()) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, dp(4f), 0, dp(4f))
                val th = PageThumb(this, pg)
                th.setOnClickListener {
                    dlg?.dismiss()
                    gotoPage(i)
                }
                row.addView(th)
                val tn = nb.tabs.filter { it.target === pg }.joinToString(", ") { it.label }
                val kind = if (pg.pdfName != null) "PDF sayfa ${pg.pdfIndex + 1}" else pg.paper.label
                val tv = TextView(this)
                val extra = ArrayList<String>()
                if (pg.dateKey != 0) extra.add("${pg.dateKey % 100} ${Planner.SHORT[(pg.dateKey / 100 % 100 - 1).coerceIn(0, 11)]}")
                if (tn.isNotEmpty()) extra.add("ayraç: $tn")
                tv.text = "Sayfa ${i + 1}\n$kind" + (if (extra.isEmpty()) "" else "\n" + extra.joinToString(" · "))
                tv.textSize = 13f
                tv.setTextColor(if (pg === keep) Look.accent else Look.ink)
                tv.setPadding(dp(12f), 0, dp(6f), 0)
                tv.setOnClickListener {
                    dlg?.dismiss()
                    gotoPage(i)
                }
                row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(glyph("▲", i > 0) { java.util.Collections.swap(nb.pages, i, i - 1) })
                row.addView(glyph("▼", i < nb.pages.size - 1) { java.util.Collections.swap(nb.pages, i, i + 1) })
                row.addView(glyph("⧉", true) {
                    val cp = pg.snapshot()
                    cp.items.clear()
                    for (it in pg.items) cp.items.add(it.copy())
                    nb.pages.add(i + 1, cp)
                })
                row.addView(glyph("✕", nb.pages.size > 1) { removePageAt(i) })
                list.addView(row)
            }
        }
        rebuild()
        val sv = ScrollView(this)
        sv.addView(list)
        dlg = AlertDialog.Builder(this)
            .setTitle("Sayfalar (▲▼ sıralar, ⧉ çoğaltır, ✕ siler)")
            .setView(sv)
            .setNegativeButton("Kapat", null)
            .create()
        dlg.setOnDismissListener {
            val at = nb.pages.indexOf(keep)
            nb.current = if (at >= 0) at else minOf(nb.current, nb.pages.size - 1)
            showCurrent()
            refresh()
            scheduleSave()
        }
        dlg.show()
    }

    private fun showPaperDialog() {
        var dlg: AlertDialog? = null
        val entries = Paper.values().map { Pair(it.label, it) }
        val grid = PaperGrid(this, entries, draw.paper.ordinal, draw.page.paperColor) { which ->
            draw.paper = Paper.values()[which]
            refresh()
            dlg?.dismiss()
        }
        val sv = android.widget.ScrollView(this)
        sv.addView(grid)
        dlg = AlertDialog.Builder(this)
            .setTitle("Kağıt stili (bu sayfa)")
            .setView(sv)
            .setNegativeButton("Kapat", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        itemId = intent.getStringExtra("id") ?: ""
        spread = getSharedPreferences("ayar", MODE_PRIVATE).getBoolean("cift_$itemId", false)
        d = resources.displayMetrics.density
        Fonts.init(assets)
        versionText = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

        val loaded = Storage.load(notebookFile())
        if (loaded != null) {
            nb = loaded
        } else {
            nb = Notebook()
            nb.pages.add(PageData())
        }

        draw = DrawView(this)
        draw.pdfDir = pdfDir()
        preview = PreviewView(this, draw)
        info = TextView(this)
        sliderLabel = TextView(this)
        pageLabel = TextView(this)
        seek = SeekBar(this)
        swatchRow = LinearLayout(this)
        swatchRow.orientation = LinearLayout.HORIZONTAL
        swatchRow.gravity = Gravity.CENTER_VERTICAL

        for (t in listOf(info, sliderLabel, pageLabel)) {
            t.textSize = 13f
            t.setSingleLine(true)
        }
        info.setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
        sliderLabel.setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
        pageLabel.textSize = 16f
        pageLabel.gravity = Gravity.CENTER_VERTICAL
        draw.onInfo = { msg -> info.text = "v$versionText · $msg" }
        val prefs = getSharedPreferences("ayar", MODE_PRIVATE)
        autoTabs = prefs.getBoolean("otoayrac_$itemId", true)
        draw.penDoubleTap = prefs.getBoolean("kalem_cift", true)
        draw.scribbleErase = prefs.getBoolean("karala", true)
        draw.onChanged = {
            markWritten()
            scheduleSave()
        }
        draw.onToolToggled = {
            settingsPopup?.dismiss()
            refresh()
        }
        draw.onTextTap = { item, x, y -> showTextDialog(item, x, y) }
        draw.onSelection = { refreshItemBar() }
        draw.onLassoMenu = { x, y ->
            val pm = android.widget.PopupMenu(this, draw)
            pm.menu.add(0, 1, 1, "Sil")
            pm.menu.add(0, 2, 2, "Çoğalt")
            pm.menu.add(0, 3, 3, "Çizgi rengini kalem rengi yap")
            pm.menu.add(0, 4, 4, "Seçimi bırak")
            pm.setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    1 -> draw.lassoDelete()
                    2 -> draw.lassoDuplicate()
                    3 -> if (!draw.lassoRecolor(draw.penColor)) Toast.makeText(this, "Seçimde çizgi yok", Toast.LENGTH_SHORT).show()
                    4 -> draw.clearLasso()
                }
                true
            }
            pm.show()
        }
        draw.onTabClick = { t ->
            var idx = nb.pages.indexOf(t.target)
            val key = t.target.dateKey
            if (key != 0 && nb.pages.getOrNull(nb.current)?.dateKey == key) {
                // O günün ayracı zaten açıksa, dokundukça o günün sonraki sayfasına geçer.
                val same = nb.pages.indices.filter { nb.pages[it].dateKey == key }
                val after = same.firstOrNull { it > nb.current + (if (spread) 1 else 0) }
                idx = after ?: same.first()
                Toast.makeText(this, "${t.label}: ${same.indexOf(idx) + 1}/${same.size}. sayfa", Toast.LENGTH_SHORT).show()
            }
            if (idx >= 0) gotoPage(idx)
        }
        draw.onTabLongPress = { t -> showTabDialog(t) }
        draw.onPageSwipe = { dir -> gotoPage(nb.current + dir * step()) }
        draw.onColorPicked = { c ->
            rememberColor(c)
            refresh()
            Toast.makeText(this, "Renk alındı, araç kaleme döndü", Toast.LENGTH_SHORT).show()
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = (progress + sliderMin).toFloat()
                when (sliderTool()) {
                    Tool.HIGHLIGHTER -> draw.hlWidthDp = v
                    Tool.ERASER -> draw.eraserRadiusDp = v
                    else -> draw.penWidthDp = v
                }
                sliderLabel.text = sliderText(v)
                preview.invalidate()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val toolRow = LinearLayout(this)
        toolRow.orientation = LinearLayout.HORIZONTAL
        toolRow.gravity = Gravity.CENTER_VERTICAL
        toolRow.setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
        fun spacer(): View {
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            return v
        }
        toolRow.addView(iconBtn(Icon.HOME, "Kütüphane") { finish() })
        toolRow.addView(iconBtn(Icon.PAGES, "Sayfalar") { showPagesDialog() })
        lateinit var addBtn: IconButton
        addBtn = iconBtn(Icon.ADD, "Yeni sayfa (şablon seçerek)") { showNewPageMenu(addBtn) }
        addBtn.setOnLongClickListener {
            addPage()
            true
        }
        toolRow.addView(addBtn)
        toolRow.addView(spacer())
        val toolIcons = listOf(
            Triple(Tool.PEN, Icon.PEN, "Kalem"), Triple(Tool.HIGHLIGHTER, Icon.HIGHLIGHTER, "Fosforlu"),
            Triple(Tool.ERASER, Icon.ERASER, "Silgi"), Triple(Tool.PICKER, Icon.PICKER, "Damlalık"),
            Triple(Tool.TEXT, Icon.TEXT, "Metin kutusu (klavyeyle yazı)"),
            Triple(Tool.LASSO, Icon.LASSO, "Lasso: çevreleyerek seç, taşı, sil, çoğalt"),
            Triple(Tool.LASER, Icon.LASER, "Lazer işaretçi (iz bırakmaz)")
        )
        for ((t, ic, lb) in toolIcons) {
            val b = iconBtn(ic, lb) {
                if (draw.tool == t && (t == Tool.PEN || t == Tool.HIGHLIGHTER || t == Tool.ERASER)) {
                    val ab = toolButtons[t]
                    if (ab != null) toggleSettings(ab)
                } else {
                    draw.tool = t
                    settingsPopup?.dismiss()
                    refresh()
                    draw.clearLasso()
                    if (t == Tool.LASSO) {
                        Toast.makeText(this, "Seçmek istediğiniz şeyin çevresini çizin; sonra sürükleyerek taşıyın, dokunarak menüyü açın", Toast.LENGTH_LONG).show()
                    }
                    if (t == Tool.TEXT) {
                        Toast.makeText(this, "Yazı yazmak için sayfada bir yere dokunun (kalemle ya da parmakla)", Toast.LENGTH_LONG).show()
                    }
                }
            }
            toolButtons[t] = b
            toolRow.addView(b)
        }
        val stickerBtn = iconBtn(Icon.STICKER, "Çıkartma ve resim") { showStickerDialog() }
        toolButtons[Tool.OBJECT] = stickerBtn
        toolRow.addView(stickerBtn)
        toolRow.addView(separator())
        toolRow.addView(iconBtn(Icon.PAPER, "Sayfa şablonu") { showPaperDialog() })
        toolRow.addView(iconBtn(Icon.TAB, "Ayraç ekle") { showTabDialog(null) })
        fingerBtn = iconBtn(Icon.FINGER, "Parmakla çizim") {
            draw.fingerDraw = !draw.fingerDraw
            refresh()
        }
        toolRow.addView(fingerBtn)
        spreadBtn = iconBtn(Icon.SPREAD, "Çift sayfa görünümü (ajanda gibi)") {
            spread = !spread
            getSharedPreferences("ayar", MODE_PRIVATE).edit().putBoolean("cift_$itemId", spread).apply()
            showCurrent()
            refresh()
        }
        toolRow.addView(spreadBtn)
        toolRow.addView(spacer())
        toolRow.addView(iconBtn(Icon.PREV, "Önceki sayfa") { gotoPage(nb.current - step()) })
        pageLabel.setTextColor(Look.ink)
        pageLabel.typeface = android.graphics.Typeface.SERIF
        pageLabel.textSize = 15f
        pageLabel.setPadding(dp(6f), 0, dp(6f), 0)
        toolRow.addView(pageLabel)
        toolRow.addView(iconBtn(Icon.NEXT, "Sonraki sayfa") { gotoPage(nb.current + step()) })
        toolRow.addView(spacer())
        toolRow.addView(iconBtn(Icon.UNDO, "Geri al") { draw.undo() })
        toolRow.addView(iconBtn(Icon.REDO, "İleri al") { draw.redo() })
        val moreBtn = iconBtn(Icon.MORE, "Diğer işlemler") { }
        moreBtn.setOnClickListener { showMoreMenu(moreBtn) }
        toolRow.addView(moreBtn)
        val toolScroll = HorizontalScrollView(this)
        toolScroll.isFillViewport = true
        toolScroll.background = bar(dp(24f).toFloat())
        toolScroll.elevation = dp(5f).toFloat()
        toolScroll.clipToOutline = true
        toolScroll.addView(toolRow)

        val swatchScroll = HorizontalScrollView(this)
        swatchScroll.background = bar(dp(20f).toFloat())
        swatchScroll.elevation = dp(3f).toFloat()
        swatchScroll.clipToOutline = true
        swatchScroll.addView(swatchRow)

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        panel.setBackgroundColor(Color.TRANSPARENT)

        kindRow = LinearLayout(this)
        kindRow.orientation = LinearLayout.HORIZONTAL
        panel.addView(kindRow)
        presetRow = LinearLayout(this)
        presetRow.orientation = LinearLayout.HORIZONTAL
        presetRow.gravity = Gravity.CENTER_VERTICAL
        panel.addView(presetRow)
        val sliderRow = LinearLayout(this)
        sliderRow.orientation = LinearLayout.HORIZONTAL
        sliderRow.gravity = Gravity.CENTER_VERTICAL
        sliderRow.addView(sliderLabel, LinearLayout.LayoutParams(dp(150f), LinearLayout.LayoutParams.WRAP_CONTENT))
        sliderRow.addView(seek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(sliderRow)
        panel.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64f)))

        val pressLabel = TextView(this)
        pressLabel.textSize = 13f
        pressLabel.setSingleLine(true)
        pressLabel.setPadding(dp(8f), 0, dp(8f), 0)
        pressLabel.text = "Basınç hassasiyeti: " + draw.pressureSensitivity
        val pressSeek = SeekBar(this)
        pressSeek.max = 100
        pressSeek.progress = draw.pressureSensitivity
        pressSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                draw.pressureSensitivity = progress
                pressLabel.text = "Basınç hassasiyeti: $progress"
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        val pressRow = LinearLayout(this)
        pressRow.orientation = LinearLayout.HORIZONTAL
        pressRow.gravity = Gravity.CENTER_VERTICAL
        pressRow.addView(pressLabel, LinearLayout.LayoutParams(dp(200f), LinearLayout.LayoutParams.WRAP_CONTENT))
        pressRow.addView(pressSeek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(pressRow)

        val snapBox = CheckBox(this)
        snapBox.text = "Fosforlu, düze yakın çizgileri tam düz çizsin"
        snapBox.isChecked = draw.highlighterSnap
        snapBox.setOnCheckedChangeListener { _, checked -> draw.highlighterSnap = checked }
        panel.addView(snapBox)
        val holdBox = CheckBox(this)
        holdBox.text = "Çizerken kalemi bekletince çizgi düzelsin"
        holdBox.isChecked = draw.holdToStraighten
        holdBox.setOnCheckedChangeListener { _, checked -> draw.holdToStraighten = checked }
        panel.addView(holdBox)
        val scribbleBox = CheckBox(this)
        scribbleBox.text = "Yazının üstünü karalayınca silinsin"
        scribbleBox.isChecked = draw.scribbleErase
        scribbleBox.setOnCheckedChangeListener { _, checked ->
            draw.scribbleErase = checked
            getSharedPreferences("ayar", MODE_PRIVATE).edit().putBoolean("karala", checked).apply()
        }
        panel.addView(scribbleBox)

        val popup = PopupWindow(panel, dp(480f), LinearLayout.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(bar(dp(20f).toFloat()))
        popup.isOutsideTouchable = true
        popup.elevation = dp(10f).toFloat()
        settingsPopup = popup

        val toggle = TextView(this)
        toggle.text = "Araçları gizle"
        toggle.textSize = 14f
        toggle.setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        toggle.setTextColor(Look.accent)
        toggle.textSize = 12f
        toggle.setOnClickListener {
            toolsVisible = !toolsVisible
            val vis = if (toolsVisible) View.VISIBLE else View.GONE
            toolScroll.visibility = vis
            swatchScroll.visibility = vis
            toggle.text = if (toolsVisible) "Araçları gizle" else "Araçları göster"
            settingsPopup?.dismiss()
        }
        val statusRow = LinearLayout(this)
        statusRow.orientation = LinearLayout.HORIZONTAL
        statusRow.gravity = Gravity.CENTER_VERTICAL
        info.setTextColor(Look.inkSoft)
        info.textSize = 11f
        statusRow.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        statusRow.addView(toggle)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Look.canvas)
        val barLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        barLp.setMargins(dp(14f), dp(10f), dp(14f), dp(4f))
        root.addView(toolScroll, barLp)
        val swLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        swLp.setMargins(dp(14f), dp(4f), dp(14f), dp(2f))
        root.addView(swatchScroll, swLp)
        root.addView(statusRow)
        itemBar = buildItemBar()
        val frame = android.widget.FrameLayout(this)
        frame.addView(draw, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        val ibLp = android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT)
        ibLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ibLp.bottomMargin = dp(16f)
        frame.addView(itemBar, ibLp)
        root.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        showCurrent()
        refresh()
        info.text = "v$versionText · yazmaya başlayabilirsiniz"
    }

    companion object {
        private const val REQ_PDF = 101
        private const val REQ_EXPORT = 102
        private const val REQ_BACKUP = 103
        private const val REQ_RESTORE = 104
        private const val REQ_IMAGE = 105
    }
}
