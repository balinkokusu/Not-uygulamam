package com.notuygulamam.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Tek bir defter sayfasını gösterir. Sayfa sabit bir mantıksal boyuta sahiptir (varsayılan 1200x1700)
 * ve ekrana sığacak şekilde ortalanıp ölçeklenir; bu yüzden ekran dönse de çizimler yerinde kalır.
 * Sayfa ve biten çizgiler bir bitmap'te tutulur, yazarken yalnızca yeni parçalar eklenir.
 * İki parmakla yakınlaştırma ve kaydırma, üç parmakla (ya da uzaklaştırılmamışken iki parmakla)
 * yana kaydırarak sayfa değiştirme desteklenir.
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

    /** Fosforlu ile çizilen neredeyse düz bir çizgi, bırakılınca tam düz çizgiye dönüşür. */
    var highlighterSnap = true

    /** Kalem ya da fosforlu ile çizerken duraklayınca çizgi başlangıç-bitiş arasında düzleşir. */
    var holdToStraighten = true

    /** 0 ile 100 arası; 0'da çizgi kalınlığı basınçtan etkilenmez. */
    var pressureSensitivity = 70

    /** Bu oturumda gözlenen en yüksek ham basınç; tam basınç ölçeği olarak kullanılır. */
    var maxPressure = 0.25f
        private set

    /** PDF sayfalarının bulunduğu klasör. */
    var pdfDir: File? = null

    var onInfo: ((String) -> Unit)? = null
    var onColorPicked: ((Int) -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    /** Yana kaydırma ile sayfa değiştirme isteği: +1 sonraki, -1 önceki. */
    var onPageSwipe: ((Int) -> Unit)? = null

    var page: PageData = PageData()
        private set
    private var strokes: ArrayList<Stroke> = page.strokes
    private var undoStack: ArrayList<HistoryAction> = page.undo
    private var redoStack: ArrayList<HistoryAction> = page.redo

    var paper: Paper
        get() = page.paper
        set(value) {
            page.paper = value
            rebuildBase()
            onChanged?.invoke()
        }

    private var current: Stroke? = null
    private var gestureErasing = false
    private var strokeActive = false
    private var strokeFromStylus = false
    private var straightLive = false
    private val gestureRemoved = ArrayList<Pair<Int, Stroke>>()

    private var base: Bitmap? = null
    private var baseCanvas: Canvas? = null
    private var drawnUpTo = 0
    private var lastInfoMs = 0L
    private var bg: Bitmap? = null
    private var bgToken = 0
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val bgRect = RectF()
    private var picking = false
    private var pickX = 0f
    private var pickY = 0f
    private var pickColor = Color.BLACK

    // Sayfa birimi -> ekran pikseli dönüşümü
    private var fitSc = 1f
    private var sc = 1f
    private var ox = 0f
    private var oy = 0f

    // Çok parmaklı hareket durumu
    private var gestureActive = false
    private var gSc0 = 1f
    private var gOx0 = 0f
    private var gOy0 = 0f
    private var lastCx = 0f
    private var lastCy = 0f
    private var totalDx = 0f
    private var totalDy = 0f
    private var pinchAccum = 0f
    private var maxFingers = 0
    private var swipeDone = false
    private val gm = Matrix()
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!gestureActive || maxFingers >= 3 || swipeDone) return true
                zoomAt(detector.scaleFactor, detector.focusX, detector.focusY)
                pinchAccum += abs(detector.scaleFactor - 1f)
                return true
            }
        }
    )

    // Basılı tutunca düzleştirme
    private val holdHandler = Handler(Looper.getMainLooper())
    private val holdRunnable = Runnable { onHold() }
    private var holdX = 0f
    private var holdY = 0f

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
    private val renderer = PageRenderer()

    fun showPage(p: PageData) {
        holdHandler.removeCallbacks(holdRunnable)
        page = p
        strokes = p.strokes
        undoStack = p.undo
        redoStack = p.redo
        current = null
        strokeActive = false
        straightLive = false
        gestureRemoved.clear()
        bg = null
        computeTransform()
        requestBg()
        rebuildBase()
    }

    /** Sayfa bir PDF sayfasıysa görüntüsünü arka planda hazırlar, hazır olunca ekrana yansıtır. */
    private fun requestBg() {
        val name = page.pdfName ?: return
        val dir = pdfDir ?: return
        if (width <= 0) return
        val token = ++bgToken
        val target = page
        val index = page.pdfIndex
        val w = (page.width * sc * 1.5f).toInt().coerceIn(900, 3200)
        PdfStore.executor.execute {
            try {
                val bmp = PdfStore.source(dir, name).render(index, w)
                post {
                    if (token == bgToken && page === target) {
                        bg = bmp
                        rebuildBase()
                    }
                }
            } catch (e: Exception) {
                // PDF çizilemezse sayfa beyaz kalır.
            }
        }
    }

    /** Sayfayı ekrana sığdırır ve yakınlaştırmayı sıfırlar. */
    private fun computeTransform() {
        if (width <= 0 || height <= 0) return
        val m = 16f * density
        fitSc = max(0.01f, min((width - 2 * m) / page.width, (height - 2 * m) / page.height))
        sc = fitSc
        ox = (width - page.width * sc) / 2f
        oy = (height - page.height * sc) / 2f
    }

    /** Yakınlaştırılmış sayfanın ekran dışına kaçmasını engeller. */
    private fun clampTransform() {
        val pw = page.width * sc
        val ph = page.height * sc
        val m = 16f * density
        ox = if (pw + 2 * m <= width) (width - pw) / 2f else ox.coerceIn(width - pw - m, m)
        oy = if (ph + 2 * m <= height) (height - ph) / 2f else oy.coerceIn(height - ph - m, m)
    }

    private fun zoomAt(factor: Float, fx: Float, fy: Float) {
        val newSc = (sc * factor).coerceIn(fitSc, fitSc * 6f)
        val f = newSc / sc
        ox = fx - (fx - ox) * f
        oy = fy - (fy - oy) * f
        sc = newSc
        clampTransform()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        base = bmp
        baseCanvas = Canvas(bmp)
        computeTransform()
        requestBg()
        rebuildBase()
    }

    private fun beginPage(c: Canvas) {
        c.save()
        c.translate(ox, oy)
        c.scale(sc, sc)
        c.clipRect(0f, 0f, page.width, page.height)
    }

    /** Arka planı, kağıdı ve tüm biten çizgileri bitmap'e baştan çizer. */
    private fun rebuildBase() {
        val c = baseCanvas ?: return
        c.drawColor(Color.rgb(120, 124, 132))
        beginPage(c)
        c.drawColor(Color.WHITE)
        val b = bg
        if (b != null) {
            bgRect.set(0f, 0f, page.width, page.height)
            c.drawBitmap(b, null, bgRect, bgPaint)
        }
        drawPaper(c)
        for (s in strokes) drawStroke(c, s)
        c.restore()
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val b = base
        if (b == null) {
            c.drawColor(Color.rgb(120, 124, 132))
            return
        }
        if (gestureActive) {
            val f = sc / gSc0
            gm.setScale(f, f)
            gm.postTranslate(ox - gOx0 * f, oy - gOy0 * f)
            c.drawColor(Color.rgb(120, 124, 132))
            c.drawBitmap(b, gm, null)
            return
        }
        c.drawBitmap(b, 0f, 0f, null)
        val s = current
        if (s != null && (s.highlighter || straightLive)) {
            beginPage(c)
            drawStroke(c, s)
            c.restore()
        }
        if (picking) drawPickerLoupe(c)
    }

    /** Damlalık seçiliyken parmağın/kalemin üstünde, alınacak rengi gösteren daire çizer. */
    private fun drawPickerLoupe(c: Canvas) {
        val r = 30f * density
        val cx = pickX
        var cy = pickY - 90f * density
        if (cy < r + 4f * density) cy = pickY + 90f * density
        fillPaint.setColor(pickColor)
        c.drawCircle(cx, cy, r, fillPaint)
        linePaint.style = Paint.Style.STROKE
        linePaint.alpha = 255
        linePaint.setColor(Color.WHITE)
        linePaint.strokeWidth = 5f * density
        c.drawCircle(cx, cy, r, linePaint)
        linePaint.setColor(Color.BLACK)
        linePaint.strokeWidth = 1.5f * density
        c.drawCircle(cx, cy, r + 3f * density, linePaint)
        c.drawCircle(pickX, pickY, 5f * density, linePaint)
    }

    private fun drawPaper(c: Canvas) = renderer.drawPaper(c, page)

    private fun widthFactor(s: Stroke, p: Float): Float = renderer.widthFactor(s, p)

    private fun segmentWidth(s: Stroke, i: Int): Float = renderer.segmentWidth(s, i)

    private fun clamp01(p: Float): Float = max(0f, min(1f, p))

    /** Ham basıncı, gözlenen en yüksek basınca göre 0..1 aralığına getirir. */
    private fun normP(p: Float): Float {
        if (p > maxPressure) maxPressure = p
        val r = clamp01(p / maxPressure)
        return Math.pow(r.toDouble(), 0.8).toFloat()
    }

    private fun drawStroke(c: Canvas, s: Stroke) = renderer.drawStroke(c, s)

    /** Kalem çizgisinin henüz çizilmemiş yeni parçalarını bitmap'e ekler ve yalnızca o bölgeyi yeniler. */
    private fun drawPenIncrement(s: Stroke) {
        val c = baseCanvas ?: return
        val n = s.xs.size
        beginPage(c)
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
        c.restore()
        drawnUpTo = max(drawnUpTo, n - 1)
    }

    /** Sayfa koordinatlarındaki bir parçanın ekrandaki sınırını yeniler. */
    private fun invalidateSegment(x0: Float, y0: Float, x1: Float, y1: Float, w: Float) {
        val pad = w * sc + 3f * density
        val sx0 = ox + x0 * sc
        val sy0 = oy + y0 * sc
        val sx1 = ox + x1 * sc
        val sy1 = oy + y1 * sc
        invalidate(
            (min(sx0, sx1) - pad).toInt(), (min(sy0, sy1) - pad).toInt(),
            (max(sx0, sx1) + pad).toInt() + 1, (max(sy0, sy1) + pad).toInt() + 1
        )
    }

    // ---- Düzleştirme ----

    /** Çizginin ikinci noktasını (x, y) olarak ayarlar; yatay, dikey ve 45 derece yakınında tam açıya yapıştırır. */
    private fun setStraightEnd(s: Stroke, x: Float, y: Float) {
        val x0 = s.xs[0]
        val y0 = s.ys[0]
        var ex = x
        var ey = y
        val dx = x - x0
        val dy = y - y0
        val ax = abs(dx)
        val ay = abs(dy)
        if (ay < ax * 0.07f) {
            ey = y0
        } else if (ax < ay * 0.07f) {
            ex = x0
        } else if (abs(ax - ay) < max(ax, ay) * 0.07f) {
            val m = (ax + ay) / 2f
            ex = x0 + (if (dx < 0) -m else m)
            ey = y0 + (if (dy < 0) -m else m)
        }
        s.xs[1] = ex
        s.ys[1] = ey
    }

    /** Çizgiyi ilk ve son noktası arasındaki düz bir çizgiye indirger. */
    private fun straighten(s: Stroke) {
        val n = s.xs.size
        if (n < 2) return
        val x0 = s.xs[0]
        val y0 = s.ys[0]
        val x1 = s.xs[n - 1]
        val y1 = s.ys[n - 1]
        var avgP = 0f
        for (p in s.ps) avgP += p
        avgP /= n
        s.xs.clear()
        s.ys.clear()
        s.ps.clear()
        s.add(x0, y0, avgP)
        s.add(x1, y1, avgP)
        setStraightEnd(s, x1, y1)
    }

    /** Çizgi başlangıç-bitiş doğrusundan çok sapmıyorsa true döner. */
    private fun nearlyStraight(s: Stroke): Boolean {
        val n = s.xs.size
        if (n < 3) return false
        val x0 = s.xs[0]
        val y0 = s.ys[0]
        val dx = s.xs[n - 1] - x0
        val dy = s.ys[n - 1] - y0
        val len = hypot(dx, dy)
        if (len < 40f) return false
        var maxDev = 0f
        for (i in 1 until n - 1) {
            val dev = abs((s.xs[i] - x0) * dy - (s.ys[i] - y0) * dx) / len
            if (dev > maxDev) maxDev = dev
        }
        return maxDev < max(len * 0.08f, 12f)
    }

    private fun onHold() {
        val s = current ?: return
        if (!holdToStraighten || straightLive || s.xs.size < 3) return
        val dx = s.xs[s.xs.size - 1] - s.xs[0]
        val dy = s.ys[s.ys.size - 1] - s.ys[0]
        if (hypot(dx, dy) < 20f) return
        straighten(s)
        straightLive = true
        rebuildBase()
        invalidate()
    }

    private fun scheduleHoldCheck(x: Float, y: Float) {
        if (!holdToStraighten) return
        if (hypot(x - holdX, y - holdY) > 8f * density) {
            holdX = x
            holdY = y
            holdHandler.removeCallbacks(holdRunnable)
            holdHandler.postDelayed(holdRunnable, 600L)
        }
    }

    // ---- Dokunma ----

    private fun typeName(t: Int): String = when (t) {
        MotionEvent.TOOL_TYPE_FINGER -> "parmak"
        MotionEvent.TOOL_TYPE_STYLUS -> "kalem"
        MotionEvent.TOOL_TYPE_ERASER -> "kalem silgisi"
        MotionEvent.TOOL_TYPE_MOUSE -> "fare"
        else -> "bilinmiyor($t)"
    }

    private fun pageX(x: Float): Float = (x - ox) / sc
    private fun pageY(y: Float): Float = (y - oy) / sc

    private fun samplePixel(x: Float, y: Float): Int {
        val b = base ?: return Color.BLACK
        val px = x.toInt().coerceIn(0, b.width - 1)
        val py = y.toInt().coerceIn(0, b.height - 1)
        return b.getPixel(px, py) or (0xFF shl 24)
    }

    private fun countFingers(e: MotionEvent): Int {
        var n = 0
        for (i in 0 until e.pointerCount) {
            if (e.getToolType(i) == MotionEvent.TOOL_TYPE_FINGER) n++
        }
        return n
    }

    private fun centroid(e: MotionEvent, skip: Int): FloatArray {
        var sx = 0f
        var sy = 0f
        var n = 0
        for (i in 0 until e.pointerCount) {
            if (i == skip) continue
            sx += e.getX(i)
            sy += e.getY(i)
            n++
        }
        if (n == 0) return floatArrayOf(lastCx, lastCy)
        return floatArrayOf(sx / n, sy / n)
    }

    private fun startGesture(e: MotionEvent) {
        holdHandler.removeCallbacks(holdRunnable)
        if (current != null || gestureErasing) {
            current = null
            straightLive = false
            if (gestureErasing && gestureRemoved.isNotEmpty()) {
                undoStack.add(HistoryAction.Erase(ArrayList(gestureRemoved)))
                redoStack.clear()
            }
            gestureRemoved.clear()
            strokeActive = false
            rebuildBase()
        }
        gestureActive = true
        gSc0 = sc
        gOx0 = ox
        gOy0 = oy
        pinchAccum = 0f
        totalDx = 0f
        totalDy = 0f
        swipeDone = false
        maxFingers = e.pointerCount
        val c = centroid(e, -1)
        lastCx = c[0]
        lastCy = c[1]
    }

    private fun endGesture() {
        gestureActive = false
        if (!swipeDone) {
            requestBg()
            rebuildBase()
        }
    }

    /** Çok parmaklı dokunuşu işler; olay buna aitse true döner. */
    private fun handleMultiTouch(e: MotionEvent): Boolean {
        val act = e.actionMasked
        if (!gestureActive) {
            if (act == MotionEvent.ACTION_POINTER_DOWN && countFingers(e) >= 2 &&
                !(strokeActive && strokeFromStylus)
            ) {
                startGesture(e)
                scaleDetector.onTouchEvent(e)
                return true
            }
            return false
        }
        if (act == MotionEvent.ACTION_DOWN) {
            // Önceki hareketin sonu kaçırılmışsa temizle.
            gestureActive = false
            return false
        }
        scaleDetector.onTouchEvent(e)
        when (act) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxFingers = max(maxFingers, e.pointerCount)
                val c = centroid(e, -1)
                lastCx = c[0]
                lastCy = c[1]
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val c = centroid(e, e.actionIndex)
                lastCx = c[0]
                lastCy = c[1]
            }
            MotionEvent.ACTION_MOVE -> {
                val c = centroid(e, -1)
                val dx = c[0] - lastCx
                val dy = c[1] - lastCy
                lastCx = c[0]
                lastCy = c[1]
                totalDx += dx
                totalDy += dy
                if (!swipeDone) {
                    val zoomed = sc > fitSc * 1.02f
                    if (e.pointerCount >= 3) {
                        if (abs(totalDx) > 90f * density && abs(totalDx) > 1.5f * abs(totalDy)) {
                            swipeDone = true
                            onPageSwipe?.invoke(if (totalDx < 0) 1 else -1)
                        }
                    } else if (zoomed || pinchAccum > 0.04f) {
                        ox += dx
                        oy += dy
                        clampTransform()
                        invalidate()
                    } else if (abs(totalDx) > 110f * density && abs(totalDx) > 1.8f * abs(totalDy)) {
                        swipeDone = true
                        onPageSwipe?.invoke(if (totalDx < 0) 1 else -1)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endGesture()
        }
        return true
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (handleMultiTouch(e)) return true

        val type = e.getToolType(0)
        val isStylus = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER

        // Bilgi satırı her olayda değil, yaklaşık 6 kez/saniye güncellenir; aksi hâlde yazı gecikir.
        val now = e.eventTime
        if (now - lastInfoMs > 160L || e.actionMasked != MotionEvent.ACTION_MOVE) {
            lastInfoMs = now
            onInfo?.invoke(
                String.format(
                    Locale.US, "Son olay: araç=%s, basınç=%.3f (en yüksek %.3f), düğme=%d, eğim=%.2f",
                    typeName(type), e.pressure, maxPressure, e.buttonState, e.getAxisValue(MotionEvent.AXIS_TILT)
                )
            )
        }

        if (tool == Tool.PICKER) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    picking = true
                    pickX = e.x
                    pickY = e.y
                    pickColor = samplePixel(e.x, e.y)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    val col = samplePixel(e.x, e.y)
                    picking = false
                    penColor = col
                    tool = Tool.PEN
                    invalidate()
                    onColorPicked?.invoke(col)
                }
                MotionEvent.ACTION_CANCEL -> {
                    picking = false
                    invalidate()
                }
            }
            return true
        }

        if (!isStylus && !fingerDraw) return true

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(e)
                strokeActive = true
                strokeFromStylus = isStylus
                straightLive = false
                gestureErasing = tool == Tool.ERASER ||
                    type == MotionEvent.TOOL_TYPE_ERASER ||
                    (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                gestureRemoved.clear()
                val gx = pageX(e.x)
                val gy = pageY(e.y)
                if (gestureErasing) {
                    if (eraseAt(gx, gy)) rebuildBase()
                } else {
                    val hl = tool == Tool.HIGHLIGHTER
                    val s = if (hl) Stroke(hlColor, hlWidthDp * UNIT, true)
                    else Stroke(penColor, penWidthDp * UNIT, false, pressureSensitivity / 100f)
                    s.add(gx, gy, if (hl) 0.5f else normP(e.pressure))
                    current = s
                    drawnUpTo = 0
                    holdX = e.x
                    holdY = e.y
                    if (holdToStraighten) {
                        holdHandler.removeCallbacks(holdRunnable)
                        holdHandler.postDelayed(holdRunnable, 600L)
                    }
                    if (!hl) {
                        val r = s.width * widthFactor(s, s.ps[0]) * 0.5f
                        val c = baseCanvas
                        if (c != null) {
                            beginPage(c)
                            fillPaint.setColor(s.color)
                            c.drawCircle(gx, gy, r, fillPaint)
                            c.restore()
                        }
                        invalidateSegment(gx, gy, gx, gy, r * 2f)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureErasing) {
                    var removed = false
                    for (h in 0 until e.historySize) {
                        if (eraseAt(pageX(e.getHistoricalX(h)), pageY(e.getHistoricalY(h)))) removed = true
                    }
                    if (eraseAt(pageX(e.x), pageY(e.y))) removed = true
                    if (removed) rebuildBase()
                } else {
                    val s = current
                    if (s != null) {
                        if (straightLive) {
                            setStraightEnd(s, pageX(e.x), pageY(e.y))
                            invalidate()
                        } else {
                            val hl = s.highlighter
                            val before = s.xs.size
                            for (h in 0 until e.historySize) {
                                s.add(
                                    pageX(e.getHistoricalX(h)), pageY(e.getHistoricalY(h)),
                                    if (hl) 0.5f else normP(e.getHistoricalPressure(h))
                                )
                            }
                            s.add(pageX(e.x), pageY(e.y), if (hl) 0.5f else normP(e.pressure))
                            if (hl) {
                                val from = max(0, before - 1)
                                for (i in from + 1 until s.xs.size) {
                                    invalidateSegment(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], s.width)
                                }
                            } else {
                                drawPenIncrement(s)
                            }
                            scheduleHoldCheck(e.x, e.y)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                holdHandler.removeCallbacks(holdRunnable)
                strokeActive = false
                if (gestureErasing) {
                    if (gestureRemoved.isNotEmpty()) {
                        undoStack.add(HistoryAction.Erase(ArrayList(gestureRemoved)))
                        redoStack.clear()
                        onChanged?.invoke()
                    }
                    gestureRemoved.clear()
                } else {
                    val s = current
                    if (s != null) {
                        var needsBake = s.highlighter || straightLive
                        if (s.highlighter && highlighterSnap && !straightLive && nearlyStraight(s)) {
                            straighten(s)
                            needsBake = true
                        }
                        strokes.add(s)
                        undoStack.add(HistoryAction.Add(s))
                        redoStack.clear()
                        current = null
                        straightLive = false
                        if (needsBake) rebuildBase()
                        onChanged?.invoke()
                    }
                }
            }
        }
        return true
    }

    private fun eraseAt(x: Float, y: Float): Boolean {
        val r = eraserRadiusDp * density / sc
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
        onChanged?.invoke()
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
        onChanged?.invoke()
    }

    fun clearAll() {
        if (strokes.isEmpty()) return
        val removed = ArrayList<Pair<Int, Stroke>>()
        for (i in strokes.indices.reversed()) removed.add(Pair(i, strokes[i]))
        strokes.clear()
        undoStack.add(HistoryAction.Erase(removed))
        redoStack.clear()
        rebuildBase()
        onChanged?.invoke()
    }

    companion object {
        /** Kalınlık kaydırıcısındaki 1 birimin sayfa birimi karşılığı. */
        const val UNIT = 2.2f
    }
}
