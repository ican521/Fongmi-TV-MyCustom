package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.fongmi.android.tv.R
import kotlin.math.roundToInt

/**
 * 毛玻璃背景：实时把本控件背后的内容（宿主里的 ViewPager2 或 overlay）截取本控件所在区域，
 * 缩小后做高斯模糊，裁成胶囊形绘制，再叠一层半透明深色蒙版。
 * 用于胶囊底栏最底层，营造 frosted glass 效果。软件模糊，全版本可用、低开销。
 */
class BlurBackdrop @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // 缩小倍数：越小越模糊、越省性能。0.22 → 模糊感很强。
    private val downscale = 0.22f
    // 模糊半径（作用在缩小后的位图上），强模糊。
    private val blurRadius = 16f
    // 深色蒙版：#242424 @ 55% 不透明（偏深，保证图标清晰）。
    private val tintColor = Color.argb(140, 0x24, 0x24, 0x24)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isFilterBitmap = true
    }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tintColor }
    private val path = Path()
    private val rect = RectF()

    private var bitmap: Bitmap? = null
    private var bmpCanvas: Canvas? = null
    private var target: View? = null
    private val selfLoc = IntArray(2)
    private val targetLoc = IntArray(2)

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (target != null && isShown && width > 0 && height > 0) invalidate()
        true
    }
    private var registeredVto: ViewTreeObserver? = null

    // 外部可指定优先模糊的目标 id（如圆形按钮指定 R.id.pager，因其与按钮为兄弟节点、不含自身）。
    private var preferredId: Int = 0

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
        path.reset()
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        // 胶囊：圆角半径 = 高度的一半。
        val r = h / 2f
        path.addRoundRect(rect, r, r, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap
        val bmpC = bmpCanvas
        if (bmp == null || bmpC == null) return

        val t = ensureTarget()
        if (t != null && t.width > 0 && t.height > 0) {
            register()
            getLocationOnScreen(selfLoc)
            t.getLocationOnScreen(targetLoc)
            val ox = (selfLoc[0] - targetLoc[0]).toFloat()
            val oy = (selfLoc[1] - targetLoc[1]).toFloat()
            bmpC.save()
            bmpC.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            bmpC.scale(downscale, downscale)
            bmpC.translate(-ox, -oy)
            try {
                t.draw(bmpC)
            } catch (_: Throwable) {
                // 某些时机目标尚未就绪，忽略，下一帧重试。
            }
            bmpC.restore()
            stackBlur(bmp, blurRadius)
        }

        canvas.save()
        canvas.clipPath(path)
        if (t != null) {
            canvas.drawBitmap(bmp, null, rect, paint)
        }
        canvas.drawRect(rect, tintPaint)
        canvas.restore()
    }

    /** 快速盒式模糊（3 次水平+垂直遍历近似高斯），作用于整张小位图。 */
    private fun stackBlur(bmp: Bitmap, radius: Float) {
        val r = radius.roundToInt().coerceAtLeast(1)
        val w = bmp.width
        val h = bmp.height
        if (w < 2 || h < 2) return
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(2) {
            boxBlurHorizontal(pixels, w, h, r)
            boxBlurVertical(pixels, w, h, r)
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun boxBlurHorizontal(pix: IntArray, w: Int, h: Int, r: Int) {
        val temp = IntArray(w)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) temp[x] = pix[row + x]
            for (x in 0 until w) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                val i0 = (x - r).coerceAtLeast(0)
                val i1 = (x + r).coerceAtMost(w - 1)
                for (i in i0..i1) {
                    val c = temp[i]
                    a += c ushr 24
                    rr += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                    n++
                }
                pix[row + x] = (a / n shl 24) or (rr / n shl 16) or (g / n shl 8) or (b / n)
            }
        }
    }

    private fun boxBlurVertical(pix: IntArray, w: Int, h: Int, r: Int) {
        val temp = IntArray(h)
        for (x in 0 until w) {
            for (y in 0 until h) temp[y] = pix[y * w + x]
            for (y in 0 until h) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                val i0 = (y - r).coerceAtLeast(0)
                val i1 = (y + r).coerceAtMost(h - 1)
                for (i in i0..i1) {
                    val c = temp[i]
                    a += c ushr 24
                    rr += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                    n++
                }
                pix[y * w + x] = (a / n shl 24) or (rr / n shl 16) or (g / n shl 8) or (b / n)
            }
        }
    }
}
