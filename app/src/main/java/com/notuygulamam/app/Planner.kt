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
        Color.rgb(244, 160, 200), Color.rgb(245, 150, 150), Color.rgb(250, 185, 150),
        Color.rgb(252, 215, 140), Color.rgb(250, 240, 150), Color.rgb(200, 230, 150),
        Color.rgb(160, 225, 170), Color.rgb(150, 225, 210), Color.rgb(150, 210, 245),
        Color.rgb(160, 180, 245), Color.rgb(190, 165, 240), Color.rgb(225, 165, 235)
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
