package com.fongmi.android.tv.ui.custom

/**
 * 入场动画统一曲线参数：胶囊底栏、右下角圆钮、点播顶栏共用。
 * 固定 1050ms + Overshoot tension 0.9（小幅过冲回弹）。
 */
object RevealAnim {

    /** 入场动画时长（毫秒）。 */
    const val DURATION_MS = 1050

    /** Overshoot 回弹张力（0.9 = 极小幅过冲）。 */
    const val TENSION = 0.9f
}
