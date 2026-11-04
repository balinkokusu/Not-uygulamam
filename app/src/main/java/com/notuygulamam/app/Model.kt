package com.notuygulamam.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class Tool { PEN, HIGHLIGHTER, ERASER, PICKER, TEXT, OBJECT }

enum class Paper(val label: String) {
    BLANK("boş"), RULED("çizgili"), GRID("kareli"), DOTTED("noktalı"),
    TODO("yapılacaklar"), DAILY("günlük ajanda"), WEEKLY("haftalık plan"),
    MONTHLY("aylık takvim"), CORNELL("Cornell notu"), YEAR("yıllık takvim")
}

/** Bir çizgi; koordinatlar ve kalınlık sayfa birimindedir, ekran çözünürlüğünden bağımsızdır. */
class Stroke(val color: Int, val width: Float, val highlighter: Boolean, val sens: Float = 0f, val kind: Int = 0) {
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    val ps = ArrayList<Float>()
    fun add(x: Float, y: Float, p: Float) {
        xs.add(x); ys.add(y); ps.add(p)
    }
}

/** Sayfadaki metin kutusu, çıkartma ya da resim; (x, y) nesnenin merkezidir, ölçüler sayfa birimindedir. */
class PageItem(var type: Int, var x: Float, var y: Float) {
    var w = 300f
    var h = 300f
    var rot = 0f
    var color = android.graphics.Color.BLACK
    var text = ""
    var size = 44f
    var kind = 0

    /** true ise nesne çizgilerin üstünde, false ise altında çizilir. */
    var above = false

    fun copy(): PageItem {
        val c = PageItem(type, x, y)
        c.w = w; c.h = h; c.rot = rot; c.color = color; c.text = text
        c.size = size; c.kind = kind; c.above = above; c.data = data
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
    }
}

sealed class HistoryAction {
    class ItemAdd(val item: PageItem) : HistoryAction()
    class ItemRemove(val item: PageItem, val index: Int) : HistoryAction()
    class ItemsErase(val removed: List<Pair<Int, PageItem>>) : HistoryAction()
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

    val undo = ArrayList<HistoryAction>()
    val redo = ArrayList<HistoryAction>()
}

/** Sayfanın kenarındaki renkli ayraç; dokununca hedef sayfaya gider. */
class Tab(var label: String, var color: Int, var target: PageData, var auto: Boolean = false)

class Notebook {
    val pages = ArrayList<PageData>()
    val tabs = ArrayList<Tab>()
    var current = 0
}

object Storage {
    private const val MAGIC = 0x4E4F5431
    private const val VERSION = 7

    fun save(nb: Notebook, file: File) {
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
            }
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    fun load(file: File): Notebook? {
        if (!file.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { i ->
                if (i.readInt() != MAGIC) return null
                val ver = i.readInt()
                if (ver < 1 || ver > 7) return null
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
                        val n = i.readInt()
                        val s = Stroke(color, sw, hl, sens, kind)
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
                        if (idx in 0 until nb.pages.size) nb.tabs.add(Tab(label, color, nb.pages[idx], auto))
                    }
                }
                nb.current = nb.current.coerceIn(0, nb.pages.size - 1)
                nb
            }
        } catch (e: Exception) {
            null
        }
    }
}
