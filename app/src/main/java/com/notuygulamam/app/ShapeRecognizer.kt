package com.notuygulamam.app

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Elle çizilen çizgiyi düzgün bir şekle çevirir: doğru, daire/elips, üçgen, dikdörtgen, yarım daire/yay ve kırık çizgi.
 * Koordinatlar sayfa birimindedir.
 */
object ShapeRecognizer {
    class Result(val xs: FloatArray, val ys: FloatArray, val isLine: Boolean)

    private class P(val x: Float, val y: Float)

    fun recognize(xs: List<Float>, ys: List<Float>): Result? {
        if (xs.size < 8) return null
        val pts = resample(xs, ys, 96)
        val len = pathLength(pts)
        if (len < 110f) return null
        val first = pts[0]
        val last = pts[pts.size - 1]
        val chord = hypot(last.x - first.x, last.y - first.y)

        // 1) Doğru (her açıda)
        if (chord > len * 0.8f && maxDevFromChord(pts) < max(len * 0.05f, 9f)) {
            return Result(floatArrayOf(first.x, last.x), floatArrayOf(first.y, last.y), true)
        }
        val closed = chord < len * 0.2f
        return if (closed) closedShape(pts, len) else openShape(pts, len)
    }

    // ---- Kapalı şekiller ----

    private fun closedShape(pts: List<P>, len: Float): Result? {
        val n = pts.size
        var cx = 0f
        var cy = 0f
        for (p in pts) {
            cx += p.x
            cy += p.y
        }
        cx /= n
        cy /= n

        // Elips (temel eksenler)
        var sxx = 0f
        var syy = 0f
        var sxy = 0f
        for (p in pts) {
            val dx = p.x - cx
            val dy = p.y - cy
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
        sxx /= n
        syy /= n
        sxy /= n
        val theta = 0.5f * atan2(2f * sxy, sxx - syy)
        val ct = cos(theta)
        val st = sin(theta)
        var maxU = 0f
        var maxV = 0f
        for (p in pts) {
            val dx = p.x - cx
            val dy = p.y - cy
            maxU = max(maxU, abs(dx * ct + dy * st))
            maxV = max(maxV, abs(-dx * st + dy * ct))
        }
        val a = maxU
        val b = maxV
        var ellErr = Float.MAX_VALUE
        if (a > 18f && b > 18f) {
            var e = 0f
            for (p in pts) {
                val dx = p.x - cx
                val dy = p.y - cy
                val u = (dx * ct + dy * st) / a
                val v = (-dx * st + dy * ct) / b
                e += abs(sqrt(u * u + v * v) - 1f) * (a + b) / 2f
            }
            ellErr = e / n
        }

        // Çokgen (üçgen / dörtgen)
        val verts = closedVertices(pts, len * 0.035f)
        var polyErr = Float.MAX_VALUE
        if (verts.size in 3..4) {
            var e = 0f
            for (p in pts) e += distToPolygon(p, verts)
            polyErr = e / n
        }

        val ellipseOk = ellErr < len * 0.02f
        val polyOk = polyErr < len * 0.02f
        if (polyOk && (!ellipseOk || polyErr < ellErr * 0.8f)) {
            return polygonResult(verts)
        }
        if (ellErr < len * 0.04f) {
            var rr = a
            var r2 = b
            if (min(a, b) / max(a, b) > 0.88f) {
                val r = (a + b) / 2f
                rr = r
                r2 = r
            }
            return ellipseResult(cx, cy, rr, r2, theta)
        }
        if (polyErr < len * 0.03f) return polygonResult(verts)
        return null
    }

    private fun polygonResult(vIn: List<P>): Result {
        var v = vIn
        if (v.size == 4 && nearRectangle(v)) v = rectangleFrom(v)
        val xs = FloatArray(v.size + 1)
        val ys = FloatArray(v.size + 1)
        for (i in v.indices) {
            xs[i] = v[i].x
            ys[i] = v[i].y
        }
        xs[v.size] = v[0].x
        ys[v.size] = v[0].y
        return Result(xs, ys, false)
    }

    private fun nearRectangle(v: List<P>): Boolean {
        for (i in 0 until 4) {
            val a = v[(i + 3) % 4]
            val b = v[i]
            val c = v[(i + 1) % 4]
            val ux = a.x - b.x
            val uy = a.y - b.y
            val wx = c.x - b.x
            val wy = c.y - b.y
            val cosA = (ux * wx + uy * wy) / (hypot(ux, uy) * hypot(wx, wy) + 1e-6f)
            if (abs(cosA) > 0.35f) return false
        }
        return true
    }

    /** Dörtgeni en uzun kenarının açısına göre döndürülmüş dikdörtgene çevirir; yataya/dikeye yakınsa eksenlere oturtur. */
    private fun rectangleFrom(v: List<P>): List<P> {
        var best = 0f
        var ang = 0f
        for (i in 0 until 4) {
            val a = v[i]
            val b = v[(i + 1) % 4]
            val l = hypot(b.x - a.x, b.y - a.y)
            if (l > best) {
                best = l
                ang = atan2(b.y - a.y, b.x - a.x)
            }
        }
        val deg = ang * 180f / PI.toFloat()
        val m = ((deg % 90f) + 90f) % 90f
        if (m < 9f || m > 81f) ang = 0f
        var cx = 0f
        var cy = 0f
        for (p in v) {
            cx += p.x
            cy += p.y
        }
        cx /= 4f
        cy /= 4f
        val c = cos(ang)
        val s = sin(ang)
        var minU = Float.MAX_VALUE
        var maxU = -Float.MAX_VALUE
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        for (p in v) {
            val dx = p.x - cx
            val dy = p.y - cy
            val u = dx * c + dy * s
            val w = -dx * s + dy * c
            minU = min(minU, u)
            maxU = max(maxU, u)
            minV = min(minV, w)
            maxV = max(maxV, w)
        }
        val corners = listOf(Pair(minU, minV), Pair(maxU, minV), Pair(maxU, maxV), Pair(minU, maxV))
        return corners.map { (u, w) -> P(cx + u * c - w * s, cy + u * s + w * c) }
    }

    private fun ellipseResult(cx: Float, cy: Float, a: Float, b: Float, theta: Float): Result {
        val n = 72
        val xs = FloatArray(n + 1)
        val ys = FloatArray(n + 1)
        val ct = cos(theta)
        val st = sin(theta)
        for (i in 0..n) {
            val t = (2.0 * PI * i / n).toFloat()
            val u = a * cos(t)
            val v = b * sin(t)
            xs[i] = cx + u * ct - v * st
            ys[i] = cy + u * st + v * ct
        }
        return Result(xs, ys, false)
    }

    /** Kapalı eğrinin köşelerini bulur: ilk noktadan en uzak noktayla ikiye bölüp her yarıya RDP uygular. */
    private fun closedVertices(pts: List<P>, eps: Float): List<P> {
        val n = pts.size
        val p0 = pts[0]
        var far = 0
        var fd = 0f
        for (i in 1 until n) {
            val d = hypot(pts[i].x - p0.x, pts[i].y - p0.y)
            if (d > fd) {
                fd = d
                far = i
            }
        }
        if (far == 0) return emptyList()
        val loop = ArrayList<P>(pts)
        loop.add(pts[0])
        val a = rdp(loop, 0, far, eps)
        val b = rdp(loop, far, loop.size - 1, eps)
        val idx = ArrayList<Int>()
        idx.addAll(a)
        for (i in b) if (i != far) idx.add(i)
        if (idx.isNotEmpty() && idx[idx.size - 1] == loop.size - 1) idx.removeAt(idx.size - 1)
        return mergeClose(idx.map { loop[it] })
    }

    private fun mergeClose(v: List<P>): List<P> {
        val out = ArrayList<P>()
        for (p in v) {
            val q = out.lastOrNull()
            if (q == null || hypot(p.x - q.x, p.y - q.y) > 14f) out.add(p)
        }
        if (out.size > 2 && hypot(out[0].x - out[out.size - 1].x, out[0].y - out[out.size - 1].y) <= 14f) {
            out.removeAt(out.size - 1)
        }
        return out
    }

    private fun distToPolygon(p: P, v: List<P>): Float {
        var best = Float.MAX_VALUE
        for (i in v.indices) {
            val a = v[i]
            val b = v[(i + 1) % v.size]
            best = min(best, distSeg(p, a, b))
        }
        return best
    }

    // ---- Açık şekiller ----

    private fun openShape(pts: List<P>, len: Float): Result? {
        val arc = fitArc(pts)
        if (arc != null) return arc
        val verts = rdpOpen(pts, len * 0.04f)
        if (verts.size in 3..6) {
            var minSeg = Float.MAX_VALUE
            for (i in 0 until verts.size - 1) {
                minSeg = min(minSeg, hypot(verts[i + 1].x - verts[i].x, verts[i + 1].y - verts[i].y))
            }
            var e = 0f
            for (p in pts) {
                var best = Float.MAX_VALUE
                for (i in 0 until verts.size - 1) best = min(best, distSeg(p, verts[i], verts[i + 1]))
                e += best
            }
            e /= pts.size
            if (minSeg > len * 0.08f && e < len * 0.018f) {
                return Result(
                    FloatArray(verts.size) { verts[it].x }, FloatArray(verts.size) { verts[it].y }, false
                )
            }
        }
        return null
    }

    private fun fitArc(pts: List<P>): Result? {
        val n = pts.size
        var mx = 0.0
        var my = 0.0
        for (p in pts) {
            mx += p.x
            my += p.y
        }
        mx /= n
        my /= n
        var sxx = 0.0
        var sxy = 0.0
        var syy = 0.0
        var sx = 0.0
        var sy = 0.0
        var sxz = 0.0
        var syz = 0.0
        var sz = 0.0
        for (p in pts) {
            val x = p.x - mx
            val y = p.y - my
            val z = x * x + y * y
            sxx += x * x
            sxy += x * y
            syy += y * y
            sx += x
            sy += y
            sxz += x * z
            syz += y * z
            sz += z
        }
        val sol = solve3(
            doubleArrayOf(sxx, sxy, sx, sxy, syy, sy, sx, sy, n.toDouble()),
            doubleArrayOf(-sxz, -syz, -sz)
        ) ?: return null
        val ccx = -sol[0] / 2.0
        val ccy = -sol[1] / 2.0
        val r2 = ccx * ccx + ccy * ccy - sol[2]
        if (r2 <= 0.0) return null
        val r = sqrt(r2).toFloat()
        val cx = (ccx + mx).toFloat()
        val cy = (ccy + my).toFloat()
        if (r < 30f || r > 2500f) return null
        var err = 0f
        for (p in pts) err += abs(hypot(p.x - cx, p.y - cy) - r)
        err /= n
        if (err > r * 0.07f) return null
        var sweep = 0f
        var prev = atan2(pts[0].y - cy, pts[0].x - cx)
        for (i in 1 until n) {
            val a = atan2(pts[i].y - cy, pts[i].x - cx)
            var d = a - prev
            while (d > PI) d -= (2 * PI).toFloat()
            while (d < -PI) d += (2 * PI).toFloat()
            sweep += d
            prev = a
        }
        val deg = abs(sweep) * 180f / PI.toFloat()
        if (deg < 70f || deg > 340f) return null
        val dir = if (sweep > 0) 1f else -1f
        val first = pts[0]
        val last = pts[n - 1]
        val chord = hypot(last.x - first.x, last.y - first.y)
        var useCx = cx
        var useCy = cy
        var useR = r
        var useSweep = abs(sweep)
        var startAng = atan2(first.y - cy, first.x - cx)
        if (deg in 150f..210f && abs(chord - 2f * r) < 0.3f * 2f * r) {
            // Yarım daire: merkez, uç noktaları birleştiren doğrunun ortası
            useCx = (first.x + last.x) / 2f
            useCy = (first.y + last.y) / 2f
            useR = chord / 2f
            useSweep = PI.toFloat()
            startAng = atan2(first.y - useCy, first.x - useCx)
        }
        val steps = max(24, (useSweep * 18f).toInt())
        val xs = FloatArray(steps + 1)
        val ys = FloatArray(steps + 1)
        for (i in 0..steps) {
            val a = startAng + dir * useSweep * i / steps
            xs[i] = useCx + useR * cos(a)
            ys[i] = useCy + useR * sin(a)
        }
        return Result(xs, ys, false)
    }

    private fun solve3(a: DoubleArray, b: DoubleArray): DoubleArray? {
        val m = Array(3) { r -> DoubleArray(4) { c -> if (c < 3) a[r * 3 + c] else b[r] } }
        for (i in 0 until 3) {
            var piv = i
            for (r in i + 1 until 3) if (abs(m[r][i]) > abs(m[piv][i])) piv = r
            if (abs(m[piv][i]) < 1e-9) return null
            val t = m[i]
            m[i] = m[piv]
            m[piv] = t
            for (r in 0 until 3) {
                if (r == i) continue
                val f = m[r][i] / m[i][i]
                for (c in i until 4) m[r][c] -= f * m[i][c]
            }
        }
        return doubleArrayOf(m[0][3] / m[0][0], m[1][3] / m[1][1], m[2][3] / m[2][2])
    }

    // ---- Ortak yardımcılar ----

    private fun resample(xs: List<Float>, ys: List<Float>, count: Int): List<P> {
        val n = xs.size
        val cum = FloatArray(n)
        for (i in 1 until n) cum[i] = cum[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        val total = cum[n - 1]
        if (total <= 0f) return listOf(P(xs[0], ys[0]), P(xs[n - 1], ys[n - 1]))
        val out = ArrayList<P>(count)
        var j = 1
        for (k in 0 until count) {
            val target = total * k / (count - 1)
            while (j < n - 1 && cum[j] < target) j++
            val seg = cum[j] - cum[j - 1]
            val t = if (seg <= 0f) 0f else (target - cum[j - 1]) / seg
            out.add(P(xs[j - 1] + (xs[j] - xs[j - 1]) * t, ys[j - 1] + (ys[j] - ys[j - 1]) * t))
        }
        return out
    }

    private fun pathLength(pts: List<P>): Float {
        var l = 0f
        for (i in 1 until pts.size) l += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        return l
    }

    private fun maxDevFromChord(pts: List<P>): Float {
        val a = pts[0]
        val b = pts[pts.size - 1]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val l = hypot(dx, dy)
        if (l < 1e-3f) return Float.MAX_VALUE
        var m = 0f
        for (p in pts) m = max(m, abs((p.x - a.x) * dy - (p.y - a.y) * dx) / l)
        return m
    }

    private fun distSeg(p: P, a: P, b: P): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val l2 = dx * dx + dy * dy
        if (l2 == 0f) return hypot(p.x - a.x, p.y - a.y)
        val t = max(0f, min(1f, ((p.x - a.x) * dx + (p.y - a.y) * dy) / l2))
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    private fun rdp(pts: List<P>, from: Int, to: Int, eps: Float): List<Int> {
        if (to <= from + 1) return listOf(from, to)
        var maxD = 0f
        var idx = from
        for (i in from + 1 until to) {
            val d = distSeg(pts[i], pts[from], pts[to])
            if (d > maxD) {
                maxD = d
                idx = i
            }
        }
        if (maxD <= eps) return listOf(from, to)
        val l = rdp(pts, from, idx, eps)
        val r = rdp(pts, idx, to, eps)
        return l + r.drop(1)
    }

    private fun rdpOpen(pts: List<P>, eps: Float): List<P> = rdp(pts, 0, pts.size - 1, eps).map { pts[it] }
}
