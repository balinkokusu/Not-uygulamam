package com.notuygulamam.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Kütüphanedeki bir defter, klasör ya da "Yeni" kutusunu çizer. */
class TileView(
    context: Context,
    private val item: LibraryItem?,
    private val coverDir: File,
    private val subtitle: String
) : View(context) {
    private val d = resources.displayMetrics.density
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val shadow = RectF()
    private val path = Path()
    private val src = Rect()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 1.5f).toInt())
    }

    private fun drawCropped(c: Canvas, bmp: Bitmap, dst: RectF) {
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val scale = max(dst.width() / bw, dst.height() / bh)
        val sw = dst.width() / scale
        val sh = dst.height() / scale
        src.set(((bw - sw) / 2f).toInt(), ((bh - sh) / 2f).toInt(), ((bw + sw) / 2f).toInt(), ((bh + sh) / 2f).toInt())
        val radius = dst.width() * 0.05f
        c.save()
        path.reset()
        path.addRoundRect(dst, radius, radius, Path.Direction.CW)
        c.clipPath(path)
        c.drawBitmap(bmp, src, dst, bitmapPaint)
        p.style = Paint.Style.FILL
        p.color = Color.argb(60, 0, 0, 0)
        c.drawRect(dst.left, dst.top, dst.left + dst.width() * 0.07f, dst.bottom, p)
        p.color = Color.argb(70, 255, 255, 255)
        c.drawRect(dst.left + dst.width() * 0.07f, dst.top, dst.left + dst.width() * 0.078f, dst.bottom, p)
        c.restore()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val artW = w * 0.82f
        val left = (w - artW) / 2f
        val coverH = artW * 1.3f
        val top = w * 0.04f
        tp.textAlign = Paint.Align.CENTER

        if (item == null) {
            rect.set(left, top, left + artW, top + coverH)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.5f * d
            p.color = Look.gold
            p.pathEffect = DashPathEffect(floatArrayOf(10f * d, 8f * d), 0f)
            c.drawRoundRect(rect, 12f * d, 12f * d, p)
            p.pathEffect = null
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 4f * d
            c.drawLine(rect.centerX() - 18f * d, rect.centerY(), rect.centerX() + 18f * d, rect.centerY(), p)
            c.drawLine(rect.centerX(), rect.centerY() - 18f * d, rect.centerX(), rect.centerY() + 18f * d, p)
            tp.color = Look.accent
            tp.typeface = Typeface.SERIF
            tp.textSize = 16f * d
            tp.isFakeBoldText = false
            c.drawText("Yeni", w / 2f, top + coverH + 22f * d, tp)
            return
        }

        if (item.isFolder) {
            val fh = artW * 0.85f
            rect.set(left, top + coverH - fh, left + artW, top + coverH)
            FolderArt.draw(c, rect, item.color, item.icon)
        } else {
            rect.set(left, top, left + artW, top + coverH)
            p.style = Paint.Style.FILL
            for (g in 1..6) {
                val e = g * 1.8f * d
                shadow.set(rect.left - e * 0.4f, rect.top + 3f * d - e * 0.3f, rect.right + e * 0.4f, rect.bottom + 5f * d + e * 0.6f)
                p.color = Color.argb(14, 70, 40, 45)
                c.drawRoundRect(shadow, artW * 0.05f + e * 0.5f, artW * 0.05f + e * 0.5f, p)
            }
            val img = item.coverImage
            val bmp = if (img != null) CoverCache.thumb(coverDir, img) else null
            if (bmp != null) drawCropped(c, bmp, rect) else CoverArt.draw(c, rect, item.coverStyle, item.color)
        }

        tp.color = Look.ink
        tp.typeface = Typeface.SERIF
        tp.textSize = 16f * d
        tp.isFakeBoldText = false
        val title = TextUtils.ellipsize(item.title, tp, w * 0.92f, TextUtils.TruncateAt.END).toString()
        c.drawText(title, w / 2f, top + coverH + 22f * d, tp)
        if (subtitle.isNotEmpty()) {
            tp.color = Look.inkSoft
            tp.typeface = Typeface.DEFAULT
            tp.textSize = 11.5f * d
            tp.isFakeBoldText = false
            c.drawText(subtitle, w / 2f, top + coverH + 40f * d, tp)
        }
    }
}

/** Seçenek listelerinde kullanılan küçük, seçilebilir önizleme. */
class ArtThumb(
    context: Context,
    private val wDp: Int,
    private val hDp: Int,
    private val selected: () -> Boolean,
    private val painter: (Canvas, RectF) -> Unit
) : View(context) {
    private val d = resources.displayMetrics.density
    private val rect = RectF()
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((wDp * d).toInt(), (hDp * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        rect.set(5f * d, 5f * d, width - 5f * d, height - 5f * d)
        painter(c, rect)
        if (selected()) {
            ring.strokeWidth = 3f * d
            ring.color = Look.accent
            rect.inset(-3f * d, -3f * d)
            c.drawRoundRect(rect, 8f * d, 8f * d, ring)
        }
    }
}

class LibraryActivity : Activity() {
    private lateinit var store: LibraryStore
    private lateinit var coverDir: File
    private lateinit var grid: GridView
    private lateinit var titleView: TextView
    private lateinit var backBtn: TextView
    private var folderId = ""
    private var d = 1f
    private var shown: List<LibraryItem?> = emptyList()
    private var draftForPhoto: LibraryItem? = null
    private var onPhotoReady: (() -> Unit)? = null
    private val pendingImages = ArrayList<String>()
    private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale("tr"))

    private val palette = intArrayOf(
        Color.rgb(70, 100, 180), Color.rgb(40, 150, 150), Color.rgb(70, 150, 90),
        Color.rgb(150, 160, 70), Color.rgb(235, 190, 60), Color.rgb(235, 140, 60),
        Color.rgb(205, 70, 70), Color.rgb(230, 120, 160), Color.rgb(140, 90, 180),
        Color.rgb(130, 90, 70), Color.rgb(120, 125, 135), Color.rgb(35, 50, 100)
    )

    private fun dp(v: Float): Int = (v * d).toInt()

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any? = shown[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val it = shown[position]
            val sub = if (it == null) "" else if (it.isFolder) {
                val n = store.items.count { c -> c.parentId == it.id }
                "$n öğe"
            } else dateFmt.format(Date(it.modified))
            return TileView(this@LibraryActivity, it, coverDir, sub)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        d = resources.displayMetrics.density
        store = LibraryStore(File(filesDir, "library"))
        coverDir = File(filesDir, "covers")
        store.load()
        migrate()
        try {
            val flag = File(filesDir, "kurtarma_v094")
            if (!flag.exists()) {
                val k = Storage.recover(File(filesDir, "library"))
                flag.createNewFile()
                if (k > 0) Toast.makeText(this, k.toString() + " defterin içeriği yedekten geri getirildi", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Look.libraryBg)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(20f), dp(18f), dp(20f), dp(6f))
        backBtn = TextView(this)
        backBtn.text = "‹ Geri"
        backBtn.textSize = 18f
        backBtn.setTextColor(Look.accent)
        backBtn.setPadding(0, dp(6f), dp(16f), dp(6f))
        backBtn.visibility = View.GONE
        backBtn.setOnClickListener { goUp() }
        titleView = TextView(this)
        titleView.textSize = 34f
        titleView.setTypeface(Typeface.SERIF, Typeface.NORMAL)
        titleView.setTextColor(Look.ink)
        header.addView(backBtn)
        header.addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val backupBtn = TextView(this)
        backupBtn.text = "Yedek"
        backupBtn.textSize = 16f
        backupBtn.setTextColor(Look.accent)
        backupBtn.setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        backupBtn.setOnClickListener { showLibraryBackup() }
        header.addView(backupBtn)

        grid = GridView(this)
        grid.numColumns = GridView.AUTO_FIT
        grid.columnWidth = dp(170f)
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.verticalSpacing = dp(10f)
        grid.horizontalSpacing = dp(10f)
        grid.setPadding(dp(20f), dp(12f), dp(20f), dp(20f))
        grid.clipToPadding = false
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> onTileClick(shown[pos]) }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            val it = shown[pos]
            if (it != null) showItemMenu(it)
            true
        }

        root.addView(header)
        // sekme çubuğu: tek yuvarlak çerçevede dört sekme
        val tabBar = LinearLayout(this)
        tabBar.orientation = LinearLayout.HORIZONTAL
        tabBar.gravity = Gravity.CENTER_VERTICAL
        val tbg = GradientDrawable()
        tbg.cornerRadius = dp(30f).toFloat()
        tbg.setColor(Color.argb(160, 255, 252, 248))
        tbg.setStroke(dp(1f), Look.hairline)
        tabBar.background = tbg
        tabBar.setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
        tabViews.clear()
        val names = arrayOf("Ana sayfa", "Defterler", "Flaş kartlar", "Klasörler")
        for (i in names.indices) {
            val v = TextView(this)
            v.text = names[i]
            v.textSize = 15f
            v.gravity = Gravity.CENTER
            v.setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
            v.setTypeface(Typeface.SERIF, Typeface.NORMAL)
            v.setOnClickListener {
                if (i == 2) startActivity(Intent(this, FlashcardsActivity::class.java))
                else { mode = if (i == 3) 2 else i; folderId = ""; refresh() }
            }
            tabViews.add(v)
            tabBar.addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val tbLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        tbLp.setMargins(dp(20f), dp(4f), dp(20f), dp(8f))
        root.addView(tabBar, tbLp)

        board = ReminderBoard(this)
        homeBox = LinearLayout(this)
        homeBox.orientation = LinearLayout.VERTICAL
        homeBox.setPadding(dp(20f), dp(4f), dp(20f), dp(24f))
        homeScroll = ScrollView(this)
        homeScroll.addView(homeBox)
        val gridBox = LinearLayout(this)
        gridBox.orientation = LinearLayout.VERTICAL
        gridBox.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        gridHost = gridBox
        val content = android.widget.FrameLayout(this)
        content.addView(homeScroll, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        content.addView(gridBox, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refresh()
    }

    private lateinit var board: ReminderBoard
    private lateinit var homeBox: LinearLayout
    private lateinit var homeScroll: ScrollView
    private lateinit var gridHost: View
    private val tabViews = ArrayList<TextView>()

    /** 0 ana sayfa, 1 defterler, 2 klasörler. */
    private var mode = 0

    private fun sectionTitle(txt: String, glyph: String, onAll: (() -> Unit)?) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val t = TextView(this)
        t.text = glyph + "  " + txt
        t.textSize = 22f
        t.setTypeface(Typeface.SERIF, Typeface.BOLD)
        t.setTextColor(Look.ink)
        row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (onAll != null) {
            val all = TextView(this)
            all.text = "Tümünü gör  ›"
            all.textSize = 14f
            all.setTextColor(Look.inkSoft)
            all.setPadding(dp(10f), dp(8f), dp(4f), dp(8f))
            all.setOnClickListener { onAll() }
            row.addView(all)
        }
        row.setPadding(0, dp(18f), 0, dp(8f))
        homeBox.addView(row)
    }

    private fun hRow(): LinearLayout {
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        hs.addView(row)
        homeBox.addView(hs)
        return row
    }

    private fun addTile(row: LinearLayout, item: LibraryItem?, newAction: (() -> Unit)? = null) {
        val sub = if (item == null) "" else if (item.isFolder) "${store.items.count { c -> c.parentId == item.id }} öğe" else dateFmt.format(Date(item.modified))
        val v = TileView(this, item, coverDir, sub)
        v.setOnClickListener { if (item == null) newAction?.invoke() else onTileClick(item) }
        v.setOnLongClickListener { if (item != null) showItemMenu(item); item != null }
        row.addView(v, LinearLayout.LayoutParams(dp(130f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(10f) })
    }

    /** Ana sayfa: son defterler, flaş kart setleri ve klasörler yatay şeritlerde. */
    private fun buildHome() {
        homeBox.removeAllViews()
        homeBox.addView(board, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val books = store.items.filter { !it.isFolder }.sortedByDescending { it.modified }
        sectionTitle("Son defterler", "▤") { mode = 1; refresh() }
        val r1 = hRow()
        for (b in books.take(6)) addTile(r1, b)
        addTile(r1, null) { showEditor(null, false) }

        sectionTitle("Flaş kartlar", "❏") { startActivity(Intent(this, FlashcardsActivity::class.java)) }
        val fr = hRow()
        val decks = FlashStore.load(this)
        val tints = intArrayOf(Color.rgb(232, 224, 242), Color.rgb(236, 242, 222), Color.rgb(252, 240, 214), Color.rgb(250, 226, 226))
        for ((i, dk) in decks.take(8).withIndex()) {
            val c = TextView(this)
            val due = FlashStore.dueCount(dk)
            c.text = dk.name + "\n" + dk.cards.size + " kart" + if (due > 0) " · $due bugün" else ""
            c.textSize = 15f
            c.setTypeface(Typeface.SERIF, Typeface.NORMAL)
            c.setTextColor(Look.ink)
            c.gravity = Gravity.CENTER
            c.setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
            val g = GradientDrawable()
            g.cornerRadius = dp(14f).toFloat()
            g.setColor(tints[i % tints.size])
            c.background = g
            c.setOnClickListener { startActivity(Intent(this, FlashcardsActivity::class.java)) }
            fr.addView(c, LinearLayout.LayoutParams(dp(130f), dp(110f)).apply { rightMargin = dp(10f) })
        }
        val nw = TextView(this)
        nw.text = "+\nYeni kart seti"
        nw.textSize = 14f
        nw.gravity = Gravity.CENTER
        nw.setTextColor(Look.accent)
        val ng = GradientDrawable()
        ng.cornerRadius = dp(14f).toFloat()
        ng.setStroke(dp(2f), Look.gold, dp(8f).toFloat(), dp(6f).toFloat())
        nw.background = ng
        nw.setOnClickListener { startActivity(Intent(this, FlashcardsActivity::class.java)) }
        fr.addView(nw, LinearLayout.LayoutParams(dp(130f), dp(110f)))

        sectionTitle("Klasörlerim", "▭") { mode = 2; refresh() }
        val kr = hRow()
        for (f in store.items.filter { it.isFolder }.take(8)) addTile(kr, f)
        addTile(kr, null) { showEditor(null, true) }
    }

    override fun onResume() {
        super.onResume()
        store.load()
        refresh()
        if (::board.isInitialized) board.refresh()
    }

    override fun onBackPressed() {
        if (folderId.isNotEmpty()) goUp() else if (mode != 0) { mode = 0; refresh() } else super.onBackPressed()
    }

    private fun showAutoBackups() {
        val list = Storage.listBackups(File(filesDir, "library")).filter { it.strokes + it.items > 0 }
            .sortedWith(compareBy({ it.target.name }, { -it.strokes }))
        if (list.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Otomatik yedekler")
                .setMessage("İçinde yazı ya da çizim bulunan otomatik yedek bulunamadı.")
                .setPositiveButton("Tamam", null).show()
            return
        }
        val fmt = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("tr"))
        val labels = list.map {
            val id = it.target.name.removePrefix("nb_").removeSuffix(".bin")
            val title = store.find(id)?.title?.ifEmpty { "Adsız defter" } ?: "Defter"
            title + "  ·  " + fmt.format(java.util.Date(it.file.lastModified())) + "  ·  " +
                it.pages + " sayfa, " + it.strokes + " çizgi, " + it.items + " nesne"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Hangi yedeği geri getireyim?")
            .setItems(labels) { _, w ->
                val b = list[w]
                try {
                    if (b.target.exists() && b.target.length() > 0L) b.target.copyTo(File(b.target.parentFile, b.target.name + ".yedek_bos"), true)
                    b.file.copyTo(b.target, true)
                    Toast.makeText(this, "Defter yedekten geri getirildi", Toast.LENGTH_LONG).show()
                    store.load(); refresh()
                } catch (e: Exception) {
                    Toast.makeText(this, "Geri getirilemedi", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun showLibraryBackup() {
        AlertDialog.Builder(this)
            .setTitle("Tüm kütüphane yedeği")
            .setItems(arrayOf("Yedek dosyası oluştur (yeri siz seçersiniz)", "Yedek dosyasından geri yükle", "Otomatik yedeklerden defter kurtar")) { _, which ->
                if (which == 2) {
                    showAutoBackups()
                } else if (which == 0) {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.type = "application/zip"
                    intent.putExtra(Intent.EXTRA_TITLE, "NotUygulamam-kutuphane.zip")
                    startActivityForResult(intent, REQ_LIB_BACKUP)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Geri yükle")
                        .setMessage("Seçtiğiniz yedekteki defterler ve klasörler şu anki kütüphanenin yerine geçer. Şu anki kütüphane uygulama içinde bir önceki sürüm olarak saklanır. Devam edilsin mi?")
                        .setPositiveButton("Dosya seç") { _, _ ->
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                            intent.addCategory(Intent.CATEGORY_OPENABLE)
                            intent.type = "application/zip"
                            startActivityForResult(intent, REQ_LIB_RESTORE)
                        }
                        .setNegativeButton("Vazgeç", null)
                        .show()
                }
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun libraryBackupTo(uri: android.net.Uri) {
        Toast.makeText(this, "Yedek hazırlanıyor...", Toast.LENGTH_SHORT).show()
        val base = filesDir
        Thread {
            try {
                val out = contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Dosya açılamadı")
                out.use { LibraryBackup.write(base, it) }
                runOnUiThread { Toast.makeText(this, "Yedek kaydedildi", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Yedek kaydedilemedi", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun libraryRestoreFrom(uri: android.net.Uri) {
        Toast.makeText(this, "Yedek yükleniyor...", Toast.LENGTH_SHORT).show()
        val base = filesDir
        Thread {
            var ok: Boolean
            try {
                val input = contentResolver.openInputStream(uri) ?: throw java.io.IOException("Dosya açılamadı")
                ok = input.use { LibraryBackup.restore(it, base) }
            } catch (e: Exception) {
                ok = false
            }
            runOnUiThread {
                if (ok) {
                    store.load()
                    folderId = ""
                    refresh()
                    Toast.makeText(this, "Yedek yüklendi", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Yedek yüklenemedi", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun goUp() {
        folderId = store.find(folderId)?.parentId ?: ""
        refresh()
    }

    /** Eski sürümlerdeki tek defteri kütüphaneye taşır. */
    private fun migrate() {
        val old = File(filesDir, "defter.bin")
        val flag = File(filesDir, "library_migrated")
        if (store.items.isEmpty() && old.exists() && !flag.exists()) {
            val it = LibraryItem(store.newId(), false, "Defterim", "")
            it.modified = old.lastModified()
            store.dir.mkdirs()
            old.copyTo(store.notebookFile(it.id), overwrite = true)
            store.items.add(it)
            store.save()
            flag.createNewFile()
        }
    }

    private fun refresh() {
        val list = ArrayList<LibraryItem?>()
        list.add(null)
        when (mode) {
            1 -> list.addAll(store.items.filter { !it.isFolder }.sortedByDescending { it.modified })
            2 -> list.addAll(if (folderId.isEmpty()) store.children("").filter { it.isFolder } else store.children(folderId))
            else -> {}
        }
        shown = list
        val f = store.find(folderId)
        titleView.text = when (mode) {
            0 -> "Not Uygulamam"
            1 -> "Defterlerim"
            else -> f?.title ?: "Klasörlerim"
        }
        backBtn.visibility = if (mode == 2 && folderId.isNotEmpty()) View.VISIBLE else View.GONE
        for ((i, v) in tabViews.withIndex()) {
            val on = (if (i == 3) 2 else i) == mode
            v.setTextColor(if (on) Color.WHITE else Look.ink)
            val g = GradientDrawable()
            g.cornerRadius = dp(24f).toFloat()
            g.setColor(if (on) Look.accent else Color.TRANSPARENT)
            v.background = g
        }
        homeScroll.visibility = if (mode == 0) View.VISIBLE else View.GONE
        gridHost.visibility = if (mode == 0) View.GONE else View.VISIBLE
        if (mode == 0) buildHome()
        adapter.notifyDataSetChanged()
    }

    private fun onTileClick(item: LibraryItem?) {
        when {
            item == null -> if (mode == 1) showEditor(null, false) else showNewMenu()
            item.isFolder -> {
                folderId = item.id
                refresh()
            }
            else -> {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("id", item.id)
                startActivity(intent)
            }
        }
    }

    private fun showNewMenu() {
        AlertDialog.Builder(this)
            .setTitle("Yeni")
            .setItems(arrayOf("Yeni defter", "Planlayıcı $plannerYear (hazır defter)", "Planlayıcı ${plannerYear + 1}", "Yeni klasör")) { _, which ->
                when (which) {
                    0 -> showEditor(null, false)
                    1 -> showEditor(null, false, plannerYear)
                    2 -> showEditor(null, false, plannerYear + 1)
                    else -> showEditor(null, true)
                }
            }
            .show()
    }

    private fun showItemMenu(item: LibraryItem) {
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(arrayOf("Düzenle (ad, kapak, renk)", "Başka klasöre taşı", "Sil")) { _, which ->
                when (which) {
                    0 -> showEditor(item, item.isFolder)
                    1 -> showMoveDialog(item)
                    else -> confirmDelete(item)
                }
            }
            .show()
    }

    private fun showMoveDialog(item: LibraryItem) {
        val blocked = HashSet<String>()
        blocked.add(item.id)
        for (x in store.descendants(item.id)) blocked.add(x.id)
        val targets = store.items.filter { it.isFolder && !blocked.contains(it.id) }
        val labels = ArrayList<String>()
        labels.add("Ana klasör (Defterlerim)")
        for (t in targets) labels.add(t.title)
        AlertDialog.Builder(this)
            .setTitle("Nereye taşınsın?")
            .setItems(labels.toTypedArray()) { _, which ->
                item.parentId = if (which == 0) "" else targets[which - 1].id
                store.save()
                refresh()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun confirmDelete(item: LibraryItem) {
        val extra = if (item.isFolder) " Klasörün içindeki her şey de silinir." else ""
        AlertDialog.Builder(this)
            .setTitle("Silinsin mi?")
            .setMessage("\"${item.title}\" silinecek.$extra Bu işlem geri alınamaz.")
            .setPositiveButton("Sil") { _, _ ->
                val all = ArrayList<LibraryItem>()
                all.add(item)
                all.addAll(store.descendants(item.id))
                for (x in all) {
                    if (!x.isFolder) store.notebookFile(x.id).delete()
                    val img = x.coverImage
                    if (img != null) CoverCache.delete(coverDir, img)
                }
                store.items.removeAll(all.toSet())
                store.save()
                refresh()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun label(t: String): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 14f
        v.setTypeface(null, Typeface.BOLD)
        v.setPadding(0, dp(14f), 0, dp(4f))
        return v
    }

    /** Yeni defter/klasör oluşturma ve var olanı düzenleme penceresi. */
    private val plannerYear: Int
        get() {
            val c = java.util.Calendar.getInstance()
            return c.get(java.util.Calendar.YEAR) + if (c.get(java.util.Calendar.MONTH) >= 8) 1 else 0
        }

    private fun showEditor(existing: LibraryItem?, isFolder: Boolean, presetPlanner: Int = 0) {
        val draft = existing?.copy() ?: LibraryItem(store.newId(), isFolder, "", folderId)
        if (existing == null) draft.color = palette[if (isFolder) 4 else 0]
        var paperIdx = if (presetPlanner > 0) Paper.values().size else Paper.RULED.ordinal
        val plannerYear = if (presetPlanner > 0) presetPlanner else this.plannerYear
        if (existing == null && presetPlanner > 0) draft.title = "Planlayıcı $presetPlanner"
        var committed = false
        val chooser = PageStyleChooser(this, 1200f, 1697f, Color.WHITE)
        val thumbs = ArrayList<View>()
        val viewGroup = android.widget.RadioGroup(this)
        viewGroup.orientation = LinearLayout.HORIZONTAL
        run {
            val one = android.widget.RadioButton(this)
            one.id = 411
            one.text = "Tek sayfa"
            val two = android.widget.RadioButton(this)
            two.id = 412
            two.text = "Çift sayfa (ajanda gibi)"
            viewGroup.addView(one)
            viewGroup.addView(two)
            viewGroup.check(if (presetPlanner > 0) 412 else 411)
        }

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(20f), dp(10f), dp(20f), dp(10f))

        val preview = TileView(this, draft, coverDir, "")
        val plp = LinearLayout.LayoutParams(dp(170f), LinearLayout.LayoutParams.WRAP_CONTENT)
        plp.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(preview, plp)

        fun refreshAll() {
            for (t in thumbs) t.invalidate()
            preview.invalidate()
        }

        val titleEdit = EditText(this)
        titleEdit.hint = if (isFolder) "Klasör adı" else "Defter adı"
        titleEdit.setText(draft.title)
        titleEdit.setSingleLine(true)
        content.addView(titleEdit)

        content.addView(label(if (isFolder) "Klasör rengi" else "Kapak rengi"))
        val colorRow = LinearLayout(this)
        colorRow.orientation = LinearLayout.HORIZONTAL
        for (col in palette) {
            val t = ArtThumb(this, 44, 44, { draft.color == col }) { cv, r ->
                val pnt = Paint(Paint.ANTI_ALIAS_FLAG)
                pnt.color = col
                cv.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, pnt)
            }
            t.setOnClickListener {
                draft.color = col
                refreshAll()
            }
            thumbs.add(t)
            colorRow.addView(t)
        }
        val colorScroll = HorizontalScrollView(this)
        colorScroll.addView(colorRow)
        content.addView(colorScroll)

        if (isFolder) {
            content.addView(label("Klasör simgesi"))
            val iconRow = LinearLayout(this)
            iconRow.orientation = LinearLayout.HORIZONTAL
            for (i in 0 until FolderArt.ICON_COUNT) {
                val t = ArtThumb(this, 56, 56, { draft.icon == i }) { cv, r ->
                    val pnt = Paint(Paint.ANTI_ALIAS_FLAG)
                    pnt.color = Color.rgb(232, 233, 238)
                    cv.drawRoundRect(r, 10f * d, 10f * d, pnt)
                    FolderArt.drawIcon(cv, r.centerX(), r.centerY(), r.width() * 0.3f, i, Color.rgb(60, 62, 70))
                }
                t.setOnClickListener {
                    draft.icon = i
                    refreshAll()
                }
                thumbs.add(t)
                iconRow.addView(t)
            }
            val iconScroll = HorizontalScrollView(this)
            iconScroll.addView(iconRow)
            content.addView(iconScroll)
        } else {
            content.addView(label("Kapak şablonu"))
            val styleRow = LinearLayout(this)
            styleRow.orientation = LinearLayout.HORIZONTAL
            for (i in 0 until CoverArt.STYLE_COUNT) {
                val t = ArtThumb(this, 66, 90, { draft.coverImage == null && draft.coverStyle == i }) { cv, r ->
                    CoverArt.draw(cv, r, i, draft.color)
                }
                t.setOnClickListener {
                    draft.coverStyle = i
                    draft.coverImage = null
                    refreshAll()
                }
                thumbs.add(t)
                styleRow.addView(t)
            }
            val styleScroll = HorizontalScrollView(this)
            styleScroll.addView(styleRow)
            content.addView(styleScroll)

            content.addView(label("Kendi fotoğrafınız"))
            val photoRow = LinearLayout(this)
            photoRow.orientation = LinearLayout.HORIZONTAL
            val pick = Button(this)
            pick.text = "Galeriden seç"
            pick.isAllCaps = false
            pick.setOnClickListener {
                draftForPhoto = draft
                onPhotoReady = { refreshAll() }
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "image/*"
                startActivityForResult(intent, REQ_COVER)
            }
            val clear = Button(this)
            clear.text = "Fotoğrafı kaldır"
            clear.isAllCaps = false
            clear.setOnClickListener {
                draft.coverImage = null
                refreshAll()
            }
            photoRow.addView(pick)
            photoRow.addView(clear)
            content.addView(photoRow)

            if (existing == null) {
                content.addView(label("İlk sayfanın kâğıdı (ya da hazır defter)"))
                val plannerPage = Pair("Planlayıcı " + plannerYear, Paper.YEAR)
                val entries = Paper.values().map { Pair(it.label, it) } + plannerPage
                content.addView(PaperGrid(this, entries, paperIdx, Color.WHITE) { i -> paperIdx = i })
                content.addView(chooser)
                content.addView(label("Sayfa görünümü"))
                content.addView(viewGroup)
            }
        }

        val sv = ScrollView(this)
        sv.addView(content)
        val dlg = AlertDialog.Builder(this)
            .setTitle(
                if (existing == null) (if (isFolder) "Yeni klasör" else "Yeni defter")
                else (if (isFolder) "Klasörü düzenle" else "Defteri düzenle")
            )
            .setView(sv)
            .setPositiveButton(if (existing == null) "Oluştur" else "Kaydet") { _, _ ->
                committed = true
                draft.title = titleEdit.text.toString().trim()
                    .ifEmpty { if (isFolder) "Adsız klasör" else "Adsız defter" }
                if (existing == null) {
                    draft.modified = System.currentTimeMillis()
                    if (!isFolder) {
                        val nb: Notebook
                        if (paperIdx >= Paper.values().size) {
                            nb = Planner.build(plannerYear)
                        } else {
                            nb = Notebook()
                            nb.pages.add(PageData(paper = Paper.values()[paperIdx]))
                        }
                        for (pg in nb.pages) {
                            pg.width = chooser.pageW
                            pg.height = chooser.pageH
                            pg.paperColor = chooser.pageColor
                        }
                        store.dir.mkdirs()
                        Storage.save(nb, store.notebookFile(draft.id))
                        getSharedPreferences("ayar", MODE_PRIVATE).edit()
                            .putBoolean("cift_" + draft.id, viewGroup.checkedRadioButtonId == 412 || (paperIdx >= Paper.values().size && viewGroup.checkedRadioButtonId != 411)).apply()
                    }
                    store.items.add(draft)
                } else {
                    val old = existing.coverImage
                    if (old != null && old != draft.coverImage) CoverCache.delete(coverDir, old)
                    existing.copyFrom(draft)
                }
                pendingImages.remove(draft.coverImage)
                store.save()
                refresh()
            }
            .setNegativeButton("Vazgeç", null)
            .create()
        dlg.setOnDismissListener {
            for (b in pendingImages) CoverCache.delete(coverDir, b)
            pendingImages.clear()
            draftForPhoto = null
            onPhotoReady = null
        }
        dlg.show()
        if (committed) return
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data?.data != null) {
            if (requestCode == REQ_LIB_BACKUP) {
                libraryBackupTo(data.data!!)
                return
            }
            if (requestCode == REQ_LIB_RESTORE) {
                libraryRestoreFrom(data.data!!)
                return
            }
        }
        if (requestCode != REQ_COVER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val target = draftForPhoto ?: return
        Toast.makeText(this, "Fotoğraf hazırlanıyor...", Toast.LENGTH_SHORT).show()
        Thread {
            val base = "c_" + target.id + "_" + System.currentTimeMillis()
            val ok = try {
                CoverCache.importImage(this, uri, coverDir, base)
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (ok) {
                    pendingImages.add(base)
                    target.coverImage = base
                    onPhotoReady?.invoke()
                } else {
                    Toast.makeText(this, "Fotoğraf açılamadı", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    companion object {
        private const val REQ_COVER = 201
        private const val REQ_LIB_BACKUP = 202
        private const val REQ_LIB_RESTORE = 203
    }
}
