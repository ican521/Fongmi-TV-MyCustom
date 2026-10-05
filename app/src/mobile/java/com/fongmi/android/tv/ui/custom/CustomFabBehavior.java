package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

public class CustomFabBehavior extends CoordinatorLayout.Behavior<View> {

    public CustomFabBehavior() {
        super();
    }

    public CustomFabBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View directTargetChild, @NonNull View target, int axes, int type) {
        return axes == ViewCompat.SCROLL_AXIS_VERTICAL;
    }

    @Override
    public void onNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, consumed);
        if ("top".equals(child.getTag())) {
            if (dyConsumed > 0 && child.getVisibility() == View.INVISIBLE) {
                child.setVisibility(View.VISIBLE);
            } else if (dyConsumed < 0 && child.getVisibility() == View.VISIBLE) {
                child.setVisibility(View.INVISIBLE);
            }
        } else {
            if (dyConsumed > 0 && child.getVisibility() == View.VISIBLE) {
                child.setVisibility(View.INVISIBLE);
            } else if (dyConsumed < 0 && child.getVisibility() == View.INVISIBLE) {
                child.setVisibility(View.VISIBLE);
            }
        }
    }
}
