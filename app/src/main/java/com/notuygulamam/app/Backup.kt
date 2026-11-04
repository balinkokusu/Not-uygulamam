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

/** Tüm kütüphaneyi (defterler, klasörler, kapaklar, PDF'ler) tek zip dosyasına yedekler; yeniden kurulumda geri yüklemek içindir. */
object LibraryBackup {
    private val ROOTS = arrayOf("library", "covers", "pdfs")

    fun write(filesDir: File, out: OutputStream) {
        ZipOutputStream(BufferedOutputStream(out)).use { z ->
            for (root in ROOTS) {
                val files = File(filesDir, root).listFiles() ?: continue
                for (f in files) {
                    if (!f.isFile) continue
                    val n = f.name
                    if (n.endsWith(".tmp") || n.endsWith(".onceki") || n.endsWith(".yeni")) continue
                    z.putNextEntry(ZipEntry("$root/$n"))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }

    /** Yedeği geri yükler. Mevcut kütüphane "library.onceki" klasörüne taşınır. Yedekte kütüphane dizini yoksa false döner. */
    fun restore(input: InputStream, filesDir: File): Boolean {
        val staging = File(filesDir, "restore_tmp")
        staging.deleteRecursively()
        staging.mkdirs()
        var hasIndex = false
        ZipInputStream(BufferedInputStream(input)).use { z ->
            var e: ZipEntry? = z.nextEntry
            while (e != null) {
                val parts = e.name.split("/")
                if (parts.size == 2 && parts[0] in ROOTS && parts[1].isNotEmpty() &&
                    !parts[1].contains("\\") && !parts[1].contains("..")
                ) {
                    val dir = File(staging, parts[0])
                    dir.mkdirs()
                    FileOutputStream(File(dir, parts[1])).use { o -> z.copyTo(o) }
                    if (parts[0] == "library" && parts[1] == "library.json") hasIndex = true
                }
                z.closeEntry()
                e = z.nextEntry
            }
        }
        if (!hasIndex) {
            staging.deleteRecursively()
            return false
        }
        val lib = File(filesDir, "library")
        val old = File(filesDir, "library.onceki")
        old.deleteRecursively()
        if (lib.exists()) lib.renameTo(old)
        File(staging, "library").renameTo(lib)
        for (root in arrayOf("covers", "pdfs")) {
            val src = File(staging, root)
            if (!src.exists()) continue
            val dst = File(filesDir, root)
            dst.mkdirs()
            for (f in src.listFiles() ?: emptyArray()) {
                val t = File(dst, f.name)
                if (root == "covers" || !t.exists()) {
                    t.delete()
                    f.renameTo(t)
                }
            }
        }
        staging.deleteRecursively()
        return true
    }
}
