package com.notuygulamam.app

import android.content.res.AssetManager
import android.graphics.Typeface
import java.io.File

/** Metin kutuları için uygulamayla birlikte gelen yazı tipleri (assets/fonts). Hepsi Türkçe harfleri içerir. */
object Fonts {
    val names = arrayOf(
        "Düz", "El yazısı", "Defter yazısı", "Kaligrafi", "Gösterişli",
        "Zarif serif", "Kitap", "Daktilo", "Dar başlık"
    )
    private val files = arrayOf(
        null, "caveat.ttf", "patrickhand.ttf", "dancingscript.ttf", "pacifico.ttf",
        "playfair.ttf", "lora.ttf", "courierprime.ttf", "oswald.ttf"
    )
    private var assets: AssetManager? = null
    private var customDir: File? = null

    /** Kullanıcının eklediği yazı tipi dosyaları (ad sırasına göre); indeksleri hazır yazı tiplerinden sonra gelir. */
    fun customFiles(): List<File> =
        (customDir?.listFiles { f -> f.isFile && (f.name.endsWith(".ttf", true) || f.name.endsWith(".otf", true)) }
            ?.sortedBy { it.name.lowercase() }) ?: emptyList()

    fun allNames(): List<String> = names.toList() + customFiles().map { it.nameWithoutExtension }

    /** Seçilen dosyayı yazı tipi olarak dener; açılıyorsa kopyalar. Başarıyı döner. */
    @Synchronized
    fun addFont(dir: File, displayName: String, bytes: ByteArray): Boolean {
        dir.mkdirs()
        val clean = displayName.replace(Regex("[^A-Za-z0-9ĞÜŞİÖÇğüşıöç _.-]"), "_")
        val nm = if (clean.endsWith(".ttf", true) || clean.endsWith(".otf", true)) clean else "$clean.ttf"
        val f = File(dir, nm)
        f.writeBytes(bytes)
        return try {
            Typeface.createFromFile(f)
            cache.clear()
            true
        } catch (e: Exception) {
            f.delete()
            false
        }
    }
    private val cache = HashMap<Int, Typeface>()

    @Synchronized
    fun init(a: AssetManager, dir: File? = null) {
        if (assets == null) assets = a
        if (dir != null) customDir = dir
    }

    /** style: Typeface.NORMAL, BOLD, ITALIC ya da BOLD_ITALIC. */
    @Synchronized
    fun get(idx: Int, style: Int): Typeface {
        val custom = customFiles()
        val i = idx.coerceIn(0, files.size + custom.size - 1)
        val key = i * 4 + style
        val hit = cache[key]
        if (hit != null) return hit
        var base: Typeface = Typeface.SANS_SERIF
        val f = if (i < files.size) files[i] else null
        val a = assets
        if (i >= files.size) {
            try { base = Typeface.createFromFile(custom[i - files.size]) } catch (e: Exception) {}
        } else if (f != null && a != null) {
            try {
                base = Typeface.createFromAsset(a, "fonts/$f")
            } catch (e: Exception) {
                // Dosya açılamazsa sistem yazı tipi kullanılır.
            }
        }
        val t = Typeface.create(base, style)
        cache[key] = t
        return t
    }
}
