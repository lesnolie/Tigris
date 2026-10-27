# 软件更新与按需安装

入口：**主屏幕设置 → 应用、投屏与更新**。

## 产品行为

Tigris 本体包含**零份** HGTV 或 MiracastReceiver APK。默认 Dock 和桌面不会加入尚未安装的应用占位图标，也不会抢占用户固定排序。

| 应用 | 独立上游 | 安装文件选择 | 未安装时 | 已安装后 |
|---|---|---|---|---|
| Tigris 桌面 | [lesnolie/Tigris](https://github.com/lesnolie/Tigris) | 匹配当前 Standard / Guard | 桌面本体 | 可检查并更新 |
| 投屏 MiracastReceiver | [weekdayjast/MiracastReceiver](https://github.com/weekdayjast/MiracastReceiver) | 官方 GitHub Latest 的 `miracast-receiver-vX.Y.Z-universal.apk` | **只有点安装后**才下载与安装 | 可打开、更新、移动至 Dock |
| HGTV 红果短剧（第三方） | [pan8664716/HGTV](https://github.com/pan8664716/HGTV) | 官方 GitHub Latest 的 `HGTV_X.Y.Z.apk` | **只有点安装后**才下载与安装 | 可打开、更新、移动至 Dock |

桌面常规应用扫描自动识别已安装的第三方 App；用户可以自己移动到 Dock / 文件夹。不在首页塞默认预装推广图标。

## 安装和更新保障

1. 后台每六小时检查 GitHub Latest **元数据**，但**不下载可选 APK**；自动更新仅处理已安装应用。
2. 用户选择“安装 / 更新”之后，下载 GitHub 正式版 APK。严格匹配资产文件名、发布仓库和 HTTPS 下载域名。
3. 第三方 APK 必须有 SHA-256 发布元数据，下载后再次计算 SHA-256，校验大小、包名、Android 最低版本和签名。
4. 首次安装 MiracastReceiver 要求已记录的官方签名 SHA-256 `ea7f4568203d294bd37acac966d6d707f79147549d24c2244f58a6fbc27a6291`；首次安装 HGTV 要求上游历史发布记录证书 `b237b1d3714a5a010b453e0a51ed32bceac40d8d4207c61bfdf8d6c97a0caeaa`，包名预期 `com.hgtv.star`。如果上游新版本不符，则**阻止安装**，等待重新审查；不能默默忽略。
5. 升级仅允许相同签名且 versionCode 增加。不卸载已有包，不清理 HGTV 收藏/历史/登录状态，也不请求 ROOT。
6. Android PackageInstaller 最终处理安装权限和电视端确认；安装被系统禁止时仅提示用户，没有强行修改固件的替代命令。
7. 投屏和 HGTV 都是独立应用，不获得 Tigris 特权；Tigris 不整合 HGTV 非官方内容接口，也不分发其 APK。

## 来源及限制

HGTV 当前公开仓库主要是使用说明和演示图片，不能据此声称其 Android 完整源码已经过审。该项目是第三方短剧客户端，使用非官方内容接口，与内容平台无关联；协议变更可能导致播放异常。用户自行决定是否安装。HGTV 上游版本更新也可能导致签名、包名或文件名变化，因此引入“失败关闭”检查。此工程尚未经过 HGTV + 投屏在所有电视厂商上的实机验收。

上游来源：
- https://github.com/pan8664716/HGTV/releases
- https://github.com/weekdayjast/MiracastReceiver/releases
