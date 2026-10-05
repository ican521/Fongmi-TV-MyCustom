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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
 * 悬浮胶囊底栏（Compose），视觉/尺寸/阴影/指示器照搬参考项目 KernelSU 的
 * FloatingBottomBar（isBlurEnabled=false 分支）：
 *  - 胶囊高 64dp、内边距 4dp、指示器高 56dp
 *  - 阴影 dropShadow：半径 10dp、黑色、alpha 0.1（暗色 0.2）
 *  - 容器色取主题 colorSurface、指示器取 colorPrimary.copy(alpha=0.15)
 *  - 未选中文字/图标 colorOnSurface，选中 colorPrimary
 * 手感沿用弹簧物理（Animatable + spring）+ 速度拉伸 + 横向拖动。
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
    private fun themeColor(attrName: String, default: Int): Color {
        val res = context.resources
        var id = res.getIdentifier(attrName, "attr", "android")
        if (id == 0) id = res.getIdentifier(attrName, "attr", context.packageName)
        if (id == 0) return Color(default)
        val ta = context.obtainStyledAttributes(intArrayOf(id))
        return try {
            Color(ta.getColor(0, default))
        } finally {
            ta.recycle()
        }
    }

    @Composable
    private fun Bar() {
        val accent = themeColor("colorPrimary", 0xFF6750A4.toInt())
        val containerColor = themeColor("colorSurface", 0xFF1B1B1F.toInt()).copy(alpha = 0.95f)
        val onSurface = themeColor("colorOnSurface", 0xFFE6E6E6.toInt())
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()

        val visible = tabs.filter { it.id in visibleIds.value }
        val count = visible.size.coerceAtLeast(1)
        val sel = selected.intValue.coerceIn(0, tabs.size - 1)

        var totalWidthPx by remember { mutableFloatStateOf(0f) }
        val contentInsetPx = with(density) { 8.dp.toPx() }
        val tabWidthPx = (totalWidthPx - contentInsetPx) / count

        val position = remember { Animatable(sel.toFloat(), 0.001f) }
        val stretch = remember { Animatable(1f, 0.001f) }
        var dragNonce by remember { mutableIntStateOf(0) }

        LaunchedEffect(sel, count, dragNonce) {
            position.animateTo(sel.toFloat(), spring(dampingRatio = 1f, stiffness = 1000f))
        }
        LaunchedEffect(Unit) {
            snapshotFlow { position.velocity }.collect { v ->
                val target = (1f + (abs(v) / count) * 0.3f).coerceIn(1f, 1.4f)
                stretch.animateTo(target, spring(dampingRatio = 0.5f, stiffness = 220f))
            }
        }

        val pill = CircleShape
        val indicatorColor = accent.copy(alpha = 0.15f)

        // 胶囊本体（含 tab）
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { totalWidthPx = it.width.toFloat() }
                    .background(containerColor, pill)
                    .pointerInput(count) {
                        if (count <= 1) return@pointerInput
                        detectDragGestures(
                            onDragEnd = {
                                val idx = position.value.roundToInt().coerceIn(0, count - 1)
                                val real = visible.getOrNull(idx)?.let { tabs.indexOf(it) } ?: sel
                                if (real != selected.intValue) {
                                    selected.intValue = real
                                    listener?.onTabSelected(real)
                                }
                                dragNonce++
                            },
                            onDragCancel = { dragNonce++ },
                        ) { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val next =
                                    (position.value + dragAmount.x / tabWidthPx)
                                        .coerceIn(0f, (count - 1).toFloat())
                                // 照搬参考项目 DampedDragAnimation.valueAnimationSpec：
                                // 拖动时用 spring 追逐目标，而非 snapTo 瞬间贴合，
                                // 让指示器带轻微延迟/阻尼地跟随手指，不再过于跟手。
                                position.animateTo(
                                    next,
                                    spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f),
                                )
                            }
                        }
                    }
                    .height(64.dp)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visible.forEach { tab ->
                    val isSel = tabs.indexOf(tab) == sel
                    val tint = if (isSel) accent else onSurface
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { selectTab(tabs.indexOf(tab), true) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                        ) {
                            Icon(
                                painter = painterResource(tab.icon),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = stringResource(tab.label),
                                color = tint,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            // 指示器：照搬参考（高 56dp、accent@15%、圆角胶囊、按速度拉伸）
            if (totalWidthPx > 0f && tabWidthPx > 0f) {
                val tabWidthDp = with(density) { tabWidthPx.toDp() }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            translationX = position.value * tabWidthPx
                            scaleX = stretch.value
                        }
                        .width(tabWidthDp)
                        .height(56.dp)
                        .clip(pill)
                        .background(indicatorColor, pill),
                )
            }
        }
    }
}
