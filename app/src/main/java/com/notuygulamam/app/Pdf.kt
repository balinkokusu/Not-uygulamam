package com.notuygulamam.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.max

/** Uygulama klasöründeki tek bir PDF dosyasını açıp sayfalarını görüntüye çevirir. */
class PdfSource(private val file: File) {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    @Synchronized
    private fun open(): PdfRenderer {
        var r = renderer
        if (r == null) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd = fd
            r = PdfRenderer(fd)
            renderer = r
        }
        return r
    }

    @Synchronized
    fun pageCount(): Int = open().pageCount

    @Synchronized
    fun pageSize(index: Int): Pair<Int, Int> {
        val page = open().openPage(index)
        try {
            return Pair(page.width, page.height)
        } finally {
            page.close()
        }
    }

    @Synchronized
    fun render(index: Int, targetW: Int): Bitmap {
        val page = open().openPage(index)
        try {
            val h = max(1, (targetW.toFloat() * page.height / page.width).toInt())
            val bmp = Bitmap.createBitmap(targetW, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } finally {
            page.close()
        }
    }
}

object PdfStore {
    private val sources = HashMap<String, PdfSource>()

    /** PDF sayfalarını arka planda çizmek için tek iş parçacıklı sıra. */
    val executor = Executors.newSingleThreadExecutor()

    fun source(dir: File, name: String): PdfSource = synchronized(this) {
        sources.getOrPut(name) { PdfSource(File(dir, name)) }
    }

    /** Seçilen PDF'i uygulama klasörüne kopyalar ve dosya adını döndürür. */
    fun importPdf(context: Context, uri: Uri, dir: File): String {
        dir.mkdirs()
        val name = "pdf_" + System.currentTimeMillis() + ".pdf"
        val out = File(dir, name)
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Dosya açılamadı")
        input.use { ins ->
            FileOutputStream(out).use { o -> ins.copyTo(o) }
        }
        return name
    }
}
