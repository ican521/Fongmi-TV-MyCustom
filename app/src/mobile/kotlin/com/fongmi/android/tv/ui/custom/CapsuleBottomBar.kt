package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.fongmi.android.tv.R
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 悬浮胶囊底栏（Compose 实现）。
 *
 * 忠实移植参考项目 KernelSU 的弹簧物理手感：指示器位置由 Animatable + spring 驱动，
 * 按速度拉伸（scaleX），并支持横向拖动切换。对外 API 与原 Java 版完全一致，
 * HomeActivity 无需改动。视觉采用纯色胶囊（参考项目的 isBlurEnabled=false 分支）。
 */
class CapsuleBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    fun interface OnTabSelectedListener {
        fun onTabSelected(index: Int)
    }

    private data class Tab(@IdRes val id: Int, val icon: Int, val label: Int)

    private val tabs = listOf(
        Tab(R.id.vod, R.drawable.ic_nav_vod, R.string.nav_vod),
        Tab(R.id.keep, R.drawable.ic_nav_keep, R.string.app_keep),
        Tab(R.id.setting, R.drawable.ic_nav_setting, R.string.nav_setting),
    )

    private val visibleIds = mutableStateOf(setOf<Int>())
    private val selected = mutableIntStateOf(0)
    private var listener: OnTabSelectedListener? = null

    init {
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setContent { Bar() }
        }
        addView(composeView)
    }

    fun setOnTabSelectedListener(listener: OnTabSelectedListener?) {
        this.listener = listener
    }

    fun selectTab(index: Int, animate: Boolean) {
        if (index < 0 || index >= tabs.size) return
        val changed = index != selected.intValue
        selected.intValue = index
        if (changed) listener?.onTabSelected(index)
    }

    fun setSelected(index: Int, animate: Boolean) {
        if (index < 0 || index >= tabs.size) return
        selected.intValue = index
    }

    fun setTabVisible(@IdRes id: Int, visible: Boolean) {
        val cur = visibleIds.value
        visibleIds.value = if (visible) cur + id else cur - id
    }

    fun isTabVisible(@IdRes id: Int): Boolean = visibleIds.value.contains(id)

    @Composable
    private fun accentColor(): Color {
        val ta = context.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.colorPrimary))
        return try {
            Color(ta.getColor(0, 0xFF6750A4.toInt()))
        } finally {
            ta.recycle()
        }
    }

    @Composable
    private fun Bar() {
        val accent = accentColor()
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()

        val visible = tabs.filter { it.id in visibleIds.value }
        val count = visible.size.coerceAtLeast(1)
        val sel = selected.intValue.coerceIn(0, tabs.size - 1)

        var widthPx by remember { mutableFloatStateOf(0f) }
        val tabWidth = widthPx / count

        val position = remember { Animatable(sel.toFloat(), 0.001f) }
        val stretch = remember { Animatable(1f, 0.001f) }

        LaunchedEffect(sel, count) {
            position.animateTo(sel.toFloat(), spring(dampingRatio = 1f, stiffness = 1000f))
        }
        LaunchedEffect(Unit) {
            snapshotFlow { position.velocity }.collect { v ->
                val target = (1f + (abs(v) / count) * 0.12f).coerceIn(1f, 1.18f)
                stretch.animateTo(target, spring(dampingRatio = 0.6f, stiffness = 250f))
            }
        }

        val containerColor = Color(0xCC1B1B1F)
        val indicatorColor = accent.copy(alpha = 0.28f)
        val pill = RoundedCornerShape(percent = 50)
        val onColor = Color(0xFFE6E6E6)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(pill)
                .background(containerColor)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(count) {
                    if (count <= 1) return@pointerInput
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val idx = position.value.roundToInt().coerceIn(0, count - 1)
                                val real = visible.getOrNull(idx)?.let { tabs.indexOf(it) } ?: sel
                                selectTab(real, true)
                            }
                        },
                        onDragCancel = {},
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val next =
                                (position.value + dragAmount.x / tabWidth)
                                    .coerceIn(0f, (count - 1).toFloat())
                            position.snapTo(next)
                        }
                    }
                },
        ) {
            // 指示器
            if (widthPx > 0f) {
                val indicatorDp = with(density) { tabWidth.toDp() }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(4.dp)
                        .width(indicatorDp)
                        .offset { IntOffset((position.value * tabWidth).roundToInt(), 0) }
                        .fillMaxHeight()
                        .graphicsLayer { scaleX = stretch.value }
                        .clip(pill)
                        .background(indicatorColor),
                )
            }

            Row(modifier = Modifier.fillMaxSize()) {
                visible.forEach { tab ->
                    val isSel = tabs.indexOf(tab) == sel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selectTab(tabs.indexOf(tab), true) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                painter = painterResource(tab.icon),
                                contentDescription = null,
                                tint = if (isSel) accent else onColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(tab.label),
                                color = if (isSel) accent else onColor,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
