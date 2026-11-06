package com.notuygulamam.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Kullanıcının kendi çıkartma kataloğu: toplu görselden ayrılan çıkartmalar burada saklanır. */
object StickerCatalog {
    private fun dir(ctx: Context): File = File(ctx.filesDir, "ozel_cikartma").apply { mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.name.endsWith(".png") }?.sortedBy { it.name } ?: emptyList()

    fun add(ctx: Context, bmp: Bitmap, seq: Int): File {
        val s = min(1f, 512f / max(bmp.width, bmp.height))
        val b = if (s < 1f) Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * s).toInt()), max(1, (bmp.height * s).toInt()), true) else bmp
        val f = File(dir(ctx), String.format("%013d_%03d.png", System.currentTimeMillis(), seq))
        FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }
}

/** Ekranın kenarında duran çıkartma kataloğu: dokununca sayfaya eklenir, basılı tutup sayfaya sürüklenince bırakılan yere konur. */
class StickerTray(
    private val ctx: Context,
    private val host: FrameLayout,
    private val onTap: (File) -> Unit,
    private val onDrop: (File, Float, Float) -> Unit
) : LinearLayout(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val list = LinearLayout(ctx)
    private val scroll = ScrollView(ctx)
    private val ghost = ImageView(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private var deleteMode = false
    private lateinit var delBtn: TextView

    init {
        orientation = VERTICAL
        val g = GradientDrawable()
        g.setColor(Color.argb(238, 252, 250, 248))
        g.cornerRadius = 14 * d
        g.setStroke((1 * d).toInt(), Color.argb(60, 90, 60, 70))
        background = g
        elevation = 8 * d
        val head = LinearLayout(ctx)
        head.orientation = HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding((8 * d).toInt(), (6 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
        val title = TextView(ctx)
        title.text = "Çıkartmalarım"
        title.textSize = 11f
        title.setTextColor(Look.ink)
        head.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        delBtn = TextView(ctx)
        delBtn.text = "Sil"
        delBtn.textSize = 11f
        delBtn.setTextColor(Look.accent)
        delBtn.setPadding((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
        delBtn.setOnClickListener {
            deleteMode = !deleteMode
            delBtn.text = if (deleteMode) "Bitti" else "Sil"
            refresh()
        }
        head.addView(delBtn)
        val close = TextView(ctx)
        close.text = "✕"
        close.textSize = 14f
        close.setTextColor(Look.inkSoft)
        close.setPadding((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
        close.setOnClickListener { visibility = View.GONE }
        head.addView(close)
        addView(head)
        list.orientation = VERTICAL
        list.setPadding((4 * d).toInt(), 0, (4 * d).toInt(), (8 * d).toInt())
        scroll.addView(list)
        scroll.isVerticalScrollBarEnabled = false
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        ghost.visibility = View.GONE
        ghost.alpha = 0.85f
        ghost.scaleType = ImageView.ScaleType.FIT_CENTER
        host.addView(ghost, FrameLayout.LayoutParams((90 * d).toInt(), (90 * d).toInt()))
    }

    fun refresh() {
        list.removeAllViews()
        val files = StickerCatalog.list(ctx)
        if (files.isEmpty()) {
            val t = TextView(ctx)
            t.text = "Henüz çıkartma yok. Toplu çıkartma görselini seçip \"Çıkartmalara ayır\" deyin."
            t.textSize = 11f
            t.setTextColor(Look.inkSoft)
            t.setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
            list.addView(t)
            return
        }
        for (f in files) {
            val opts = BitmapFactory.Options()
            opts.inSampleSize = 4
            val bmp = BitmapFactory.decodeFile(f.absolutePath, opts) ?: continue
            val iv = ImageView(ctx)
            iv.setImageBitmap(bmp)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.setPadding((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
            if (deleteMode) {
                val g = GradientDrawable()
                g.setColor(Color.argb(40, 200, 60, 60))
                g.setStroke((1.5f * d).toInt(), Color.rgb(190, 70, 70))
                g.cornerRadius = 6 * d
                iv.background = g
            }
            iv.setOnTouchListener(thumbTouch(f, bmp))
            list.addView(iv, LayoutParams((78 * d).toInt(), (78 * d).toInt()))
        }
    }

    private fun thumbTouch(f: File, bmp: Bitmap): View.OnTouchListener {
        var startX = 0f
        var startY = 0f
        var dragging = false
        var moved = false
        var run: Runnable? = null
        return View.OnTouchListener { v, e ->
            val loc = IntArray(2)
            host.getLocationOnScreen(loc)
            val fx = e.rawX - loc[0]
            val fy = e.rawY - loc[1]
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX; startY = e.rawY; dragging = false; moved = false
                    if (!deleteMode) {
                        run = Runnable {
                            dragging = true
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            ghost.setImageBitmap(bmp)
                            ghost.visibility = View.VISIBLE
                            ghost.bringToFront()
                            ghost.x = fx - ghost.layoutParams.width / 2f
                            ghost.y = fy - ghost.layoutParams.height / 2f
                        }
                        handler.postDelayed(run!!, 220)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (hypot(e.rawX - startX, e.rawY - startY) > 12 * d) {
                        moved = true
                        if (!dragging) run?.let { handler.removeCallbacks(it) }
                    }
                    if (dragging) {
                        ghost.x = fx - ghost.layoutParams.width / 2f
                        ghost.y = fy - ghost.layoutParams.height / 2f
                    }
                }
                MotionEvent.ACTION_UP -> {
                    run?.let { handler.removeCallbacks(it) }
                    if (dragging) {
                        ghost.visibility = View.GONE
                        onDrop(f, fx, fy)
                    } else if (!moved) {
                        if (deleteMode) { f.delete(); refresh() } else onTap(f)
                    }
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    run?.let { handler.removeCallbacks(it) }
                    ghost.visibility = View.GONE
                    dragging = false
                }
            }
            true
        }
    }
}
