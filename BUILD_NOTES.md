# FongMi/TV 构建说明（BUILD_NOTES）

本工程源码依赖一套**定制版 media3**（带 libass / 弹幕 / mpv / preload 等特性），该定制 media3 的部分模块（libass、SecondaryText 等）未公开。为使工程可编译，当前采用「**保留自编译 media3 + 补空壳类**」的方案，已验证可成功构建并在真机正常播放。

---

## 1. 环境要求

| 工具 | 路径 / 版本 |
|------|-------------|
| JDK 21 | `d:\TV-fongmi\jbrsdk_jcef-21.0.10-windows-x64-b1163.110`（JetBrains Runtime） |
| Android SDK | `C:\Users\15791\AppData\Local\Android\Sdk`（platforms: android-36 / android-37.0；compileSdk=37, minSdk=24, targetSdk=37） |
| Python 3.10 | `C:\Users\15791\AppData\Local\Programs\Python\Python310`（Chaquopy 需要，`py -3.10` 可用） |
| Gradle 9.1.0 | 本地 zip：`d:\TV-fongmi\gradle-9.1.0-all.zip`（wrapper 指向它，避免在线下载超时） |

`local.properties`（工程根目录）需包含：
```
sdk.dir=C\:\\Users\\15791\\AppData\\Local\\Android\\Sdk
# 签名占位（debug 构建不依赖真实 keystore）
storeFile=release.keystore
keyAlias=android
```

---

## 2. 为成功构建所做的改动

### 2.1 自编译 media3 AAR（已放入 app/libs/）
从 `FongMi/media` 分支 `release-1.11.0-fongmi` 编译得到 20 个 release AAR，与原有的 5 个（forcetech/hook/jianpian/thunder/tvbus）一同放在 `app/libs/`，由 `app/build.gradle` 的 `fileTree(dir: "libs", include: ["*.aar"])` 自动引入。

### 2.2 空壳类（libass / 弹幕 / 副字幕，运行时 no-op）
位于 `app/src/main/java/androidx/media3/`：
- `exoplayer/libass/LibassConfiguration.java`（含 Builder）
- `exoplayer/libass/LibassPlaybackSession.java`（`isAvailable()` 恒返回 false，关闭 libass 路径）
- `exoplayer/libass/LibassSubtitleController.java`
- `exoplayer/libass/LibassFontFile.java`（`getFamilyName` 返回 null，声明 throws IOException）
- `ui/libass/LibassPlayerViewController.java`
- `ui/danmaku/DanmakuPlayerViewController.java`
- `exoplayer/text/SecondaryTextOutput.java`（实现 TextOutput.onCues(CueGroup)）
- `exoplayer/trackselection/SecondaryTextTrackSelector.java`（嵌套 Factory 委托 delegate）

> 注意包路径：`SubtitleParser` 在 `androidx.media3.extractor.text`，`CueGroup` 在 `androidx.media3.common.text`，`TextOutput` 在 `androidx.media3.exoplayer.text`。

### 2.3 调用点修正（副字幕/字体设为 no-op）
- `app/src/main/java/com/fongmi/android/tv/player/mpv/MpvPlayerEngine.java`
  - `getSecondarySubtitleState()` 返回 `new SecondarySubtitleState(null, null, List.of(), false)`
  - `setSecondarySubtitleSelection(...)` 与 `applySecondarySubtitleMode(...)` 置空
- `app/src/main/java/com/fongmi/android/tv/player/mpv/MpvUtil.java`
  - `buildSubtitleOptions()` 移除 `setSecondarySubtitlePosition` / `setSecondaryAssStyleOverride` / `setFontFamily` / `setFontsDirectory`（这些方法在当前 AAR 的 `MpvSubtitleOptions.Builder` 上不存在）

### 2.4 新增依赖
`app/build.gradle` 增加：
```gradle
implementation "androidx.mediarouter:mediarouter:1.8.1"
```
解决 `lib-cast` AAR 中 `media_route_button_view.xml` 引用 `mediaRouteButtonTint` 属性找不到的资源链接错误。

---

## 3. 构建命令（Windows PowerShell）

```powershell
$env:JAVA_HOME="d:\TV-fongmi\jbrsdk_jcef-21.0.10-windows-x64-b1163.110"
$env:ANDROID_HOME="C:\Users\15791\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:Path = "C:\Users\15791\AppData\Local\Programs\Python\Python310;C:\Users\15791\AppData\Local\Programs\Python\Python310\Scripts;" + $env:Path
cd d:\TV-fongmi
.\gradlew.bat :app:assembleMobileDebug
```

> PowerShell 执行策略会报 CLIXML 配置文件加载错误（仅外观警告，不影响构建）；务必用 `.\gradlew.bat`。多任务时用数组 splat：`& .\gradlew.bat @tasks`。

---

## 4. 产物

构建成功后 APK 位于：
```
app/build/outputs/apk/mobile/debug/
  app-mobile-arm64-v8a-debug.apk   # 主推，现代手机/电视（约 109 MB）
  app-mobile-armeabi-v7a-debug.apk # 老旧 32 位设备（约 97 MB）
```

---

## 5. 已知限制

- **ASS/SSA 字幕渲染**：libass 为空壳，实际走 media3 默认字幕管线（实测普通字幕可用）。
- 若未来拿到完整定制 media3（含 libass），可删除第 2.2 / 2.3 节的空壳与修正，恢复原生实现。

---

## 6. 功能删除记录（直播 / 弹幕）

按需求**仅针对 mobile 构建变体**（不影响 leanback，leanback 不参与 mobile 编译）彻底移除「直播（IPTV）」与「弹幕」两个功能。删除后 `:app:compileMobileDebugJavaWithJavac` 与 `:app:assembleMobileDebug` 均 **BUILD SUCCESSFUL**。

### 6.1 删除「直播（IPTV）」

**删除的专用文件（约 24 个）：**
- bean：`Live.java`、`Channel.java`、`Epg.java`、`Group.java`
- api：`LiveApi.java`、`config/LiveConfig.java`、`parser/LiveParser.java`、`parser/EpgParser.java`
- db：`dao/LiveDao.java`
- model：`LiveViewModel.java`
- browse：`LiveBrowse.java`
- impl：`LiveListener.java`
- playback/live：`LiveDataSource`、`LivePlayRequest`、`LivePlaybackController`、`LivePlaybackHost`、`LivePlaybackState`
- player/extractor：`TVBus.java`（tvbus:// 直播 P2P，依赖 LiveConfig）
- mobile：`ui/activity/LiveActivity.java`、`ui/adapter/LiveAdapter.java`、`ui/adapter/ChannelAdapter.java`、`ui/adapter/GroupAdapter.java`、`ui/dialog/LiveDialog.java`、`res/layout/activity_live.xml`、`res/layout/adapter_live.xml`

**修改的共享文件（移除直播引用）：**
- `api/loader/BaseLoader.java` — `getSpider` 去掉 live 分支
- `bean/Backup.java` — 移除 live 字段/方法及 DB 读写
- `db/AppDatabase.java` — `@Database(entities=...)` 移除 `Live.class`，移除 `getLiveDao()`；保留 VERSION，靠 `fallbackToDestructiveMigration(true)` 处理 schema 变化
- `player/extractor/Source.java` — 移除 `extractors.add(new TVBus())`
- `server/Nano.java` — 移除 `/tvbus` 路由及 LiveConfig import
- `api/config/RuleConfig.java` — `merge()` 不再合并 LiveConfig 的 ads/rules
- `api/config/VodConfig.java` — 移除 `initLive()` 及其调用
- `browse/BrowseTree.java` — 移除 LIVE 浏览节点、`clearLive()`、LiveBrowse 分支
- `service/PlaybackService.java` — 移除 `else if (event.isLive())` 分支
- mobile `HomeActivity.java` — 移除直播入口、`loadLive/openLive/addShortcut`、BOOT 分支、`LiveConfig.get().clear()`、`R.id.live` 导航
- mobile `ConfigDialog.java` — 移除 `live()`、case 1、`R.string.setting_live`
- mobile `SettingFragment.java` — 移除 live 相关实现（LiveConfig/Live/LiveListener/LiveDialog）
- mobile `AndroidManifest.xml` — 移除 LiveActivity 注册

**保留**：`LiveSetting.java`（被 `CustomKeyDown`/`ConfigEvent` 引用）。leanback 端直播代码未动。

### 6.2 删除「弹幕」

**删除的专用文件（约 15 个）：**
- main：`bean/Danmaku.java`、`bean/DanmakuData.java`、`api/DanmakuApi.java`、`impl/DanmakuListener.java`、`setting/DanmakuSetting.java`、`ui/dialog/DanmakuDialog.java`、`ui/dialog/DanmakuSearchDialog.java`、`ui/dialog/DanmakuSettingDialog.java`、`ui/dialog/DanmakuSettingPanel.java`、`ui/adapter/DanmakuAdapter.java`、`gson/DanmakuAdapter.java`
- `ui/danmaku/DanmakuPlayerViewController.java`（原 2.2 节空壳桩）
- mobile：`ui/fragment/SettingDanmakuFragment.java`、`res/layout/fragment_setting_danmaku.xml`、`ui/dialog/DanmakuApiDialog.java`

**修改的共享/引用文件：**
- `player/media/PlaySpec.java` — 移除 `danmakus` 字段及全部弹幕方法、构造参数、`getDanmaku()` 调用
- `bean/Result.java` — 移除 `danmaku` 字段、`getDanmaku()`、相关 import
- `playback/vod/VodPlaybackMedia.java` — 移除 `searchDanmaku()`
- `player/PlayerManager.java` — 移除 `danmakuEnabled`、全部弹幕方法、`Callback` 接口的 4 个弹幕方法、`onPlayerError` 中弹幕恢复分支
- `ui/activity/PlaybackActivity.java` — 移除 `danmakuController` 字段、`syncDanmakuSource()`、匿名 Callback 的 4 个弹幕方法、相关可见性行
- `playback/vod/VodPlaybackHost.java` / `VodPlaybackController.java` — 移除 `loadDanmaku(...)` 接口方法与调用
- `server/process/Action.java` — 移除网页遥控 `danmaku` 动作分发与 `onDanmaku()`
- `service/PlaybackService.java` — 移除 4 个弹幕回调转发方法
- mobile `HomeActivity.java` — 移除 SettingDanmakuFragment，并将 Preload/Decode 序号前移（3=Preload, 4=Decode）
- mobile `SettingFragment.java` — 移除弹幕设置入口 `onDanmaku()`
- mobile `SettingPlayerFragment.java` — 同步序号（`change(3)`/`change(4)`）
- mobile `VideoActivity.java` — 移除弹幕按钮、搜索/加载/发送、`DanmakuApi.cancel()`、PIP 相关弹幕调用

**注意**：`DanmakuConfig`（`androidx.media3.ui.danmaku`）来自 `app/libs` 内 AAR，未删除。`Config.java`/`Site.java` 中保留 `danmaku` 字段（配置解析用，无害）、`RefreshEvent.danmaku()`/`Action.java` 的 `case "danmaku"` 分发仍在（无接收方，无害）。leanback 端弹幕代码未动。残留资源清理见第 7 节。

### 6.3 影响
- 首页无「直播」入口，设置无「弹幕设置」，播放页无弹幕按钮。
- mobile APK 已重新产出（见第 4 节）。
- 若需恢复任一功能，按上述清单反向补回文件与引用即可。

---

## 7. 残留资源清理（直播 / 弹幕）

第 6 节删除代码后，仍有未被引用的布局、字符串、drawable、遥控网页残留。本节记录对这些**资源**的清理（仍仅限 mobile + main，leanback 不动）。清理后 `:app:assembleMobileDebug` **BUILD SUCCESSFUL**，APK 已更新。

### 7.1 删除的资源

**布局（main + mobile）：**
- main：`view_danmaku_appearance.xml`、`view_danmaku_display.xml`、`view_danmaku_density.xml`、`view_danmaku_timing.xml`、`dialog_danmaku_setting.xml`
- mobile：`dialog_danmaku.xml`、`dialog_danmaku_search.xml`、`adapter_danmaku.xml`
- mobile 控制栏弹幕视图：`dialog_control.xml`、`view_control_vod.xml`、`view_control_vod_action.xml` 中的 `@+id/danmaku`（同步删 `ControlDialog.java` 里对 `binding.danmaku` / `parent.control.action.danmaku` 的引用）
- mobile 设置页：`fragment_setting.xml` 的直播行（`@+id/live`/`liveUrl`/`liveHome`/`liveHistory`）与弹幕行（`@+id/danmaku`）；`menu_nav.xml` 的 `@+id/live` 项

**字符串（6 个 strings.xml，各删约 51 行）：**
`app/src/{main,mobile}/res/{values,values-zh-rCN,values-zh-rTW}/strings.xml` 中全部 `danmaku*`、`setting_danmaku` 字符串及其 `<!-- Danmaku -->` 注释。
> **保留**：`live*` / `setting_live` / `nav_live` 等直播字符串，因 leanback 端 `LiveActivity` 仍引用（mobile 编译不含 leanback，但保留可避免破坏 leanback 构建）。

**drawable：**
- 删：`ic_nav_live.xml`、`ic_control_danmaku_on.xml`、`ic_control_danmaku_off.xml`、`ic_danmaku_setting.xml`（mobile）
- **保留并重建**：`shape_danmaku_search_keyword.xml`（被 `dialog_subtitle_search.xml` 字幕搜索复用，不能删；已重建为圆角描边背景）

**遥控网页（`app/src/main/assets/`）：**
- `index.html`：删除弹幕面板 `panel3`、弹幕 tab、弹幕模式/大小两个 dialog，并把原 `panel4`(設定)→`panel3`、`panel5`(本地)→`panel4`，tab 同步重排为 4 个（搜尋/推送/設定/本地）
- `js/script.js`：删除 `danmakuMode`/`danmakuSize` 变量、`sendDanmaku/showDanmakuModeDialog/setDanmakuMode/showDanmakuSizeDialog/setDanmakuSize` 函数、`#danmaku_text` 的 keydown 绑定；`showPanel` 循环上限 `5→4`，本地面板判定 `id===5→id===4`

### 7.2 过程中的坑与修复
1. **PowerShell `Set-Content` 写入 BOM**：批量删 string 行时用 `Set-Content` 重写 `fragment_setting.xml` / 各 `strings.xml`，引入了 UTF-8 BOM，导致 aapt 报 `line 1:0 mismatched input '﻿'`、`root is null`，资源合并失败。
   - 修复：用 `[System.IO.File]::WriteAllText` + `UTF8Encoding($false)` 重写为**无 BOM** UTF-8，校验首字节为 `3C`（`<`）。
   - 教训：**用 Edit 工具编辑 XML，避免用 PowerShell 重写整个文件**；必须用脚本写时务必无 BOM。
2. **误删被复用的 drawable**：`shape_danmaku_search_keyword` 名字带 danmaku 但被字幕搜索对话框引用，删除后 `processMobileDebugResources` 报 `resource ... not found`。已重建该 drawable。教训：删 drawable 前先全局 grep 确认无任何 `@drawable/` 引用。

### 7.3 仍保留的"弹幕"字样（无害）
- `Config.java` / `Site.java` 的 `danmaku` 字段（解析服务端配置用）
- `RefreshEvent.danmaku()` 与 `Action.java` 的 `case "danmaku"`（网页遥控分发，无接收方）
- `PlaybackService.java` 的 `import androidx.media3.ui.danmaku.DanmakuConfig`（AAR 类）
- `DanmakuConfig` / `DanmakuPlayerViewController` 等 AAR 内类（来自 `app/libs`）
- leanback 端全部直播/弹幕代码与资源（不参与 mobile 编译）
