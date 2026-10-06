package com.fongmi.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.animation.ValueAnimator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.databinding.FragmentVodBinding;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.RevealEvent;
import com.fongmi.android.tv.event.StateEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.FilterListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.adapter.TypeAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.FilterDialog;
import com.fongmi.android.tv.ui.dialog.LinkDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class VodFragment extends BaseFragment implements ConfigListener, SiteListener, FilterListener, TypeAdapter.OnClickListener {

    private FragmentVodBinding mBinding;
    private SiteViewModel mViewModel;
    private TypeAdapter mAdapter;
    private Result mResult;
    // 右下角按钮是否已执行过入场动画。
    private boolean fabEntered;
    // 按钮入场的起始位移（屏幕右边框之外）。
    private float fabSlidePx;
    // 顶栏是否已执行过入场动画。
    private boolean appBarEntered;
    // 顶栏入场的起始位移（屏幕上边框之外）。
    private float appBarSlidePx;
    // 当前选中的分类页签位置（用于滑动指示器）。
    private int mTypePosition;
    // 指示器平移动画（切换页签时播放；每帧实时追踪目标位置，避免被 tab 条自身滚动打断）。
    private ValueAnimator mIndicatorAnim;

    public static VodFragment newInstance() {
        return new VodFragment();
    }

    private FolderFragment getFragment() {
        return (FolderFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
    }

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentVodBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        setRecyclerView();
        setViewModel();
        initFabEnter();
        initAppBarInset();
        initAppBarEnter();
        showProgress();
    }

    /**
     * 主页 ViewPager2 整体有状态栏高度的上 padding，顶栏默认从状态栏下方开始。
     * 这里把 appBar 负 margin 上移一个状态栏高度、并加等高顶部内边距，
     * 让黑色渐变从屏幕最顶（状态栏后方）开始，标题/tab 仍位于状态栏之下。
     */
    private void initAppBarInset() {
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.appBar, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            if (lp.topMargin != -top) {
                lp.topMargin = -top;
                v.setLayoutParams(lp);
            }
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    /** 按钮初始置于屏幕右边框之外、透明，等待入场动画。 */
    private void initFabEnter() {
        fabSlidePx = ResUtil.dp2px(280);
        for (View v : new View[]{mBinding.filter, mBinding.link, mBinding.top}) {
            v.setTranslationX(fabSlidePx);
            v.setAlpha(0f);
        }
    }

    /** 顶栏初始置于屏幕上边框之外、透明，等待入场动画。 */
    private void initAppBarEnter() {
        appBarSlidePx = ResUtil.dp2px(180);
        mBinding.appBar.setTranslationY(-appBarSlidePx);
        mBinding.appBar.setAlpha(0f);
    }

    @Override
    protected void initEvent() {
        mBinding.top.setOnClickListener(this::onTop);
        mBinding.link.setOnClickListener(this::onLink);
        mBinding.filter.setOnClickListener(this::onFilter);
        mBinding.filter.setOnLongClickListener(this::onLink);
        mBinding.toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.type.smoothScrollToPosition(position);
                mAdapter.setSelected(position);
                mTypePosition = position;
                updateTypeIndicator(true);
                setFabVisible(position);
            }
        });
    }

    private void setRecyclerView() {
        mBinding.type.setHasFixedSize(true);
        mBinding.type.setItemAnimator(null);
        mBinding.type.setAdapter(mAdapter = new TypeAdapter(this));
        mBinding.pager.setAdapter(new PageAdapter(getChildFragmentManager()));
        // 滑动指示器：默认解析为选中色，并在页签滚动/布局变化时实时跟随当前选中项。
        mBinding.typeIndicator.setSelected(true);
        mBinding.type.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                // 平移动画运行中由动画逐帧追踪目标，不能瞬时贴附，否则会 cancel 动画导致"没有动画"。
                if (mIndicatorAnim != null && mIndicatorAnim.isRunning()) return;
                updateTypeIndicator(false);
            }
        });
        mBinding.type.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (mIndicatorAnim != null && mIndicatorAnim.isRunning()) return;
            updateTypeIndicator(false);
        });
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(getViewLifecycleOwner(), this::setAdapter);
    }

    private void setAdapter(Result result) {
        mAdapter.addAll(mResult = result);
        mBinding.pager.getAdapter().notifyDataSetChanged();
        mTypePosition = 0;
        setFabVisible(0);
        revealFabs();
        revealAppBar();
        hideProgress();
        showContent();
        // 数据刷新后 tab 需重新布局，post 到布局完成后做一次无动画校正，
        // 保证指示器立即贴合首个 tab，不必等用户滑动 tab 触发。
        mBinding.type.post(() -> {
            if (mBinding != null) updateTypeIndicator(false);
        });
    }

    /**
     * 顶部分类页签的滑动指示器：把胶囊背景平滑移动/缩放到当前选中页签的位置与尺寸。
     * 动画每帧实时读取目标页签的位置（tab 条自身可能正在 smoothScroll），从旧位置插值到最新位置，
     * 因此不会再被 onScrolled 的瞬时贴附取消。
     *
     * @param animate true=切换页签时带动画；false=手指拖动 tab 条等场景实时跟随（无动画）。
     */
    private void updateTypeIndicator(boolean animate) {
        if (mBinding == null) return;
        RecyclerView rv = mBinding.type;
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return;
        // 数据切换（notifyDataSetChanged）时 RecyclerView 可能正处于布局中，
        // 此刻 findViewByPosition 会返回尺寸未稳定的复用/预布局 item，读出来是脏尺寸，
        // 必须跳过，待布局结束后的 onScrolled/onLayoutChange 或 post 校正再定位。
        if (rv.isComputingLayout() || rv.getWidth() <= 0 || rv.getHeight() <= 0) return;
        LinearLayoutManager llm = (LinearLayoutManager) lm;
        View target = llm.findViewByPosition(mTypePosition);
        // 目标必须已完成布局，且尺寸不超出 tab 条本身（过滤 728x176 这类瞬态脏值）。
        if (target == null || !target.isLaidOut() || target.getWidth() <= 0 || target.getHeight() <= 0) return;
        if (target.getWidth() > rv.getWidth() || target.getHeight() > rv.getHeight()) return;
        final View indicator = mBinding.typeIndicator;
        if (indicator.getVisibility() != View.VISIBLE) indicator.setVisibility(View.VISIBLE);
        final ViewGroup.LayoutParams lp = indicator.getLayoutParams();
        // 先缓存动画起点（旧位置/旧尺寸），再启动动画，避免读到变化后的值导致 delta 为 0。
        final float startX = indicator.getTranslationX();
        final float startY = indicator.getTranslationY();
        final int startW = lp.width > 0 ? lp.width : target.getWidth();
        final int startH = lp.height > 0 ? lp.height : target.getHeight();
        if (mIndicatorAnim != null) mIndicatorAnim.cancel();
        if (!animate) {
            lp.width = target.getWidth();
            lp.height = target.getHeight();
            indicator.setLayoutParams(lp);
            indicator.setTranslationX(target.getLeft());
            indicator.setTranslationY(target.getTop());
            return;
        }
        mIndicatorAnim = ValueAnimator.ofFloat(0f, 1f);
        mIndicatorAnim.setDuration(280);
        mIndicatorAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        mIndicatorAnim.addUpdateListener(a -> {
            View t = llm.findViewByPosition(mTypePosition);
            // 动画途中 tab 可能被回收/复用，同样只信任已布局且尺寸合理的目标，异常帧直接跳过。
            if (t == null || !t.isLaidOut() || t.getWidth() <= 0 || t.getHeight() <= 0) return;
            if (t.getWidth() > rv.getWidth() || t.getHeight() > rv.getHeight()) return;
            float f = a.getAnimatedFraction();
            indicator.setTranslationX(startX + (t.getLeft() - startX) * f);
            indicator.setTranslationY(startY + (t.getTop() - startY) * f);
            ViewGroup.LayoutParams p = indicator.getLayoutParams();
            p.width = Math.round(startW + (t.getWidth() - startW) * f);
            p.height = Math.round(startH + (t.getHeight() - startH) * f);
            indicator.setLayoutParams(p);
        });
        mIndicatorAnim.start();
    }

    /** 应用加载完成后，右下角按钮从屏幕右边框之外带回弹地滑入原位（与底栏 reveal 同时机）。 */
    private void revealFabs() {
        if (fabEntered || mBinding == null) return;
        fabEntered = true;
        View[] fabs = {mBinding.filter, mBinding.link, mBinding.top};
        int index = 0;
        for (View v : fabs) {
            if (v.getVisibility() == View.VISIBLE) {
                v.setTranslationX(fabSlidePx);
                v.setAlpha(0f);
                // 非线性 + 极小幅回弹：tension 0.9 过冲量进一步收小，1050ms 偏慢，错开 110ms 错落。
                v.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setInterpolator(new OvershootInterpolator(0.9f))
                        .setStartDelay(index * 110L)
                        .setDuration(1050)
                        .start();
                index++;
            } else {
                // 尚未显示的按钮复位变换，避免之后出现时停在屏幕外。
                v.setTranslationX(0f);
                v.setAlpha(1f);
            }
        }
    }

    /** 顶栏从屏幕上边框之外带回弹地滑入并淡入（与底栏 reveal 同时机，参数同底栏/圆钮一致）。 */
    private void revealAppBar() {
        if (appBarEntered || mBinding == null) return;
        appBarEntered = true;
        View bar = mBinding.appBar;
        bar.setTranslationY(-appBarSlidePx);
        bar.setAlpha(0f);
        bar.animate()
                .translationY(0f)
                .alpha(1f)
                .setInterpolator(new OvershootInterpolator(0.9f))
                .setDuration(1050)
                .start();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRevealEvent(RevealEvent event) {
        revealFabs();
        revealAppBar();
    }

    private void setFabVisible(int position) {
        if (mAdapter.getItemCount() == 0) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.link.setVisibility(View.VISIBLE);
            mBinding.filter.setVisibility(View.GONE);
        } else if (!mAdapter.get(position).getFilters().isEmpty()) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.link.setVisibility(View.GONE);
            mBinding.filter.setVisibility(View.VISIBLE);
        } else if (position == 0 || mAdapter.get(position).getFilters().isEmpty()) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.filter.setVisibility(View.GONE);
            mBinding.link.setVisibility(View.VISIBLE);
        }
    }

    private void onTop(View view) {
        getFragment().scrollToTop();
        mBinding.top.setVisibility(View.INVISIBLE);
        if (mBinding.filter.getVisibility() == View.INVISIBLE) mBinding.filter.setVisibility(View.VISIBLE);
        else if (mBinding.link.getVisibility() == View.INVISIBLE) mBinding.link.setVisibility(View.VISIBLE);
    }

    private boolean onLink(View view) {
        LinkDialog.show(this);
        return true;
    }

    private void onFilter(View view) {
        if (mAdapter.getItemCount() > 0) FilterDialog.create().filter(mAdapter.get(mBinding.pager.getCurrentItem()).getFilters()).show(this);
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.search) SearchActivity.start(requireActivity());
        else if (item.getItemId() == R.id.history) HistoryActivity.start(requireActivity());
        return true;
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
    }

    private void hideContent() {
        mBinding.type.setVisibility(View.INVISIBLE);
        mBinding.pager.setVisibility(View.INVISIBLE);
    }

    private void showContent() {
        mBinding.type.setVisibility(View.VISIBLE);
        mBinding.pager.setVisibility(View.VISIBLE);
    }

    private void homeContent() {
        showProgress();
        setFabVisible(0);
        mAdapter.clear();
        // 清空旧数据时隐藏指示器，避免切换配置期间残留上一个配置的错位大块。
        mBinding.typeIndicator.setVisibility(View.INVISIBLE);
        mViewModel.homeContent();
        mBinding.pager.setAdapter(new PageAdapter(getChildFragmentManager()));
    }

    public Result getResult() {
        return mResult == null ? new Result() : mResult;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
            case SIZE:
                homeContent();
                break;
            case CATEGORY:
                getFragment().onRefresh();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStateEvent(StateEvent event) {
        switch (event.type()) {
            case EMPTY:
                hideProgress();
                break;
            case PROGRESS:
                showProgress();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        ReceiveDialog.create().event(event).show(this);
    }

    @Override
    public void setConfig(Config config) {
        VodConfig.load(config, new Callback() {
            @Override
            public void start() {
                showProgress();
                hideContent();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
                showContent();
            }
        });
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void onItemClick(int position, Class item) {
        mBinding.pager.setCurrentItem(position);
        mAdapter.setSelected(position);
        mTypePosition = position;
        updateTypeIndicator(true);
    }

    @Override
    public void setFilter(String key, Value value) {
        getFragment().setFilter(key, value);
    }

    @Override
    public boolean canBack() {
        if (mBinding.pager.getAdapter() == null || mBinding.pager.getAdapter().getCount() == 0) return true;
        if (!getFragment().canBack()) return true;
        getFragment().goBack();
        return false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mIndicatorAnim != null) mIndicatorAnim.cancel();
        EventBus.getDefault().unregister(this);
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = mAdapter.get(position);
            return FolderFragment.newInstance(getHome().getKey(), type, 4);
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }
    }
}
