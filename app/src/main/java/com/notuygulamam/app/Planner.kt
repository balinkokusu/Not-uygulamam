package com.notuygulamam.app

import android.graphics.Color

/** Hazır planlayıcı defteri: yıllık takvim, 12 aylık takvim ve her ayın yapılacaklar sayfası; kenarda ay ayraçları. */
object Planner {
    val MONTHS = arrayOf(
        "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )
    val SHORT = arrayOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")
    val DAYS = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

    private val colors = intArrayOf(
        Color.rgb(236, 196, 244), Color.rgb(246, 196, 226), Color.rgb(250, 186, 196),
        Color.rgb(252, 208, 184), Color.rgb(252, 232, 170), Color.rgb(232, 244, 180),
        Color.rgb(200, 236, 190), Color.rgb(186, 238, 222), Color.rgb(190, 226, 246),
        Color.rgb(196, 208, 246), Color.rgb(212, 198, 244), Color.rgb(226, 196, 240)
    )

    /** Yıllık takvimdeki ay başlık şeritlerinin renkleri (sıcak tonlardan serin tonlara). */
    val MONTH_COLORS = intArrayOf(
        Color.rgb(222, 120, 100), Color.rgb(226, 132, 110), Color.rgb(214, 144, 120),
        Color.rgb(224, 158, 90), Color.rgb(220, 170, 80), Color.rgb(196, 170, 90),
        Color.rgb(120, 150, 170), Color.rgb(104, 140, 168), Color.rgb(130, 150, 180),
        Color.rgb(176, 130, 150), Color.rgb(190, 120, 130), Color.rgb(206, 116, 112)
    )

    /**
     * Yıl ajandası: kapak, yıllık takvim; her ay için iki sayfalık aylık bakış ve o ayın tarihli haftalık sayfaları.
     * horizontal=false dikey (günler alt alta), true yatay (günler yan yana, saatli).
     */
    fun buildAgenda(year: Int, horizontal: Boolean): Notebook {
        val nb = Notebook()
        val cover = PageData(paper = Paper.COVER)
        cover.calYear = year
        nb.pages.add(cover)
        nb.tabs.add(Tab("Kapak", Color.rgb(240, 200, 190), cover, false, 2))
        val yearPage = PageData(paper = Paper.YEAR)
        yearPage.calYear = year
        nb.pages.add(yearPage)
        nb.tabs.add(Tab(year.toString(), Color.rgb(205, 205, 215), yearPage, false, 1))
        val cal = java.util.Calendar.getInstance()
        cal.clear()
        cal.set(year, 0, 1)
        // 1 Ocak'ı içeren haftanın pazartesisi
        cal.add(java.util.Calendar.DAY_OF_MONTH, -((cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7))
        for (m in 1..12) {
            val ml = PageData(paper = Paper.MONTH_L); ml.calYear = year; ml.calMonth = m
            val mr = PageData(paper = Paper.MONTH_R); mr.calYear = year; mr.calMonth = m
            nb.pages.add(ml); nb.pages.add(mr)
            nb.tabs.add(Tab(SHORT[m - 1], colors[m - 1], ml, false, 1))
            var count = 0
            while (true) {
                val wy = cal.get(java.util.Calendar.YEAR)
                val wm = cal.get(java.util.Calendar.MONTH) + 1
                // hafta, pazartesisinin ayına yazılır; yıl başındaki ilk hafta ocak ayına girer
                val belongs = if (wy < year) m == 1 else (wy == year && wm == m)
                if (!belongs) break
                val wp = PageData(paper = if (horizontal) Paper.WEEK_H else Paper.WEEK_V)
                wp.calYear = wy
                wp.calMonth = wm * 100 + cal.get(java.util.Calendar.DAY_OF_MONTH)
                nb.pages.add(wp)
                count++
                cal.add(java.util.Calendar.DAY_OF_MONTH, 7)
            }
            // çift sayfa görünümünde aylık bakış hep aynı ikilide kalsın diye sayfa sayısı çifte tamamlanır
            if (count % 2 == 1) nb.pages.add(PageData(paper = Paper.RULED))
        }
        return nb
    }

    fun build(year: Int): Notebook {
        val nb = Notebook()
        val cover = PageData(paper = Paper.COVER)
        cover.calYear = year
        nb.pages.add(cover)
        nb.tabs.add(Tab("Kapak", Color.rgb(240, 200, 190), cover, false, 2))
        val yearPage = PageData(paper = Paper.YEAR)
        yearPage.calYear = year
        nb.pages.add(yearPage)
        nb.tabs.add(Tab(year.toString(), Color.rgb(205, 205, 215), yearPage, false, 1))
        for (m in 1..12) {
            val cal = PageData(paper = Paper.MONTHLY)
            cal.calYear = year
            cal.calMonth = m
            nb.pages.add(cal)
            nb.pages.add(PageData(paper = Paper.TODO))
            nb.tabs.add(Tab(SHORT[m - 1], colors[m - 1], cal, false, 1))
        }
        return nb
    }
}
