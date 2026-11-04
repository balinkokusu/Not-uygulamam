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

    fun build(year: Int): Notebook {
        val nb = Notebook()
        val yearPage = PageData(paper = Paper.YEAR)
        yearPage.calYear = year
        nb.pages.add(yearPage)
        nb.tabs.add(Tab(year.toString(), Color.rgb(205, 205, 215), yearPage))
        for (m in 1..12) {
            val cal = PageData(paper = Paper.MONTHLY)
            cal.calYear = year
            cal.calMonth = m
            nb.pages.add(cal)
            nb.pages.add(PageData(paper = Paper.TODO))
            nb.tabs.add(Tab(SHORT[m - 1], colors[m - 1], cal))
        }
        return nb
    }
}
