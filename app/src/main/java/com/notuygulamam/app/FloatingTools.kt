package com.notuygulamam.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Sayfanın üstünde duran, sürüklenebilir küçük araç penceresi (hesap makinesi, zamanlayıcı). */
open class FloatingPanel(ctx: Context, title: String, private val host: FrameLayout, w: Int) : LinearLayout(ctx) {
    protected val d = ctx.resources.displayMetrics.density
    protected val body = LinearLayout(ctx)
    private var dx = 0f
    private var dy = 0f

    init {
        orientation = VERTICAL
        val g = GradientDrawable()
        g.setColor(Color.argb(246, 252, 250, 248))
        g.cornerRadius = 16 * d
        g.setStroke((1 * d).toInt(), Color.argb(70, 90, 60, 70))
        background = g
        elevation = 10 * d
        val head = LinearLayout(ctx)
        head.orientation = HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding((12 * d).toInt(), (8 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
        val t = TextView(ctx)
        t.text = title
        t.textSize = 13f
        t.setTextColor(Look.ink)
        head.addView(t, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val x = TextView(ctx)
        x.text = "✕"
        x.textSize = 16f
        x.setTextColor(Look.inkSoft)
        x.setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt())
        x.setOnClickListener { close() }
        head.addView(x)
        head.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = this.x - e.rawX; dy = this.y - e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    this.x = (e.rawX + dx).coerceIn(0f, (host.width - width).toFloat().coerceAtLeast(0f))
                    this.y = (e.rawY + dy).coerceIn(0f, (host.height - height).toFloat().coerceAtLeast(0f))
                }
            }
            true
        }
        addView(head)
        body.orientation = VERTICAL
        body.setPadding((10 * d).toInt(), 0, (10 * d).toInt(), (10 * d).toInt())
        addView(body)
        val lp = FrameLayout.LayoutParams((w * d).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
        lp.leftMargin = (90 * d).toInt()
        lp.topMargin = (120 * d).toInt()
        host.addView(this, lp)
    }

    open fun close() {
        host.removeView(this)
    }

    protected fun btn(text: String, bg: Int, fg: Int, click: () -> Unit): TextView {
        val b = TextView(context)
        b.text = text
        b.textSize = 18f
        b.gravity = Gravity.CENTER
        b.setTextColor(fg)
        val g = GradientDrawable()
        g.cornerRadius = 12 * d
        g.setColor(bg)
        b.background = g
        b.setOnClickListener { click() }
        return b
    }
}

/** Dört işlem yapan hesap makinesi. */
class CalculatorPanel(ctx: Context, host: FrameLayout) : FloatingPanel(ctx, "Hesap makinesi", host, 260) {
    private var cur = "0"
    private var acc = 0.0
    private var op = ""
    private var fresh = true
    private val screen = TextView(ctx)

    init {
        screen.textSize = 30f
        screen.gravity = Gravity.END
        screen.setTextColor(Look.ink)
        screen.setPadding(0, (4 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
        body.addView(screen)
        val rows = listOf(
            listOf("C", "⌫", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("±", "0", ",", "=")
        )
        for (r in rows) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            for (k in r) {
                val isOp = k in listOf("÷", "×", "−", "+", "=")
                val b = btn(k, if (isOp) Look.accent else if (k in listOf("C", "⌫", "%", "±")) Look.accentSoft else Color.rgb(240, 236, 232), if (isOp) Color.WHITE else Look.ink) { press(k) }
                val lp = LinearLayout.LayoutParams(0, (46 * d).toInt(), 1f)
                lp.setMargins((3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
                row.addView(b, lp)
            }
            body.addView(row)
        }
        show()
    }

    private fun num(): Double = cur.replace(',', '.').toDoubleOrNull() ?: 0.0
    private fun fmt(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "Hata"
        val s = if (v == Math.floor(v) && Math.abs(v) < 1e12) v.toLong().toString() else String.format(Locale.US, "%.10f", v).trimEnd('0').trimEnd('.')
        return s.replace('.', ',')
    }

    private fun apply() {
        val b = num()
        acc = when (op) {
            "+" -> acc + b
            "−" -> acc - b
            "×" -> acc * b
            "÷" -> if (b == 0.0) Double.NaN else acc / b
            else -> b
        }
        cur = fmt(acc)
    }

    private fun press(k: String) {
        when (k) {
            "C" -> { cur = "0"; acc = 0.0; op = ""; fresh = true }
            "⌫" -> { cur = if (fresh || cur.length <= 1) "0" else cur.dropLast(1); if (cur == "-" ) cur = "0" }
            "±" -> if (cur != "0" && cur != "Hata") cur = if (cur.startsWith("-")) cur.drop(1) else "-$cur"
            "%" -> { cur = fmt(num() / 100.0); fresh = true }
            "+", "−", "×", "÷" -> {
                if (!fresh || op.isEmpty()) { if (op.isEmpty()) acc = num() else apply() }
                op = k; fresh = true
            }
            "=" -> { if (op.isNotEmpty()) { apply(); op = "" }; fresh = true }
            "," -> { if (fresh) { cur = "0,"; fresh = false } else if (!cur.contains(',')) cur += "," }
            else -> { if (fresh || cur == "0" || cur == "Hata") cur = k else cur += k; fresh = false }
        }
        show()
    }

    private fun show() { screen.text = cur }
}

/** Pomodoro: çalışma ve mola süreleri, başlat/duraklat/sıfırla; süre bitince ses ve titreşim. */
class PomodoroPanel(private val ctx: Context, host: FrameLayout) : FloatingPanel(ctx, "Pomodoro", host, 260) {
    private val handler = Handler(Looper.getMainLooper())
    private var work = true
    private var workMin = 25
    private var breakMin = 5
    private var left = workMin * 60
    private var running = false
    private var rounds = 0
    private val timeView = TextView(ctx)
    private val state = TextView(ctx)
    private lateinit var startBtn: TextView
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            left--
            if (left <= 0) finishPhase() else { show(); handler.postDelayed(this, 1000) }
        }
    }

    init {
        state.textSize = 13f
        state.gravity = Gravity.CENTER
        state.setTextColor(Look.accent)
        body.addView(state)
        timeView.textSize = 48f
        timeView.gravity = Gravity.CENTER
        timeView.setTextColor(Look.ink)
        body.addView(timeView)
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        startBtn = btn("Başlat", Look.accent, Color.WHITE) { toggle() }
        val reset = btn("Sıfırla", Look.accentSoft, Look.ink) { reset() }
        val lp1 = LinearLayout.LayoutParams(0, (46 * d).toInt(), 1f)
        lp1.setMargins((3 * d).toInt(), (6 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
        row.addView(startBtn, lp1)
        row.addView(reset, LinearLayout.LayoutParams(lp1))
        body.addView(row)
        val set = LinearLayout(ctx)
        set.orientation = LinearLayout.HORIZONTAL
        set.gravity = Gravity.CENTER
        fun stepper(label: String, get: () -> Int, change: (Int) -> Unit): LinearLayout {
            val l = LinearLayout(ctx)
            l.orientation = LinearLayout.HORIZONTAL
            l.gravity = Gravity.CENTER_VERTICAL
            val tv = TextView(ctx)
            tv.textSize = 12f
            tv.setTextColor(Look.inkSoft)
            fun upd() { tv.text = label + " " + get() + " dk" }
            upd()
            val m = btn("−", Color.rgb(240, 236, 232), Look.ink) { change(-1); upd() }
            val p = btn("+", Color.rgb(240, 236, 232), Look.ink) { change(1); upd() }
            val sz = (32 * d).toInt()
            l.addView(m, LinearLayout.LayoutParams(sz, sz))
            l.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins((6 * d).toInt(), 0, (6 * d).toInt(), 0) })
            l.addView(p, LinearLayout.LayoutParams(sz, sz))
            return l
        }
        body.addView(stepper("Çalışma", { workMin }) { dlt -> workMin = (workMin + dlt).coerceIn(1, 90); if (!running && work) { left = workMin * 60; show() } })
        body.addView(stepper("Mola", { breakMin }) { dlt -> breakMin = (breakMin + dlt).coerceIn(1, 30); if (!running && !work) { left = breakMin * 60; show() } })
        show()
    }

    private fun show() {
        timeView.text = String.format(Locale.US, "%02d:%02d", left / 60, left % 60)
        state.text = (if (work) "Çalışma" else "Mola") + (if (rounds > 0) " · $rounds tur tamam" else "")
        startBtn.text = if (running) "Duraklat" else "Başlat"
    }

    private fun toggle() {
        running = !running
        if (running) handler.postDelayed(tick, 1000) else handler.removeCallbacks(tick)
        show()
    }

    private fun reset() {
        running = false
        handler.removeCallbacks(tick)
        work = true
        left = workMin * 60
        show()
    }

    private fun finishPhase() {
        running = false
        handler.removeCallbacks(tick)
        try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).startTone(ToneGenerator.TONE_PROP_BEEP2, 600) } catch (_: Throwable) {}
        try {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (android.os.Build.VERSION.SDK_INT >= 26) v?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v?.vibrate(500)
        } catch (_: Throwable) {}
        if (work) rounds++
        work = !work
        left = (if (work) workMin else breakMin) * 60
        show()
    }

    override fun close() {
        running = false
        handler.removeCallbacks(tick)
        super.close()
    }
}

/** Durum satırına sığan yalın Pomodoro: Çalışma − 25 +  Mola − 5 +  süre  ▶  ↺. Her şey doğrudan satırda, açılır pencere yok. */
class PomodoroStrip(private val ctx: Context) : LinearLayout(ctx) {
    private val handler = Handler(Looper.getMainLooper())
    private val d = ctx.resources.displayMetrics.density
    private var work = true
    private var workMin = 25
    private var breakMin = 5
    private var left = workMin * 60
    private var running = false
    private var rounds = 0
    private val timeView = TextView(ctx)
    private val playBtn = TextView(ctx)
    private val workView = TextView(ctx)
    private val breakView = TextView(ctx)
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            left--
            if (left <= 0) finishPhase() else { show(); handler.postDelayed(this, 1000) }
        }
    }

    private fun tv(t: String, size: Float, color: Int): TextView {
        val v = TextView(ctx)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        v.gravity = Gravity.CENTER
        return v
    }

    private fun stepper(label: String, value: TextView, change: (Int) -> Unit) {
        val l = tv(label, 12f, Look.inkSoft)
        l.setPadding((4 * d).toInt(), 0, (2 * d).toInt(), 0)
        val minus = tv("−", 18f, Look.accent)
        minus.setPadding((9 * d).toInt(), 0, (5 * d).toInt(), 0)
        minus.setOnClickListener { change(-1) }
        value.textSize = 14f
        value.setTextColor(Look.ink)
        value.setTypeface(null, android.graphics.Typeface.BOLD)
        value.gravity = Gravity.CENTER
        val plus = tv("+", 18f, Look.accent)
        plus.setPadding((5 * d).toInt(), 0, (9 * d).toInt(), 0)
        plus.setOnClickListener { change(1) }
        addView(l); addView(minus); addView(value); addView(plus)
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val g = GradientDrawable()
        g.cornerRadius = 10 * d
        g.setColor(Look.accentSoft)
        background = g
        setPadding((8 * d).toInt(), (2 * d).toInt(), (6 * d).toInt(), (2 * d).toInt())
        stepper("Çalışma", workView) { dlt ->
            workMin = (workMin + dlt).coerceIn(1, 120)
            if (!running && work) left = workMin * 60
            show()
        }
        stepper("Mola", breakView) { dlt ->
            breakMin = (breakMin + dlt).coerceIn(1, 60)
            if (!running && !work) left = breakMin * 60
            show()
        }
        timeView.textSize = 16f
        timeView.setTypeface(null, android.graphics.Typeface.BOLD)
        timeView.setTextColor(Look.accent)
        timeView.setPadding((12 * d).toInt(), 0, (8 * d).toInt(), 0)
        addView(timeView)
        playBtn.textSize = 17f
        playBtn.setTextColor(Look.accent)
        playBtn.setPadding((8 * d).toInt(), 0, (8 * d).toInt(), 0)
        playBtn.setOnClickListener { toggle() }
        addView(playBtn)
        val reset = tv("↺", 17f, Look.accent)
        reset.setPadding((8 * d).toInt(), 0, (6 * d).toInt(), 0)
        reset.setOnClickListener { reset() }
        addView(reset)
        show()
    }

    private fun show() {
        workView.text = workMin.toString()
        breakView.text = breakMin.toString()
        timeView.text = (if (work) "" else "Mola ") + String.format(Locale.US, "%02d:%02d", left / 60, left % 60) +
            (if (rounds > 0) "  ($rounds)" else "")
        playBtn.text = if (running) "❚❚" else "▶"
    }

    private fun toggle() {
        running = !running
        if (running) handler.postDelayed(tick, 1000) else handler.removeCallbacks(tick)
        show()
    }

    private fun reset() {
        running = false
        handler.removeCallbacks(tick)
        work = true
        left = workMin * 60
        show()
    }

    private fun finishPhase() {
        running = false
        handler.removeCallbacks(tick)
        try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).startTone(ToneGenerator.TONE_PROP_BEEP2, 600) } catch (_: Throwable) {}
        try {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (android.os.Build.VERSION.SDK_INT >= 26) v?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v?.vibrate(500)
        } catch (_: Throwable) {}
        if (work) rounds++
        work = !work
        left = (if (work) workMin else breakMin) * 60
        show()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
}
