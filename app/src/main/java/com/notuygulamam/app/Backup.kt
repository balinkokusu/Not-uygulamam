package com.notuygulamam.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Defteri (ve içindeki PDF dosyalarını) tek bir zip dosyası olarak yedekler ve geri yükler. */
object Backup {
    private const val NOTEBOOK_ENTRY = "defter.bin"
    private const val PDF_PREFIX = "pdfs/"

    fun write(notebookFile: File, pdfDir: File, out: OutputStream) {
        ZipOutputStream(BufferedOutputStream(out)).use { z ->
            if (notebookFile.exists()) {
                z.putNextEntry(ZipEntry(NOTEBOOK_ENTRY))
                notebookFile.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
            val files = pdfDir.listFiles()
            if (files != null) {
                for (f in files) {
                    if (!f.isFile) continue
                    z.putNextEntry(ZipEntry(PDF_PREFIX + f.name))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }

    /** Yedekten defteri geri yükler; defter dosyası bulunursa true döner. Eski defter ".onceki" adıyla saklanır. */
    fun restore(input: InputStream, notebookFile: File, pdfDir: File): Boolean {
        pdfDir.mkdirs()
        var found = false
        val tmp = File(notebookFile.parentFile, notebookFile.name + ".yeni")
        ZipInputStream(BufferedInputStream(input)).use { z ->
            var e: ZipEntry? = z.nextEntry
            while (e != null) {
                val name = e.name
                if (name == NOTEBOOK_ENTRY) {
                    FileOutputStream(tmp).use { o -> z.copyTo(o) }
                    found = true
                } else if (name.startsWith(PDF_PREFIX)) {
                    val fn = name.substring(PDF_PREFIX.length)
                    val safe = fn.isNotEmpty() && !fn.contains("/") && !fn.contains("\\") && !fn.contains("..")
                    val target = File(pdfDir, fn)
                    if (safe && !target.exists()) {
                        FileOutputStream(target).use { o -> z.copyTo(o) }
                    }
                }
                z.closeEntry()
                e = z.nextEntry
            }
        }
        if (!found) {
            tmp.delete()
            return false
        }
        if (notebookFile.exists()) {
            val old = File(notebookFile.parentFile, notebookFile.name + ".onceki")
            old.delete()
            notebookFile.renameTo(old)
        }
        return tmp.renameTo(notebookFile)
    }
}
