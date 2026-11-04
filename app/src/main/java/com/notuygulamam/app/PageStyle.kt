package com.notuygulamam.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Sayfa boyutu ve kâğıt rengi seçici; yeni defter penceresinde ve sayfa ayarları penceresinde kullanılır. */
class PageStyleChooser(
    context: Context,
    initW: Float,
    initH: Float,
    initColor: Int
) : LinearLayout(context) {
    class Size(val label: String, val w: Float, val h: Float)

    private val d = resources.displayMetrics.density
    private val sizes = listOf(
        Size("A4 dikey", 1200f, 1697f),
        Size("A4 yatay", 1697f, 1200f),
        Size("Ajanda", 1000f, 1400f),
        Size("Letter", 1200f, 1553f),
        Size("Kare", 1200f, 1200f),
        Size("16:9 yatay", 1700f, 956f)
    )
    private val customIdx = sizes.size
    private val colors = listOf(
        Pair("Beyaz", Color.WHITE),
        Pair("Krem", Color.rgb(250, 244, 225)),
        Pair("Sarı", Color.rgb(255, 249, 196)),
        Pair("Gri", Color.rgb(236, 238, 242)),
        Pair("Mavi", Color.rgb(226, 238, 252)),
        Pair("Yeşil", Color.rgb(228, 244, 232)),
        Pair("Pembe", Color.rgb(252, 232, 238)),
        Pair("Koyu", Color.rgb(34, 35, 40))
    )

    private var sizeIdx = customIdx
    var pageColor: Int = initColor
        private set
    private val chips = ArrayList<TextView>()
    private val swatches = ArrayList<Pair<View, Int>>()
    private val customRow = LinearLayout(context)
    private val wEdit = EditText(context)
    private val hEdit = EditText(context)

    val pageW: Float
        get() = if (sizeIdx < customIdx) sizes[sizeIdx].w else cmToUnits(wEdit.text.toString(), 21f)
    val pageH: Float
        get() = if (sizeIdx < customIdx) sizes[sizeIdx].h else cmToUnits(hEdit.text.toString(), 29.7f)

    private fun cmToUnits(t: String, fallback: Float): Float {
        val cm = t.replace(',', '.').toFloatOrNull() ?: fallback
        return (cm.coerceIn(5f, 60f) * 1200f / 21f)
    }

    private fun dp(v: Float) = (v * d).toInt()

    init {
        orientation = VERTICAL
        for ((i, s) in sizes.withIndex()) {
            if (abs(s.w - initW) < 6f && abs(s.h - initH) < 6f) sizeIdx = i
        }
        addView(caption("Sayfa boyutu"))
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        for (i in 0..customIdx) {
            val tv = TextView(context)
            tv.text = if (i < customIdx) sizes[i].label else "Özel (cm)"
            tv.textSize = 14f
            tv.setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, dp(6f), 0)
            tv.layoutParams = lp
            tv.setOnClickListener {
                sizeIdx = i
                update()
            }
            chips.add(tv)
            row.addView(tv)
        }
        val sc = HorizontalScrollView(context)
        sc.isHorizontalScrollBarEnabled = false
        sc.addView(row)
        addView(sc)

        customRow.orientation = HORIZONTAL
        customRow.gravity = Gravity.CENTER_VERTICAL
        for ((e, v) in listOf(Pair(wEdit, initW), Pair(hEdit, initH))) {
            e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            e.setSingleLine(true)
            e.setText(String.format(java.util.Locale.US, "%.1f", v * 21f / 1200f))
            e.minEms = 3
        }
        customRow.addView(TextView(context).apply { text = "Genişlik " })
        customRow.addView(wEdit)
        customRow.addView(TextView(context).apply { text = "  Yükseklik " })
        customRow.addView(hEdit)
        customRow.addView(TextView(context).apply { text = " cm" })
        addView(customRow)

        addView(caption("Kâğıt rengi"))
        val crow = LinearLayout(context)
        crow.orientation = HORIZONTAL
        for ((_, c) in colors) {
            val v = View(context)
            val lp = LayoutParams(dp(38f), dp(38f))
            lp.setMargins(dp(3f), dp(3f), dp(3f), dp(3f))
            v.layoutParams = lp
            v.setOnClickListener {
                pageColor = c
                update()
            }
            swatches.add(Pair(v, c))
            crow.addView(v)
        }
        val csc = HorizontalScrollView(context)
        csc.isHorizontalScrollBarEnabled = false
        csc.addView(crow)
        addView(csc)
        update()
    }

    private fun caption(t: String): TextView {
        val tv = TextView(context)
        tv.text = t
        tv.textSize = 13f
        tv.setTextColor(Color.rgb(90, 94, 104))
        tv.setPadding(0, dp(12f), 0, dp(4f))
        return tv
    }

    private fun update() {
        for ((i, tv) in chips.withIndex()) {
            tv.setBackgroundColor(if (i == sizeIdx) Look.ink else Look.accentSoft)
            tv.setTextColor(if (i == sizeIdx) Color.WHITE else Color.BLACK)
        }
        customRow.visibility = if (sizeIdx == customIdx) View.VISIBLE else View.GONE
        for ((v, c) in swatches) {
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(c)
            if (c == pageColor) g.setStroke(dp(4f), Look.accent) else g.setStroke(dp(1f), Color.GRAY)
            v.background = g
        }
    }
}
