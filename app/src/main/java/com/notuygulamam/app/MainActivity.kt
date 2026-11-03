package com.notuygulamam.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File

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
    private var versionText = ""
    private var d = 1f

    private val handler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { saveNow() }

    private fun notebookFile(): File = File(filesDir, "defter.bin")

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
        val lp = LinearLayout.LayoutParams(dp(42f), dp(42f))
        lp.setMargins(dp(6f), dp(4f), dp(6f), dp(4f))
        v.layoutParams = lp
        v.setOnClickListener { applyColor(c) }
        return v
    }

    private fun rebuildSwatches() {
        swatchRow.removeAllViews()
        val highlighter = draw.tool == Tool.HIGHLIGHTER
        val selected = if (highlighter) draw.hlColor else draw.penColor
        swatchRow.addView(iconBtn(Icon.PALETTE, "Renk") { showColorDialog() })
        for (c in recentColors) swatchRow.addView(swatch(c, c == selected))
        if (recentColors.isNotEmpty()) swatchRow.addView(separator())
        val palette = if (highlighter) hlPalette else penPalette
        for (c in palette) swatchRow.addView(swatch(c, c == selected))
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

    private fun showPagesDialog() {
        val labels = nb.pages.mapIndexed { i, pg ->
            "Sayfa ${i + 1}  (${pg.paper.label}, ${pg.strokes.size} çizgi)"
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
        val toolScroll = HorizontalScrollView(this)
        toolScroll.addView(toolRow)

        val swatchScroll = HorizontalScrollView(this)
        swatchScroll.addView(swatchRow)

        val sliderRow = LinearLayout(this)
        sliderRow.orientation = LinearLayout.HORIZONTAL
        sliderRow.gravity = Gravity.CENTER_VERTICAL
        sliderRow.setPadding(dp(8f), 0, dp(8f), 0)
        sliderRow.addView(sliderLabel, LinearLayout.LayoutParams(dp(150f), LinearLayout.LayoutParams.WRAP_CONTENT))
        sliderRow.addView(seek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        sliderRow.addView(preview, LinearLayout.LayoutParams(dp(160f), dp(60f)))

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
        pressRow.setPadding(dp(8f), 0, dp(8f), 0)
        pressRow.addView(pressLabel, LinearLayout.LayoutParams(dp(200f), LinearLayout.LayoutParams.WRAP_CONTENT))
        pressRow.addView(pressSeek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.addView(toolScroll)
        root.addView(swatchScroll)
        root.addView(sliderRow)
        root.addView(pressRow)
        root.addView(info)
        root.addView(
            draw,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)

        draw.showPage(nb.pages[nb.current])
        refresh()
        info.text = "v$versionText · yazmaya başlayabilirsiniz"
    }
}
