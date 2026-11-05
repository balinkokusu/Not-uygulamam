package com.notuygulamam.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Defterlerden bağımsız küçük hatırlatıcı notlar; kitaplık ekranında panoda görünür. */
object Reminders {
    class Note(val id: Long, var text: String, var color: Int)

    val COLORS = intArrayOf(
        Color.rgb(255, 238, 140), Color.rgb(255, 205, 210), Color.rgb(200, 230, 201), Color.rgb(187, 222, 251)
    )
    private val NAMES = arrayOf("Sarı", "Pembe", "Yeşil", "Mavi")

    fun load(ctx: Context): MutableList<Note> {
        val out = ArrayList<Note>()
        try {
            val s = ctx.getSharedPreferences("hatirlatma", Context.MODE_PRIVATE).getString("liste", "[]") ?: "[]"
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Note(o.getLong("id"), o.getString("text"), o.getInt("color")))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun save(ctx: Context, list: List<Note>) {
        val a = JSONArray()
        for (n in list) a.put(JSONObject().put("id", n.id).put("text", n.text).put("color", n.color))
        ctx.getSharedPreferences("hatirlatma", Context.MODE_PRIVATE).edit().putString("liste", a.toString()).apply()
    }

    fun add(ctx: Context, text: String, color: Int) {
        val l = load(ctx)
        l.add(Note(System.currentTimeMillis(), text, color))
        save(ctx, l)
    }

    /** Not ekler ya da düzenler; metni boş bırakıp kaydetmek notu siler. */
    fun edit(ctx: Context, note: Note?, onDone: () -> Unit) {
        val d = ctx.resources.displayMetrics.density
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0)
        val et = EditText(ctx)
        et.hint = "Hatırlatıcı metni"
        et.minLines = 3
        et.setText(note?.text ?: "")
        box.addView(et)
        val grp = RadioGroup(ctx)
        grp.orientation = LinearLayout.HORIZONTAL
        for (i in NAMES.indices) {
            val rb = RadioButton(ctx)
            rb.id = 500 + i
            rb.text = NAMES[i]
            grp.addView(rb)
        }
        val cur = if (note == null) 0 else COLORS.indexOf(note.color).coerceAtLeast(0)
        grp.check(500 + cur)
        box.addView(grp)
        val b = AlertDialog.Builder(ctx).setTitle(if (note == null) "Yeni hatırlatıcı" else "Hatırlatıcıyı düzenle").setView(box)
            .setPositiveButton("Kaydet") { _, _ ->
                val col = COLORS[(grp.checkedRadioButtonId - 500).coerceIn(0, 3)]
                val txt = et.text.toString().trim()
                val l = load(ctx)
                if (note == null) {
                    if (txt.isNotEmpty()) l.add(Note(System.currentTimeMillis(), txt, col))
                } else {
                    val n = l.firstOrNull { it.id == note.id }
                    if (n != null) {
                        if (txt.isEmpty()) l.remove(n) else { n.text = txt; n.color = col }
                    }
                }
                save(ctx, l)
                onDone()
            }
            .setNegativeButton("Vazgeç", null)
        if (note != null) {
            b.setNeutralButton("Sil") { _, _ ->
                val l = load(ctx)
                l.removeAll { it.id == note.id }
                save(ctx, l)
                onDone()
            }
        }
        b.show()
    }
}

/** Yan yana kaydırılan küçük yapışkan not kartları. */
class ReminderBoard(private val ctx: Context) : HorizontalScrollView(ctx) {
    private val row = LinearLayout(ctx)
    private val d = ctx.resources.displayMetrics.density

    init {
        isHorizontalScrollBarEnabled = false
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding((20 * d).toInt(), (6 * d).toInt(), (20 * d).toInt(), (10 * d).toInt())
        addView(row)
        refresh()
    }

    fun refresh() {
        row.removeAllViews()
        val notes = Reminders.load(ctx)
        for ((i, n) in notes.withIndex()) {
            val t = TextView(ctx)
            t.text = n.text
            t.textSize = 15f
            t.setTextColor(Color.rgb(60, 50, 40))
            t.setTypeface(Typeface.SERIF, Typeface.NORMAL)
            t.maxLines = 5
            t.ellipsize = android.text.TextUtils.TruncateAt.END
            t.setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            val g = GradientDrawable()
            g.setColor(n.color)
            g.cornerRadius = 4 * d
            t.background = g
            t.elevation = 4 * d
            t.rotation = if (i % 2 == 0) -1.5f else 1.5f
            t.setOnClickListener { Reminders.edit(ctx, n) { refresh() } }
            val lp = LinearLayout.LayoutParams((150 * d).toInt(), (116 * d).toInt())
            lp.setMargins(0, (4 * d).toInt(), (12 * d).toInt(), (4 * d).toInt())
            row.addView(t, lp)
        }
        val add = TextView(ctx)
        add.text = if (notes.isEmpty()) "＋ Hatırlatıcı ekle\n(defterlerden bağımsız küçük not)" else "＋"
        add.textSize = if (notes.isEmpty()) 14f else 26f
        add.gravity = Gravity.CENTER
        add.setTextColor(Look.accent)
        val g = GradientDrawable()
        g.setStroke((1.5f * d).toInt(), Look.accent)
        g.cornerRadius = 4 * d
        g.setColor(Look.accentSoft)
        add.background = g
        add.setOnClickListener { Reminders.edit(ctx, null) { refresh() } }
        val w = if (notes.isEmpty()) (230 * d).toInt() else (70 * d).toInt()
        val lp = LinearLayout.LayoutParams(w, (116 * d).toInt())
        lp.setMargins(0, (4 * d).toInt(), (12 * d).toInt(), (4 * d).toInt())
        row.addView(add, lp)
    }
}
