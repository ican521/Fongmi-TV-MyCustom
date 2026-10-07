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

    private static final String EXTRA_KEYWORD = "keyword";
    private static final String EXTRA_AUTO_FINISH = "autoFinish";

    public static void start(Activity activity) {
        start(activity, "");
    }

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, false);
    }

    /**
     * @param autoFinish true 表示本次搜索只是点播/收藏进入详情前的“中转”：
     *                   搜索结果页返回时直接关闭搜索页回到原页面（不回退到搜索输入页），
     *                   从结果进入影片或文件夹后搜索页同样自动移出返回栈。
     */
    public static void start(Activity activity, String keyword, boolean autoFinish) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra(EXTRA_KEYWORD, keyword);
        intent.putExtra(EXTRA_AUTO_FINISH, autoFinish);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        return getIntent().getStringExtra(EXTRA_KEYWORD);
    }

    public boolean isAutoFinish() {
        return getIntent().getBooleanExtra(EXTRA_AUTO_FINISH, false);
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
        // 点播/收藏借道搜索的“中转”场景：结果页返回直接关闭搜索页，
        // 不再回退到 Fragment 栈底的搜索输入页，保证一次返回即回到点播页。
        if (isAutoFinish()) {
            super.onBackInvoked();
            return;
        }
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            super.onBackInvoked();
        }
    }
}
