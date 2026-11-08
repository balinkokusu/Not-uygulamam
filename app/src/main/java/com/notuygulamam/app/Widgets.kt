package com.notuygulamam.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

enum class Icon {
    PEN, HIGHLIGHTER, ERASER, PICKER, UNDO, REDO, PAPER, FINGER, TRASH, PREV, NEXT, ADD, PAGES, PALETTE, IMPORT, THICKNESS, SAVE, EXPORT, ARCHIVE, HOME, TAB, MORE, SPREAD, TEXT, STICKER, LASSO, LASER, CALC, TIMER, TAPE, RULER, HAND, IMAGE, MIC, SELECT
}

/** Düğme yerine simge gösteren basit görünüm; simgeler kodla çizilir. */
class IconButton(
    context: Context,
    private val icon: Icon,
    private val label: String,
    private val compact: Boolean = false
) : View(context) {
    private val d = resources.displayMetrics.density
    var active = false
        set(value) {
            field = value
            invalidate()
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val path = Path()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * d
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (compact) setMeasuredDimension((42 * d).toInt(), (42 * d).toInt())
        else setMeasuredDimension((74 * d).toInt(), (68 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        if (compact) {
            rect.set(4 * d, 4 * d, width - 4 * d, height - 4 * d)
            if (active) {
                bg.setColor(Look.accentSoft)
                c.drawRoundRect(rect, 10 * d, 10 * d, bg)
            }
            p.setColor(if (active) Look.accent else Look.ink)
            val uc = width * 0.44f
            p.strokeWidth = maxOf(1.3f * d, uc * 0.075f)
            c.save()
            c.translate((width - uc) / 2f, (height - uc) / 2f)
            glyph(c, uc)
            c.restore()
            return
        }
        rect.set(3 * d, 3 * d, width - 3 * d, height - 3 * d)
        bg.setColor(if (active) Color.rgb(35, 35, 40) else Color.rgb(236, 237, 240))
        c.drawRoundRect(rect, 12 * d, 12 * d, bg)
        val fg = if (active) Color.WHITE else Color.rgb(30, 30, 34)
        p.setColor(fg)
        textPaint.setColor(fg)
        val areaH = height - 20 * d
        val u = areaH * 0.66f
        p.strokeWidth = u * 0.115f
        c.save()
        c.translate((width - u) / 2f, 5 * d + (areaH - u) / 2f)
        glyph(c, u)
        c.restore()
        c.drawText(label, width / 2f, height - 8 * d, textPaint)
    }

    private fun line(c: Canvas, u: Float, x0: Float, y0: Float, x1: Float, y1: Float) {
        c.drawLine(x0 * u, y0 * u, x1 * u, y1 * u, p)
    }

    private fun poly(c: Canvas, u: Float, close: Boolean, vararg v: Float) {
        path.reset()
        path.moveTo(v[0] * u, v[1] * u)
        var i = 2
        while (i + 1 < v.size) {
            path.lineTo(v[i] * u, v[i + 1] * u)
            i += 2
        }
        if (close) path.close()
        c.drawPath(path, p)
    }

    private fun undoGlyph(c: Canvas, u: Float) {
        path.reset()
        path.moveTo(0.1f * u, 0.4f * u)
        path.lineTo(0.6f * u, 0.4f * u)
        path.quadTo(0.95f * u, 0.4f * u, 0.95f * u, 0.7f * u)
        path.quadTo(0.95f * u, 0.95f * u, 0.6f * u, 0.95f * u)
        path.lineTo(0.35f * u, 0.95f * u)
        c.drawPath(path, p)
        poly(c, u, false, 0.32f, 0.18f, 0.1f, 0.4f, 0.32f, 0.62f)
    }

    private fun glyph(c: Canvas, u: Float) {
        when (icon) {
            Icon.PEN -> {
                poly(c, u, true, 0.28f, 0.72f, 0.72f, 0.28f, 0.86f, 0.42f, 0.42f, 0.86f)
                poly(c, u, true, 0.28f, 0.72f, 0.42f, 0.86f, 0.1f, 0.9f)
            }
            Icon.HIGHLIGHTER -> {
                poly(c, u, true, 0.2f, 0.62f, 0.58f, 0.14f, 0.86f, 0.4f, 0.44f, 0.82f)
                line(c, u, 0.08f, 0.96f, 0.6f, 0.96f)
            }
            Icon.ERASER -> {
                poly(c, u, true, 0.12f, 0.62f, 0.5f, 0.24f, 0.88f, 0.62f, 0.5f, 1.0f)
                line(c, u, 0.31f, 0.43f, 0.69f, 0.81f)
            }
            Icon.PICKER -> {
                line(c, u, 0.1f, 0.9f, 0.48f, 0.52f)
                line(c, u, 0.4f, 0.6f, 0.62f, 0.82f)
                c.drawCircle(0.68f * u, 0.32f * u, 0.2f * u, p)
            }
            Icon.UNDO -> undoGlyph(c, u)
            Icon.REDO -> {
                c.save()
                c.scale(-1f, 1f, u / 2f, 0f)
                undoGlyph(c, u)
                c.restore()
            }
            Icon.PAPER -> {
                c.drawRect(0.1f * u, 0.1f * u, 0.9f * u, 0.9f * u, p)
                line(c, u, 0.37f, 0.1f, 0.37f, 0.9f)
                line(c, u, 0.63f, 0.1f, 0.63f, 0.9f)
                line(c, u, 0.1f, 0.37f, 0.9f, 0.37f)
                line(c, u, 0.1f, 0.63f, 0.9f, 0.63f)
            }
            Icon.FINGER -> {
                rect.set(0.36f * u, 0.08f * u, 0.64f * u, 0.96f * u)
                c.drawRoundRect(rect, 0.14f * u, 0.14f * u, p)
                line(c, u, 0.64f, 0.55f, 0.86f, 0.55f)
                line(c, u, 0.36f, 0.62f, 0.14f, 0.62f)
            }
            Icon.TRASH -> {
                c.drawRect(0.25f * u, 0.38f * u, 0.75f * u, 0.96f * u, p)
                line(c, u, 0.12f, 0.26f, 0.88f, 0.26f)
                poly(c, u, false, 0.38f, 0.26f, 0.38f, 0.1f, 0.62f, 0.1f, 0.62f, 0.26f)
                line(c, u, 0.42f, 0.5f, 0.42f, 0.82f)
                line(c, u, 0.58f, 0.5f, 0.58f, 0.82f)
            }
            Icon.PREV -> poly(c, u, false, 0.7f, 0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
            Icon.NEXT -> poly(c, u, false, 0.3f, 0.1f, 0.7f, 0.5f, 0.3f, 0.9f)
            Icon.TAB -> {
                poly(c, u, true, 0.34f, 0.12f, 0.9f, 0.12f, 0.9f, 0.88f, 0.34f, 0.88f, 0.1f, 0.5f)
                c.drawCircle(0.42f * u, 0.5f * u, 0.07f * u, p)
            }
            Icon.TEXT -> {
                line(c, u, 0.14f, 0.2f, 0.86f, 0.2f)
                line(c, u, 0.5f, 0.2f, 0.5f, 0.86f)
                line(c, u, 0.36f, 0.86f, 0.64f, 0.86f)
                line(c, u, 0.14f, 0.2f, 0.14f, 0.34f)
                line(c, u, 0.86f, 0.2f, 0.86f, 0.34f)
            }
            Icon.LASSO -> {
                p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(0.1f * u, 0.08f * u), 0f)
                rect.set(0.12f * u, 0.16f * u, 0.88f * u, 0.7f * u)
                c.drawOval(rect, p)
                p.pathEffect = null
                line(c, u, 0.4f, 0.68f, 0.3f, 0.9f)
                c.drawCircle(0.3f * u, 0.9f * u, 0.04f * u, p)
            }
            Icon.SELECT -> {
                // nesne seçme: kesik çizgili çerçeve ve içinde ok imleci
                p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(0.1f * u, 0.08f * u), 0f)
                rect.set(0.12f * u, 0.14f * u, 0.8f * u, 0.76f * u)
                c.drawRoundRect(rect, 0.05f * u, 0.05f * u, p)
                p.pathEffect = null
                poly(c, u, true, 0.42f, 0.4f, 0.42f, 0.88f, 0.54f, 0.76f, 0.64f, 0.94f, 0.72f, 0.9f, 0.62f, 0.72f, 0.78f, 0.7f)
            }
            Icon.LASER -> {
                line(c, u, 0.2f, 0.8f, 0.55f, 0.45f)
                line(c, u, 0.55f, 0.1f, 0.55f, 0.28f)
                line(c, u, 0.8f, 0.45f, 0.9f, 0.45f)
                line(c, u, 0.72f, 0.22f, 0.84f, 0.1f)
                line(c, u, 0.38f, 0.2f, 0.3f, 0.12f)
                c.drawCircle(0.62f * u, 0.38f * u, 0.07f * u, p)
            }
            Icon.STICKER -> {
                poly(c, u, true, 0.1f, 0.1f, 0.9f, 0.1f, 0.9f, 0.62f, 0.62f, 0.9f, 0.1f, 0.9f)
                poly(c, u, false, 0.9f, 0.62f, 0.62f, 0.62f, 0.62f, 0.9f)
                c.drawCircle(0.34f * u, 0.4f * u, 0.05f * u, p)
                c.drawCircle(0.62f * u, 0.4f * u, 0.05f * u, p)
                rect.set(0.28f * u, 0.4f * u, 0.68f * u, 0.7f * u)
                c.drawArc(rect, 20f, 140f, false, p)
            }
            Icon.TAPE -> {
                // bant makarası: dış halka, orta delik ve makaradan uzanan bant şeridi
                c.drawCircle(0.38f * u, 0.46f * u, 0.3f * u, p)
                c.drawCircle(0.38f * u, 0.46f * u, 0.1f * u, p)
                c.drawLine(0.38f * u, 0.76f * u, 0.92f * u, 0.76f * u, p)
                c.drawLine(0.92f * u, 0.76f * u, 0.92f * u, 0.58f * u, p)
                c.drawLine(0.6f * u, 0.58f * u, 0.92f * u, 0.58f * u, p)
            }
            Icon.HAND -> {
                // açık el: dört parmak ve baş parmak
                val fx = floatArrayOf(0.30f, 0.42f, 0.54f, 0.66f)
                val ft = floatArrayOf(0.26f, 0.14f, 0.12f, 0.20f)
                for (k in 0..3) c.drawLine(fx[k] * u, 0.52f * u, fx[k] * u, ft[k] * u, p)
                c.drawLine(0.30f * u, 0.52f * u, 0.18f * u, 0.40f * u, p)
                c.drawLine(0.66f * u, 0.52f * u, 0.78f * u, 0.40f * u, p)
                rect.set(0.26f * u, 0.46f * u, 0.70f * u, 0.88f * u)
                c.drawRoundRect(rect, 0.16f * u, 0.16f * u, p)
            }
            Icon.MIC -> {
                rect.set(0.38f * u, 0.12f * u, 0.62f * u, 0.56f * u)
                c.drawRoundRect(rect, 0.12f * u, 0.12f * u, p)
                rect.set(0.26f * u, 0.32f * u, 0.74f * u, 0.72f * u)
                c.drawArc(rect, 0f, 180f, false, p)
                c.drawLine(0.5f * u, 0.72f * u, 0.5f * u, 0.88f * u, p)
                c.drawLine(0.36f * u, 0.88f * u, 0.64f * u, 0.88f * u, p)
            }
            Icon.IMAGE -> {
                rect.set(0.12f * u, 0.2f * u, 0.88f * u, 0.8f * u)
                c.drawRoundRect(rect, 0.06f * u, 0.06f * u, p)
                c.drawCircle(0.32f * u, 0.38f * u, 0.07f * u, p)
                c.drawLine(0.14f * u, 0.74f * u, 0.4f * u, 0.5f * u, p)
                c.drawLine(0.4f * u, 0.5f * u, 0.56f * u, 0.64f * u, p)
                c.drawLine(0.56f * u, 0.64f * u, 0.66f * u, 0.54f * u, p)
                c.drawLine(0.66f * u, 0.54f * u, 0.86f * u, 0.74f * u, p)
            }
            Icon.RULER -> {
                rect.set(0.06f * u, 0.3f * u, 0.94f * u, 0.7f * u)
                c.drawRoundRect(rect, 0.05f * u, 0.05f * u, p)
                for (k in 1..6) {
                    val x = (0.06f + 0.88f * k / 7f) * u
                    c.drawLine(x, 0.3f * u, x, (if (k % 2 == 0) 0.52f else 0.44f) * u, p)
                }
            }
            Icon.CALC -> {
                rect.set(0.2f * u, 0.08f * u, 0.8f * u, 0.92f * u)
                c.drawRoundRect(rect, 0.08f * u, 0.08f * u, p)
                rect.set(0.3f * u, 0.18f * u, 0.7f * u, 0.36f * u)
                c.drawRect(rect, p)
                p.style = Paint.Style.FILL
                for (r in 0..1) for (q in 0..2) c.drawCircle((0.34f + 0.16f * q) * u, (0.52f + 0.17f * r) * u, 0.04f * u, p)
                p.style = Paint.Style.STROKE
            }
            Icon.TIMER -> {
                c.drawCircle(0.5f * u, 0.56f * u, 0.34f * u, p)
                c.drawLine(0.5f * u, 0.56f * u, 0.5f * u, 0.34f * u, p)
                c.drawLine(0.5f * u, 0.56f * u, 0.64f * u, 0.64f * u, p)
                c.drawLine(0.4f * u, 0.12f * u, 0.6f * u, 0.12f * u, p)
                c.drawLine(0.5f * u, 0.12f * u, 0.5f * u, 0.22f * u, p)
            }
            Icon.SPREAD -> {
                rect.set(0.04f * u, 0.14f * u, 0.48f * u, 0.86f * u)
                c.drawRoundRect(rect, 0.06f * u, 0.06f * u, p)
                rect.set(0.52f * u, 0.14f * u, 0.96f * u, 0.86f * u)
                c.drawRoundRect(rect, 0.06f * u, 0.06f * u, p)
            }
            Icon.MORE -> {
                p.style = Paint.Style.FILL
                c.drawCircle(0.5f * u, 0.14f * u, 0.08f * u, p)
                c.drawCircle(0.5f * u, 0.5f * u, 0.08f * u, p)
                c.drawCircle(0.5f * u, 0.86f * u, 0.08f * u, p)
                p.style = Paint.Style.STROKE
            }
            Icon.ADD -> {
                line(c, u, 0.5f, 0.1f, 0.5f, 0.9f)
                line(c, u, 0.1f, 0.5f, 0.9f, 0.5f)
            }
            Icon.PAGES -> {
                c.drawRect(0.1f * u, 0.24f * u, 0.64f * u, 0.96f * u, p)
                poly(c, u, false, 0.3f, 0.24f, 0.3f, 0.06f, 0.9f, 0.06f, 0.9f, 0.78f, 0.64f, 0.78f)
            }
            Icon.THICKNESS -> {
                val w0 = p.strokeWidth
                p.strokeWidth = w0 * 0.5f
                line(c, u, 0.1f, 0.2f, 0.9f, 0.2f)
                p.strokeWidth = w0 * 1.3f
                line(c, u, 0.1f, 0.5f, 0.9f, 0.5f)
                p.strokeWidth = w0 * 2.4f
                line(c, u, 0.1f, 0.8f, 0.9f, 0.8f)
                p.strokeWidth = w0
            }
            Icon.SAVE -> {
                poly(c, u, true, 0.1f, 0.1f, 0.8f, 0.1f, 0.9f, 0.2f, 0.9f, 0.9f, 0.1f, 0.9f)
                c.drawRect(0.28f * u, 0.1f * u, 0.7f * u, 0.38f * u, p)
                c.drawRect(0.25f * u, 0.58f * u, 0.75f * u, 0.9f * u, p)
            }
            Icon.EXPORT -> {
                poly(c, u, false, 0.1f, 0.6f, 0.1f, 0.95f, 0.9f, 0.95f, 0.9f, 0.6f)
                line(c, u, 0.5f, 0.72f, 0.5f, 0.08f)
                poly(c, u, false, 0.28f, 0.3f, 0.5f, 0.08f, 0.72f, 0.3f)
            }
            Icon.ARCHIVE -> {
                c.drawRect(0.1f * u, 0.12f * u, 0.9f * u, 0.36f * u, p)
                c.drawRect(0.17f * u, 0.36f * u, 0.83f * u, 0.92f * u, p)
                line(c, u, 0.4f, 0.56f, 0.6f, 0.56f)
            }
            Icon.HOME -> {
                poly(c, u, false, 0.06f, 0.5f, 0.5f, 0.1f, 0.94f, 0.5f)
                poly(c, u, false, 0.2f, 0.42f, 0.2f, 0.92f, 0.8f, 0.92f, 0.8f, 0.42f)
                poly(c, u, false, 0.42f, 0.92f, 0.42f, 0.62f, 0.58f, 0.62f, 0.58f, 0.92f)
            }
            Icon.IMPORT -> {
                poly(c, u, false, 0.1f, 0.6f, 0.1f, 0.95f, 0.9f, 0.95f, 0.9f, 0.6f)
                line(c, u, 0.5f, 0.05f, 0.5f, 0.7f)
                poly(c, u, false, 0.28f, 0.5f, 0.5f, 0.72f, 0.72f, 0.5f)
            }
            Icon.PALETTE -> {
                rect.set(0.05f * u, 0.1f * u, 0.95f * u, 0.9f * u)
                c.drawOval(rect, p)
                c.drawCircle(0.3f * u, 0.4f * u, 0.07f * u, p)
                c.drawCircle(0.5f * u, 0.28f * u, 0.07f * u, p)
                c.drawCircle(0.7f * u, 0.4f * u, 0.07f * u, p)
            }
        }
    }
}

/** Renk tonu çubuğu ile doygunluk/parlaklık karesinden oluşan renk seçici. */
class ColorPickerView(context: Context, initial: Int) : View(context) {
    private val d = resources.displayMetrics.density
    private val hsv = FloatArray(3)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val svRect = RectF()
    private val hueRect = RectF()
    private val prevRect = RectF()
    private var dragSv = true

    val color: Int get() = Color.HSVToColor(hsv)

    init {
        Color.colorToHSV(initial, hsv)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((320 * d).toInt(), (400 * d).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = 12 * d
        svRect.set(pad, pad, w - pad, pad + 260 * d)
        hueRect.set(pad, svRect.bottom + 18 * d, w - pad, svRect.bottom + 18 * d + 36 * d)
        prevRect.set(pad, hueRect.bottom + 18 * d, w - pad, h - pad)
    }

    override fun onDraw(c: Canvas) {
        val hueColor = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(svRect.left, 0f, svRect.right, 0f, Color.WHITE, hueColor, Shader.TileMode.CLAMP)
        c.drawRect(svRect, paint)
        paint.shader = LinearGradient(0f, svRect.top, 0f, svRect.bottom, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        c.drawRect(svRect, paint)

        val hues = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
        paint.shader = LinearGradient(hueRect.left, 0f, hueRect.right, 0f, hues, null, Shader.TileMode.CLAMP)
        c.drawRect(hueRect, paint)
        paint.shader = null

        paint.color = color
        c.drawRect(prevRect, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3 * d
        paint.color = Color.WHITE
        val sx = svRect.left + hsv[1] * svRect.width()
        val sy = svRect.top + (1f - hsv[2]) * svRect.height()
        c.drawCircle(sx, sy, 10 * d, paint)
        paint.strokeWidth = 1 * d
        paint.color = Color.BLACK
        c.drawCircle(sx, sy, 11.5f * d, paint)
        paint.strokeWidth = 4 * d
        paint.color = Color.WHITE
        val hx = hueRect.left + hsv[0] / 360f * hueRect.width()
        c.drawLine(hx, hueRect.top - 4 * d, hx, hueRect.bottom + 4 * d, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            dragSv = e.y < svRect.bottom + 9 * d
        }
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
            if (dragSv) {
                hsv[1] = max(0f, min(1f, (e.x - svRect.left) / svRect.width()))
                hsv[2] = 1f - max(0f, min(1f, (e.y - svRect.top) / svRect.height()))
            } else {
                hsv[0] = max(0f, min(359.9f, (e.x - hueRect.left) / hueRect.width() * 360f))
            }
            invalidate()
        }
        return true
    }
}
