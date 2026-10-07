package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;

/**
 * 圆形纯色按钮：底层为纯色圆形背景（#242424，90% 不透明度微微透明，与胶囊底栏一致），
 * 其上为按压变亮覆盖层（常态透明，按下 120ms 渐显 30% 白、松开 220ms 渐隐），
 * 再上为图标，按下时整个按钮缩至 88%。对外行为与普通 View 一致
 * （setVisibility / setOnClickListener / getTag 等）。
 */
public class BlurFab extends FrameLayout {

    private final View mBg;
    private final View mPress;
    private final AppCompatImageView mIcon;

    public static final long PRESS_DURATION = 120L;
    public static final long RELEASE_DURATION = 200L;
    private long mPressedAt;

    /** 回弹动画入口：postDelayed 延迟调度，避免打断未播完的按下动画。 */
    private final Runnable mRelease = this::startRelease;

    public BlurFab(@NonNull Context context) {
        this(context, null);
    }

    public BlurFab(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BlurFab(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // 底层：纯色圆形背景（#242424，90% 不透明度微微透明，与胶囊底栏同色同风格）。
        mBg = new View(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(ContextCompat.getColor(context, R.color.capsule_bar_bg));
        mBg.setBackground(bg);
        LayoutParams bgLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(mBg, bgLp);

        // 按压变亮层：30% 白圆形，常态 alpha=0 完全透明，按下渐显（见 drawableStateChanged）。
        // 不用 selector：按压状态在父容器上而覆盖层可挂在自身上，由代码直接驱动，机制确定生效。
        mPress = new View(context);
        GradientDrawable press = new GradientDrawable();
        press.setShape(GradientDrawable.OVAL);
        press.setColor(ContextCompat.getColor(context, R.color.fab_press_overlay));
        mPress.setBackground(press);
        mPress.setAlpha(0f);
        LayoutParams pressLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(mPress, pressLp);

        // 上层：图标。padding 16dp。
        mIcon = new AppCompatImageView(context);
        int pad = ResUtil.dp2px(16);
        mIcon.setPadding(pad, pad, pad, pad);
        LayoutParams iconLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        iconLp.gravity = Gravity.CENTER;
        addView(mIcon, iconLp);

        // 最上层：与底栏一致的亮色描边（白色 12%、1dp），作为前景覆盖在图标之上。
        setForeground(ContextCompat.getDrawable(context, R.drawable.bg_fab_stroke));

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BlurFab);
            try {
                int src = a.getResourceId(R.styleable.BlurFab_fabSrc, 0);
                if (src != 0) mIcon.setImageResource(src);
                if (a.hasValue(R.styleable.BlurFab_fabTint)) {
                    mIcon.setColorFilter(a.getColor(R.styleable.BlurFab_fabTint, 0));
                }
            } finally {
                a.recycle();
            }
        }
    }

    public AppCompatImageView getIcon() {
        return mIcon;
    }

    /**
     * 按压双重特效，由系统 pressed 状态驱动（与缩放同源，确定性生效）：
     * 变亮——按压覆盖层 alpha 0→1（120ms）；缩放——整个按钮 1→0.88（120ms）；
     * 松开回弹均为 200ms。
     * 快速单击时松手发生在按下动画播完前：此时绝不能启动回弹动画
     * （animate().start() 会取消正在跑的按下动画，导致变亮未达峰值即冻结），
     * 只 postDelayed 等按下动画自然播完后再回弹；长按则立即回弹。
     */
    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        removeCallbacks(mRelease);
        if (isPressed()) {
            mPressedAt = android.os.SystemClock.elapsedRealtime();
            animatePress(1f, PRESS_DURATION);
            animateScale(0.88f, PRESS_DURATION);
        } else if (mPressedAt > 0) {
            long elapsed = android.os.SystemClock.elapsedRealtime() - mPressedAt;
            if (elapsed < PRESS_DURATION) {
                postDelayed(mRelease, PRESS_DURATION - elapsed);
            } else {
                mRelease.run();
            }
        }
    }

    private void startRelease() {
        animatePress(0f, RELEASE_DURATION);
        animateScale(1f, RELEASE_DURATION);
    }

    private void animatePress(float target, long duration) {
        if (mPress.getAlpha() == target) return;
        mPress.animate().alpha(target)
                .setDuration(duration)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void animateScale(float target, long duration) {
        if (getScaleX() == target) return;
        animate().scaleX(target).scaleY(target)
                .setDuration(duration)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }
}
