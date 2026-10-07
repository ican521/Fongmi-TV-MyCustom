package com.fongmi.android.tv;

import android.text.TextUtils;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class Updater implements Download.Callback, UpdateListener {

    private Download download;
    private UpdateDialog dialog;
    private String apkUrl;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getApkName() {
        return BuildConfig.FLAVOR + "-" + (android.os.Process.is64Bit() ? "arm64_v8a" : "armeabi_v7a") + ".apk";
    }

    public Updater force() {
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        if (!Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity));
    }

    private void doInBackground(FragmentActivity activity) {
        try {
            JSONObject object = new JSONObject(OkHttp.string(Github.getRelease()));
            // tag_name 约定为 vX.Y.Z 语义化版本（与 versionName 比较），例如 v2.1.0。
            String remote = object.optString("tag_name");
            if (!isNewer(remote, BuildConfig.VERSION_NAME)) return;
            String name = object.optString("name");
            if (TextUtils.isEmpty(name)) name = object.optString("tag_name");
            final String version = name;
            String desc = object.optString("body");
            apkUrl = findApkUrl(object.optJSONArray("assets"), getApkName());
            if (TextUtils.isEmpty(apkUrl)) return;
            App.post(() -> show(activity, version, desc));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 语义化版本比较：remote 大于 local 时返回 true（逐段比较 major.minor.patch，缺段补 0）。 */
    private boolean isNewer(String remote, String local) {
        int[] r = parseVersion(remote);
        int[] l = parseVersion(local);
        for (int i = 0; i < 3; i++) {
            if (r[i] != l[i]) return r[i] > l[i];
        }
        return false;
    }

    private int[] parseVersion(String value) {
        int[] parts = new int[]{0, 0, 0};
        if (TextUtils.isEmpty(value)) return parts;
        // 兼容 "v2.1.0" / "V2.1.0" / "2.1" / "2" 等写法。
        String cleaned = value.trim().replaceFirst("^[vV]", "");
        String[] segs = cleaned.split("\\.");
        for (int i = 0; i < Math.min(segs.length, 3); i++) {
            try {
                parts[i] = Integer.parseInt(segs[i].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return parts;
    }

    private String findApkUrl(JSONArray assets, String apkName) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            if (apkName.equals(asset.optString("name"))) {
                return asset.optString("browser_download_url");
            }
        }
        return null;
    }

    private void show(FragmentActivity activity, String version, String desc) {
        dismiss();
        dialog = UpdateDialog.create().title(ResUtil.getString(R.string.update_version, version)).desc(desc).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        if (TextUtils.isEmpty(apkUrl)) {
            error("未找到匹配的安装包");
            return;
        }
        download = Download.create(apkUrl, getFile());
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        if (download != null) download.cancel();
        dismiss();
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        if (dialog != null) dialog.setProgress(progress);
    }

    @Override
    public void error(String msg) {
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        FileUtil.openFile(file);
        dismiss();
    }
}
