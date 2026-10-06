package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.SearchFragment;

public class SearchActivity extends BaseActivity {

    public static void start(Activity activity) {
        start(activity, "");
    }

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, false);
    }

    /**
     * @param autoFinish true 表示本次搜索只是点播/收藏进入详情前的“中转”：
     *                   从搜索结果进入影片或文件夹后，搜索页自动移出返回栈，
     *                   使用户从详情页一次返回即可回到原页面，而不是先落回搜索页。
     */
    public static void start(Activity activity, String keyword, boolean autoFinish) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        intent.putExtra("autoFinish", autoFinish);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    public boolean isAutoFinish() {
        return getIntent().getBooleanExtra("autoFinish", false);
    }

    @Override
    protected ViewBinding getBinding() {
        return ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.container, SearchFragment.newInstance(getKeyword()), SearchFragment.class.getSimpleName()).commit();
        }
    }

    @Override
    protected void onBackInvoked() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            super.onBackInvoked();
        }
    }
}
