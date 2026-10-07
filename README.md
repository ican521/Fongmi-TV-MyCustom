# 影视TV

适用于 Android TV 与手机的影音应用，整合媒体浏览与播放体验，并支持外部配置与 [CatVod](https://github.com/CatVodTVOfficial/CatVodTVJarLoader) Spider 接口扩展。

**App 本身不内置或提供任何内容来源。** 外部内容需自行配置，也可打开本地媒体文件或推送媒体网址。

[使用与开发指南](https://fongmi.github.io/TV/) · [讨论群组](https://t.me/fongmi_official)

## 开始使用

1. 安装适合设备的 APK：`leanback` 为电视版，`mobile` 为手机版；按 Android 系统支持的 ABI 选择 `arm64-v8a` 或 `armeabi-v7a`。最低要求为 Android 7.0（API 24）。
2. 在设置中添加自己的配置，格式与字段见[配置示例](https://fongmi.github.io/TV/config/#examples)。
3. 也可从系统文件管理器打开媒体文件，或通过推送入口播放媒体网址。

## 主要功能

- **播放**：Media3／ExoPlayer、mpv、硬解与 FFmpeg 软解；字幕、弹幕、音轨、倍速与片头／片尾跳过。
- **浏览与管理**：分类筛选、搜索、播放记录、收藏与无痕模式。
- **播放列表**：M3U／TXT／JSON 格式、列表分组与 XMLTV 节目信息。
- **操作**：电视遥控器、手机手势、画中画与后台音频。
- **互联互通**：DLNA 投放／接收、Android Auto、本地 HTTP 控制与设备同步。

实际能力因配置、媒体、播放引擎与设备而异；本地 HTTP API 仅供可信的局域网使用，不要直接转发到公网。

## 开发文档

| 文档 | 内容 |
| --- | --- |
| [App 功能](https://fongmi.github.io/TV/features/) | 操作与功能介绍 |
| [配置字典](https://fongmi.github.io/TV/config/) | 配置字段、网络设置与 JSON 示例 |
| [扩展对接](https://fongmi.github.io/TV/spider/) | Java／Python／JavaScript 示例、方法与返回格式 |
| [本地 API](https://fongmi.github.io/TV/local/) | 播放控制、推送、文件与同步端点 |
| [网站维护](website/README.md) | 静态网站构建与 GitHub Pages 发布 |

`app/src/main/` 为共用逻辑，`app/src/leanback/`、`app/src/mobile/` 为各自的 UI。模块清单见 [settings.gradle](settings.gradle)，SDK 与依赖版本见 [libs.versions.toml](gradle/libs.versions.toml)。

## Windows 构建

先准备以下环境与文件：

- **JDK 21、Android SDK、Python 3.10**。SDK 平台版本按 `compileSdk` 设置；Python 可用 `py -3.10 --version` 确认，找不到时在 [chaquo/build.gradle](chaquo/build.gradle) 的 Python 区块设置 `buildPython`。
- **配套 AAR**：放入 `app/libs/`。`lib-*.aar` 未纳入 Git，仅 clone 不包含完整的播放器依赖。
- **自己的签名文件与 `local.properties`**：在仓库根目录创建如下配置，将所有示例值替换成自己的信息。

```properties
sdk.dir=C:/Android/Sdk
storeFile=C:/keys/yingshi-tv.jks
keyAlias=your-key-alias
storePassword=your-keystore-password
```

密钥密码与 keystore 密码共用 `storePassword`；不要提交签名文件或真实密码。

在仓库根目录用 PowerShell 运行：

```powershell
# 电视版
.\gradlew.bat :app:assembleLeanbackRelease

# 手机版
.\gradlew.bat :app:assembleMobileRelease
```

APK 按 ABI 分包并输出到 `Release/apk/`。签名不同的 APK 不能直接覆盖已有安装。网站位于 `website/`，可独立构建，无需编译 Android App。

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=FongMi/TV&type=Date)](https://www.star-history.com/#FongMi/TV&Date)
