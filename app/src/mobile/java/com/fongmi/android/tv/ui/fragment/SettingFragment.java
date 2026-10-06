package com.fongmi.android.tv.ui.fragment;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.FragmentSettingBinding;
import com.fongmi.android.tv.db.BackupManager;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.SettingActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.google.android.material.textview.MaterialTextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class SettingFragment extends BaseFragment implements ConfigListener, SiteListener {

    private FragmentSettingBinding mBinding;
    private String[] size;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        setRowPressBackground(mBinding.sizeRow, true, false);
        setRowPressBackground(mBinding.dohRow, false, true);
        setOtherText();
        setCacheText();
    }

    /**
     * 合并卡片内各行的按压覆盖层：圆角跟随卡片（上一行顶部圆角、下一行底部圆角），
     * 避免矩形按压层超出 14dp 卡片圆角。
     */
    private void setRowPressBackground(View row, boolean topRound, boolean bottomRound) {
        float r = ResUtil.dp2px(14);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(ResUtil.getColor(R.color.press_overlay));
        pressed.setCornerRadii(new float[]{
                topRound ? r : 0, topRound ? r : 0,
                topRound ? r : 0, topRound ? r : 0,
                bottomRound ? r : 0, bottomRound ? r : 0,
                bottomRound ? r : 0, bottomRound ? r : 0
        });
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed}, pressed);
        background.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        row.setBackground(background);
    }

    private void setOtherText() {
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoSwitch.setChecked(Setting.isIncognito());
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[PlayerSetting.getSize()]);
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.wall.setOnLongClickListener(this::onWallEdit);
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.sizeRow.setOnClickListener(v -> showSizeMenu());
        mBinding.dohRow.setOnClickListener(v -> showDohMenu());
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> load(config));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 2:
                Setting.putWall(0);
                WallConfig.load(config, getCallback());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    private void onVod(View view) {
        ConfigDialog.create().vod().show(this);
    }

    private void onWall(View view) {
        ConfigDialog.create().wall().show(this);
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create().vod().edit().show(this);
        return true;
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create().wall().edit().show(this);
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create().search().change().show(this);
    }

    private void onVodHistory(View view) {
        HistoryDialog.create().vod().show(this);
    }

    private void onPlayer(View view) {
        SettingActivity.start(requireActivity(), SettingActivity.PLAYER);
    }

    private void onVersion(View view) {
        Updater.create().force().start(requireActivity());
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoSwitch.setChecked(Setting.isIncognito());
    }

    private void showSizeMenu() {
        showOptionMenu(mBinding.sizeToggle, size, PlayerSetting.getSize(), index -> {
            PlayerSetting.putSize(index);
            mBinding.sizeText.setText(size[index]);
            RefreshEvent.size();
        });
    }

    private void showDohMenu() {
        List<Doh> list = VodConfig.get().getDoh();
        String[] items = new String[list.size()];
        for (int i = 0; i < list.size(); i++) items[i] = list.get(i).getName();
        showOptionMenu(mBinding.dohToggle, items, getDohIndex(), index -> setDoh(list.get(index)));
    }

    /**
     * 照搬参考项目 SegmentedDropdownItem 的交互：点击后在箭头处弹出深色圆角菜单，
     * 当前选项以主题主色文字 + 同色对勾标记，其余为白色文字，点外部关闭。
     */
    private void showOptionMenu(View anchor, String[] items, int selectedIndex, OnOptionPicked listener) {
        TypedValue value = new TypedValue();
        requireContext().getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true);
        int colorPrimary = value.data;

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.shape_menu_bg);
        int pad = ResUtil.dp2px(8);
        container.setPadding(pad, pad, pad, pad);

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            boolean selected = i == selectedIndex;

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(ResUtil.dp2px(48));
            row.setBackgroundResource(R.drawable.shape_menu_item);
            int padH = ResUtil.dp2px(14);
            row.setPadding(padH, 0, padH, 0);

            MaterialTextView text = new MaterialTextView(requireContext());
            text.setText(items[i]);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            text.setTextColor(selected ? colorPrimary : Color.WHITE);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textParams.setMarginEnd(ResUtil.dp2px(20));
            row.addView(text, textParams);

            AppCompatImageView check = new AppCompatImageView(requireContext());
            check.setImageResource(R.drawable.ic_action_check);
            check.setColorFilter(colorPrimary);
            check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            int checkSize = ResUtil.dp2px(20);
            row.addView(check, new LinearLayout.LayoutParams(checkSize, checkSize));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            container.addView(row, rowParams);
        }

        PopupWindow popup = new PopupWindow(container, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(ResUtil.dp2px(8));
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(android.R.style.Animation_Dialog);

        for (int i = 0; i < container.getChildCount(); i++) {
            final int index = i;
            container.getChildAt(i).setOnClickListener(v -> {
                popup.dismiss();
                listener.onPicked(index);
            });
        }

        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int gap = ResUtil.dp2px(4);
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int spaceBelow = ResUtil.getScreenHeight(requireContext()) - location[1] - anchor.getHeight();
        if (spaceBelow >= container.getMeasuredHeight() + gap) {
            popup.showAsDropDown(anchor, 0, gap, Gravity.END);
        } else {
            popup.showAsDropDown(anchor, 0, -(anchor.getHeight() + container.getMeasuredHeight() + gap), Gravity.END);
        }
    }

    private interface OnOptionPicked {
        void onPicked(int index);
    }

    private void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
    }

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
            }
        });
    }

    private void onBackup(View view) {
        PermissionUtil.requestFile(this, allGranted -> BackupManager.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
            }
        }));
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().show(requireActivity(), new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                setOtherText();
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }));
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        WallConfig.get().init().load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() != ConfigEvent.Type.COMMON) return;
        mBinding.vodUrl.setText(VodConfig.getDesc());
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) return;
        setCacheText();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }
}
