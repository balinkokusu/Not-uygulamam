package com.notuygulamam.app

import android.content.Context
import android.graphics.Bitmap
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

/** Sayfa listesi için bir sayfanın küçük resmi. */
class ThumbEntry(val bmp: Bitmap, val sig: Long)

private val cache = android.util.LruCache<Int, ThumbEntry>(220)

class PageThumb(context: Context, private val page: PageData) : View(context) {
    private val d = resources.displayMetrics.density
    private val renderer = PageRenderer()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = 62f
        val h = (w * page.height / page.width).coerceIn(44f, 100f)
        setMeasuredDimension((w * d).toInt() + (8 * d).toInt(), (h * d).toInt() + (4 * d).toInt())
    }

    private fun signature(): Long {
        var h = page.strokes.size.toLong()
        h = h * 31 + page.items.size
        h = h * 31 + page.paper.ordinal
        h = h * 31 + page.calYear
        h = h * 31 + page.calMonth
        h = h * 31 + page.paperColor
        h = h * 31 + (page.strokes.lastOrNull()?.xs?.size ?: 0)
        return h
    }

    override fun onDraw(c: Canvas) {
        val w = width - 8f * d
        val h = height - 4f * d
        c.save()
        c.translate(4f * d, 2f * d)
        c.clipRect(0f, 0f, w, h)
        c.drawColor(page.paperColor)
        if (page.pdfName != null) {
            c.drawColor(Color.WHITE)
            txt.setColor(Look.inkSoft)
            txt.textSize = 11f * d
            c.drawText("PDF", w / 2f, h / 2f, txt)
        } else {
            // küçük resim bir kez çizilip önbelleğe alınır; liste her yenilendiğinde sayfalar baştan çizilmez
            val sig = signature()
            val key = System.identityHashCode(page)
            var hit = cache.get(key)
            if (hit == null || hit.sig != sig || hit.bmp.width != w.toInt().coerceAtLeast(1)) {
                val bw = w.toInt().coerceAtLeast(1)
                val bh = h.toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                val bc = Canvas(bmp)
                bc.drawColor(page.paperColor)
                val k = bw / page.width
                bc.scale(k, k)
                renderer.drawPaper(bc, page)
                renderer.drawItems(bc, page, false)
                // çok çizgili sayfalarda küçük resim için tüm çizgiler gerekmez; ilk 400'ü yeter
                val ss = page.strokes
                for (i in 0 until minOf(ss.size, 400)) renderer.drawStroke(bc, ss[i])
                renderer.drawItems(bc, page, true)
                hit = ThumbEntry(bmp, sig)
                cache.put(key, hit)
            }
            c.drawBitmap(hit.bmp, 0f, 0f, null)
        }
        c.restore()
        p.style = Paint.Style.STROKE
        p.strokeWidth = d
        p.setColor(Look.hairline)
        c.drawRect(4f * d, 2f * d, 4f * d + w, 2f * d + h, p)
    }
}


/** Sayfa görünümü ve cilt seçeneklerinin küçük önizlemesi: 0 tek sayfa, 1 çift sayfa, 2 gölge, 3 dikiş, 4 spiral, 5 boyut/renk. */
class MiniView(context: Context, private val kind: Int, private val on: Boolean) : View(context) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((72f * d).toInt(), (54f * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val line = if (on) Look.accent else Look.inkSoft
        fun page(l: Float, t: Float, r: Float, b: Float) {
            p.style = Paint.Style.FILL
            p.color = Color.WHITE
            c.drawRoundRect(l, t, r, b, 3f * d, 3f * d, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = (if (on) 2f else 1.2f) * d
            p.color = line
            c.drawRoundRect(l, t, r, b, 3f * d, 3f * d, p)
            // satır çizgileri
            p.strokeWidth = 1f * d
            p.color = Color.argb(70, 120, 124, 140)
            var y = t + 9f * d
            while (y < b - 5f * d) { c.drawLine(l + 5f * d, y, r - 5f * d, y, p); y += 6f * d }
        }
        val w = width.toFloat()
        val h = height.toFloat()
        when (kind) {
            0 -> page(w * 0.30f, 4f * d, w * 0.70f, h - 4f * d)
            5 -> {
                page(w * 0.24f, 6f * d, w * 0.66f, h - 8f * d)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.6f * d
                p.color = line
                c.drawLine(w * 0.74f, 8f * d, w * 0.74f, h - 10f * d, p)
                c.drawLine(w * 0.74f, 8f * d, w * 0.70f, 14f * d, p)
                c.drawLine(w * 0.74f, 8f * d, w * 0.78f, 14f * d, p)
                c.drawLine(w * 0.74f, h - 10f * d, w * 0.70f, h - 16f * d, p)
                c.drawLine(w * 0.74f, h - 10f * d, w * 0.78f, h - 16f * d, p)
            }
            else -> {
                val mid = w / 2f
                page(6f * d, 4f * d, mid - 0.5f * d, h - 4f * d)
                page(mid + 0.5f * d, 4f * d, w - 6f * d, h - 4f * d)
                when (kind) {
                    2 -> {
                        p.style = Paint.Style.FILL
                        val g = android.graphics.LinearGradient(mid - 7f * d, 0f, mid + 7f * d, 0f,
                            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(90, 0, 0, 0), Color.argb(0, 0, 0, 0)), null, android.graphics.Shader.TileMode.CLAMP)
                        p.shader = g
                        c.drawRect(mid - 7f * d, 4f * d, mid + 7f * d, h - 4f * d, p)
                        p.shader = null
                    }
                    3 -> {
                        p.style = Paint.Style.STROKE
                        p.strokeWidth = 1.4f * d
                        p.color = Color.argb(200, 150, 100, 90)
                        var y = 8f * d
                        while (y < h - 6f * d) { c.drawLine(mid, y, mid, y + 3.2f * d, p); y += 6f * d }
                    }
                    4 -> {
                        p.style = Paint.Style.STROKE
                        p.strokeWidth = 1.8f * d
                        p.color = Color.argb(230, 90, 94, 104)
                        var y = 9f * d
                        while (y < h - 6f * d) { c.drawArc(mid - 3.5f * d, y - 3f * d, mid + 3.5f * d, y + 3f * d, 0f, 360f, false, p); y += 8f * d }
                    }
                }
            }
        }
    }
}
