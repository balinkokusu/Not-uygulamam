package com.notuygulamam.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Seçili aracın rengini ve kalınlığını gösteren küçük örnek çizgi. */
class PreviewView(context: Context, private val draw: DrawView) : View(context) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.WHITE)
        val cy = height / 2f
        when (draw.tool) {
            Tool.ERASER -> {
                p.setColor(Color.GRAY)
                p.alpha = 255
                p.strokeWidth = d
                c.drawCircle(width / 2f, cy, draw.eraserRadiusDp * d, p)
            }
            Tool.HIGHLIGHTER -> {
                p.setColor(draw.hlColor)
                p.alpha = 110
                p.strokeWidth = draw.hlWidthDp * d
                p.strokeCap = Paint.Cap.SQUARE
                c.drawLine(12f * d, cy, width - 12f * d, cy, p)
            }
            Tool.PEN, Tool.PICKER -> {
                p.setColor(draw.penColor)
                p.alpha = 255
                p.strokeWidth = draw.penWidthDp * d
                p.strokeCap = Paint.Cap.ROUND
                c.drawLine(12f * d, cy, width - 12f * d, cy, p)
            }
        }
    }
}
