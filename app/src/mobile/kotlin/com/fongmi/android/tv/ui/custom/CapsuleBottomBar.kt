package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.fongmi.android.tv.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

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

    // 启动加载完成后由宿主置 true，触发整个胶囊从屏幕底部外向上平移回位的入场动画。
    private val revealRequested = mutableStateOf(false)

    /** 启动加载完成后调用：底栏从屏幕底部之外向上平移进入原位。 */
    fun reveal() {
        revealRequested.value = true
    }

    init {
        // 允许胶囊橡皮筋微移时溢出部分正常绘制，不被宿主裁切。
        clipChildren = false
        clipToPadding = false
        val composeView = ComposeView(context).apply {
            clipChildren = false
            clipToPadding = false
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
        val onSurface = themeColor("colorOnSurface", 0xFFE6E6E6.toInt())
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()

        // 启动加载期间胶囊隐藏在屏幕底部之外；宿主调用 reveal() 后，整体向上平移回原位并淡入。
        val slide = remember { Animatable(1f) }
        val barAlpha = remember { Animatable(0f) }
        // 位移距离 = 胶囊高度(64dp) + 底部外边距(20dp)，确保初始完全在屏幕之外不可见。
        val slideDistancePx = with(density) { 84.dp.toPx() }
        val shouldReveal = revealRequested.value
        LaunchedEffect(shouldReveal) {
            if (shouldReveal) {
                launch { slide.animateTo(0f, tween(durationMillis = 420, easing = EaseOut)) }
                launch { barAlpha.animateTo(1f, tween(durationMillis = 320)) }
            }
        }

        val visible = tabs.filter { it.id in visibleIds.value }
        val count = visible.size.coerceAtLeast(1)
        val sel = selected.intValue.coerceIn(0, tabs.size - 1)

        var totalWidthPx by remember { mutableFloatStateOf(0f) }
        val contentInsetPx = with(density) { 8.dp.toPx() }
        val tabWidthPx = (totalWidthPx - contentInsetPx) / count

        val position = remember { Animatable(sel.toFloat(), 0.001f) }
        val stretch = remember { Animatable(1f, 0.001f) }
        var dragNonce by remember { mutableIntStateOf(0) }
        // 拖动目标累加器：独立于滞后的动画值 position.value，保证目标随手指正确累加，能拖到任意 tab。
        var dragTarget by remember { mutableFloatStateOf(sel.toFloat()) }

        // 照搬参考：拖动时整个胶囊做橡皮筋式整体微移（panelOffset），松手弹回 0。
        val offsetAnimation = remember { Animatable(0f) }
        val rubberBandPx = with(density) { 4.dp.toPx() }
        val panelOffset by remember(rubberBandPx, totalWidthPx) {
            derivedStateOf {
                if (totalWidthPx == 0f) 0f
                else {
                    val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                    rubberBandPx * sign(fraction) * EaseOut.transform(abs(fraction))
                }
            }
        }

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
        // 胶囊外层一圈亮色描边：底栏为深色，用白色 12% 透明度做细微亮边（参考项目的高光思路）。
        val strokeColor = Color.White.copy(alpha = 0.12f)

        // 胶囊本体（含 tab + 指示器）。panelOffset 加在最外层，拖动时整个胶囊
        // （背景 + 图标文字 + 指示器）作为一个刚体做橡皮筋微移，图标文字不会单独滑动。
        // 内层 280dp 居中放入 288dp 的 View，左右各留 4dp 余量，橡皮筋平移不会裁切圆角。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = barAlpha.value
                    translationX = panelOffset
                    translationY = slide.value * slideDistancePx
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight(),
            ) {
            // 毛玻璃背景层：实时模糊背后内容 + 半透明深色蒙版，裁成胶囊形。
            // 作为最底层绘制，图标文字与指示器在其之上。
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(pill),
                factory = { ctx -> BlurBackdrop(ctx) },
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { totalWidthPx = it.width.toFloat() }
                    .border(1.dp, strokeColor, pill)
                    .pointerInput(count) {
                        if (count <= 1) return@pointerInput
                        detectDragGestures(
                            onDragStart = { dragTarget = position.value },
                            onDragEnd = {
                                val idx = dragTarget.roundToInt().coerceIn(0, count - 1)
                                val real = visible.getOrNull(idx)?.let { tabs.indexOf(it) } ?: sel
                                if (real != selected.intValue) {
                                    selected.intValue = real
                                    listener?.onTabSelected(real)
                                }
                                dragNonce++
                                scope.launch {
                                    offsetAnimation.animateTo(
                                        0f,
                                        spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.5f),
                                    )
                                }
                            },
                            onDragCancel = {
                                dragNonce++
                                scope.launch {
                                    offsetAnimation.animateTo(
                                        0f,
                                        spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.5f),
                                    )
                                }
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            // 用独立累加器 dragTarget 累加位移（而非滞后的 position.value），
                            // 保证目标随手指正确累加、能拖到任意 tab；position 用弹簧追逐 dragTarget。
                            val next =
                                (dragTarget + dragAmount.x / tabWidthPx)
                                    .coerceIn(0f, (count - 1).toFloat())
                            dragTarget = next
                            // 弹簧追逐手指：刚度取参考的 1000，追得快、延迟很轻。
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                position.animateTo(
                                    next,
                                    spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f),
                                )
                            }
                            // 橡皮筋：整个胶囊微移。
                            scope.launch {
                                offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                            }
                        }
                    }
                    .height(64.dp)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visible.forEach { tab ->
                    // 照搬参考：基础 tab 的图标/文字始终为 onSurface，不随选中变色；
                    // 选中态仅由上方覆盖的指示器（accent@15%）体现。
                    val tint = onSurface
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
}
