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
        "Zil", "Hediye", "Ampul", "Takvim", "Saat", "Pasta", "Taç", "Damla",
        "Çam ağacı", "Ağaç", "Dağ", "Kaktüs", "Lale", "Kiraz", "Elma", "Karpuz",
        "Dondurma", "Çay", "Balon", "Kâğıt uçak", "Zarf", "Nota", "Ünlem", "Soru",
        "Tamam", "İptal", "Hedef", "Kupa", "Roket", "Gezegen", "Uğur böceği", "Arı",
        "Kar tanesi", "Şemsiye", "ÖNEMLİ", "YAPILACAK", "TOPLANTI", "SINAV", "TESLİM", "TATİL",
        "Diş", "İlaç", "Hastane", "Çadır", "Kamp ateşi", "Dambıl", "Top", "Bayram şekeri",
        "Sunum panosu", "Belge", "Kum saati", "Kadehler", "Çift kalp",
        "RANDEVU", "DİŞÇİ", "DOKTOR", "HASTANE", "DATE", "GIRLS DATE", "KAMP", "BAYRAM",
        "SUNUM", "BAŞVURU", "SON GÜN", "RAPOR", "SPOR", "ÖDEME", "PROJE"
    )
    /** Çıkartma penceresindeki konu grupları. */
    val groups = listOf(
        Pair("Yazılı etiketler", intArrayOf(62, 63, 64, 65, 66, 67, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95)),
        Pair("İş, okul ve proje", intArrayOf(23, 24, 25, 26, 31, 32, 30, 76, 77, 78, 54, 55, 48, 15, 9)),
        Pair("İşaretler ve süsler", intArrayOf(0, 16, 1, 80, 10, 52, 53, 50, 51, 11, 27, 28, 19, 6, 7, 8, 18, 34, 21)),
        Pair("Doğa ve hava", intArrayOf(2, 3, 40, 13, 36, 37, 38, 39, 17, 14, 58, 59, 4, 5, 12, 20, 35, 60, 61)),
        Pair("Yiyecek ve içecek", intArrayOf(22, 45, 33, 44, 41, 42, 43, 75, 79)),
        Pair("Sağlık ve spor", intArrayOf(68, 69, 70, 73, 74)),
        Pair("Gezi, kutlama ve eğlence", intArrayOf(71, 72, 47, 56, 57, 46, 29, 49))
    )
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }

    private fun tri(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        c.drawPath(path, fill)
    }

    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        rect.set(l, t, r, b); c.drawOval(rect, fill)
    }

    private fun badge(c: Canvas, u: Float, color: Int) {
        f(c, color); c.drawCircle(0f, 0f, u * 0.92f, fill)
        line.setColor(Color.WHITE); line.strokeWidth = u * 0.17f
    }

    private fun banner(c: Canvas, w: Float, h: Float, color: Int, text: String) {
        f(c, color)
        rect.set(-w / 2f, -h * 0.42f, w / 2f, h * 0.42f)
        c.drawRoundRect(rect, h * 0.42f, h * 0.42f, fill)
        line.setColor(Color.argb(170, 255, 255, 255)); line.strokeWidth = h * 0.04f
        rect.inset(h * 0.09f, h * 0.09f)
        c.drawRoundRect(rect, h * 0.33f, h * 0.33f, line)
        txt.setColor(Color.WHITE)
        txt.textSize = h * 0.4f
        val tw = txt.measureText(text)
        if (tw > w * 0.8f) txt.textSize = h * 0.4f * w * 0.8f / tw
        c.drawText(text, 0f, txt.textSize * 0.36f, txt)
    }

    /** Çıkartmanın genişlik/yükseklik oranı. */
    fun aspect(kind: Int): Float = when (kind) {
        6, 7 -> 3.2f
        in 62..67, in 81..95 -> 3.2f
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
            36 -> { // çam
                f(c, Color.rgb(132, 96, 66)); c.drawRect(-u * 0.1f, u * 0.55f, u * 0.1f, u * 0.95f, fill)
                f(c, Color.rgb(52, 124, 90))
                tri(c, -u * 0.7f, u * 0.6f, 0f, -u * 0.2f, u * 0.7f, u * 0.6f)
                f(c, Color.rgb(70, 146, 104)); tri(c, -u * 0.56f, u * 0.15f, 0f, -u * 0.6f, u * 0.56f, u * 0.15f)
                f(c, Color.rgb(96, 168, 120)); tri(c, -u * 0.4f, -u * 0.3f, 0f, -u * 0.98f, u * 0.4f, -u * 0.3f)
            }
            37 -> { // ağaç
                f(c, Color.rgb(132, 96, 66)); c.drawRect(-u * 0.1f, u * 0.2f, u * 0.1f, u * 0.95f, fill)
                f(c, Color.rgb(96, 168, 110)); c.drawCircle(0f, -u * 0.3f, u * 0.6f, fill)
                c.drawCircle(-u * 0.42f, u * 0.0f, u * 0.36f, fill); c.drawCircle(u * 0.42f, u * 0.0f, u * 0.36f, fill)
                f(c, Color.WHITE, 60); c.drawCircle(-u * 0.2f, -u * 0.5f, u * 0.2f, fill)
            }
            38 -> { // dağ
                f(c, Color.rgb(140, 160, 190)); tri(c, -u * 0.2f, u * 0.7f, u * 0.4f, -u * 0.3f, u * 0.98f, u * 0.7f)
                f(c, Color.rgb(104, 128, 168)); tri(c, -u * 0.98f, u * 0.7f, -u * 0.25f, -u * 0.75f, u * 0.5f, u * 0.7f)
                f(c, Color.WHITE); tri(c, -u * 0.47f, -u * 0.32f, -u * 0.25f, -u * 0.75f, -u * 0.03f, -u * 0.32f)
            }
            39 -> { // kaktüs
                f(c, Color.rgb(96, 168, 120))
                rect.set(-u * 0.22f, -u * 0.9f, u * 0.22f, u * 0.5f); c.drawRoundRect(rect, u * 0.22f, u * 0.22f, fill)
                rect.set(-u * 0.68f, -u * 0.3f, -u * 0.4f, u * 0.2f); c.drawRoundRect(rect, u * 0.14f, u * 0.14f, fill)
                c.drawRect(-u * 0.55f, u * 0.02f, -u * 0.1f, u * 0.22f, fill)
                rect.set(u * 0.4f, -u * 0.55f, u * 0.68f, u * 0.0f); c.drawRoundRect(rect, u * 0.14f, u * 0.14f, fill)
                c.drawRect(u * 0.1f, -u * 0.2f, u * 0.55f, u * 0.0f, fill)
                f(c, Color.rgb(214, 140, 96)); path.reset(); path.moveTo(-u * 0.45f, u * 0.45f); path.lineTo(u * 0.45f, u * 0.45f); path.lineTo(u * 0.34f, u * 0.95f); path.lineTo(-u * 0.34f, u * 0.95f); path.close(); c.drawPath(path, fill)
                f(c, Color.rgb(244, 143, 166)); c.drawCircle(0f, -u * 0.9f, u * 0.12f, fill)
            }
            40 -> { // lale
                line.setColor(Color.rgb(96, 168, 110)); line.strokeWidth = u * 0.1f
                c.drawLine(0f, -u * 0.1f, 0f, u * 0.95f, line)
                f(c, Color.rgb(96, 168, 110)); path.reset(); path.moveTo(0f, u * 0.8f); path.quadTo(-u * 0.7f, u * 0.5f, -u * 0.5f, u * 0.0f); path.quadTo(-u * 0.1f, u * 0.4f, 0f, u * 0.8f); c.drawPath(path, fill)
                f(c, Color.rgb(232, 90, 110)); path.reset(); path.moveTo(-u * 0.42f, -u * 0.85f); path.lineTo(-u * 0.2f, -u * 0.55f); path.lineTo(0f, -u * 0.95f); path.lineTo(u * 0.2f, -u * 0.55f); path.lineTo(u * 0.42f, -u * 0.85f)
                path.quadTo(u * 0.6f, u * 0.0f, 0f, u * 0.05f); path.quadTo(-u * 0.6f, u * 0.0f, -u * 0.42f, -u * 0.85f); c.drawPath(path, fill)
            }
            41 -> { // kiraz
                line.setColor(Color.rgb(110, 150, 80)); line.strokeWidth = u * 0.07f
                path.reset(); path.moveTo(-u * 0.4f, u * 0.3f); path.quadTo(-u * 0.2f, -u * 0.5f, u * 0.2f, -u * 0.85f); path.quadTo(u * 0.4f, -u * 0.3f, u * 0.42f, u * 0.4f); c.drawPath(path, line)
                f(c, Color.rgb(210, 50, 70)); c.drawCircle(-u * 0.42f, u * 0.5f, u * 0.38f, fill); c.drawCircle(u * 0.44f, u * 0.58f, u * 0.38f, fill)
                f(c, Color.WHITE, 130); c.drawCircle(-u * 0.55f, u * 0.38f, u * 0.09f, fill); c.drawCircle(u * 0.31f, u * 0.46f, u * 0.09f, fill)
            }
            42 -> { // elma
                f(c, Color.rgb(226, 70, 70)); c.drawCircle(-u * 0.26f, u * 0.15f, u * 0.6f, fill); c.drawCircle(u * 0.26f, u * 0.15f, u * 0.6f, fill)
                line.setColor(Color.rgb(120, 84, 60)); line.strokeWidth = u * 0.08f; c.drawLine(0f, -u * 0.4f, u * 0.08f, -u * 0.85f, line)
                f(c, Color.rgb(110, 176, 100)); oval(c, u * 0.1f, -u * 0.85f, u * 0.6f, -u * 0.55f)
                f(c, Color.WHITE, 110); c.drawCircle(-u * 0.45f, -u * 0.1f, u * 0.13f, fill)
            }
            43 -> { // karpuz dilimi
                f(c, Color.rgb(96, 168, 110)); rect.set(-u * 0.95f, -u * 1.1f, u * 0.95f, u * 0.8f); c.drawArc(rect, 0f, 180f, true, fill)
                f(c, Color.rgb(240, 244, 230)); rect.inset(u * 0.1f, u * 0.1f); c.drawArc(rect, 0f, 180f, true, fill)
                f(c, Color.rgb(236, 88, 100)); rect.inset(u * 0.08f, u * 0.08f); c.drawArc(rect, 0f, 180f, true, fill)
                f(c, Color.rgb(50, 40, 40)); for (i in -2..2) oval(c, i * u * 0.27f - u * 0.04f, u * 0.1f + (if (i % 2 == 0) u * 0.18f else 0f), i * u * 0.27f + u * 0.04f, u * 0.22f + (if (i % 2 == 0) u * 0.18f else 0f))
            }
            44 -> { // dondurma
                f(c, Color.rgb(226, 178, 120)); tri(c, -u * 0.4f, u * 0.0f, u * 0.4f, u * 0.0f, 0f, u * 0.98f)
                f(c, Color.rgb(250, 190, 212)); c.drawCircle(0f, -u * 0.3f, u * 0.48f, fill)
                f(c, Color.rgb(255, 240, 200)); c.drawCircle(-u * 0.3f, -u * 0.02f, u * 0.2f, fill); c.drawCircle(u * 0.3f, -u * 0.02f, u * 0.2f, fill); c.drawCircle(0f, u * 0.02f, u * 0.2f, fill)
                f(c, Color.rgb(210, 50, 70)); c.drawCircle(0f, -u * 0.82f, u * 0.13f, fill)
            }
            45 -> { // çay bardağı
                f(c, Color.rgb(240, 244, 248)); oval(c, -u * 0.85f, u * 0.55f, u * 0.85f, u * 0.92f)
                f(c, Color.rgb(176, 58, 46))
                path.reset(); path.moveTo(-u * 0.42f, -u * 0.6f); path.quadTo(-u * 0.2f, -u * 0.1f, -u * 0.45f, u * 0.35f); path.quadTo(0f, u * 0.95f, u * 0.45f, u * 0.35f); path.quadTo(u * 0.2f, -u * 0.1f, u * 0.42f, -u * 0.6f); path.close(); c.drawPath(path, fill)
                line.setColor(Color.argb(200, 235, 240, 246)); line.strokeWidth = u * 0.06f
                path.reset(); path.moveTo(-u * 0.44f, -u * 0.85f); path.quadTo(-u * 0.2f, -u * 0.1f, -u * 0.47f, u * 0.35f); path.quadTo(0f, u * 0.98f, u * 0.47f, u * 0.35f); path.quadTo(u * 0.2f, -u * 0.1f, u * 0.44f, -u * 0.85f); c.drawPath(path, line)
            }
            46 -> { // balon
                line.setColor(Color.rgb(150, 150, 160)); line.strokeWidth = u * 0.04f
                path.reset(); path.moveTo(0f, u * 0.3f); path.quadTo(u * 0.2f, u * 0.6f, -u * 0.05f, u * 0.98f); c.drawPath(path, line)
                f(c, Color.rgb(232, 96, 116)); oval(c, -u * 0.55f, -u * 0.98f, u * 0.55f, u * 0.3f)
                tri(c, -u * 0.1f, u * 0.38f, u * 0.1f, u * 0.38f, 0f, u * 0.25f)
                f(c, Color.WHITE, 130); oval(c, -u * 0.35f, -u * 0.75f, -u * 0.15f, -u * 0.4f)
            }
            47 -> { // kâğıt uçak
                f(c, Color.rgb(150, 196, 240)); tri(c, -u * 0.95f, -u * 0.1f, u * 0.95f, -u * 0.75f, -u * 0.1f, u * 0.25f)
                f(c, Color.rgb(110, 160, 220)); tri(c, -u * 0.1f, u * 0.25f, u * 0.95f, -u * 0.75f, u * 0.25f, u * 0.75f)
                f(c, Color.rgb(84, 128, 190)); tri(c, -u * 0.1f, u * 0.25f, u * 0.12f, u * 0.42f, -u * 0.05f, u * 0.7f)
            }
            48 -> { // zarf
                f(c, Color.rgb(250, 236, 214)); rect.set(-u * 0.92f, -u * 0.6f, u * 0.92f, u * 0.6f); c.drawRoundRect(rect, u * 0.08f, u * 0.08f, fill)
                line.setColor(Color.rgb(206, 170, 130)); line.strokeWidth = u * 0.06f
                c.drawRoundRect(rect, u * 0.08f, u * 0.08f, line)
                path.reset(); path.moveTo(-u * 0.9f, -u * 0.55f); path.lineTo(0f, u * 0.1f); path.lineTo(u * 0.9f, -u * 0.55f); c.drawPath(path, line)
                f(c, Color.rgb(226, 80, 96)); heart(c, 0f, u * 0.12f, u * 0.18f)
            }
            49 -> { // nota
                f(c, Color.rgb(70, 70, 90)); oval(c, -u * 0.7f, u * 0.4f, -u * 0.2f, u * 0.82f); oval(c, u * 0.2f, u * 0.22f, u * 0.7f, u * 0.64f)
                line.setColor(Color.rgb(70, 70, 90)); line.strokeWidth = u * 0.1f
                c.drawLine(-u * 0.24f, u * 0.6f, -u * 0.24f, -u * 0.7f, line); c.drawLine(u * 0.66f, u * 0.42f, u * 0.66f, -u * 0.88f, line)
                line.strokeWidth = u * 0.2f; c.drawLine(-u * 0.24f, -u * 0.64f, u * 0.66f, -u * 0.82f, line)
            }
            50 -> { badge(c, u, Color.rgb(232, 96, 76)); c.drawLine(0f, -u * 0.5f, 0f, u * 0.12f, line); f(c, Color.WHITE); c.drawCircle(0f, u * 0.48f, u * 0.1f, fill) }
            51 -> {
                badge(c, u, Color.rgb(96, 140, 220))
                path.reset(); path.moveTo(-u * 0.28f, -u * 0.25f); path.quadTo(-u * 0.25f, -u * 0.6f, u * 0.02f, -u * 0.58f); path.quadTo(u * 0.36f, -u * 0.55f, u * 0.28f, -u * 0.22f); path.quadTo(u * 0.2f, -u * 0.02f, 0f, u * 0.14f); c.drawPath(path, line)
                f(c, Color.WHITE); c.drawCircle(0f, u * 0.5f, u * 0.1f, fill)
            }
            52 -> { badge(c, u, Color.rgb(80, 176, 120)); path.reset(); path.moveTo(-u * 0.42f, u * 0.02f); path.lineTo(-u * 0.12f, u * 0.34f); path.lineTo(u * 0.44f, -u * 0.32f); c.drawPath(path, line) }
            53 -> { badge(c, u, Color.rgb(214, 80, 80)); c.drawLine(-u * 0.34f, -u * 0.34f, u * 0.34f, u * 0.34f, line); c.drawLine(u * 0.34f, -u * 0.34f, -u * 0.34f, u * 0.34f, line) }
            54 -> { // hedef
                f(c, Color.rgb(226, 76, 76)); c.drawCircle(0f, 0f, u * 0.95f, fill)
                f(c, Color.WHITE); c.drawCircle(0f, 0f, u * 0.7f, fill)
                f(c, Color.rgb(226, 76, 76)); c.drawCircle(0f, 0f, u * 0.45f, fill)
                f(c, Color.WHITE); c.drawCircle(0f, 0f, u * 0.2f, fill)
            }
            55 -> { // kupa
                f(c, Color.rgb(246, 196, 70))
                path.reset(); path.moveTo(-u * 0.5f, -u * 0.85f); path.lineTo(u * 0.5f, -u * 0.85f); path.quadTo(u * 0.5f, u * 0.2f, 0f, u * 0.25f); path.quadTo(-u * 0.5f, u * 0.2f, -u * 0.5f, -u * 0.85f); c.drawPath(path, fill)
                c.drawRect(-u * 0.08f, u * 0.2f, u * 0.08f, u * 0.6f, fill)
                rect.set(-u * 0.4f, u * 0.58f, u * 0.4f, u * 0.88f); c.drawRoundRect(rect, u * 0.06f, u * 0.06f, fill)
                line.setColor(Color.rgb(246, 196, 70)); line.strokeWidth = u * 0.1f
                rect.set(-u * 0.85f, -u * 0.7f, -u * 0.3f, -u * 0.1f); c.drawArc(rect, 90f, 180f, false, line)
                rect.set(u * 0.3f, -u * 0.7f, u * 0.85f, -u * 0.1f); c.drawArc(rect, 270f, 180f, false, line)
                f(c, Color.WHITE, 200); star(c, 0f, -u * 0.35f, u * 0.24f, 0.46f, 5)
            }
            56 -> { // roket
                c.rotate(35f)
                f(c, Color.rgb(246, 150, 60)); tri(c, -u * 0.2f, u * 0.55f, u * 0.2f, u * 0.55f, 0f, u * 0.98f)
                f(c, Color.rgb(226, 80, 90)); tri(c, -u * 0.3f, u * 0.1f, -u * 0.6f, u * 0.6f, -u * 0.3f, u * 0.5f); tri(c, u * 0.3f, u * 0.1f, u * 0.6f, u * 0.6f, u * 0.3f, u * 0.5f)
                f(c, Color.rgb(232, 236, 242)); path.reset(); path.moveTo(0f, -u * 0.98f); path.quadTo(u * 0.5f, -u * 0.4f, u * 0.3f, u * 0.55f); path.lineTo(-u * 0.3f, u * 0.55f); path.quadTo(-u * 0.5f, -u * 0.4f, 0f, -u * 0.98f); c.drawPath(path, fill)
                f(c, Color.rgb(110, 170, 230)); c.drawCircle(0f, -u * 0.2f, u * 0.17f, fill)
            }
            57 -> { // gezegen
                f(c, Color.rgb(240, 170, 110)); c.drawCircle(0f, 0f, u * 0.55f, fill)
                f(c, Color.rgb(226, 140, 90)); oval(c, -u * 0.35f, -u * 0.25f, u * 0.1f, -u * 0.05f); oval(c, -u * 0.05f, u * 0.15f, u * 0.38f, u * 0.32f)
                c.rotate(-20f)
                line.setColor(Color.rgb(150, 130, 200)); line.strokeWidth = u * 0.09f
                rect.set(-u * 0.95f, -u * 0.22f, u * 0.95f, u * 0.22f); c.drawArc(rect, -20f, 220f, false, line)
            }
            58 -> { // uğur böceği
                f(c, Color.rgb(40, 36, 40)); c.drawCircle(0f, -u * 0.55f, u * 0.32f, fill)
                f(c, Color.rgb(222, 60, 56)); oval(c, -u * 0.7f, -u * 0.5f, u * 0.7f, u * 0.9f)
                line.setColor(Color.rgb(40, 36, 40)); line.strokeWidth = u * 0.06f; c.drawLine(0f, -u * 0.48f, 0f, u * 0.9f, line)
                c.drawLine(-u * 0.12f, -u * 0.8f, -u * 0.3f, -u * 0.98f, line); c.drawLine(u * 0.12f, -u * 0.8f, u * 0.3f, -u * 0.98f, line)
                f(c, Color.rgb(40, 36, 40)); c.drawCircle(-u * 0.35f, -u * 0.05f, u * 0.13f, fill); c.drawCircle(u * 0.35f, -u * 0.05f, u * 0.13f, fill); c.drawCircle(-u * 0.28f, u * 0.48f, u * 0.11f, fill); c.drawCircle(u * 0.28f, u * 0.48f, u * 0.11f, fill)
                f(c, Color.WHITE); c.drawCircle(-u * 0.13f, -u * 0.6f, u * 0.06f, fill); c.drawCircle(u * 0.13f, -u * 0.6f, u * 0.06f, fill)
            }
            59 -> { // arı
                f(c, Color.rgb(200, 226, 246), 220); oval(c, -u * 0.5f, -u * 0.9f, u * 0.05f, -u * 0.15f); oval(c, -u * 0.05f, -u * 0.9f, u * 0.5f, -u * 0.15f)
                f(c, Color.rgb(250, 204, 70)); oval(c, -u * 0.75f, -u * 0.3f, u * 0.75f, u * 0.6f)
                c.save(); path.reset(); rect.set(-u * 0.75f, -u * 0.3f, u * 0.75f, u * 0.6f); path.addOval(rect, Path.Direction.CW); c.clipPath(path)
                f(c, Color.rgb(50, 44, 44)); c.drawRect(-u * 0.25f, -u * 0.4f, -u * 0.02f, u * 0.7f, fill); c.drawRect(u * 0.22f, -u * 0.4f, u * 0.45f, u * 0.7f, fill)
                c.restore()
                f(c, Color.rgb(50, 44, 44)); c.drawCircle(-u * 0.52f, u * 0.08f, u * 0.07f, fill)
            }
            60 -> { // kar tanesi
                line.setColor(Color.rgb(120, 180, 230)); line.strokeWidth = u * 0.1f
                for (k in 0 until 6) {
                    val an = Math.toRadians(k * 60.0)
                    val ex = (cos(an) * u * 0.92f).toFloat(); val ey = (sin(an) * u * 0.92f).toFloat()
                    c.drawLine(0f, 0f, ex, ey, line)
                    val mx = ex * 0.6f; val my = ey * 0.6f
                    c.drawLine(mx, my, mx + (cos(an + 0.8) * u * 0.28f).toFloat(), my + (sin(an + 0.8) * u * 0.28f).toFloat(), line)
                    c.drawLine(mx, my, mx + (cos(an - 0.8) * u * 0.28f).toFloat(), my + (sin(an - 0.8) * u * 0.28f).toFloat(), line)
                }
            }
            61 -> { // şemsiye
                f(c, Color.rgb(110, 160, 226)); rect.set(-u * 0.92f, -u * 0.9f, u * 0.92f, u * 0.7f); c.drawArc(rect, 180f, 180f, true, fill)
                f(c, Color.rgb(150, 196, 240)); rect.set(-u * 0.32f, -u * 0.9f, u * 0.32f, u * 0.7f); c.drawArc(rect, 180f, 180f, true, fill)
                line.setColor(Color.rgb(90, 80, 90)); line.strokeWidth = u * 0.08f
                path.reset(); path.moveTo(0f, -u * 0.1f); path.lineTo(0f, u * 0.7f); path.quadTo(0f, u * 0.95f, u * 0.25f, u * 0.92f); path.quadTo(u * 0.4f, u * 0.9f, u * 0.4f, u * 0.7f); c.drawPath(path, line)
            }
            62 -> banner(c, w, h, Color.rgb(226, 76, 76), "ÖNEMLİ")
            63 -> banner(c, w, h, Color.rgb(96, 140, 220), "YAPILACAK")
            64 -> banner(c, w, h, Color.rgb(132, 96, 196), "TOPLANTI")
            65 -> banner(c, w, h, Color.rgb(232, 140, 50), "SINAV")
            66 -> banner(c, w, h, Color.rgb(40, 150, 140), "TESLİM")
            67 -> banner(c, w, h, Color.rgb(80, 176, 120), "TATİL")
            68 -> { // diş
                f(c, Color.rgb(250, 250, 252))
                path.reset(); path.moveTo(-u * 0.6f, -u * 0.5f); path.quadTo(-u * 0.6f, -u * 0.95f, -u * 0.15f, -u * 0.8f); path.quadTo(0f, -u * 0.72f, u * 0.15f, -u * 0.8f); path.quadTo(u * 0.6f, -u * 0.95f, u * 0.6f, -u * 0.5f)
                path.quadTo(u * 0.6f, u * 0.1f, u * 0.38f, u * 0.85f); path.quadTo(u * 0.2f, u * 0.95f, u * 0.14f, u * 0.3f); path.quadTo(0f, u * 0.05f, -u * 0.14f, u * 0.3f); path.quadTo(-u * 0.2f, u * 0.95f, -u * 0.38f, u * 0.85f); path.quadTo(-u * 0.6f, u * 0.1f, -u * 0.6f, -u * 0.5f); path.close()
                c.drawPath(path, fill)
                line.setColor(Color.rgb(130, 180, 226)); line.strokeWidth = u * 0.07f; c.drawPath(path, line)
                f(c, Color.rgb(130, 180, 226), 150); c.drawCircle(-u * 0.28f, -u * 0.42f, u * 0.1f, fill)
            }
            69 -> { // ilaç
                c.rotate(-40f)
                f(c, Color.rgb(232, 96, 106)); rect.set(-u * 0.9f, -u * 0.36f, u * 0.9f, u * 0.36f); c.drawRoundRect(rect, u * 0.36f, u * 0.36f, fill)
                f(c, Color.rgb(250, 244, 236)); c.save(); c.clipRect(0f, -u, u, u); c.drawRoundRect(rect, u * 0.36f, u * 0.36f, fill); c.restore()
                f(c, Color.WHITE, 120); rect.set(-u * 0.7f, -u * 0.24f, -u * 0.2f, -u * 0.1f); c.drawRoundRect(rect, u * 0.07f, u * 0.07f, fill)
            }
            70 -> { // hastane
                f(c, Color.rgb(250, 250, 252)); rect.set(-u * 0.9f, -u * 0.9f, u * 0.9f, u * 0.9f); c.drawRoundRect(rect, u * 0.22f, u * 0.22f, fill)
                line.setColor(Color.rgb(226, 76, 76)); line.strokeWidth = u * 0.08f; c.drawRoundRect(rect, u * 0.22f, u * 0.22f, line)
                f(c, Color.rgb(226, 76, 76)); c.drawRect(-u * 0.18f, -u * 0.6f, u * 0.18f, u * 0.6f, fill); c.drawRect(-u * 0.6f, -u * 0.18f, u * 0.6f, u * 0.18f, fill)
            }
            71 -> { // çadır
                f(c, Color.rgb(96, 168, 110)); c.drawRect(-u * 0.98f, u * 0.6f, u * 0.98f, u * 0.75f, fill)
                f(c, Color.rgb(240, 150, 70)); tri(c, -u * 0.85f, u * 0.62f, 0f, -u * 0.7f, u * 0.85f, u * 0.62f)
                f(c, Color.rgb(120, 70, 50)); tri(c, -u * 0.28f, u * 0.62f, 0f, -u * 0.1f, u * 0.28f, u * 0.62f)
                line.setColor(Color.rgb(120, 70, 50)); line.strokeWidth = u * 0.06f; c.drawLine(-u * 0.1f, -u * 0.85f, u * 0.5f, u * 0.1f, line); c.drawLine(u * 0.1f, -u * 0.85f, -u * 0.5f, u * 0.1f, line)
            }
            72 -> { // kamp ateşi
                line.setColor(Color.rgb(132, 90, 60)); line.strokeWidth = u * 0.2f
                c.drawLine(-u * 0.7f, u * 0.5f, u * 0.7f, u * 0.85f, line); c.drawLine(u * 0.7f, u * 0.5f, -u * 0.7f, u * 0.85f, line)
                f(c, Color.rgb(240, 110, 50)); path.reset(); path.moveTo(0f, -u * 0.95f); path.quadTo(u * 0.75f, -u * 0.1f, u * 0.4f, u * 0.45f); path.quadTo(0f, u * 0.75f, -u * 0.4f, u * 0.45f); path.quadTo(-u * 0.75f, -u * 0.05f, -u * 0.25f, -u * 0.4f); path.quadTo(-u * 0.1f, -u * 0.6f, 0f, -u * 0.95f); c.drawPath(path, fill)
                f(c, Color.rgb(252, 204, 80)); path.reset(); path.moveTo(0f, -u * 0.3f); path.quadTo(u * 0.35f, u * 0.1f, u * 0.18f, u * 0.42f); path.quadTo(0f, u * 0.56f, -u * 0.18f, u * 0.42f); path.quadTo(-u * 0.35f, u * 0.1f, 0f, -u * 0.3f); c.drawPath(path, fill)
            }
            73 -> { // dambıl
                c.rotate(-30f)
                f(c, Color.rgb(120, 126, 140)); c.drawRect(-u * 0.5f, -u * 0.09f, u * 0.5f, u * 0.09f, fill)
                f(c, Color.rgb(70, 76, 92))
                rect.set(-u * 0.72f, -u * 0.5f, -u * 0.46f, u * 0.5f); c.drawRoundRect(rect, u * 0.08f, u * 0.08f, fill)
                rect.set(u * 0.46f, -u * 0.5f, u * 0.72f, u * 0.5f); c.drawRoundRect(rect, u * 0.08f, u * 0.08f, fill)
                rect.set(-u * 0.95f, -u * 0.32f, -u * 0.74f, u * 0.32f); c.drawRoundRect(rect, u * 0.07f, u * 0.07f, fill)
                rect.set(u * 0.74f, -u * 0.32f, u * 0.95f, u * 0.32f); c.drawRoundRect(rect, u * 0.07f, u * 0.07f, fill)
            }
            74 -> { // top
                f(c, Color.rgb(240, 140, 60)); c.drawCircle(0f, 0f, u * 0.92f, fill)
                line.setColor(Color.rgb(70, 50, 40)); line.strokeWidth = u * 0.06f
                c.drawCircle(0f, 0f, u * 0.92f, line); c.drawLine(-u * 0.92f, 0f, u * 0.92f, 0f, line); c.drawLine(0f, -u * 0.92f, 0f, u * 0.92f, line)
                rect.set(-u * 1.5f, -u * 0.75f, -u * 0.4f, u * 0.75f); c.drawArc(rect, -60f, 120f, false, line)
                rect.set(u * 0.4f, -u * 0.75f, u * 1.5f, u * 0.75f); c.drawArc(rect, 120f, 120f, false, line)
            }
            75 -> { // bayram şekeri
                c.rotate(-25f)
                f(c, Color.rgb(250, 190, 212)); tri(c, -u * 0.45f, 0f, -u * 0.98f, -u * 0.4f, -u * 0.98f, u * 0.4f); tri(c, u * 0.45f, 0f, u * 0.98f, -u * 0.4f, u * 0.98f, u * 0.4f)
                f(c, Color.rgb(232, 96, 130)); oval(c, -u * 0.55f, -u * 0.42f, u * 0.55f, u * 0.42f)
                line.setColor(Color.WHITE); line.strokeWidth = u * 0.09f
                c.drawLine(-u * 0.2f, -u * 0.36f, -u * 0.32f, u * 0.3f, line); c.drawLine(u * 0.1f, -u * 0.4f, -u * 0.02f, u * 0.4f, line); c.drawLine(u * 0.36f, -u * 0.28f, u * 0.26f, u * 0.34f, line)
            }
            76 -> { // sunum panosu
                line.setColor(Color.rgb(110, 110, 124)); line.strokeWidth = u * 0.07f
                c.drawLine(0f, u * 0.4f, -u * 0.4f, u * 0.95f, line); c.drawLine(0f, u * 0.4f, u * 0.4f, u * 0.95f, line)
                f(c, Color.rgb(250, 250, 252)); rect.set(-u * 0.9f, -u * 0.75f, u * 0.9f, u * 0.45f); c.drawRect(rect, fill); c.drawRect(rect, line)
                f(c, Color.rgb(110, 110, 124)); c.drawRect(-u * 0.98f, -u * 0.88f, u * 0.98f, -u * 0.72f, fill)
                f(c, Color.rgb(96, 140, 220)); c.drawRect(-u * 0.6f, -u * 0.05f, -u * 0.36f, u * 0.3f, fill)
                f(c, Color.rgb(80, 176, 120)); c.drawRect(-u * 0.12f, -u * 0.3f, u * 0.12f, u * 0.3f, fill)
                f(c, Color.rgb(232, 140, 50)); c.drawRect(u * 0.36f, -u * 0.52f, u * 0.6f, u * 0.3f, fill)
            }
            77 -> { // belge
                f(c, Color.rgb(250, 250, 252)); path.reset(); path.moveTo(-u * 0.65f, -u * 0.95f); path.lineTo(u * 0.3f, -u * 0.95f); path.lineTo(u * 0.65f, -u * 0.6f); path.lineTo(u * 0.65f, u * 0.95f); path.lineTo(-u * 0.65f, u * 0.95f); path.close(); c.drawPath(path, fill)
                line.setColor(Color.rgb(110, 130, 170)); line.strokeWidth = u * 0.07f; c.drawPath(path, line)
                for (k in 0 until 4) c.drawLine(-u * 0.4f, -u * 0.35f + k * u * 0.3f, u * (if (k == 3) 0.1f else 0.4f), -u * 0.35f + k * u * 0.3f, line)
                f(c, Color.rgb(110, 130, 170)); tri(c, u * 0.3f, -u * 0.95f, u * 0.65f, -u * 0.6f, u * 0.3f, -u * 0.6f)
            }
            78 -> { // kum saati
                f(c, Color.rgb(132, 96, 66)); c.drawRect(-u * 0.6f, -u * 0.95f, u * 0.6f, -u * 0.8f, fill); c.drawRect(-u * 0.6f, u * 0.8f, u * 0.6f, u * 0.95f, fill)
                f(c, Color.rgb(220, 236, 248)); tri(c, -u * 0.5f, -u * 0.8f, u * 0.5f, -u * 0.8f, 0f, 0f); tri(c, -u * 0.5f, u * 0.8f, u * 0.5f, u * 0.8f, 0f, 0f)
                f(c, Color.rgb(240, 180, 80)); tri(c, -u * 0.28f, -u * 0.45f, u * 0.28f, -u * 0.45f, 0f, 0f); tri(c, -u * 0.44f, u * 0.8f, u * 0.44f, u * 0.8f, 0f, u * 0.35f)
            }
            79 -> { // kadehler
                for (sgn in intArrayOf(-1, 1)) {
                    c.save(); c.rotate(sgn * 14f); c.translate(sgn * u * 0.38f, 0f)
                    f(c, Color.rgb(250, 190, 212)); tri(c, -u * 0.36f, -u * 0.75f, u * 0.36f, -u * 0.75f, 0f, -u * 0.05f)
                    line.setColor(Color.rgb(200, 210, 226)); line.strokeWidth = u * 0.06f
                    c.drawLine(0f, -u * 0.05f, 0f, u * 0.75f, line); c.drawLine(-u * 0.25f, u * 0.78f, u * 0.25f, u * 0.78f, line)
                    path.reset(); path.moveTo(-u * 0.4f, -u * 0.82f); path.lineTo(0f, -u * 0.05f); path.lineTo(u * 0.4f, -u * 0.82f); c.drawPath(path, line)
                    c.restore()
                }
                f(c, Color.rgb(252, 214, 100)); star(c, 0f, -u * 0.88f, u * 0.12f, 0.46f, 4)
            }
            80 -> { // çift kalp
                f(c, Color.rgb(250, 170, 190)); heart(c, u * 0.3f, -u * 0.25f, u * 0.5f)
                f(c, Color.rgb(232, 80, 110)); heart(c, -u * 0.22f, u * 0.2f, u * 0.62f)
                f(c, Color.WHITE, 130); c.drawCircle(-u * 0.5f, -u * 0.08f, u * 0.09f, fill)
            }
            81 -> banner(c, w, h, Color.rgb(96, 140, 220), "RANDEVU")
            82 -> banner(c, w, h, Color.rgb(90, 180, 200), "DİŞÇİ")
            83 -> banner(c, w, h, Color.rgb(60, 160, 150), "DOKTOR")
            84 -> banner(c, w, h, Color.rgb(226, 76, 76), "HASTANE")
            85 -> banner(c, w, h, Color.rgb(232, 80, 110), "DATE ♥")
            86 -> banner(c, w, h, Color.rgb(214, 100, 180), "GIRLS DATE")
            87 -> banner(c, w, h, Color.rgb(96, 150, 100), "KAMP")
            88 -> banner(c, w, h, Color.rgb(196, 150, 60), "BAYRAM")
            89 -> banner(c, w, h, Color.rgb(132, 96, 196), "SUNUM")
            90 -> banner(c, w, h, Color.rgb(54, 124, 178), "BAŞVURU")
            91 -> banner(c, w, h, Color.rgb(200, 50, 50), "SON GÜN!")
            92 -> banner(c, w, h, Color.rgb(100, 110, 140), "RAPOR")
            93 -> banner(c, w, h, Color.rgb(240, 130, 60), "SPOR")
            94 -> banner(c, w, h, Color.rgb(60, 150, 100), "ÖDEME")
            95 -> banner(c, w, h, Color.rgb(84, 84, 170), "PROJE")
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
