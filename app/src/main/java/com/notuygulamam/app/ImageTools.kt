package com.notuygulamam.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Toplu çıkartma sayfasını tek tek çıkartmaya ayırma ve kement ile görsel kesme. */
object ImageTools {
    /** Kaynak görselin oranlarıyla: merkez (cx, cy) ve boyut (w, h), hepsi 0..1 arasında. */
    class Piece(val bmp: Bitmap, val cx: Float, val cy: Float, val w: Float, val h: Float)

    private fun dist(a: Int, b: Int): Int {
        val dr = abs(((a shr 16) and 255) - ((b shr 16) and 255))
        val dg = abs(((a shr 8) and 255) - ((b shr 8) and 255))
        val db = abs((a and 255) - (b and 255))
        return max(dr, max(dg, db))
    }

    /** Arka plan renginden ayrışan bölgeleri bulur, her birini ayrı saydam görsel olarak keser. */
    fun split(src: Bitmap, maxSide: Int = 1600): List<Piece> {
        val sc = min(1f, maxSide.toFloat() / max(src.width, src.height))
        val w = max(1, (src.width * sc).toInt())
        val h = max(1, (src.height * sc).toInt())
        val bm = if (sc < 1f) Bitmap.createScaledBitmap(src, w, h, true) else src.copy(Bitmap.Config.ARGB_8888, false)
        val px = IntArray(w * h)
        bm.getPixels(px, 0, w, 0, 0, w, h)

        // Kenar piksellerine bakarak saydam mı yoksa düz renkli arka plan mı olduğunu anla
        var transp = 0
        var total = 0
        val border = ArrayList<Int>()
        fun addB(x: Int, y: Int) {
            val p = px[y * w + x]
            total++
            if ((p ushr 24) < 40) transp++ else border.add(p)
        }
        for (x in 0 until w step 3) { addB(x, 0); addB(x, h - 1) }
        for (y in 0 until h step 3) { addB(0, y); addB(w - 1, y) }
        val alphaMode = transp * 2 > total
        var bg = 0xFFFFFFFF.toInt()
        if (!alphaMode && border.isNotEmpty()) {
            val cnt = HashMap<Int, Int>()
            for (p in border) {
                val q = ((p shr 20) and 15) shl 8 or (((p shr 12) and 15) shl 4) or ((p shr 4) and 15)
                cnt[q] = (cnt[q] ?: 0) + 1
            }
            val best = cnt.maxByOrNull { it.value }!!.key
            var sr = 0L; var sg = 0L; var sb = 0L; var n = 0
            for (p in border) {
                val q = ((p shr 20) and 15) shl 8 or (((p shr 12) and 15) shl 4) or ((p shr 4) and 15)
                if (q == best) { sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255; n++ }
            }
            bg = (255 shl 24) or ((sr / n).toInt() shl 16) or ((sg / n).toInt() shl 8) or (sb / n).toInt()
        }
        val bgLike = BooleanArray(w * h) {
            val p = px[it]
            if (alphaMode) (p ushr 24) < 40 else dist(p, bg) <= 34
        }

        // Yakın parçaları (gölge, ince boşluk) aynı çıkartmada toplamak için genişlet
        val r = max(1, min(w, h) / 700)
        val fg = BooleanArray(w * h) { !bgLike[it] }
        val tmp = IntArray(w * h)
        for (y in 0 until h) {
            var run = 0
            val row = y * w
            for (x in 0 until min(r, w)) if (fg[row + x]) run++
            for (x in 0 until w) {
                val add = x + r
                if (add < w && fg[row + add]) run++
                val rem = x - r - 1
                if (rem >= 0 && fg[row + rem]) run--
                tmp[row + x] = run
            }
        }
        val dil = BooleanArray(w * h)
        for (x in 0 until w) {
            var run = 0
            for (y in 0 until min(r, h)) run += tmp[y * w + x]
            for (y in 0 until h) {
                val add = y + r
                if (add < h) run += tmp[add * w + x]
                val rem = y - r - 1
                if (rem >= 0) run -= tmp[rem * w + x]
                dil[y * w + x] = run > 0
            }
        }

        // Bağlı bileşenler (8 komşuluk)
        val lab = IntArray(w * h)
        var n = 0
        val stack = IntArray(w * h)
        val x0s = ArrayList<Int>(); val y0s = ArrayList<Int>(); val x1s = ArrayList<Int>(); val y1s = ArrayList<Int>(); val area = ArrayList<Int>()
        for (s in 0 until w * h) {
            if (!dil[s] || lab[s] != 0) continue
            n++
            var sp = 0
            stack[sp++] = s
            lab[s] = n
            var mx0 = w; var my0 = h; var mx1 = 0; var my1 = 0; var a = 0
            while (sp > 0) {
                val p = stack[--sp]
                val x = p % w
                val y = p / w
                if (fg[p]) a++
                if (x < mx0) mx0 = x
                if (x > mx1) mx1 = x
                if (y < my0) my0 = y
                if (y > my1) my1 = y
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (dil[q] && lab[q] == 0) { lab[q] = n; stack[sp++] = q }
                }
            }
            x0s.add(mx0); y0s.add(my0); x1s.add(mx1); y1s.add(my1); area.add(a)
        }

        val minArea = max(60, (w.toLong() * h / 5000).toInt())
        val out = ArrayList<Piece>()
        for (id in 1..n) {
            if (area[id - 1] < minArea) continue
            var bx0 = max(0, x0s[id - 1] - 1)
            var by0 = max(0, y0s[id - 1] - 1)
            var bx1 = min(w - 1, x1s[id - 1] + 1)
            var by1 = min(h - 1, y1s[id - 1] + 1)
            val bw = bx1 - bx0 + 1
            val bh = by1 - by0 + 1
            if (bw < 14 || bh < 14) continue
            val outside = BooleanArray(bw * bh)
            val q = IntArray(bw * bh)
            var qs = 0
            fun idx(x: Int, y: Int) = (y - by0) * bw + (x - bx0)
            if (!alphaMode) {
                for (y in by0..by1) for (x in bx0..bx1) {
                    val p = y * w + x
                    if (lab[p] == id && bgLike[p]) {
                        val edge = x == bx0 || y == by0 || x == bx1 || y == by1 ||
                            lab[p - 1] != id || lab[p + 1] != id || lab[p - w] != id || lab[p + w] != id
                        if (edge) { outside[idx(x, y)] = true; q[qs++] = p }
                    }
                }
                var head = 0
                while (head < qs) {
                    val p = q[head++]
                    val x = p % w
                    val y = p / w
                    val nb = intArrayOf(x - 1, y, x + 1, y, x, y - 1, x, y + 1)
                    var k = 0
                    while (k < 8) {
                        val nx = nb[k]; val ny = nb[k + 1]; k += 2
                        if (nx < bx0 || nx > bx1 || ny < by0 || ny > by1) continue
                        val pp = ny * w + nx
                        val ii = idx(nx, ny)
                        if (!outside[ii] && lab[pp] == id && bgLike[pp]) { outside[ii] = true; q[qs++] = pp }
                    }
                }
            }
            val pix = IntArray(bw * bh)
            for (y in by0..by1) for (x in bx0..bx1) {
                val p = y * w + x
                val ii = idx(x, y)
                pix[ii] = if (lab[p] == id && !outside[ii] && !(alphaMode && bgLike[p])) {
                    if (alphaMode) px[p] else (px[p] or (255 shl 24))
                } else 0
            }
            val pb = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            pb.setPixels(pix, 0, bw, 0, 0, bw, bh)
            out.add(Piece(pb, (bx0 + bw / 2f) / w, (by0 + bh / 2f) / h, bw.toFloat() / w, bh.toFloat() / h))
            if (out.size >= 250) break
        }
        return out
    }

    /** Kement ile kesme: (piece, remaining, bbox); poly kaynak görselin piksel koordinatlarında x,y çiftleridir. */
    class CutResult(val piece: Bitmap, val remaining: Bitmap, val box: RectF)

    fun cut(src: Bitmap, poly: FloatArray): CutResult? {
        if (poly.size < 6) return null
        val path = Path()
        path.moveTo(poly[0], poly[1])
        var i = 2
        while (i + 1 < poly.size) { path.lineTo(poly[i], poly[i + 1]); i += 2 }
        path.close()
        val b = RectF()
        path.computeBounds(b, true)
        val bx = max(0, b.left.toInt())
        val by = max(0, b.top.toInt())
        val bx2 = min(src.width, b.right.toInt() + 1)
        val by2 = min(src.height, b.bottom.toInt() + 1)
        val bw = bx2 - bx
        val bh = by2 - by
        if (bw < 8 || bh < 8) return null
        val piece = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(piece)
        c.translate(-bx.toFloat(), -by.toFloat())
        c.clipPath(path)
        c.drawBitmap(src, 0f, 0f, null)
        val rest = src.copy(Bitmap.Config.ARGB_8888, true)
        val c2 = Canvas(rest)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        c2.drawPath(path, p)
        return CutResult(piece, rest, RectF(bx.toFloat(), by.toFloat(), bx2.toFloat(), by2.toFloat()))
    }
}
