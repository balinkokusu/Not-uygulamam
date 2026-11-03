package com.notuygulamam.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Kütüphanedeki bir defter ya da klasör. */
class LibraryItem(val id: String, val isFolder: Boolean, var title: String, var parentId: String) {
    var color: Int = Color.rgb(70, 100, 180)
    var coverStyle: Int = 0

    /** Galeriden alınan kapak fotoğrafının dosya adı (uzantısız); yoksa şablon kullanılır. */
    var coverImage: String? = null
    var icon: Int = 0
    var modified: Long = 0L

    fun copy(): LibraryItem {
        val c = LibraryItem(id, isFolder, title, parentId)
        c.color = color
        c.coverStyle = coverStyle
        c.coverImage = coverImage
        c.icon = icon
        c.modified = modified
        return c
    }

    fun copyFrom(o: LibraryItem) {
        title = o.title
        color = o.color
        coverStyle = o.coverStyle
        coverImage = o.coverImage
        icon = o.icon
    }
}

/** Defterlerin ve klasörlerin listesini bir JSON dosyasında tutar; her defter ayrı bir dosyadır. */
class LibraryStore(val dir: File) {
    val items = ArrayList<LibraryItem>()

    fun notebookFile(id: String): File = File(dir, "nb_$id.bin")

    fun newId(): String =
        java.lang.Long.toString(System.currentTimeMillis(), 36) + kotlin.random.Random.nextInt(1000)

    fun find(id: String): LibraryItem? = items.firstOrNull { it.id == id }

    fun children(parentId: String): List<LibraryItem> {
        val list = items.filter { it.parentId == parentId }
        val folders = list.filter { it.isFolder }.sortedBy { it.title.lowercase() }
        val books = list.filter { !it.isFolder }.sortedByDescending { it.modified }
        return folders + books
    }

    fun descendants(id: String): List<LibraryItem> {
        val out = ArrayList<LibraryItem>()
        for (c in items.filter { it.parentId == id }) {
            out.add(c)
            if (c.isFolder) out.addAll(descendants(c.id))
        }
        return out
    }

    fun touch(id: String) {
        val it = find(id) ?: return
        it.modified = System.currentTimeMillis()
        save()
    }

    fun load() {
        items.clear()
        val f = File(dir, "library.json")
        if (!f.exists()) return
        try {
            val arr = JSONArray(f.readText())
            for (k in 0 until arr.length()) {
                val o = arr.getJSONObject(k)
                val it = LibraryItem(o.getString("id"), o.getBoolean("folder"), o.getString("title"), o.getString("parent"))
                it.color = o.getInt("color")
                it.coverStyle = o.getInt("style")
                val img = o.getString("image")
                it.coverImage = if (img.isEmpty()) null else img
                it.icon = o.getInt("icon")
                it.modified = o.getLong("modified")
                items.add(it)
            }
        } catch (e: Exception) {
            // Bozuk liste dosyası varsa boş kütüphane ile devam edilir.
        }
    }

    fun save() {
        dir.mkdirs()
        val arr = JSONArray()
        for (i in items) {
            val o = JSONObject()
            o.put("id", i.id)
            o.put("folder", i.isFolder)
            o.put("title", i.title)
            o.put("parent", i.parentId)
            o.put("color", i.color)
            o.put("style", i.coverStyle)
            o.put("image", i.coverImage ?: "")
            o.put("icon", i.icon)
            o.put("modified", i.modified)
            arr.put(o)
        }
        val tmp = File(dir, "library.json.tmp")
        tmp.writeText(arr.toString())
        val target = File(dir, "library.json")
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }
}

/** Kapak fotoğraflarını içe aktarır, küçük önizlemeleri bellekte tutar. */
object CoverCache {
    private val cache = LruCache<String, Bitmap>(24)

    fun thumb(dir: File, base: String): Bitmap? {
        val hit = cache.get(base)
        if (hit != null) return hit
        val f = File(dir, base + "_t.jpg")
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.path) ?: return null
        cache.put(base, bmp)
        return bmp
    }

    fun delete(dir: File, base: String) {
        File(dir, base + ".jpg").delete()
        File(dir, base + "_t.jpg").delete()
        cache.remove(base)
    }

    private fun rotationOf(context: Context, uri: Uri): Int {
        return try {
            val o = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            when (o) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /** Seçilen görseli küçültüp kapak olarak kaydeder (tam boy ve küçük önizleme). */
    fun importImage(context: Context, uri: Uri, dir: File, base: String): Boolean {
        dir.mkdirs()
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val side = max(bounds.outWidth, bounds.outHeight)
        if (side <= 0) return false
        var sample = 1
        while (side / sample > 1800) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        var bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return false
        val rot = rotationOf(context, uri)
        if (rot != 0) {
            val m = Matrix()
            m.postRotate(rot.toFloat())
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val full = scaleTo(bmp, 1200)
        FileOutputStream(File(dir, base + ".jpg")).use { full.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        val small = scaleTo(bmp, 480)
        FileOutputStream(File(dir, base + "_t.jpg")).use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return true
    }

    private fun scaleTo(src: Bitmap, maxSide: Int): Bitmap {
        val s = max(src.width, src.height)
        if (s <= maxSide) return src
        val f = maxSide.toFloat() / s
        return Bitmap.createScaledBitmap(src, (src.width * f).toInt().coerceAtLeast(1), (src.height * f).toInt().coerceAtLeast(1), true)
    }
}

/** Kapak şablonları; hepsi kodla çizilir, kullanıcı şablon ve rengi seçer. */
object CoverArt {
    const val STYLE_COUNT = 10
    val names = arrayOf(
        "Düz", "Geçişli", "Noktalı", "Çizgili", "Dalga", "Kareli", "Çiçek", "Gece", "Gün batımı", "Kraft"
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun lighten(c: Int, f: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = hsv[1] * (1f - f * 0.6f)
        hsv[2] = min(1f, hsv[2] + (1f - hsv[2]) * f)
        return Color.HSVToColor(hsv)
    }

    fun darken(c: Int, f: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] = hsv[2] * (1f - f)
        return Color.HSVToColor(hsv)
    }

    fun draw(c: Canvas, r: RectF, style: Int, color: Int) {
        val radius = r.width() * 0.05f
        c.save()
        path.reset()
        path.addRoundRect(r, radius, radius, Path.Direction.CW)
        c.clipPath(path)
        val light = lighten(color, 0.5f)
        val dark = darken(color, 0.3f)
        val w = r.width()
        val h = r.height()
        paint.style = Paint.Style.FILL
        paint.shader = null
        when (style) {
            1 -> {
                paint.shader = LinearGradient(r.left, r.top, r.right, r.bottom, light, dark, Shader.TileMode.CLAMP)
                c.drawRect(r, paint)
                paint.shader = null
            }
            7 -> {
                paint.color = darken(color, 0.7f)
                c.drawRect(r, paint)
            }
            8 -> {
                paint.shader = LinearGradient(0f, r.top, 0f, r.bottom, lighten(color, 0.75f), color, Shader.TileMode.CLAMP)
                c.drawRect(r, paint)
                paint.shader = null
            }
            9 -> {
                paint.color = lighten(color, 0.55f)
                c.drawRect(r, paint)
            }
            else -> {
                paint.color = color
                c.drawRect(r, paint)
            }
        }
        when (style) {
            2 -> {
                paint.color = Color.argb(190, Color.red(light), Color.green(light), Color.blue(light))
                val step = w / 8f
                var y = r.top + step * 0.8f
                var row = 0
                while (y < r.bottom) {
                    var x = r.left + step * (if (row % 2 == 0) 0.6f else 1.1f)
                    while (x < r.right) {
                        c.drawCircle(x, y, step * 0.16f, paint)
                        x += step
                    }
                    y += step * 0.9f
                    row++
                }
            }
            3 -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = w * 0.035f
                paint.color = Color.argb(150, Color.red(light), Color.green(light), Color.blue(light))
                val step = w / 6f
                var k = -(h / step).toInt() - 1
                while (k < (w / step).toInt() + 2) {
                    c.drawLine(r.left + k * step, r.top, r.left + k * step + h * 0.6f, r.bottom, paint)
                    k++
                }
                paint.style = Paint.Style.FILL
            }
            4 -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = w * 0.03f
                paint.color = Color.argb(170, Color.red(light), Color.green(light), Color.blue(light))
                val step = h / 9f
                var row = 1
                while (row < 9) {
                    path.reset()
                    val y0 = r.top + row * step
                    path.moveTo(r.left, y0)
                    var x = r.left
                    while (x <= r.right) {
                        path.lineTo(x, y0 + sin((x - r.left) / w * 2f * PI.toFloat() * 3f) * step * 0.28f)
                        x += w / 40f
                    }
                    c.drawPath(path, paint)
                    row++
                }
                paint.style = Paint.Style.FILL
            }
            5 -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = w * 0.012f
                paint.color = Color.argb(120, Color.red(light), Color.green(light), Color.blue(light))
                val step = w / 7f
                var x = r.left + step
                while (x < r.right) {
                    c.drawLine(x, r.top, x, r.bottom, paint)
                    x += step
                }
                var y = r.top + step
                while (y < r.bottom) {
                    c.drawLine(r.left, y, r.right, y, paint)
                    y += step
                }
                paint.style = Paint.Style.FILL
            }
            6 -> {
                val step = w / 3.2f
                var y = r.top + step * 0.55f
                var row = 0
                while (y < r.bottom + step) {
                    var x = r.left + step * (if (row % 2 == 0) 0.5f else 1.1f)
                    while (x < r.right + step) {
                        paint.color = Color.argb(210, Color.red(light), Color.green(light), Color.blue(light))
                        for (k in 0 until 5) {
                            val a = (k * 72f - 90f) * PI.toFloat() / 180f
                            c.drawCircle(x + cos(a) * step * 0.17f, y + sin(a) * step * 0.17f, step * 0.14f, paint)
                        }
                        paint.color = Color.rgb(255, 214, 90)
                        c.drawCircle(x, y, step * 0.09f, paint)
                        x += step
                    }
                    y += step * 0.8f
                    row++
                }
            }
            7 -> {
                val rnd = java.util.Random(7)
                paint.color = Color.WHITE
                for (k in 0 until 34) {
                    val x = r.left + rnd.nextFloat() * w
                    val y = r.top + rnd.nextFloat() * h
                    c.drawCircle(x, y, w * (0.006f + rnd.nextFloat() * 0.014f), paint)
                }
                paint.color = Color.argb(230, 255, 240, 200)
                c.drawCircle(r.left + w * 0.72f, r.top + h * 0.2f, w * 0.11f, paint)
                paint.color = darken(color, 0.7f)
                c.drawCircle(r.left + w * 0.76f, r.top + h * 0.18f, w * 0.1f, paint)
            }
            8 -> {
                paint.color = Color.argb(235, 255, 236, 170)
                c.drawCircle(r.left + w * 0.5f, r.top + h * 0.64f, w * 0.26f, paint)
            }
            9 -> {
                val rnd = java.util.Random(3)
                paint.color = Color.argb(70, 90, 60, 30)
                for (k in 0 until 140) {
                    c.drawCircle(r.left + rnd.nextFloat() * w, r.top + rnd.nextFloat() * h, w * 0.006f, paint)
                }
            }
        }
        // Defter sırtı
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(60, 0, 0, 0)
        c.drawRect(r.left, r.top, r.left + w * 0.07f, r.bottom, paint)
        paint.color = Color.argb(70, 255, 255, 255)
        c.drawRect(r.left + w * 0.07f, r.top, r.left + w * 0.078f, r.bottom, paint)
        c.restore()
    }
}

/** Klasör simgesi ve içine konabilen küçük simgeler. */
object FolderArt {
    const val ICON_COUNT = 6
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    fun draw(c: Canvas, r: RectF, color: Int, icon: Int) {
        val w = r.width()
        val h = r.height()
        val tabH = h * 0.14f
        val rad = w * 0.08f
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = CoverArt.darken(color, 0.25f)
        rect.set(r.left, r.top, r.left + w * 0.42f, r.top + tabH * 2.2f)
        c.drawRoundRect(rect, rad, rad, paint)
        rect.set(r.left, r.top + tabH, r.right, r.bottom)
        paint.color = CoverArt.darken(color, 0.12f)
        c.drawRoundRect(rect, rad, rad, paint)
        rect.set(r.left, r.top + tabH * 1.9f, r.right, r.bottom)
        paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, CoverArt.lighten(color, 0.25f), color, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, rad, rad, paint)
        paint.shader = null
        if (icon > 0) {
            drawIcon(c, r.centerX(), rect.centerY(), min(w, h) * 0.3f, icon, Color.argb(235, 255, 255, 255))
        }
    }

    fun drawIcon(c: Canvas, cx: Float, cy: Float, size: Float, icon: Int, color: Int) {
        paint.shader = null
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.strokeWidth = size * 0.14f
        paint.strokeCap = Paint.Cap.ROUND
        when (icon) {
            0 -> {
                paint.style = Paint.Style.STROKE
                c.drawCircle(cx, cy, size * 0.6f, paint)
                c.drawLine(cx - size * 0.42f, cy + size * 0.42f, cx + size * 0.42f, cy - size * 0.42f, paint)
            }
            1 -> {
                path.reset()
                path.moveTo(cx, cy + size * 0.55f)
                path.cubicTo(cx - size * 1.15f, cy - size * 0.1f, cx - size * 0.5f, cy - size * 0.9f, cx, cy - size * 0.3f)
                path.cubicTo(cx + size * 0.5f, cy - size * 0.9f, cx + size * 1.15f, cy - size * 0.1f, cx, cy + size * 0.55f)
                path.close()
                c.drawPath(path, paint)
            }
            2 -> {
                path.reset()
                for (k in 0 until 10) {
                    val a = (k * 36f - 90f) * PI.toFloat() / 180f
                    val rr = if (k % 2 == 0) size * 0.7f else size * 0.3f
                    val x = cx + cos(a) * rr
                    val y = cy + sin(a) * rr
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                c.drawPath(path, paint)
            }
            3 -> {
                c.drawRect(cx - size * 0.62f, cy - size * 0.45f, cx - size * 0.04f, cy + size * 0.45f, paint)
                c.drawRect(cx + size * 0.04f, cy - size * 0.45f, cx + size * 0.62f, cy + size * 0.45f, paint)
            }
            4 -> {
                paint.style = Paint.Style.STROKE
                c.drawCircle(cx, cy, size * 0.6f, paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(cx - size * 0.22f, cy - size * 0.15f, size * 0.07f, paint)
                c.drawCircle(cx + size * 0.22f, cy - size * 0.15f, size * 0.07f, paint)
                paint.style = Paint.Style.STROKE
                rect.set(cx - size * 0.32f, cy - size * 0.1f, cx + size * 0.32f, cy + size * 0.38f)
                c.drawArc(rect, 20f, 140f, false, paint)
            }
            5 -> {
                c.drawCircle(cx, cy, size * 0.34f, paint)
                for (k in 0 until 8) {
                    val a = k * 45f * PI.toFloat() / 180f
                    c.drawLine(
                        cx + cos(a) * size * 0.52f, cy + sin(a) * size * 0.52f,
                        cx + cos(a) * size * 0.74f, cy + sin(a) * size * 0.74f, paint
                    )
                }
            }
        }
        paint.style = Paint.Style.FILL
    }
}
