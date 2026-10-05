package com.fongmi.android.tv.ui.fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.animation.ValueAnimator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.fongmi.android.tv.event.ConfigEvent;
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
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LinkDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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

    public static VodFragment newInstance() {
        return new VodFragment();
    }

    private FolderFragment getFragment() {
        return (FolderFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
    }

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentVodBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        mBinding.title.setSelected(true);
        setRecyclerView();
        setViewModel();
        initFabEnter();
        initAppBarEnter();
        showProgress();
        setTitle();
        setLogo();
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
        mBinding.logo.setOnClickListener(this::onLogo);
        mBinding.link.setOnClickListener(this::onLink);
        mBinding.title.setOnClickListener(this::onSite);
        mBinding.filter.setOnClickListener(this::onFilter);
        mBinding.filter.setOnLongClickListener(this::onLink);
        mBinding.toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
        mBinding.appBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            float factor = Math.abs(verticalOffset * 1f / appBarLayout.getTotalScrollRange());
            int padding = (int) (ResUtil.dp2px(12) * factor);
            if (mBinding.type.getPaddingTop() == padding) return;
            mBinding.type.setPadding(mBinding.type.getPaddingStart(), padding, mBinding.type.getPaddingEnd(), mBinding.type.getPaddingBottom());
        });
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
                updateTypeIndicator(false);
            }
        });
        mBinding.type.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateTypeIndicator(false));
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
    }

    /**
     * 顶部分类页签的滑动指示器：把胶囊背景平滑移动/缩放到当前选中页签的位置与尺寸。
     *
     * @param animate true=切换页签时带动画；false=滚动/布局变化时实时跟随（无动画）。
     */
    private void updateTypeIndicator(boolean animate) {
        if (mBinding == null) return;
        RecyclerView rv = mBinding.type;
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return;
        View target = ((LinearLayoutManager) lm).findViewByPosition(mTypePosition);
        if (target == null || target.getWidth() == 0) return;
        View indicator = mBinding.typeIndicator;
        int left = target.getLeft();
        int top = target.getTop();
        int width = target.getWidth();
        int height = target.getHeight();
        ViewGroup.LayoutParams lp = indicator.getLayoutParams();
        indicator.animate().cancel();
        if (indicator.getVisibility() != View.VISIBLE) indicator.setVisibility(View.VISIBLE);
        if (!animate) {
            if (lp.width != width || lp.height != height) {
                lp.width = width;
                lp.height = height;
                indicator.setLayoutParams(lp);
            }
            indicator.setTranslationX(left);
            indicator.setTranslationY(top);
            return;
        }
        int fromWidth = lp.width > 0 ? lp.width : width;
        indicator.animate()
                .translationX(left)
                .translationY(top)
                .setDuration(280)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        if (fromWidth != width) {
            ValueAnimator widthAnim = ValueAnimator.ofInt(fromWidth, width);
            widthAnim.addUpdateListener(a -> {
                lp.width = (int) a.getAnimatedValue();
                indicator.setLayoutParams(lp);
            });
            widthAnim.setDuration(280);
            widthAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            widthAnim.start();
        }
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
                // 非线性 + 小幅回弹：tension 1.25 过冲量收小（幅度收敛），800ms 偏慢，错开 90ms 错落。
                v.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setInterpolator(new OvershootInterpolator(1.25f))
                        .setStartDelay(index * 90L)
                        .setDuration(800)
                        .start();
                index++;
            } else {
                // 尚未显示的按钮复位变换，避免之后出现时停在屏幕外。
                v.setTranslationX(0f);
                v.setAlpha(1f);
            }
        }
    }

    /** 顶栏从屏幕上边框之外带回弹地滑入并淡入（与底栏 reveal 同时机，幅度收敛）。 */
    private void revealAppBar() {
        if (appBarEntered || mBinding == null) return;
        appBarEntered = true;
        View bar = mBinding.appBar;
        bar.setTranslationY(-appBarSlidePx);
        bar.setAlpha(0f);
        bar.animate()
                .translationY(0f)
                .alpha(1f)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .setDuration(800)
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

    private void setTitle() {
        List<String> items = Arrays.asList(getHome().getName(), getConfig().getName(), getString(R.string.app_name));
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> mBinding.title.setText(s));
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

    private void onLogo(View view) {
        HistoryDialog.create().vod().readOnly().show(this);
    }

    private void onSite(View view) {
        SiteDialog.create().change().show(this);
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
        mViewModel.homeContent();
        mBinding.pager.setAdapter(new PageAdapter(getChildFragmentManager()));
    }

    public Result getResult() {
        return mResult == null ? new Result() : mResult;
    }

    private void setLogo() {
        ImgUtil.logo(mBinding.logo);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() == ConfigEvent.Type.VOD) setLogo();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                setTitle();
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
                setTitle();
                setLogo();
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
