package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.R;

/**
 * 圆形毛玻璃按钮：底层为 {@link BlurBackdrop}（实时模糊背后内容 + 半透明深色蒙版，参数与底栏一致），
 * 上层为图标，按下时图标层叠加深色反馈。对外行为与普通 View 一致（setVisibility / setOnClickListener / getTag 等）。
 */
public class BlurFab extends FrameLayout {

    private final BlurBackdrop mBlur;
    private final AppCompatImageView mIcon;

    public BlurFab(@NonNull Context context) {
        this(context, null);
    }

    public BlurFab(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BlurFab(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // 底层：毛玻璃（BlurBackdrop 会按自身尺寸裁剪，正方形 + 半径=高/2 即正圆）。
        mBlur = new BlurBackdrop(context);
        // 模糊背后的点播列表 pager（与按钮为兄弟节点，不含按钮自身，避免递归）。
        mBlur.setPreferredTargetId(R.id.pager);
        LayoutParams blurLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(mBlur, blurLp);

        // 上层：图标。padding 16dp，按下时背景加深。
        mIcon = new AppCompatImageView(context);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
        mIcon.setPadding(pad, pad, pad, pad);
        mIcon.setBackgroundResource(R.drawable.bg_fab_press);
        LayoutParams iconLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        iconLp.gravity = Gravity.CENTER;
        addView(mIcon, iconLp);

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

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        // 隐藏时停止底层模糊的绘制开销（BlurBackdrop 自身 onDraw 会判断 isShown）。
    }
}
