package com.notuygulamam.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class Tool { PEN, HIGHLIGHTER, ERASER }

enum class Paper(val label: String) {
    BLANK("boş"), RULED("çizgili"), GRID("kareli"), DOTTED("noktalı")
}

class Stroke(val color: Int, val width: Float, val highlighter: Boolean) {
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    val ps = ArrayList<Float>()
    fun add(x: Float, y: Float, p: Float) {
        xs.add(x); ys.add(y); ps.add(p)
    }
}

private sealed class HistoryAction {
    class Add(val stroke: Stroke) : HistoryAction()
    class Erase(val removed: List<Pair<Int, Stroke>>) : HistoryAction()
}

/**
 * Sürüm 0.3: gecikmeyi azaltmak için sayfa (kağıt ve biten çizgiler) bir bitmap'te tutuluyor.
 * Yazarken yalnızca yeni çizgi parçaları bitmap'e eklenip değişen dikdörtgen yeniden çiziliyor.
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
    var paper = Paper.RULED
        set(value) {
            field = value
            rebuildBase()
        }
    var onInfo: ((String) -> Unit)? = null

    private val strokes = ArrayList<Stroke>()
    private val undoStack = ArrayList<HistoryAction>()
    private val redoStack = ArrayList<HistoryAction>()
    private var current: Stroke? = null
    private var gestureErasing = false
    private val gestureRemoved = ArrayList<Pair<Int, Stroke>>()

    private var base: Bitmap? = null
    private var baseCanvas: Canvas? = null
    private var drawnUpTo = 0
    private var lastInfoMs = 0L

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
        strokeWidth = density
    }
    private val path = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        base = bmp
        baseCanvas = Canvas(bmp)
        rebuildBase()
    }

    /** Kağıdı ve tüm biten çizgileri bitmap'e baştan çizer. */
    private fun rebuildBase() {
        val c = baseCanvas ?: return
        c.drawColor(Color.WHITE)
        drawPaper(c)
        for (s in strokes) drawStroke(c, s)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val b = base
        if (b == null) {
            c.drawColor(Color.WHITE)
            return
        }
        c.drawBitmap(b, 0f, 0f, null)
        val s = current
        if (s != null && s.highlighter) drawStroke(c, s)
    }

    private fun drawPaper(c: Canvas) {
        val step = 30f * density
        val w = width.toFloat()
        val h = height.toFloat()
        when (paper) {
            Paper.BLANK -> {}
            Paper.RULED -> {
                var y = step * 1.5f
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
                        c.drawCircle(x, y, 1.6f * density, dotPaint)
                        x += step
                    }
                    y += step
                }
            }
        }
    }

    private fun segmentWidth(s: Stroke, i: Int): Float {
        val p = (s.ps[i] + s.ps[i - 1]) / 2f
        return s.width * (0.35f + 1.3f * p)
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
            c.drawCircle(s.xs[0], s.ys[0], s.width * 0.5f, fillPaint)
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
        drawnUpTo = max(drawnUpTo, n - 1)
    }

    private fun invalidateSegment(x0: Float, y0: Float, x1: Float, y1: Float, w: Float) {
        val pad = w + 2f * density
        invalidate(
            (min(x0, x1) - pad).toInt(), (min(y0, y1) - pad).toInt(),
            (max(x0, x1) + pad).toInt() + 1, (max(y0, y1) + pad).toInt() + 1
        )
    }

    private fun typeName(t: Int): String = when (t) {
        MotionEvent.TOOL_TYPE_FINGER -> "parmak"
        MotionEvent.TOOL_TYPE_STYLUS -> "kalem"
        MotionEvent.TOOL_TYPE_ERASER -> "kalem silgisi"
        MotionEvent.TOOL_TYPE_MOUSE -> "fare"
        else -> "bilinmiyor($t)"
    }

    private fun clamp01(p: Float): Float = max(0f, min(1f, p))

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val type = e.getToolType(0)
        val isStylus = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER

        // Bilgi satırı her olayda değil, yaklaşık 6 kez/saniye güncellenir; aksi hâlde yazı gecikir.
        val now = e.eventTime
        if (now - lastInfoMs > 160L || e.actionMasked != MotionEvent.ACTION_MOVE) {
            lastInfoMs = now
            onInfo?.invoke(
                String.format(
                    Locale.US, "Son olay: araç=%s, basınç=%.2f, düğme=%d, eğim=%.2f",
                    typeName(type), e.pressure, e.buttonState, e.getAxisValue(MotionEvent.AXIS_TILT)
                )
            )
        }
        if (!isStylus && !fingerDraw) return true

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(e)
                gestureErasing = tool == Tool.ERASER ||
                    type == MotionEvent.TOOL_TYPE_ERASER ||
                    (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                gestureRemoved.clear()
                if (gestureErasing) {
                    if (eraseAt(e.x, e.y)) rebuildBase()
                } else {
                    val hl = tool == Tool.HIGHLIGHTER
                    val s = if (hl) Stroke(hlColor, hlWidthDp * density, true)
                    else Stroke(penColor, penWidthDp * density, false)
                    s.add(e.x, e.y, if (hl) 0.5f else clamp01(e.pressure))
                    current = s
                    drawnUpTo = 0
                    if (!hl) {
                        val r = s.width * (0.35f + 1.3f * s.ps[0]) * 0.5f
                        fillPaint.setColor(s.color)
                        baseCanvas?.drawCircle(e.x, e.y, r, fillPaint)
                        invalidateSegment(e.x, e.y, e.x, e.y, r * 2f)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureErasing) {
                    var removed = false
                    for (h in 0 until e.historySize) {
                        if (eraseAt(e.getHistoricalX(h), e.getHistoricalY(h))) removed = true
                    }
                    if (eraseAt(e.x, e.y)) removed = true
                    if (removed) rebuildBase()
                } else {
                    val s = current
                    if (s != null) {
                        val hl = s.highlighter
                        val before = s.xs.size
                        for (h in 0 until e.historySize) {
                            s.add(
                                e.getHistoricalX(h), e.getHistoricalY(h),
                                if (hl) 0.5f else clamp01(e.getHistoricalPressure(h))
                            )
                        }
                        s.add(e.x, e.y, if (hl) 0.5f else clamp01(e.pressure))
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
                            baseCanvas?.let { drawStroke(it, s) }
                            invalidate()
                        }
                    }
                }
            }
        }
        return true
    }

    private fun eraseAt(x: Float, y: Float): Boolean {
        val r = eraserRadiusDp * density
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
    }

    fun clearAll() {
        if (strokes.isEmpty()) return
        val removed = ArrayList<Pair<Int, Stroke>>()
        for (i in strokes.indices.reversed()) removed.add(Pair(i, strokes[i]))
        strokes.clear()
        undoStack.add(HistoryAction.Erase(removed))
        redoStack.clear()
        rebuildBase()
    }
}
