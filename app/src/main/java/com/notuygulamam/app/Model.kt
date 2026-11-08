package com.notuygulamam.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class Tool { PEN, HIGHLIGHTER, ERASER, PICKER, TEXT, OBJECT, LASSO, LASER, TAPE, HAND }

enum class Paper(val label: String) {
    BLANK("boş"), RULED("çizgili"), GRID("kareli"), DOTTED("noktalı"),
    TODO("yapılacaklar"), DAILY("günlük ajanda"), WEEKLY("haftalık plan"),
    MONTHLY("aylık takvim"), CORNELL("Cornell notu"), YEAR("yıllık takvim"),
    WATER("su takibi"), HABIT("alışkanlık takvimi"), MOOD("ruh hâli takvimi"),
    MEAL("yemek planı"), BUDGET("gider takibi"), READING("okuma günlüğü"),
    GOALS("hedefler"), SLEEP("uyku takibi"), STAFF("porte (nota kâğıdı)"),
    TIMETABLE("ders programı"), LITERATURE("literatür notu"), SHOPPING("alışveriş listesi"),
    COVER("planlayıcı kapağı")
}

/** Bir çizgi; koordinatlar ve kalınlık sayfa birimindedir, ekran çözünürlüğünden bağımsızdır. */
class Stroke(val color: Int, val width: Float, val highlighter: Boolean, val sens: Float = 0f, val kind: Int = 0) {
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    val ps = ArrayList<Float>()
    /** Çizginin başladığı an (ms); ses kaydıyla eşleştirmek için. */
    var t = 0L
    fun add(x: Float, y: Float, p: Float) {
        xs.add(x); ys.add(y); ps.add(p)
    }

    private var cum = FloatArray(64)
    private var cumN = 0
    fun resetArc() { cumN = 0 }

    /** i. noktaya kadar çizginin yay uzunluğu (kesikli ve noktalı kalemler için); önbellekli. */
    fun arc(i: Int): Float {
        if (i <= 0) return 0f
        if (i == 1 || cumN > xs.size) cumN = 1
        if (cum.size < xs.size + 1) cum = cum.copyOf(maxOf(xs.size * 2, 64))
        if (cumN < 1) cumN = 1
        cum[0] = 0f
        var j = cumN
        while (j <= i && j < xs.size) {
            cum[j] = cum[j - 1] + Math.hypot((xs[j] - xs[j - 1]).toDouble(), (ys[j] - ys[j - 1]).toDouble()).toFloat()
            j++
        }
        if (j > cumN) cumN = j
        return cum[minOf(i, xs.size - 1)]
    }
}

/** Sayfadaki metin kutusu, çıkartma ya da resim; (x, y) nesnenin merkezidir, ölçüler sayfa birimindedir. */
/** Şablonun sayfa başına koyduğu başlık / tarih satırı; silgi ve karalama bunu silmez, yalnız seçilip silinebilir. */
fun PageItem.isTemplateHeader(): Boolean =
    type == PageItem.TEXT && size == 30f && color == android.graphics.Color.rgb(120, 108, 110) &&
        y < 80f && Math.abs((x - w / 2f) - 70f) < 2f

class PageItem(var type: Int, var x: Float, var y: Float) {
    var w = 300f
    var h = 300f
    var rot = 0f
    var color = android.graphics.Color.BLACK
    var text = ""
    var size = 44f
    var kind = 0

    /** Tablo nesnesinde bir satırın en az yüksekliği. */
    var rowH = 64f

    /** true ise nesne çizgilerin üstünde, false ise altında çizilir. */
    var above = false

    fun copy(): PageItem {
        val c = PageItem(type, x, y)
        c.w = w; c.h = h; c.rot = rot; c.color = color; c.text = text
        c.size = size; c.kind = kind; c.above = above; c.data = data; c.rowH = rowH
        return c
    }

    /** Resim çıkartmasının sıkıştırılmış baytları (JPEG ya da PNG). */
    var data: ByteArray? = null

    /** Çözülmüş resim; dosyaya yazılmaz. */
    var bmp: android.graphics.Bitmap? = null

    companion object {
        const val TEXT = 0
        const val STICKER = 1
        const val IMAGE = 2
        const val TABLE = 3
    }
}

/** Eklenen her işleme artan bir sıra numarası verir; çift sayfada geri almanın sayfalar arası sırasını belirler. */
class HistoryList : ArrayList<HistoryAction>() {
    override fun add(element: HistoryAction): Boolean {
        element.seq = ++counter
        return super.add(element)
    }

    companion object {
        private var counter = 0L
    }
}

sealed class HistoryAction {
    var seq = 0L

    class ItemAdd(val item: PageItem) : HistoryAction()
    class ItemRemove(val item: PageItem, val index: Int) : HistoryAction()
    class ItemsErase(val removed: List<Pair<Int, PageItem>>) : HistoryAction()
    class Move(val strokes: List<Stroke>, val items: List<PageItem>, val dx: Float, val dy: Float) : HistoryAction()
    /** Lasso ile silme/çoğaltma: çıkarılanlar geri konur, eklenenler geri alınınca çıkarılır. */
    class Group(
        val removedS: List<Pair<Int, Stroke>>, val removedI: List<Pair<Int, PageItem>>,
        val addedS: List<Stroke>, val addedI: List<PageItem>
    ) : HistoryAction()
    class Scale(val strokes: List<Stroke>, val items: List<PageItem>, val fx: Float, val fy: Float, val ax: Float, val ay: Float) : HistoryAction()
    class Add(val stroke: Stroke) : HistoryAction()
    class Erase(val removed: List<Pair<Int, Stroke>>) : HistoryAction()
}

class PageData(
    var width: Float = 1200f,
    var height: Float = 1700f,
    var paper: Paper = Paper.RULED
) {
    /** Sayfa bir PDF sayfasından geliyorsa PDF dosyasının adı ve sayfa sırası. */
    var pdfName: String? = null
    var pdfIndex = 0

    /** Tarihli takvim sayfaları için yıl ve ay (1-12); 0 ise tarihsiz şablon. */
    var calYear = 0
    var calMonth = 0

    /** Kâğıdın rengi (ARGB). */
    var paperColor: Int = -1

    /** Sayfanın ait olduğu gün (yyyyMMdd); 0 ise tarihsiz. Aynı güne ait sayfalar tek ayracı paylaşır. */
    var dateKey = 0
    val items = ArrayList<PageItem>()
    val strokes = ArrayList<Stroke>()

    /** Arka planda dışa aktarmak için sayfanın o anki halinin kopyası. */
    fun snapshot(): PageData {
        val p = PageData(width, height, paper)
        p.pdfName = pdfName
        p.pdfIndex = pdfIndex
        p.calYear = calYear
        p.calMonth = calMonth
        p.paperColor = paperColor
        p.dateKey = dateKey
        p.items.addAll(items)
        p.strokes.addAll(strokes)
        return p
    }

    val undo = HistoryList()
    val redo = HistoryList()
}

/** Sayfanın kenarındaki renkli ayraç; dokununca hedef sayfaya gider. */
class Tab(var label: String, var color: Int, var target: PageData, var auto: Boolean = false, var side: Int = 0) {
    companion object {
        /** Kenar başına en fazla ayraç: sol, sağ, üst, alt. */
        val CAP = intArrayOf(30, 30, 16, 0)
        val SIDE_NAMES = arrayOf("Sol", "Sağ", "Üst", "Alt")
        const val SIDES = 3
    }
}

class Notebook {
    val pages = ArrayList<PageData>()
    val tabs = ArrayList<Tab>()
    var current = 0

    /** Kenardaki elle eklenmiş ayraç sayısı; otomatik gün ayraçları sınıra sayılmaz (aya göre süzülür). */
    fun sideCount(side: Int) = tabs.count { it.side == side && !it.auto }

    /** Tercih edilen kenarda yer varsa onu, yoksa sırayla sol, sağ, üst kenarlardan yeri olanı verir; hepsi doluysa -1. */
    fun freeSide(pref: Int = 0): Int {
        val p = pref.coerceIn(0, 2)
        if (sideCount(p) < Tab.CAP[p]) return p
        for (s in intArrayOf(0, 1, 2)) if (sideCount(s) < Tab.CAP[s]) return s
        return -1
    }

    private fun place(cnt: IntArray, pref: Int): Int {
        var s = pref.coerceIn(0, 2)
        if (cnt[s] >= Tab.CAP[s]) {
            for (c in intArrayOf(0, 1, 2)) if (cnt[c] < Tab.CAP[c]) { s = c; break }
        }
        return s
    }

    /** Kenar sınırlarını uygular; kaldırılan alt kenardaki ayraçlar sola taşınır. Gün ayraçları hep sağdadır. */
    fun fitTabSides() {
        val cnt = IntArray(4)
        for (t in tabs) {
            if (t.auto) { t.side = 1; continue }
            val s = place(cnt, if (t.side >= 3) 0 else t.side)
            t.side = s
            cnt[s]++
        }
    }

    /** Otomatik düzen: gün, ay ve yıl ayraçları sağa, kullanıcının kendi ayraçları solda kalır. */
    fun relayoutTabs() {
        val cnt = IntArray(4)
        for (t in tabs) {
            if (t.auto) { t.side = 1; continue }
            val cal = Planner.SHORT.contains(t.label) || Regex("\\d{4}").matches(t.label)
            val s = place(cnt, if (cal) 1 else (if (t.side >= 3) 0 else t.side))
            t.side = s
            cnt[s]++
        }
    }
}

object Storage {
    private const val MAGIC = 0x4E4F5431
    private const val VERSION = 10

    /** Günde bir kez, kayıttan önce mevcut dosyanın kopyasını alır; son 4 günün kopyaları saklanır. */
    private fun dailyBackup(file: File) {
        try {
            if (!file.exists() || file.length() == 0L) return
            val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
            val dst = File(file.parentFile, file.name + ".gun" + day)
            if (!dst.exists()) file.copyTo(dst)
            val all = file.parentFile?.listFiles { f -> f.name.startsWith(file.name + ".gun") }?.sortedBy { it.name } ?: return
            for (old in all.dropLast(4)) old.delete()
        } catch (e: Exception) {
        }
    }

    fun save(nb: Notebook, file: File) {
        dailyBackup(file)
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { o ->
            o.writeInt(MAGIC)
            o.writeInt(VERSION)
            o.writeInt(nb.pages.size)
            o.writeInt(nb.current)
            for (pg in nb.pages) {
                o.writeFloat(pg.width)
                o.writeFloat(pg.height)
                o.writeInt(pg.paper.ordinal)
                val nm = pg.pdfName
                o.writeBoolean(nm != null)
                if (nm != null) {
                    o.writeUTF(nm)
                    o.writeInt(pg.pdfIndex)
                }
                o.writeInt(pg.calYear)
                o.writeInt(pg.calMonth)
                o.writeInt(pg.paperColor)
                o.writeInt(pg.dateKey)
                o.writeInt(pg.strokes.size)
                for (s in pg.strokes) {
                    o.writeInt(s.color)
                    o.writeFloat(s.width)
                    o.writeBoolean(s.highlighter)
                    o.writeFloat(s.sens)
                    o.writeInt(s.kind)
                    o.writeLong(s.t)
                    val n = s.xs.size
                    o.writeInt(n)
                    for (i in 0 until n) {
                        o.writeFloat(s.xs[i])
                        o.writeFloat(s.ys[i])
                        o.writeFloat(s.ps[i])
                    }
                }
                o.writeInt(pg.items.size)
                for (it in pg.items) {
                    o.writeInt(it.type)
                    o.writeFloat(it.x); o.writeFloat(it.y)
                    o.writeFloat(it.w); o.writeFloat(it.h); o.writeFloat(it.rot)
                    o.writeInt(it.color)
                    o.writeUTF(it.text)
                    o.writeFloat(it.size)
                    o.writeInt(it.kind)
                    o.writeBoolean(it.above)
                    if (it.type == PageItem.TABLE) o.writeFloat(it.rowH)
                    val dt = it.data
                    o.writeInt(dt?.size ?: -1)
                    if (dt != null) o.write(dt)
                }
            }
            val valid = nb.tabs.filter { nb.pages.indexOf(it.target) >= 0 }
            o.writeInt(valid.size)
            for (t in valid) {
                o.writeUTF(t.label)
                o.writeInt(t.color)
                o.writeInt(nb.pages.indexOf(t.target))
                o.writeBoolean(t.auto)
                o.writeInt(t.side)
            }
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    /** Okunamayan bir defter dosyası asla sessizce üzerine yazılmasın diye yan tarafa kopyalanır. */
    private fun quarantine(file: File) {
        try {
            if (file.exists() && file.length() > 0L) {
                var k = 1
                var dst = File(file.parentFile, file.name + ".bozuk" + k)
                while (dst.exists()) { k++; dst = File(file.parentFile, file.name + ".bozuk" + k) }
                file.copyTo(dst)
            }
        } catch (e: Exception) {
        }
    }

    private fun score(nb: Notebook): Int = nb.pages.sumOf { it.strokes.size + it.items.size }

    /**
     * Okuma hatası yüzünden boş açılıp üzerine yazılan defterleri, yanındaki .bozuk / .gun yedeklerinden geri getirir.
     * Yalnızca daha dolu bir yedek varsa değiştirir; eski dosya ".yedek_bos" olarak saklanır. Döner: kurtarılan defter sayısı.
     */
    fun recover(dir: File): Int {
        var n = 0
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("nb_") && f.name.endsWith(".bin") } ?: return 0
        for (f in files) {
            try {
                val cur = loadInner(f)
                val curScore = if (cur == null) -1 else score(cur)
                val curPages = cur?.pages?.size ?: 0
                var best: File? = null
                var bestScore = curScore
                var bestPages = curPages
                val cands = dir.listFiles { c -> c.isFile && (c.name.startsWith(f.name + ".bozuk") || c.name.startsWith(f.name + ".gun")) } ?: emptyArray()
                for (c in cands) {
                    val nb = loadInner(c) ?: continue
                    val sc = score(nb)
                    if (sc > bestScore || (sc == bestScore && sc > 0 && nb.pages.size > bestPages)) {
                        best = c; bestScore = sc; bestPages = nb.pages.size
                    }
                }
                if (best != null && bestScore > curScore) {
                    if (f.exists() && f.length() > 0L) f.copyTo(File(dir, f.name + ".yedek_bos"), true)
                    best.copyTo(f, true)
                    n++
                }
            } catch (e: Exception) {
            }
        }
        return n
    }

    fun load(file: File): Notebook? {
        val r = loadInner(file)
        if (r == null) quarantine(file)
        return r
    }

    private fun loadInner(file: File): Notebook? {
        if (!file.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { i ->
                if (i.readInt() != MAGIC) return null
                val ver = i.readInt()
                if (ver < 1 || ver > VERSION) return null
                val nb = Notebook()
                val count = i.readInt()
                nb.current = i.readInt()
                for (k in 0 until count) {
                    val w = i.readFloat()
                    val h = i.readFloat()
                    val paper = Paper.values()[i.readInt()]
                    val pg = PageData(w, h, paper)
                    if (ver >= 2 && i.readBoolean()) {
                        pg.pdfName = i.readUTF()
                        pg.pdfIndex = i.readInt()
                    }
                    if (ver >= 4) {
                        pg.calYear = i.readInt()
                        pg.calMonth = i.readInt()
                    }
                    if (ver >= 5) pg.paperColor = i.readInt()
                    if (ver >= 6) pg.dateKey = i.readInt()
                    val sc = i.readInt()
                    for (j in 0 until sc) {
                        val color = i.readInt()
                        val sw = i.readFloat()
                        val hl = i.readBoolean()
                        val sens = i.readFloat()
                        val kind = if (ver >= 6) i.readInt() else 0
                        // kayıt sırası: tür, (sürüm 10+) zaman, nokta sayısı
                        val tm = if (ver >= 10) i.readLong() else 0L
                        val n = i.readInt()
                        val s = Stroke(color, sw, hl, sens, kind)
                        if (ver >= 10) s.t = tm
                        for (q in 0 until n) {
                            val x = i.readFloat()
                            val y = i.readFloat()
                            val p = i.readFloat()
                            s.add(x, y, p)
                        }
                        pg.strokes.add(s)
                    }
                    if (ver >= 6) {
                        val ic = i.readInt()
                        for (q in 0 until ic) {
                            val it = PageItem(i.readInt(), i.readFloat(), i.readFloat())
                            it.w = i.readFloat(); it.h = i.readFloat(); it.rot = i.readFloat()
                            it.color = i.readInt()
                            it.text = i.readUTF()
                            it.size = i.readFloat()
                            it.kind = i.readInt()
                            if (ver >= 7) it.above = i.readBoolean()
                            if (ver >= 9 && it.type == PageItem.TABLE) it.rowH = i.readFloat()
                            val len = i.readInt()
                            if (len >= 0) {
                                val b = ByteArray(len)
                                i.readFully(b)
                                it.data = b
                            }
                            pg.items.add(it)
                        }
                    }
                    nb.pages.add(pg)
                }
                if (nb.pages.isEmpty()) return null
                if (ver >= 3) {
                    val tc = i.readInt()
                    for (k in 0 until tc) {
                        val label = i.readUTF()
                        val color = i.readInt()
                        val idx = i.readInt()
                        val auto = if (ver >= 6) i.readBoolean() else false
                        val side = if (ver >= 8) i.readInt() else 0
                        if (idx in 0 until nb.pages.size) nb.tabs.add(Tab(label, color, nb.pages[idx], auto, side))
                    }
                    if (ver < 9) nb.relayoutTabs() else nb.fitTabSides()
                }
                nb.current = nb.current.coerceIn(0, nb.pages.size - 1)
                nb
            }
        } catch (e: Exception) {
            null
        }
    }
}
