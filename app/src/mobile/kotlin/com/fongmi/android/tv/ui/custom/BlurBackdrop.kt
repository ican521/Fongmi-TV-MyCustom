package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.fongmi.android.tv.R
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * 毛玻璃背景：实时把本控件背后的内容截取本控件所在区域，缩小后模糊，裁成圆角（胶囊/正圆）绘制，
 * 再叠一层半透明深色蒙版。直接对目标内容 View 做软件绘制（view.draw），只截背后的内容，
 * 不会把底栏/按钮自身截进去造成越叠越黑的反馈问题。用于胶囊底栏与圆形按钮最底层。
 */
class BlurBackdrop @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // 缩小倍数：越小越模糊、越省性能。0.30x 位图比 0.40x 小约 43%，模糊计算量同步减少；
    // 且等效模糊半径（radius/downscale）从 20px 提升到 26.7px，视觉强度反而更高。
    private val downscale = 0.30f
    // 高斯模糊半径（作用在缩小后的位图上，σ = radius/2）。
    private val blurRadius = 8f
    // 深色蒙版：#242424 @ 39% 不透明（对齐 KernelSU 的 surfaceContainer @ 40%）。
    private val tintColor = Color.argb(100, 0x24, 0x24, 0x24)

    // 窗口背景（?android:windowBackground）：view.draw() 只画目标视图树，不含窗口底色。
    // 截取前先铺满底色，否则文字等半透明区域会让后方清晰内容透出来（看起来文字没被模糊）。
    private val windowBg: Drawable? = run {
        val ta = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        val d = ta.getDrawable(0)
        ta.recycle()
        d
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isFilterBitmap = true
    }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tintColor }
    private val path = Path()
    private val rect = RectF()

    // 显示用位图（本控件区域，缩小后已模糊）。
    private var bitmap: Bitmap? = null
    private var bmpCanvas: Canvas? = null
    // 抓取用暂存位图：单帧抓取成功后才合入显示位图，失败帧保留上一帧内容，避免滚动中闪烁。
    private var captureBmp: Bitmap? = null
    private var captureCanvas: Canvas? = null

    private var target: View? = null
    private val selfLoc = IntArray(2)
    private val targetLoc = IntArray(2)

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (isShown && width > 0 && height > 0) invalidate()
        true
    }
    private var registeredVto: ViewTreeObserver? = null

    private var preferredId: Int = 0
    /** 截取中标志：兜底防止 t.draw() 递归进入自己的 onDraw。 */
    private var capturing = false

    fun setPreferredTargetId(id: Int) {
        if (preferredId != id) {
            preferredId = id
            target = null
            unregister()
            invalidate()
        }
    }

    private fun ensureTarget(): View? {
        target?.let { if (it.isAttachedToWindow) return it }
        var p: android.view.ViewParent? = parent
        while (p != null) {
            if (p is ViewGroup) {
                if (preferredId != 0) {
                    val pref = p.findViewById<View>(preferredId)
                    if (pref != null && pref.width > 0 && pref.height > 0) {
                        target = pref
                        return pref
                    }
                }
                val overlay = p.findViewById<View>(R.id.overlay)
                if (overlay != null && overlay.visibility == View.VISIBLE && overlay.width > 0) {
                    target = overlay
                    return overlay
                }
                val c = p.findViewById<View>(R.id.container)
                if (c != null) {
                    target = c
                    return c
                }
            }
            p = p.parent
        }
        return null
    }

    private fun register() {
        val t = ensureTarget() ?: return
        val vto = t.viewTreeObserver
        if (registeredVto !== vto) {
            unregister()
            vto.addOnPreDrawListener(preDrawListener)
            registeredVto = vto
        }
    }

    /** 找到自己在目标 root 子树内的最高宿主 View（直接位于 root 之下的祖先）。
     *  若 root 是自己的祖先（圆钮目标为 R.id.container 的情况），返回该宿主用于截取前临时隐藏；
     *  若 root 不是自己祖先（底栏情况），返回 null。 */
    private fun findHostWithin(root: View): View? {
        if (root === this) return this
        var v: View? = this
        while (v != null) {
            val p = v.parent
            if (p === root) return v
            v = (p as? View)
        }
        return null
    }

    private fun unregister() {
        registeredVto?.let {
            if (it.isAlive) it.removeOnPreDrawListener(preDrawListener)
        }
        registeredVto = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        register()
    }

    override fun onDetachedFromWindow() {
        unregister()
        target = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val bw = (w * downscale).roundToInt().coerceAtLeast(1)
        val bh = (h * downscale).roundToInt().coerceAtLeast(1)
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        bmpCanvas = Canvas(bitmap!!)
        captureBmp?.recycle()
        captureBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        captureCanvas = Canvas(captureBmp!!)
        path.reset()
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        val r = h / 2f
        path.addRoundRect(rect, r, r, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        val cap = captureBmp
        val capC = captureCanvas
        val t = ensureTarget()
        // 先把背后内容（目标 View）软件绘制到暂存位图；成功后才合入显示位图并模糊。
        // 失败帧（目标正在 layout、位图回收瞬间等）保留上一帧显示内容，避免闪烁。
        if (t != null && cap != null && capC != null && !capturing && t.isAttachedToWindow && t.isLaidOut && t.width > 0 && t.height > 0) {
            register()
            getLocationOnScreen(selfLoc)
            t.getLocationOnScreen(targetLoc)
            val ox = (selfLoc[0] - targetLoc[0]).toFloat()
            val oy = (selfLoc[1] - targetLoc[1]).toFloat()
            var ok = true
            // 若目标 t 是自己的祖先（例如圆钮的目标 R.id.container 是 BlurFab 的祖先），
            // 截取时 t.draw() 会递归绘制到自己造成 StackOverflow（被 catch 后 ok=false、位图永远是空的）。
            // 截取前把自己在 t 内的最高宿主（如 BlurFab）临时隐藏，截完恢复；同时用 capturing 标志兜底防递归。
            val host = findHostWithin(t)
            val hostOldVis = host?.visibility
            capturing = true
            try {
                if (host != null && hostOldVis == View.VISIBLE) host.visibility = View.INVISIBLE
                capC.save()
                capC.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                windowBg?.let {
                    it.setBounds(0, 0, cap.width, cap.height)
                    it.draw(capC)
                }
                capC.scale(downscale, downscale)
                capC.translate(-ox, -oy)
                try {
                    t.draw(capC)
                } catch (_: Throwable) {
                    ok = false
                }
                capC.restore()
            } finally {
                if (host != null && hostOldVis == View.VISIBLE) host.visibility = hostOldVis!!
                capturing = false
            }
            if (ok) {
                bmpCanvas?.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                bmpCanvas?.drawBitmap(cap, 0f, 0f, null)
                gaussianBlur(bmp, blurRadius)
            }
        }

        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bmp, null, rect, paint)
        canvas.drawRect(rect, tintPaint)
        canvas.restore()
    }

    /** 真正的高斯模糊：σ=radius/2，可分离横向+纵向两趟卷积，真实高斯核权重。 */
    private fun gaussianBlur(bmp: Bitmap, radius: Float) {
        val r = radius.roundToInt().coerceAtLeast(1)
        val w = bmp.width
        val h = bmp.height
        if (w < 2 || h < 2) return
        // 高斯核：σ = r/2，权重 w[i] = exp(-i²/(2σ²))，归一化到 [0..1]。
        val sigma = r / 2f
        val kernel = FloatArray(2 * r + 1) { i ->
            val d = i - r
            exp(-(d * d) / (2f * sigma * sigma))
        }
        var sum = 0f
        for (k in kernel) sum += k
        for (i in kernel.indices) kernel[i] /= sum

        val src = IntArray(w * h)
        val tmp = IntArray(w * h)
        bmp.getPixels(src, 0, w, 0, 0, w, h)
        // 横向
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var a = 0f; var rr = 0f; var g = 0f; var b = 0f
                for (k in -r..r) {
                    val xi = (x + k).coerceIn(0, w - 1)
                    val c = src[row + xi]
                    val f = kernel[k + r]
                    a += (c ushr 24) * f
                    rr += ((c shr 16) and 0xFF) * f
                    g += ((c shr 8) and 0xFF) * f
                    b += (c and 0xFF) * f
                }
                tmp[row + x] = (a.toInt() shl 24) or (rr.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
            }
        }
        // 纵向
        for (x in 0 until w) {
            for (y in 0 until h) {
                var a = 0f; var rr = 0f; var g = 0f; var b = 0f
                for (k in -r..r) {
                    val yi = (y + k).coerceIn(0, h - 1)
                    val c = tmp[yi * w + x]
                    val f = kernel[k + r]
                    a += (c ushr 24) * f
                    rr += ((c shr 16) and 0xFF) * f
                    g += ((c shr 8) and 0xFF) * f
                    b += (c and 0xFF) * f
                }
                src[y * w + x] = (a.toInt() shl 24) or (rr.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
            }
        }
        bmp.setPixels(src, 0, w, 0, 0, w, h)
    }
}
