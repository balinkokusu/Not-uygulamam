package com.notuygulamam.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/** Kodla çizilen hazır çıkartmalar. Her biri (-w/2, -h/2) ile (w/2, h/2) arasındaki kutuya sığar. */
object Stickers {
    val names = listOf(
        "Yıldız", "Kalp", "Çiçek", "Papatya", "Güneş", "Bulut", "Şerit bant", "Pembe bant",
        "Başlık şeridi", "Yer imi", "Onay işareti", "Ok", "Ay", "Yaprak", "Kelebek", "Not kâğıdı",
        "Altın yıldız", "Mantar", "Elmas", "Konuşma balonu",
        "Gökkuşağı", "Şimşek", "Kahve", "Kitap", "Kalem", "Ataş", "Raptiye", "Bayrak",
        "Zil", "Hediye", "Ampul", "Takvim", "Saat", "Pasta", "Taç", "Damla"
    )

    /** Çıkartmanın genişlik/yükseklik oranı. */
    fun aspect(kind: Int): Float = when (kind) {
        6, 7 -> 3.2f
        8 -> 2.6f
        9 -> 0.55f
        11 -> 1.8f
        15 -> 1.0f
        19 -> 1.3f
        20 -> 1.6f
        24 -> 0.45f
        else -> 1f
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    private fun f(c: Canvas, color: Int, alpha: Int = 255) {
        fill.setColor(color)
        fill.alpha = alpha
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float, inner: Float, points: Int, rotDeg: Float = -90f) {
        path.reset()
        for (i in 0 until points * 2) {
            val rr = if (i % 2 == 0) r else r * inner
            val a = Math.toRadians((rotDeg + i * 180.0 / points))
            val x = cx + rr * cos(a).toFloat()
            val y = cy + rr * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, fill)
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(cx, cy + s * 0.9f)
        path.cubicTo(cx - s * 1.5f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.1f, cx, cy - s * 0.4f)
        path.cubicTo(cx + s * 0.7f, cy - s * 1.1f, cx + s * 1.5f, cy - s * 0.1f, cx, cy + s * 0.9f)
        path.close()
        c.drawPath(path, fill)
    }

    private fun petals(c: Canvas, n: Int, r: Float, pr: Float, color: Int) {
        f(c, color)
        for (i in 0 until n) {
            val a = Math.toRadians(i * 360.0 / n)
            c.drawCircle(r * cos(a).toFloat(), r * sin(a).toFloat(), pr, fill)
        }
    }

    fun draw(c: Canvas, kind: Int, w: Float, h: Float) {
        val u = minOf(w, h) / 2f
        c.save()
        when (kind) {
            0 -> {
                f(c, Color.rgb(255, 214, 102))
                star(c, 0f, 0f, u, 0.46f, 5)
                line.setColor(Color.rgb(240, 170, 60)); line.strokeWidth = u * 0.06f
                c.drawPath(path, line)
            }
            1 -> {
                f(c, Color.rgb(244, 143, 166))
                heart(c, 0f, 0f, u * 0.9f)
                f(c, Color.WHITE, 120)
                c.drawCircle(-u * 0.38f, -u * 0.38f, u * 0.14f, fill)
            }
            2 -> {
                petals(c, 6, u * 0.5f, u * 0.38f, Color.rgb(250, 190, 212))
                f(c, Color.rgb(255, 224, 130))
                c.drawCircle(0f, 0f, u * 0.3f, fill)
            }
            3 -> {
                petals(c, 10, u * 0.62f, u * 0.2f, Color.WHITE)
                line.setColor(Color.rgb(225, 215, 205)); line.strokeWidth = u * 0.04f
                for (i in 0 until 10) {
                    val a = Math.toRadians(i * 36.0)
                    c.drawCircle(u * 0.62f * cos(a).toFloat(), u * 0.62f * sin(a).toFloat(), u * 0.2f, line)
                }
                f(c, Color.rgb(250, 200, 80))
                c.drawCircle(0f, 0f, u * 0.28f, fill)
            }
            4 -> {
                line.setColor(Color.rgb(255, 196, 80)); line.strokeWidth = u * 0.1f
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0)
                    c.drawLine(u * 0.62f * cos(a).toFloat(), u * 0.62f * sin(a).toFloat(), u * 0.95f * cos(a).toFloat(), u * 0.95f * sin(a).toFloat(), line)
                }
                f(c, Color.rgb(255, 214, 102))
                c.drawCircle(0f, 0f, u * 0.5f, fill)
            }
            5 -> {
                f(c, Color.rgb(205, 226, 246))
                c.drawCircle(-u * 0.4f, u * 0.1f, u * 0.38f, fill)
                c.drawCircle(u * 0.05f, -u * 0.15f, u * 0.48f, fill)
                c.drawCircle(u * 0.45f, u * 0.12f, u * 0.36f, fill)
                rect.set(-u * 0.75f, u * 0.1f, u * 0.8f, u * 0.5f)
                c.drawRoundRect(rect, u * 0.2f, u * 0.2f, fill)
            }
            6, 7 -> {
                val base = if (kind == 6) Color.rgb(196, 226, 210) else Color.rgb(250, 206, 218)
                rect.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
                f(c, base, 215)
                c.drawRect(rect, fill)
                f(c, Color.WHITE, 120)
                var x = -w / 2f + h * 0.3f
                while (x < w / 2f) {
                    c.drawCircle(x, 0f, h * 0.12f, fill)
                    x += h * 0.6f
                }
                f(c, Color.WHITE, 90)
                c.drawRect(-w / 2f, -h / 2f, w / 2f, -h / 2f + h * 0.12f, fill)
                c.drawRect(-w / 2f, h / 2f - h * 0.12f, w / 2f, h / 2f, fill)
            }
            8 -> {
                f(c, Color.rgb(225, 170, 176))
                path.reset()
                path.moveTo(-w / 2f, -h * 0.35f); path.lineTo(w / 2f, -h * 0.35f)
                path.lineTo(w * 0.42f, 0f); path.lineTo(w / 2f, h * 0.35f)
                path.lineTo(-w / 2f, h * 0.35f); path.lineTo(-w * 0.42f, 0f); path.close()
                c.drawPath(path, fill)
                line.setColor(Color.WHITE); line.strokeWidth = h * 0.04f
                line.pathEffect = android.graphics.DashPathEffect(floatArrayOf(h * 0.08f, h * 0.06f), 0f)
                rect.set(-w * 0.38f, -h * 0.26f, w * 0.38f, h * 0.26f)
                c.drawRect(rect, line)
                line.pathEffect = null
            }
            9 -> {
                f(c, Color.rgb(166, 98, 106))
                path.reset()
                path.moveTo(-w / 2f, -h / 2f); path.lineTo(w / 2f, -h / 2f)
                path.lineTo(w / 2f, h / 2f); path.lineTo(0f, h * 0.28f); path.lineTo(-w / 2f, h / 2f); path.close()
                c.drawPath(path, fill)
            }
            10 -> {
                f(c, Color.rgb(196, 226, 200))
                c.drawCircle(0f, 0f, u * 0.95f, fill)
                line.setColor(Color.rgb(60, 130, 80)); line.strokeWidth = u * 0.16f
                path.reset(); path.moveTo(-u * 0.5f, 0f); path.lineTo(-u * 0.12f, u * 0.4f); path.lineTo(u * 0.5f, -u * 0.36f)
                c.drawPath(path, line)
            }
            11 -> {
                line.setColor(Color.rgb(166, 98, 106)); line.strokeWidth = h * 0.09f
                path.reset(); path.moveTo(-w * 0.45f, h * 0.2f); path.quadTo(0f, -h * 0.5f, w * 0.4f, -h * 0.05f)
                c.drawPath(path, line)
                path.reset(); path.moveTo(w * 0.18f, -h * 0.34f); path.lineTo(w * 0.42f, -h * 0.05f); path.lineTo(w * 0.1f, h * 0.1f)
                c.drawPath(path, line)
            }
            12 -> {
                f(c, Color.rgb(252, 224, 150))
                val moon = Path()
                moon.addCircle(0f, 0f, u * 0.9f, Path.Direction.CW)
                val cut = Path()
                cut.addCircle(u * 0.42f, -u * 0.12f, u * 0.78f, Path.Direction.CW)
                moon.op(cut, Path.Op.DIFFERENCE)
                c.drawPath(moon, fill)
            }
            13 -> {
                f(c, Color.rgb(168, 208, 160))
                path.reset()
                path.moveTo(-u * 0.8f, u * 0.8f)
                path.quadTo(-u * 0.9f, -u * 0.7f, u * 0.8f, -u * 0.8f)
                path.quadTo(u * 0.9f, u * 0.7f, -u * 0.8f, u * 0.8f)
                path.close()
                c.drawPath(path, fill)
                line.setColor(Color.rgb(110, 160, 110)); line.strokeWidth = u * 0.06f
                c.drawLine(-u * 0.8f, u * 0.8f, u * 0.45f, -u * 0.45f, line)
            }
            14 -> {
                f(c, Color.rgb(196, 170, 236))
                path.reset()
                path.moveTo(0f, 0f); path.cubicTo(-u * 1.2f, -u * 1.1f, -u * 1.1f, u * 0.2f, 0f, 0f)
                path.cubicTo(-u * 1.0f, u * 1.0f, -u * 0.4f, u * 0.9f, 0f, 0f)
                path.moveTo(0f, 0f); path.cubicTo(u * 1.2f, -u * 1.1f, u * 1.1f, u * 0.2f, 0f, 0f)
                path.cubicTo(u * 1.0f, u * 1.0f, u * 0.4f, u * 0.9f, 0f, 0f)
                c.drawPath(path, fill)
                line.setColor(Color.rgb(90, 70, 110)); line.strokeWidth = u * 0.08f
                c.drawLine(0f, -u * 0.35f, 0f, u * 0.4f, line)
            }
            15 -> {
                f(c, Color.rgb(255, 238, 160))
                c.drawRoundRect(-u * 0.9f, -u * 0.9f, u * 0.9f, u * 0.9f, u * 0.06f, u * 0.06f, fill)
                f(c, Color.argb(40, 0, 0, 0))
                c.drawRect(-u * 0.9f, -u * 0.9f, u * 0.9f, -u * 0.62f, fill)
                line.setColor(Color.argb(90, 120, 100, 40)); line.strokeWidth = u * 0.04f
                for (i in 0 until 4) c.drawLine(-u * 0.65f, -u * 0.25f + i * u * 0.35f, u * 0.65f, -u * 0.25f + i * u * 0.35f, line)
            }
            16 -> {
                f(c, Color.rgb(226, 180, 90))
                star(c, 0f, 0f, u, 0.5f, 8)
                f(c, Color.rgb(250, 226, 150))
                star(c, 0f, 0f, u * 0.62f, 0.5f, 8, -67.5f)
            }
            17 -> {
                f(c, Color.rgb(236, 196, 176))
                rect.set(-u * 0.22f, 0f, u * 0.22f, u * 0.9f)
                c.drawRoundRect(rect, u * 0.1f, u * 0.1f, fill)
                f(c, Color.rgb(226, 120, 120))
                rect.set(-u * 0.95f, -u * 0.9f, u * 0.95f, u * 0.25f)
                c.drawArc(rect, 180f, 180f, true, fill)
                rect.set(-u * 0.95f, -u * 0.35f, u * 0.95f, u * 0.25f)
                f(c, Color.WHITE, 230)
                c.drawCircle(-u * 0.4f, -u * 0.35f, u * 0.14f, fill)
                c.drawCircle(u * 0.35f, -u * 0.5f, u * 0.12f, fill)
                c.drawCircle(u * 0.05f, -u * 0.12f, u * 0.1f, fill)
            }
            18 -> {
                f(c, Color.rgb(176, 214, 236))
                path.reset()
                path.moveTo(0f, -u * 0.9f); path.lineTo(u * 0.8f, -u * 0.2f); path.lineTo(0f, u * 0.9f); path.lineTo(-u * 0.8f, -u * 0.2f); path.close()
                c.drawPath(path, fill)
                f(c, Color.WHITE, 140)
                path.reset(); path.moveTo(0f, -u * 0.9f); path.lineTo(u * 0.3f, -u * 0.2f); path.lineTo(-u * 0.3f, -u * 0.2f); path.close()
                c.drawPath(path, fill)
            }
            20 -> {
                val cols = intArrayOf(Color.rgb(240, 130, 130), Color.rgb(250, 190, 120), Color.rgb(250, 232, 130), Color.rgb(160, 214, 160), Color.rgb(140, 190, 240))
                line.strokeWidth = u * 0.17f
                line.strokeCap = Paint.Cap.BUTT
                for ((i, col) in cols.withIndex()) {
                    line.setColor(col)
                    val rr = u * (1.7f - i * 0.17f)
                    rect.set(-rr, -rr * 0.5f + u * 0.5f, rr, rr * 1.5f + u * 0.5f)
                    c.drawArc(rect, 180f, 180f, false, line)
                }
                line.strokeCap = Paint.Cap.ROUND
            }
            21 -> {
                f(c, Color.rgb(255, 214, 90))
                path.reset()
                path.moveTo(u * 0.25f, -u * 0.95f); path.lineTo(-u * 0.6f, u * 0.1f); path.lineTo(-u * 0.05f, u * 0.1f)
                path.lineTo(-u * 0.3f, u * 0.95f); path.lineTo(u * 0.6f, -u * 0.18f); path.lineTo(u * 0.05f, -u * 0.18f); path.close()
                c.drawPath(path, fill)
            }
            22 -> {
                f(c, Color.rgb(250, 244, 236))
                rect.set(-u * 0.7f, -u * 0.3f, u * 0.5f, u * 0.8f)
                c.drawRoundRect(rect, u * 0.12f, u * 0.12f, fill)
                line.setColor(Color.rgb(166, 98, 106)); line.strokeWidth = u * 0.12f
                rect.set(u * 0.2f, -u * 0.1f, u * 0.85f, u * 0.5f)
                c.drawArc(rect, -90f, 180f, false, line)
                f(c, Color.rgb(110, 76, 60))
                rect.set(-u * 0.62f, -u * 0.3f, u * 0.42f, -u * 0.08f)
                c.drawOval(rect, fill)
                line.setColor(Color.argb(150, 160, 160, 170)); line.strokeWidth = u * 0.07f
                path.reset(); path.moveTo(-u * 0.3f, -u * 0.5f); path.quadTo(-u * 0.1f, -u * 0.7f, -u * 0.3f, -u * 0.9f)
                c.drawPath(path, line)
            }
            23 -> {
                f(c, Color.rgb(176, 120, 130))
                rect.set(-u * 0.85f, -u * 0.8f, u * 0.85f, u * 0.8f)
                c.drawRoundRect(rect, u * 0.08f, u * 0.08f, fill)
                f(c, Color.rgb(250, 240, 226))
                rect.set(-u * 0.75f, -u * 0.7f, u * 0.8f, u * 0.7f)
                c.drawRoundRect(rect, u * 0.05f, u * 0.05f, fill)
                line.setColor(Color.rgb(176, 120, 130)); line.strokeWidth = u * 0.06f
                for (i in 0 until 4) c.drawLine(-u * 0.5f, -u * 0.3f + i * u * 0.28f, u * 0.5f, -u * 0.3f + i * u * 0.28f, line)
            }
            24 -> {
                c.rotate(0f)
                f(c, Color.rgb(250, 206, 96))
                rect.set(-w / 2f, -h * 0.34f, w / 2f, h * 0.3f)
                c.drawRect(rect, fill)
                f(c, Color.rgb(240, 150, 150))
                c.drawRect(-w / 2f, -h * 0.46f, w / 2f, -h * 0.34f, fill)
                f(c, Color.rgb(246, 220, 190))
                path.reset(); path.moveTo(-w / 2f, h * 0.3f); path.lineTo(0f, h / 2f); path.lineTo(w / 2f, h * 0.3f); path.close()
                c.drawPath(path, fill)
                f(c, Color.rgb(70, 60, 64))
                path.reset(); path.moveTo(-w * 0.14f, h * 0.4f); path.lineTo(0f, h / 2f); path.lineTo(w * 0.14f, h * 0.4f); path.close()
                c.drawPath(path, fill)
            }
            25 -> {
                line.setColor(Color.rgb(150, 150, 165)); line.strokeWidth = u * 0.1f
                path.reset()
                path.moveTo(u * 0.2f, u * 0.2f); path.lineTo(u * 0.2f, -u * 0.55f)
                path.arcTo(-u * 0.2f, -u * 0.95f, u * 0.2f, -u * 0.15f, 0f, -180f, false)
                path.lineTo(-u * 0.2f, u * 0.55f)
                path.arcTo(-u * 0.6f, u * 0.15f, u * 0.2f, u * 0.95f, 180f, -180f, false)
                path.lineTo(u * 0.6f, -u * 0.5f)
                c.drawPath(path, line)
            }
            26 -> {
                f(c, Color.rgb(226, 110, 110))
                c.drawCircle(0f, -u * 0.35f, u * 0.5f, fill)
                f(c, Color.rgb(200, 200, 210))
                c.drawRect(-u * 0.05f, u * 0.05f, u * 0.05f, u * 0.9f, fill)
                f(c, Color.WHITE, 130)
                c.drawCircle(-u * 0.18f, -u * 0.52f, u * 0.12f, fill)
            }
            27 -> {
                line.setColor(Color.rgb(120, 100, 100)); line.strokeWidth = u * 0.1f
                c.drawLine(-u * 0.6f, -u * 0.95f, -u * 0.6f, u * 0.95f, line)
                f(c, Color.rgb(236, 120, 140))
                path.reset(); path.moveTo(-u * 0.55f, -u * 0.9f); path.quadTo(0f, -u * 0.5f, u * 0.2f, -u * 0.85f)
                path.quadTo(u * 0.5f, -u * 1.0f, u * 0.85f, -u * 0.8f); path.lineTo(u * 0.85f, u * 0.0f)
                path.quadTo(u * 0.5f, u * 0.2f, u * 0.2f, -u * 0.05f); path.quadTo(0f, -u * 0.3f, -u * 0.55f, u * 0.05f); path.close()
                c.drawPath(path, fill)
            }
            28 -> {
                f(c, Color.rgb(250, 204, 100))
                path.reset(); path.moveTo(-u * 0.8f, u * 0.55f); path.quadTo(-u * 0.7f, u * 0.4f, -u * 0.62f, -u * 0.1f)
                path.cubicTo(-u * 0.6f, -u * 0.9f, u * 0.6f, -u * 0.9f, u * 0.62f, -u * 0.1f)
                path.quadTo(u * 0.7f, u * 0.4f, u * 0.8f, u * 0.55f); path.close()
                c.drawPath(path, fill)
                c.drawCircle(0f, u * 0.75f, u * 0.2f, fill)
            }
            29 -> {
                f(c, Color.rgb(240, 150, 170))
                rect.set(-u * 0.85f, -u * 0.1f, u * 0.85f, u * 0.85f)
                c.drawRoundRect(rect, u * 0.08f, u * 0.08f, fill)
                f(c, Color.rgb(250, 190, 120))
                rect.set(-u * 0.95f, -u * 0.45f, u * 0.95f, -u * 0.05f)
                c.drawRoundRect(rect, u * 0.08f, u * 0.08f, fill)
                f(c, Color.rgb(250, 235, 140))
                c.drawRect(-u * 0.1f, -u * 0.45f, u * 0.1f, u * 0.85f, fill)
                line.setColor(Color.rgb(250, 235, 140)); line.strokeWidth = u * 0.1f
                path.reset(); path.moveTo(0f, -u * 0.45f); path.quadTo(-u * 0.6f, -u * 1.0f, -u * 0.35f, -u * 0.5f)
                c.drawPath(path, line)
                path.reset(); path.moveTo(0f, -u * 0.45f); path.quadTo(u * 0.6f, -u * 1.0f, u * 0.35f, -u * 0.5f)
                c.drawPath(path, line)
            }
            30 -> {
                f(c, Color.rgb(255, 232, 130))
                c.drawCircle(0f, -u * 0.25f, u * 0.55f, fill)
                f(c, Color.rgb(200, 200, 210))
                rect.set(-u * 0.25f, u * 0.3f, u * 0.25f, u * 0.75f)
                c.drawRoundRect(rect, u * 0.06f, u * 0.06f, fill)
                line.setColor(Color.rgb(255, 196, 80)); line.strokeWidth = u * 0.07f
                for (i in 0 until 5) {
                    val a = Math.toRadians(-160.0 + i * 35.0)
                    c.drawLine(u * 0.7f * cos(a).toFloat(), -u * 0.25f + u * 0.7f * sin(a).toFloat(), u * 0.95f * cos(a).toFloat(), -u * 0.25f + u * 0.95f * sin(a).toFloat(), line)
                }
            }
            31 -> {
                f(c, Color.WHITE)
                rect.set(-u * 0.9f, -u * 0.8f, u * 0.9f, u * 0.9f)
                c.drawRoundRect(rect, u * 0.1f, u * 0.1f, fill)
                f(c, Color.rgb(226, 120, 130))
                rect.set(-u * 0.9f, -u * 0.8f, u * 0.9f, -u * 0.35f)
                c.drawRoundRect(rect, u * 0.1f, u * 0.1f, fill)
                line.setColor(Color.rgb(200, 190, 190)); line.strokeWidth = u * 0.04f
                c.drawRoundRect(-u * 0.9f, -u * 0.8f, u * 0.9f, u * 0.9f, u * 0.1f, u * 0.1f, line)
                f(c, Color.rgb(170, 140, 140))
                for (r in 0 until 3) for (q in 0 until 4) c.drawCircle(-u * 0.55f + q * u * 0.37f, -u * 0.05f + r * u * 0.3f, u * 0.07f, fill)
            }
            32 -> {
                f(c, Color.WHITE)
                c.drawCircle(0f, 0f, u * 0.92f, fill)
                line.setColor(Color.rgb(166, 98, 106)); line.strokeWidth = u * 0.1f
                c.drawCircle(0f, 0f, u * 0.92f, line)
                line.strokeWidth = u * 0.08f
                c.drawLine(0f, 0f, 0f, -u * 0.6f, line)
                c.drawLine(0f, 0f, u * 0.42f, u * 0.2f, line)
            }
            33 -> {
                f(c, Color.rgb(250, 226, 220))
                rect.set(-u * 0.9f, -u * 0.1f, u * 0.9f, u * 0.85f)
                c.drawRoundRect(rect, u * 0.1f, u * 0.1f, fill)
                f(c, Color.rgb(246, 170, 190))
                path.reset(); path.moveTo(-u * 0.9f, -u * 0.1f)
                for (i in 0..5) path.quadTo(-u * 0.9f + (i * 2 + 1) * u * 0.15f, u * 0.25f, -u * 0.9f + (i + 1) * u * 0.3f, -u * 0.1f)
                path.lineTo(u * 0.9f, -u * 0.35f); path.lineTo(-u * 0.9f, -u * 0.35f); path.close()
                c.drawPath(path, fill)
                f(c, Color.rgb(226, 80, 100))
                c.drawCircle(0f, -u * 0.5f, u * 0.17f, fill)
                line.setColor(Color.rgb(255, 214, 102)); line.strokeWidth = u * 0.07f
                c.drawLine(0f, -u * 0.65f, 0f, -u * 0.95f, line)
            }
            34 -> {
                f(c, Color.rgb(250, 206, 96))
                path.reset(); path.moveTo(-u * 0.85f, u * 0.6f); path.lineTo(-u * 0.9f, -u * 0.5f); path.lineTo(-u * 0.4f, -u * 0.05f)
                path.lineTo(0f, -u * 0.8f); path.lineTo(u * 0.4f, -u * 0.05f); path.lineTo(u * 0.9f, -u * 0.5f); path.lineTo(u * 0.85f, u * 0.6f); path.close()
                c.drawPath(path, fill)
                f(c, Color.rgb(226, 100, 110))
                c.drawCircle(0f, u * 0.1f, u * 0.11f, fill)
                f(c, Color.rgb(100, 170, 220))
                c.drawCircle(-u * 0.5f, u * 0.28f, u * 0.08f, fill)
                c.drawCircle(u * 0.5f, u * 0.28f, u * 0.08f, fill)
            }
            35 -> {
                f(c, Color.rgb(140, 196, 240))
                path.reset(); path.moveTo(0f, -u * 0.95f)
                path.cubicTo(u * 0.2f, -u * 0.5f, u * 0.75f, u * 0.0f, u * 0.5f, u * 0.5f)
                path.cubicTo(u * 0.35f, u * 0.9f, -u * 0.35f, u * 0.9f, -u * 0.5f, u * 0.5f)
                path.cubicTo(-u * 0.75f, u * 0.0f, -u * 0.2f, -u * 0.5f, 0f, -u * 0.95f); path.close()
                c.drawPath(path, fill)
                f(c, Color.WHITE, 140)
                c.drawCircle(-u * 0.18f, u * 0.3f, u * 0.12f, fill)
            }
            else -> {
                f(c, Color.rgb(246, 218, 224))
                rect.set(-w / 2f, -h / 2f, w / 2f, h * 0.28f)
                c.drawRoundRect(rect, h * 0.2f, h * 0.2f, fill)
                path.reset(); path.moveTo(-w * 0.2f, h * 0.2f); path.lineTo(-w * 0.32f, h / 2f); path.lineTo(w * 0.05f, h * 0.2f); path.close()
                c.drawPath(path, fill)
            }
        }
        c.restore()
    }
}
