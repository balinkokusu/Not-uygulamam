package com.notuygulamam.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.TextView
import android.widget.ScrollView
import java.io.File

/** Uygulama çökerse hatayı dosyaya yazar; bir sonraki açılışta gösterir. */
object Crash {
    private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val f = File(ctx.applicationContext.filesDir, "son_hata.txt")
        val ver = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "?" }
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                f.writeText("Sürüm " + ver + " · " + java.util.Date() + "\n" + sw.toString().take(6000))
            } catch (x: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    fun showIfAny(a: Activity) {
        val f = File(a.filesDir, "son_hata.txt")
        if (!f.exists()) return
        val txt = try { f.readText() } catch (e: Exception) { "" }
        f.delete()
        val tv = TextView(a)
        tv.text = txt
        tv.textSize = 11f
        tv.setTextIsSelectable(true)
        val pad = (12 * a.resources.displayMetrics.density).toInt()
        tv.setPadding(pad, pad, pad, pad)
        val sv = ScrollView(a)
        sv.addView(tv)
        AlertDialog.Builder(a)
            .setTitle("Uygulama son açılışta çöktü")
            .setMessage(null)
            .setView(sv)
            .setPositiveButton("Kopyala") { _, _ ->
                (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("hata", txt))
            }
            .setNegativeButton("Kapat", null)
            .show()
    }
}
