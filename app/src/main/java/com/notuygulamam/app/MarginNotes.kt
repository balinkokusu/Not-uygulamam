package com.notuygulamam.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Defterin kendi kenar notları: sayfaların solundaki ve sağındaki boşlukta duran küçük hatırlatma kartları. */
object MarginNotes {
    class Note(val id: Long, var text: String, var color: Int, var side: Int)

    private fun key(nb: String) = "n_$nb"

    fun load(ctx: Context, nb: String): MutableList<Note> {
        val out = ArrayList<Note>()
        try {
            val s = ctx.getSharedPreferences("kenar", Context.MODE_PRIVATE).getString(key(nb), "[]") ?: "[]"
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Note(o.getLong("id"), o.getString("text"), o.getInt("color"), o.optInt("side", 0)))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun save(ctx: Context, nb: String, list: List<Note>) {
        val a = JSONArray()
        for (n in list) a.put(JSONObject().put("id", n.id).put("text", n.text).put("color", n.color).put("side", n.side))
        ctx.getSharedPreferences("kenar", Context.MODE_PRIVATE).edit().putString(key(nb), a.toString()).apply()
    }

    /** Not ekler ya da düzenler; metni boş bırakıp kaydetmek notu siler. */
    fun edit(ctx: Context, nb: String, note: Note?, defaultSide: Int, onDone: () -> Unit) {
        val d = ctx.resources.displayMetrics.density
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0)
        val et = EditText(ctx)
        et.hint = "Örneğin: ilacımı iç"
        et.minLines = 3
        et.setText(note?.text ?: "")
        box.addView(et)
        val colors = Reminders.COLORS
        val names = arrayOf("Sarı", "Pembe", "Yeşil", "Mavi")
        val grp = RadioGroup(ctx)
        grp.orientation = LinearLayout.HORIZONTAL
        for (i in names.indices) {
            val rb = RadioButton(ctx)
            rb.id = 700 + i
            rb.text = names[i]
            grp.addView(rb)
        }
        grp.check(700 + (if (note == null) 0 else colors.indexOf(note.color).coerceAtLeast(0)))
        box.addView(grp)
        val sg = RadioGroup(ctx)
        sg.orientation = LinearLayout.HORIZONTAL
        val sl = RadioButton(ctx); sl.id = 800; sl.text = "Sayfaların solunda"
        val sr = RadioButton(ctx); sr.id = 801; sr.text = "Sayfaların sağında"
        sg.addView(sl); sg.addView(sr)
        sg.check(800 + (note?.side ?: defaultSide).coerceIn(0, 1))
        box.addView(sg)
        val b = AlertDialog.Builder(ctx).setTitle(if (note == null) "Yeni kenar notu" else "Kenar notunu düzenle").setView(box)
            .setPositiveButton("Kaydet") { _, _ ->
                val col = colors[(grp.checkedRadioButtonId - 700).coerceIn(0, 3)]
                val side = if (sg.checkedRadioButtonId == 801) 1 else 0
                val txt = et.text.toString().trim()
                val l = load(ctx, nb)
                if (note == null) {
                    if (txt.isNotEmpty()) l.add(Note(System.currentTimeMillis(), txt, col, side))
                } else {
                    val n = l.firstOrNull { it.id == note.id }
                    if (n != null) {
                        if (txt.isEmpty()) l.remove(n) else { n.text = txt; n.color = col; n.side = side }
                    }
                }
                save(ctx, nb, l)
                onDone()
            }
            .setNegativeButton("Vazgeç", null)
        if (note != null) {
            b.setNeutralButton("Sil") { _, _ ->
                val l = load(ctx, nb)
                l.removeAll { it.id == note.id }
                save(ctx, nb, l)
                onDone()
            }
        }
        b.show()
    }
}

/** Sayfaların bir yanındaki dikey kart sütunu. */
class MarginColumn(private val ctx: Context, private val nb: String, private val side: Int, private val onChange: () -> Unit) : ScrollView(ctx) {
    private val col = LinearLayout(ctx)
    private val d = ctx.resources.displayMetrics.density

    init {
        isVerticalScrollBarEnabled = false
        col.orientation = LinearLayout.VERTICAL
        col.setPadding((4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt())
        addView(col)
    }

    fun refresh(): Int {
        col.removeAllViews()
        val mine = MarginNotes.load(ctx, nb).filter { it.side == side }
        for ((i, n) in mine.withIndex()) {
            val t = TextView(ctx)
            t.text = n.text
            t.textSize = 11f
            t.setTextColor(Color.rgb(60, 50, 40))
            t.setTypeface(Typeface.SERIF, Typeface.NORMAL)
            t.maxLines = 8
            t.ellipsize = android.text.TextUtils.TruncateAt.END
            t.setPadding((7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt())
            val g = GradientDrawable()
            g.setColor(n.color)
            g.cornerRadius = 3 * d
            t.background = g
            t.elevation = 3 * d
            t.rotation = if (i % 2 == 0) -1f else 1f
            t.setOnClickListener { MarginNotes.edit(ctx, nb, n, side) { onChange() } }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, (3 * d).toInt(), 0, (5 * d).toInt())
            col.addView(t, lp)
        }
        if (mine.isNotEmpty()) {
            val add = TextView(ctx)
            add.text = "＋"
            add.textSize = 20f
            add.gravity = Gravity.CENTER
            add.setTextColor(Look.accent)
            add.setOnClickListener { MarginNotes.edit(ctx, nb, null, side) { onChange() } }
            col.addView(add, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (36 * d).toInt()))
        }
        visibility = if (mine.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        return mine.size
    }
}
