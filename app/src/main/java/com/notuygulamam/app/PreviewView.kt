package com.notuygulamam.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.sin

/** Seçili aracın rengini, kalınlığını ve türünü gösteren küçük örnek çizgi. */
class PreviewView(context: Context, private val draw: DrawView) : View(context) {
    private val d = resources.displayMetrics.density
    private val renderer = PageRenderer()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.WHITE)
        val cy = height / 2f
        if (draw.tool == Tool.ERASER) {
            p.setColor(Color.GRAY)
            p.alpha = 255
            p.strokeWidth = d
            c.drawCircle(width / 2f, cy, draw.eraserRadiusDp * d, p)
            return
        }
        val hl = draw.tool == Tool.HIGHLIGHTER
        val sample = sampleStroke(
            hl, if (hl) draw.hlColor else draw.penColor,
            (if (hl) draw.hlWidthDp else draw.penWidthDp) * DrawView.UNIT,
            if (hl) draw.hlKind else draw.penKind, draw.pressureSensitivity / 100f,
            (width - 24f * d) / (d / DrawView.UNIT)
        )
        c.save()
        c.translate(12f * d, cy)
        c.scale(d / DrawView.UNIT, d / DrawView.UNIT)
        renderer.drawStroke(c, sample)
        c.restore()
    }

    companion object {
        /** Kalem türüne göre basınç duyarlılığı; 0 basınçtan bağımsız, 1 en duyarlı. */
        fun penSens(kind: Int, ps: Float): Float = when (kind) {
            1 -> 0.04f
            2 -> 0.35f
            3 -> 1f
            4 -> 0.3f
            5 -> 0.08f
            6, 7, 8, 9 -> 0.04f
            else -> ps * 0.55f
        }

        /** Örnek bir dalga çizgisi; basınç ortada artar ki kalem türlerinin farkı görülsün. */
        fun sampleStroke(hl: Boolean, color: Int, width: Float, kind: Int, ps: Float, length: Float): Stroke {
            val sens = if (hl) 0f else penSens(kind, ps)
            val s = Stroke(color, width, hl, sens, kind)
            val n = 150
            for (i in 0..n) {
                val t = i / n.toFloat()
                val y = (sin(t * 6.28f) * 20f)
                val pr = 0.2f + 0.7f * sin(t * 3.14f)
                s.add(t * length, y, if (hl) 0.5f else pr)
            }
            return s
        }
    }
}
