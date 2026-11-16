package com.notuygulamam.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Konularına göre gruplanmış ek kapaklar (13 ve sonrası). Hepsi kodla çizilir; çağıran taraf kırpmayı (clip) yapmıştır. */
object CoverExtra {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rr = RectF()

    private fun a(c: Int, alpha: Int) = Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))

    private fun fill(c: Canvas, color: Int, vararg v: Float) {
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = color
        path.reset()
        path.moveTo(v[0], v[1])
        var i = 2
        while (i + 1 < v.size) { path.lineTo(v[i], v[i + 1]); i += 2 }
        path.close()
        c.drawPath(path, p)
    }

    private fun dot(c: Canvas, color: Int, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = color
        c.drawCircle(x, y, r, p)
    }

    private fun bg(c: Canvas, r: RectF, top: Int, bottom: Int) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, r.top, 0f, r.bottom, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        p.shader = null
    }

    private fun stroke(color: Int, w: Float) {
        p.style = Paint.Style.STROKE
        p.shader = null
        p.color = color
        p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
    }

    fun draw(c: Canvas, r: RectF, style: Int, color: Int) {
        val w = r.width()
        val h = r.height()
        val l = r.left
        val t = r.top
        val cx = r.centerX()
        val cy = r.centerY()
        val light = CoverArt.lighten(color, 0.5f)
        val pale = CoverArt.lighten(color, 0.85f)
        val dark = CoverArt.darken(color, 0.3f)
        val deep = CoverArt.darken(color, 0.55f)
        val rnd = java.util.Random(style * 31L + 5)
        when (style) {
            // ---------------- Geometrik ----------------
            13 -> { // üçgenler
                bg(c, r, color, color)
                val n = 5
                val s = w / n
                val shades = intArrayOf(light, color, dark, pale, CoverArt.darken(color, 0.15f))
                var row = 0
                var y = t
                while (y < r.bottom) {
                    for (i in -1..n) {
                        val x = l + i * s + (if (row % 2 == 0) 0f else s / 2f)
                        fill(c, shades[rnd.nextInt(shades.size)], x, y + s * 0.87f, x + s / 2f, y, x + s, y + s * 0.87f)
                        fill(c, shades[rnd.nextInt(shades.size)], x + s / 2f, y, x + s * 1.5f, y, x + s, y + s * 0.87f)
                    }
                    y += s * 0.87f
                    row++
                }
            }
            14 -> { // daireler
                bg(c, r, pale, pale)
                val cols = intArrayOf(color, light, dark, Color.rgb(240, 190, 120), Color.rgb(140, 190, 200))
                for (i in 0 until 16) {
                    dot(c, a(cols[i % cols.size], 150), l + rnd.nextFloat() * w, t + rnd.nextFloat() * h, w * (0.08f + rnd.nextFloat() * 0.16f))
                }
            }
            15 -> { // zikzak
                bg(c, r, pale, pale)
                val band = h / 9f
                val zz = w / 6f
                for (k in 0 until 10) {
                    stroke(if (k % 2 == 0) color else light, band * 0.42f)
                    p.strokeCap = Paint.Cap.BUTT
                    path.reset()
                    val y = t + k * band
                    path.moveTo(l - zz, y)
                    var x = l - zz
                    var up = true
                    while (x < r.right + zz) {
                        x += zz / 2f
                        path.lineTo(x, y + (if (up) -band * 0.4f else band * 0.4f))
                        up = !up
                    }
                    c.drawPath(path, p)
                }
            }
            16 -> { // elmas kafes
                bg(c, r, color, dark)
                val s = w / 4f
                var row = 0
                var y = t - s
                while (y < r.bottom + s) {
                    var x = l - s + (if (row % 2 == 0) 0f else s / 2f)
                    while (x < r.right + s) {
                        fill(c, a(light, if ((row + (x / s).toInt()) % 2 == 0) 90 else 40), x, y, x + s * 0.5f, y - s * 0.7f, x + s, y, x + s * 0.5f, y + s * 0.7f)
                        x += s
                    }
                    y += s * 0.7f
                    row++
                }
                stroke(a(pale, 200), w * 0.012f)
                rr.set(l + w * 0.09f, t + w * 0.09f, r.right - w * 0.09f, r.bottom - w * 0.09f)
                c.drawRect(rr, p)
            }
            17 -> { // art deco yelpaze
                bg(c, r, deep, deep)
                val s = w / 3f
                var row = 0
                var y = r.bottom + s * 0.5f
                while (y > t - s) {
                    var x = l + (if (row % 2 == 0) 0f else s / 2f) - s
                    while (x < r.right + s) {
                        for (k in 4 downTo 1) {
                            stroke(if (k % 2 == 0) Color.rgb(214, 178, 110) else light, w * 0.012f)
                            rr.set(x - s * k / 8f, y - s * k / 8f, x + s * k / 8f, y + s * k / 8f)
                            c.drawArc(rr, 180f, 180f, false, p)
                        }
                        x += s
                    }
                    y -= s * 0.5f
                    row++
                }
            }
            // ---------------- Hayvanlar (geometrik) ----------------
            18 -> { // kedi
                bg(c, r, light, color)
                val s = w * 0.3f
                fill(c, deep, cx - s, cy - s * 0.2f, cx - s * 0.95f, cy - s * 1.45f, cx - s * 0.2f, cy - s * 0.75f)
                fill(c, deep, cx + s, cy - s * 0.2f, cx + s * 0.95f, cy - s * 1.45f, cx + s * 0.2f, cy - s * 0.75f)
                fill(c, deep, cx - s, cy - s * 0.3f, cx - s * 0.5f, cy - s * 0.85f, cx + s * 0.5f, cy - s * 0.85f, cx + s, cy - s * 0.3f, cx + s * 0.75f, cy + s * 0.7f, cx, cy + s * 1.05f, cx - s * 0.75f, cy + s * 0.7f)
                fill(c, a(pale, 60), cx, cy - s * 0.85f, cx + s * 0.5f, cy - s * 0.85f, cx + s, cy - s * 0.3f, cx + s * 0.75f, cy + s * 0.7f, cx, cy + s * 1.05f)
                fill(c, Color.rgb(250, 214, 110), cx - s * 0.62f, cy - s * 0.05f, cx - s * 0.36f, cy - s * 0.24f, cx - s * 0.12f, cy - s * 0.02f, cx - s * 0.36f, cy + s * 0.12f)
                fill(c, Color.rgb(250, 214, 110), cx + s * 0.62f, cy - s * 0.05f, cx + s * 0.36f, cy - s * 0.24f, cx + s * 0.12f, cy - s * 0.02f, cx + s * 0.36f, cy + s * 0.12f)
                fill(c, Color.rgb(240, 150, 150), cx - s * 0.12f, cy + s * 0.38f, cx + s * 0.12f, cy + s * 0.38f, cx, cy + s * 0.54f)
                stroke(a(pale, 220), w * 0.008f)
                for (k in -1..1) {
                    c.drawLine(cx - s * 0.3f, cy + s * 0.5f, cx - s * 1.25f, cy + s * (0.5f + k * 0.2f), p)
                    c.drawLine(cx + s * 0.3f, cy + s * 0.5f, cx + s * 1.25f, cy + s * (0.5f + k * 0.2f), p)
                }
            }
            19 -> { // tilki
                bg(c, r, pale, light)
                val s = w * 0.32f
                val or1 = Color.rgb(226, 120, 50)
                val or2 = Color.rgb(196, 92, 36)
                fill(c, or2, cx - s, cy - s * 0.25f, cx - s * 0.9f, cy - s * 1.4f, cx - s * 0.2f, cy - s * 0.55f)
                fill(c, or2, cx + s, cy - s * 0.25f, cx + s * 0.9f, cy - s * 1.4f, cx + s * 0.2f, cy - s * 0.55f)
                fill(c, deep, cx - s * 0.82f, cy - s * 0.55f, cx - s * 0.82f, cy - s * 1.1f, cx - s * 0.45f, cy - s * 0.62f)
                fill(c, deep, cx + s * 0.82f, cy - s * 0.55f, cx + s * 0.82f, cy - s * 1.1f, cx + s * 0.45f, cy - s * 0.62f)
                fill(c, or1, cx - s, cy - s * 0.25f, cx, cy - s * 0.7f, cx, cy + s * 1.05f)
                fill(c, or2, cx + s, cy - s * 0.25f, cx, cy - s * 0.7f, cx, cy + s * 1.05f)
                fill(c, Color.WHITE, cx - s, cy - s * 0.25f, cx - s * 0.3f, cy + s * 0.25f, cx, cy + s * 1.05f)
                fill(c, Color.rgb(240, 236, 230), cx + s, cy - s * 0.25f, cx + s * 0.3f, cy + s * 0.25f, cx, cy + s * 1.05f)
                dot(c, Color.rgb(40, 34, 34), cx - s * 0.38f, cy - s * 0.02f, s * 0.07f)
                dot(c, Color.rgb(40, 34, 34), cx + s * 0.38f, cy - s * 0.02f, s * 0.07f)
                fill(c, Color.rgb(40, 34, 34), cx - s * 0.13f, cy + s * 0.8f, cx + s * 0.13f, cy + s * 0.8f, cx, cy + s * 1.05f)
            }
            20 -> { // baykuş
                bg(c, r, deep, CoverArt.darken(color, 0.75f))
                for (i in 0 until 26) dot(c, a(Color.WHITE, 120 + rnd.nextInt(120)), l + rnd.nextFloat() * w, t + rnd.nextFloat() * h, w * 0.006f)
                val s = w * 0.3f
                fill(c, light, cx - s * 0.8f, cy - s * 0.9f, cx - s * 0.75f, cy - s * 1.45f, cx - s * 0.25f, cy - s * 1.0f)
                fill(c, light, cx + s * 0.8f, cy - s * 0.9f, cx + s * 0.75f, cy - s * 1.45f, cx + s * 0.25f, cy - s * 1.0f)
                p.style = Paint.Style.FILL; p.color = light
                rr.set(cx - s * 0.9f, cy - s * 1.1f, cx + s * 0.9f, cy + s * 1.2f)
                c.drawOval(rr, p)
                p.color = color
                rr.set(cx - s * 0.6f, cy - s * 0.1f, cx + s * 0.6f, cy + s * 1.1f)
                c.drawOval(rr, p)
                stroke(a(pale, 200), w * 0.01f)
                for (k in 0 until 3) for (j in -1..1) {
                    val x = cx + j * s * 0.3f + (if (k % 2 == 0) 0f else s * 0.15f)
                    val y = cy + s * (0.3f + k * 0.24f)
                    path.reset(); path.moveTo(x - s * 0.1f, y); path.lineTo(x, y + s * 0.1f); path.lineTo(x + s * 0.1f, y)
                    c.drawPath(path, p)
                }
                dot(c, Color.WHITE, cx - s * 0.4f, cy - s * 0.5f, s * 0.36f)
                dot(c, Color.WHITE, cx + s * 0.4f, cy - s * 0.5f, s * 0.36f)
                dot(c, Color.rgb(40, 34, 40), cx - s * 0.4f, cy - s * 0.5f, s * 0.17f)
                dot(c, Color.rgb(40, 34, 40), cx + s * 0.4f, cy - s * 0.5f, s * 0.17f)
                dot(c, Color.WHITE, cx - s * 0.34f, cy - s * 0.56f, s * 0.05f)
                dot(c, Color.WHITE, cx + s * 0.46f, cy - s * 0.56f, s * 0.05f)
                fill(c, Color.rgb(236, 170, 70), cx - s * 0.13f, cy - s * 0.25f, cx + s * 0.13f, cy - s * 0.25f, cx, cy + s * 0.05f)
            }
            21 -> { // kuşlar
                bg(c, r, pale, light)
                dot(c, a(Color.rgb(255, 214, 140), 230), l + w * 0.72f, t + h * 0.24f, w * 0.13f)
                stroke(deep, w * 0.014f)
                for (i in 0 until 9) {
                    val x = l + w * (0.12f + rnd.nextFloat() * 0.76f)
                    val y = t + h * (0.18f + rnd.nextFloat() * 0.6f)
                    val s = w * (0.05f + rnd.nextFloat() * 0.06f)
                    path.reset()
                    path.moveTo(x - s, y - s * 0.25f)
                    path.quadTo(x - s * 0.45f, y - s * 0.75f, x, y)
                    path.quadTo(x + s * 0.45f, y - s * 0.75f, x + s, y - s * 0.25f)
                    c.drawPath(path, p)
                }
            }
            22 -> { // balıklar
                bg(c, r, CoverArt.lighten(color, 0.3f), dark)
                var row = 0
                val s = w / 4.2f
                var y = t + s * 0.4f
                while (y < r.bottom + s) {
                    var x = l + (if (row % 2 == 0) s * 0.5f else 0f)
                    while (x < r.right + s) {
                        val col = if ((row + (x / s).toInt()) % 3 == 0) Color.rgb(250, 190, 110) else pale
                        p.style = Paint.Style.FILL; p.color = a(col, 220)
                        rr.set(x - s * 0.3f, y - s * 0.15f, x + s * 0.22f, y + s * 0.15f)
                        c.drawOval(rr, p)
                        fill(c, a(col, 220), x + s * 0.16f, y, x + s * 0.38f, y - s * 0.15f, x + s * 0.38f, y + s * 0.15f)
                        dot(c, deep, x - s * 0.17f, y - s * 0.03f, s * 0.025f)
                        x += s
                    }
                    y += s * 0.62f
                    row++
                }
            }
            // ---------------- Mevsimler ----------------
            23 -> { // ilkbahar
                bg(c, r, Color.rgb(232, 244, 232), Color.rgb(252, 236, 240))
                stroke(Color.rgb(122, 92, 74), w * 0.018f)
                path.reset(); path.moveTo(l - w * 0.05f, t + h * 0.85f); path.quadTo(l + w * 0.4f, t + h * 0.62f, r.right + w * 0.05f, t + h * 0.2f); c.drawPath(path, p)
                path.reset(); path.moveTo(l + w * 0.38f, t + h * 0.63f); path.quadTo(l + w * 0.45f, t + h * 0.4f, l + w * 0.3f, t + h * 0.22f); c.drawPath(path, p)
                path.reset(); path.moveTo(l + w * 0.62f, t + h * 0.47f); path.quadTo(l + w * 0.8f, t + h * 0.55f, l + w * 0.88f, t + h * 0.72f); c.drawPath(path, p)
                val pts = floatArrayOf(0.12f, 0.8f, 0.3f, 0.7f, 0.5f, 0.57f, 0.72f, 0.41f, 0.9f, 0.27f, 0.42f, 0.42f, 0.33f, 0.27f, 0.3f, 0.2f, 0.74f, 0.56f, 0.86f, 0.7f, 0.2f, 0.72f, 0.6f, 0.5f)
                var i = 0
                while (i < pts.size) {
                    val x = l + w * pts[i]; val y = t + h * pts[i + 1]
                    val rad = w * (0.035f + rnd.nextFloat() * 0.02f)
                    for (k in 0 until 5) {
                        val an = (k * 72f - 90f) * PI.toFloat() / 180f
                        dot(c, Color.rgb(246, 178, 196), x + cos(an) * rad, y + sin(an) * rad, rad * 0.8f)
                    }
                    dot(c, Color.rgb(250, 226, 130), x, y, rad * 0.5f)
                    i += 2
                }
            }
            24 -> { // yaz
                bg(c, r, Color.rgb(255, 226, 150), Color.rgb(255, 170, 120))
                dot(c, Color.rgb(255, 244, 200), cx, t + h * 0.36f, w * 0.2f)
                stroke(a(Color.rgb(255, 244, 200), 200), w * 0.016f)
                for (k in 0 until 12) {
                    val an = k * 30f * PI.toFloat() / 180f
                    c.drawLine(cx + cos(an) * w * 0.25f, t + h * 0.36f + sin(an) * w * 0.25f, cx + cos(an) * w * 0.33f, t + h * 0.36f + sin(an) * w * 0.33f, p)
                }
                val seas = intArrayOf(Color.rgb(90, 180, 200), Color.rgb(60, 150, 185), Color.rgb(40, 120, 165))
                for (k in 0 until 3) {
                    p.style = Paint.Style.FILL; p.color = seas[k]
                    path.reset()
                    val y0 = t + h * (0.64f + k * 0.11f)
                    path.moveTo(l, y0)
                    var x = l
                    val step = w / 4f
                    while (x < r.right) {
                        path.quadTo(x + step * 0.25f, y0 - h * 0.035f, x + step * 0.5f, y0)
                        path.quadTo(x + step * 0.75f, y0 + h * 0.035f, x + step, y0)
                        x += step
                    }
                    path.lineTo(r.right, r.bottom); path.lineTo(l, r.bottom); path.close()
                    c.drawPath(path, p)
                }
            }
            25 -> { // sonbahar
                bg(c, r, Color.rgb(250, 236, 210), Color.rgb(240, 206, 160))
                val cols = intArrayOf(Color.rgb(204, 84, 44), Color.rgb(226, 140, 50), Color.rgb(170, 60, 50), Color.rgb(214, 170, 70), Color.rgb(140, 90, 50))
                for (i in 0 until 22) {
                    val x = l + rnd.nextFloat() * w
                    val y = t + rnd.nextFloat() * h
                    val s = w * (0.06f + rnd.nextFloat() * 0.06f)
                    c.save()
                    c.rotate(rnd.nextFloat() * 360f, x, y)
                    p.style = Paint.Style.FILL; p.color = cols[i % cols.size]
                    path.reset()
                    path.moveTo(x, y - s)
                    path.quadTo(x + s * 0.75f, y - s * 0.2f, x, y + s)
                    path.quadTo(x - s * 0.75f, y - s * 0.2f, x, y - s)
                    c.drawPath(path, p)
                    stroke(a(Color.rgb(90, 50, 30), 150), w * 0.005f)
                    c.drawLine(x, y - s * 0.8f, x, y + s * 1.25f, p)
                    c.restore()
                }
            }
            26 -> { // kış
                bg(c, r, Color.rgb(46, 70, 110), Color.rgb(110, 150, 190))
                for (i in 0 until 40) dot(c, a(Color.WHITE, 90 + rnd.nextInt(120)), l + rnd.nextFloat() * w, t + rnd.nextFloat() * h, w * (0.004f + rnd.nextFloat() * 0.008f))
                for (i in 0 until 7) {
                    val x = l + w * (0.12f + rnd.nextFloat() * 0.76f)
                    val y = t + h * (0.1f + rnd.nextFloat() * 0.8f)
                    val s = w * (0.06f + rnd.nextFloat() * 0.07f)
                    stroke(a(Color.WHITE, 235), s * 0.1f)
                    for (k in 0 until 6) {
                        val an = k * 60f * PI.toFloat() / 180f
                        val ex = x + cos(an) * s; val ey = y + sin(an) * s
                        c.drawLine(x, y, ex, ey, p)
                        val mx = x + cos(an) * s * 0.6f; val my = y + sin(an) * s * 0.6f
                        val a1 = an + 0.7f; val a2 = an - 0.7f
                        c.drawLine(mx, my, mx + cos(a1) * s * 0.3f, my + sin(a1) * s * 0.3f, p)
                        c.drawLine(mx, my, mx + cos(a2) * s * 0.3f, my + sin(a2) * s * 0.3f, p)
                    }
                }
            }
            // ---------------- Manzaralar ----------------
            27 -> { // dağlar
                bg(c, r, pale, CoverArt.lighten(color, 0.6f))
                dot(c, a(Color.WHITE, 220), l + w * 0.7f, t + h * 0.24f, w * 0.1f)
                fill(c, CoverArt.lighten(color, 0.35f), l - w * 0.1f, r.bottom, l + w * 0.3f, t + h * 0.38f, l + w * 0.62f, t + h * 0.7f, l + w * 0.8f, t + h * 0.5f, r.right + w * 0.2f, r.bottom)
                fill(c, Color.WHITE, l + w * 0.3f, t + h * 0.38f, l + w * 0.22f, t + h * 0.5f, l + w * 0.3f, t + h * 0.47f, l + w * 0.37f, t + h * 0.52f, l + w * 0.4f, t + h * 0.48f)
                fill(c, color, l - w * 0.2f, r.bottom, l + w * 0.1f, t + h * 0.62f, l + w * 0.35f, t + h * 0.8f, l + w * 0.6f, t + h * 0.55f, r.right + w * 0.1f, r.bottom)
                fill(c, dark, l - w * 0.1f, r.bottom, l + w * 0.25f, t + h * 0.82f, l + w * 0.55f, t + h * 0.9f, l + w * 0.85f, t + h * 0.74f, r.right + w * 0.1f, t + h * 0.84f, r.right + w * 0.1f, r.bottom)
            }
            28 -> { // orman
                bg(c, r, Color.rgb(226, 238, 226), Color.rgb(186, 214, 196))
                dot(c, a(Color.rgb(255, 240, 200), 220), l + w * 0.28f, t + h * 0.2f, w * 0.09f)
                val greens = intArrayOf(Color.rgb(150, 190, 160), Color.rgb(96, 150, 118), Color.rgb(52, 110, 84), Color.rgb(30, 78, 60))
                for (layer in 0 until 4) {
                    val base = t + h * (0.62f + layer * 0.13f)
                    val n = 5 + layer
                    for (i in 0..n) {
                        val x = l + w * (i.toFloat() / n) + (rnd.nextFloat() - 0.5f) * w * 0.08f
                        val th = h * (0.2f + rnd.nextFloat() * 0.12f + layer * 0.02f)
                        val tw = th * 0.42f
                        for (k in 0 until 3) {
                            val yb = base - th * (k * 0.28f)
                            val ww = tw * (1f - k * 0.25f)
                            fill(c, greens[layer], x - ww, yb, x, yb - th * 0.46f, x + ww, yb)
                        }
                    }
                    p.style = Paint.Style.FILL; p.color = greens[layer]
                    c.drawRect(l, base - 1f, r.right, r.bottom, p)
                }
            }
            29 -> { // deniz
                bg(c, r, Color.rgb(255, 214, 170), Color.rgb(250, 160, 130))
                dot(c, Color.rgb(255, 240, 210), cx, t + h * 0.52f, w * 0.17f)
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(0f, t + h * 0.55f, 0f, r.bottom, Color.rgb(70, 140, 180), Color.rgb(28, 70, 120), Shader.TileMode.CLAMP)
                c.drawRect(l, t + h * 0.55f, r.right, r.bottom, p)
                p.shader = null
                stroke(a(Color.rgb(255, 236, 200), 170), w * 0.012f)
                for (k in 0 until 7) {
                    val y = t + h * (0.6f + k * 0.055f)
                    val half = w * (0.06f + k * 0.035f)
                    c.drawLine(cx - half, y, cx + half, y, p)
                }
                fill(c, Color.WHITE, l + w * 0.2f, t + h * 0.7f, l + w * 0.2f, t + h * 0.6f, l + w * 0.29f, t + h * 0.7f)
                fill(c, Color.rgb(60, 50, 60), l + w * 0.13f, t + h * 0.71f, l + w * 0.31f, t + h * 0.71f, l + w * 0.27f, t + h * 0.735f, l + w * 0.16f, t + h * 0.735f)
            }
            // ---------------- Deri ve defter ----------------
            30 -> { // dikişli deri
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(l, t, r.right, r.bottom, color, CoverArt.darken(color, 0.4f), Shader.TileMode.CLAMP)
                c.drawRect(r, p); p.shader = null
                for (i in 0 until 260) dot(c, a(if (i % 2 == 0) Color.BLACK else Color.WHITE, 14), l + rnd.nextFloat() * w, t + rnd.nextFloat() * h, w * 0.006f)
                stroke(a(pale, 210), w * 0.012f)
                p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(w * 0.035f, w * 0.03f), 0f)
                rr.set(l + w * 0.07f, t + w * 0.07f, r.right - w * 0.07f, r.bottom - w * 0.07f)
                c.drawRoundRect(rr, w * 0.04f, w * 0.04f, p)
                p.pathEffect = null
                p.style = Paint.Style.FILL; p.color = a(pale, 235)
                rr.set(cx - w * 0.26f, t + h * 0.26f, cx + w * 0.26f, t + h * 0.4f)
                c.drawRoundRect(rr, w * 0.02f, w * 0.02f, p)
                stroke(a(deep, 150), w * 0.006f)
                rr.inset(w * 0.02f, w * 0.02f)
                c.drawRoundRect(rr, w * 0.012f, w * 0.012f, p)
            }
            31 -> { // lastikli defter
                bg(c, r, color, dark)
                p.style = Paint.Style.FILL; p.color = a(Color.BLACK, 40)
                c.drawRect(l, t, l + w * 0.07f, r.bottom, p)
                p.color = deep
                c.drawRect(l + w * 0.8f, t, l + w * 0.87f, r.bottom, p)
                p.color = a(Color.WHITE, 40)
                c.drawRect(l + w * 0.8f, t, l + w * 0.815f, r.bottom, p)
                stroke(a(Color.rgb(214, 178, 110), 230), w * 0.02f)
                p.strokeCap = Paint.Cap.BUTT
                path.reset(); path.moveTo(l + w * 0.4f, t); path.lineTo(l + w * 0.4f, t + h * 0.16f); path.lineTo(l + w * 0.46f, t + h * 0.13f); path.lineTo(l + w * 0.52f, t + h * 0.16f); path.lineTo(l + w * 0.52f, t)
                p.style = Paint.Style.FILL; p.color = Color.rgb(200, 70, 70)
                c.drawPath(path, p)
            }
            32 -> { // köşebentli
                bg(c, r, deep, CoverArt.darken(color, 0.7f))
                val gold = Color.rgb(214, 178, 110)
                val k = w * 0.2f
                fill(c, gold, r.right, t, r.right - k, t, r.right, t + k)
                fill(c, gold, r.right, r.bottom, r.right - k, r.bottom, r.right, r.bottom - k)
                p.style = Paint.Style.FILL; p.color = color
                c.drawRect(l, t, l + w * 0.14f, r.bottom, p)
                stroke(gold, w * 0.008f)
                c.drawLine(l + w * 0.14f, t, l + w * 0.14f, r.bottom, p)
                rr.set(l + w * 0.26f, t + h * 0.14f, r.right - w * 0.12f, r.bottom - h * 0.14f)
                c.drawRect(rr, p)
                rr.inset(w * 0.025f, w * 0.025f)
                c.drawRect(rr, p)
                fill(c, gold, cx + w * 0.07f, cy - w * 0.07f, cx + w * 0.14f, cy, cx + w * 0.07f, cy + w * 0.07f, cx, cy)
            }
            else -> { // 33: spiralli kraft
                bg(c, r, Color.rgb(206, 176, 136), Color.rgb(186, 152, 112))
                for (i in 0 until 200) dot(c, a(Color.rgb(120, 90, 60), 26), l + rnd.nextFloat() * w, t + rnd.nextFloat() * h, w * 0.004f)
                p.style = Paint.Style.FILL; p.color = color
                rr.set(cx - w * 0.24f, t + h * 0.24f, cx + w * 0.32f, t + h * 0.42f)
                c.drawRoundRect(rr, w * 0.02f, w * 0.02f, p)
                stroke(a(pale, 220), w * 0.008f)
                c.drawLine(rr.left + w * 0.05f, rr.centerY() - h * 0.02f, rr.right - w * 0.05f, rr.centerY() - h * 0.02f, p)
                c.drawLine(rr.left + w * 0.05f, rr.centerY() + h * 0.03f, rr.right - w * 0.16f, rr.centerY() + h * 0.03f, p)
                val n = 12
                for (i in 0 until n) {
                    val y = t + h * (i + 0.5f) / n
                    dot(c, Color.rgb(90, 70, 50), l + w * 0.1f, y, w * 0.022f)
                    stroke(Color.rgb(150, 150, 156), w * 0.016f)
                    rr.set(l - w * 0.05f, y - w * 0.03f, l + w * 0.11f, y + w * 0.03f)
                    c.drawArc(rr, 200f, 320f, false, p)
                }
            }
        }
        p.style = Paint.Style.FILL
    }
}
