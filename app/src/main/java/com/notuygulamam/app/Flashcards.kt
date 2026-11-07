package com.notuygulamam.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Flaş kart destesi: kartlar iki yüzlüdür, aralıklı tekrar için her kartın kutu numarası ve vade günü tutulur. */
class Card(var front: String, var back: String, var box: Int = 0, var due: Long = 0L) {
    /** Sayfadan alınmış görsel yüzler (kartlar klasöründeki dosya adı). */
    var fImg: String? = null
    var bImg: String? = null
}
class Deck(val id: String, var name: String, val cards: ArrayList<Card> = ArrayList())

object FlashStore {
    private val BOX_DAYS = intArrayOf(0, 1, 2, 4, 8, 16, 32)

    fun today(): Long = System.currentTimeMillis() / 86400000L

    private fun file(ctx: Context) = File(ctx.filesDir, "kartlar.json")

    fun load(ctx: Context): ArrayList<Deck> {
        val out = ArrayList<Deck>()
        val f = file(ctx)
        if (!f.exists()) return out
        try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val d = Deck(o.getString("id"), o.getString("name"))
                val cs = o.getJSONArray("cards")
                for (k in 0 until cs.length()) {
                    val c = cs.getJSONObject(k)
                    val card = Card(c.getString("f"), c.getString("b"), c.optInt("x", 0), c.optLong("d", 0L))
                    card.fImg = c.optString("fi", "").ifEmpty { null }
                    card.bImg = c.optString("bi", "").ifEmpty { null }
                    d.cards.add(card)
                }
                out.add(d)
            }
        } catch (e: Exception) {
            // bozuk dosya yedeklenir, boş liste döner
            try { f.copyTo(File(ctx.filesDir, "kartlar.bozuk.json"), true) } catch (_: Exception) {}
        }
        return out
    }

    fun save(ctx: Context, decks: List<Deck>) {
        val arr = JSONArray()
        for (d in decks) {
            val o = JSONObject()
            o.put("id", d.id)
            o.put("name", d.name)
            val cs = JSONArray()
            for (c in d.cards) {
                val co = JSONObject()
                co.put("f", c.front); co.put("b", c.back); co.put("x", c.box); co.put("d", c.due)
                c.fImg?.let { co.put("fi", it) }
                c.bImg?.let { co.put("bi", it) }
                cs.put(co)
            }
            o.put("cards", cs)
            arr.put(o)
        }
        val tmp = File(ctx.filesDir, "kartlar.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file(ctx))
    }

    private fun imgDir(ctx: Context) = File(ctx.filesDir, "kartlar").also { it.mkdirs() }

    fun imageFile(ctx: Context, name: String) = File(imgDir(ctx), name)

    fun saveImage(ctx: Context, bmp: android.graphics.Bitmap, tag: String): String {
        val name = "k" + System.currentTimeMillis() + tag + ".png"
        java.io.FileOutputStream(File(imgDir(ctx), name)).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    fun dropImages(ctx: Context, c: Card) {
        c.fImg?.let { File(imgDir(ctx), it).delete() }
        c.bImg?.let { File(imgDir(ctx), it).delete() }
    }

    fun dueCount(d: Deck): Int = d.cards.count { it.due <= today() }

    /** Cevap sonrası kutu ve vade günü: 0 bilemedim (kutu 0, bugün), 1 zor (aynı kutu), 2 bildim (bir üst kutu). */
    fun answer(c: Card, level: Int) {
        c.box = when (level) { 0 -> 0; 1 -> c.box; else -> (c.box + 1).coerceAtMost(BOX_DAYS.size - 1) }
        c.due = today() + (if (level == 0) 0 else BOX_DAYS[c.box].coerceAtLeast(1))
    }
}

class FlashcardsActivity : Activity() {
    private lateinit var decks: ArrayList<Deck>
    private lateinit var root: LinearLayout
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()

    // çalışma oturumu
    private var queue = ArrayList<Card>()
    private var current: Card? = null
    private var revealed = false
    private var studying: Deck? = null
    private var done = 0
    private var total = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        decks = FlashStore.load(this)
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Look.libraryBg)
        setContentView(root)
        showDecks()
    }

    override fun onBackPressed() {
        if (studying != null) { studying = null; showDecks() } else super.onBackPressed()
    }

    private fun save() = FlashStore.save(this, decks)

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD)
        return v
    }

    private fun pill(t: String, bg: Int, fg: Int, click: () -> Unit): TextView {
        val b = text(t, 16f, fg)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(18f), dp(12f), dp(18f), dp(12f))
        val g = GradientDrawable()
        g.cornerRadius = dp(16f).toFloat()
        g.setColor(bg)
        b.background = g
        b.setOnClickListener { click() }
        return b
    }

    private fun input(hint: String, multi: Boolean = true): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = InputType.TYPE_CLASS_TEXT or (if (multi) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0) or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        if (multi) e.minLines = 2
        return e
    }

    // ---- Deste listesi ----

    private fun showDecks() {
        studying = null
        root.removeAllViews()
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(20f), dp(18f), dp(20f), dp(8f))
        val back = text("‹ Geri", 18f, Look.accent)
        back.setPadding(0, dp(6f), dp(16f), dp(6f))
        back.setOnClickListener { finish() }
        head.addView(back)
        val title = text("Flaş kartlar", 30f, Look.ink)
        title.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
        head.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(pill("＋ Yeni deste", Look.accentSoft, Look.accent) { newDeck() })
        root.addView(head)
        val sv = ScrollView(this)
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(20f), dp(8f), dp(20f), dp(20f))
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        if (decks.isEmpty()) {
            list.addView(text("Henüz deste yok. \"Yeni deste\" ile başlayın; kartları tek tek ya da toplu ekleyebilirsiniz. Çalışırken bildiğiniz kartlar daha seyrek, bilemedikleriniz daha sık karşınıza çıkar.", 15f, Look.inkSoft))
        }
        for (deck in decks) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(dp(18f), dp(16f), dp(18f), dp(16f))
            val g = GradientDrawable()
            g.cornerRadius = dp(16f).toFloat()
            g.setColor(Color.WHITE)
            g.setStroke(dp(1f), Look.hairline)
            row.background = g
            row.addView(text(deck.name, 20f, Look.ink, true))
            val due = FlashStore.dueCount(deck)
            row.addView(text(deck.cards.size.toString() + " kart · bugün sırada: " + due, 14f, Look.inkSoft))
            row.setOnClickListener { deckMenu(deck) }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(10f)
            list.addView(row, lp)
        }
    }

    private fun newDeck() {
        val e = input("Deste adı", false)
        AlertDialog.Builder(this).setTitle("Yeni deste").setView(pad(e))
            .setPositiveButton("Oluştur") { _, _ ->
                val n = e.text.toString().trim().ifEmpty { "Adsız deste" }
                decks.add(Deck(System.currentTimeMillis().toString(), n))
                save()
                showDecks()
            }.setNegativeButton("Vazgeç", null).show()
    }

    private fun pad(v: View): View {
        val box = LinearLayout(this)
        box.setPadding(dp(22f), dp(10f), dp(22f), dp(4f))
        box.addView(v, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        return box
    }

    private fun deckMenu(deck: Deck) {
        val due = FlashStore.dueCount(deck)
        AlertDialog.Builder(this).setTitle(deck.name)
            .setItems(arrayOf(
                "Çalış (bugün sırada: $due)", "Tüm kartları çalış (${deck.cards.size})", "Kart ekle", "Toplu ekle (her satır: ön ; arka)",
                "Kartları düzenle / sil", "Desteyi yeniden adlandır", "Desteyi sil"
            )) { _, w ->
                when (w) {
                    0 -> startStudy(deck, false)
                    1 -> startStudy(deck, true)
                    2 -> addCard(deck)
                    3 -> bulkAdd(deck)
                    4 -> cardList(deck)
                    5 -> rename(deck)
                    6 -> AlertDialog.Builder(this).setTitle("Bu deste silinsin mi?").setMessage("İçindeki " + deck.cards.size + " kart da silinir.")
                        .setPositiveButton("Sil") { _, _ -> deck.cards.forEach { FlashStore.dropImages(this, it) }; decks.remove(deck); save(); showDecks() }
                        .setNegativeButton("Vazgeç", null).show()
                }
            }.setNegativeButton("Kapat", null).show()
    }

    private fun rename(deck: Deck) {
        val e = input("Deste adı", false)
        e.setText(deck.name)
        AlertDialog.Builder(this).setTitle("Yeniden adlandır").setView(pad(e))
            .setPositiveButton("Kaydet") { _, _ -> deck.name = e.text.toString().trim().ifEmpty { deck.name }; save(); showDecks() }
            .setNegativeButton("Vazgeç", null).show()
    }

    private fun addCard(deck: Deck) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val f = input("Ön yüz (soru)")
        val b = input("Arka yüz (cevap)")
        box.addView(f); box.addView(b)
        AlertDialog.Builder(this).setTitle("Kart ekle").setView(pad(box))
            .setPositiveButton("Kaydet") { _, _ -> put(deck, f, b); showDecks() }
            .setNeutralButton("Kaydet ve yeni ekle") { _, _ -> put(deck, f, b); addCard(deck) }
            .setNegativeButton("Vazgeç", null).show()
    }

    private fun put(deck: Deck, f: EditText, b: EditText) {
        val ft = f.text.toString().trim()
        val bt = b.text.toString().trim()
        if (ft.isEmpty() && bt.isEmpty()) return
        deck.cards.add(Card(ft, bt))
        save()
    }

    private fun bulkAdd(deck: Deck) {
        val e = input("Her satıra bir kart: ön ; arka")
        e.minLines = 8
        AlertDialog.Builder(this).setTitle("Toplu ekle").setMessage("Her satır bir kart olur. Ön ve arka yüzü noktalı virgülle ayırın.").setView(pad(e))
            .setPositiveButton("Ekle") { _, _ ->
                var n = 0
                for (line in e.text.toString().split('\n')) {
                    val i = line.indexOf(';')
                    if (i <= 0) continue
                    val a = line.substring(0, i).trim()
                    val b = line.substring(i + 1).trim()
                    if (a.isEmpty() || b.isEmpty()) continue
                    deck.cards.add(Card(a, b)); n++
                }
                save()
                Toast.makeText(this, "$n kart eklendi", Toast.LENGTH_SHORT).show()
                showDecks()
            }.setNegativeButton("Vazgeç", null).show()
    }

    private fun cardList(deck: Deck) {
        if (deck.cards.isEmpty()) { Toast.makeText(this, "Bu destede kart yok", Toast.LENGTH_SHORT).show(); return }
        val labels = deck.cards.map {
            (if (it.fImg != null) "[görsel] " else "") + it.front.replace('\n', ' ').take(50) + "  →  " +
                (if (it.bImg != null) "[görsel] " else "") + it.back.replace('\n', ' ').take(30)
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Kartlar (düzenlemek için dokunun)").setItems(labels) { _, i ->
            val c = deck.cards[i]
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            val f = input("Ön yüz"); f.setText(c.front)
            val b = input("Arka yüz"); b.setText(c.back)
            box.addView(f); box.addView(b)
            AlertDialog.Builder(this).setTitle("Kartı düzenle").setView(pad(box))
                .setPositiveButton("Kaydet") { _, _ -> c.front = f.text.toString().trim(); c.back = b.text.toString().trim(); save(); showDecks() }
                .setNeutralButton("Sil") { _, _ -> FlashStore.dropImages(this, deck.cards[i]); deck.cards.removeAt(i); save(); showDecks() }
                .setNegativeButton("Vazgeç", null).show()
        }.setNegativeButton("Kapat", null).show()
    }

    // ---- Çalışma ----

    private fun startStudy(deck: Deck, all: Boolean) {
        val pool = if (all) deck.cards.toList() else deck.cards.filter { it.due <= FlashStore.today() }
        if (pool.isEmpty()) { Toast.makeText(this, "Bugün için sırada kart yok", Toast.LENGTH_SHORT).show(); return }
        queue = ArrayList(pool.shuffled())
        total = queue.size
        done = 0
        studying = deck
        next()
    }

    private fun next() {
        current = if (queue.isEmpty()) null else queue.removeAt(0)
        revealed = false
        showStudy()
    }

    private fun showStudy() {
        root.removeAllViews()
        val deck = studying ?: return
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(20f), dp(18f), dp(20f), dp(8f))
        val back = text("‹ Bitir", 18f, Look.accent)
        back.setOnClickListener { studying = null; showDecks() }
        head.addView(back)
        val prog = text(deck.name + " · " + done + " / " + total, 15f, Look.inkSoft)
        prog.gravity = Gravity.CENTER
        head.addView(prog, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(head)
        val c = current
        if (c == null) {
            val msg = text("Bu tur bitti. $total kart çalıştınız.", 20f, Look.ink)
            msg.gravity = Gravity.CENTER
            root.addView(msg, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            return
        }
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER
        card.setPadding(dp(30f), dp(30f), dp(30f), dp(30f))
        val g = GradientDrawable()
        g.cornerRadius = dp(24f).toFloat()
        g.setColor(Color.WHITE)
        g.setStroke(dp(1f), Look.hairline)
        card.background = g
        card.elevation = dp(4f).toFloat()
        val img = if (revealed) c.bImg else c.fImg
        val txt = if (revealed) c.back else c.front
        if (img != null) {
            val bmp = android.graphics.BitmapFactory.decodeFile(FlashStore.imageFile(this, img).absolutePath)
            if (bmp != null) {
                val iv = android.widget.ImageView(this)
                iv.setImageBitmap(bmp)
                iv.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                card.addView(iv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            }
        }
        if (txt.isNotEmpty() || img == null) {
            val face = text(txt, if (img != null) 20f else 30f, Look.ink)
            face.gravity = Gravity.CENTER
            card.addView(face)
        }
        val hint = text(if (revealed) "Cevap" else "Cevabı görmek için kartın üstüne dokunun", 13f, Look.inkSoft)
        hint.gravity = Gravity.CENTER
        hint.setPadding(0, dp(18f), 0, 0)
        card.addView(hint)
        card.setOnClickListener { revealed = !revealed; showStudy() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        lp.setMargins(dp(40f), dp(10f), dp(40f), dp(10f))
        root.addView(card, lp)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.setPadding(dp(20f), dp(8f), dp(20f), dp(24f))
        fun ans(label: String, level: Int, bg: Int, fg: Int) {
            val b = pill(label, bg, fg) {
                FlashStore.answer(c, level)
                if (level == 0) queue.add(c) else done++
                save()
                next()
            }
            val l = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            l.setMargins(dp(6f), 0, dp(6f), 0)
            row.addView(b, l)
        }
        if (revealed) {
            ans("Bilemedim", 0, Color.rgb(246, 214, 212), Color.rgb(150, 60, 60))
            ans("Zor", 1, Color.rgb(250, 238, 200), Color.rgb(140, 100, 20))
            ans("Bildim", 2, Color.rgb(214, 238, 218), Color.rgb(50, 110, 70))
        }
        root.addView(row)
    }
}
