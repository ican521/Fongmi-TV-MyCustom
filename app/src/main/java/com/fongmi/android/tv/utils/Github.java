package com.fongmi.android.tv.utils;

public class Github {

    // 更新源：本仓库 GitHub Releases 的最新版本接口。
    public static final String REPO = "ican521/Fongmi-TV-MyCustom";
    public static final String URL = "https://api.github.com/repos/" + REPO + "/releases/latest";

    /** 最新 Release 的元数据接口（含 tag_name / body / assets）。 */
    public static String getRelease() {
        return URL;
    }
}
