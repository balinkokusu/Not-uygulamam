package com.notuygulamam.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max

/** 1200 sayfa birimi = 210 mm (A4 genişliği). Boyutlar bu orana göre milimetreden hesaplanır. */
object PageUnits {
    const val PER_MM = 1200f / 210f
    fun mm(v: Float): Float = v * PER_MM
}

/** Kâğıt şablonunun küçük önizlemesi (gerçek çizim koduyla çizilir). */
class PaperThumb(
    context: Context,
    private val paper: Paper,
    private val label: String,
    private val color: Int,
    var chosen: Boolean = false
) : View(context) {
    private val d = resources.displayMetrics.density
    private val renderer = PageRenderer()
    private val page = PageData(1200f, 1697f, paper)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * d
    }

    init {
        val cal = java.util.Calendar.getInstance()
        page.calYear = cal.get(java.util.Calendar.YEAR)
        page.calMonth = cal.get(java.util.Calendar.MONTH) + 1
        page.paperColor = color
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((104 * d).toInt(), (172 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width - 16 * d
        val h = w * 1.414f
        rect.set(8 * d, 6 * d, 8 * d + w, 6 * d + h)
        p.style = Paint.Style.FILL
        for (g in 1..4) {
            p.setColor(Color.argb(16, 70, 40, 45))
            c.drawRoundRect(rect.left - g * d, rect.top + d, rect.right + g * d, rect.bottom + 3 * d + g * d, 6 * d, 6 * d, p)
        }
        c.save()
        c.clipRect(rect)
        c.translate(rect.left, rect.top)
        val k = w / 1200f
        c.scale(k, k)
        c.drawColor(color)
        renderer.drawPaper(c, page)
        c.restore()
        p.style = Paint.Style.STROKE
        p.strokeWidth = if (chosen) 3f * d else 1f * d
        p.setColor(if (chosen) Look.accent else Look.hairline)
        c.drawRect(rect, p)
        text.setColor(if (chosen) Look.accent else Look.ink)
        text.typeface = if (chosen) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        c.drawText(label, width / 2f, rect.bottom + 20 * d, text)
    }
}

/** Kâğıt şablonlarını önizlemeleriyle gösteren, satırlara bölünmüş seçim ızgarası. */
class PaperGrid(
    context: Context,
    entries: List<Pair<String, Paper>>,
    selectedIdx: Int,
    paperColor: Int,
    private val onPick: (Int) -> Unit
) : LinearLayout(context) {
    private val thumbs = ArrayList<PaperThumb>()

    init {
        orientation = VERTICAL
        var row: LinearLayout? = null
        for ((i, e) in entries.withIndex()) {
            if (i % 5 == 0) {
                row = LinearLayout(context)
                row.orientation = HORIZONTAL
                addView(row)
            }
            val t = PaperThumb(context, e.second, e.first, paperColor, i == selectedIdx)
            t.setOnClickListener {
                for ((k, th) in thumbs.withIndex()) {
                    th.chosen = k == i
                    th.invalidate()
                }
                onPick(i)
            }
            thumbs.add(t)
            row?.addView(t)
        }
    }
}

/** Sayfa boyutu (A3, A4, A5 gibi yazdırmaya uygun ya da özel) ve kâğıt rengi seçici. */
class PageStyleChooser(
    context: Context,
    initW: Float,
    initH: Float,
    initColor: Int
) : LinearLayout(context) {
    class Size(val label: String, val sub: String, val w: Float, val h: Float)

    private val d = resources.displayMetrics.density
    private val sizes = listOf(
        mk("A3 dikey", 297f, 420f), mk("A3 yatay", 420f, 297f),
        mk("A4 dikey", 210f, 297f), mk("A4 yatay", 297f, 210f),
        mk("A5 dikey", 148f, 210f), mk("A5 yatay", 210f, 148f),
        mk("A6 dikey", 105f, 148f), mk("B5 dikey", 176f, 250f),
        mk("Letter", 216f, 279f), mk("Kare", 200f, 200f),
        Size("16:9 yatay", "ekran", 1700f, 956f)
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

    private fun mk(label: String, wmm: Float, hmm: Float) =
        Size(label, "${wmm.toInt()}×${hmm.toInt()} mm", PageUnits.mm(wmm), PageUnits.mm(hmm))

    private var sizeIdx = customIdx
    var pageColor: Int = initColor
        private set
    private val chips = ArrayList<SizeChip>()
    private val swatches = ArrayList<Pair<View, Int>>()
    private val customRow = LinearLayout(context)
    private val wEdit = EditText(context)
    private val hEdit = EditText(context)

    val pageW: Float
        get() = if (sizeIdx < customIdx) sizes[sizeIdx].w else PageUnits.mm(cm(wEdit.text.toString(), 21f) * 10f)
    val pageH: Float
        get() = if (sizeIdx < customIdx) sizes[sizeIdx].h else PageUnits.mm(cm(hEdit.text.toString(), 29.7f) * 10f)

    private fun cm(t: String, fallback: Float): Float =
        (t.replace(',', '.').toFloatOrNull() ?: fallback).coerceIn(4f, 80f)

    private fun dp(v: Float) = (v * d).toInt()

    /** Sayfa oranını gösteren küçük çerçeveli seçim kutusu. */
    private inner class SizeChip(context: Context, val size: Size?, val selectedProvider: () -> Boolean) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(dp(86f), dp(104f))
        }

        override fun onDraw(c: Canvas) {
            val sel = selectedProvider()
            val maxSide = 46f * this@PageStyleChooser.d
            val w0 = size?.w ?: 1200f
            val h0 = size?.h ?: 1697f
            val k = maxSide / max(w0, h0)
            val pw = w0 * k
            val ph = h0 * k
            r.set(width / 2f - pw / 2f, 8f * this@PageStyleChooser.d + (maxSide - ph) / 2f, width / 2f + pw / 2f,
                8f * this@PageStyleChooser.d + (maxSide - ph) / 2f + ph)
            p.style = Paint.Style.FILL
            p.setColor(if (size == null) Look.accentSoft else Color.WHITE)
            c.drawRect(r, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = (if (sel) 3f else 1.2f) * this@PageStyleChooser.d
            p.setColor(if (sel) Look.accent else Look.inkSoft)
            c.drawRect(r, p)
            t.setColor(if (sel) Look.accent else Look.ink)
            t.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            t.textSize = 12f * this@PageStyleChooser.d
            c.drawText(size?.label ?: "Özel (cm)", width / 2f, 72f * this@PageStyleChooser.d, t)
            t.typeface = Typeface.DEFAULT
            t.textSize = 10f * this@PageStyleChooser.d
            t.setColor(Look.inkSoft)
            c.drawText(size?.sub ?: "", width / 2f, 88f * this@PageStyleChooser.d, t)
        }
    }

    init {
        orientation = VERTICAL
        for ((i, s) in sizes.withIndex()) {
            if (abs(s.w - initW) < 8f && abs(s.h - initH) < 8f && sizeIdx == customIdx) sizeIdx = i
        }
        addView(caption("Sayfa boyutu"))
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        for (i in 0..customIdx) {
            val chip = SizeChip(context, if (i < customIdx) sizes[i] else null) { sizeIdx == i }
            chip.setOnClickListener {
                sizeIdx = i
                update()
            }
            chips.add(chip)
            row.addView(chip)
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
            e.setText(String.format(java.util.Locale.US, "%.1f", v / PageUnits.PER_MM / 10f))
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
        tv.setTextColor(Look.inkSoft)
        tv.setPadding(0, dp(12f), 0, dp(4f))
        return tv
    }

    private fun update() {
        for (c in chips) c.invalidate()
        customRow.visibility = if (sizeIdx == customIdx) View.VISIBLE else View.GONE
        for ((v, c) in swatches) {
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(c)
            if (c == pageColor) g.setStroke(dp(4f), Look.accent) else g.setStroke(dp(1f), Look.hairline)
            v.background = g
        }
    }
}
