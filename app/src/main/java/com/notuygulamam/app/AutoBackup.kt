package com.notuygulamam.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kütüphanenin tamamını birkaç saatte bir (her gün için tek dosya) uygulamanın dışına zip olarak kaydeder; uygulama silinse de kopya kalır.
 * Varsayılan yer: Belgeler/NotUygulamam. Kullanıcı bir klasör seçtiyse (bulut eşitlemeli klasör olabilir) oraya yazılır.
 * Son 7 günün yedeği tutulur.
 */
object AutoBackup {
    private const val PREF = "oto_yedek"
    private const val KEEP = 7
    private const val PREFIX = "NotUygulamam-yedek-"
    @Volatile private var running = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun folder(ctx: Context): Uri? = prefs(ctx).getString("klasor", null)?.let { Uri.parse(it) }

    fun setFolder(ctx: Context, tree: Uri?) {
        prefs(ctx).edit().putString("klasor", tree?.toString()).apply()
    }

    fun lastTime(ctx: Context) = prefs(ctx).getLong("son", 0L)
    fun lastPlace(ctx: Context) = prefs(ctx).getString("yer", "") ?: ""

    private fun hasContent(ctx: Context): Boolean {
        val lib = File(ctx.filesDir, "library")
        return (lib.listFiles() ?: emptyArray()).any { it.name.startsWith("nb_") && it.length() > 0 }
    }

    /** Son yedekten bu yana 3 saat geçtiyse arka planda yedek alır. */
    fun maybeRun(ctx: Context) {
        if (System.currentTimeMillis() - lastTime(ctx) < 3L * 3600 * 1000) return
        runNow(ctx.applicationContext, null)
    }

    /** Hemen yedek alır; sonuç (başarı, açıklama) ana iş parçacığında bildirilir. */
    fun runNow(ctx: Context, done: ((Boolean, String) -> Unit)?) {
        if (running) { done?.invoke(false, "Yedek zaten alınıyor"); return }
        // yeni kurulmuş, boş bir uygulama eski iyi yedeklerin yerine boş yedek yazmasın
        if (!hasContent(ctx)) { done?.invoke(false, "Kütüphanede henüz defter yok"); return }
        running = true
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            var ok = false
            var msg: String
            try {
                val name = PREFIX + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".zip"
                val tree = folder(ctx)
                msg = if (tree != null) writeToTree(ctx, tree, name) else writeToDocuments(ctx, name)
                prefs(ctx).edit().putLong("son", System.currentTimeMillis()).putString("yer", msg).apply()
                ok = true
            } catch (e: Exception) {
                msg = "Otomatik yedek alınamadı: " + (e.message ?: e.javaClass.simpleName)
            }
            running = false
            val m = msg
            val o = ok
            main.post { done?.invoke(o, m) }
        }.start()
    }

    private fun writeToTree(ctx: Context, tree: Uri, name: String): String {
        val cr = ctx.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val existing = ArrayList<Pair<String, String>>() // (ad, belge kimliği)
        cr.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                if (n.startsWith(PREFIX) && n.endsWith(".zip")) existing.add(Pair(n, c.getString(1)))
            }
        }
        // önce yeni dosya yazılır, sonra aynı günün eskisi silinir: yazım yarıda kalırsa eski yedek kaybolmaz
        val tmpName = name.removeSuffix(".zip") + "-yaziliyor.zip"
        val uri = DocumentsContract.createDocument(cr, parent, "application/zip", tmpName) ?: throw java.io.IOException("Dosya oluşturulamadı")
        (cr.openOutputStream(uri) ?: throw java.io.IOException("Dosya açılamadı")).use { LibraryBackup.write(ctx.filesDir, it) }
        for ((n, id) in existing) if (n == name) DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id))
        DocumentsContract.renameDocument(cr, uri, name)
        val olds = existing.filter { it.first != name }.sortedByDescending { it.first }
        for ((_, id) in olds.drop(KEEP - 1)) try { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id)) } catch (_: Exception) {}
        return "seçtiğiniz klasöre ($name)"
    }

    private fun writeToDocuments(ctx: Context, name: String): String {
        if (Build.VERSION.SDK_INT < 29) {
            val dir = File(ctx.getExternalFilesDir(null), "yedek")
            dir.mkdirs()
            File(dir, name).outputStream().use { LibraryBackup.write(ctx.filesDir, it) }
            return "uygulama klasörüne ($name)"
        }
        val cr = ctx.contentResolver
        val coll = MediaStore.Files.getContentUri("external")
        val rel = "Documents/NotUygulamam/"
        val mine = ArrayList<Pair<String, Uri>>()
        cr.query(coll, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            MediaStore.MediaColumns.RELATIVE_PATH + "=?", arrayOf(rel), null)?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(1) ?: continue
                if (n.startsWith(PREFIX) && n.endsWith(".zip")) mine.add(Pair(n, android.content.ContentUris.withAppendedId(coll, c.getLong(0))))
            }
        }
        val v = ContentValues()
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name.removeSuffix(".zip") + "-yaziliyor.zip")
        v.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
        v.put(MediaStore.MediaColumns.IS_PENDING, 1)
        val uri = cr.insert(coll, v) ?: throw java.io.IOException("Belgeler klasörüne yazılamadı")
        try {
            (cr.openOutputStream(uri) ?: throw java.io.IOException("Dosya açılamadı")).use { LibraryBackup.write(ctx.filesDir, it) }
        } catch (e: Exception) {
            cr.delete(uri, null, null)
            throw e
        }
        for ((n, u) in mine) if (n == name) try { cr.delete(u, null, null) } catch (_: Exception) {}
        val fin = ContentValues()
        fin.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        fin.put(MediaStore.MediaColumns.IS_PENDING, 0)
        cr.update(uri, fin, null, null)
        val olds = mine.filter { it.first != name }.sortedByDescending { it.first }
        for ((_, u) in olds.drop(KEEP - 1)) try { cr.delete(u, null, null) } catch (_: Exception) {}
        return "Belgeler/NotUygulamam klasörüne ($name)"
    }
}
