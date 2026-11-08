package com.notuygulamam.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Araç çubuğundaki simgelerin ne işe yaradığını birkaç dilde anlatan yardım penceresi. */
object Help {
    val langs = arrayOf("Türkçe", "English", "Deutsch", "Français", "Español")

    private class Entry(val icon: Icon, val t: Array<String>)

    private val entries = listOf(
        Entry(Icon.HOME, arrayOf("Kütüphane: defterlerin listesine döner", "Library: back to the list of notebooks", "Bibliothek: zurück zur Notizbuchliste", "Bibliothèque : retour à la liste des carnets", "Biblioteca: vuelve a la lista de cuadernos")),
        Entry(Icon.SAVE, arrayOf("Kaydet: defteri hemen kaydeder (kendiliğinden de kaydedilir)", "Save: saves the notebook now (it also saves automatically)", "Speichern: sichert das Notizbuch sofort (auch automatisch)", "Enregistrer : sauvegarde le carnet tout de suite (aussi automatique)", "Guardar: guarda el cuaderno ahora (también se guarda solo)")),
        Entry(Icon.PAGES, arrayOf("Sayfalar: sayfa listesi, sıralama, silme", "Pages: page list, reordering, deleting", "Seiten: Seitenliste, Reihenfolge, Löschen", "Pages : liste, ordre, suppression", "Páginas: lista, orden y borrado")),
        Entry(Icon.ADD, arrayOf("Yeni sayfa: şablon seçerek sayfa ekler", "New page: adds a page from a template", "Neue Seite: fügt eine Seite aus einer Vorlage hinzu", "Nouvelle page : ajoute une page depuis un modèle", "Página nueva: añade una página con una plantilla")),
        Entry(Icon.PEN, arrayOf("Kalem: yazı ve çizim (kalem türü, kalınlık ve renk seçilir)", "Pen: writing and drawing (choose type, thickness, colour)", "Stift: schreiben und zeichnen (Art, Stärke, Farbe)", "Stylo : écrire et dessiner (type, épaisseur, couleur)", "Lápiz: escribir y dibujar (tipo, grosor y color)")),
        Entry(Icon.HIGHLIGHTER, arrayOf("Fosforlu kalem: yarı saydam vurgu", "Highlighter: translucent emphasis", "Textmarker: halbtransparente Hervorhebung", "Surligneur : mise en valeur translucide", "Resaltador: énfasis translúcido")),
        Entry(Icon.ERASER, arrayOf("Silgi: çizgiyi ya da dokunulan yeri siler", "Eraser: removes a stroke or the touched area", "Radierer: löscht einen Strich oder die berührte Stelle", "Gomme : efface un trait ou la zone touchée", "Borrador: borra un trazo o la zona tocada")),
        Entry(Icon.PICKER, arrayOf("Damlalık: sayfadaki bir rengi seçer", "Colour picker: picks a colour from the page", "Pipette: wählt eine Farbe von der Seite", "Pipette : prélève une couleur de la page", "Cuentagotas: toma un color de la página")),
        Entry(Icon.TEXT, arrayOf("Metin kutusu: klavyeyle yazı ekler", "Text box: adds typed text", "Textfeld: fügt getippten Text hinzu", "Zone de texte : ajoute du texte tapé", "Cuadro de texto: añade texto escrito")),
        Entry(Icon.SELECT, arrayOf("Nesne seç: resim, yazı, çıkartma ve tabloyu taşır, boyutlandırır, siler; başka araçta nesneler kilitlidir", "Select objects: move, resize or delete pictures, text, stickers and tables; other tools keep them locked", "Objekt wählen: Bilder, Text, Sticker und Tabellen verschieben, skalieren, löschen; bei anderen Werkzeugen sind sie gesperrt", "Sélection d'objets : déplacer, redimensionner ou supprimer images, texte, autocollants et tableaux ; verrouillés avec les autres outils", "Seleccionar objetos: mover, redimensionar o borrar imágenes, texto, pegatinas y tablas; bloqueados con otras herramientas")),
        Entry(Icon.LASSO, arrayOf("Lasso: çevreleyerek seçer, taşır, siler, çoğaltır", "Lasso: select by circling, then move, delete or duplicate", "Lasso: durch Einkreisen auswählen, verschieben, löschen, kopieren", "Lasso : sélectionner en entourant, déplacer, supprimer, dupliquer", "Lazo: selecciona rodeando; mover, borrar, duplicar")),
        Entry(Icon.LASER, arrayOf("Lazer işaretçi: iz bırakmadan gösterir", "Laser pointer: points without leaving a mark", "Laserpointer: zeigt, ohne Spuren zu hinterlassen", "Pointeur laser : montre sans laisser de trace", "Puntero láser: señala sin dejar rastro")),
        Entry(Icon.IMAGE, arrayOf("Resim: galeriden resim ekler", "Image: adds a picture from the gallery", "Bild: fügt ein Bild aus der Galerie hinzu", "Image : ajoute une image de la galerie", "Imagen: añade una imagen de la galería")),
        Entry(Icon.MIC, arrayOf("Ses kaydı: kaydı başlatır; çizgiler kayıtla birlikte oynatılır", "Audio: records sound; strokes replay with the recording", "Audio: nimmt Ton auf; Striche werden mit abgespielt", "Audio : enregistre le son ; les traits se rejouent avec lui", "Audio: graba sonido; los trazos se reproducen con él")),
        Entry(Icon.STICKER, arrayOf("Çıkartma: çıkartmalar, tablo, yapışkan not, washi bant, hatırlatıcılar", "Stickers: stickers, table, sticky note, washi tape, reminders", "Sticker: Sticker, Tabelle, Haftnotiz, Washi-Tape, Erinnerungen", "Autocollants : autocollants, tableau, note, washi, rappels", "Pegatinas: pegatinas, tabla, nota adhesiva, washi, recordatorios")),
        Entry(Icon.TAPE, arrayOf("Bant kalemi: çizgi çekerek washi bant koyar; bantın üstüne dokununca açılır / kapanır", "Tape pen: draw a line to place washi tape; tap the tape to lift or cover", "Bandstift: Linie ziehen für Washi-Tape; Antippen hebt es an oder deckt zu", "Ruban : tracer une ligne pour poser du washi ; toucher pour soulever ou couvrir", "Cinta: traza una línea para poner washi; toca la cinta para levantarla o cubrir")),
        Entry(Icon.RULER, arrayOf("Cetvel: aç / kapat", "Ruler: on / off", "Lineal: an / aus", "Règle : activer / désactiver", "Regla: activar / desactivar")),
        Entry(Icon.CALC, arrayOf("Hesap makinesi", "Calculator", "Taschenrechner", "Calculatrice", "Calculadora")),
        Entry(Icon.TIMER, arrayOf("Pomodoro zamanlayıcı", "Pomodoro timer", "Pomodoro-Timer", "Minuteur Pomodoro", "Temporizador Pomodoro")),
        Entry(Icon.PAPER, arrayOf("Sayfa şablonu: çizgili, kareli, noktalı, takvim…", "Page template: ruled, grid, dotted, calendar…", "Seitenvorlage: liniert, kariert, gepunktet, Kalender…", "Modèle de page : lignes, grille, points, calendrier…", "Plantilla: rayas, cuadrícula, puntos, calendario…")),
        Entry(Icon.TAB, arrayOf("Ayraçlar: kenardaki sekmeler", "Tabs: the edge bookmarks", "Reiter: Lesezeichen am Rand", "Onglets : marque-pages sur le bord", "Pestañas: marcadores del borde")),
        Entry(Icon.FINGER, arrayOf("Parmakla çizim: açıkken parmak da çizer", "Finger drawing: when on, the finger draws too", "Fingerzeichnen: wenn an, zeichnet auch der Finger", "Dessin au doigt : si activé, le doigt dessine aussi", "Dibujo con el dedo: si está activo, el dedo también dibuja")),
        Entry(Icon.SPREAD, arrayOf("Görünüm: tek / çift sayfa, cilt, sayfa boyutu", "View: single / double page, binding, page size", "Ansicht: Einzel- / Doppelseite, Bindung, Seitengröße", "Affichage : page simple / double, reliure, taille", "Vista: página simple / doble, encuadernación, tamaño")),
        Entry(Icon.PREV, arrayOf("Önceki sayfa", "Previous page", "Vorherige Seite", "Page précédente", "Página anterior")),
        Entry(Icon.NEXT, arrayOf("Sonraki sayfa", "Next page", "Nächste Seite", "Page suivante", "Página siguiente")),
        Entry(Icon.UNDO, arrayOf("Geri al", "Undo", "Rückgängig", "Annuler", "Deshacer")),
        Entry(Icon.REDO, arrayOf("İleri al", "Redo", "Wiederholen", "Rétablir", "Rehacer")),
        Entry(Icon.MORE, arrayOf("Diğer işlemler: yedek, dışa aktarma, yardım…", "More: backup, export, help…", "Mehr: Sicherung, Export, Hilfe…", "Plus : sauvegarde, export, aide…", "Más: copia, exportar, ayuda…"))
    )

    private val titles = arrayOf("Simgeler ve ne işe yaradıkları", "Icons and what they do", "Symbole und ihre Funktion", "Icônes et leur rôle", "Iconos y para qué sirven")
    private val closeLabel = arrayOf("Kapat", "Close", "Schließen", "Fermer", "Cerrar")

    fun show(ctx: Context, dp: (Float) -> Int, prefs: android.content.SharedPreferences) {
        var lang = prefs.getInt("yardim_dil", 0).coerceIn(0, langs.size - 1)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16f), dp(12f), dp(16f), dp(4f))
        val chips = LinearLayout(ctx)
        chips.orientation = LinearLayout.HORIZONTAL
        val hs = android.widget.HorizontalScrollView(ctx)
        hs.addView(chips)
        root.addView(hs)
        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(ctx)
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(380f)))
        lateinit var dlg: AlertDialog
        fun fill() {
            chips.removeAllViews()
            for (i in langs.indices) {
                val t = TextView(ctx)
                t.text = langs[i]
                t.textSize = 14f
                t.setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
                t.setTextColor(if (i == lang) Color.WHITE else Look.ink)
                val g = android.graphics.drawable.GradientDrawable()
                g.cornerRadius = dp(16f).toFloat()
                g.setColor(if (i == lang) Look.accent else Color.TRANSPARENT)
                g.setStroke(dp(1f), Look.accent)
                t.background = g
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, dp(6f), dp(8f))
                t.layoutParams = lp
                t.setOnClickListener {
                    lang = i
                    prefs.edit().putInt("yardim_dil", i).apply()
                    fill()
                    dlg.setTitle(titles[lang])
                    dlg.getButton(AlertDialog.BUTTON_POSITIVE)?.text = closeLabel[lang]
                }
                chips.addView(t)
            }
            list.removeAllViews()
            for (e in entries) {
                val row = LinearLayout(ctx)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, dp(4f), 0, dp(4f))
                row.addView(IconButton(ctx, e.icon, "", true), LinearLayout.LayoutParams(dp(44f), dp(44f)))
                val tv = TextView(ctx)
                tv.text = e.t[lang]
                tv.textSize = 15f
                tv.setTextColor(Look.ink)
                tv.setPadding(dp(10f), 0, 0, 0)
                row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                list.addView(row)
            }
        }
        fill()
        dlg = AlertDialog.Builder(ctx).setTitle(titles[lang]).setView(root).setPositiveButton(closeLabel[lang], null).create()
        dlg.show()
    }
}
