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

    /** Çizgi dengeleme (titremeyi azaltır): 0 kapalı, 100 en yüksek. */
    var stabilize = 0

    private fun addStab(s: Stroke, x: Float, y: Float, p: Float) {
        if (stabilize > 0 && s.xs.isNotEmpty() && !rulerSnap) {
            // "ip" yöntemi: çizgi ucu, kalem ucuna bağlı uzunluğu sabit bir ipin ucunda sürüklenir.
            // Küçük titremeler ipin boşluğunda kaybolur; 100'de ip yaklaşık 30 dp'dir.
            val t = stabilize.coerceIn(0, 100) / 100f
            val rope = t * 30f * density / sc
            val lx = s.xs[s.xs.size - 1]
            val ly = s.ys[s.ys.size - 1]
            val dx = x - lx
            val dy = y - ly
            val d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (d > rope && d > 0f) {
                val k = (d - rope) / d
                s.add(lx + dx * k, ly + dy * k, p)
            }
        } else {
            s.add(x, y, p)
        }
    }

    // ---- Avuç içi reddi: kalem yakındayken parmak dokunuşları yok sayılır ----
    var palmReject = true
    private var lastPenMs = 0L
    private var rejecting = false

    override fun onHoverEvent(e: MotionEvent): Boolean {
        val t = e.getToolType(0)
        if (t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER) lastPenMs = e.eventTime
        return super.onHoverEvent(e)
    }

    // ---- Sayfadan alan seçip görsel olarak almak (flaş kart için) ----
    var captureMode = false
        private set
    var onCaptured: ((Bitmap?) -> Unit)? = null
    private var capX0 = 0f
    private var capY0 = 0f
    private var capX1 = 0f
    private var capY1 = 0f
    private var capDown = false
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun startCapture(cb: (Bitmap?) -> Unit) {
        onCaptured = cb
        captureMode = true
        capDown = false
        invalidate()
    }

    fun cancelCapture() {
        if (!captureMode) return
        captureMode = false
        capDown = false
        invalidate()
        onCaptured?.invoke(null)
    }

    private fun handleCaptureTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { capX0 = e.x; capY0 = e.y; capX1 = e.x; capY1 = e.y; capDown = true }
            MotionEvent.ACTION_MOVE -> { capX1 = e.x; capY1 = e.y }
            MotionEvent.ACTION_UP -> {
                capX1 = e.x; capY1 = e.y
                capDown = false
                val b = base
                val l = Math.min(capX0, capX1).toInt().coerceIn(0, (b?.width ?: 1) - 1)
                val t = Math.min(capY0, capY1).toInt().coerceIn(0, (b?.height ?: 1) - 1)
                val r = Math.max(capX0, capX1).toInt().coerceIn(l + 1, b?.width ?: l + 1)
                val bt = Math.max(capY0, capY1).toInt().coerceIn(t + 1, b?.height ?: t + 1)
                if (b != null && r - l > 40 && bt - t > 40) {
                    val crop = Bitmap.createBitmap(b, l, t, r - l, bt - t)
                    captureMode = false
                    invalidate()
                    onCaptured?.invoke(crop)
                } else {
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> capDown = false
        }
        invalidate()
        return true
    }

    private fun drawCaptureRect(c: Canvas) {
        if (!captureMode || !capDown) return
        val l = Math.min(capX0, capX1)
        val t = Math.min(capY0, capY1)
        val r = Math.max(capX0, capX1)
        val b = Math.max(capY0, capY1)
        capPaint.style = Paint.Style.FILL
        capPaint.color = Color.argb(40, 166, 98, 106)
        c.drawRect(l, t, r, b, capPaint)
        capPaint.style = Paint.Style.STROKE
        capPaint.strokeWidth = 2f * density
        capPaint.color = Color.rgb(166, 98, 106)
        capPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
        c.drawRect(l, t, r, b, capPaint)
        capPaint.pathEffect = null
    }

    /** Yazının üstünü karalayınca altındaki çizgiler silinir. */
    var scribbleErase = true
    private var lastToggleMs = 0L
    private var lastDrawTool = Tool.PEN
    var onToolToggled: (() -> Unit)? = null

    /** Seçili metin/çıkartma/resim. */
    var selectedItem: PageItem? = null
        private set
    var onSelection: (() -> Unit)? = null

    private fun setSel(it: PageItem?) {
        if (selectedItem !== it) {
            selectedItem = it
            onSelection?.invoke()
        }
    }

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

    /** Gizleme bandını kaldırır ya da yeniden kapatır (durum, nesnenin text alanında "k" olarak tutulur). */
    private fun toggleTape(t: PageItem) {
        t.text = if (t.text == "k") "" else "k"
        rebuildBase()
        onChanged?.invoke()
        invalidate()
    }

    private var tapDX = 0f
    private var tapDY = 0f
    private var tapDT = 0L

    fun spreadLeft(): PageData = leftPage
    fun spreadRight(): PageData? = rightPage

    /** Çift sayfa görünümünde belirli sayfaların kâğıt stilini değiştirir. */
    fun setPaperFor(pages: List<PageData>, value: Paper) {
        for (pg in pages) pg.paper = value
        rebuildBase()
        onChanged?.invoke()
    }

    private var current: Stroke? = null
    private var gestureErasing = false
    private var strokeActive = false
    private var strokeFromStylus = false
    private var straightLive = false
    private val gestureRemoved = ArrayList<Pair<Int, Stroke>>()
    private val gestureAdded = ArrayList<Stroke>()
    private var lastTapAdded: List<Stroke>? = null
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

    private val tabSides = Array(4) { ArrayList<Tab>() }

    private fun gl() = if (tabSides[0].isEmpty()) 0f else TAB_GUTTER
    private fun gr() = if (tabSides[1].isEmpty()) 0f else TAB_GUTTER
    private fun gt() = if (tabSides[2].isEmpty()) 0f else TAB_GUTTER
    private fun gb() = if (tabSides[3].isEmpty()) 0f else TAB_GUTTER
    private fun gutterKey() = gl() + 2 * gr() + 3 * gt() + 5 * gb()

    /** Ayraçlar sırasıyla ve hangisinin açık olduğuyla verilir; kenar boşlukları değişirse sayfa yeniden yerleşir. */
    fun setTabs(list: List<Tab>, active: Tab?) {
        val had = gutterKey()
        tabs = list
        tabActive = active
        for (s in tabSides) s.clear()
        for (t in list) tabSides[t.side.coerceIn(0, 3)].add(t)
        if (had != gutterKey()) {
            computeTransform()
            requestBg()
            rebuildBase()
        } else {
            invalidate()
        }
    }

    private fun tabLen(side: Int): Float = if (side < 2) leftPage.height else worldW()

    private fun tabHeight(side: Int): Float {
        val n = max(1, tabSides[side].size)
        return min(TAB_H_MAX, max(TAB_H_MIN, (tabLen(side) - 2f * TAB_TOP - (n - 1) * TAB_GAP) / n))
    }

    /** Ayracın dünya koordinatındaki dikdörtgeni; açık olan dışarı doğru daha çok uzar. */
    private fun tabBox(t: Tab, out: RectF) {
        val side = t.side.coerceIn(0, 3)
        val i = tabSides[side].indexOf(t)
        val h = tabHeight(side)
        var pos = TAB_TOP
        for (k in 0..i) {
            // üst/alt kenarda ayraç iki sayfanın arasındaki cilt boşluğuna taşmaz
            if (side >= 2 && rightPage != null && pos + h > leftPage.width - 8f && pos < leftPage.width + 8f) {
                pos = leftPage.width + 8f + TAB_GAP
            }
            if (k < i) pos += h + TAB_GAP
        }
        val ext = if (t === tabActive) TAB_GUTTER else TAB_OUT
        when (side) {
            0 -> out.set(-ext, pos, TAB_IN, pos + h)
            1 -> out.set(worldW() - TAB_IN, pos, worldW() + ext, pos + h)
            2 -> out.set(pos, -ext, pos + h, TAB_IN)
            else -> out.set(pos, worldH() - TAB_IN, pos + h, worldH() + ext)
        }
    }

    private val hitRect = RectF()

    private fun tabAt(px: Float, py: Float): Tab? {
        for (t in tabs) {
            tabBox(t, hitRect)
            hitRect.inset(-TAB_GAP / 2f, -TAB_GAP / 2f)
            if (hitRect.contains(px, py)) return t
        }
        return null
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tabPath = Path()
    private val tabRadii = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

    private fun lighten(color: Int, f: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.rgb((r + (255 - r) * f).toInt(), (g + (255 - g) * f).toInt(), (b + (255 - b) * f).toInt())
    }

    private fun setRadii(side: Int) {
        val big = 22f
        val sm = 4f
        // sırayla: sol-üst, sağ-üst, sağ-alt, sol-alt
        val corner = when (side) {
            0 -> booleanArrayOf(true, false, false, true)
            1 -> booleanArrayOf(false, true, true, false)
            2 -> booleanArrayOf(true, true, false, false)
            else -> booleanArrayOf(false, false, true, true)
        }
        for (k in 0 until 4) {
            val r = if (corner[k]) big else sm
            tabRadii[2 * k] = r
            tabRadii[2 * k + 1] = r
        }
    }

    private fun drawTabs(c: Canvas) {
        if (tabs.isEmpty()) return
        c.save()
        c.translate(ox, oy)
        c.scale(sc, sc)
        for (t in tabs) {
            val side = t.side.coerceIn(0, 3)
            val on = t === tabActive
            tabBox(t, tabRect)
            val r = RectF(tabRect)
            setRadii(side)
            tabPaint.shader = null
            tabPaint.style = Paint.Style.FILL
            tabPaint.setColor(Color.argb(if (on) 70 else 42, 0, 0, 0))
            val sh = RectF(r)
            sh.offset(if (side == 1) 2f else -2f, 4f)
            tabPath.reset()
            tabPath.addRoundRect(sh, tabRadii, Path.Direction.CW)
            c.drawPath(tabPath, tabPaint)
            tabPaint.setColor(Color.BLACK)
            tabPaint.shader = LinearGradient(
                0f, r.top, 0f, r.bottom, lighten(t.color, 0.5f), t.color, Shader.TileMode.CLAMP
            )
            tabPath.reset()
            tabPath.addRoundRect(r, tabRadii, Path.Direction.CW)
            c.drawPath(tabPath, tabPaint)
            tabPaint.shader = null

            tabText.setColor(if (on) Color.rgb(35, 35, 50) else Color.argb(215, 70, 70, 85))
            tabText.typeface = Typeface.create(if (on) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            tabText.letterSpacing = 0.05f
            val cx = r.centerX()
            val cy = r.centerY()
            var label = t.label
            val horizontal = side >= 2
            val avail = (if (horizontal) r.width() else r.height()) - 26f
            if (!horizontal && r.height() < 62f) {
                var size = min(26f, r.height() * 0.62f)
                tabText.textSize = size
                while (tabText.measureText(label) > r.width() - 14f && size > 10f) {
                    size -= 1f
                    tabText.textSize = size
                }
                val fm = tabText.fontMetrics
                c.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, tabText)
            } else if (label.length <= 2 || horizontal) {
                var size = if (label.length <= 2) 34f else 26f
                tabText.textSize = size
                while (tabText.measureText(label) > avail && size > 14f) {
                    size -= 2f
                    tabText.textSize = size
                }
                while (tabText.measureText(label) > avail && label.length > 2) label = label.substring(0, label.length - 1)
                val fm = tabText.fontMetrics
                c.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, tabText)
            } else {
                var size = 28f
                tabText.textSize = size
                while (tabText.measureText(label) > avail && size > 16f) {
                    size -= 2f
                    tabText.textSize = size
                }
                while (tabText.measureText(label) > avail && label.length > 2) label = label.substring(0, label.length - 1)
                val fm = tabText.fontMetrics
                c.save()
                c.rotate(if (side == 1) -90f else 90f, cx, cy)
                c.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, tabText)
                c.restore()
            }
        }
        c.restore()
    }

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
                val t = tabAt(worldX(e.x) , pageY(e.y)) ?: return false
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
        setSel(null)
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
        switchTo(if (useRight) r else leftPage)
    }

    private fun switchTo(target: PageData) {
        val r = rightPage ?: return
        val useRight = target === r
        if (target === page) return
        page = target
        offX = if (useRight) leftPage.width + SPREAD_GAP else 0f
        strokes = target.strokes
        undoStack = target.undo
        redoStack = target.redo
    }

    /** Kenar notları için ekranın solunda/sağında ayrılan piksel genişliği. */
    private var reserveL = 0f
    private var reserveR = 0f

    fun setReserve(l: Float, r: Float) {
        if (l == reserveL && r == reserveR) return
        reserveL = l
        reserveR = r
        computeTransform()
        requestBg()
        rebuildBase()
    }

    /** Sayfayı ekrana sığdırır ve yakınlaştırmayı sıfırlar. */
    private fun computeTransform() {
        if (width <= 0 || height <= 0) return
        val m = 16f * density
        val tw = worldW() + gl() + gr()
        val th = worldH() + gt() + gb()
        val vw = width - reserveL - reserveR
        fitSc = max(0.01f, min((vw - 2 * m) / tw, (height - 2 * m) / th))
        sc = fitSc
        ox = reserveL + (vw - tw * sc) / 2f + gl() * sc
        oy = (height - th * sc) / 2f + gt() * sc
    }

    /** Yakınlaştırılmış sayfanın ekran dışına kaçmasını engeller. */
    private fun clampTransform() {
        val pw = (worldW() + gl() + gr()) * sc
        val ph = (worldH() + gt() + gb()) * sc
        val m = 16f * density
        ox = if (pw + 2 * m <= width - reserveL - reserveR) reserveL + (width - reserveL - reserveR - pw) / 2f + gl() * sc
        else ox.coerceIn(width - (worldW() + gr()) * sc - m, m + gl() * sc)
        oy = if (ph + 2 * m <= height) (height - ph) / 2f + gt() * sc
        else oy.coerceIn(height - (worldH() + gb()) * sc - m, m + gt() * sc)
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

    /** Çift sayfa görünümünde iki sayfanın toplam genişliği; tek sayfada sayfa genişliği. */
    private fun spreadW(): Float {
        val r = rightPage
        return if (r == null) page.width else leftPage.width + SPREAD_GAP + r.width
    }

    /** Çizgi sayfa sınırında kesilmez; çift sayfada iki sayfa tek yüzey gibi devam eder. */
    private fun beginPage(c: Canvas) {
        c.save()
        c.translate(ox + offX * sc, oy)
        c.scale(sc, sc)
        c.clipRect(-offX, 0f, spreadW() - offX, page.height)
        spiralClip(c, offX)
    }

    /** Katlanma yerinde kalem izi hafifçe kesilir (tel spiralde şerit geniştir); çizgi telin altından geçip öbür yanda devam eder. */
    private fun spiralClip(c: Canvas, off: Float) {
        if (rightPage == null) return
        // her cilt türünde katlanma yerinde hafif bir kesiklik olur; tel spiralde şerit daha geniştir
        val half = if (binding == 2) SPIRAL_HALF else SPINE_HALF
        val x = leftPage.width - off
        @Suppress("DEPRECATION")
        c.clipRect(x - half, -10f, x + half, 100000f, android.graphics.Region.Op.DIFFERENCE)
    }

    /** Cilt görünümü: 0 yalnız gölge, 1 düz dikiş, 2 tel spiral. */
    var binding = 1
        set(v) {
            field = v
            rebuildBase()
        }

    private val bindPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** İki sayfanın birleştiği yerde cilt çizer; yazı üzerinden kesilmeden geçer. */
    private fun drawBinding(c: Canvas) {
        val r = rightPage ?: return
        val x = leftPage.width
        val h = min(leftPage.height, r.height)
        c.save()
        c.translate(ox, oy)
        c.scale(sc, sc)
        c.clipRect(0f, 0f, x + r.width, h)
        // iç gölge
        val gw = if (binding == 0) 10f else 38f
        val ga = if (binding == 0) 20 else 70
        bindPaint.style = Paint.Style.FILL
        bindPaint.shader = LinearGradient(x - gw, 0f, x, 0f, Color.argb(0, 60, 40, 40), Color.argb(ga, 60, 40, 40), Shader.TileMode.CLAMP)
        c.drawRect(x - gw, 0f, x, h, bindPaint)
        bindPaint.shader = LinearGradient(x, 0f, x + gw, 0f, Color.argb(ga, 60, 40, 40), Color.argb(0, 60, 40, 40), Shader.TileMode.CLAMP)
        c.drawRect(x, 0f, x + gw, h, bindPaint)
        bindPaint.shader = null
        if (binding == 1) {
            // katlanma çizgisi
            bindPaint.style = Paint.Style.STROKE
            bindPaint.strokeWidth = 1.6f
            bindPaint.setColor(Color.argb(110, 70, 55, 55))
            c.drawLine(x, 0f, x, h, bindPaint)
            // gerçek dikiş: iğne delikleri ve üstten geçen iplik
            var y = 36f
            val len = 30f
            while (y + len < h - 20f) {
                bindPaint.style = Paint.Style.FILL
                bindPaint.setColor(Color.argb(170, 60, 50, 50))
                c.drawCircle(x, y, 2.6f, bindPaint)
                c.drawCircle(x, y + len, 2.6f, bindPaint)
                bindPaint.style = Paint.Style.STROKE
                bindPaint.strokeCap = Paint.Cap.ROUND
                bindPaint.strokeWidth = 4.2f
                bindPaint.setColor(Color.argb(235, 176, 142, 104))
                c.drawLine(x, y + 1f, x, y + len - 1f, bindPaint)
                bindPaint.strokeWidth = 1.4f
                bindPaint.setColor(Color.argb(200, 236, 214, 182))
                c.drawLine(x - 0.8f, y + 3f, x - 0.8f, y + len - 3f, bindPaint)
                y += len * 2f
            }
        } else if (binding == 2) {
            var y = 44f
            while (y < h - 20f) {
                bindPaint.style = Paint.Style.FILL
                bindPaint.setColor(Color.argb(150, 90, 80, 90))
                c.drawCircle(x - 20f, y, 5f, bindPaint)
                c.drawCircle(x + 20f, y, 5f, bindPaint)
                bindPaint.style = Paint.Style.STROKE
                bindPaint.strokeWidth = 5f
                bindPaint.strokeCap = Paint.Cap.ROUND
                bindPaint.setColor(Color.argb(235, 150, 150, 160))
                c.drawLine(x - 24f, y - 3f, x + 24f, y + 3f, bindPaint)
                bindPaint.strokeWidth = 2f
                bindPaint.setColor(Color.argb(200, 235, 235, 242))
                c.drawLine(x - 22f, y - 4f, x + 22f, y + 2f, bindPaint)
                y += 58f
            }
        }
        bindPaint.strokeCap = Paint.Cap.BUTT
        c.restore()
    }

    /** Arka planı, kağıdı ve tüm biten çizgileri bitmap'e baştan çizer. */
    private fun rebuildBase() {
        val c = baseCanvas ?: return
        c.drawColor(Look.canvas)
        val list = listOfNotNull(leftPage, rightPage)
        val total = spreadW()
        // Gölge tüm açık defter için tek parça çizilir: iki sayfa ayrı kâğıtlar gibi görünmesin
        run {
            val sl = ox
            val hgt = worldH() * sc
            for (g in 1..7) {
                shadowPaint.setColor(Color.argb(13, 70, 40, 45))
                val e = g * 1.6f * density
                c.drawRoundRect(
                    sl - e, oy + 2f * density - e * 0.4f, sl + total * sc + e, oy + hgt + 4f * density + e,
                    e + 3f * density, e + 3f * density, shadowPaint
                )
            }
        }
        for ((k, pg) in list.withIndex()) {
            val off = if (k == 0) 0f else leftPage.width + SPREAD_GAP
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
            renderer.drawItems(c, pg, false)
            c.restore()
        }
        // Çizgiler tüm yüzeye çizilir: bir sayfadan taşan yazı öbür sayfada devam eder
        for ((k, pg) in list.withIndex()) {
            val off = if (k == 0) 0f else leftPage.width + SPREAD_GAP
            c.save()
            c.translate(ox + off * sc, oy)
            c.scale(sc, sc)
            c.clipRect(-off, 0f, total - off, pg.height)
            spiralClip(c, off)
            for (s in pg.strokes) drawStroke(c, s)
            c.restore()
        }
        for ((k, pg) in list.withIndex()) {
            val off = if (k == 0) 0f else leftPage.width + SPREAD_GAP
            c.save()
            c.translate(ox + off * sc, oy)
            c.scale(sc, sc)
            c.clipRect(0f, 0f, pg.width, pg.height)
            renderer.drawItems(c, pg, true)
            c.restore()
        }
        // cilt en üstte çizilir: gerçek bir ajandada tel ve dikiş yazının üzerindedir
        if (rightPage != null) drawBinding(c)
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
        drawLasso(c)
        drawCutLasso(c)
        drawRuler(c)
        drawLaser(c)
        drawCaptureRect(c)
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
        if (dragMode == 1 && dragMoved && dragItem === it) renderer.drawItem(c, it)
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
        // yeni eklenen görsel ve çıkartma çizgilerin önüne gelir; isteyen arkaya gönderebilir
        if (it.kind == STICKY_KIND && it.type == PageItem.IMAGE) it.above = false
        else if (it.type == PageItem.IMAGE || it.type == PageItem.STICKER) it.above = true
        renderer.measureItem(it)
        if (topLeft) {
            it.x += it.w / 2f
            it.y += it.h / 2f
        }
        keepInPage(it)
        page.items.add(it)
        undoStack.add(HistoryAction.ItemAdd(it))
        redoStack.clear()
        setSel(it)
        rebuildBase()
        onChanged?.invoke()
    }

    /** Nesneyi tamamen sayfa sınırları içinde tutar; diğer sayfanın altında kalmasın. */
    private fun keepInPage(it: PageItem) {
        val r = Math.toRadians(it.rot.toDouble())
        val cs = Math.abs(Math.cos(r)).toFloat()
        val sn = Math.abs(Math.sin(r)).toFloat()
        val hw = (it.w * cs + it.h * sn) / 2f
        val hh = (it.w * sn + it.h * cs) / 2f
        it.x = if (hw * 2f >= page.width) page.width / 2f else it.x.coerceIn(hw, page.width - hw)
        it.y = if (hh * 2f >= page.height) page.height / 2f else it.y.coerceIn(hh, page.height - hh)
    }

    /** Yapışkan notlar masaya (ekranın kenarına) alınırken çağrılır: nesne, ekran koordinatındaki merkeziyle. */
    var onStickyToDesk: ((PageItem, Float, Float) -> Unit)? = null

    /** Sürüklemeyi bitirir: sayfa dışına bırakılan yapışkan not masaya geçer, öbür sayfaya bırakılan nesne o sayfaya taşınır. */
    private fun finishItemDrag(it: PageItem) {
        val centerOut = it.x < -offX || it.x > spreadW() - offX || it.y < 0f || it.y > page.height
        // yapışkan notun dörtte biri sayfanın dışına taştıysa da masaya geçer
        val over = max(
            max(-offX - (it.x - it.w / 2f), (it.x + it.w / 2f) - (spreadW() - offX)),
            max(-(it.y - it.h / 2f), (it.y + it.h / 2f) - page.height)
        )
        val outside = centerOut || (it.kind == STICKY_KIND && it.type == PageItem.IMAGE && over > 0.25f * it.w)
        if (outside) {
            val cb = onStickyToDesk
            if (it.kind == STICKY_KIND && it.type == PageItem.IMAGE && cb != null) {
                val sx = ox + (it.x + offX) * sc
                val sy = oy + it.y * sc
                val idx = page.items.indexOf(it)
                if (idx >= 0) {
                    page.items.removeAt(idx)
                    undoStack.add(HistoryAction.ItemRemove(it, idx))
                    redoStack.clear()
                }
                setSel(null)
                rebuildBase()
                cb(it, sx, sy)
                return
            }
            keepInPage(it)
            rebuildBase()
            return
        }
        val other = otherSpreadPage()
        if (other != null && (it.x < 0f || it.x > page.width)) {
            val idx = page.items.indexOf(it)
            val src = page
            val newX = if (src === leftPage) it.x - leftPage.width - SPREAD_GAP else it.x + leftPage.width + SPREAD_GAP
            if (idx >= 0) {
                src.items.removeAt(idx)
                src.undo.add(HistoryAction.ItemRemove(it, idx))
                src.redo.clear()
            }
            it.x = newX
            other.items.add(it)
            other.undo.add(HistoryAction.ItemAdd(it))
            other.redo.clear()
            switchTo(other)
            setSel(it)
        }
        keepInPage(it)
        rebuildBase()
    }

    /** Ekran noktası iki sayfanın üzerinde mi? */
    fun screenInsideSpread(sx: Float, sy: Float): Boolean {
        val wx = (sx - ox) / sc
        val wy = (sy - oy) / sc
        return wx >= 0f && wx <= worldW() && wy >= 0f && wy <= worldH()
    }

    /** Nesneyi ekrandaki bir noktaya, o noktadaki sayfaya ekler. */
    fun addItemAtScreen(it: PageItem, sx: Float, sy: Float) {
        activateAt(sx)
        it.x = pageX(sx)
        it.y = pageY(sy)
        addItem(it)
    }

    private fun layerInfo(it: PageItem) {
        onInfo?.invoke("Nesne sırası: " + (page.items.indexOf(it) + 1) + " / " + page.items.size + (if (it.above) " (çizginin üstünde)" else " (çizginin altında)"))
    }

    /** Sıra değişir; en uçta ise çizgilerin üstüne/altına geçer. */
    private fun reorder(toFront: Boolean, step: Boolean) {
        val it = selectedItem ?: return
        val i = page.items.indexOf(it)
        if (i < 0) return
        val n = page.items.size
        val j = if (step) (if (toFront) i + 1 else i - 1) else (if (toFront) n - 1 else 0)
        if (j in 0 until n && j != i) {
            page.items.removeAt(i)
            page.items.add(j, it)
        }
        // en uçta ya da en öne/en arkaya basıldığında çizgilerin de önüne/arkasına geçer
        if (!step || j !in 0 until n) it.above = toFront
        rebuildBase()
        onChanged?.invoke()
        layerInfo(it)
    }

    fun itemForward() = reorder(true, true)
    fun itemBackward() = reorder(false, true)
    fun itemToFront() = reorder(true, false)
    fun itemToBack() = reorder(false, false)

    /** Nesneyi çizgilerin üstüne ya da altına alır. */
    fun toggleAbove() {
        val it = selectedItem ?: return
        it.above = !it.above
        rebuildBase()
        onChanged?.invoke()
        layerInfo(it)
    }

    fun duplicateSelected() {
        val it = selectedItem ?: return
        val c = it.copy()
        c.x += 40f
        c.y += 40f
        page.items.add(c)
        undoStack.add(HistoryAction.ItemAdd(c))
        redoStack.clear()
        setSel(c)
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
        setSel(null)
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
                    val rs = handleAt(sel, 1f, 1f)
                    val dDel = hypot(gx - del[0], gy - del[1])
                    val dRs = hypot(gx - rs[0], gy - rs[1])
                    // küçük nesnelerde iki tutamaç üst üste biner; en yakın olan seçilir, silme yalnız açıkça ona dokunulunca
                    if (dDel <= hr && dDel < dRs) {
                        deleteSelected()
                        return true
                    }
                    if (dRs <= hr) {
                        dragMode = 2
                        dragItem = sel
                        dist0 = max(1f, hypot(gx - sel.x, gy - sel.y))
                        ang0 = Math.toDegrees(Math.atan2((gy - sel.y).toDouble(), (gx - sel.x).toDouble())).toFloat()
                        w0 = sel.w
                        size0 = sel.size
                        rowH0 = sel.rowH
                        rot0 = sel.rot
                        return true
                    }
                }
                val hit = page.items.lastOrNull { itemHit(it, gx, gy) }
                if (hit != null) {
                    tapOnSelected = hit === selectedItem
                    setSel(hit)
                    dragItem = hit
                    dragMode = 1
                    dragSx = gx
                    dragSy = gy
                    itemX0 = hit.x
                    itemY0 = hit.y
                } else {
                    setSel(null)
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
                        invalidate()
                    }
                } else if (dragMode == 2 && it != null) {
                    dragMoved = true
                    val dist = max(1f, hypot(gx - it.x, gy - it.y))
                    val ratio = (dist / dist0).coerceIn(0.2f, 8f)
                    val ang = Math.toDegrees(Math.atan2((gy - it.y).toDouble(), (gx - it.x).toDouble())).toFloat()
                    it.rot = if (it.type == PageItem.TABLE) 0f else rot0 + (ang - ang0)
                    val aspect = if (it.w > 0f) it.h / it.w else 1f
                    if (it.type == PageItem.TEXT) {
                        it.w = max(60f, w0 * ratio)
                        it.size = (size0 * ratio).coerceIn(10f, 400f)
                    } else if (it.type == PageItem.TABLE) {
                        it.w = max(160f, w0 * ratio)
                        it.size = (size0 * ratio).coerceIn(10f, 120f)
                        it.rowH = max(30f, rowH0 * ratio)
                        renderer.measureItem(it)
                    } else {
                        it.w = max(70f, w0 * ratio)
                        it.h = it.w * aspect
                    }
                    keepInPage(it)
                    rebuildBase()
                } else if (createPending && hypot(e.x - dragSx, e.y - dragSy) > 12f * density) {
                    createPending = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val it = dragItem
                if (e.actionMasked == MotionEvent.ACTION_UP) {
                    if (dragMode == 1 && dragMoved && it != null) {
                        finishItemDrag(it)
                        onChanged?.invoke()
                    } else if (dragMode != 0 && dragMoved) {
                        onChanged?.invoke()
                    } else if (dragMode == 1 && !dragMoved && it != null && it.type == PageItem.IMAGE && it.kind == TAPE_KIND) {
                        toggleTape(it)
                    } else if (dragMode == 1 && !dragMoved && tapOnSelected && it != null && it.type == PageItem.TEXT) {
                        onTextTap?.invoke(it, it.x, it.y)
                    } else if (dragMode == 1 && !dragMoved && tapOnSelected && it != null && it.type == PageItem.TABLE) {
                        renderer.measureItem(it)
                        val cell = renderer.tableCellAt(it, pageX(e.x) - (it.x - it.w / 2f), pageY(e.y) - (it.y - it.h / 2f))
                        if (cell != null) onTableTap?.invoke(it, cell.first, cell.second)
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
                commitStrokeErase()
                redoStack.clear()
            }
            gestureRemoved.clear()
            gestureAdded.clear()
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

    // ---- Kement ile görsel kesme ----
    private var cutTarget: PageItem? = null
    private val cutPts = ArrayList<Float>()
    var onImageCut: ((PageItem, FloatArray) -> Unit)? = null

    fun startImageCut(item: PageItem) {
        cutTarget = item
        cutPts.clear()
        invalidate()
        onInfo?.invoke("Kement: kesmek istediğiniz bölgeyi kalemle ya da parmakla çevreleyin")
    }

    fun cancelImageCut() {
        cutTarget = null
        cutPts.clear()
        invalidate()
    }

    private fun handleCutTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activateAt(e.x)
                cutPts.clear()
                cutPts.add(pageX(e.x)); cutPts.add(pageY(e.y))
            }
            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until e.historySize) { cutPts.add(pageX(e.getHistoricalX(h))); cutPts.add(pageY(e.getHistoricalY(h))) }
                cutPts.add(pageX(e.x)); cutPts.add(pageY(e.y))
            }
            MotionEvent.ACTION_UP -> {
                val t = cutTarget
                val pts = cutPts.toFloatArray()
                cutTarget = null
                cutPts.clear()
                if (t != null && pts.size >= 8) onImageCut?.invoke(t, pts)
            }
            MotionEvent.ACTION_CANCEL -> { cutTarget = null; cutPts.clear() }
        }
        invalidate()
        return true
    }

    private val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cutPath = Path()

    private fun drawCutLasso(c: Canvas) {
        if (cutTarget == null || cutPts.size < 4) return
        c.save()
        c.translate(ox + offX * sc, oy)
        c.scale(sc, sc)
        cutPath.reset()
        cutPath.moveTo(cutPts[0], cutPts[1])
        var i = 2
        while (i + 1 < cutPts.size) { cutPath.lineTo(cutPts[i], cutPts[i + 1]); i += 2 }
        cutPaint.color = Look.accent
        cutPaint.strokeWidth = 2.4f * density / sc
        cutPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f * density / sc, 7f * density / sc), 0f)
        c.drawPath(cutPath, cutPaint)
        cutPaint.pathEffect = null
        c.restore()
    }

    /** Tek bir görseli, bir dizi parçayla değiştirir; geri alınınca asıl görsel geri gelir. */
    fun replaceItem(old: PageItem, added: List<PageItem>) {
        val idx = page.items.indexOf(old)
        if (idx < 0) return
        page.items.removeAt(idx)
        for (a in added) {
            a.above = true
            page.items.add(a)
        }
        undoStack.add(HistoryAction.Group(emptyList(), listOf(Pair(idx, old)), emptyList(), ArrayList(added)))
        redoStack.clear()
        setSel(added.lastOrNull())
        rebuildBase()
        onChanged?.invoke()
    }

    /** Görsel öğesindeki sayfa noktasını (x, y), bitmap piksel koordinatına çevirir. */
    fun itemToBitmapPoint(it: PageItem, x: Float, y: Float, bw: Int, bh: Int): FloatArray {
        val a = rad(-it.rot)
        val dx = x - it.x
        val dy = y - it.y
        val lx = dx * Math.cos(a).toFloat() - dy * Math.sin(a).toFloat() + it.w / 2f
        val ly = dx * Math.sin(a).toFloat() + dy * Math.cos(a).toFloat() + it.h / 2f
        return floatArrayOf(lx * bw / it.w, ly * bh / it.h)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (captureMode) return handleCaptureTouch(e)
        run {
            val t0 = e.getToolType(0)
            if (t0 == MotionEvent.TOOL_TYPE_STYLUS || t0 == MotionEvent.TOOL_TYPE_ERASER) {
                lastPenMs = e.eventTime
            } else if (palmReject && !fingerDraw && t0 == MotionEvent.TOOL_TYPE_FINGER) {
                val a = e.actionMasked
                if (a == MotionEvent.ACTION_DOWN) {
                    rejecting = e.eventTime - lastPenMs < 500L || (strokeActive && strokeFromStylus)
                }
                if (rejecting) {
                    if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) rejecting = false
                    return true
                }
            }
        }
        if (cutTarget != null) return handleCutTouch(e)
        if (tabTouch(e)) return true
        if (handleMultiTouch(e)) return true

        val type = e.getToolType(0)
        val isStylus = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
        if (rulerOn && !isStylus && handleRulerTouch(e)) return true

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
        if (tool == Tool.LASER) return handleLaserTouch(e)
        if (tool == Tool.LASSO) return handleLassoTouch(e)

        if (!isStylus && !fingerDraw) {
            // parmakla kısa dokunuş: gizleme bandı kalkar / kapanır
            if (e.actionMasked == MotionEvent.ACTION_DOWN) { tapDX = e.x; tapDY = e.y; tapDT = e.eventTime }
            if (e.actionMasked == MotionEvent.ACTION_UP && hypot(e.x - tapDX, e.y - tapDY) < 14f * density && e.eventTime - tapDT < 350L) {
                activateAt(e.x)
                val gx = pageX(e.x)
                val gy = pageY(e.y)
                val t = page.items.lastOrNull { it.type == PageItem.IMAGE && it.kind == TAPE_KIND && itemHit(it, gx, gy) }
                if (t != null) toggleTape(t)
            }
            return true
        }

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
                gestureAdded.clear()
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
                    rulerSnap = rulerOn && rulerDist(gx, gy) < 44f * density / sc
                    s.add(gx, gy, if (hl) 0.5f else normP(e.pressure))
                    if (rulerSnap) snapLast(s, 0)
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
                                addStab(
                                    s, pageX(e.getHistoricalX(h)), pageY(e.getHistoricalY(h)),
                                    if (hl) 0.5f else normP(e.getHistoricalPressure(h))
                                )
                            }
                            addStab(s, pageX(e.x), pageY(e.y), if (hl) 0.5f else normP(e.pressure))
                            if (rulerSnap) snapLast(s, before)
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
                    lastTapAdded?.let { strokes.removeAll(it.toSet()) }
                    strokes.removeAll(gestureAdded.toSet())
                    gestureAdded.clear()
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
                    lastTapAdded = ArrayList(gestureAdded)
                } else {
                    lastTapTime = 0L
                }
                if (gestureErasing) {
                    if (gestureRemoved.isNotEmpty() || gestureRemovedItems.isNotEmpty()) {
                        commitStrokeErase()
                        if (gestureRemovedItems.isNotEmpty()) undoStack.add(HistoryAction.ItemsErase(ArrayList(gestureRemovedItems)))
                        redoStack.clear()
                        onChanged?.invoke()
                    }
                    gestureRemoved.clear()
                    gestureAdded.clear()
                    gestureRemovedItems.clear()
                } else {
                    val s = current
                    if (s != null && stabilize > 0 && e.actionMasked == MotionEvent.ACTION_UP && !straightLive && !shapeFrozen && !rulerSnap) {
                        // dengeleme geride kaldığı için çizginin ucu kalemin bittiği noktaya tamamlanır
                        val ex = pageX(e.x)
                        val ey = pageY(e.y)
                        val lx = s.xs[s.xs.size - 1]
                        val ly = s.ys[s.ys.size - 1]
                        val pr = s.ps[s.ps.size - 1]
                        val before = s.xs.size
                        for (k in 1..6) s.add(lx + (ex - lx) * k / 6f, ly + (ey - ly) * k / 6f, pr)
                        if (s.highlighter) {
                            for (i in before until s.xs.size) invalidateSegment(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], s.width)
                        } else {
                            drawPenIncrement(s)
                        }
                    }
                    if (s != null && scribbleErase && !s.highlighter && !straightLive && !shapeFrozen &&
                        isScribble(s) && eraseUnderScribble(s)
                    ) {
                        return true
                    }
                    if (s != null && scribbleErase && !s.highlighter && scribbleInfo.isNotEmpty()) onInfo?.invoke(scribbleInfo)
                    if (s != null) {
                        var needsBake = s.highlighter || straightLive || s.kind == 3
                        if (s.highlighter && highlighterSnap && !straightLive && nearlyStraight(s)) {
                            straighten(s)
                            needsBake = true
                        }
                        if (rightPage != null && binding != 0) {
                            // cilt çizginin üstünde durmalı: ciltten geçen çizgi sonunda yeniden çizilir
                            val spine = leftPage.width
                            var lo = Float.MAX_VALUE
                            var hi = -Float.MAX_VALUE
                            for (xv in s.xs) { val wx = xv + offX; if (wx < lo) lo = wx; if (wx > hi) hi = wx }
                            if (hi >= spine - 40f && lo <= spine + 40f) needsBake = true
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

    /** İleri geri sık yön değiştiren, kısa ve sık bir çizgi mi (karalama)? Ana ve dik eksen ayrı ayrı denenir. */
    private var scribbleInfo = ""

    /** Bir eksendeki gidiş-dönüşleri sayar: (dönüş sayısı, toplam katedilen yol). Küçük titremeler eşikle elenir. */
    private fun zigzag(a: FloatArray, ext: Float): Pair<Int, Float> {
        val h = max(14f, 0.18f * ext)
        var dir = 0
        var lo = a[0]
        var hi = a[0]
        var ex = a[0]
        var piv = a[0]
        var turns = 0
        var travel = 0f
        for (v in a) {
            when (dir) {
                0 -> {
                    lo = min(lo, v); hi = max(hi, v)
                    if (v - lo > h) { dir = 1; piv = lo; ex = v }
                    else if (hi - v > h) { dir = -1; piv = hi; ex = v }
                }
                1 -> if (v > ex) ex = v else if (ex - v > h) { turns++; travel += ex - piv; piv = ex; dir = -1; ex = v }
                else -> if (v < ex) ex = v else if (v - ex > h) { turns++; travel += piv - ex; piv = ex; dir = 1; ex = v }
            }
        }
        if (dir != 0) travel += abs(ex - piv)
        return Pair(turns, travel)
    }

    private fun isScribble(s: Stroke): Boolean {
        val n = s.xs.size
        scribbleInfo = "karalama: çizgi çok kısa"
        if (n < 12) return false
        var mx = 0f
        var my = 0f
        for (i in 0 until n) { mx += s.xs[i]; my += s.ys[i] }
        mx /= n; my /= n
        var sxx = 0f; var syy = 0f; var sxy = 0f
        for (i in 0 until n) {
            val dx = s.xs[i] - mx; val dy = s.ys[i] - my
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        val th = 0.5 * Math.atan2((2f * sxy).toDouble(), (sxx - syy).toDouble())
        val ux = Math.cos(th).toFloat()
        val uy = Math.sin(th).toFloat()
        val pa = FloatArray(n) { (s.xs[it] - mx) * ux + (s.ys[it] - my) * uy }
        val pb = FloatArray(n) { -(s.xs[it] - mx) * uy + (s.ys[it] - my) * ux }
        val extA = pa.max() - pa.min()
        val extB = pb.max() - pb.min()
        if (max(extA, extB) < 50f) return false
        val za = zigzag(pa, extA)
        val zb = zigzag(pb, extB)
        scribbleInfo = "karalama: eksen1 dönüş=${za.first} yol=${(za.second / max(1f, extA)).toInt()}x, eksen2 dönüş=${zb.first} yol=${(zb.second / max(1f, extB)).toInt()}x"
        val okA = extA >= 40f && za.first >= 3 && za.second >= 3.2f * extA
        val okB = extB >= 40f && zb.first >= 3 && zb.second >= 3.2f * extB
        return okA || okB
    }

    /** Karalamanın yolunun hemen çevresindeki çizgileri ve yazıları siler; silinecek bir şey yoksa false döner. */
    private fun eraseUnderScribble(s: Stroke): Boolean {
        val cell = 16f
        val gw = (page.width / cell).toInt() + 6
        val gh = (page.height / cell).toInt() + 6
        val mark = BooleanArray(gw * gh)
        val radius = max(26f, s.width * 1.6f)
        val rc = (radius / cell).toInt() + 1
        fun stampAt(x: Float, y: Float) {
            val cx = (x / cell).toInt() + 2
            val cy = (y / cell).toInt() + 2
            for (gy in cy - rc..cy + rc) for (gx in cx - rc..cx + rc) {
                if (gx < 0 || gy < 0 || gx >= gw || gy >= gh) continue
                val ddx = (gx - 2 + 0.5f) * cell - x
                val ddy = (gy - 2 + 0.5f) * cell - y
                if (ddx * ddx + ddy * ddy <= radius * radius) mark[gy * gw + gx] = true
            }
        }
        for (i in 1 until s.xs.size) {
            val len = hypot(s.xs[i] - s.xs[i - 1], s.ys[i] - s.ys[i - 1])
            val steps = max(1, (len / 8f).toInt())
            for (k in 0..steps) {
                val t = k / steps.toFloat()
                stampAt(s.xs[i - 1] + (s.xs[i] - s.xs[i - 1]) * t, s.ys[i - 1] + (s.ys[i] - s.ys[i - 1]) * t)
            }
        }
        fun marked(x: Float, y: Float): Boolean {
            val gx = (x / cell).toInt() + 2
            val gy = (y / cell).toInt() + 2
            return gx in 0 until gw && gy in 0 until gh && mark[gy * gw + gx]
        }
        val victims = ArrayList<Pair<Int, Stroke>>()
        if (eraseInk) {
            for (i in strokes.indices.reversed()) {
                val t = strokes[i]
                if (t.xs.isEmpty()) continue
                var inside = 0
                for (k in t.xs.indices) if (marked(t.xs[k], t.ys[k])) inside++
                if (inside >= 0.5f * t.xs.size) victims.add(Pair(i, t))
            }
        }
        val itemVictims = ArrayList<Pair<Int, PageItem>>()
        if (eraseText) {
            for (k in page.items.indices.reversed()) {
                val it = page.items[k]
                if (it.type == PageItem.TEXT && marked(it.x, it.y)) itemVictims.add(Pair(k, it))
            }
        }
        if (victims.isEmpty() && itemVictims.isEmpty()) return false
        for ((i, _) in victims) strokes.removeAt(i)
        for ((k, it) in itemVictims) {
            page.items.removeAt(k)
            if (selectedItem === it) setSel(null)
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
                val rr = r + s.width / 2f
                if (hits(s, x, y, rr)) {
                    val pieces = cutStroke(s, x, y, rr)
                    if (pieces != null) {
                        strokes.removeAt(i)
                        // bu hareketin kendi parçasıysa özgün çizgi sayılmaz
                        if (!gestureAdded.remove(s)) gestureRemoved.add(Pair(i, s))
                        for (pc in pieces) {
                            strokes.add(pc)
                            gestureAdded.add(pc)
                        }
                        removedAny = true
                    }
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
                if (selectedItem === it) setSel(null)
                removedAny = true
            }
            k--
        }
        return removedAny
    }

    /** Gerçek silgi gibi yalnız silginin değdiği kısmı keser; geriye kalan parçaları döndürür. Hiçbir nokta silinmiyorsa null. */
    private fun cutStroke(s: Stroke, x: Float, y: Float, r: Float): List<Stroke>? {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val ps = ArrayList<Float>()
        val step = max(r * 0.5f, 2f)
        for (k in s.xs.indices) {
            if (k > 0) {
                val ax = s.xs[k - 1]; val ay = s.ys[k - 1]; val ap = s.ps[k - 1]
                val d = hypot(s.xs[k] - ax, s.ys[k] - ay)
                if (d > step) {
                    val m = Math.ceil((d / step).toDouble()).toInt()
                    for (j in 1 until m) {
                        val t = j.toFloat() / m
                        xs.add(ax + (s.xs[k] - ax) * t)
                        ys.add(ay + (s.ys[k] - ay) * t)
                        ps.add(ap + (s.ps[k] - ap) * t)
                    }
                }
            }
            xs.add(s.xs[k]); ys.add(s.ys[k]); ps.add(s.ps[k])
        }
        val keep = BooleanArray(xs.size) { hypot(xs[it] - x, ys[it] - y) > r }
        if (keep.all { it }) return null
        val out = ArrayList<Stroke>()
        var cur: Stroke? = null
        for (k in xs.indices) {
            if (keep[k]) {
                if (cur == null) cur = Stroke(s.color, s.width, s.highlighter, s.sens, s.kind)
                cur.add(xs[k], ys[k], ps[k])
            } else {
                if (cur != null && cur.xs.size >= 2) out.add(cur)
                cur = null
            }
        }
        if (cur != null && cur.xs.size >= 2) out.add(cur)
        return out
    }

    /** Silme hareketini geri alma listesine yazar; çizgiler kesildiyse kalan parçalarla birlikte tek işlem olur. */
    private fun commitStrokeErase() {
        if (gestureRemoved.isEmpty()) return
        if (gestureAdded.isEmpty()) undoStack.add(HistoryAction.Erase(ArrayList(gestureRemoved)))
        else undoStack.add(HistoryAction.Group(ArrayList(gestureRemoved), emptyList(), ArrayList(gestureAdded), emptyList()))
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

    /** Çift sayfada en son yapılan işlem hangi sayfadaysa geri alma ona uygulanır. */
    private fun pickLatest(redoSide: Boolean) {
        val r = rightPage ?: return
        val a = if (redoSide) leftPage.redo else leftPage.undo
        val b = if (redoSide) r.redo else r.undo
        val sa = a.lastOrNull()?.seq ?: -1L
        val sb = b.lastOrNull()?.seq ?: -1L
        if (sa < 0 && sb < 0) return
        switchTo(if (sb > sa) r else leftPage)
    }

    fun undo() {
        pickLatest(false)
        if (undoStack.isEmpty()) return
        val a = undoStack.removeAt(undoStack.size - 1)
        when (a) {
            is HistoryAction.Add -> strokes.remove(a.stroke)
            is HistoryAction.Move -> { shift(a.strokes, a.items, -a.dx, -a.dy); clearLasso() }
            is HistoryAction.Scale -> { scaleSel(a.strokes, a.items, 1f / a.fx, 1f / a.fy, a.ax, a.ay); clearLasso() }
            is HistoryAction.Group -> {
                strokes.removeAll(a.addedS.toSet()); page.items.removeAll(a.addedI.toSet())
                for ((idx, s) in a.removedS.asReversed()) strokes.add(min(idx, strokes.size), s)
                for ((idx, it) in a.removedI.asReversed()) page.items.add(min(idx, page.items.size), it)
                clearLasso(); setSel(null)
            }
            is HistoryAction.Erase -> {
                for ((idx, s) in a.removed.asReversed()) strokes.add(min(idx, strokes.size), s)
            }
            is HistoryAction.ItemAdd -> {
                page.items.remove(a.item)
                if (selectedItem === a.item) setSel(null)
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
        pickLatest(true)
        if (redoStack.isEmpty()) return
        val a = redoStack.removeAt(redoStack.size - 1)
        when (a) {
            is HistoryAction.Add -> strokes.add(a.stroke)
            is HistoryAction.Move -> { shift(a.strokes, a.items, a.dx, a.dy); clearLasso() }
            is HistoryAction.Scale -> { scaleSel(a.strokes, a.items, a.fx, a.fy, a.ax, a.ay); clearLasso() }
            is HistoryAction.Group -> {
                for ((_, s) in a.removedS) strokes.remove(s)
                for ((_, it) in a.removedI) page.items.remove(it)
                strokes.addAll(a.addedS); page.items.addAll(a.addedI)
                clearLasso(); setSel(null)
            }
            is HistoryAction.Erase -> {
                for ((_, s) in a.removed) strokes.remove(s)
            }
            is HistoryAction.ItemAdd -> page.items.add(a.item)
            is HistoryAction.ItemRemove -> {
                page.items.remove(a.item)
                if (selectedItem === a.item) setSel(null)
            }
            is HistoryAction.ItemsErase -> {
                for ((_, it) in a.removed) page.items.remove(it)
                setSel(null)
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


    // ---- Lazer işaretçi: iz bırakmaz, kısa sürede solar ----
    private val laserX = ArrayList<Float>()
    private val laserY = ArrayList<Float>()
    private val laserT = ArrayList<Long>()
    var laserColor = Color.rgb(230, 30, 40)
    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    private fun handleLaserTouch(e: MotionEvent): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { laserX.add(Float.NaN); laserY.add(Float.NaN); laserT.add(now) }
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
            for (h in 0 until e.historySize) { laserX.add(e.getHistoricalX(h)); laserY.add(e.getHistoricalY(h)); laserT.add(now) }
            laserX.add(e.x); laserY.add(e.y); laserT.add(now)
        }
        postInvalidateOnAnimation()
        return true
    }

    private fun drawLaser(c: Canvas) {
        if (laserT.isEmpty()) return
        val now = android.os.SystemClock.uptimeMillis()
        while (laserT.isNotEmpty() && now - laserT[0] > LASER_MS) { laserX.removeAt(0); laserY.removeAt(0); laserT.removeAt(0) }
        for (i in 1 until laserT.size) {
            if (laserX[i].isNaN() || laserX[i - 1].isNaN()) continue
            val age = (now - laserT[i]).toFloat() / LASER_MS
            val a = ((1f - age) * 255f).toInt().coerceIn(0, 255)
            laserPaint.setColor(laserColor); laserPaint.alpha = (a * 0.35f).toInt()
            laserPaint.strokeWidth = 18f * density
            c.drawLine(laserX[i - 1], laserY[i - 1], laserX[i], laserY[i], laserPaint)
            laserPaint.setColor(laserColor); laserPaint.alpha = a
            laserPaint.strokeWidth = 7f * density
            c.drawLine(laserX[i - 1], laserY[i - 1], laserX[i], laserY[i], laserPaint)
        }
        if (laserT.isNotEmpty()) postInvalidateOnAnimation()
    }

    // ---- Lasso: serbest çevreleme ile çizgi ve nesne seçimi, taşıma, silme, çoğaltma ----
    private val lassoXs = ArrayList<Float>()
    private val lassoYs = ArrayList<Float>()
    private var lassoDrawing = false
    private var clipS: List<Stroke> = emptyList()
    private var clipI: List<PageItem> = emptyList()
    private val lassoStrokes = ArrayList<Stroke>()
    private val lassoItems = ArrayList<PageItem>()
    private val lassoBox = RectF()
    private var lassoMoving = false
    private var lassoLastX = 0f
    private var lassoLastY = 0f
    private var lassoDX = 0f
    private var lassoDY = 0f
    private var lassoDownT = 0L
    private var lassoMoved = false
    var onLassoMenu: ((Float, Float) -> Unit)? = null
    var onLassoCrossed: (() -> Unit)? = null
    private val lassoHalo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }

    fun hasLasso() = lassoStrokes.isNotEmpty() || lassoItems.isNotEmpty()

    fun clearLasso() {
        lassoStrokes.clear(); lassoItems.clear(); lassoDrawing = false; lassoMoving = false
        lassoXs.clear(); lassoYs.clear()
        invalidate()
    }

    private fun shift(ss: List<Stroke>, its: List<PageItem>, dx: Float, dy: Float) {
        for (s in ss) for (i in s.xs.indices) { s.xs[i] = s.xs[i] + dx; s.ys[i] = s.ys[i] + dy }
        for (it in its) { it.x += dx; it.y += dy }
        rebuildBase()
        onChanged?.invoke()
    }

    private fun inPoly(x: Float, y: Float): Boolean {
        var inside = false
        var j = lassoXs.size - 1
        for (i in lassoXs.indices) {
            val xi = lassoXs[i]; val yi = lassoYs[i]; val xj = lassoXs[j]; val yj = lassoYs[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private fun computeLassoBox() {
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (s in lassoStrokes) for (i in s.xs.indices) {
            val r = s.width / 2f
            x0 = min(x0, s.xs[i] - r); x1 = max(x1, s.xs[i] + r); y0 = min(y0, s.ys[i] - r); y1 = max(y1, s.ys[i] + r)
        }
        for (it in lassoItems) {
            renderer.measureItem(it)
            val r = max(it.w, it.h) / 2f
            x0 = min(x0, it.x - r); x1 = max(x1, it.x + r); y0 = min(y0, it.y - r); y1 = max(y1, it.y + r)
        }
        lassoBox.set(x0 - 12f, y0 - 12f, x1 + 12f, y1 + 12f)
    }

    /** false: bölgeye değen her çizgi/nesne bütün olarak seçilir. true: çizgiler bölge sınırından kesilir, yalnız içerideki parça seçilir. */
    var lassoCut = false

    private fun itemTouchesPoly(it: PageItem): Boolean {
        renderer.measureItem(it)
        if (inPoly(it.x, it.y)) return true
        val hw = it.w / 2f
        val hh = it.h / 2f
        return inPoly(it.x - hw, it.y - hh) || inPoly(it.x + hw, it.y - hh) ||
            inPoly(it.x - hw, it.y + hh) || inPoly(it.x + hw, it.y + hh)
    }

    private fun selectInLasso() {
        lassoStrokes.clear(); lassoItems.clear()
        if (lassoXs.size < 3) return
        if (!lassoCut) {
            for (s in strokes) {
                for (i in s.xs.indices) if (inPoly(s.xs[i], s.ys[i])) { lassoStrokes.add(s); break }
            }
            for (it in page.items) if (itemTouchesPoly(it)) lassoItems.add(it)
        } else {
            val removed = ArrayList<Pair<Int, Stroke>>()
            val added = ArrayList<Stroke>()
            for (idx in strokes.indices.reversed()) {
                val s = strokes[idx]
                val n = s.xs.size
                val ins = BooleanArray(n) { inPoly(s.xs[it], s.ys[it]) }
                val cnt = ins.count { it }
                if (cnt == 0) continue
                if (cnt == n) { lassoStrokes.add(s); continue }
                // Kesip böl: ardışık aynı durumdaki noktalar bir parça olur; parçalar birbirine bir nokta paylaşarak bağlanır.
                var start = 0
                val pieces = ArrayList<Stroke>()
                val pieceIn = ArrayList<Boolean>()
                for (i in 1..n) {
                    if (i == n || ins[i] != ins[start]) {
                        val p = Stroke(s.color, s.width, s.highlighter, s.sens, s.kind)
                        val from = if (start > 0) start - 1 else start
                        for (k in from until i) p.add(s.xs[k], s.ys[k], s.ps[k])
                        if (p.xs.size == 1) p.add(p.xs[0], p.ys[0], p.ps[0])
                        pieces.add(p); pieceIn.add(ins[start])
                        start = i
                    }
                }
                removed.add(Pair(idx, s))
                for ((k, p) in pieces.withIndex()) {
                    added.add(p)
                    if (pieceIn[k]) lassoStrokes.add(p)
                }
            }
            if (removed.isNotEmpty()) {
                for ((_, s) in removed) strokes.remove(s)
                strokes.addAll(added)
                undoStack.add(HistoryAction.Group(removed, emptyList(), added, emptyList()))
                redoStack.clear()
                rebuildBase()
                onChanged?.invoke()
            }
            for (it in page.items) if (inPoly(it.x, it.y)) lassoItems.add(it)
        }
        if (hasLasso()) computeLassoBox()
    }

    private var lassoScaling = false
    private var lassoSX = 1f
    private var lassoSY = 1f
    private var lassoAX = 0f
    private var lassoAY = 0f

    private fun scaleSel(ss: List<Stroke>, its: List<PageItem>, fx: Float, fy: Float, ax: Float, ay: Float) {
        for (s in ss) for (i in s.xs.indices) {
            s.xs[i] = ax + (s.xs[i] - ax) * fx
            s.ys[i] = ay + (s.ys[i] - ay) * fy
        }
        for (it in its) {
            it.x = ax + (it.x - ax) * fx
            it.y = ay + (it.y - ay) * fy
            if (it.type == PageItem.TEXT) {
                it.size = (it.size * (fx + fy) / 2f).coerceIn(8f, 400f)
            } else {
                it.w = (it.w * fx).coerceAtLeast(20f)
                it.h = (it.h * fy).coerceAtLeast(20f)
            }
        }
        rebuildBase()
        onChanged?.invoke()
    }

    private fun nearHandle(gx: Float, gy: Float): Boolean {
        val r = 34f * density / sc
        return hypot(gx - lassoBox.right, gy - lassoBox.bottom) < r
    }

    // ---- Pano: kopyala/kes/yapıştır ve sayfalar arası taşıma ----
    fun lassoCopy(cut: Boolean) {
        if (!hasLasso()) return
        clipS = lassoStrokes.map { s ->
            val n = Stroke(s.color, s.width, s.highlighter, s.sens, s.kind)
            for (i in s.xs.indices) n.add(s.xs[i], s.ys[i], s.ps[i]); n
        }
        clipI = lassoItems.map { it.copy() }
        if (cut) lassoDelete()
    }

    fun hasClip() = clipS.isNotEmpty() || clipI.isNotEmpty()

    fun pasteClip() {
        if (!hasClip()) return
        val ns = clipS.map { s ->
            val n = Stroke(s.color, s.width, s.highlighter, s.sens, s.kind)
            for (i in s.xs.indices) n.add(s.xs[i], s.ys[i], s.ps[i]); n
        }
        val ni = clipI.map { it.copy() }
        strokes.addAll(ns); page.items.addAll(ni)
        undoStack.add(HistoryAction.Group(emptyList(), emptyList(), ns, ni)); redoStack.clear()
        clearLasso()
        lassoStrokes.addAll(ns); lassoItems.addAll(ni)
        if (hasLasso()) computeLassoBox()
        rebuildBase(); onChanged?.invoke()
    }

    /** Seçimi bu sayfadan çıkarıp başka bir sayfaya koyar. Kaynak ve hedef sayfanın geri alma geçmişi tutarlı kalsın diye temizlenir. */
    fun lassoMoveTo(target: PageData, dx: Float = 0f): Boolean {
        if (!hasLasso() || target === page) return false
        val ss = ArrayList(lassoStrokes)
        val its = ArrayList(lassoItems)
        strokes.removeAll(ss.toSet()); page.items.removeAll(its.toSet())
        if (dx != 0f) {
            for (s in ss) for (i in s.xs.indices) s.xs[i] = s.xs[i] + dx
            for (it in its) it.x += dx
        }
        target.strokes.addAll(ss); target.items.addAll(its)
        for (it in its) {
            it.x = it.x.coerceIn(0f, target.width)
            it.y = it.y.coerceIn(0f, target.height)
        }
        undoStack.clear(); redoStack.clear()
        target.undo.clear(); target.redo.clear()
        clearLasso()
        rebuildBase()
        onChanged?.invoke()
        return true
    }

    fun otherSpreadPage(): PageData? = if (rightPage == null) null else if (page === leftPage) rightPage else leftPage

    private fun handleLassoTouch(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN && !(hasLasso() && lassoBox.contains(pageX(e.x), pageY(e.y))) &&
            !(hasLasso() && nearHandle(pageX(e.x), pageY(e.y)))
        ) {
            // Açık iki sayfadan hangisine dokunulduysa onu etkin yap (kalemle önceden dokunmaya gerek kalmasın).
            if (rightPage != null) { clearLasso(); activateAt(e.x) }
        }
        val gx = pageX(e.x); val gy = pageY(e.y)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lassoDownT = e.eventTime; lassoMoved = false
                if (hasLasso() && nearHandle(gx, gy)) {
                    lassoScaling = true
                    lassoSX = 1f; lassoSY = 1f
                    lassoAX = lassoBox.left; lassoAY = lassoBox.top
                } else if (hasLasso() && lassoBox.contains(gx, gy)) {
                    lassoMoving = true; lassoLastX = gx; lassoLastY = gy; lassoDX = 0f; lassoDY = 0f
                } else {
                    clearLasso()
                    lassoDrawing = true
                    lassoXs.add(gx); lassoYs.add(gy)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (lassoScaling) {
                    val oldW = lassoBox.width(); val oldH = lassoBox.height()
                    val newW = max(40f, gx - lassoBox.left); val newH = max(40f, gy - lassoBox.top)
                    val fx = newW / oldW; val fy = newH / oldH
                    scaleSel(lassoStrokes, lassoItems, fx, fy, lassoAX, lassoAY)
                    lassoSX *= fx; lassoSY *= fy
                    lassoBox.set(lassoBox.left, lassoBox.top, lassoBox.left + newW, lassoBox.top + newH)
                } else if (lassoMoving) {
                    val dx = gx - lassoLastX; val dy = gy - lassoLastY
                    if (hypot(dx, dy) > 0.5f) lassoMoved = true
                    lassoLastX = gx; lassoLastY = gy; lassoDX += dx; lassoDY += dy
                    shift(lassoStrokes, lassoItems, dx, dy)
                    lassoBox.offset(dx, dy)
                } else if (lassoDrawing) {
                    for (h in 0 until e.historySize) { lassoXs.add(pageX(e.getHistoricalX(h))); lassoYs.add(pageY(e.getHistoricalY(h))) }
                    lassoXs.add(gx); lassoYs.add(gy)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (lassoScaling) {
                    lassoScaling = false
                    if (lassoSX != 1f || lassoSY != 1f) {
                        undoStack.add(HistoryAction.Scale(ArrayList(lassoStrokes), ArrayList(lassoItems), lassoSX, lassoSY, lassoAX, lassoAY))
                        redoStack.clear()
                    }
                } else if (lassoMoving) {
                    lassoMoving = false
                    if (lassoMoved && (lassoDX != 0f || lassoDY != 0f)) {
                        undoStack.add(HistoryAction.Move(ArrayList(lassoStrokes), ArrayList(lassoItems), lassoDX, lassoDY))
                        redoStack.clear()
                        // Seçimin merkezi öbür açık sayfanın üstüne bırakıldıysa seçim o sayfaya geçer.
                        val other = otherSpreadPage()
                        if (other != null && e.actionMasked == MotionEvent.ACTION_UP) {
                            val offO = if (other === leftPage) 0f else leftPage.width + SPREAD_GAP
                            val cxWorld = offX + lassoBox.centerX()
                            if (cxWorld >= offO && cxWorld <= offO + other.width) {
                                val had = lassoMoveTo(other, offX - offO)
                                if (had) onLassoCrossed?.invoke()
                            }
                        }
                    } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        onLassoMenu?.invoke(e.x, e.y)
                    }
                } else if (lassoDrawing) {
                    lassoDrawing = false
                    val tiny = lassoXs.size < 6
                    selectInLasso()
                    lassoXs.clear(); lassoYs.clear()
                    if (tiny && !hasLasso() && e.actionMasked == MotionEvent.ACTION_UP) onLassoMenu?.invoke(e.x, e.y)
                }
                invalidate()
            }
        }
        return true
    }

    private fun drawLasso(c: Canvas) {
        if (tool != Tool.LASSO) return
        // Sayfa dışına sürüklerken seçim görünür kalsın diye kırpmasız çizilir.
        c.save()
        c.translate(ox + offX * sc, oy)
        c.scale(sc, sc)
        lassoPaint.strokeWidth = 2f / sc
        lassoPaint.setColor(Look.accent)
        if (lassoDrawing && lassoXs.size > 1) {
            path.reset()
            path.moveTo(lassoXs[0], lassoYs[0])
            for (i in 1 until lassoXs.size) path.lineTo(lassoXs[i], lassoYs[i])
            c.drawPath(path, lassoPaint)
        }
        if (hasLasso()) {
            // Seçilen çizgiler gül tonlu bir hâleyle işaretlenir; seçilmeyenler olduğu gibi kalır.
            lassoHalo.style = Paint.Style.STROKE
            lassoHalo.strokeCap = Paint.Cap.ROUND
            lassoHalo.strokeJoin = Paint.Join.ROUND
            lassoHalo.setColor(Color.argb(110, 166, 98, 106))
            for (s in lassoStrokes) {
                if (s.xs.size < 1) continue
                lassoHalo.strokeWidth = s.width * 1.15f + 12f / sc
                path.reset()
                path.moveTo(s.xs[0], s.ys[0])
                if (s.xs.size == 1) path.lineTo(s.xs[0] + 0.1f, s.ys[0])
                for (i in 1 until s.xs.size) path.lineTo(s.xs[i], s.ys[i])
                c.drawPath(path, lassoHalo)
            }
            for (it in lassoItems) {
                c.save()
                c.translate(it.x, it.y); c.rotate(it.rot)
                lassoPaint.strokeWidth = 3f / sc
                c.drawRect(-it.w / 2f - 6f, -it.h / 2f - 6f, it.w / 2f + 6f, it.h / 2f + 6f, lassoPaint)
                c.restore()
            }
            lassoPaint.strokeWidth = 1.5f / sc
            lassoPaint.alpha = 140
            c.drawRect(lassoBox, lassoPaint)
            lassoPaint.alpha = 255
            fillPaint.setColor(Look.accent)
            c.drawCircle(lassoBox.right, lassoBox.bottom, 14f / sc * density * 0.7f + 6f, fillPaint)
        }
        c.restore()
    }

    fun lassoDelete() {
        if (!hasLasso()) return
        val rs = ArrayList<Pair<Int, Stroke>>(); val ri = ArrayList<Pair<Int, PageItem>>()
        for (s in lassoStrokes) rs.add(Pair(strokes.indexOf(s), s))
        for (it in lassoItems) ri.add(Pair(page.items.indexOf(it), it))
        rs.sortByDescending { it.first }; ri.sortByDescending { it.first }
        strokes.removeAll(lassoStrokes.toSet()); page.items.removeAll(lassoItems.toSet())
        undoStack.add(HistoryAction.Group(rs, ri, emptyList(), emptyList())); redoStack.clear()
        clearLasso(); rebuildBase(); onChanged?.invoke()
    }

    fun lassoDuplicate() {
        if (!hasLasso()) return
        val off = 40f
        val ns = ArrayList<Stroke>(); val ni = ArrayList<PageItem>()
        for (s in lassoStrokes) {
            val n = Stroke(s.color, s.width, s.highlighter, s.sens, s.kind)
            for (i in s.xs.indices) n.add(s.xs[i] + off, s.ys[i] + off, s.ps[i])
            ns.add(n)
        }
        for (it in lassoItems) { val n = it.copy(); n.x += off; n.y += off; ni.add(n) }
        strokes.addAll(ns); page.items.addAll(ni)
        undoStack.add(HistoryAction.Group(emptyList(), emptyList(), ns, ni)); redoStack.clear()
        lassoStrokes.clear(); lassoItems.clear(); lassoStrokes.addAll(ns); lassoItems.addAll(ni)
        computeLassoBox(); rebuildBase(); onChanged?.invoke()
    }

    /** Seçili çizgilerin rengini değiştirir (geri alınabilir değil; hızlı düzeltme için). */
    fun lassoRecolor(color: Int): Boolean {
        if (lassoStrokes.isEmpty()) return false
        val old = ArrayList(lassoStrokes)
        val rep = ArrayList<Stroke>()
        val rs = ArrayList<Pair<Int, Stroke>>()
        for (s in old) {
            val n = Stroke(color, s.width, s.highlighter, s.sens, s.kind)
            for (i in s.xs.indices) n.add(s.xs[i], s.ys[i], s.ps[i])
            rs.add(Pair(strokes.indexOf(s), s)); rep.add(n)
        }
        rs.sortByDescending { it.first }
        for ((i, s) in rs.withIndex()) strokes[strokes.indexOf(s.second)] = rep[old.indexOf(s.second)]
        undoStack.add(HistoryAction.Group(rs, emptyList(), rep, emptyList())); redoStack.clear()
        lassoStrokes.clear(); lassoStrokes.addAll(rep)
        rebuildBase(); onChanged?.invoke()
        return true
    }

    // ---- Cetvel: parmakla taşınır/döndürülür; yakınından başlayan kalem çizgisi cetvele oturur ----
    var rulerOn = false
    private var rx1 = 200f
    private var ry1 = 600f
    private var rx2 = 1000f
    private var ry2 = 600f
    private var rulerSnap = false
    private var rulerMode = 0
    private var rLastX = 0f
    private var rLastY = 0f
    private val rulerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun toggleRuler(): Boolean {
        rulerOn = !rulerOn
        if (rulerOn) {
            val c = viewCenterOnPage()
            val len = min(640f, page.width - 140f)
            rx1 = (c[0] - len / 2f).coerceIn(40f, max(40f, page.width - 40f - len)); rx2 = rx1 + len
            ry1 = c[1]; ry2 = c[1]
        }
        invalidate()
        return rulerOn
    }

    private fun rulerDist(x: Float, y: Float): Float {
        val dx = rx2 - rx1; val dy = ry2 - ry1
        val l2 = dx * dx + dy * dy
        val t = (((x - rx1) * dx + (y - ry1) * dy) / l2).coerceIn(0f, 1f)
        return hypot(x - (rx1 + t * dx), y - (ry1 + t * dy))
    }

    private fun snapLast(s: Stroke, from: Int) {
        val dx = rx2 - rx1; val dy = ry2 - ry1
        val l2 = dx * dx + dy * dy
        for (i in max(0, from) until s.xs.size) {
            val t = (((s.xs[i] - rx1) * dx + (s.ys[i] - ry1) * dy) / l2).coerceIn(0f, 1f)
            s.xs[i] = rx1 + t * dx; s.ys[i] = ry1 + t * dy
        }
    }

    private fun handleRulerTouch(e: MotionEvent): Boolean {
        if (e.pointerCount > 1) return false
        val gx = pageX(e.x); val gy = pageY(e.y)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val thr = 40f * density / sc
                rulerMode = when {
                    hypot(gx - rx1, gy - ry1) < thr * 1.6f -> 1
                    hypot(gx - rx2, gy - ry2) < thr * 1.6f -> 2
                    rulerDist(gx, gy) < thr -> 3
                    else -> 0
                }
                rLastX = gx; rLastY = gy
                return rulerMode != 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (rulerMode == 0) return false
                val dx = gx - rLastX; val dy = gy - rLastY
                when (rulerMode) {
                    1 -> { rx1 += dx; ry1 += dy }
                    2 -> { rx2 += dx; ry2 += dy }
                    else -> { rx1 += dx; ry1 += dy; rx2 += dx; ry2 += dy }
                }
                rLastX = gx; rLastY = gy
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val was = rulerMode != 0
                rulerMode = 0
                return was
            }
        }
        return false
    }

    private fun drawRuler(c: Canvas) {
        if (!rulerOn) return
        beginPage(c)
        val dx = rx2 - rx1; val dy = ry2 - ry1
        val len = hypot(dx, dy)
        val ang = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        c.translate(rx1, ry1)
        c.rotate(ang)
        rulerPaint.style = Paint.Style.FILL
        rulerPaint.setColor(Color.argb(120, 240, 214, 150))
        c.drawRect(0f, -34f, len, 34f, rulerPaint)
        rulerPaint.style = Paint.Style.STROKE
        rulerPaint.strokeWidth = 2f
        rulerPaint.setColor(Color.argb(200, 120, 90, 40))
        c.drawRect(0f, -34f, len, 34f, rulerPaint)
        // 1 cm = 1200/21 sayfa birimi
        val cm = 1200f / 21f
        var k = 0
        var x = 0f
        while (x <= len) {
            val h = if (k % 5 == 0) 20f else 11f
            c.drawLine(x, 34f, x, 34f - h, rulerPaint)
            if (k % 10 == 0) {
                rulerPaint.style = Paint.Style.FILL
                rulerPaint.textSize = 18f
                c.drawText((k / 10).toString(), x + 3f, 8f, rulerPaint)
                rulerPaint.style = Paint.Style.STROKE
            }
            k++
            x = k * cm / 10f
        }
        rulerPaint.style = Paint.Style.FILL
        rulerPaint.setColor(Color.argb(220, 166, 98, 106))
        c.drawCircle(0f, 0f, 14f, rulerPaint)
        c.drawCircle(len, 0f, 14f, rulerPaint)
        c.restore()
    }

    /** Word benzeri gerçek bir tablo nesnesi ekler; hücreler dokunularak düzenlenir, yazıya göre uzar. */
    fun addTable(rows: Int, cols: Int): PageItem {
        val c = viewCenterOnPage()
        val it = PageItem(PageItem.TABLE, c[0], c[1])
        it.w = min(page.width - 160f, 900f)
        it.size = 30f
        it.rowH = 70f
        it.color = Color.rgb(70, 70, 80)
        it.kind = rows or (cols shl 8) or (1 shl 16)
        it.text = "\u001f".repeat(rows * cols - 1)
        addItem(it)
        return it
    }

    var onTableTap: ((PageItem, Int, Int) -> Unit)? = null
    private var rowH0 = 64f

    companion object {
        const val LASER_MS = 900L
        /** Kalınlık kaydırıcısındaki 1 birimin sayfa birimi karşılığı. */
        const val UNIT = 2.2f
        const val SPREAD_GAP = 0f
        const val SPIRAL_HALF = 30f
        const val SPINE_HALF = 6f
        const val STICKY_KIND = 77
        const val TAPE_KIND = 78
        const val HOLD_MS = 320L
        const val TAB_OUT = 50f
        const val TAB_IN = 6f
        const val TAB_GUTTER = 68f
        const val TAB_TOP = 60f
        const val TAB_GAP = 6f
        const val TAB_H_MAX = 150f
        const val TAB_H_MIN = 26f
    }
}
