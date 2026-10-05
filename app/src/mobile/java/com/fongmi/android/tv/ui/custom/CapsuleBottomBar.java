package com.fongmi.android.tv.ui.custom;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬浮胶囊底栏：复刻参考项目的「圆角胶囊容器 + 选中胶囊横向平滑跟随 + TV 焦点导航」交互，
 * 用原生 View 实现以适配现有架构（不引入 Compose）。
 */
public class CapsuleBottomBar extends FrameLayout {

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }

    private static final int[] TAB_IDS = {R.id.vod, R.id.keep, R.id.setting};
    private static final int[] TAB_ICONS = {R.drawable.ic_nav_vod, R.drawable.ic_nav_keep, R.drawable.ic_nav_setting};
    private static final int[] TAB_LABELS = {R.string.nav_vod, R.string.app_keep, R.string.nav_setting};

    private LinearLayout mTabs;
    private View mIndicator;
    private final List<View> mTabViews = new ArrayList<>();
    private int mSelectedIndex = 0;
    private OnTabSelectedListener mListener;
    private ObjectAnimator mAnimator;

    public CapsuleBottomBar(@NonNull Context context) {
        this(context, null);
    }

    public CapsuleBottomBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CapsuleBottomBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_capsule_bottom_bar, this, true);
        mTabs = findViewById(R.id.tabs);
        mIndicator = findViewById(R.id.indicator);

        for (int i = 0; i < TAB_IDS.length; i++) {
            View tab = LayoutInflater.from(context).inflate(R.layout.item_capsule_tab, mTabs, false);
            tab.setId(TAB_IDS[i]);
            tab.setVisibility(GONE);
            ((ImageView) tab.findViewById(R.id.icon)).setImageResource(TAB_ICONS[i]);
            ((TextView) tab.findViewById(R.id.label)).setText(TAB_LABELS[i]);
            final int index = i;
            tab.setOnClickListener(v -> {
                v.requestFocus();
                selectTab(index, true);
            });
            tab.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) selectTab(index, true);
            });
            mTabs.addView(tab);
            mTabViews.add(tab);
        }
        applyIndicatorColor();
    }

    private void applyIndicatorColor() {
        int primary = resolveColorPrimary();
        int indicatorColor = (primary & 0x00FFFFFF) | (0x40 << 24);
        if (mIndicator.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) mIndicator.getBackground()).setColor(indicatorColor);
        }
    }

    private int resolveColorPrimary() {
        TypedArray ta = getContext().obtainStyledAttributes(new int[]{androidx.appcompat.R.attr.colorPrimary});
        try {
            return ta.getColor(0, Color.WHITE);
        } finally {
            ta.recycle();
        }
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        mListener = listener;
    }

    /** 用户/焦点触发的选中：更新高亮、移动指示器，并回调切页。 */
    public void selectTab(int index, boolean animate) {
        if (index < 0 || index >= mTabViews.size()) return;
        boolean changed = index != mSelectedIndex;
        applySelection(index, animate);
        if (changed && mListener != null) {
            mListener.onTabSelected(index);
        }
    }

    /** 仅同步高亮与指示器位置，不触发切页回调（供程序化返回/初始化使用）。 */
    public void setSelected(int index, boolean animate) {
        if (index < 0 || index >= mTabViews.size()) return;
        applySelection(index, animate);
    }

    private void applySelection(int index, boolean animate) {
        mSelectedIndex = index;
        for (int i = 0; i < mTabViews.size(); i++) {
            mTabViews.get(i).setSelected(i == index);
        }
        if (animate && mIndicator.getWidth() > 0) {
            animateIndicatorTo(index);
        } else {
            positionIndicator(false);
        }
    }

    private List<View> visibleTabs() {
        List<View> visible = new ArrayList<>();
        for (View tab : mTabViews) if (tab.getVisibility() == VISIBLE) visible.add(tab);
        return visible;
    }

    private int visibleIndexOf(int globalIndex) {
        int pos = 0;
        for (int i = 0; i < mTabViews.size(); i++) {
            if (i == globalIndex) return pos;
            if (mTabViews.get(i).getVisibility() == VISIBLE) pos++;
        }
        return 0;
    }

    private void animateIndicatorTo(int index) {
        List<View> visible = visibleTabs();
        if (visible.isEmpty()) return;
        int tabWidth = mIndicator.getWidth();
        if (tabWidth <= 0) {
            positionIndicator(false);
            return;
        }
        float target = visibleIndexOf(index) * tabWidth;
        if (mAnimator != null) mAnimator.cancel();
        mAnimator = ObjectAnimator.ofFloat(mIndicator, "translationX", mIndicator.getTranslationX(), target);
        mAnimator.setDuration(260);
        mAnimator.setInterpolator(new OvershootInterpolator(1.4f));
        mAnimator.start();
    }

    private void positionIndicator(boolean animate) {
        List<View> visible = visibleTabs();
        int innerWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        ViewGroup.LayoutParams lp = mIndicator.getLayoutParams();
        if (visible.isEmpty() || innerWidth <= 0) {
            lp.width = 0;
            lp.height = 0;
            mIndicator.setLayoutParams(lp);
            return;
        }
        int tabWidth = innerWidth / visible.size();
        lp.width = tabWidth;
        lp.height = getHeight() - getPaddingTop() - getPaddingBottom();
        mIndicator.setLayoutParams(lp);
        float target = visibleIndexOf(mSelectedIndex) * tabWidth;
        if (animate) {
            if (mAnimator != null) mAnimator.cancel();
            mAnimator = ObjectAnimator.ofFloat(mIndicator, "translationX", mIndicator.getTranslationX(), target);
            mAnimator.setDuration(260);
            mAnimator.setInterpolator(new OvershootInterpolator(1.4f));
            mAnimator.start();
        } else {
            mIndicator.setTranslationX(target);
        }
    }

    public void setTabVisible(int id, boolean visible) {
        for (View tab : mTabViews) {
            if (tab.getId() == id) {
                tab.setVisibility(visible ? VISIBLE : GONE);
                requestLayout();
                return;
            }
        }
    }

    public boolean isTabVisible(int id) {
        for (View tab : mTabViews) {
            if (tab.getId() == id) return tab.getVisibility() == VISIBLE;
        }
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        positionIndicator(false);
    }
}
