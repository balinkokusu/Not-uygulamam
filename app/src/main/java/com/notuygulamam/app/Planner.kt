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
