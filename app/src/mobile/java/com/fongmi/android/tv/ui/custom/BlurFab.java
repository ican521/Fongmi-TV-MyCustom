package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.R;

/**
 * 圆形纯色按钮：底层为纯色圆形背景（#242424 不透明，与胶囊底栏一致），
 * 上层为图标，按下时图标层叠加深色反馈。对外行为与普通 View 一致（setVisibility / setOnClickListener / getTag 等）。
 */
public class BlurFab extends FrameLayout {

    private final View mBg;
    private final AppCompatImageView mIcon;

    public BlurFab(@NonNull Context context) {
        this(context, null);
    }

    public BlurFab(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BlurFab(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // 底层：纯色圆形背景（#242424 不透明，与胶囊底栏同色同风格）。
        mBg = new View(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.rgb(36, 36, 36));
        mBg.setBackground(bg);
        LayoutParams bgLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(mBg, bgLp);

        // 上层：图标。padding 16dp，按下时背景加深。
        mIcon = new AppCompatImageView(context);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
        mIcon.setPadding(pad, pad, pad, pad);
        mIcon.setBackgroundResource(R.drawable.bg_fab_press);
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

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
    }
}
