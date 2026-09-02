package com.jooshin.diary.util

import android.content.Context
import androidx.core.content.ContextCompat
import com.jooshin.diary.R

/** 설정에서 고를 수 있는 디자인(팔레트). 앱과 위젯이 함께 바뀐다. */
enum class AppTheme(val key: String, val label: String, val styleRes: Int) {
    GREEN("green", "딥그린 (원버전)", R.style.Theme_EnJJoy_Green),
    BLUE("blue", "스카이블루", R.style.Theme_EnJJoy_Blue),
    PINK("pink", "연핑크", R.style.Theme_EnJJoy_Pink),
    MONO("mono", "블랙 + 그레이", R.style.Theme_EnJJoy_Mono),
    RED("red", "레드 + 주황", R.style.Theme_EnJJoy_Red),
    NAVY("navy", "군청", R.style.Theme_EnJJoy_Navy),
    LIGHT_GREEN("light_green", "연그린", R.style.Theme_EnJJoy_LightGreen),
    YELLOW("yellow", "노랑", R.style.Theme_EnJJoy_Yellow);

    companion object {
        fun of(key: String?): AppTheme = values().firstOrNull { it.key == key } ?: GREEN
    }
}

/**
 * 선택된 팔레트의 실제 색상값.
 *
 * 앱 화면은 테마 속성(?attr/...)으로 자동 적용되지만,
 * 위젯(RemoteViews)과 직접 그리는 달력 뷰는 테마를 쓸 수 없어서 여기서 색을 가져온다.
 */
class Palette(private val ctx: Context, val theme: AppTheme) {

    private class Ids(
        val toolbar: Int, val accent: Int, val bg: Int, val surface: Int, val section: Int,
        val stroke: Int, val textPrimary: Int, val textSecondary: Int, val textMuted: Int,
        val divider: Int, val onSelected: Int, val calNormal: Int, val calOutside: Int,
        val calLunar: Int, val calGrid: Int, val todayRing: Int,
        val widgetBg: Int, val widgetSection: Int, val todayCell: Int
    )

    private val ids: Ids = when (theme) {
        AppTheme.GREEN -> Ids(
            R.color.pg_toolbar, R.color.pg_accent, R.color.pg_bg, R.color.pg_surface, R.color.pg_section,
            R.color.pg_stroke, R.color.pg_text_primary, R.color.pg_text_secondary, R.color.pg_text_muted,
            R.color.pg_divider, R.color.pg_on_selected, R.color.pg_cal_normal, R.color.pg_cal_outside,
            R.color.pg_cal_lunar, R.color.pg_cal_grid, R.color.pg_today_ring,
            R.drawable.widget_bg_pg, R.drawable.widget_section_bg_pg, R.drawable.bg_widget_today_cell_pg
        )
        AppTheme.BLUE -> Ids(
            R.color.pb_toolbar, R.color.pb_accent, R.color.pb_bg, R.color.pb_surface, R.color.pb_section,
            R.color.pb_stroke, R.color.pb_text_primary, R.color.pb_text_secondary, R.color.pb_text_muted,
            R.color.pb_divider, R.color.pb_on_selected, R.color.pb_cal_normal, R.color.pb_cal_outside,
            R.color.pb_cal_lunar, R.color.pb_cal_grid, R.color.pb_today_ring,
            R.drawable.widget_bg_pb, R.drawable.widget_section_bg_pb, R.drawable.bg_widget_today_cell_pb
        )
        AppTheme.PINK -> Ids(
            R.color.pp_toolbar, R.color.pp_accent, R.color.pp_bg, R.color.pp_surface, R.color.pp_section,
            R.color.pp_stroke, R.color.pp_text_primary, R.color.pp_text_secondary, R.color.pp_text_muted,
            R.color.pp_divider, R.color.pp_on_selected, R.color.pp_cal_normal, R.color.pp_cal_outside,
            R.color.pp_cal_lunar, R.color.pp_cal_grid, R.color.pp_today_ring,
            R.drawable.widget_bg_pp, R.drawable.widget_section_bg_pp, R.drawable.bg_widget_today_cell_pp
        )
        AppTheme.MONO -> Ids(
            R.color.pm_toolbar, R.color.pm_accent, R.color.pm_bg, R.color.pm_surface, R.color.pm_section,
            R.color.pm_stroke, R.color.pm_text_primary, R.color.pm_text_secondary, R.color.pm_text_muted,
            R.color.pm_divider, R.color.pm_on_selected, R.color.pm_cal_normal, R.color.pm_cal_outside,
            R.color.pm_cal_lunar, R.color.pm_cal_grid, R.color.pm_today_ring,
            R.drawable.widget_bg_pm, R.drawable.widget_section_bg_pm, R.drawable.bg_widget_today_cell_pm
        )
        AppTheme.RED -> Ids(
            R.color.pr_toolbar, R.color.pr_accent, R.color.pr_bg, R.color.pr_surface, R.color.pr_section,
            R.color.pr_stroke, R.color.pr_text_primary, R.color.pr_text_secondary, R.color.pr_text_muted,
            R.color.pr_divider, R.color.pr_on_selected, R.color.pr_cal_normal, R.color.pr_cal_outside,
            R.color.pr_cal_lunar, R.color.pr_cal_grid, R.color.pr_today_ring,
            R.drawable.widget_bg_pr, R.drawable.widget_section_bg_pr, R.drawable.bg_widget_today_cell_pr
        )
        AppTheme.NAVY -> Ids(
            R.color.pn_toolbar, R.color.pn_accent, R.color.pn_bg, R.color.pn_surface, R.color.pn_section,
            R.color.pn_stroke, R.color.pn_text_primary, R.color.pn_text_secondary, R.color.pn_text_muted,
            R.color.pn_divider, R.color.pn_on_selected, R.color.pn_cal_normal, R.color.pn_cal_outside,
            R.color.pn_cal_lunar, R.color.pn_cal_grid, R.color.pn_today_ring,
            R.drawable.widget_bg_pn, R.drawable.widget_section_bg_pn, R.drawable.bg_widget_today_cell_pn
        )
        AppTheme.LIGHT_GREEN -> Ids(
            R.color.pl_toolbar, R.color.pl_accent, R.color.pl_bg, R.color.pl_surface, R.color.pl_section,
            R.color.pl_stroke, R.color.pl_text_primary, R.color.pl_text_secondary, R.color.pl_text_muted,
            R.color.pl_divider, R.color.pl_on_selected, R.color.pl_cal_normal, R.color.pl_cal_outside,
            R.color.pl_cal_lunar, R.color.pl_cal_grid, R.color.pl_today_ring,
            R.drawable.widget_bg_pl, R.drawable.widget_section_bg_pl, R.drawable.bg_widget_today_cell_pl
        )
        AppTheme.YELLOW -> Ids(
            R.color.py_toolbar, R.color.py_accent, R.color.py_bg, R.color.py_surface, R.color.py_section,
            R.color.py_stroke, R.color.py_text_primary, R.color.py_text_secondary, R.color.py_text_muted,
            R.color.py_divider, R.color.py_on_selected, R.color.py_cal_normal, R.color.py_cal_outside,
            R.color.py_cal_lunar, R.color.py_cal_grid, R.color.py_today_ring,
            R.drawable.widget_bg_py, R.drawable.widget_section_bg_py, R.drawable.bg_widget_today_cell_py
        )
    }

    private fun c(id: Int) = ContextCompat.getColor(ctx, id)

    val toolbar = c(ids.toolbar)
    val accent = c(ids.accent)
    val bg = c(ids.bg)
    val surface = c(ids.surface)
    val section = c(ids.section)
    val stroke = c(ids.stroke)
    val textPrimary = c(ids.textPrimary)
    val textSecondary = c(ids.textSecondary)
    val textMuted = c(ids.textMuted)
    val divider = c(ids.divider)
    val onSelected = c(ids.onSelected)
    val calNormal = c(ids.calNormal)
    val calOutside = c(ids.calOutside)
    val calLunar = c(ids.calLunar)
    val calGrid = c(ids.calGrid)
    val todayRing = c(ids.todayRing)

    /** 팔레트와 상관없이 고정 (달력 관례) */
    val sun = c(R.color.cal_sun)
    val sat = c(R.color.cal_sat)

    /** 위젯 배경/구역/오늘칸 그림 리소스 */
    val widgetBgRes = ids.widgetBg
    val widgetSectionRes = ids.widgetSection
    val todayCellRes = ids.todayCell

    companion object {
        /** 현재 설정된 팔레트로 만든다. */
        fun of(context: Context) = Palette(context, Prefs.appTheme(context))
    }
}
