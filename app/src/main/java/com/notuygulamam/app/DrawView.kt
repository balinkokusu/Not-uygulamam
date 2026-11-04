package com.notuygulamam.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.graphics.Typeface
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

    /** Kalem türü: 0 dolma, 1 tükenmez, 2 kurşun, 3 fırça. */
    var penKind = 0

    /** Fosforlu türü: 0 düz, 1 pırıltılı, 2 yıldız, 3 kalp. */
    var hlKind = 0

    /** Kalemle hızlı iki kez dokunmak kalem ile silgi arasında geçiş yapar. */
    var penDoubleTap = true

    /** Yazının üstünü karalayınca altındaki çizgiler silinir. */
    var scribbleErase = true
    private var lastToggleMs = 0L
    private var lastDrawTool = Tool.PEN
    var onToolToggled: (() -> Unit)? = null

    /** Seçili metin/çıkartma/resim. */
    var selectedItem: PageItem? = null
        private set

    /** Metin aracıyla boşa dokunulunca (item=null, x, y) ya da seçili metne tekrar dokunulunca çağrılır. */
    var onTextTap: ((PageItem?, Float, Float) -> Unit)? = null
    private var dragMode = 0
    private var dragItem: PageItem? = null
    private var dragMoved = false
    private var tapOnSelected = false
    private var createPending = false
    private var createX = 0f
    private var createY = 0f
    private var dragSx = 0f
    private var dragSy = 0f
    private var dragDx = 0f
    private var dragDy = 0f
    private var itemX0 = 0f
    private var itemY0 = 0f
    private var dist0 = 1f
    private var ang0 = 0f
    private var w0 = 0f
    private var size0 = 0f
    private var rot0 = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var lastTapStroke: Stroke? = null
    private var lastTapErase: List<Pair<Int, Stroke>>? = null
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Fosforlu ile çizilen neredeyse düz bir çizgi, bırakılınca tam düz çizgiye dönüşür. */
    var highlighterSnap = true

    /** Kalem ya da fosforlu ile çizerken duraklayınca çizgi başlangıç-bitiş arasında düzleşir. */
    var holdToStraighten = true

    /** 0 ile 100 arası; 0'da çizgi kalınlığı basınçtan etkilenmez. */
    var pressureSensitivity = 85

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
    private val gestureRemovedItems = ArrayList<Pair<Int, PageItem>>()

    /** Silgi hangi katmanları siler. */
    var eraseInk = true
    var eraseText = true
    var eraseSticker = true
    var eraseImage = true

    private var base: Bitmap? = null
    private var baseCanvas: Canvas? = null
    private var drawnUpTo = 0
    private var lastInfoMs = 0L
    private val bgs = java.util.IdentityHashMap<PageData, Bitmap>()
    private var rightPage: PageData? = null
    private var leftPage: PageData = page
    private var offX = 0f
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

    // ---- Sayfa kenarındaki ayraçlar (sayfa birimiyle çizilir; sayfayla birlikte yakınlaşır) ----
    private var tabs: List<Tab> = emptyList()
    private var tabActive: Tab? = null
    var onTabClick: ((Tab) -> Unit)? = null
    var onTabLongPress: ((Tab) -> Unit)? = null
    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tabText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val tabRect = RectF()
    private var tabDown: Tab? = null
    private var tabDownX = 0f
    private var tabDownY = 0f
    private var tabLongFired = false
    private val tabSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val tabLongRunnable = Runnable {
        val t = tabDown
        if (t != null) {
            tabLongFired = true
            onTabLongPress?.invoke(t)
        }
    }

    private fun tabGutter(): Float = if (tabs.isEmpty()) 0f else TAB_GUTTER

    /** Ayraçlar sırasıyla ve hangisinin açık olduğuyla verilir; ilk ayraç gelince ya da son ayraç gidince sayfa yeniden yerleşir. */
    fun setTabs(list: List<Tab>, active: Tab?) {
        val hadGutter = tabGutter()
        tabs = list
        tabActive = active
        if (hadGutter != tabGutter()) {
            computeTransform()
            requestBg()
            rebuildBase()
        } else {
            invalidate()
        }
    }

    private fun tabHeight(): Float {
        val n = max(1, tabs.size)
        return min(TAB_H_MAX, max(TAB_H_MIN, (leftPage.height - 2f * TAB_TOP - (n - 1) * TAB_GAP) / n))
    }

    private fun tabTop(i: Int): Float = TAB_TOP + i * (tabHeight() + TAB_GAP)

    private fun tabAt(px: Float, py: Float): Tab? {
        if (px > TAB_IN || px < -TAB_GUTTER) return null
        val h = tabHeight()
        for (i in tabs.indices) {
            val top = tabTop(i)
            if (py >= top - TAB_GAP / 2f && py <= top + h + TAB_GAP / 2f) return tabs[i]
        }
        return null
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tabPath = Path()
    private val tabRadii = floatArrayOf(22f, 22f, 4f, 4f, 4f, 4f, 22f, 22f)

    private fun lighten(color: Int, f: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb((r + (255 - r) * f).toInt(), (g + (255 - g) * f).toInt(), (b + (255 - b) * f).toInt())
    }

    private fun drawTabs(c: Canvas) {
        if (tabs.isEmpty()) return
        c.save()
        c.translate(ox, oy)
        c.scale(sc, sc)
        val h = tabHeight()
        for (i in tabs.indices) {
            val t = tabs[i]
            val on = t === tabActive
            val top = tabTop(i)
            val left = if (on) -TAB_GUTTER else -TAB_OUT
            // Yumuşak gölge
            tabPaint.shader = null
            tabPaint.style = Paint.Style.FILL
            tabPaint.setColor(Color.argb(if (on) 70 else 42, 0, 0, 0))
            tabRect.set(left - 2f, top + 4f, TAB_IN - 2f, top + h + 4f)
            tabPath.reset()
            tabPath.addRoundRect(tabRect, tabRadii, Path.Direction.CW)
            c.drawPath(tabPath, tabPaint)
            // Üstü açık, altı koyu pastel geçiş
            tabRect.set(left, top, TAB_IN, top + h)
            tabPaint.setColor(Color.BLACK)
            tabPaint.shader = LinearGradient(
                0f, top, 0f, top + h, lighten(t.color, 0.5f), t.color, Shader.TileMode.CLAMP
            )
            tabPath.reset()
            tabPath.addRoundRect(tabRect, tabRadii, Path.Direction.CW)
            c.drawPath(tabPath, tabPaint)
            tabPaint.shader = null

            tabText.setColor(if (on) Color.rgb(35, 35, 50) else Color.argb(215, 70, 70, 85))
            tabText.typeface = Typeface.create(if (on) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            tabText.letterSpacing = 0.05f
            val cx = (left + TAB_IN) / 2f - 3f
            val cy = top + h / 2f
            var label = t.label
            if (label.length <= 2) {
                tabText.textSize = 34f
                val fm = tabText.fontMetrics
                c.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, tabText)
            } else {
                var size = 28f
                val avail = h - 26f
                tabText.textSize = size
                while (tabText.measureText(label) > avail && size > 16f) {
                    size -= 2f
                    tabText.textSize = size
                }
                while (tabText.measureText(label) > avail && label.length > 2) {
                    label = label.substring(0, label.length - 1)
                }
                val fm = tabText.fontMetrics
                c.save()
                c.rotate(90f, cx, cy)
                c.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, tabText)
                c.restore()
            }
        }
        c.restore()
    }

    /** Ayraç üstüne dokunma: kısa dokunuş sayfaya götürür, uzun basış düzenleme penceresini açar. Ayraca denk gelmezse false döner. */
    private fun tabTouch(e: MotionEvent): Boolean {
        if (tabs.isEmpty()) return false
        if (e.pointerCount > 1) {
            if (tabDown != null) {
                holdHandler.removeCallbacks(tabLongRunnable)
                tabDown = null
            }
            return false
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val t = tabAt(worldX(e.x), pageY(e.y)) ?: return false
                tabDown = t
                tabDownX = e.x
                tabDownY = e.y
                tabLongFired = false
                holdHandler.postDelayed(tabLongRunnable, 500L)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (tabDown == null) return false
                if (hypot(e.x - tabDownX, e.y - tabDownY) > tabSlop) {
                    holdHandler.removeCallbacks(tabLongRunnable)
                    tabDown = null
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val t = tabDown ?: return false
                holdHandler.removeCallbacks(tabLongRunnable)
                tabDown = null
                if (!tabLongFired) onTabClick?.invoke(t)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (tabDown == null) return false
                holdHandler.removeCallbacks(tabLongRunnable)
                tabDown = null
                return true
            }
        }
        return false
    }

    /** Tek sayfa ya da (right verilirse) yan yana iki sayfa gösterir; etkin sayfa sol sayfadır. */
    fun showPage(p: PageData, right: PageData? = null) {
        holdHandler.removeCallbacks(holdRunnable)
        leftPage = p
        rightPage = right
        offX = 0f
        page = p
        strokes = p.strokes
        undoStack = p.undo
        redoStack = p.redo
        current = null
        selectedItem = null
        strokeActive = false
        straightLive = false
        gestureRemoved.clear()
        bgs.clear()
        computeTransform()
        gSc0 = sc
        gOx0 = ox
        gOy0 = oy
        requestBg()
        rebuildBase()
    }

    /** Gösterilen PDF sayfalarının görüntülerini arka planda hazırlar, hazır olunca ekrana yansıtır. */
    private fun requestBg() {
        val dir = pdfDir ?: return
        if (width <= 0) return
        val token = ++bgToken
        for (target in listOfNotNull(leftPage, rightPage)) {
            val name = target.pdfName ?: continue
            val index = target.pdfIndex
            val w = (target.width * sc * 1.5f).toInt().coerceIn(900, 3200)
            PdfStore.executor.execute {
                try {
                    val bmp = PdfStore.source(dir, name).render(index, w)
                    post {
                        if (token == bgToken && (leftPage === target || rightPage === target)) {
                            bgs[target] = bmp
                            rebuildBase()
                        }
                    }
                } catch (e: Exception) {
                    // PDF çizilemezse sayfa boş kalır.
                }
            }
        }
    }

    private fun worldW(): Float {
        val r = rightPage
        return if (r == null) leftPage.width else leftPage.width + SPREAD_GAP + r.width
    }

    private fun worldH(): Float {
        val r = rightPage
        return if (r == null) leftPage.height else max(leftPage.height, r.height)
    }

    /** Dokunulan x konumuna göre etkin sayfayı seçer (çift sayfa görünümünde). */
    private fun activateAt(screenX: Float) {
        val r = rightPage ?: return
        val wx = (screenX - ox) / sc
        val useRight = wx > leftPage.width + SPREAD_GAP / 2f
        val target = if (useRight) r else leftPage
        if (target === page) return
        page = target
        offX = if (useRight) leftPage.width + SPREAD_GAP else 0f
        strokes = target.strokes
        undoStack = target.undo
        redoStack = target.redo
    }

    /** Sayfayı ekrana sığdırır ve yakınlaştırmayı sıfırlar. */
    private fun computeTransform() {
        if (width <= 0 || height <= 0) return
        val m = 16f * density
        val g = tabGutter()
        fitSc = max(0.01f, min((width - 2 * m) / (worldW() + g), (height - 2 * m) / worldH()))
        sc = fitSc
        ox = (width - (worldW() + g) * sc) / 2f + g * sc
        oy = (height - worldH() * sc) / 2f
    }

    /** Yakınlaştırılmış sayfanın ekran dışına kaçmasını engeller. */
    private fun clampTransform() {
        val g = tabGutter()
        val pw = (worldW() + g) * sc
        val ph = worldH() * sc
        val m = 16f * density
        ox = if (pw + 2 * m <= width) (width - pw) / 2f + g * sc
        else ox.coerceIn(width - worldW() * sc - m, m + g * sc)
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
        c.translate(ox + offX * sc, oy)
        c.scale(sc, sc)
        c.clipRect(0f, 0f, page.width, page.height)
    }

    /** Arka planı, kağıdı ve tüm biten çizgileri bitmap'e baştan çizer. */
    private fun rebuildBase() {
        val c = baseCanvas ?: return
        c.drawColor(Look.canvas)
        val list = listOfNotNull(leftPage, rightPage)
        for ((k, pg) in list.withIndex()) {
            val off = if (k == 0) 0f else leftPage.width + SPREAD_GAP
            // Sayfanın altına yumuşak gölge
            val sl = ox + off * sc
            for (g in 1..7) {
                shadowPaint.setColor(Color.argb(13, 70, 40, 45))
                val e = g * 1.6f * density
                c.drawRoundRect(
                    sl - e, oy + 2f * density - e * 0.4f, sl + pg.width * sc + e, oy + pg.height * sc + 4f * density + e,
                    e + 3f * density, e + 3f * density, shadowPaint
                )
            }
            c.save()
            c.translate(ox + off * sc, oy)
            c.scale(sc, sc)
            c.clipRect(0f, 0f, pg.width, pg.height)
            c.drawColor(pg.paperColor)
            val b = bgs[pg]
            if (b != null) {
                bgRect.set(0f, 0f, pg.width, pg.height)
                c.drawBitmap(b, null, bgRect, bgPaint)
            }
            renderer.drawPaper(c, pg)
            renderer.drawItems(c, pg)
            for (s in pg.strokes) drawStroke(c, s)
            c.restore()
        }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val b = base
        if (b == null) {
            c.drawColor(Look.canvas)
            return
        }
        if (gestureActive) {
            val f = sc / gSc0
            gm.setScale(f, f)
            gm.postTranslate(ox - gOx0 * f, oy - gOy0 * f)
            c.drawColor(Look.canvas)
            c.drawBitmap(b, gm, null)
            drawTabs(c)
            return
        }
        c.drawBitmap(b, 0f, 0f, null)
        drawTabs(c)
        val s = current
        if (s != null && (s.highlighter || straightLive)) {
            beginPage(c)
            drawStroke(c, s)
            c.restore()
        }
        if (picking) drawPickerLoupe(c)
        drawSelection(c)
    }

    private fun rad(deg: Float) = Math.toRadians(deg.toDouble())

    private fun handleAt(it: PageItem, sx: Float, sy: Float): FloatArray {
        val lx = sx * it.w / 2f
        val ly = sy * it.h / 2f
        val a = rad(it.rot)
        val cs = Math.cos(a).toFloat()
        val sn = Math.sin(a).toFloat()
        return floatArrayOf(it.x + lx * cs - ly * sn, it.y + lx * sn + ly * cs)
    }

    private fun itemHit(it: PageItem, x: Float, y: Float): Boolean {
        renderer.measureItem(it)
        val a = rad(-it.rot)
        val dx = x - it.x
        val dy = y - it.y
        val lx = dx * Math.cos(a).toFloat() - dy * Math.sin(a).toFloat()
        val ly = dx * Math.sin(a).toFloat() + dy * Math.cos(a).toFloat()
        val pad = 8f * density / sc
        return abs(lx) <= it.w / 2f + pad && abs(ly) <= it.h / 2f + pad
    }

    private fun drawSelection(c: Canvas) {
        val it = selectedItem ?: return
        if (gestureActive || (tool != Tool.TEXT && tool != Tool.OBJECT)) return
        renderer.measureItem(it)
        c.save()
        c.translate(ox + offX * sc, oy)
        c.scale(sc, sc)
        val px = 1f / sc
        c.save()
        c.translate(it.x, it.y)
        c.rotate(it.rot)
        selPaint.style = Paint.Style.STROKE
        selPaint.setColor(Look.accent)
        selPaint.strokeWidth = 1.6f * density * px
        selPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f * density * px, 5f * density * px), 0f)
        c.drawRect(-it.w / 2f, -it.h / 2f, it.w / 2f, it.h / 2f, selPaint)
        selPaint.pathEffect = null
        c.restore()
        val r = 13f * density * px
        val del = handleAt(it, -1f, -1f)
        val rs = handleAt(it, 1f, 1f)
        selPaint.style = Paint.Style.FILL
        selPaint.setColor(Color.WHITE)
        c.drawCircle(del[0], del[1], r, selPaint)
        c.drawCircle(rs[0], rs[1], r, selPaint)
        selPaint.style = Paint.Style.STROKE
        selPaint.setColor(Look.accent)
        selPaint.strokeWidth = 1.6f * density * px
        c.drawCircle(del[0], del[1], r, selPaint)
        c.drawCircle(rs[0], rs[1], r, selPaint)
        val k = r * 0.4f
        c.drawLine(del[0] - k, del[1] - k, del[0] + k, del[1] + k, selPaint)
        c.drawLine(del[0] - k, del[1] + k, del[0] + k, del[1] - k, selPaint)
        c.drawArc(rs[0] - k, rs[1] - k, rs[0] + k, rs[1] + k, 200f, 250f, false, selPaint)
        c.restore()
    }

    // ---- Nesneler ----

    fun addItem(it: PageItem, topLeft: Boolean = false) {
        renderer.measureItem(it)
        if (topLeft) {
            it.x += it.w / 2f
            it.y += it.h / 2f
        }
        it.x = it.x.coerceIn(0f, page.width)
        it.y = it.y.coerceIn(0f, page.height)
        page.items.add(it)
        undoStack.add(HistoryAction.ItemAdd(it))
        redoStack.clear()
        selectedItem = it
        rebuildBase()
        onChanged?.invoke()
    }

    fun deleteSelected() {
        val it = selectedItem ?: return
        val idx = page.items.indexOf(it)
        if (idx < 0) return
        page.items.removeAt(idx)
        undoStack.add(HistoryAction.ItemRemove(it, idx))
        redoStack.clear()
        selectedItem = null
        rebuildBase()
        onChanged?.invoke()
    }

    /** Seçili nesnenin metni/rengi değiştirildikten sonra çağrılır. */
    fun itemEdited() {
        rebuildBase()
        onChanged?.invoke()
    }

    /** Görünen alanın ortasının sayfa koordinatları (yeni çıkartmayı koymak için). */
    fun viewCenterOnPage(): FloatArray {
        val x = pageX(width / 2f).coerceIn(80f, max(80f, page.width - 80f))
        val y = pageY(height / 2f).coerceIn(80f, max(80f, page.height - 80f))
        return floatArrayOf(x, y)
    }

    private fun handleItemTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activateAt(e.x)
                val gx = pageX(e.x)
                val gy = pageY(e.y)
                dragMode = 0
                dragItem = null
                dragMoved = false
                tapOnSelected = false
                createPending = false
                val sel = selectedItem
                if (sel != null && page.items.contains(sel)) {
                    renderer.measureItem(sel)
                    val hr = 30f * density / sc
                    val del = handleAt(sel, -1f, -1f)
                    if (hypot(gx - del[0], gy - del[1]) <= hr) {
                        deleteSelected()
                        return true
                    }
                    val rs = handleAt(sel, 1f, 1f)
                    if (hypot(gx - rs[0], gy - rs[1]) <= hr) {
                        dragMode = 2
                        dragItem = sel
                        dist0 = max(1f, hypot(gx - sel.x, gy - sel.y))
                        ang0 = Math.toDegrees(Math.atan2((gy - sel.y).toDouble(), (gx - sel.x).toDouble())).toFloat()
                        w0 = sel.w
                        size0 = sel.size
                        rot0 = sel.rot
                        return true
                    }
                }
                val hit = page.items.lastOrNull { itemHit(it, gx, gy) }
                if (hit != null) {
                    tapOnSelected = hit === selectedItem
                    selectedItem = hit
                    dragItem = hit
                    dragMode = 1
                    dragSx = gx
                    dragSy = gy
                    itemX0 = hit.x
                    itemY0 = hit.y
                } else {
                    selectedItem = null
                    if (tool == Tool.TEXT) {
                        createPending = true
                        createX = gx
                        createY = gy
                        dragSx = e.x
                        dragSy = e.y
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val gx = pageX(e.x)
                val gy = pageY(e.y)
                val it = dragItem
                if (dragMode == 1 && it != null) {
                    if (!dragMoved && hypot(gx - dragSx, gy - dragSy) * sc > 8f * density) dragMoved = true
                    if (dragMoved) {
                        it.x = itemX0 + gx - dragSx
                        it.y = itemY0 + gy - dragSy
                        rebuildBase()
                    }
                } else if (dragMode == 2 && it != null) {
                    dragMoved = true
                    val dist = max(1f, hypot(gx - it.x, gy - it.y))
                    val ratio = (dist / dist0).coerceIn(0.2f, 8f)
                    val ang = Math.toDegrees(Math.atan2((gy - it.y).toDouble(), (gx - it.x).toDouble())).toFloat()
                    it.rot = rot0 + (ang - ang0)
                    val aspect = if (it.w > 0f) it.h / it.w else 1f
                    if (it.type == PageItem.TEXT) {
                        it.w = max(60f, w0 * ratio)
                        it.size = (size0 * ratio).coerceIn(10f, 400f)
                    } else {
                        it.w = max(30f, w0 * ratio)
                        it.h = it.w * aspect
                    }
                    rebuildBase()
                } else if (createPending && hypot(e.x - dragSx, e.y - dragSy) > 12f * density) {
                    createPending = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val it = dragItem
                if (e.actionMasked == MotionEvent.ACTION_UP) {
                    if (dragMode != 0 && dragMoved) {
                        onChanged?.invoke()
                    } else if (dragMode == 1 && !dragMoved && tapOnSelected && it != null && it.type == PageItem.TEXT) {
                        onTextTap?.invoke(it, it.x, it.y)
                    } else if (createPending) {
                        onTextTap?.invoke(null, createX, createY)
                    }
                }
                dragMode = 0
                dragItem = null
                createPending = false
                invalidate()
            }
        }
        return true
    }

    /** Kalem ile silgi arasında geçiş yapar. */
    fun togglePenEraser() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastToggleMs < 700L) return
        lastToggleMs = now
        if (tool == Tool.ERASER) {
            tool = lastDrawTool
        } else {
            if (tool == Tool.PEN || tool == Tool.HIGHLIGHTER) lastDrawTool = tool
            tool = Tool.ERASER
        }
        onToolToggled?.invoke()
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

    private fun widthFactor(s: Stroke, p: Float): Float = renderer.widthFactor(s, p)

    private fun segmentWidth(s: Stroke, i: Int): Float = renderer.segmentWidth(s, i)

    private fun clamp01(p: Float): Float = max(0f, min(1f, p))

    /** Ham basıncı, gözlenen en yüksek basınca göre 0..1 aralığına getirir. */
    private fun normP(p: Float): Float {
        if (p > maxPressure) maxPressure = p
        val r = clamp01(p / maxPressure)
        return Math.pow(r.toDouble(), 0.7).toFloat()
    }

    private fun drawStroke(c: Canvas, s: Stroke) = renderer.drawStroke(c, s)

    /** Kalem çizgisinin henüz çizilmemiş yeni parçalarını bitmap'e ekler ve yalnızca o bölgeyi yeniler. */
    private fun drawPenIncrement(s: Stroke) {
        val c = baseCanvas ?: return
        val n = s.xs.size
        beginPage(c)
        var i = drawnUpTo + 1
        while (i < n) {
            renderer.drawPenSegment(c, s, i, false)
            invalidateSegment(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], segmentWidth(s, i) * 1.3f)
            i++
        }
        c.restore()
        drawnUpTo = max(drawnUpTo, n - 1)
    }

    /** Sayfa koordinatlarındaki bir parçanın ekrandaki sınırını yeniler. */
    private fun invalidateSegment(x0: Float, y0: Float, x1: Float, y1: Float, w: Float) {
        val pad = w * sc + 3f * density
        val sx0 = ox + (x0 + offX) * sc
        val sy0 = oy + y0 * sc
        val sx1 = ox + (x1 + offX) * sc
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

    private var shapeFrozen = false

    private fun onHold() {
        val s = current ?: return
        if (!holdToStraighten || straightLive || shapeFrozen || s.xs.size < 6) return
        val r = ShapeRecognizer.recognize(s.xs, s.ys) ?: return
        if (r.isLine) {
            straighten(s)
            straightLive = true
        } else {
            var avgP = 0f
            for (p in s.ps) avgP += p
            avgP /= s.ps.size
            s.xs.clear()
            s.ys.clear()
            s.ps.clear()
            for (i in r.xs.indices) s.add(r.xs[i], r.ys[i], avgP)
            straightLive = true
            shapeFrozen = true
        }
        rebuildBase()
        invalidate()
    }

    private fun scheduleHoldCheck(x: Float, y: Float) {
        if (!holdToStraighten) return
        if (hypot(x - holdX, y - holdY) > 8f * density) {
            holdX = x
            holdY = y
            holdHandler.removeCallbacks(holdRunnable)
            holdHandler.postDelayed(holdRunnable, HOLD_MS)
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

    private fun pageX(x: Float): Float = (x - ox) / sc - offX
    private fun worldX(x: Float): Float = (x - ox) / sc
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
            if (gestureErasing && gestureRemovedItems.isNotEmpty()) {
                undoStack.add(HistoryAction.ItemsErase(ArrayList(gestureRemovedItems)))
            }
            gestureRemovedItems.clear()
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
        invalidate()
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
        if (tabTouch(e)) return true
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

        if (tool == Tool.TEXT || tool == Tool.OBJECT) return handleItemTouch(e)

        if (!isStylus && !fingerDraw) return true

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                activateAt(e.x)
                requestUnbufferedDispatch(e)
                strokeActive = true
                strokeFromStylus = isStylus
                straightLive = false
                shapeFrozen = false
                gestureErasing = tool == Tool.ERASER ||
                    type == MotionEvent.TOOL_TYPE_ERASER ||
                    (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                gestureRemoved.clear()
                gestureRemovedItems.clear()
                val gx = pageX(e.x)
                val gy = pageY(e.y)
                if (gestureErasing) {
                    if (eraseAt(gx, gy)) rebuildBase()
                } else {
                    val hl = tool == Tool.HIGHLIGHTER
                    val sens = PreviewView.penSens(penKind, pressureSensitivity / 100f)
                    val s = if (hl) Stroke(hlColor, hlWidthDp * UNIT, true, 0f, hlKind)
                    else Stroke(penColor, penWidthDp * UNIT, false, sens, penKind)
                    s.add(gx, gy, if (hl) 0.5f else normP(e.pressure))
                    current = s
                    drawnUpTo = 0
                    holdX = e.x
                    holdY = e.y
                    if (holdToStraighten) {
                        holdHandler.removeCallbacks(holdRunnable)
                        holdHandler.postDelayed(holdRunnable, HOLD_MS)
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
                        if (shapeFrozen) {
                            // Şekil oturtuldu; kalem kalkana kadar değişmez.
                        } else if (straightLive) {
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
                val isTap = e.actionMasked == MotionEvent.ACTION_UP && isStylus && penDoubleTap &&
                    (e.eventTime - e.downTime) < 260L && hypot(e.x - downX, e.y - downY) < 9f * density
                val twice = isTap && e.eventTime - lastTapTime < 420L &&
                    hypot(e.x - lastTapX, e.y - lastTapY) < 48f * density
                if (twice) {
                    // İkinci hızlı dokunuş: iki nokta/silme izini geri al, kalem-silgi geçişi yap.
                    val ps = lastTapStroke
                    if (ps != null) {
                        strokes.remove(ps)
                        undoStack.removeAll { it is HistoryAction.Add && it.stroke === ps }
                    }
                    val pe = lastTapErase
                    if (pe != null) for ((idx, st) in pe.asReversed()) strokes.add(min(idx, strokes.size), st)
                    for ((idx, st) in gestureRemoved.asReversed()) strokes.add(min(idx, strokes.size), st)
                    gestureRemoved.clear()
                    for ((idx, it) in gestureRemovedItems.asReversed()) page.items.add(min(idx, page.items.size), it)
                    gestureRemovedItems.clear()
                    current = null
                    straightLive = false
                    lastTapStroke = null
                    lastTapErase = null
                    lastTapTime = 0L
                    rebuildBase()
                    onChanged?.invoke()
                    togglePenEraser()
                    return true
                }
                lastTapStroke = null
                lastTapErase = null
                if (isTap) {
                    lastTapTime = e.eventTime
                    lastTapX = e.x
                    lastTapY = e.y
                    lastTapStroke = current
                    lastTapErase = if (gestureRemoved.isEmpty()) null else ArrayList(gestureRemoved)
                } else {
                    lastTapTime = 0L
                }
                if (gestureErasing) {
                    if (gestureRemoved.isNotEmpty() || gestureRemovedItems.isNotEmpty()) {
                        if (gestureRemoved.isNotEmpty()) undoStack.add(HistoryAction.Erase(ArrayList(gestureRemoved)))
                        if (gestureRemovedItems.isNotEmpty()) undoStack.add(HistoryAction.ItemsErase(ArrayList(gestureRemovedItems)))
                        redoStack.clear()
                        onChanged?.invoke()
                    }
                    gestureRemoved.clear()
                    gestureRemovedItems.clear()
                } else {
                    val s = current
                    if (s != null && scribbleErase && !s.highlighter && !straightLive && !shapeFrozen &&
                        isScribble(s) && eraseUnderScribble(s)
                    ) {
                        return true
                    }
                    if (s != null) {
                        var needsBake = s.highlighter || straightLive || s.kind == 3
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

    /** Aynı bölgede ileri geri sık yön değiştiren, kısa ve sık bir çizgi mi (karalama)? */
    private fun isScribble(s: Stroke): Boolean {
        val n = s.xs.size
        if (n < 12) return false
        var minX = s.xs[0]
        var maxX = minX
        var minY = s.ys[0]
        var maxY = minY
        var len = 0f
        for (i in 0 until n) {
            minX = min(minX, s.xs[i]); maxX = max(maxX, s.xs[i])
            minY = min(minY, s.ys[i]); maxY = max(maxY, s.ys[i])
            if (i > 0) len += hypot(s.xs[i] - s.xs[i - 1], s.ys[i] - s.ys[i - 1])
        }
        val horizontal = (maxX - minX) >= (maxY - minY)
        val ext = if (horizontal) maxX - minX else maxY - minY
        if (ext < 60f || len < 3.2f * ext) return false
        val lo = if (horizontal) minX else minY
        val hi = if (horizontal) maxX else maxY
        var px = s.xs[0]
        var py = s.ys[0]
        var ldx = 0f
        var ldy = 0f
        var total = 0
        var edge = 0
        var lastSide = 0
        var alternating = 0
        for (i in 1 until n) {
            val dx = s.xs[i] - px
            val dy = s.ys[i] - py
            val m = hypot(dx, dy)
            if (m < 10f) continue
            if (ldx != 0f || ldy != 0f) {
                val dot = dx * ldx + dy * ldy
                if (dot / (m * hypot(ldx, ldy)) < -0.3f) {
                    total++
                    val a = if (horizontal) px else py
                    val side = if (a - lo < 0.22f * ext) -1 else if (hi - a < 0.22f * ext) 1 else 0
                    if (side != 0) {
                        edge++
                        if (side != lastSide) alternating++
                        lastSide = side
                    }
                }
            }
            ldx = dx
            ldy = dy
            px = s.xs[i]
            py = s.ys[i]
        }
        return edge >= 4 && edge >= 0.75f * total && alternating >= 3
    }

    /** Karalamanın kapladığı alandaki çizgileri ve yazıları siler; silinecek bir şey yoksa false döner. */
    private fun eraseUnderScribble(s: Stroke): Boolean {
        var minX = s.xs[0]
        var maxX = minX
        var minY = s.ys[0]
        var maxY = minY
        for (i in s.xs.indices) {
            minX = min(minX, s.xs[i]); maxX = max(maxX, s.xs[i])
            minY = min(minY, s.ys[i]); maxY = max(maxY, s.ys[i])
        }
        val pad = 26f
        val victims = ArrayList<Pair<Int, Stroke>>()
        if (eraseInk) {
            for (i in strokes.indices.reversed()) {
                val t = strokes[i]
                var inside = 0
                for (k in t.xs.indices) {
                    if (t.xs[k] in (minX - pad)..(maxX + pad) && t.ys[k] in (minY - pad)..(maxY + pad)) inside++
                }
                if (t.xs.isNotEmpty() && inside >= 0.5f * t.xs.size) victims.add(Pair(i, t))
            }
        }
        val itemVictims = ArrayList<Pair<Int, PageItem>>()
        if (eraseText) {
            for (k in page.items.indices.reversed()) {
                val it = page.items[k]
                if (it.type == PageItem.TEXT && it.x in (minX - pad)..(maxX + pad) && it.y in (minY - pad)..(maxY + pad)) {
                    itemVictims.add(Pair(k, it))
                }
            }
        }
        if (victims.isEmpty() && itemVictims.isEmpty()) return false
        for ((i, _) in victims) strokes.removeAt(i)
        for ((k, it) in itemVictims) {
            page.items.removeAt(k)
            if (selectedItem === it) selectedItem = null
        }
        if (victims.isNotEmpty()) undoStack.add(HistoryAction.Erase(victims))
        if (itemVictims.isNotEmpty()) undoStack.add(HistoryAction.ItemsErase(itemVictims))
        redoStack.clear()
        current = null
        straightLive = false
        rebuildBase()
        onChanged?.invoke()
        return true
    }

    private fun eraseAt(x: Float, y: Float): Boolean {
        val r = eraserRadiusDp * density / sc
        var removedAny = false
        if (eraseInk) {
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
        }
        var k = page.items.size - 1
        while (k >= 0) {
            val it = page.items[k]
            val allowed = when (it.type) {
                PageItem.TEXT -> eraseText
                PageItem.STICKER -> eraseSticker
                else -> eraseImage
            }
            if (allowed && itemNear(it, x, y, r)) {
                gestureRemovedItems.add(Pair(k, it))
                page.items.removeAt(k)
                if (selectedItem === it) selectedItem = null
                removedAny = true
            }
            k--
        }
        return removedAny
    }

    private fun itemNear(it: PageItem, x: Float, y: Float, r: Float): Boolean {
        renderer.measureItem(it)
        val a = rad(-it.rot)
        val dx = x - it.x
        val dy = y - it.y
        val lx = dx * Math.cos(a).toFloat() - dy * Math.sin(a).toFloat()
        val ly = dx * Math.sin(a).toFloat() + dy * Math.cos(a).toFloat()
        val ex = max(abs(lx) - it.w / 2f, 0f)
        val ey = max(abs(ly) - it.h / 2f, 0f)
        return hypot(ex, ey) <= r
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
            is HistoryAction.ItemAdd -> {
                page.items.remove(a.item)
                if (selectedItem === a.item) selectedItem = null
            }
            is HistoryAction.ItemRemove -> page.items.add(min(a.index, page.items.size), a.item)
            is HistoryAction.ItemsErase -> {
                for ((idx, it) in a.removed.asReversed()) page.items.add(min(idx, page.items.size), it)
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
            is HistoryAction.ItemAdd -> page.items.add(a.item)
            is HistoryAction.ItemRemove -> {
                page.items.remove(a.item)
                if (selectedItem === a.item) selectedItem = null
            }
            is HistoryAction.ItemsErase -> {
                for ((_, it) in a.removed) page.items.remove(it)
                selectedItem = null
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
        const val SPREAD_GAP = 28f
        const val HOLD_MS = 320L
        const val TAB_OUT = 50f
        const val TAB_IN = 6f
        const val TAB_GUTTER = 68f
        const val TAB_TOP = 60f
        const val TAB_GAP = 6f
        const val TAB_H_MAX = 150f
        const val TAB_H_MIN = 44f
    }
}
