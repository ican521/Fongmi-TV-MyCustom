package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySettingBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.SettingDecodeFragment;
import com.fongmi.android.tv.ui.fragment.SettingPlayerFragment;
import com.fongmi.android.tv.ui.fragment.SettingPreloadFragment;

/**
 * 设置子页面（播放/预加载/解码）的独立承载 Activity。
 * 打开方式与首页顶部搜索按钮一致（startActivity，默认从右滑入覆盖），
 * 避免之前以覆盖层(overlay)形式出现时无转场动画的问题。
 */
public class SettingActivity extends BaseActivity {

    private static final String POSITION = "position";

    public static final int PLAYER = 3;
    public static final int PRELOAD = 4;
    public static final int DECODE = 5;

    public static void start(Activity activity, int position) {
        Intent intent = new Intent(activity, SettingActivity.class);
        intent.putExtra(POSITION, position);
        activity.startActivity(intent);
    }

    private int getPosition() {
        return getIntent().getIntExtra(POSITION, PLAYER);
    }

    @Override
    protected ViewBinding getBinding() {
        return ActivitySettingBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.container, fragmentFor(getPosition())).commit();
        }
    }

    private Fragment fragmentFor(int position) {
        switch (position) {
            case PRELOAD:
                return SettingPreloadFragment.newInstance();
            case DECODE:
                return SettingDecodeFragment.newInstance();
            case PLAYER:
            default:
                return SettingPlayerFragment.newInstance();
        }
    }
}
