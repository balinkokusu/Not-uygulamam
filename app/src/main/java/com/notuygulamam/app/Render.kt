package com.notuygulamam.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.OutputStream
import kotlin.math.max

/**
 * Kağıdı ve çizgileri bir tuvale çizer. Ekranda ve PDF dışa aktarmada aynı kod kullanılır.
 * Her kullanıcının kendi boyaları olduğundan farklı iş parçacıklarında ayrı örnekler oluşturulmalıdır.
 */
class PageRenderer {
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
    private const val PAGE_WIDTH_PT = 595

    fun export(pages: List<PageData>, pdfDir: File, out: OutputStream) {
        val doc = PdfDocument()
        val renderer = PageRenderer()
        val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val rect = RectF()
        try {
            for ((i, pg) in pages.withIndex()) {
                val hPt = max(1, (PAGE_WIDTH_PT * pg.height / pg.width).toInt())
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, hPt, i + 1).create()
                val page = doc.startPage(info)
                val c = page.canvas
                val k = PAGE_WIDTH_PT / pg.width
                c.scale(k, k)
                c.drawColor(Color.WHITE)
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
