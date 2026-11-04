package com.notuygulamam.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Calendar
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.OutputStream
import kotlin.math.max

/**
 * Kağıdı ve çizgileri bir tuvale çizer. Ekranda ve PDF dışa aktarmada aynı kod kullanılır.
 * Her kullanıcının kendi boyaları olduğundan farklı iş parçacıklarında ayrı örnekler oluşturulmalıdır.
 */
class PageRenderer {
    private val DW = 1200f
    private val DH = 1700f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setColor(Color.rgb(170, 176, 190))
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paperPaint = Paint().apply {
        setColor(Color.rgb(200, 205, 215))
        strokeWidth = 1.5f
    }
    private val path = Path()

    /** Duyarlılık 0'da çarpan 1; 1'de çarpan 0,2 (hafif basınç) ile 2,8 (tam basınç) arasında. */
    fun widthFactor(s: Stroke, p: Float): Float = 1f - s.sens * 0.85f + s.sens * 3.0f * p

    fun segmentWidth(s: Stroke, i: Int): Float {
        val p = (s.ps[i] + s.ps[i - 1]) / 2f
        return s.width * widthFactor(s, p)
    }

    fun drawPaper(c: Canvas, page: PageData) {
        val step = 40f
        val w = page.width
        val h = page.height
        when (page.paper) {
            Paper.BLANK -> {}
            Paper.RULED -> {
                var y = step * 2f
                while (y < h) {
                    c.drawLine(0f, y, w, y, paperPaint)
                    y += step
                }
            }
            Paper.GRID -> {
                var y = step
                while (y < h) {
                    c.drawLine(0f, y, w, y, paperPaint)
                    y += step
                }
                var x = step
                while (x < w) {
                    c.drawLine(x, 0f, x, h, paperPaint)
                    x += step
                }
            }
            Paper.DOTTED -> {
                var y = step
                while (y < h) {
                    var x = step
                    while (x < w) {
                        c.drawCircle(x, y, 2.2f, dotPaint)
                        x += step
                    }
                    y += step
                }
            }
            Paper.TODO, Paper.DAILY, Paper.WEEKLY, Paper.MONTHLY, Paper.CORNELL, Paper.YEAR -> drawTemplate(c, page)
        }
    }

    /** Hazır şablonlar 1200 x 1700 tasarım alanında çizilir; sayfa başka boyuttaysa orantılı küçültülüp ortalanır. */
    private fun drawTemplate(c: Canvas, page: PageData) {
        val k = kotlin.math.min(page.width / DW, page.height / DH)
        c.save()
        c.translate((page.width - DW * k) / 2f, (page.height - DH * k) / 2f)
        c.scale(k, k)
        when (page.paper) {
            Paper.TODO -> drawTodo(c, page)
            Paper.DAILY -> drawDaily(c, page)
            Paper.WEEKLY -> drawWeekly(c, page)
            Paper.MONTHLY -> drawMonthly(c, page)
            Paper.CORNELL -> drawCornell(c, page)
            Paper.YEAR -> drawYear(c, page)
            else -> {}
        }
        c.restore()
    }

    // ---- Hazır sayfa şablonları (1200 x 1700 sayfa birimine göre; sayfa boyutu farklıysa orantılı çizilir) ----

    private val ink = Color.rgb(60, 60, 70)
    private val soft = Color.rgb(140, 146, 160)
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        setColor(Color.rgb(170, 176, 190))
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rr = RectF()

    private fun label(
        c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int,
        bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT
    ) {
        textPaint.textSize = size
        textPaint.setColor(color)
        textPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textPaint.textAlign = align
        c.drawText(t, x, y, textPaint)
    }

    private fun checkbox(c: Canvas, x: Float, y: Float, size: Float = 44f) {
        rr.set(x, y, x + size, y + size)
        c.drawRoundRect(rr, 8f, 8f, boxPaint)
    }

    private fun header(c: Canvas, text: String) {
        label(c, text, 80f, 120f, 54f, ink, true)
        label(c, "Tarih:", 820f, 116f, 28f, soft)
        c.drawLine(910f, 120f, 1120f, 120f, paperPaint)
        c.drawLine(80f, 150f, 1120f, 150f, boxPaint)
    }

    private fun drawTodo(c: Canvas, page: PageData) {
        header(c, "YAPILACAKLAR")
        var y = 190f
        while (y + 110f < DH - 40f) {
            checkbox(c, 86f, y + 20f, 46f)
            c.drawLine(160f, y + 80f, 1120f, y + 80f, paperPaint)
            y += 110f
        }
    }

    private fun drawDaily(c: Canvas, page: PageData) {
        header(c, "GÜNLÜK AJANDA")
        for (i in 0 until 17) {
            val y = 190f + i * 72f
            label(c, String.format("%02d:00", 6 + i), 80f, y + 46f, 26f, soft)
            c.drawLine(190f, y + 72f, 740f, y + 72f, paperPaint)
        }
        for (k in 0 until 3) {
            c.drawLine(80f, 1480f + k * 70f, 740f, 1480f + k * 70f, paperPaint)
        }
        label(c, "Günün notu", 80f, 1450f, 24f, soft)
        c.drawLine(770f, 175f, 770f, DH - 70f, boxPaint)

        label(c, "ÖNCELİKLER", 800f, 215f, 28f, ink, true)
        for (k in 0 until 3) {
            val y = 240f + k * 72f
            checkbox(c, 800f, y + 8f)
            c.drawLine(860f, y + 54f, 1120f, y + 54f, paperPaint)
        }
        label(c, "YAPILACAKLAR", 800f, 520f, 28f, ink, true)
        for (k in 0 until 7) {
            val y = 545f + k * 72f
            checkbox(c, 800f, y + 8f)
            c.drawLine(860f, y + 54f, 1120f, y + 54f, paperPaint)
        }
        label(c, "NOTLAR", 800f, 1100f, 28f, ink, true)
        var y = 1150f
        while (y < DH - 60f) {
            c.drawLine(800f, y, 1120f, y, paperPaint)
            y += 58f
        }
    }

    private fun drawWeekly(c: Canvas, page: PageData) {
        label(c, "HAFTALIK PLAN", 80f, 120f, 54f, ink, true)
        label(c, "Hafta:", 820f, 116f, 28f, soft)
        c.drawLine(900f, 120f, 1120f, 120f, paperPaint)
        val names = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar", "Notlar")
        val bw = 520f
        val bh = (DH - 180f - 80f - 3 * 20f) / 4f
        for (i in names.indices) {
            val x = 70f + (i % 2) * (bw + 20f)
            val y = 180f + (i / 2) * (bh + 20f)
            rr.set(x, y, x + bw, y + bh)
            c.drawRoundRect(rr, 14f, 14f, boxPaint)
            label(c, names[i], x + 18f, y + 40f, 30f, ink, true)
            c.drawLine(x + 12f, y + 54f, x + bw - 12f, y + 54f, boxPaint)
            var ly = y + 106f
            while (ly < y + bh - 8f) {
                c.drawLine(x + 18f, ly, x + bw - 18f, ly, paperPaint)
                ly += 52f
            }
        }
    }

    private fun monthShape(year: Int, month: Int): Pair<Int, Int> {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, 1)
        val off = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        return Pair(off, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    }

    private fun drawMonthly(c: Canvas, page: PageData) {
        val dated = page.calMonth in 1..12 && page.calYear > 0
        val title = if (dated) Planner.MONTHS[page.calMonth - 1] + " " + page.calYear else "AYLIK TAKVİM"
        label(c, title, 70f, 125f, 60f, ink, true)
        val x0 = 70f
        val cw = 1060f / 7f
        val top = 190f
        val headH = 50f
        val rh = (DH - 70f - top - headH) / 6f
        fillPaint.setColor(Color.rgb(238, 240, 244))
        c.drawRect(x0, top, x0 + 1060f, top + headH, fillPaint)
        val days = Planner.DAYS
        for (i in 0 until 7) label(c, days[i], x0 + cw * (i + 0.5f), top + 34f, 26f, soft, false, Paint.Align.CENTER)
        val gridTop = top + headH
        for (r in 0..6) c.drawLine(x0, gridTop + r * rh, x0 + 1060f, gridTop + r * rh, boxPaint)
        c.drawLine(x0, top, x0 + 1060f, top, boxPaint)
        for (i in 0..7) c.drawLine(x0 + i * cw, top, x0 + i * cw, gridTop + 6 * rh, boxPaint)
        if (dated) {
            val (off, n) = monthShape(page.calYear, page.calMonth)
            for (d in 1..n) {
                val idx = off + d - 1
                label(c, d.toString(), x0 + (idx % 7) * cw + 10f, gridTop + (idx / 7) * rh + 34f, 26f, ink)
            }
        }
    }

    private fun drawCornell(c: Canvas, page: PageData) {
        label(c, "Konu:", 80f, 110f, 30f, soft)
        c.drawLine(170f, 114f, 1120f, 114f, paperPaint)
        val bottom = DH - 300f
        c.drawLine(70f, 150f, 1130f, 150f, boxPaint)
        c.drawLine(70f, bottom, 1130f, bottom, boxPaint)
        c.drawLine(380f, 150f, 380f, bottom, boxPaint)
        label(c, "Anahtar kelimeler", 85f, 195f, 24f, soft)
        label(c, "Notlar", 410f, 195f, 24f, soft)
        label(c, "Özet", 85f, bottom + 40f, 24f, soft)
    }

    private fun drawYear(c: Canvas, page: PageData) {
        val dated = page.calYear > 0
        label(c, if (dated) "YILLIK TAKVİM " + page.calYear else "YILLIK TAKVİM", 80f, 125f, 56f, ink, true)
        val letters = arrayOf("P", "S", "Ç", "P", "C", "C", "P")
        for (m in 0 until 12) {
            val bx = 75f + (m % 3) * 350f
            val by = 200f + (m / 3) * 345f
            label(c, Planner.MONTHS[m], bx + 4f, by + 34f, 30f, ink, true)
            for (i in 0 until 7) label(c, letters[i], bx + 25f + i * 50f, by + 76f, 20f, soft, false, Paint.Align.CENTER)
            if (dated) {
                val (off, n) = monthShape(page.calYear, m + 1)
                for (d in 1..n) {
                    val idx = off + d - 1
                    label(c, d.toString(), bx + 25f + (idx % 7) * 50f, by + 118f + (idx / 7) * 42f, 22f, ink, false, Paint.Align.CENTER)
                }
            }
        }
    }

    fun drawStroke(c: Canvas, s: Stroke) {
        val n = s.xs.size
        if (n == 0) return
        if (s.highlighter) {
            linePaint.setColor(s.color)
            linePaint.alpha = 110
            linePaint.strokeWidth = s.width
            linePaint.strokeCap = Paint.Cap.SQUARE
            path.reset()
            path.moveTo(s.xs[0], s.ys[0])
            if (n == 1) path.lineTo(s.xs[0] + 0.1f, s.ys[0])
            for (i in 1 until n) path.lineTo(s.xs[i], s.ys[i])
            c.drawPath(path, linePaint)
            linePaint.alpha = 255
            linePaint.strokeCap = Paint.Cap.ROUND
            return
        }
        linePaint.setColor(s.color)
        linePaint.alpha = 255
        linePaint.strokeCap = Paint.Cap.ROUND
        if (n == 1) {
            fillPaint.setColor(s.color)
            c.drawCircle(s.xs[0], s.ys[0], s.width * widthFactor(s, s.ps[0]) * 0.5f, fillPaint)
            return
        }
        for (i in 1 until n) {
            linePaint.strokeWidth = segmentWidth(s, i)
            c.drawLine(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], linePaint)
        }
    }
}

/** Defteri, çizimleri PDF sayfalarının üstüne işleyerek tek bir PDF dosyasına yazar. */
object PdfExporter {
    /** 1200 sayfa birimi = 210 mm = 595,28 pt; sayfalar fiziksel boyutta (A3, A4, A5 ...) yazılır. */
    private const val K = 595.28f / 1200f

    fun export(pages: List<PageData>, pdfDir: File, out: OutputStream) {
        val doc = PdfDocument()
        val renderer = PageRenderer()
        val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val rect = RectF()
        try {
            for ((i, pg) in pages.withIndex()) {
                val wPt = max(1, Math.round(pg.width * K))
                val hPt = max(1, Math.round(pg.height * K))
                val info = PdfDocument.PageInfo.Builder(wPt, hPt, i + 1).create()
                val page = doc.startPage(info)
                val c = page.canvas
                c.scale(K, K)
                c.drawColor(pg.paperColor)
                val name = pg.pdfName
                if (name != null) {
                    try {
                        val bmp = PdfStore.source(pdfDir, name).render(pg.pdfIndex, 1800)
                        rect.set(0f, 0f, pg.width, pg.height)
                        c.drawBitmap(bmp, null, rect, bgPaint)
                        bmp.recycle()
                    } catch (e: Exception) {
                        // PDF sayfası çizilemezse beyaz zemin üstüne yazılanlar yine de aktarılır.
                    }
                }
                renderer.drawPaper(c, pg)
                for (s in pg.strokes) renderer.drawStroke(c, s)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}
