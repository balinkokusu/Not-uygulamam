package com.notuygulamam.app

import android.content.Context
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

/** Sürüm 0.1: tek sayfalık çizim yüzeyi. Basınç, kalem düğmesi ve silgi olaylarını sınamak için. */
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
            invalidate()
        }
    var onInfo: ((String) -> Unit)? = null

    private val strokes = ArrayList<Stroke>()
    private val undoStack = ArrayList<HistoryAction>()
    private val redoStack = ArrayList<HistoryAction>()
    private var current: Stroke? = null
    private var gestureErasing = false
    private val gestureRemoved = ArrayList<Pair<Int, Stroke>>()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setColor(Color.rgb(170, 176, 190))
    }
    private val paperPaint = Paint().apply {
        setColor(Color.rgb(200, 205, 215))
        strokeWidth = density
    }
    private val path = Path()

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.WHITE)
        drawPaper(c)
        for (s in strokes) drawStroke(c, s)
        current?.let { drawStroke(c, it) }
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
        if (n == 1) {
            dotPaint.setColor(s.color)
            c.drawCircle(s.xs[0], s.ys[0], s.width / 2f, dotPaint)
            dotPaint.setColor(Color.rgb(170, 176, 190))
            return
        }
        for (i in 1 until n) {
            val p = (s.ps[i] + s.ps[i - 1]) / 2f
            linePaint.strokeWidth = s.width * (0.35f + 1.3f * p)
            c.drawLine(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], linePaint)
        }
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
        onInfo?.invoke(
            String.format(
                Locale.US, "Son olay: araç=%s, basınç=%.2f, düğme=%d, eğim=%.2f",
                typeName(type), e.pressure, e.buttonState, e.getAxisValue(MotionEvent.AXIS_TILT)
            )
        )
        if (!isStylus && !fingerDraw) return true

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureErasing = tool == Tool.ERASER ||
                    type == MotionEvent.TOOL_TYPE_ERASER ||
                    (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                gestureRemoved.clear()
                if (gestureErasing) {
                    eraseAt(e.x, e.y)
                } else {
                    val hl = tool == Tool.HIGHLIGHTER
                    val s = if (hl) Stroke(hlColor, hlWidthDp * density, true)
                    else Stroke(penColor, penWidthDp * density, false)
                    s.add(e.x, e.y, if (hl) 0.5f else clamp01(e.pressure))
                    current = s
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureErasing) {
                    for (h in 0 until e.historySize) eraseAt(e.getHistoricalX(h), e.getHistoricalY(h))
                    eraseAt(e.x, e.y)
                } else {
                    val s = current
                    if (s != null) {
                        val hl = s.highlighter
                        for (h in 0 until e.historySize) {
                            s.add(
                                e.getHistoricalX(h), e.getHistoricalY(h),
                                if (hl) 0.5f else clamp01(e.getHistoricalPressure(h))
                            )
                        }
                        s.add(e.x, e.y, if (hl) 0.5f else clamp01(e.pressure))
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
                    }
                    current = null
                }
            }
        }
        invalidate()
        return true
    }

    private fun eraseAt(x: Float, y: Float) {
        val r = eraserRadiusDp * density
        var i = strokes.size - 1
        while (i >= 0) {
            val s = strokes[i]
            if (hits(s, x, y, r + s.width / 2f)) {
                gestureRemoved.add(Pair(i, s))
                strokes.removeAt(i)
            }
            i--
        }
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
        invalidate()
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
        invalidate()
    }

    fun clearAll() {
        if (strokes.isEmpty()) return
        val removed = ArrayList<Pair<Int, Stroke>>()
        for (i in strokes.indices.reversed()) removed.add(Pair(i, strokes[i]))
        strokes.clear()
        undoStack.add(HistoryAction.Erase(removed))
        redoStack.clear()
        invalidate()
    }
}
