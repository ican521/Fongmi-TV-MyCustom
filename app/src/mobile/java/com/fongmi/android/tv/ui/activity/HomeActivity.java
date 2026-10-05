package com.fongmi.android.tv.ui.activity;

import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.BackupManager;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.RevealEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.event.StateEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.receiver.ShortcutReceiver;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CapsuleBottomBar;
import com.fongmi.android.tv.ui.custom.FragmentStateManager;
import com.fongmi.android.tv.ui.fragment.KeepFragment;
import com.fongmi.android.tv.ui.fragment.SettingFragment;
import com.fongmi.android.tv.ui.fragment.SettingPlayerFragment;
import com.fongmi.android.tv.ui.fragment.SettingPreloadFragment;
import com.fongmi.android.tv.ui.fragment.SettingDecodeFragment;
import com.fongmi.android.tv.ui.fragment.VodFragment;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HomeActivity extends BaseActivity implements CapsuleBottomBar.OnTabSelectedListener {

    private FragmentStateManager mManager;
    private ActivityHomeBinding mBinding;
    private ViewPager2 mPager;
    private int orientation;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        orientation = getResources().getConfiguration().orientation;
        mBinding.navigation.setOnTabSelectedListener(this);
        PermissionUtil.requestNotify(this);
        initFragment(savedInstanceState);
        Updater.create().start(this);
        initConfig();
    }

    @Override
    protected void initEvent() {
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void checkType(Intent intent) {
        FileChooser.getUri(intent, uri -> VideoActivity.file(this, uri));
    }

    private void initFragment(Bundle savedInstanceState) {
        mPager = mBinding.container;
        // 根布局 fitsSystemWindows=false，不再给系统栏强制留白；这里仅把顶部状态栏 inset
        // 作为内容区的上 padding，保证工具栏不被状态栏遮挡，列表仍可一直铺到屏幕最底端。
        ViewCompat.setOnApplyWindowInsetsListener(mPager, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), 0);
            return insets;
        });
        mPager.setUserInputEnabled(false);
        mPager.setAdapter(new HomePagerAdapter(getSupportFragmentManager(), getLifecycle()));
        mPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                mBinding.navigation.setSelected(position, true);
            }
        });
        // 设置子页面（3/4/5）走覆盖层 overlay，与 ViewPager2 的主页面互不干扰
        mManager = new FragmentStateManager(mBinding.overlay, getSupportFragmentManager(), position -> switch (position) {
            case 3 -> SettingPlayerFragment.newInstance();
            case 4 -> SettingPreloadFragment.newInstance();
            case 5 -> SettingDecodeFragment.newInstance();
            default -> null;
        });
        mPager.setCurrentItem(0, false);
        mBinding.navigation.setSelected(0, false);
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        WallConfig.get().init();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                checkAction(getIntent());
                revealNavigation();
            }

            @Override
            public void error(String msg) {
                checkAction(getIntent());
                revealNavigation();
                StateEvent.empty();
                Notify.show(msg);
            }
        };
    }

    /** 加载完成后让胶囊底栏从屏幕底部之外向上平移进入，同时触发右下角按钮的入场动画。 */
    private void revealNavigation() {
        if (mBinding != null && mBinding.navigation != null) mBinding.navigation.reveal();
        RevealEvent.post();
    }

    private void setNavigation() {
        mBinding.navigation.setTabVisible(R.id.vod, true);
        mBinding.navigation.setTabVisible(R.id.keep, true);
        mBinding.navigation.setTabVisible(R.id.setting, true);
    }

    public void change(int position) {
        if (position < 3) {
            mManager.clear();
            mBinding.overlay.setVisibility(View.GONE);
            mPager.setCurrentItem(position, true);
            mBinding.navigation.setSelected(position, true);
        } else {
            mManager.change(position);
            mBinding.overlay.setVisibility(View.VISIBLE);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.home();
                break;
            case COMMON:
                setNavigation();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.THEME) recreate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() == ServerEvent.Type.PUSH) VideoActivity.push(this, event.text());
        if (event.type() == ServerEvent.Type.SEARCH) SearchActivity.start(this, event.text());
    }

    @Override
    public void onTabSelected(int index) {
        change(index);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        App.post(() -> checkOrientation(newConfig), 100);
    }

    private void checkOrientation(Configuration newConfig) {
        if (orientation != newConfig.orientation) {
            orientation = newConfig.orientation;
            RefreshEvent.home();
        }
    }

    @Override
    protected void onBackInvoked() {
        if (!mBinding.navigation.isTabVisible(R.id.vod)) {
            setNavigation();
        } else if (mManager.isVisible(5) || mManager.isVisible(4) || mManager.isVisible(3)) {
            change(2);
        } else if (mPager.getCurrentItem() == 2) {
            change(0);
        } else if (mPager.getCurrentItem() == 1) {
            if (canBackCurrent()) change(0);
        } else if (canBackCurrent()) {
            if (PlaybackService.isRunning()) Util.moveToBackground(this);
            else super.onBackInvoked();
        }
    }

    private boolean canBackCurrent() {
        BaseFragment fragment = currentFragment();
        return fragment != null && fragment.canBack();
    }

    private BaseFragment currentFragment() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + mPager.getCurrentItem());
        return fragment instanceof BaseFragment ? (BaseFragment) fragment : null;
    }

    @Override
    protected void onDestroy() {
        VodConfig.get().clear();
        BackupManager.backup();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        super.onDestroy();
    }

    /** 底部三个主页面的 ViewPager2 适配器，切换时自带左右平移动画。 */
    private static class HomePagerAdapter extends FragmentStateAdapter {

        HomePagerAdapter(FragmentManager fragmentManager, Lifecycle lifecycle) {
            super(fragmentManager, lifecycle);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return switch (position) {
                case 0 -> VodFragment.newInstance();
                case 1 -> KeepFragment.newInstance();
                default -> SettingFragment.newInstance();
            };
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}
