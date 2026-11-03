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
import android.widget.CheckBox
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
    private var settingsAnchor: View? = null
    private var versionText = ""
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
        val b = IconButton(this, icon, label)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(3f), dp(3f), dp(3f), dp(3f))
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    private fun separator(): View {
        val v = View(this)
        v.setBackgroundColor(Color.LTGRAY)
        val lp = LinearLayout.LayoutParams(dp(1f), dp(36f))
        lp.setMargins(dp(8f), dp(8f), dp(8f), dp(8f))
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
        val lp = LinearLayout.LayoutParams(dp(36f), dp(36f))
        lp.setMargins(dp(5f), dp(4f), dp(5f), dp(4f))
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

    private fun toggleSettings() {
        val p = settingsPopup ?: return
        val anchor = settingsAnchor ?: return
        if (p.isShowing) p.dismiss() else p.showAsDropDown(anchor, dp(8f), 0)
    }

    private fun colorButton(): View {
        val t = TextView(this)
        t.text = "Renk seç"
        t.textSize = 14f
        t.setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
        val g = GradientDrawable()
        g.setColor(Color.rgb(225, 227, 232))
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
        pageLabel.text = " ${nb.current + 1} / ${nb.pages.size} "

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

    private fun gotoPage(i: Int) {
        if (i < 0 || i >= nb.pages.size) return
        nb.current = i
        draw.showPage(nb.pages[i])
        refresh()
        scheduleSave()
    }

    private fun addPage() {
        val cur = nb.pages[nb.current]
        nb.pages.add(nb.current + 1, PageData(cur.width, cur.height, cur.paper))
        gotoPage(nb.current + 1)
    }

    private fun deleteCurrentPage() {
        if (nb.pages.size <= 1) return
        nb.pages.removeAt(nb.current)
        if (nb.current >= nb.pages.size) nb.current = nb.pages.size - 1
        draw.showPage(nb.pages[nb.current])
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
                    draw.showPage(nb.pages[nb.current])
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

    private fun showPagesDialog() {
        val labels = nb.pages.mapIndexed { i, pg ->
            val kind = if (pg.pdfName != null) "PDF sayfa ${pg.pdfIndex + 1}" else pg.paper.label
            "Sayfa ${i + 1}  ($kind, ${pg.strokes.size} çizgi)"
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
        draw.onPageSwipe = { dir -> gotoPage(nb.current + dir) }
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
        toolRow.addView(iconBtn(Icon.HOME, "Kütüphane") { finish() })
        toolRow.addView(separator())
        val toolIcons = listOf(
            Triple(Tool.PEN, Icon.PEN, "Kalem"), Triple(Tool.HIGHLIGHTER, Icon.HIGHLIGHTER, "Fosforlu"),
            Triple(Tool.ERASER, Icon.ERASER, "Silgi"), Triple(Tool.PICKER, Icon.PICKER, "Damlalık")
        )
        for ((t, ic, lb) in toolIcons) {
            val b = iconBtn(ic, lb) {
                draw.tool = t
                refresh()
            }
            toolButtons[t] = b
            toolRow.addView(b)
        }
        toolRow.addView(iconBtn(Icon.THICKNESS, "Kalınlık") { toggleSettings() })
        toolRow.addView(separator())
        toolRow.addView(iconBtn(Icon.UNDO, "Geri al") { draw.undo() })
        toolRow.addView(iconBtn(Icon.REDO, "İleri al") { draw.redo() })
        toolRow.addView(separator())
        toolRow.addView(iconBtn(Icon.PAPER, "Kâğıt") { showPaperDialog() })
        fingerBtn = iconBtn(Icon.FINGER, "Parmak") {
            draw.fingerDraw = !draw.fingerDraw
            refresh()
        }
        toolRow.addView(fingerBtn)
        toolRow.addView(iconBtn(Icon.TRASH, "Temizle") { draw.clearAll() })
        toolRow.addView(separator())
        toolRow.addView(iconBtn(Icon.PREV, "Önceki") { gotoPage(nb.current - 1) })
        toolRow.addView(pageLabel)
        toolRow.addView(iconBtn(Icon.NEXT, "Sonraki") { gotoPage(nb.current + 1) })
        toolRow.addView(iconBtn(Icon.ADD, "Yeni sayfa") { addPage() })
        toolRow.addView(iconBtn(Icon.PAGES, "Sayfalar") { showPagesDialog() })
        toolRow.addView(separator())
        toolRow.addView(iconBtn(Icon.IMPORT, "PDF ekle") { pickPdf() })
        toolRow.addView(separator())
        toolRow.addView(iconBtn(Icon.SAVE, "Kaydet") { saveWithMessage() })
        toolRow.addView(iconBtn(Icon.EXPORT, "PDF kaydet") { startExport() })
        toolRow.addView(iconBtn(Icon.ARCHIVE, "Yedek") { showBackupDialog() })
        val toolScroll = HorizontalScrollView(this)
        toolScroll.addView(toolRow)

        val swatchScroll = HorizontalScrollView(this)
        swatchScroll.addView(swatchRow)

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        panel.setBackgroundColor(Color.WHITE)

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
        settingsAnchor = toolScroll

        val toggle = TextView(this)
        toggle.text = "Araçları gizle"
        toggle.textSize = 14f
        toggle.setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        toggle.setBackgroundColor(Color.rgb(225, 227, 232))
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
        statusRow.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        statusRow.addView(toggle)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.addView(toolScroll)
        root.addView(swatchScroll)
        root.addView(statusRow)
        root.addView(
            draw,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)

        draw.showPage(nb.pages[nb.current])
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
