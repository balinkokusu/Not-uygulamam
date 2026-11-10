package com.notuygulamam.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Calendar
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Kağıdı ve çizgileri bir tuvale çizer. Ekranda ve PDF dışa aktarmada aynı kod kullanılır.
 * Her kullanıcının kendi boyaları olduğundan farklı iş parçacıklarında ayrı örnekler oluşturulmalıdır.
 */
class PageRenderer {
    private val DW = 1200f
    private val DH = 1700f

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

    /** Duyarlılık 0'da çarpan 1; 1'de çarpan 0,2 (hafif basınç) ile 2,8 (tam basınç) arasında. */
    fun widthFactor(s: Stroke, p: Float): Float = 1f - s.sens * 0.85f + s.sens * 3.0f * p

    private fun kindScale(k: Int): Float = when (k) {
        1 -> 0.7f
        2 -> 1.0f
        13 -> 1.5f
        14 -> 0.45f
        3 -> 0.85f
        4 -> 0.9f
        5 -> 1.9f
        15 -> 1f
        6, 7 -> 1f
        8 -> 1.15f
        9 -> 1f
        10 -> 1.3f
        11 -> 1.15f
        12 -> 0.8f
        else -> 1f
    }

    private fun mix(c: Int, with: Int, t: Float): Int = Color.rgb(
        (Color.red(c) + (Color.red(with) - Color.red(c)) * t).toInt(),
        (Color.green(c) + (Color.green(with) - Color.green(c)) * t).toInt(),
        (Color.blue(c) + (Color.blue(with) - Color.blue(c)) * t).toInt()
    )

    /** Birkaç katmandan oluşan kalemler (dış çizgili, boru, neon): katmanlar tek yol olarak çizilince birleşim yerleri temiz olur. */
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainCache = HashMap<Long, android.graphics.BitmapShader>()

    /** Kurşun için benekli doku: renge ve koyuluğa göre önbelleğe alınır; sayfa koordinatına bağlı olduğu için çizgi boyunca sabit durur. */
    private fun grainShader(color: Int, dark: Boolean, pass: Int): android.graphics.BitmapShader {
        val key = (color.toLong() and 0xFFFFFFFFL) * 4L + (if (dark) 2L else 0L) + pass
        grainCache[key]?.let { return it }
        val n = 128
        val rnd = java.util.Random(7L + pass * 101L)
        val raw = FloatArray(n * n) { rnd.nextFloat() }
        // 3x3 bulanıklaştırma: tek tek benekler yerine kâğıt lifi gibi küçük kümeler oluşur
        val bl = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            var sum = 0f
            for (dy in -1..1) for (dx in -1..1) sum += raw[((y + dy + n) % n) * n + (x + dx + n) % n]
            bl[y * n + x] = sum / 9f
        }
        val px = IntArray(n * n)
        val base = if (dark) 0.68f else 0.30f
        val gain = if (dark) 0.32f else 0.55f
        for (i in 0 until n * n) {
            val v = ((bl[i] - 0.5f) * 3.2f + 0.5f).coerceIn(0f, 1f)
            val a = ((base + gain * v) * 255f).toInt().coerceIn(0, 255)
            px[i] = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
        }
        val bm = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        val sh = android.graphics.BitmapShader(bm, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
        val m = android.graphics.Matrix()
        m.setScale(0.55f, 0.55f)
        m.postTranslate(pass * 37f, pass * 19f)
        sh.setLocalMatrix(m)
        grainCache[key] = sh
        return sh
    }

    /**
     * Kurşun kalem: grafit, basınca göre üç katman halinde birikir (hafif, orta, sert).
     * Her katman kâğıt lifini andıran sabit bir doku desenidir; desen sayfa koordinatına bağlıdır,
     * bu yüzden aynı çizgi yeniden çizilince birebir aynı görünür. Üst üste geçilen yerde alfa birikir.
     */
    private fun pencilLayers(c: Canvas, s: Stroke, i0: Int, i1: Int, segmentOnly: Boolean) {
        val dark = s.kind == 13
        val graphite = mix(s.color, Color.rgb(50, 50, 55), if (dark) 0.1f else 0.45f)
        val wBase = s.width * kindScale(s.kind)
        val thr = floatArrayOf(0f, if (dark) 0.12f else 0.4f, if (dark) 0.35f else 0.72f)
        val wid = if (dark) floatArrayOf(1.08f, 1.0f, 0.94f) else floatArrayOf(0.92f, 0.96f, 1.0f)
        val alp = if (dark) intArrayOf(120, 150, 210) else intArrayOf(120, 110, 130)
        grainPaint.style = Paint.Style.STROKE
        // canlı çizimde yuvarlak uçlar parça eklerinde üst üste binip koyu leke yapıyordu; düz uç kullanılır
        grainPaint.strokeCap = if (segmentOnly) Paint.Cap.BUTT else Paint.Cap.ROUND
        grainPaint.strokeJoin = Paint.Join.ROUND
        grainPaint.isFilterBitmap = true
        for (L in 0..2) {
            path.reset()
            var open = false
            for (i in i0..i1) {
                val lo = max(0, i - 2)
                val hi = min(s.xs.size - 1, i + 2)
                var q = 0f
                for (j in lo..hi) q += s.ps[j]
                q /= (hi - lo + 1)
                val on = q >= thr[L]
                if (on) {
                    if (!open) { path.moveTo(s.xs[i], s.ys[i]); open = true } else path.lineTo(s.xs[i], s.ys[i])
                } else open = false
            }
            if (segmentOnly) {
                // canlı çizimde yalnızca son parça
                path.reset()
                val q = (s.ps[i1] + s.ps[max(0, i1 - 1)]) / 2f
                if (q < thr[L]) continue
                path.moveTo(s.xs[max(0, i1 - 1)], s.ys[max(0, i1 - 1)])
                path.lineTo(s.xs[i1], s.ys[i1])
            }
            grainPaint.strokeWidth = max(1.2f, wBase * wid[L])
            grainPaint.alpha = alp[L]
            for (pass in 0..1) {
                if (pass == 1 && L != 0) continue
                grainPaint.shader = grainShader(graphite, dark, pass)
                grainPaint.alpha = if (pass == 0) alp[L] else alp[L] / 2
                c.drawPath(path, grainPaint)
            }
        }
        grainPaint.shader = null
        grainPaint.alpha = 255
    }

    private fun layeredPen(k: Int) = k == 2 || k == 13 || k == 8 || k == 9 || k == 11 || k == 12

    /** Birden çok katmandan oluşan kalemler: canlı çizimde de çizginin tamamı bitmiş haliyle çizilir; böylece kalem kalkınca görünüm değişmez. */
    fun wholeStrokePen(k: Int) = layeredPen(k)

    private fun drawLayered(c: Canvas, s: Stroke, path: Path?, x0: Float, y0: Float, x1: Float, y1: Float, w: Float) {
        fun line(width: Float, color: Int, dx: Float = 0f, dy: Float = 0f, alpha: Int = 255) {
            linePaint.setColor(color)
            linePaint.alpha = alpha
            linePaint.strokeWidth = width
            if (path != null) {
                c.save(); c.translate(dx, dy); c.drawPath(path, linePaint); c.restore()
            } else {
                c.drawLine(x0 + dx, y0 + dy, x1 + dx, y1 + dy, linePaint)
            }
        }
        val col = s.color
        when (s.kind) {
            8 -> {
                line(w, col)
                line(w * 0.52f, mix(col, Color.WHITE, 0.85f))
            }
            2, 13 -> {
                // Kurşun kalem: kâğıt dokusuna sürtünen grafit; çizgi, benekli bir doku deseninden "boyanır", kenarlar hafif pürüzlü kalır
                val dark = s.kind == 13
                val graphite = mix(col, Color.rgb(55, 55, 60), if (dark) 0.35f else 0.5f)
                // yumuşak, bulanık kenar payı: grafit çizginin çevresine hafifçe yayılır
                linePaint.maskFilter = android.graphics.BlurMaskFilter(max(1f, w * 0.35f), android.graphics.BlurMaskFilter.Blur.NORMAL)
                line(w * 1.25f, graphite, 0f, 0f, if (dark) 46 else 26)
                linePaint.maskFilter = null
                grainPaint.style = Paint.Style.STROKE
                grainPaint.strokeCap = Paint.Cap.ROUND
                grainPaint.strokeJoin = Paint.Join.ROUND
                grainPaint.isFilterBitmap = true
                for (pass in 0..1) {
                    grainPaint.shader = grainShader(graphite, dark, pass)
                    grainPaint.strokeWidth = if (pass == 0) w else w * 0.85f
                    if (path != null) c.drawPath(path, grainPaint) else c.drawLine(x0, y0, x1, y1, grainPaint)
                }
                grainPaint.shader = null
            }
            9 -> {
                // Gölgeli: kayık yumuşak gölge (tek yol olarak çizilir, parçalar üst üste binip noktalanmaz) ve asıl çizgi
                val off = w * 0.4f
                line(w * 1.15f, Color.BLACK, off, off, 28)
                line(w * 0.9f, Color.BLACK, off, off, 42)
                line(w, col)
            }
            11 -> {
                // Boru: koyu kenar, ana renk, üst solda parlak yansıma; üç boyutlu bir boru gibi görünür
                line(w, mix(col, Color.BLACK, 0.45f))
                line(w * 0.8f, col)
                line(w * 0.5f, mix(col, Color.WHITE, 0.3f), -w * 0.06f, -w * 0.08f)
                line(w * 0.17f, mix(col, Color.WHITE, 0.78f), -w * 0.15f, -w * 0.19f)
            }
            12 -> {
                // Neon: dışa doğru açılan ışıma, renkli çekirdek, beyaz iç hat
                line(w * 3.1f, mix(col, Color.WHITE, 0.86f))
                line(w * 2.3f, mix(col, Color.WHITE, 0.68f))
                line(w * 1.6f, mix(col, Color.WHITE, 0.4f))
                line(w * 1.0f, col)
                line(w * 0.38f, mix(col, Color.WHITE, 0.9f))
            }
        }
    }

    /** Son üç parçanın ortalama uzunluğundan hız tahmini: hızlı çizgi 0,4'e, yavaş çizgi 1'e yaklaşır. */
    private fun speedFactor(s: Stroke, i: Int): Float {
        var sum = 0f
        var cnt = 0
        for (j in max(1, i - 2)..i) {
            sum += Math.hypot((s.xs[j] - s.xs[j - 1]).toDouble(), (s.ys[j] - s.ys[j - 1]).toDouble()).toFloat()
            cnt++
        }
        val len = sum / cnt
        return (1f / (1f + len * 0.12f)).coerceIn(0.4f, 1f)
    }

    fun segmentWidth(s: Stroke, i: Int): Float {
        val p = (s.ps[i] + s.ps[i - 1]) / 2f
        var w = s.width * widthFactor(s, p) * kindScale(s.kind)
        when (s.kind) {
            0 -> {
                // Dolma kalem: uç açısı yüzünden çizginin yönüne göre kalınlık değişir; yön, birkaç nokta geriden ölçülür ki kalınlık parçadan parçaya sıçramasın
                val j = max(0, i - 5)
                val dx = s.xs[i] - s.xs[j]
                val dy = s.ys[i] - s.ys[j]
                if (dx != 0f || dy != 0f) {
                    val ang = Math.atan2(dy.toDouble(), dx.toDouble())
                    w *= (0.42 + 0.58 * Math.abs(Math.sin(ang + 0.7))).toFloat() * 0.85f
                }
            }
            3 -> w *= speedFactor(s, i)
            4 -> w *= 0.85f + 0.3f * (1f - speedFactor(s, i))
            5 -> {
                // Sivri uçlu kaligrafi: aşağı inen vuruş kalın, yukarı çıkan ve yatay vuruş kıl gibi ince.
                // yön, birkaç nokta geriden ölçülür: titreşim kalınlığı sıçratmasın
                val j = max(0, i - 6)
                val dx = s.xs[i] - s.xs[j]
                val dy = s.ys[i] - s.ys[j]
                if (dx != 0f || dy != 0f) {
                    val ang = Math.atan2(dy.toDouble(), dx.toDouble())
                    val down = Math.max(0.0, Math.sin(ang))
                    var f = 0.10 + 0.90 * Math.pow(down, 1.4)
                    // çizginin ilk birkaç noktasında yön henüz belli değil; başlangıç noktası (damla) oluşmasın diye ince başlar
                    if (i < 7) f = 0.10 + (f - 0.10) * (i / 7.0)
                    w *= f.toFloat()
                }
            }
        }
        return w
    }

    fun drawPaper(c: Canvas, page: PageData) {
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
            else -> drawTemplate(c, page)
        }
    }

    /** Hazır şablonlar 1200 x 1700 tasarım alanında çizilir; sayfa başka boyuttaysa orantılı küçültülüp ortalanır. */
    private fun drawTemplate(c: Canvas, page: PageData) {
        val k = kotlin.math.min(page.width / DW, page.height / DH)
        c.save()
        c.translate((page.width - DW * k) / 2f, (page.height - DH * k) / 2f)
        c.scale(k, k)
        when (page.paper) {
            Paper.TODO -> drawTodo(c, page)
            Paper.DAILY -> drawDaily(c, page)
            Paper.WEEKLY -> drawWeekly(c, page)
            Paper.MONTHLY -> drawMonthly(c, page)
            Paper.CORNELL -> drawCornell(c, page)
            Paper.YEAR -> drawYear(c, page)
            Paper.COVER -> drawCover(c, page)
            Paper.WATER -> drawWater(c, page)
            Paper.HABIT -> drawHabit(c, page)
            Paper.MOOD -> drawMood(c, page)
            Paper.MEAL -> drawMeal(c, page)
            Paper.BUDGET -> drawBudget(c, page)
            Paper.READING -> drawReading(c, page)
            Paper.GOALS -> drawGoals(c, page)
            Paper.SLEEP -> drawSleep(c, page)
            Paper.STAFF -> drawStaff(c, page)
            Paper.TIMETABLE -> drawTimetable(c, page)
            Paper.LITERATURE -> drawLiterature(c, page)
            Paper.SHOPPING -> drawShopping(c, page)
            else -> {}
        }
        c.restore()
    }

    // ---- Hazır sayfa şablonları (1200 x 1700 sayfa birimine göre; sayfa boyutu farklıysa orantılı çizilir) ----

    private val ink = Color.rgb(60, 60, 70)
    private val soft = Color.rgb(140, 146, 160)
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        setColor(Color.rgb(170, 176, 190))
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rr = RectF()

    private fun label(
        c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int,
        bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT
    ) {
        textPaint.textSize = size
        textPaint.setColor(color)
        textPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textPaint.textAlign = align
        c.drawText(t, x, y, textPaint)
    }

    private fun checkbox(c: Canvas, x: Float, y: Float, size: Float = 44f) {
        rr.set(x, y, x + size, y + size)
        c.drawRoundRect(rr, 8f, 8f, boxPaint)
    }

    private fun header(c: Canvas, text: String) {
        label(c, text, 80f, 120f, 54f, ink, true)
        label(c, "Tarih:", 820f, 116f, 28f, soft)
        c.drawLine(910f, 120f, 1120f, 120f, paperPaint)
        c.drawLine(80f, 150f, 1120f, 150f, boxPaint)
    }

    private fun drawTodo(c: Canvas, page: PageData) {
        header(c, "YAPILACAKLAR")
        var y = 190f
        while (y + 110f < DH - 40f) {
            checkbox(c, 86f, y + 20f, 46f)
            c.drawLine(160f, y + 80f, 1120f, y + 80f, paperPaint)
            y += 110f
        }
    }

    private fun drawDaily(c: Canvas, page: PageData) {
        header(c, "GÜNLÜK AJANDA")
        for (i in 0 until 17) {
            val y = 190f + i * 72f
            label(c, String.format("%02d:00", 6 + i), 80f, y + 46f, 26f, soft)
            c.drawLine(190f, y + 72f, 740f, y + 72f, paperPaint)
        }
        for (k in 0 until 3) {
            c.drawLine(80f, 1480f + k * 70f, 740f, 1480f + k * 70f, paperPaint)
        }
        label(c, "Günün notu", 80f, 1450f, 24f, soft)
        c.drawLine(770f, 175f, 770f, DH - 70f, boxPaint)

        label(c, "ÖNCELİKLER", 800f, 215f, 28f, ink, true)
        for (k in 0 until 3) {
            val y = 240f + k * 72f
            checkbox(c, 800f, y + 8f)
            c.drawLine(860f, y + 54f, 1120f, y + 54f, paperPaint)
        }
        label(c, "YAPILACAKLAR", 800f, 520f, 28f, ink, true)
        for (k in 0 until 7) {
            val y = 545f + k * 72f
            checkbox(c, 800f, y + 8f)
            c.drawLine(860f, y + 54f, 1120f, y + 54f, paperPaint)
        }
        label(c, "NOTLAR", 800f, 1100f, 28f, ink, true)
        var y = 1150f
        while (y < DH - 60f) {
            c.drawLine(800f, y, 1120f, y, paperPaint)
            y += 58f
        }
    }

    private fun drawWeekly(c: Canvas, page: PageData) {
        label(c, "HAFTALIK PLAN", 80f, 120f, 54f, ink, true)
        label(c, "Hafta:", 820f, 116f, 28f, soft)
        c.drawLine(900f, 120f, 1120f, 120f, paperPaint)
        val names = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar", "Notlar")
        val bw = 520f
        val bh = (DH - 180f - 80f - 3 * 20f) / 4f
        for (i in names.indices) {
            val x = 70f + (i % 2) * (bw + 20f)
            val y = 180f + (i / 2) * (bh + 20f)
            rr.set(x, y, x + bw, y + bh)
            c.drawRoundRect(rr, 14f, 14f, boxPaint)
            label(c, names[i], x + 18f, y + 40f, 30f, ink, true)
            c.drawLine(x + 12f, y + 54f, x + bw - 12f, y + 54f, boxPaint)
            var ly = y + 106f
            while (ly < y + bh - 8f) {
                c.drawLine(x + 18f, ly, x + bw - 18f, ly, paperPaint)
                ly += 52f
            }
        }
    }

    private fun monthShape(year: Int, month: Int): Pair<Int, Int> {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, 1)
        val off = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        return Pair(off, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    }

    private fun drawMonthly(c: Canvas, page: PageData) {
        // yıl ve ay belirtilmemişse içinde bulunulan ay gösterilir; böylece gün sayıları her zaman çıkar
        val nowCal = Calendar.getInstance()
        val calY = if (page.calYear > 0) page.calYear else nowCal.get(Calendar.YEAR)
        val calM = if (page.calMonth in 1..12) page.calMonth else nowCal.get(Calendar.MONTH) + 1
        val dated = true
        val title = Planner.MONTHS[calM - 1] + " " + calY
        label(c, title, 70f, 125f, 60f, ink, true)
        val x0 = 70f
        val cw = 1060f / 7f
        val top = 190f
        val headH = 50f
        val rh = (DH - 70f - top - headH) / 6f
        fillPaint.setColor(Color.rgb(238, 240, 244))
        c.drawRect(x0, top, x0 + 1060f, top + headH, fillPaint)
        val days = Planner.DAYS
        for (i in 0 until 7) label(c, days[i], x0 + cw * (i + 0.5f), top + 34f, 26f, soft, false, Paint.Align.CENTER)
        val gridTop = top + headH
        for (r in 0..6) c.drawLine(x0, gridTop + r * rh, x0 + 1060f, gridTop + r * rh, boxPaint)
        c.drawLine(x0, top, x0 + 1060f, top, boxPaint)
        for (i in 0..7) c.drawLine(x0 + i * cw, top, x0 + i * cw, gridTop + 6 * rh, boxPaint)
        if (dated) {
            val (off, n) = monthShape(calY, calM)
            for (d in 1..n) {
                val idx = off + d - 1
                label(c, d.toString(), x0 + (idx % 7) * cw + 10f, gridTop + (idx / 7) * rh + 34f, 26f, ink)
            }
        }
    }

    private fun drawCornell(c: Canvas, page: PageData) {
        label(c, "Konu:", 80f, 110f, 30f, soft)
        c.drawLine(170f, 114f, 1120f, 114f, paperPaint)
        val bottom = DH - 300f
        c.drawLine(70f, 150f, 1130f, 150f, boxPaint)
        c.drawLine(70f, bottom, 1130f, bottom, boxPaint)
        c.drawLine(380f, 150f, 380f, bottom, boxPaint)
        label(c, "Anahtar kelimeler", 85f, 195f, 24f, soft)
        label(c, "Notlar", 410f, 195f, 24f, soft)
        label(c, "Özet", 85f, bottom + 40f, 24f, soft)
    }

    // ---- Ek sayfa türleri (v0.25) ----

    private fun title(c: Canvas, text: String, field: String? = "Ay:") {
        label(c, text, 80f, 120f, 54f, ink, true)
        if (field != null) {
            label(c, field, 820f, 116f, 28f, soft)
            c.drawLine(900f, 120f, 1120f, 120f, paperPaint)
        }
        c.drawLine(80f, 150f, 1120f, 150f, boxPaint)
    }

    private fun band(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        fillPaint.setColor(color)
        rr.set(x, y, x + w, y + h)
        c.drawRoundRect(rr, 10f, 10f, fillPaint)
    }

    private fun ring(c: Canvas, cx: Float, cy: Float, r: Float) {
        c.drawCircle(cx, cy, r, boxPaint)
    }

    private fun box(c: Canvas, x: Float, y: Float, w: Float, h: Float, name: String?) {
        rr.set(x, y, x + w, y + h)
        c.drawRoundRect(rr, 14f, 14f, boxPaint)
        if (name != null) label(c, name, x + 18f, y + 36f, 26f, ink, true)
    }

    private fun drawWater(c: Canvas, page: PageData) {
        title(c, "SU TAKİBİ")
        label(c, "Günlük hedef:", 80f, 196f, 26f, soft)
        c.drawLine(260f, 198f, 420f, 198f, paperPaint)
        label(c, "bardak", 430f, 196f, 26f, soft)
        val top = 230f
        val rh = 44f
        band(c, 70f, top - 4f, 1060f, 40f, Color.rgb(226, 238, 248))
        label(c, "Gün", 90f, top + 26f, 24f, ink, true)
        label(c, "Bardaklar", 170f, top + 26f, 24f, ink, true)
        label(c, "Toplam", 930f, top + 26f, 24f, ink, true)
        for (d in 1..31) {
            val y = top + 40f + (d - 1) * rh
            label(c, d.toString(), 100f, y + 30f, 24f, ink)
            for (g in 0 until 8) ring(c, 190f + g * 88f, y + 22f, 15f)
            c.drawLine(900f, y + rh, 1120f, y + rh, paperPaint)
            c.drawLine(80f, y + rh, 880f, y + rh, paperPaint)
        }
        label(c, "Not:", 80f, DH - 60f, 26f, soft)
        c.drawLine(140f, DH - 58f, 1120f, DH - 58f, paperPaint)
    }

    private fun drawHabit(c: Canvas, page: PageData) {
        title(c, "ALIŞKANLIK TAKVİMİ")
        val top = 180f
        val x0 = 70f
        val dayW = 90f
        val cw = (1060f - dayW) / 8f
        band(c, x0, top, 1060f, 120f, Color.rgb(238, 242, 232))
        label(c, "Gün", x0 + 14f, top + 70f, 24f, ink, true)
        val rh = 40f
        val bottom = top + 120f + 31 * rh
        for (i in 0..8) {
            val x = x0 + dayW + i * cw
            c.drawLine(x, top, x, bottom, boxPaint)
        }
        c.drawLine(x0, top, x0, bottom, boxPaint)
        c.drawLine(x0, top, x0 + 1060f, top, boxPaint)
        c.drawLine(x0, top + 120f, x0 + 1060f, top + 120f, boxPaint)
        for (d in 1..31) {
            val y = top + 120f + d * rh
            label(c, d.toString(), x0 + dayW / 2f, y - 11f, 22f, ink, false, Paint.Align.CENTER)
            c.drawLine(x0, y, x0 + 1060f, y, paperPaint)
        }
        c.drawLine(x0, bottom, x0 + 1060f, bottom, boxPaint)
        label(c, "Alışkanlık adlarını üst kutulara yazın; yaptığınız günü işaretleyin.", 80f, DH - 60f, 24f, soft)
    }

    private fun drawMood(c: Canvas, page: PageData) {
        title(c, "RUH HÂLİ TAKVİMİ", "Yıl:")
        val cols = intArrayOf(
            Color.rgb(250, 214, 120), Color.rgb(176, 214, 150), Color.rgb(150, 198, 226),
            Color.rgb(214, 170, 214), Color.rgb(236, 150, 150)
        )
        for (i in 0 until 5) {
            val x = 90f + i * 210f
            fillPaint.setColor(cols[i])
            c.drawCircle(x + 16f, 205f, 16f, fillPaint)
            c.drawLine(x + 42f, 214f, x + 190f, 214f, paperPaint)
        }
        val top = 250f
        val x0 = 70f
        val dayW = 70f
        val cw = (1060f - dayW) / 12f
        val rh = 44f
        val names = arrayOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")
        for (m in 0 until 12) label(c, names[m], x0 + dayW + m * cw + cw / 2f, top + 28f, 22f, ink, true, Paint.Align.CENTER)
        for (d in 1..31) {
            val y = top + 40f + (d - 1) * rh
            label(c, d.toString(), x0 + 30f, y + 30f, 20f, soft, false, Paint.Align.CENTER)
            for (m in 0 until 12) ring(c, x0 + dayW + m * cw + cw / 2f, y + 22f, 16f)
        }
    }

    private fun drawMeal(c: Canvas, page: PageData) {
        title(c, "YEMEK PLANI", "Hafta:")
        val days = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
        val heads = arrayOf("Kahvaltı", "Öğle", "Akşam", "Ara öğün")
        val x0 = 70f
        val dw = 170f
        val cw = (1060f - dw) / 4f
        val top = 180f
        band(c, x0, top, 1060f, 50f, Color.rgb(246, 236, 224))
        for (i in heads.indices) label(c, heads[i], x0 + dw + i * cw + cw / 2f, top + 34f, 24f, ink, true, Paint.Align.CENTER)
        val rh = 140f
        for (r in 0 until 7) {
            val y = top + 50f + r * rh
            label(c, days[r], x0 + 14f, y + 44f, 24f, ink, true)
            c.drawLine(x0, y, x0 + 1060f, y, boxPaint)
        }
        val bottom = top + 50f + 7 * rh
        c.drawLine(x0, bottom, x0 + 1060f, bottom, boxPaint)
        for (i in 0..4) {
            val x = x0 + if (i == 0) 0f else dw + (i - 1) * cw
            c.drawLine(x, top, x, bottom, boxPaint)
        }
        c.drawLine(x0 + 1060f, top, x0 + 1060f, bottom, boxPaint)
        label(c, "ALIŞVERİŞ LİSTESİ", 80f, bottom + 50f, 28f, ink, true)
        for (k in 0 until 5) {
            val y = bottom + 70f + k * 56f
            for (col in 0 until 2) {
                val x = 80f + col * 540f
                checkbox(c, x, y + 4f, 36f)
                c.drawLine(x + 50f, y + 40f, x + 500f, y + 40f, paperPaint)
            }
        }
    }

    private fun drawBudget(c: Canvas, page: PageData) {
        title(c, "GİDER TAKİBİ")
        val w = 330f
        val labels = arrayOf("Gelir", "Gider", "Kalan")
        for (i in 0 until 3) box(c, 70f + i * (w + 35f), 180f, w, 100f, labels[i])
        val top = 320f
        val xs = floatArrayOf(70f, 230f, 700f, 930f, 1130f)
        band(c, 70f, top, 1060f, 46f, Color.rgb(232, 238, 232))
        val heads = arrayOf("Tarih", "Açıklama", "Kategori", "Tutar")
        for (i in heads.indices) label(c, heads[i], xs[i] + 14f, top + 32f, 24f, ink, true)
        val rh = 46f
        val n = 27
        val bottom = top + 46f + n * rh
        for (r in 1..n) c.drawLine(70f, top + 46f + r * rh, 1130f, top + 46f + r * rh, paperPaint)
        for (x in xs) c.drawLine(x, top, x, bottom, boxPaint)
        c.drawLine(70f, top, 1130f, top, boxPaint)
        c.drawLine(70f, bottom, 1130f, bottom, boxPaint)
        label(c, "Toplam:", 800f, bottom + 44f, 28f, ink, true)
        c.drawLine(930f, bottom + 46f, 1130f, bottom + 46f, paperPaint)
    }

    private fun drawReading(c: Canvas, page: PageData) {
        title(c, "OKUMA GÜNLÜĞÜ", null)
        val fields = arrayOf("Başlık", "Yazar", "Başladığım tarih", "Bitirdiğim tarih", "Sayfa sayısı")
        for ((i, f) in fields.withIndex()) {
            val y = 205f + i * 62f
            label(c, f + ":", 80f, y, 26f, soft)
            c.drawLine(340f, y + 4f, 1120f, y + 4f, paperPaint)
        }
        label(c, "Puanım", 80f, 540f, 26f, soft)
        for (i in 0 until 5) ring(c, 280f + i * 60f, 530f, 20f)
        box(c, 70f, 580f, 1060f, 300f, "Kitabın özeti")
        box(c, 70f, 900f, 1060f, 420f, "Alıntılar ve sayfa numaraları")
        box(c, 70f, 1340f, 1060f, 310f, "Düşüncelerim")
    }

    private fun drawGoals(c: Canvas, page: PageData) {
        title(c, "HEDEFLER", "Tarih:")
        box(c, 70f, 180f, 1060f, 150f, "Ana hedefim")
        label(c, "Son tarih:", 80f, 372f, 26f, soft)
        c.drawLine(230f, 376f, 520f, 376f, paperPaint)
        label(c, "Neden önemli?", 580f, 372f, 26f, soft)
        c.drawLine(790f, 376f, 1120f, 376f, paperPaint)
        for (b in 0 until 3) {
            val y = 420f + b * 290f
            band(c, 70f, y, 1060f, 46f, Color.rgb(240, 232, 244))
            label(c, "Alt hedef " + (b + 1), 90f, y + 32f, 26f, ink, true)
            for (k in 0 until 3) {
                val ly = y + 56f + k * 74f
                checkbox(c, 90f, ly + 12f, 40f)
                c.drawLine(150f, ly + 56f, 1110f, ly + 56f, paperPaint)
            }
        }
        box(c, 70f, 1300f, 520f, 340f, "Olası engeller")
        box(c, 610f, 1300f, 520f, 340f, "Çözümler ve destek")
    }

    private fun drawSleep(c: Canvas, page: PageData) {
        title(c, "UYKU TAKİBİ")
        val top = 190f
        val xs = floatArrayOf(70f, 170f, 400f, 630f, 790f, 1130f)
        val heads = arrayOf("Gün", "Yattım", "Kalktım", "Süre", "Uyku kalitesi")
        band(c, 70f, top, 1060f, 46f, Color.rgb(228, 232, 246))
        for (i in heads.indices) label(c, heads[i], xs[i] + 14f, top + 32f, 24f, ink, true)
        val rh = 44f
        for (d in 1..31) {
            val y = top + 46f + (d - 1) * rh
            label(c, d.toString(), 100f, y + 30f, 22f, ink)
            c.drawLine(70f, y + rh, 1130f, y + rh, paperPaint)
            for (q in 0 until 5) ring(c, 830f + q * 62f, y + 22f, 14f)
        }
        val bottom = top + 46f + 31 * rh
        for (x in xs) c.drawLine(x, top, x, bottom, boxPaint)
        c.drawLine(70f, top, 1130f, top, boxPaint)
        c.drawLine(70f, bottom, 1130f, bottom, boxPaint)
    }

    private fun drawStaff(c: Canvas, page: PageData) {
        val n = 11
        for (s in 0 until n) {
            val y = 150f + s * 145f
            for (l in 0 until 5) c.drawLine(70f, y + l * 14f, 1130f, y + l * 14f, boxPaint)
            c.drawLine(70f, y, 70f, y + 56f, boxPaint)
            c.drawLine(1130f, y, 1130f, y + 56f, boxPaint)
        }
    }

    private fun drawTimetable(c: Canvas, page: PageData) {
        title(c, "DERS PROGRAMI", "Dönem:")
        val days = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma")
        val x0 = 70f
        val tw = 150f
        val cw = (1060f - tw) / 5f
        val top = 180f
        band(c, x0, top, 1060f, 50f, Color.rgb(232, 240, 236))
        for (i in days.indices) label(c, days[i], x0 + tw + i * cw + cw / 2f, top + 34f, 24f, ink, true, Paint.Align.CENTER)
        val rh = 140f
        for (r in 0 until 10) {
            val y = top + 50f + r * rh
            label(c, String.format("%02d:00", 8 + r), x0 + 14f, y + 36f, 22f, soft)
            c.drawLine(x0, y, x0 + 1060f, y, paperPaint)
        }
        val bottom = top + 50f + 10 * rh
        c.drawLine(x0, bottom, x0 + 1060f, bottom, boxPaint)
        for (i in 0..5) {
            val x = x0 + if (i == 0) 0f else tw + (i - 1) * cw
            c.drawLine(x, top, x, bottom, boxPaint)
        }
        c.drawLine(x0 + 1060f, top, x0 + 1060f, bottom, boxPaint)
        c.drawLine(x0, top, x0 + 1060f, top, boxPaint)
    }

    private fun drawLiterature(c: Canvas, page: PageData) {
        title(c, "LİTERATÜR NOTU", "Tarih:")
        label(c, "Kaynak (APA):", 80f, 200f, 26f, soft)
        c.drawLine(300f, 204f, 1120f, 204f, paperPaint)
        c.drawLine(80f, 262f, 1120f, 262f, paperPaint)
        box(c, 70f, 290f, 520f, 200f, "Araştırma sorusu")
        box(c, 610f, 290f, 520f, 200f, "Kuramsal çerçeve")
        box(c, 70f, 510f, 520f, 240f, "Yöntem ve örneklem")
        box(c, 610f, 510f, 520f, 240f, "Temel bulgular")
        box(c, 70f, 770f, 1060f, 330f, "Önemli alıntılar (sayfa no ile)")
        box(c, 70f, 1120f, 520f, 260f, "Eleştirel değerlendirme")
        box(c, 610f, 1120f, 520f, 260f, "Çalışmama katkısı")
        label(c, "Anahtar kavramlar:", 80f, 1440f, 26f, soft)
        c.drawLine(330f, 1444f, 1120f, 1444f, paperPaint)
        c.drawLine(80f, 1510f, 1120f, 1510f, paperPaint)
        label(c, "Dönüp bakılacak kaynaklar:", 80f, 1580f, 26f, soft)
        c.drawLine(420f, 1584f, 1120f, 1584f, paperPaint)
        c.drawLine(80f, 1650f, 1120f, 1650f, paperPaint)
    }

    private fun drawShopping(c: Canvas, page: PageData) {
        title(c, "ALIŞVERİŞ LİSTESİ", "Tarih:")
        for (col in 0 until 2) {
            val x = 70f + col * 540f
            for (r in 0 until 22) {
                val y = 180f + r * 64f
                checkbox(c, x + 6f, y + 8f, 38f)
                c.drawLine(x + 60f, y + 50f, x + 510f, y + 50f, paperPaint)
            }
        }
        label(c, "Bütçe:", 80f, DH - 60f, 26f, soft)
        c.drawLine(190f, DH - 58f, 480f, DH - 58f, paperPaint)
        label(c, "Harcanan:", 620f, DH - 60f, 26f, soft)
        c.drawLine(780f, DH - 58f, 1120f, DH - 58f, paperPaint)
    }

    private val boxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Planlayıcı kapağı: büyük renkli yıl rakamları, el yazısı başlık ve yumuşak dalgalar. */
    private fun drawCover(c: Canvas, page: PageData) {
        val year = if (page.calYear > 0) page.calYear else Calendar.getInstance().get(Calendar.YEAR)
        val w = DW
        // yumuşak arka plan dalgaları
        boxFill.setColor(Color.argb(60, 240, 190, 190))
        val wave = Path()
        wave.moveTo(0f, 0f)
        wave.lineTo(w * 0.62f, 0f)
        wave.cubicTo(w * 0.40f, 300f, w * 0.78f, 520f, w * 0.30f, 900f)
        wave.cubicTo(w * 0.18f, 1000f, w * 0.10f, 1100f, 0f, 1200f)
        wave.close()
        c.drawPath(wave, boxFill)
        boxFill.setColor(Color.argb(45, 245, 215, 190))
        val wave2 = Path()
        wave2.moveTo(w, DH)
        wave2.lineTo(w * 0.35f, DH)
        wave2.cubicTo(w * 0.55f, DH - 260f, w * 0.85f, DH - 300f, w, DH - 560f)
        wave2.close()
        c.drawPath(wave2, boxFill)
        val digits = year.toString().padStart(4, '0')
        val cols = intArrayOf(Color.rgb(214, 70, 60), Color.rgb(224, 150, 110), Color.rgb(228, 168, 52), Color.rgb(104, 130, 150))
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 430f
        textPaint.textAlign = Paint.Align.LEFT
        for (i in 0 until 4) {
            val row = i / 2
            val col = i % 2
            textPaint.setColor(cols[i])
            c.drawText(digits[i].toString(), 250f + col * 270f, 700f + row * 400f, textPaint)
        }
        textPaint.typeface = Fonts.get(3, Typeface.BOLD)
        textPaint.setColor(Color.rgb(70, 50, 60))
        textPaint.textSize = 170f
        c.drawText("Planlayıcı", 230f, 1500f, textPaint)
    }

    private fun drawYear(c: Canvas, page: PageData) {
        val calY = if (page.calYear > 0) page.calYear else Calendar.getInstance().get(Calendar.YEAR)
        val dated = true
        label(c, "YILLIK TAKVİM $calY", 80f, 125f, 56f, ink, true)
        val letters = arrayOf("P", "S", "Ç", "P", "C", "C", "P")
        for (m in 0 until 12) {
            val bx = 75f + (m % 3) * 350f
            val by = 200f + (m / 3) * 345f
            // her ayın başlığı kendi renginde yuvarlak bir şerit
            boxFill.setColor(Planner.MONTH_COLORS[m])
            rr.set(bx, by - 4f, bx + 335f, by + 44f)
            c.drawRoundRect(rr, 12f, 12f, boxFill)
            label(c, Planner.MONTHS[m], bx + 167f, by + 33f, 30f, Color.WHITE, true, Paint.Align.CENTER)
            for (i in 0 until 7) label(c, letters[i], bx + 25f + i * 50f, by + 76f, 20f, soft, false, Paint.Align.CENTER)
            if (dated) {
                val (off, n) = monthShape(calY, m + 1)
                for (d in 1..n) {
                    val idx = off + d - 1
                    label(c, d.toString(), bx + 25f + (idx % 7) * 50f, by + 118f + (idx / 7) * 42f, 22f, ink, false, Paint.Align.CENTER)
                }
            }
        }
    }

    private fun hash(i: Int, salt: Int = 0): Float {
        var h = i * 374761393 + salt * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f
    }

    /** i. noktadaki kalınlık: son birkaç parçanın ortalaması; kalınlık noktadan noktaya sıçramaz, çizgi pürüzsüz kalınlaşır / incelir. */
    private fun pointWidth(s: Stroke, i: Int): Float {
        var sum = 0f
        var cnt = 0
        val hi = min(max(1, i), s.xs.size - 1)
        for (j in max(1, i - 3)..hi) { sum += segmentWidth(s, j); cnt++ }
        return if (cnt == 0) s.width else sum / cnt
    }

    /** Dolu, kalınlığı değişen çizgi parçası: iki uçta farklı yarıçaplı daire ve aradaki yamuk tek renkte birleşir; dikiş ve topak oluşmaz. */
    private fun capsule(c: Canvas, x0: Float, y0: Float, r0: Float, x1: Float, y1: Float, r1: Float) {
        c.drawCircle(x0, y0, r0, fillPaint)
        c.drawCircle(x1, y1, r1, fillPaint)
        val dx = x1 - x0
        val dy = y1 - y0
        val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len < 0.01f) return
        val nx = -dy / len
        val ny = dx / len
        path.reset()
        path.moveTo(x0 + nx * r0, y0 + ny * r0)
        path.lineTo(x1 + nx * r1, y1 + ny * r1)
        path.lineTo(x1 - nx * r1, y1 - ny * r1)
        path.lineTo(x0 - nx * r0, y0 - ny * r0)
        path.close()
        c.drawPath(path, fillPaint)
    }

    /** Çizginin başında ve (yeniden çizilirken) sonunda kalınlık çarpanı. */
    private fun endTaper(s: Stroke, i: Int, last: Boolean): Float {
        // başlangıç uzunluğu (kalem kalınlığının katı), başlangıç oranı, bitiş uzunluğu, bitiş oranı
        val p: FloatArray = when (s.kind) {
            0 -> floatArrayOf(4f, 0.45f, 6f, 0.2f)
            1 -> floatArrayOf(1f, 0.8f, 2f, 0.6f)
            2 -> floatArrayOf(5f, 0.25f, 8f, 0.15f)
            13 -> floatArrayOf(8f, 0.2f, 8f, 0.15f)
            3 -> floatArrayOf(6f, 0.15f, 14f, 0.05f)
            4 -> floatArrayOf(1f, 0.85f, 3f, 0.55f)
            14 -> floatArrayOf(1.5f, 0.7f, 3f, 0.4f)
            5 -> floatArrayOf(1f, 1f, 6f, 0.2f)
            else -> return 1f
        }
        val w = max(1f, s.width)
        var t = min(1f, p[1] + (1f - p[1]) * s.arc(i) / (p[0] * w))
        // Bitiş inceltmesi bilerek yok: kalem kalkınca çizgi değişmesin; incelme basınç düşüşünden gelir
        return t
    }

    /** Kalem çizgisinin i. parçasını (i-1 ile i arası) türüne göre çizer. */
    fun drawPenSegment(c: Canvas, s: Stroke, i: Int, last: Boolean) {
        if (!s.highlighter && (s.kind == 2 || s.kind == 13)) {
            pencilLayers(c, s, i, i, true)
            return
        }
        if (!s.highlighter && (s.kind == 0 || s.kind == 1 || s.kind == 3 || s.kind == 4 || s.kind == 5 || s.kind == 14)) {
            fillPaint.style = Paint.Style.FILL
            fillPaint.setColor(s.color)
            fillPaint.alpha = 255
            val r0 = pointWidth(s, i - 1) * endTaper(s, i - 1, last) / 2f
            val r1 = pointWidth(s, i) * endTaper(s, i, last) / 2f
            capsule(c, s.xs[i - 1], s.ys[i - 1], max(0.3f, r0), s.xs[i], s.ys[i], max(0.3f, r1))
            return
        }
        val x0 = s.xs[i - 1]
        val y0 = s.ys[i - 1]
        val x1 = s.xs[i]
        val y1 = s.ys[i]
        var w = segmentWidth(s, i)
        linePaint.setColor(s.color)
        linePaint.alpha = 255
        linePaint.strokeCap = Paint.Cap.ROUND
        when (s.kind) {
            2 -> drawLayered(c, s, null, x0, y0, x1, y1, w)
            15 -> {
                // Geniş uçlu kaligrafi: sabit açılı, düz uç; çizgi uca paralelken incelir, dik giderken kalınlaşır.
                val half = s.width * 1.0f
                val a = -0.7f
                val ux = Math.cos(a.toDouble()).toFloat() * half
                val uy = Math.sin(a.toDouble()).toFloat() * half
                // uç çizgiye paralelken çizgi tamamen kaybolmasın diye ince bir taban çizgi
                linePaint.setColor(s.color)
                linePaint.strokeWidth = s.width * 0.05f
                c.drawLine(x0, y0, x1, y1, linePaint)
                fillPaint.setColor(s.color)
                fillPaint.alpha = 255
                // parçalar arası ince çizgilenme olmasın diye kenarlar hafifçe taşırılır
                fillPaint.style = Paint.Style.FILL_AND_STROKE
                fillPaint.strokeWidth = 1.4f
                fillPaint.strokeJoin = Paint.Join.ROUND
                path.reset()
                path.moveTo(x0 - ux, y0 - uy)
                path.lineTo(x0 + ux, y0 + uy)
                path.lineTo(x1 + ux, y1 + uy)
                path.lineTo(x1 - ux, y1 - uy)
                path.close()
                c.drawPath(path, fillPaint)
                fillPaint.style = Paint.Style.FILL
            }
            10 -> {
                // Gökkuşağı: yay uzunluğuna göre renk döner
                val hsv = floatArrayOf(((s.arc(i) * 0.55f) % 360f + 360f) % 360f, 0.82f, 1f)
                linePaint.setColor(Color.HSVToColor(hsv))
                linePaint.strokeWidth = w
                c.drawLine(x0, y0, x1, y1, linePaint)
            }
            11, 12 -> drawLayered(c, s, null, x0, y0, x1, y1, w)
            3 -> {
                // Fırça: başta ve sonda incelen çizgi
                val n = s.xs.size
                var t = minOf(1f, 0.25f + i / 10f)
                if (last) t = minOf(t, 0.25f + (n - 1 - i) / 10f)
                w *= t
                linePaint.strokeWidth = w
                c.drawLine(x0, y0, x1, y1, linePaint)
            }
            6 -> {
                // Kesikli kalem: yay uzunluğuna göre eşit çizgi ve boşluklar
                val bw = s.width * widthFactor(s, 0.5f)
                val dash = bw * 3.2f + 6f
                val per = dash + bw * 2.4f + 6f
                val a0 = s.arc(i - 1)
                val a1 = s.arc(i)
                val len = a1 - a0
                linePaint.strokeWidth = bw
                if (len > 0f) {
                    var k = Math.floor((a0 / per).toDouble()).toInt()
                    while (k * per <= a1) {
                        val ds = maxOf(k * per, a0)
                        val de = minOf(k * per + dash, a1)
                        if (de > ds) {
                            val t0 = (ds - a0) / len
                            val t1 = (de - a0) / len
                            c.drawLine(x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0, x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1, linePaint)
                        }
                        k++
                    }
                }
            }
            7 -> {
                // Noktalı kalem: yay uzunluğu boyunca eşit aralıklı yuvarlak noktalar
                val bw = s.width * widthFactor(s, 0.5f)
                val per = bw * 2.3f + 4f
                val a0 = s.arc(i - 1)
                val a1 = s.arc(i)
                val len = a1 - a0
                fillPaint.setColor(s.color)
                if (len > 0f) {
                    var k = Math.floor((a0 / per).toDouble()).toInt() + 1
                    while (k * per <= a1) {
                        val t = (k * per - a0) / len
                        c.drawCircle(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, bw * 0.5f, fillPaint)
                        k++
                    }
                }
            }
            8 -> drawLayered(c, s, null, x0, y0, x1, y1, w)
            9 -> {
                // Gölgeli kalem: kayık, yumuşak bir gölge ve üstünde asıl çizgi
                val off = w * 0.4f
                linePaint.alpha = 70
                linePaint.setColor(Color.argb(70, 0, 0, 0))
                linePaint.strokeWidth = w
                c.drawLine(x0 + off, y0 + off, x1 + off, y1 + off, linePaint)
                linePaint.setColor(s.color)
                linePaint.alpha = 255
                linePaint.strokeWidth = w
                c.drawLine(x0, y0, x1, y1, linePaint)
            }
            0 -> {
                // Dolma: çizginin sonunda mürekkep topağı bırakmadan incelir
                if (last) {
                    val n = s.xs.size
                    w *= min(1f, 0.45f + (n - 1 - i) / 7f)
                }
                linePaint.strokeWidth = w
                c.drawLine(x0, y0, x1, y1, linePaint)
            }
            else -> {
                linePaint.strokeWidth = w
                c.drawLine(x0, y0, x1, y1, linePaint)
            }
        }
    }

    private fun stamp(c: Canvas, kind: Int, x: Float, y: Float, r: Float, color: Int) {
        fillPaint.setColor(color)
        fillPaint.alpha = 215
        path.reset()
        if (kind == 2) {
            for (i in 0 until 10) {
                val rr = if (i % 2 == 0) r else r * 0.45f
                val a = Math.toRadians(-90.0 + i * 36.0)
                val px = x + rr * Math.cos(a).toFloat()
                val py = y + rr * Math.sin(a).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
        } else {
            val sz = r * 0.9f
            path.moveTo(x, y + sz * 0.9f)
            path.cubicTo(x - sz * 1.5f, y - sz * 0.1f, x - sz * 0.7f, y - sz * 1.1f, x, y - sz * 0.4f)
            path.cubicTo(x + sz * 0.7f, y - sz * 1.1f, x + sz * 1.5f, y - sz * 0.1f, x, y + sz * 0.9f)
            path.close()
        }
        c.drawPath(path, fillPaint)
        fillPaint.alpha = 255
    }

    private fun drawHighlighter(c: Canvas, s: Stroke) {
        val n = s.xs.size
        linePaint.setColor(s.color)
        linePaint.strokeWidth = s.width
        if (s.kind == 2 || s.kind == 3) {
            // Şekilli fosforlu: çizgi boyunca yıldız ya da kalp damgaları
            val step = s.width * 0.95f
            var carry = 0f
            stamp(c, s.kind, s.xs[0], s.ys[0], s.width * 0.55f, s.color)
            for (i in 1 until n) {
                val dx = s.xs[i] - s.xs[i - 1]
                val dy = s.ys[i] - s.ys[i - 1]
                val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (len == 0f) continue
                var d = step - carry
                while (d <= len) {
                    val t = d / len
                    stamp(c, s.kind, s.xs[i - 1] + dx * t, s.ys[i - 1] + dy * t, s.width * 0.55f, s.color)
                    d += step
                }
                carry = len - (d - step)
            }
            return
        }
        linePaint.alpha = if (s.kind == 1) 85 else 110
        linePaint.strokeCap = Paint.Cap.SQUARE
        path.reset()
        path.moveTo(s.xs[0], s.ys[0])
        if (n == 1) path.lineTo(s.xs[0] + 0.1f, s.ys[0])
        for (i in 1 until n) path.lineTo(s.xs[i], s.ys[i])
        c.drawPath(path, linePaint)
        linePaint.alpha = 255
        linePaint.strokeCap = Paint.Cap.ROUND
        if (s.kind == 1) {
            // Pırıltılı fosforlu: bandın üstüne beyaz ve altın ışıltılar
            var k = 0
            var carry = 0f
            val step = s.width * 0.45f
            for (i in 1 until n) {
                val dx = s.xs[i] - s.xs[i - 1]
                val dy = s.ys[i] - s.ys[i - 1]
                val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (len == 0f) continue
                var d = step - carry
                while (d <= len) {
                    val t = d / len
                    val off = (hash(k, 3) - 0.5f) * s.width * 0.9f
                    val px = s.xs[i - 1] + dx * t - dy / len * off
                    val py = s.ys[i - 1] + dy * t + dx / len * off
                    val big = hash(k, 5) > 0.75f
                    fillPaint.setColor(if (hash(k, 9) > 0.5f) Color.WHITE else Color.rgb(255, 214, 120))
                    c.drawCircle(px, py, if (big) s.width * 0.09f else s.width * 0.05f, fillPaint)
                    k++
                    d += step
                }
                carry = len - (d - step)
            }
        }
    }

    fun drawStroke(c: Canvas, s: Stroke) {
        val n = s.xs.size
        if (n == 0) return
        if (s.highlighter) {
            drawHighlighter(c, s)
            return
        }
        linePaint.setColor(s.color)
        linePaint.alpha = 255
        linePaint.strokeCap = Paint.Cap.ROUND
        if (n == 1) {
            if (s.kind == 15) {
                val half = s.width * 1.5f
                linePaint.setColor(s.color)
                linePaint.strokeWidth = s.width * 0.3f
                c.drawLine(
                    s.xs[0] - Math.cos(-0.7) .toFloat() * half, s.ys[0] - Math.sin(-0.7).toFloat() * half,
                    s.xs[0] + Math.cos(-0.7).toFloat() * half, s.ys[0] + Math.sin(-0.7).toFloat() * half, linePaint
                )
                return
            }
            fillPaint.setColor(s.color)
            c.drawCircle(s.xs[0], s.ys[0], s.width * widthFactor(s, s.ps[0]) * 0.5f, fillPaint)
            return
        }
        if (s.kind == 2 || s.kind == 13) {
            pencilLayers(c, s, 0, n - 1, false)
            return
        }
        if (layeredPen(s.kind)) {
            var sp = 0f
            for (i in 1 until n) sp += s.ps[i]
            val p = if (n > 1) sp / (n - 1) else 0.5f
            val w = s.width * widthFactor(s, p) * kindScale(s.kind)
            path.reset()
            path.moveTo(s.xs[0], s.ys[0])
            for (i in 1 until n) path.lineTo(s.xs[i], s.ys[i])
            drawLayered(c, s, path, 0f, 0f, 0f, 0f, w)
            linePaint.setColor(s.color)
            linePaint.alpha = 255
            return
        }
        for (i in 1 until n) drawPenSegment(c, s, i, true)
    }

    // ---- Sayfa nesneleri: metin, çıkartma, resim ----

    private val itemText = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)

    private fun layoutOf(it: PageItem): android.text.StaticLayout {
        itemText.setColor(it.color)
        itemText.textSize = it.size
        val flags = if (it.type == PageItem.TEXT) it.kind else 0
        val style = (if (flags and 256 != 0) android.graphics.Typeface.BOLD else 0) or
            (if (flags and 512 != 0) android.graphics.Typeface.ITALIC else 0)
        itemText.typeface = Fonts.get(flags and 255, style)
        val txt = if (it.text.isEmpty()) " " else it.text
        return android.text.StaticLayout.Builder.obtain(txt, 0, txt.length, itemText, maxOf(40, it.w.toInt())).build()
    }

    /** Kutuyu, en çok maxW genişliğe kadar metne göre daraltır; dokunma alanı gereksiz geniş kalmaz. */
    fun fitText(it: PageItem, maxW: Float) {
        it.w = maxW
        val lay = layoutOf(it)
        var m = 0f
        for (i in 0 until lay.lineCount) m = max(m, lay.getLineWidth(i))
        it.w = (m + 24f).coerceIn(120f, maxW)
        measureItem(it)
    }

    // ---- Tablo nesnesi: Word'deki gibi satır/sütunlu, hücreleri yazıya göre uzayan tablo ----
    class TableLay(val rows: Int, val cols: Int, val cw: Float, val rowsH: FloatArray, val cells: Array<android.text.StaticLayout>)

    private val tblText = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)

    fun tableLayout(it: PageItem): TableLay {
        val rows = max(1, it.kind and 255)
        val cols = max(1, (it.kind shr 8) and 255)
        val parts = it.text.split('\u001f')
        val cw = it.w / cols
        tblText.setColor(Color.rgb(40, 40, 52))
        tblText.textSize = it.size
        tblText.typeface = Typeface.DEFAULT
        val wInt = max(20, (cw - 16f).toInt())
        val cells = Array(rows * cols) { idx ->
            val t = parts.getOrElse(idx) { "" }.ifEmpty { " " }
            android.text.StaticLayout.Builder.obtain(t, 0, t.length, tblText, wInt).build()
        }
        val rh = FloatArray(rows) { r ->
            var mh = it.rowH
            for (c in 0 until cols) mh = max(mh, cells[r * cols + c].height + 16f)
            mh
        }
        return TableLay(rows, cols, cw, rh, cells)
    }

    /** Tablonun sol-üst köşesine göre (lx, ly) noktasının hücresini (satır, sütun) verir; dışındaysa null. */
    fun tableCellAt(it: PageItem, lx: Float, ly: Float): Pair<Int, Int>? {
        val t = tableLayout(it)
        if (lx < 0f || ly < 0f || lx > it.w) return null
        var y = 0f
        for (r in 0 until t.rows) {
            y += t.rowsH[r]
            if (ly <= y) return Pair(r, (lx / t.cw).toInt().coerceIn(0, t.cols - 1))
        }
        return null
    }

    private fun drawTable(c: Canvas, it: PageItem) {
        val t = tableLayout(it)
        val total = t.rowsH.sum()
        it.h = total
        c.save()
        c.translate(-it.w / 2f, -total / 2f)
        fillPaint.setColor(Color.argb(235, 255, 255, 255))
        c.drawRect(0f, 0f, it.w, total, fillPaint)
        if ((it.kind shr 16) and 1 != 0) {
            fillPaint.setColor(Color.argb(255, 236, 228, 226))
            c.drawRect(0f, 0f, it.w, t.rowsH[0], fillPaint)
        }
        boxPaint.setColor(it.color)
        val old = boxPaint.strokeWidth
        boxPaint.strokeWidth = 2.6f
        var y = 0f
        for (r in 0..t.rows) {
            c.drawLine(0f, y, it.w, y, boxPaint)
            if (r < t.rows) y += t.rowsH[r]
        }
        for (k in 0..t.cols) c.drawLine(k * t.cw, 0f, k * t.cw, total, boxPaint)
        boxPaint.strokeWidth = old
        boxPaint.setColor(Color.rgb(170, 176, 190))
        y = 0f
        for (r in 0 until t.rows) {
            for (k in 0 until t.cols) {
                c.save()
                c.translate(k * t.cw + 8f, y + 8f)
                t.cells[r * t.cols + k].draw(c)
                c.restore()
            }
            y += t.rowsH[r]
        }
        c.restore()
    }

    /** Metin kutusunun ve tablonun yüksekliğini günceller. */
    fun measureItem(it: PageItem) {
        if (it.type == PageItem.TEXT) it.h = layoutOf(it).height.toFloat() + 12f
        else if (it.type == PageItem.TABLE) it.h = tableLayout(it).rowsH.sum()
    }

    /** above=false: çizgilerin altındaki nesneler, above=true: üstündekiler. */
    fun drawItems(c: Canvas, page: PageData, above: Boolean = false) {
        for (it in page.items) if (it.above == above) drawItem(c, it)
    }

    fun drawItem(c: Canvas, it: PageItem) {
        c.save()
        c.translate(it.x, it.y)
        c.rotate(it.rot)
        when (it.type) {
            PageItem.TEXT -> {
                val lay = layoutOf(it)
                it.h = lay.height.toFloat() + 12f
                c.translate(-it.w / 2f, -it.h / 2f + 6f)
                lay.draw(c)
            }
            PageItem.STICKER -> Stickers.draw(c, it.kind, it.w, it.h)
            PageItem.TABLE -> drawTable(c, it)
            else -> {
                var b = it.bmp
                if (b == null) {
                    val d = it.data
                    if (d != null) {
                        b = android.graphics.BitmapFactory.decodeByteArray(d, 0, d.size)
                        it.bmp = b
                    }
                }
                if (b != null) {
                    val r = RectF(-it.w / 2f, -it.h / 2f, it.w / 2f, it.h / 2f)
                    if (it.kind == TAPE_KIND && it.text == "k") {
                        // kalkmış bant: yalnız kesikli çerçevesi kalır, bant parçası görünmez; dokununca yeniden kapanır
                        val dp = Paint(Paint.ANTI_ALIAS_FLAG)
                        dp.style = Paint.Style.STROKE
                        dp.strokeWidth = 3f
                        dp.color = Color.argb(140, 120, 100, 100)
                        dp.pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
                        c.drawRect(r, dp)
                    } else {
                        c.drawBitmap(b, null, r, bmpPaint)
                    }
                }
            }
        }
        c.restore()
    }

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val TAPE_KIND = DrawView.TAPE_KIND
}

/** Defteri, çizimleri PDF sayfalarının üstüne işleyerek tek bir PDF dosyasına yazar. */
object PdfExporter {
    /** 1200 sayfa birimi = 210 mm = 595,28 pt; sayfalar fiziksel boyutta (A3, A4, A5 ...) yazılır. */
    private const val K = 595.28f / 1200f

    fun export(pages: List<PageData>, pdfDir: File, out: OutputStream) {
        val doc = PdfDocument()
        val renderer = PageRenderer()
        val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val rect = RectF()
        try {
            for ((i, pg) in pages.withIndex()) {
                val wPt = max(1, Math.round(pg.width * K))
                val hPt = max(1, Math.round(pg.height * K))
                val info = PdfDocument.PageInfo.Builder(wPt, hPt, i + 1).create()
                val page = doc.startPage(info)
                val c = page.canvas
                c.scale(K, K)
                c.drawColor(pg.paperColor)
                val name = pg.pdfName
                if (name != null) {
                    try {
                        val bmp = PdfStore.source(pdfDir, name).render(pg.pdfIndex, 1800)
                        rect.set(0f, 0f, pg.width, pg.height)
                        c.drawBitmap(bmp, null, rect, bgPaint)
                        bmp.recycle()
                    } catch (e: Exception) {
                        // PDF sayfası çizilemezse beyaz zemin üstüne yazılanlar yine de aktarılır.
                    }
                }
                renderer.drawPaper(c, pg)
                renderer.drawItems(c, pg, false)
                for (s in pg.strokes) renderer.drawStroke(c, s)
                renderer.drawItems(c, pg, true)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}
