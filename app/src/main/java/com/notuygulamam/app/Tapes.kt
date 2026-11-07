package com.notuygulamam.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/** Washi bant koleksiyonu: desenler koddan üretilir, her kategoride altı renk vardır. */
object Tapes {
    const val W = 720
    const val H = 110

    val categories = listOf("Pastel", "Çizgili", "Noktalı", "Kareli", "Çiçekli", "Petek", "Yıldız", "Kalp", "Yaprak", "Folyo")

    private val pastel = intArrayOf(0xF6C1CC, 0xFAD7B5, 0xFBEFA6, 0xC5E8D0, 0xBFDDF3, 0xD9C8EE)
    private val honey = intArrayOf(0xF6C445, 0xF2A93B, 0xFADF8A, 0xE8B14A, 0xFFD966, 0xD99A2B)
    private val greens = intArrayOf(0x9DB86B, 0x7FA35A, 0xBFD8A0, 0x6E9A62, 0xA8C77F, 0x8DB08A)
    private val foil = intArrayOf(0xE7C15A, 0xCFCFD6, 0xF0B7A4, 0xC98B5A, 0xEBDDB5, 0xB58A4B)

    private fun pal(cat: Int): IntArray = when (categories[cat]) {
        "Petek" -> honey
        "Yaprak" -> greens
        "Folyo" -> foil
        else -> pastel
    }

    private fun rgb(v: Int) = Color.rgb((v shr 16) and 255, (v shr 8) and 255, v and 255)
    private fun darker(c: Int, k: Float) = Color.rgb((Color.red(c) * k).toInt(), (Color.green(c) * k).toInt(), (Color.blue(c) * k).toInt())

    /** Kategori ve renk numarasına göre bant görseli; gizleme bandı neredeyse opak, süs bandı yarı saydamdır. */
    fun make(cat: Int, variant: Int, hide: Boolean): Bitmap {
        val base = rgb(pal(cat)[variant % 6])
        val tmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(tmp)
        // yırtık uçlu şerit
        val shape = Path()
        shape.moveTo(0f, 0f)
        var y = 0f
        var k = 0
        while (y < H) { shape.lineTo(if (k % 2 == 0) 0f else 9f, y); y += 11f; k++ }
        shape.lineTo(0f, H.toFloat())
        shape.lineTo(W.toFloat(), H.toFloat())
        y = H.toFloat()
        k = 0
        while (y > 0) { shape.lineTo(if (k % 2 == 0) W.toFloat() else W - 9f, y); y -= 11f; k++ }
        shape.lineTo(W.toFloat(), 0f)
        shape.close()
        c.save()
        c.clipPath(shape)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = base
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        val acc = darker(base, 0.78f)
        val white = Color.argb(170, 255, 255, 255)
        when (categories[cat]) {
            "Pastel" -> {
                p.color = Color.argb(120, 255, 255, 255)
                c.drawRect(0f, 10f, W.toFloat(), 18f, p)
                c.drawRect(0f, H - 18f, W.toFloat(), H - 10f, p)
            }
            "Çizgili" -> {
                p.color = white
                p.strokeWidth = 10f
                var x = -H.toFloat()
                while (x < W) { c.drawLine(x, H.toFloat(), x + H, 0f, p); x += 30f }
            }
            "Noktalı" -> {
                p.color = white
                var row = 0
                var yy = 18f
                while (yy < H) {
                    var xx = if (row % 2 == 0) 18f else 36f
                    while (xx < W) { c.drawCircle(xx, yy, 7f, p); xx += 36f }
                    yy += 28f; row++
                }
            }
            "Kareli" -> {
                p.color = Color.argb(120, 255, 255, 255)
                var x = 0f
                while (x < W) { c.drawRect(x, 0f, x + 18f, H.toFloat(), p); x += 36f }
                var yy = 0f
                while (yy < H) { c.drawRect(0f, yy, W.toFloat(), yy + 18f, p); yy += 36f }
            }
            "Çiçekli" -> {
                var x = 40f
                var row = 0
                while (x < W + 40) {
                    val cy = if (row % 2 == 0) 34f else 78f
                    p.color = Color.argb(235, 255, 255, 255)
                    for (a in 0 until 5) {
                        val ang = Math.toRadians(a * 72.0 - 90.0)
                        c.drawCircle(x + 11f * cos(ang).toFloat(), cy + 11f * sin(ang).toFloat(), 8f, p)
                    }
                    p.color = acc
                    c.drawCircle(x, cy, 5f, p)
                    x += 60f; row++
                }
            }
            "Petek" -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 4f
                p.color = Color.argb(150, 120, 70, 0)
                val r = 26f
                var col = 0
                var x = 0f
                while (x < W + r) {
                    var yy = if (col % 2 == 0) 0f else r * 0.87f
                    while (yy < H + r) {
                        val hx = Path()
                        for (i in 0..5) {
                            val a = Math.toRadians(60.0 * i)
                            val px = x + r * cos(a).toFloat()
                            val py = yy + r * sin(a).toFloat()
                            if (i == 0) hx.moveTo(px, py) else hx.lineTo(px, py)
                        }
                        hx.close()
                        c.drawPath(hx, p)
                        yy += r * 1.74f
                    }
                    x += r * 1.5f; col++
                }
                p.style = Paint.Style.FILL
            }
            "Yıldız" -> {
                var x = 34f
                var row = 0
                while (x < W + 30) {
                    val cy = if (row % 2 == 0) 36f else 76f
                    p.color = Color.argb(235, 255, 255, 255)
                    c.drawPath(star(x, cy, 14f), p)
                    x += 52f; row++
                }
            }
            "Kalp" -> {
                var x = 34f
                var row = 0
                while (x < W + 30) {
                    val cy = if (row % 2 == 0) 36f else 76f
                    p.color = Color.argb(235, 255, 255, 255)
                    c.drawPath(heart(x, cy, 13f), p)
                    x += 52f; row++
                }
            }
            "Yaprak" -> {
                var x = 30f
                var row = 0
                while (x < W + 30) {
                    val cy = if (row % 2 == 0) 34f else 76f
                    p.color = Color.argb(215, 255, 255, 255)
                    c.save()
                    c.rotate(if (row % 2 == 0) -35f else 35f, x, cy)
                    c.drawOval(RectF(x - 8f, cy - 18f, x + 8f, cy + 18f), p)
                    c.restore()
                    x += 44f; row++
                }
            }
            "Folyo" -> {
                val g = android.graphics.LinearGradient(0f, 0f, 0f, H.toFloat(),
                    intArrayOf(Color.argb(90, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(70, 0, 0, 0)), null, android.graphics.Shader.TileMode.CLAMP)
                p.shader = g
                c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
                p.shader = null
                p.color = Color.argb(140, 255, 255, 255)
                p.strokeWidth = 4f
                var x = 20f
                while (x < W) { c.drawLine(x, H.toFloat(), x + 60f, 0f, p); x += 140f }
            }
        }
        // kenar inceliği
        p.color = Color.argb(40, 0, 0, 0)
        c.drawRect(0f, 0f, W.toFloat(), 3f, p)
        c.drawRect(0f, H - 3f, W.toFloat(), H.toFloat(), p)
        c.restore()
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        val ap = Paint(Paint.FILTER_BITMAP_FLAG)
        ap.alpha = if (hide) 255 else 222
        oc.drawBitmap(tmp, 0f, 0f, ap)
        tmp.recycle()
        return out
    }

    private fun star(cx: Float, cy: Float, r: Float): Path {
        val path = Path()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.45f
            val a = Math.toRadians(i * 36.0 - 90.0)
            val x = cx + rr * cos(a).toFloat()
            val y = cy + rr * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun heart(cx: Float, cy: Float, r: Float): Path {
        val path = Path()
        path.moveTo(cx, cy + r)
        path.cubicTo(cx - 2f * r, cy - 0.2f * r, cx - 0.8f * r, cy - 1.4f * r, cx, cy - 0.4f * r)
        path.cubicTo(cx + 0.8f * r, cy - 1.4f * r, cx + 2f * r, cy - 0.2f * r, cx, cy + r)
        path.close()
        return path
    }
}
