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
    private var d = 1f

    private val handler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { saveNow() }

    private var itemId: String = ""
    private fun libraryDir(): File = File(filesDir, "library")
    private fun notebookFile(): File =
        if (itemId.isEmpty()) File(filesDir, "defter.bin") else File(libraryDir(), "nb_$itemId.bin")
    private fun pdfDir(): File = File(filesDir, "pdfs")

    private fun dp(v: Float): Int = (v * d).toInt()

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
        v.setBackgroundColor(Color.rgb(100, 102, 110))
        val lp = LinearLayout.LayoutParams(dp(1f), dp(28f))
        lp.setMargins(dp(6f), dp(6f), dp(6f), dp(6f))
        v.layoutParams = lp
        return v
    }

    /** Kaydırıcının hangi aracın değerini ayarladığı; damlalık seçiliyken kalem değerleri gösterilir. */
    private fun sliderTool(): Tool = if (draw.tool == Tool.PICKER) Tool.PEN else draw.tool

    private fun applyColor(c: Int) {
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
        if (selected) g.setStroke(dp(4f), Color.rgb(0, 120, 255))
        else g.setStroke(dp(1f), Color.LTGRAY)
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
                        paint.setColor(Color.rgb(228, 236, 252))
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
        t.setTextColor(Color.WHITE)
        val g = GradientDrawable()
        g.setColor(Color.rgb(78, 80, 90))
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
        if (spread) nb.current -= nb.current % 2
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
        if (nb.pages.size <= 1) return
        val idx = nb.pages.indexOf(draw.page).let { if (it < 0) nb.current else it }
        val gone = nb.pages[idx]
        nb.pages.removeAt(idx)
        if (nb.current >= nb.pages.size) nb.current = nb.pages.size - 1
        val heir = nb.pages[minOf(idx, nb.pages.size - 1)]
        for (t in nb.tabs) if (t.target === gone) t.target = heir
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
                if (c == chosen) g.setStroke(dp(4f), Color.rgb(0, 120, 255)) else g.setStroke(dp(1f), Color.LTGRAY)
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
            "PDF ekle", "Şablonlu yeni sayfa", "Kaydet", "PDF olarak dışa aktar",
            "Bu defterin yedeği", "Bu sayfadaki çizimleri temizle", "Bu sayfayı sil",
            "Sayfa boyutu ve kâğıt rengi"
        )
        for ((i, n) in names.withIndex()) m.menu.add(0, i, i, n)
        m.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                0 -> pickPdf()
                1 -> showTemplatePageDialog()
                2 -> saveWithMessage()
                3 -> startExport()
                4 -> showBackupDialog()
                5 -> draw.clearAll()
                6 -> confirmDeletePage()
                7 -> showPageStyleDialog()
            }
            true
        }
        m.show()
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
        val labels = Paper.values().map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Yeni sayfa için şablon")
            .setItems(labels) { _, which ->
                val cur = draw.page
                val pg = if (cur.pdfName == null) PageData(cur.width, cur.height, Paper.values()[which])
                else PageData(1200f, 1697f, Paper.values()[which])
                if (cur.pdfName == null) pg.paperColor = cur.paperColor
                val at = nb.pages.indexOf(cur).let { if (it < 0) nb.current else it } + 1
                nb.pages.add(at, pg)
                gotoPage(at)
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun showPagesDialog() {
        val labels = nb.pages.mapIndexed { i, pg ->
            val kind = if (pg.pdfName != null) "PDF sayfa ${pg.pdfIndex + 1}" else pg.paper.label
            val tn = nb.tabs.filter { it.target === pg }.joinToString(", ") { it.label }
            val tabInfo = if (tn.isEmpty()) "" else "  [ayraç: $tn]"
            "Sayfa ${i + 1}  ($kind, ${pg.strokes.size} çizgi)$tabInfo"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Sayfalar")
            .setItems(labels) { _, which -> gotoPage(which) }
            .setNeutralButton("Bu sayfayı sil") { _, _ -> deleteCurrentPage() }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun showPaperDialog() {
        val labels = Paper.values().map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Kağıt stili (bu sayfa)")
            .setItems(labels) { _, which ->
                draw.paper = Paper.values()[which]
                refresh()
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        itemId = intent.getStringExtra("id") ?: ""
        spread = getSharedPreferences("ayar", MODE_PRIVATE).getBoolean("cift_$itemId", false)
        d = resources.displayMetrics.density
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
        draw.onChanged = { scheduleSave() }
        draw.onTabClick = { t ->
            val idx = nb.pages.indexOf(t.target)
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
        val addBtn = iconBtn(Icon.ADD, "Yeni sayfa (uzun basınca şablon seç)") { addPage() }
        addBtn.setOnLongClickListener {
            showTemplatePageDialog()
            true
        }
        toolRow.addView(addBtn)
        toolRow.addView(spacer())
        val toolIcons = listOf(
            Triple(Tool.PEN, Icon.PEN, "Kalem"), Triple(Tool.HIGHLIGHTER, Icon.HIGHLIGHTER, "Fosforlu"),
            Triple(Tool.ERASER, Icon.ERASER, "Silgi"), Triple(Tool.PICKER, Icon.PICKER, "Damlalık")
        )
        for ((t, ic, lb) in toolIcons) {
            val b = iconBtn(ic, lb) {
                if (draw.tool == t && t != Tool.PICKER) {
                    val ab = toolButtons[t]
                    if (ab != null) toggleSettings(ab)
                } else {
                    draw.tool = t
                    settingsPopup?.dismiss()
                    refresh()
                }
            }
            toolButtons[t] = b
            toolRow.addView(b)
        }
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
        pageLabel.setTextColor(Color.WHITE)
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
        toolScroll.setBackgroundColor(Color.rgb(43, 43, 46))
        toolScroll.addView(toolRow)

        val swatchScroll = HorizontalScrollView(this)
        swatchScroll.setBackgroundColor(Color.rgb(56, 57, 63))
        swatchScroll.addView(swatchRow)

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        panel.setBackgroundColor(Color.WHITE)

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

        val popup = PopupWindow(panel, dp(480f), LinearLayout.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        popup.isOutsideTouchable = true
        popup.elevation = dp(10f).toFloat()
        settingsPopup = popup

        val toggle = TextView(this)
        toggle.text = "Araçları gizle"
        toggle.textSize = 14f
        toggle.setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        toggle.setTextColor(Color.WHITE)
        toggle.setBackgroundColor(Color.rgb(78, 80, 90))
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
        statusRow.setBackgroundColor(Color.rgb(43, 43, 46))
        info.setTextColor(Color.rgb(170, 172, 180))
        statusRow.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        statusRow.addView(toggle)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.addView(toolScroll)
        root.addView(swatchScroll)
        root.addView(statusRow)
        root.addView(draw, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
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
    }
}
