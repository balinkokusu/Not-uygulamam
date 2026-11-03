package com.notuygulamam.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Tek bir defter sayfasını gösterir. Sayfa sabit bir mantıksal boyuta sahiptir (varsayılan 1200x1700)
 * ve ekrana sığacak şekilde ortalanıp ölçeklenir; bu yüzden ekran dönse de çizimler yerinde kalır.
 * Sayfa ve biten çizgiler bir bitmap'te tutulur, yazarken yalnızca yeni parçalar eklenir.
 */
class DrawView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density

    var tool = Tool.PEN
    var penColor = Color.BLACK
    var penWidthDp = 4f
    var hlColor = Color.rgb(255, 235, 0)
    var hlWidthDp = 14f
    var eraserRadiusDp = 14f
    var fingerDraw = false

    /** 0 ile 100 arası; 0'da çizgi kalınlığı basınçtan etkilenmez. */
    var pressureSensitivity = 70

    /** Bu oturumda gözlenen en yüksek ham basınç; tam basınç ölçeği olarak kullanılır. */
    var maxPressure = 0.25f
        private set

    /** PDF sayfalarının bulunduğu klasör. */
    var pdfDir: File? = null

    var onInfo: ((String) -> Unit)? = null
    var onColorPicked: ((Int) -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    var page: PageData = PageData()
        private set
    private var strokes: ArrayList<Stroke> = page.strokes
    private var undoStack: ArrayList<HistoryAction> = page.undo
    private var redoStack: ArrayList<HistoryAction> = page.redo

    var paper: Paper
        get() = page.paper
        set(value) {
            page.paper = value
            rebuildBase()
            onChanged?.invoke()
        }

    private var current: Stroke? = null
    private var gestureErasing = false
    private val gestureRemoved = ArrayList<Pair<Int, Stroke>>()

    private var base: Bitmap? = null
    private var baseCanvas: Canvas? = null
    private var drawnUpTo = 0
    private var lastInfoMs = 0L
    private var bg: Bitmap? = null
    private var bgToken = 0
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val bgRect = RectF()
    private var picking = false
    private var pickX = 0f
    private var pickY = 0f
    private var pickColor = Color.BLACK

    // Sayfa birimi -> ekran pikseli dönüşümü
    private var sc = 1f
    private var ox = 0f
    private var oy = 0f

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

    fun showPage(p: PageData) {
        page = p
        strokes = p.strokes
        undoStack = p.undo
        redoStack = p.redo
        current = null
        gestureRemoved.clear()
        computeTransform()
        requestBg()
        rebuildBase()
    }

    /** Sayfa bir PDF sayfasıysa görüntüsünü arka planda hazırlar, hazır olunca ekrana yansıtır. */
    private fun requestBg() {
        bg = null
        val name = page.pdfName ?: return
        val dir = pdfDir ?: return
        if (width <= 0) return
        val token = ++bgToken
        val target = page
        val index = page.pdfIndex
        val w = (page.width * sc * 1.5f).toInt().coerceIn(900, 2600)
        PdfStore.executor.execute {
            try {
                val bmp = PdfStore.source(dir, name).render(index, w)
                post {
                    if (token == bgToken && page === target) {
                        bg = bmp
                        rebuildBase()
                    }
                }
            } catch (e: Exception) {
                // PDF çizilemezse sayfa beyaz kalır.
            }
        }
    }

    private fun computeTransform() {
        if (width <= 0 || height <= 0) return
        val m = 16f * density
        sc = max(0.01f, min((width - 2 * m) / page.width, (height - 2 * m) / page.height))
        ox = (width - page.width * sc) / 2f
        oy = (height - page.height * sc) / 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        base = bmp
        baseCanvas = Canvas(bmp)
        computeTransform()
        requestBg()
        rebuildBase()
    }

    private fun beginPage(c: Canvas) {
        c.save()
        c.translate(ox, oy)
        c.scale(sc, sc)
        c.clipRect(0f, 0f, page.width, page.height)
    }

    /** Arka planı, kağıdı ve tüm biten çizgileri bitmap'e baştan çizer. */
    private fun rebuildBase() {
        val c = baseCanvas ?: return
        c.drawColor(Color.rgb(120, 124, 132))
        beginPage(c)
        c.drawColor(Color.WHITE)
        val b = bg
        if (b != null) {
            bgRect.set(0f, 0f, page.width, page.height)
            c.drawBitmap(b, null, bgRect, bgPaint)
        }
        drawPaper(c)
        for (s in strokes) drawStroke(c, s)
        c.restore()
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val b = base
        if (b == null) {
            c.drawColor(Color.rgb(120, 124, 132))
            return
        }
        c.drawBitmap(b, 0f, 0f, null)
        val s = current
        if (s != null && s.highlighter) {
            beginPage(c)
            drawStroke(c, s)
            c.restore()
        }
        if (picking) drawPickerLoupe(c)
    }

    /** Damlalık seçiliyken parmağın/kalemin üstünde, alınacak rengi gösteren daire çizer. */
    private fun drawPickerLoupe(c: Canvas) {
        val r = 30f * density
        val cx = pickX
        var cy = pickY - 90f * density
        if (cy < r + 4f * density) cy = pickY + 90f * density
        fillPaint.setColor(pickColor)
        c.drawCircle(cx, cy, r, fillPaint)
        linePaint.style = Paint.Style.STROKE
        linePaint.alpha = 255
        linePaint.setColor(Color.WHITE)
        linePaint.strokeWidth = 5f * density
        c.drawCircle(cx, cy, r, linePaint)
        linePaint.setColor(Color.BLACK)
        linePaint.strokeWidth = 1.5f * density
        c.drawCircle(cx, cy, r + 3f * density, linePaint)
        c.drawCircle(pickX, pickY, 5f * density, linePaint)
    }

    private fun drawPaper(c: Canvas) {
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

    /** Duyarlılık 0'da çarpan 1; 1'de çarpan 0,2 (hafif basınç) ile 2,8 (tam basınç) arasında. */
    private fun widthFactor(s: Stroke, p: Float): Float = 1f - s.sens * 0.8f + s.sens * 2.6f * p

    private fun segmentWidth(s: Stroke, i: Int): Float {
        val p = (s.ps[i] + s.ps[i - 1]) / 2f
        return s.width * widthFactor(s, p)
    }

    private fun clamp01(p: Float): Float = max(0f, min(1f, p))

    /** Ham basıncı, gözlenen en yüksek basınca göre 0..1 aralığına getirir. */
    private fun normP(p: Float): Float {
        if (p > maxPressure) maxPressure = p
        val r = clamp01(p / maxPressure)
        return Math.pow(r.toDouble(), 0.8).toFloat()
    }

    private fun drawStroke(c: Canvas, s: Stroke) {
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

    /** Kalem çizgisinin henüz çizilmemiş yeni parçalarını bitmap'e ekler ve yalnızca o bölgeyi yeniler. */
    private fun drawPenIncrement(s: Stroke) {
        val c = baseCanvas ?: return
        val n = s.xs.size
        beginPage(c)
        linePaint.setColor(s.color)
        linePaint.alpha = 255
        linePaint.strokeCap = Paint.Cap.ROUND
        var i = drawnUpTo + 1
        while (i < n) {
            val w = segmentWidth(s, i)
            linePaint.strokeWidth = w
            c.drawLine(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], linePaint)
            invalidateSegment(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], w)
            i++
        }
        c.restore()
        drawnUpTo = max(drawnUpTo, n - 1)
    }

    /** Sayfa koordinatlarındaki bir parçanın ekrandaki sınırını yeniler. */
    private fun invalidateSegment(x0: Float, y0: Float, x1: Float, y1: Float, w: Float) {
        val pad = w * sc + 3f * density
        val sx0 = ox + x0 * sc
        val sy0 = oy + y0 * sc
        val sx1 = ox + x1 * sc
        val sy1 = oy + y1 * sc
        invalidate(
            (min(sx0, sx1) - pad).toInt(), (min(sy0, sy1) - pad).toInt(),
            (max(sx0, sx1) + pad).toInt() + 1, (max(sy0, sy1) + pad).toInt() + 1
        )
    }

    private fun typeName(t: Int): String = when (t) {
        MotionEvent.TOOL_TYPE_FINGER -> "parmak"
        MotionEvent.TOOL_TYPE_STYLUS -> "kalem"
        MotionEvent.TOOL_TYPE_ERASER -> "kalem silgisi"
        MotionEvent.TOOL_TYPE_MOUSE -> "fare"
        else -> "bilinmiyor($t)"
    }

    private fun pageX(x: Float): Float = (x - ox) / sc
    private fun pageY(y: Float): Float = (y - oy) / sc

    private fun samplePixel(x: Float, y: Float): Int {
        val b = base ?: return Color.BLACK
        val px = x.toInt().coerceIn(0, b.width - 1)
        val py = y.toInt().coerceIn(0, b.height - 1)
        return b.getPixel(px, py) or (0xFF shl 24)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val type = e.getToolType(0)
        val isStylus = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER

        // Bilgi satırı her olayda değil, yaklaşık 6 kez/saniye güncellenir; aksi hâlde yazı gecikir.
        val now = e.eventTime
        if (now - lastInfoMs > 160L || e.actionMasked != MotionEvent.ACTION_MOVE) {
            lastInfoMs = now
            onInfo?.invoke(
                String.format(
                    Locale.US, "Son olay: araç=%s, basınç=%.3f (en yüksek %.3f), düğme=%d, eğim=%.2f",
                    typeName(type), e.pressure, maxPressure, e.buttonState, e.getAxisValue(MotionEvent.AXIS_TILT)
                )
            )
        }

        if (tool == Tool.PICKER) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    picking = true
                    pickX = e.x
                    pickY = e.y
                    pickColor = samplePixel(e.x, e.y)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    val col = samplePixel(e.x, e.y)
                    picking = false
                    penColor = col
                    tool = Tool.PEN
                    invalidate()
                    onColorPicked?.invoke(col)
                }
                MotionEvent.ACTION_CANCEL -> {
                    picking = false
                    invalidate()
                }
            }
            return true
        }

        if (!isStylus && !fingerDraw) return true

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(e)
                gestureErasing = tool == Tool.ERASER ||
                    type == MotionEvent.TOOL_TYPE_ERASER ||
                    (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                gestureRemoved.clear()
                val gx = pageX(e.x)
                val gy = pageY(e.y)
                if (gestureErasing) {
                    if (eraseAt(gx, gy)) rebuildBase()
                } else {
                    val hl = tool == Tool.HIGHLIGHTER
                    val s = if (hl) Stroke(hlColor, hlWidthDp * UNIT, true)
                    else Stroke(penColor, penWidthDp * UNIT, false, pressureSensitivity / 100f)
                    s.add(gx, gy, if (hl) 0.5f else normP(e.pressure))
                    current = s
                    drawnUpTo = 0
                    if (!hl) {
                        val r = s.width * widthFactor(s, s.ps[0]) * 0.5f
                        val c = baseCanvas
                        if (c != null) {
                            beginPage(c)
                            fillPaint.setColor(s.color)
                            c.drawCircle(gx, gy, r, fillPaint)
                            c.restore()
                        }
                        invalidateSegment(gx, gy, gx, gy, r * 2f)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureErasing) {
                    var removed = false
                    for (h in 0 until e.historySize) {
                        if (eraseAt(pageX(e.getHistoricalX(h)), pageY(e.getHistoricalY(h)))) removed = true
                    }
                    if (eraseAt(pageX(e.x), pageY(e.y))) removed = true
                    if (removed) rebuildBase()
                } else {
                    val s = current
                    if (s != null) {
                        val hl = s.highlighter
                        val before = s.xs.size
                        for (h in 0 until e.historySize) {
                            s.add(
                                pageX(e.getHistoricalX(h)), pageY(e.getHistoricalY(h)),
                                if (hl) 0.5f else normP(e.getHistoricalPressure(h))
                            )
                        }
                        s.add(pageX(e.x), pageY(e.y), if (hl) 0.5f else normP(e.pressure))
                        if (hl) {
                            val from = max(0, before - 1)
                            for (i in from + 1 until s.xs.size) {
                                invalidateSegment(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], s.width)
                            }
                        } else {
                            drawPenIncrement(s)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gestureErasing) {
                    if (gestureRemoved.isNotEmpty()) {
                        undoStack.add(HistoryAction.Erase(ArrayList(gestureRemoved)))
                        redoStack.clear()
                        onChanged?.invoke()
                    }
                    gestureRemoved.clear()
                } else {
                    val s = current
                    if (s != null) {
                        strokes.add(s)
                        undoStack.add(HistoryAction.Add(s))
                        redoStack.clear()
                        current = null
                        if (s.highlighter) {
                            val c = baseCanvas
                            if (c != null) {
                                beginPage(c)
                                drawStroke(c, s)
                                c.restore()
                            }
                            invalidate()
                        }
                        onChanged?.invoke()
                    }
                }
            }
        }
        return true
    }

    private fun eraseAt(x: Float, y: Float): Boolean {
        val r = eraserRadiusDp * density / sc
        var removedAny = false
        var i = strokes.size - 1
        while (i >= 0) {
            val s = strokes[i]
            if (hits(s, x, y, r + s.width / 2f)) {
                gestureRemoved.add(Pair(i, s))
                strokes.removeAt(i)
                removedAny = true
            }
            i--
        }
        return removedAny
    }

    private fun hits(s: Stroke, x: Float, y: Float, r: Float): Boolean {
        val n = s.xs.size
        if (n == 1) return hypot(s.xs[0] - x, s.ys[0] - y) <= r
        for (k in 1 until n) {
            if (distToSegment(x, y, s.xs[k - 1], s.ys[k - 1], s.xs[k], s.ys[k]) <= r) return true
        }
        return false
    }

    private fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return hypot(px - ax, py - ay)
        val t = max(0f, min(1f, ((px - ax) * dx + (py - ay) * dy) / len2))
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val a = undoStack.removeAt(undoStack.size - 1)
        when (a) {
            is HistoryAction.Add -> strokes.remove(a.stroke)
            is HistoryAction.Erase -> {
                for ((idx, s) in a.removed.asReversed()) strokes.add(min(idx, strokes.size), s)
            }
        }
        redoStack.add(a)
        rebuildBase()
        onChanged?.invoke()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val a = redoStack.removeAt(redoStack.size - 1)
        when (a) {
            is HistoryAction.Add -> strokes.add(a.stroke)
            is HistoryAction.Erase -> {
                for ((_, s) in a.removed) strokes.remove(s)
            }
        }
        undoStack.add(a)
        rebuildBase()
        onChanged?.invoke()
    }

    fun clearAll() {
        if (strokes.isEmpty()) return
        val removed = ArrayList<Pair<Int, Stroke>>()
        for (i in strokes.indices.reversed()) removed.add(Pair(i, strokes[i]))
        strokes.clear()
        undoStack.add(HistoryAction.Erase(removed))
        redoStack.clear()
        rebuildBase()
        onChanged?.invoke()
    }

    companion object {
        /** Kalınlık kaydırıcısındaki 1 birimin sayfa birimi karşılığı. */
        const val UNIT = 2.2f
    }
}
