package com.notuygulamam.app

import android.content.res.AssetManager
import android.graphics.Typeface

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
    private val cache = HashMap<Int, Typeface>()

    @Synchronized
    fun init(a: AssetManager) {
        if (assets == null) assets = a
    }

    /** style: Typeface.NORMAL, BOLD, ITALIC ya da BOLD_ITALIC. */
    @Synchronized
    fun get(idx: Int, style: Int): Typeface {
        val i = idx.coerceIn(0, files.size - 1)
        val key = i * 4 + style
        val hit = cache[key]
        if (hit != null) return hit
        var base: Typeface = Typeface.SANS_SERIF
        val f = files[i]
        val a = assets
        if (f != null && a != null) {
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
