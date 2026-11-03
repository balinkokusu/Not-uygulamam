package com.notuygulamam.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class Tool { PEN, HIGHLIGHTER, ERASER, PICKER }

enum class Paper(val label: String) {
    BLANK("boş"), RULED("çizgili"), GRID("kareli"), DOTTED("noktalı")
}

/** Bir çizgi; koordinatlar ve kalınlık sayfa birimindedir, ekran çözünürlüğünden bağımsızdır. */
class Stroke(val color: Int, val width: Float, val highlighter: Boolean, val sens: Float = 0f) {
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    val ps = ArrayList<Float>()
    fun add(x: Float, y: Float, p: Float) {
        xs.add(x); ys.add(y); ps.add(p)
    }
}

sealed class HistoryAction {
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
    val strokes = ArrayList<Stroke>()

    /** Arka planda dışa aktarmak için sayfanın o anki halinin kopyası. */
    fun snapshot(): PageData {
        val p = PageData(width, height, paper)
        p.pdfName = pdfName
        p.pdfIndex = pdfIndex
        p.strokes.addAll(strokes)
        return p
    }

    val undo = ArrayList<HistoryAction>()
    val redo = ArrayList<HistoryAction>()
}

class Notebook {
    val pages = ArrayList<PageData>()
    var current = 0
}

object Storage {
    private const val MAGIC = 0x4E4F5431
    private const val VERSION = 2

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
                o.writeInt(pg.strokes.size)
                for (s in pg.strokes) {
                    o.writeInt(s.color)
                    o.writeFloat(s.width)
                    o.writeBoolean(s.highlighter)
                    o.writeFloat(s.sens)
                    val n = s.xs.size
                    o.writeInt(n)
                    for (i in 0 until n) {
                        o.writeFloat(s.xs[i])
                        o.writeFloat(s.ys[i])
                        o.writeFloat(s.ps[i])
                    }
                }
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
                if (ver != 1 && ver != 2) return null
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
                    val sc = i.readInt()
                    for (j in 0 until sc) {
                        val color = i.readInt()
                        val sw = i.readFloat()
                        val hl = i.readBoolean()
                        val sens = i.readFloat()
                        val n = i.readInt()
                        val s = Stroke(color, sw, hl, sens)
                        for (q in 0 until n) {
                            val x = i.readFloat()
                            val y = i.readFloat()
                            val p = i.readFloat()
                            s.add(x, y, p)
                        }
                        pg.strokes.add(s)
                    }
                    nb.pages.add(pg)
                }
                if (nb.pages.isEmpty()) return null
                nb.current = nb.current.coerceIn(0, nb.pages.size - 1)
                nb
            }
        } catch (e: Exception) {
            null
        }
    }
}
