package com.notuygulamam.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val colors = intArrayOf(
        Color.BLACK, Color.rgb(25, 90, 200), Color.rgb(200, 40, 40),
        Color.rgb(30, 140, 60), Color.rgb(230, 130, 0)
    )
    private val colorNames = arrayOf("siyah", "mavi", "kırmızı", "yeşil", "turuncu")
    private val widths = floatArrayOf(2f, 4f, 6f, 10f)
    private var ci = 0
    private var wi = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val draw = DrawView(this)
        val status = TextView(this)
        val info = TextView(this)
        for (t in listOf(status, info)) {
            t.textSize = 12f
            t.setPadding((8 * d).toInt(), (2 * d).toInt(), (8 * d).toInt(), (2 * d).toInt())
        }
        draw.onInfo = { msg -> info.text = msg }

        fun refresh() {
            val toolName = when (draw.tool) {
                Tool.PEN -> "kalem"
                Tool.HIGHLIGHTER -> "fosforlu"
                Tool.ERASER -> "silgi"
            }
            val finger = if (draw.fingerDraw) "açık" else "kapalı"
            status.text = "Araç: $toolName · renk: ${colorNames[ci]} · kalınlık: ${widths[wi].toInt()} · " +
                "kağıt: ${draw.paper.label} · parmakla çizim: $finger"
        }

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        fun addButton(label: String, action: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.isAllCaps = false
            b.setOnClickListener {
                action()
                refresh()
            }
            bar.addView(b)
        }

        addButton("Kalem") { draw.tool = Tool.PEN }
        addButton("Fosforlu") { draw.tool = Tool.HIGHLIGHTER }
        addButton("Silgi") { draw.tool = Tool.ERASER }
        addButton("Renk") {
            ci = (ci + 1) % colors.size
            draw.inkColor = colors[ci]
        }
        addButton("Kalınlık") {
            wi = (wi + 1) % widths.size
            draw.penWidthDp = widths[wi]
        }
        addButton("Kağıt") {
            val all = Paper.values()
            draw.paper = all[(draw.paper.ordinal + 1) % all.size]
        }
        addButton("Geri") { draw.undo() }
        addButton("İleri") { draw.redo() }
        addButton("Parmak") { draw.fingerDraw = !draw.fingerDraw }
        addButton("Temizle") { draw.clearAll() }

        val scroll = HorizontalScrollView(this)
        scroll.addView(bar)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.addView(scroll)
        root.addView(status)
        root.addView(info)
        root.addView(
            draw,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)
        refresh()
    }
}
