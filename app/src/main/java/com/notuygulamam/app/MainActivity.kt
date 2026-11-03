package com.notuygulamam.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

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

    private lateinit var draw: DrawView
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var info: TextView
    private lateinit var sliderLabel: TextView
    private lateinit var seek: SeekBar
    private lateinit var swatchRow: LinearLayout
    private lateinit var fingerButton: Button
    private lateinit var paperButton: Button
    private val toolButtons = HashMap<Tool, Button>()
    private var sliderMin = 1
    private var d = 1f

    private fun dp(v: Float): Int = (v * d).toInt()

    private fun styleToggle(b: Button, on: Boolean) {
        b.setBackgroundColor(if (on) Color.rgb(40, 40, 40) else Color.rgb(225, 225, 225))
        b.setTextColor(if (on) Color.WHITE else Color.BLACK)
    }

    private fun makeButton(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(3f), dp(3f), dp(3f), dp(3f))
        b.layoutParams = lp
        return b
    }

    private fun rebuildSwatches() {
        swatchRow.removeAllViews()
        val highlighter = draw.tool == Tool.HIGHLIGHTER
        val palette = if (highlighter) hlPalette else penPalette
        val selected = if (highlighter) draw.hlColor else draw.penColor
        for (c in palette) {
            val v = View(this)
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(c)
            if (c == selected) g.setStroke(dp(4f), Color.rgb(0, 120, 255))
            else g.setStroke(dp(1f), Color.LTGRAY)
            v.background = g
            val lp = LinearLayout.LayoutParams(dp(42f), dp(42f))
            lp.setMargins(dp(6f), dp(4f), dp(6f), dp(4f))
            v.layoutParams = lp
            v.setOnClickListener {
                if (highlighter) {
                    draw.hlColor = c
                } else {
                    draw.penColor = c
                    if (draw.tool == Tool.ERASER) draw.tool = Tool.PEN
                }
                refresh()
            }
            swatchRow.addView(v)
        }
    }

    private fun refresh() {
        for ((t, b) in toolButtons) styleToggle(b, draw.tool == t)
        styleToggle(fingerButton, draw.fingerDraw)
        fingerButton.text = if (draw.fingerDraw) "Parmak: açık" else "Parmak: kapalı"
        paperButton.text = "Kağıt: " + draw.paper.label

        val range = when (draw.tool) {
            Tool.PEN -> Pair(1, 24)
            Tool.HIGHLIGHTER -> Pair(6, 40)
            Tool.ERASER -> Pair(6, 30)
        }
        val cur = when (draw.tool) {
            Tool.PEN -> draw.penWidthDp
            Tool.HIGHLIGHTER -> draw.hlWidthDp
            Tool.ERASER -> draw.eraserRadiusDp
        }
        sliderMin = range.first
        seek.max = range.second - range.first
        seek.progress = (cur - range.first).toInt()
        sliderLabel.text = sliderText(cur)

        rebuildSwatches()
        preview.invalidate()

        val toolName = when (draw.tool) {
            Tool.PEN -> "kalem"
            Tool.HIGHLIGHTER -> "fosforlu"
            Tool.ERASER -> "silgi"
        }
        status.text = "Seçili araç: $toolName"
    }

    private fun sliderText(v: Float): String {
        val name = if (draw.tool == Tool.ERASER) "Silgi boyutu: " else "Kalınlık: "
        return name + v.toInt()
    }

    private fun showPaperDialog() {
        val labels = Paper.values().map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Kağıt stili")
            .setItems(labels) { _, which ->
                draw.paper = Paper.values()[which]
                refresh()
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        d = resources.displayMetrics.density

        draw = DrawView(this)
        preview = PreviewView(this, draw)
        status = TextView(this)
        info = TextView(this)
        sliderLabel = TextView(this)
        seek = SeekBar(this)
        swatchRow = LinearLayout(this)
        swatchRow.orientation = LinearLayout.HORIZONTAL
        fingerButton = makeButton("") {
            draw.fingerDraw = !draw.fingerDraw
            refresh()
        }
        paperButton = makeButton("") { showPaperDialog() }

        for (t in listOf(status, info, sliderLabel)) {
            t.textSize = 13f
            t.setSingleLine(true)
            t.setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
        }
        draw.onInfo = { msg -> info.text = msg }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = (progress + sliderMin).toFloat()
                when (draw.tool) {
                    Tool.PEN -> draw.penWidthDp = v
                    Tool.HIGHLIGHTER -> draw.hlWidthDp = v
                    Tool.ERASER -> draw.eraserRadiusDp = v
                }
                sliderLabel.text = sliderText(v)
                preview.invalidate()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val toolRow = LinearLayout(this)
        toolRow.orientation = LinearLayout.HORIZONTAL
        for ((t, label) in listOf(Tool.PEN to "Kalem", Tool.HIGHLIGHTER to "Fosforlu", Tool.ERASER to "Silgi")) {
            val b = makeButton(label) {
                draw.tool = t
                refresh()
            }
            toolButtons[t] = b
            toolRow.addView(b)
        }
        toolRow.addView(paperButton)
        toolRow.addView(fingerButton)
        toolRow.addView(makeButton("Geri") { draw.undo() })
        toolRow.addView(makeButton("İleri") { draw.redo() })
        toolRow.addView(makeButton("Temizle") { draw.clearAll() })
        val toolScroll = HorizontalScrollView(this)
        toolScroll.addView(toolRow)

        val swatchScroll = HorizontalScrollView(this)
        swatchScroll.addView(swatchRow)

        val sliderRow = LinearLayout(this)
        sliderRow.orientation = LinearLayout.HORIZONTAL
        sliderRow.setPadding(dp(8f), 0, dp(8f), 0)
        sliderRow.addView(sliderLabel, LinearLayout.LayoutParams(dp(150f), LinearLayout.LayoutParams.WRAP_CONTENT))
        sliderRow.addView(seek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        sliderRow.addView(preview, LinearLayout.LayoutParams(dp(160f), dp(70f)))

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.addView(toolScroll)
        root.addView(swatchScroll)
        root.addView(sliderRow)
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
