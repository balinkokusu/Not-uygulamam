package com.notuygulamam.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Defterin kendi kenar notları: sayfaların solundaki ve sağındaki boşlukta duran küçük hatırlatma kartları. */
object MarginNotes {
    class Ink(val color: Int, val pts: ArrayList<Float> = ArrayList())

    class Note(val id: Long, var text: String, var color: Int, var side: Int, var ink: ArrayList<Ink> = ArrayList(), var fx: Float = -1f, var fy: Float = -1f)

    private fun key(nb: String) = "n_$nb"

    fun load(ctx: Context, nb: String): MutableList<Note> {
        val out = ArrayList<Note>()
        try {
            val s = ctx.getSharedPreferences("kenar", Context.MODE_PRIVATE).getString(key(nb), "[]") ?: "[]"
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val n = Note(o.getLong("id"), o.getString("text"), o.getInt("color"), o.optInt("side", 0))
                n.fx = o.optDouble("fx", -1.0).toFloat()
                n.fy = o.optDouble("fy", -1.0).toFloat()
                val ia = o.optJSONArray("ink")
                if (ia != null) for (k in 0 until ia.length()) {
                    val io = ia.getJSONObject(k)
                    val st = Ink(io.getInt("c"))
                    val pa = io.getJSONArray("p")
                    for (q in 0 until pa.length()) st.pts.add(pa.getDouble(q).toFloat())
                    n.ink.add(st)
                }
                out.add(n)
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun save(ctx: Context, nb: String, list: List<Note>) {
        val a = JSONArray()
        for (n in list) {
            val ia = JSONArray()
            for (st in n.ink) {
                val pa = JSONArray()
                for (v in st.pts) pa.put(Math.round(v * 1000f) / 1000.0)
                ia.put(JSONObject().put("c", st.color).put("p", pa))
            }
            a.put(JSONObject().put("id", n.id).put("text", n.text).put("color", n.color).put("side", n.side).put("fx", n.fx.toDouble()).put("fy", n.fy.toDouble()).put("ink", ia))
        }
        ctx.getSharedPreferences("kenar", Context.MODE_PRIVATE).edit().putString(key(nb), a.toString()).apply()
    }

    /** Not ekler ya da düzenler; metni boş bırakıp kaydetmek notu siler. */
    fun edit(ctx: Context, nb: String, note: Note?, defaultSide: Int, onDone: () -> Unit) {
        val d = ctx.resources.displayMetrics.density
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0)
        val et = EditText(ctx)
        et.hint = "Yazı (isteğe bağlı; boş bırakıp kalemle üstüne yazabilirsiniz)"
        et.minLines = 3
        et.setText(note?.text ?: "")
        box.addView(et)
        val colors = Reminders.COLORS
        val names = arrayOf("Sarı", "Pembe", "Yeşil", "Mavi")
        val grp = RadioGroup(ctx)
        grp.orientation = LinearLayout.HORIZONTAL
        for (i in names.indices) {
            val rb = RadioButton(ctx)
            rb.id = 700 + i
            rb.text = names[i]
            grp.addView(rb)
        }
        grp.check(700 + (if (note == null) 0 else colors.indexOf(note.color).coerceAtLeast(0)))
        box.addView(grp)
        val sg = RadioGroup(ctx)
        sg.orientation = LinearLayout.HORIZONTAL
        val sl = RadioButton(ctx); sl.id = 800; sl.text = "Sayfaların solunda"
        val sr = RadioButton(ctx); sr.id = 801; sr.text = "Sayfaların sağında"
        sg.addView(sl); sg.addView(sr)
        sg.check(800 + (note?.side ?: defaultSide).coerceIn(0, 1))
        box.addView(sg)
        val clearInk = android.widget.CheckBox(ctx)
        clearInk.text = "Üzerindeki kalem yazısını temizle"
        if (note != null && note.ink.isNotEmpty()) box.addView(clearInk)
        val b = AlertDialog.Builder(ctx).setTitle(if (note == null) "Yeni kenar notu" else "Kenar notunu düzenle").setView(box)
            .setPositiveButton("Kaydet") { _, _ ->
                val col = colors[(grp.checkedRadioButtonId - 700).coerceIn(0, 3)]
                val side = if (sg.checkedRadioButtonId == 801) 1 else 0
                val txt = et.text.toString().trim()
                val l = load(ctx, nb)
                if (note == null) {
                    l.add(Note(System.currentTimeMillis(), txt, col, side))
                } else {
                    val n = l.firstOrNull { it.id == note.id }
                    if (n != null) {
                        n.text = txt; n.color = col; n.side = side
                        if (clearInk.isChecked) n.ink.clear()
                    }
                }
                save(ctx, nb, l)
                onDone()
            }
            .setNegativeButton("Vazgeç", null)
        if (note != null) {
            b.setNeutralButton("Sil") { _, _ ->
                val l = load(ctx, nb)
                l.removeAll { it.id == note.id }
                save(ctx, nb, l)
                onDone()
            }
        }
        b.show()
    }
}

/** Yapışkan not görünümlü, üzerine kalemle yazılabilen kenar notu. */
class NoteCard(
    ctx: Context,
    private val nb: String,
    private val note: MarginNotes.Note,
    private val penColor: () -> Int,
    private val onEdit: () -> Unit,
    private val onDropped: (MarginNotes.Note, Float, Float) -> Unit,
    private val erasing: () -> Boolean,
    private val eraserDp: () -> Float,
    private val onDelete: (MarginNotes.Note) -> Unit
) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.2f * d
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 50, 40)
        textSize = 11f * d
        typeface = Typeface.SERIF
    }
    private val path = Path()
    private var cur: MarginNotes.Ink? = null
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var downTime = 0L
    private var startX = 0f
    private var startY = 0f
    private var deletePending = false
    private var erasingNow = false
    private var rawDX = 0f
    private var rawDY = 0f

    init {
        val g = GradientDrawable()
        g.setColor(note.color)
        g.cornerRadius = 3 * d
        background = g
        elevation = 5 * d
        rotation = 0f
        isClickable = true
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        setMeasuredDimension(w, w)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // kıvrık köşe
        val f = w * 0.22f
        fill.style = Paint.Style.FILL
        fill.color = Color.argb(60, 0, 0, 0)
        path.reset()
        path.moveTo(w, h - f); path.lineTo(w - f, h); path.lineTo(w, h); path.close()
        c.drawPath(path, fill)
        if (note.text.isNotEmpty()) {
            val sl = StaticLayout(note.text, textPaint, (w - 12 * d).toInt(), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
            c.save()
            c.translate(6 * d, 6 * d)
            c.clipRect(0f, 0f, w - 12 * d, h - 12 * d)
            sl.draw(c)
            c.restore()
        }
        for (st in note.ink) drawInk(c, st, w, h)
        cur?.let { drawInk(c, it, w, h) }
        // silme düğmesi
        fill.style = Paint.Style.FILL
        fill.color = Color.argb(150, 255, 255, 255)
        c.drawCircle(w - 13 * d, 13 * d, 10 * d, fill)
        inkPaint.color = Color.argb(200, 120, 60, 60)
        val sw = inkPaint.strokeWidth
        inkPaint.strokeWidth = 1.6f * d
        c.drawLine(w - 17 * d, 9 * d, w - 9 * d, 17 * d, inkPaint)
        c.drawLine(w - 17 * d, 17 * d, w - 9 * d, 9 * d, inkPaint)
        inkPaint.strokeWidth = sw
    }

    /** Silgi: noktanın yakınındaki kalem izlerinden yalnız değen kısmı keser. */
    private fun eraseInkAt(px: Float, py: Float): Boolean {
        val rx = eraserDp() * d / width
        val ry = eraserDp() * d / height
        var any = false
        val out = ArrayList<MarginNotes.Ink>()
        for (st in note.ink) {
            var touched = false
            val pts = st.pts
            var k = 0
            while (k + 1 < pts.size) {
                val dx = (pts[k] - px) / rx
                val dy = (pts[k + 1] - py) / ry
                if (dx * dx + dy * dy <= 1f) { touched = true; break }
                k += 2
            }
            if (!touched) { out.add(st); continue }
            any = true
            var curSt: MarginNotes.Ink? = null
            k = 0
            while (k + 1 < pts.size) {
                val dx = (pts[k] - px) / rx
                val dy = (pts[k + 1] - py) / ry
                if (dx * dx + dy * dy > 1f) {
                    if (curSt == null) curSt = MarginNotes.Ink(st.color)
                    curSt.pts.add(pts[k]); curSt.pts.add(pts[k + 1])
                } else {
                    if (curSt != null && curSt.pts.size >= 4) out.add(curSt)
                    curSt = null
                }
                k += 2
            }
            if (curSt != null && curSt.pts.size >= 4) out.add(curSt)
        }
        if (any) { note.ink.clear(); note.ink.addAll(out) }
        return any
    }

    private fun drawInk(c: Canvas, st: MarginNotes.Ink, w: Float, h: Float) {
        if (st.pts.size < 2) return
        inkPaint.color = st.color
        path.reset()
        path.moveTo(st.pts[0] * w, st.pts[1] * h)
        if (st.pts.size == 2) path.lineTo(st.pts[0] * w + 0.1f, st.pts[1] * h)
        var i = 2
        while (i + 1 < st.pts.size) { path.lineTo(st.pts[i] * w, st.pts[i + 1] * h); i += 2 }
        c.drawPath(path, inkPaint)
    }

    private fun persist() {
        val l = MarginNotes.load(context, nb)
        val n = l.firstOrNull { it.id == note.id } ?: return
        n.ink = ArrayList(note.ink)
        MarginNotes.save(context, nb, l)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val pen = e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS || e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = e.x; downY = e.y; moved = false; downTime = e.eventTime
                startX = x; startY = y; rawDX = e.rawX; rawDY = e.rawY
                if (!pen) bringToFront()
                val nearDel = e.x > width - 26 * d && e.y < 26 * d
                deletePending = nearDel && !pen
                val eraser = pen && (erasing() || e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER || (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0)
                erasingNow = eraser
                if (eraser) {
                    if (eraseInkAt(e.x / width, e.y / height)) invalidate()
                } else if (pen) {
                    val st = MarginNotes.Ink(penColor())
                    st.pts.add((e.x / width).coerceIn(0f, 1f)); st.pts.add((e.y / height).coerceIn(0f, 1f))
                    cur = st
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pen && (moved || hypot(e.rawX - rawDX, e.rawY - rawDY) > 10 * d)) {
                    moved = true
                    x = startX + (e.rawX - rawDX)
                    y = startY + (e.rawY - rawDY)
                }
                val st = cur
                if (erasingNow) {
                    var ch = false
                    for (i in 0 until e.historySize) if (eraseInkAt(e.getHistoricalX(i) / width, e.getHistoricalY(i) / height)) ch = true
                    if (eraseInkAt(e.x / width, e.y / height)) ch = true
                    if (ch) invalidate()
                } else if (pen && st != null) {
                    for (i in 0 until e.historySize) {
                        st.pts.add((e.getHistoricalX(i) / width).coerceIn(0f, 1f)); st.pts.add((e.getHistoricalY(i) / height).coerceIn(0f, 1f))
                    }
                    st.pts.add((e.x / width).coerceIn(0f, 1f)); st.pts.add((e.y / height).coerceIn(0f, 1f))
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val st = cur
                if (erasingNow) {
                    erasingNow = false
                    persist()
                    invalidate()
                } else if (deletePending && e.actionMasked == MotionEvent.ACTION_UP && !moved && e.x > width - 26 * d && e.y < 26 * d) {
                    deletePending = false
                    onDelete(note)
                } else if (st != null) {
                    note.ink.add(st)
                    cur = null
                    persist()
                    invalidate()
                } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                    if (!moved) onEdit() else onDropped(note, x + width / 2f, y + height / 2f)
                }
            }
        }
        return true
    }
}

/** Ekranda serbestçe duran, tüm sayfalarda sabit kalan kenar notları (masa). */
class DeskLayer(
    private val ctx: Context,
    private val nb: String,
    private val penColor: () -> Int,
    private val onChange: () -> Unit,
    private val erasing: () -> Boolean,
    private val eraserDp: () -> Float,
    private val dropped: (MarginNotes.Note, Float, Float) -> Boolean
) : android.widget.FrameLayout(ctx) {
    private val d = ctx.resources.displayMetrics.density
    val cardPx = (96 * d).toInt()
    private val cards = ArrayList<Pair<NoteCard, MarginNotes.Note>>()

    fun refresh() {
        removeAllViews()
        cards.clear()
        val all = MarginNotes.load(ctx, nb)
        var changed = false
        var li = 0
        var ri = 0
        for (n in all) {
            if (n.fx < 0f || n.fy < 0f) {
                // ilk yerleşim: seçilen yanın kenarı
                if (n.side == 1) { n.fx = 0.9f; n.fy = 0.05f + 0.22f * ri; ri++ } else { n.fx = 0.005f; n.fy = 0.05f + 0.22f * li; li++ }
                changed = true
            }
        }
        if (changed) MarginNotes.save(ctx, nb, all)
        for (n in all) {
            val card = NoteCard(ctx, nb, n, penColor, { MarginNotes.edit(ctx, nb, n, n.side) { onChange() } }, { note, cx, cy ->
                if (!dropped(note, cx, cy)) {
                    val l = MarginNotes.load(ctx, nb)
                    val m = l.firstOrNull { it.id == note.id }
                    if (m != null && width > 0 && height > 0) {
                        m.fx = ((cx - cardPx / 2f) / width).coerceIn(0f, 1f - cardPx.toFloat() / width)
                        m.fy = ((cy - cardPx / 2f) / height).coerceIn(0f, 1f - cardPx.toFloat() / height)
                        MarginNotes.save(ctx, nb, l)
                    }
                    requestLayout()
                }
            }, erasing, eraserDp) { note ->
                android.app.AlertDialog.Builder(ctx).setTitle("Bu not silinsin mi?")
                    .setPositiveButton("Sil") { _, _ ->
                        val l = MarginNotes.load(ctx, nb)
                        l.removeAll { it.id == note.id }
                        MarginNotes.save(ctx, nb, l)
                        onChange()
                    }
                    .setNegativeButton("Vazgeç", null).show()
            }
            addView(card, LayoutParams(cardPx, cardPx))
            cards.add(Pair(card, n))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val w = r - l
        val h = b - t
        val stored = MarginNotes.load(ctx, nb)
        for ((card, n) in cards) {
            val m = stored.firstOrNull { it.id == n.id } ?: n
            card.x = (m.fx * w).coerceIn(0f, (w - cardPx).toFloat().coerceAtLeast(0f))
            card.y = (m.fy * h).coerceIn(0f, (h - cardPx).toFloat().coerceAtLeast(0f))
        }
    }
}
