# Tigris

<p align="center">
  <a href="https://tigris-tv.pages.dev/">
    <img src="website/public/img/favicon.svg" alt="Tigris 官方网站图标" width="112" />
  </a>
</p>

**语言：** [简体中文](README.md) · [English](README.en.md)

把 Android TV 的桌面换成 tvOS 26 的样子：沉底的 Liquid Glass Dock、Top Shelf 式顶部内容、毛玻璃控制中心、风景照片背景和相框屏保。

> 十二生肖开源系列第三个项目：虎。Tigris 是拉丁语和希腊语里的“老虎”。

- 官网：**[tigris-tv.pages.dev](https://tigris-tv.pages.dev)**
- 最新正式版：**[v2.0.14 Release](https://github.com/lesnolie/Tigris/releases/tag/v2.0.14)**（[标准版 APK](https://github.com/lesnolie/Tigris/releases/download/v2.0.14/Tigris-v2.0.14.apk) · [兼容守护版 Guard APK](https://github.com/lesnolie/Tigris/releases/download/v2.0.14/Tigris-guard-v2.0.14.apk) · [SHA-256 校验文件](https://github.com/lesnolie/Tigris/releases/download/v2.0.14/SHA256SUMS-v2.0.14.txt)）

[![观看 Tigris 宣传片](docs/screenshots/film.jpg)](https://tigris-tv.pages.dev/#film)

▶ [观看 42 秒宣传片](https://tigris-tv.pages.dev/#film)（在官网播放，有声音）

---

## 下载哪个安装包？按电视系统和固件选择

> **正式版为 v2.0.14。** 安装包名称沿用 `Tigris-v2.0.14.apk`（标准版）和 `Tigris-guard-v2.0.14.apk`（兼容守护版）。名称中的 *Guard* 指运行方式，**不是所有 TCL 必装或小米通用的破解包**。本版统一液态玻璃 Settings 与更新页，修复滚动底色跳动，并加入蓝天白云的首次手写欢迎动画；已在 Sony 电视展示验证。

| 设备 / 系统 | 先选哪个包 | 什么时候换另一种 | 必须注意 |
|---|---|---|---|
| Sony BRAVIA Android TV | **标准版** | 原生 HOME 被固件阻拦，确认守护可用后才研究 Guard | 个别固件存在 HOME alias、注册提示和实体键问题 |
| 原生 Android TV / Google TV、通用安卓盒子 | **标准版** | 真实检测发现标准 HOME 被系统拒绝时，才评估 Guard | APK 装好不代表 Home、冷启动和待机均成功 |
| TCL / 雷鸟 / FFalcon Android | **先检测**，普通机型优先标准版；**拒绝带 HOME 的 APK 的固件用兼容守护版 Guard** | 遇 `INSTALL_FAILED_VERIFICATION_FAILURE`，用官方 Guard 包及机型允许的电视端多屏互动 / T-Cast 安装 | Guard **不声明 HOME**，需按机型授权自启动、无障碍/守护；部分固件仍无法完整接管 |
| 小米 / Redmi（MIUI TV / HyperOS TV） | **优先尝试标准版** | 如果 PatchWall 阻止接管，先做固件诊断；Guard 仅在对应机制可用且获授权后尝试 | 不禁用 PatchWall、系统设置或其他关键组件 |
| 创维 / 酷开、海信 / Vidda、OPPO、极米、康佳、乐视等 **Android 9+** | **标准版作为安全起点**，按真实固件诊断 | 只有证据表明该固件需要 Guard 才改用 | 各品牌/机芯差异大；无对应实机证据时不宣称 HOME 接管通过 |
| LG **webOS** | **不能使用 Android APK**；仅提供 [webOS 独立 IPK 预览包](https://github.com/lesnolie/Tigris/releases/tag/webos-v0.1.0-preview.1) | 不适用 Guard 或标准版 | Developer Mode 安装；无实机认证，不接管 HOME |
| Samsung **Tizen**、海信**原生 VIDAA** | **无兼容的 Android APK 安装方式** | 不适用 | 请勿执行 Android ADB 安装命令 |

**如何验收：** 安装后先打开其他应用，再按实体遥控器 Home；分别检查冷开机、待机唤醒、长按 Home 清理内存。无法通过时保留原厂桌面，按照[机型安装/恢复指南](docs/installation-compatibility.md)继续。切换 Standard/Guard 应先确认厂商限制与签名兼容性，**不要为解决安装失败直接卸载已有 Tigris，以免丢失设置和文件夹**。

## v2.0.14：液态玻璃设置与蓝天欢迎

Settings 和「应用与更新」统一使用透明折射玻璃及当前主屏幕壁纸，修复滚动停止后底色突然跳动的问题。设置增加可视开关、应用图标与勾选、玻璃弹窗和真实关于详情。首次使用时，Tigris Logo 在蓝天白云中逐笔写出，确认后进入桌面；可在「设置 → 预览首次欢迎」重播。完整内容见 [v2.0.14 更新说明](docs/releases/v2.0.14.md)。

## v2.0.13：投屏和 HGTV 按需安装

在 Tigris 电视端打开 **主屏幕设置 → 应用、投屏与更新**，可选择安装两个独立应用：

- **MiracastReceiver 投屏**：连接 [官方 GitHub Releases](https://github.com/weekdayjast/MiracastReceiver/releases)，未安装前不下载；安装后可直接打开、更新。
- **HGTV 红果短剧**：连接 [pan8664716/HGTV Releases](https://github.com/pan8664716/HGTV/releases)，用户主动选择才下载与安装。HGTV 是第三方客户端，使用非官方内容接口；内容平台变更可能影响功能。

**应用与更新**现在采用三张 tvOS 风格应用卡片，每个应用仅显示「获取 / 打开 / 更新」等当前适用的操作；下载进度、继续安装、失败重试按需出现。两者均**不包含在 Tigris APK**、不会被自动首次安装或预先塞进 Dock；安装成功后会像普通应用一样出现在应用列表，可以自行移动到 Dock。安装前检查 SHA-256 和已知发布签名，不匹配则停止，不清除已有应用数据。自动更新仅作用于已安装的应用。更多说明见 [软件更新和可选安装](docs/updater.md)。

## 1. 展示与功能

![主屏幕](docs/screenshots/home.jpg)

| 顶部内容（Top Shelf） | 展开全部 App |
|---|---|
| ![顶部内容](docs/screenshots/shelf.jpg) | ![展开全部 App](docs/screenshots/grid.jpg) |
| **控制中心** | **相框 / 屏保** |
| ![控制中心](docs/screenshots/cc.jpg) | ![相框屏保](docs/screenshots/frame.jpg) |

### 历史功能：v2.0.6

- **应用顺序可以固定**：主屏幕设置 → App →「最近打开的 App 自动置顶」。默认关闭，不再因为启动应用而改变顺序；需要自动置顶时可自行开启。切换选项不会重置现有布局和文件夹。
- **正式安装包带版本号**：标准版 `Tigris-v2.0.6.apk`、守护版 `Tigris-guard-v2.0.6.apk`；同版本的校验文件为 `SHA256SUMS-v2.0.6.txt`。应用内更新与安装脚本兼容新版和旧版命名。
- **稳定升级**：沿用现有正式版签名，兼容签名一致的覆盖安装；不需要卸载清数据。完整变化见 [v2.0.6 更新说明](docs/releases/v2.0.6.md)。

### 2.0.4 新增

- **应用文件夹**：长按应用创建文件夹，支持移入、移出、改名和解散。
- **上传头像**：主屏幕设置 → 上传头像，用手机扫码上传或恢复默认头像。
- **统一更新中心**：在同一个页面检查 Tigris 和 MiracastReceiver 的 GitHub 正式版，可手动下载，也可尝试自动安装。
- **下滑磨砂效果**：应用列表下滑时背景逐渐虚化，Dock 保留玻璃底板并随第一排自然滑出屏幕；右上角时间和控件同步离屏，不吸顶、不占用焦点。
- **内置默认素材**：内置默认开机视频和设置页背景；设置背景的主体向左偏移，为右侧按钮留出空间。自定义素材优先，已有配置保留。

### 核心功能

- **主屏幕**：默认 5 列，可选 6 列的 16:9 圆角图标，选中时弹性放大、倾斜并带高光和投影。右上角显示时间、网络与可选头像；时间支持 12 / 24 小时制，也可改成左上角大时钟或隐藏。
- **Liquid Glass Dock**：第一排应用放在液态玻璃 Dock 中，下一排露出上沿。玻璃模糊、边缘折射和高光会按当前背景预先生成，低配电视可改用更省性能的样式。
- **顶部内容**：选中 Dock 中的云视听小电视或斗鱼时，上半屏轮播对应的热门内容。数据来自服务公开的网页接口，使用前需要安装对应的 TV 版 App。
- **主屏幕设置**：仿 tvOS 的分组布局，可分别调整时钟、聚焦动画、控制中心样式、背景、相框、开机动画、头像和桌面守护。
- **最近打开自动置顶（可开启）**：v2.0.6 首次安装或从旧版升级时默认关闭；可在「主屏幕设置 → App → 最近打开的 App 自动置顶」开启，之后成功打开的 App 或文件夹会移到第一位。关闭后固定当前排序，长按「编辑主屏幕」可自行调整；已有的个人布局和开关选择会保留。
- **编辑、文件夹与删除**：长按图标可移动、放入文件夹或删除。电视预装 App 也可用 `pm uninstall --user 0` 从当前用户空间移除，并可在设置中恢复；系统运行必需组件受到保护。
- **控制中心**：遥控器返回键或右上角按钮打开，提供网络、待机、关机、重启、释放内存、相框、设置、亮度、音量和 HDMI 输入源。低配机型可切换成深色面板。
- **背景与手机上传**：支持必应每日风景、360 壁纸、Wallhaven 全年龄二次元、公有领域名画、纯色渐变、手机相册照片和自定义图片链接。手机扫码页面还支持上传头像、开机视频和设置页背景。**扫码后的手机页面现支持中英文自动识别**：跟随手机浏览器语言，和电视系统语言独立；英文手机不会再看到中文上传按钮或提示。
- **相框与航拍屏保**：照片全屏轮播，画面缓慢平移缩放，时间文字定时移动以减少 OLED 烙印；也可在线播放 Apple TV 航拍视频，支持左右键切换，断网时回到照片。
- **HDMI 输入源上屏**：在控制中心把常用 HDMI 口固定到主屏幕，按一下即可切换。
- **软件更新与投屏**：每 6 小时检查 Tigris 和 [MiracastReceiver](https://github.com/weekdayjast/MiracastReceiver) 的正式版。更新前验证包名、系统版本、递增版本号和签名；不会自动卸载、不清数据、不接受降级。电视系统仍可能要求一次安装来源授权或每次确认。
- **内存清理**：支持定时清理和待机清理，保留名单可在设置中修改。

### 素材与商标说明

仓库为界面展示打包了默认开机视频，并在线播放 Apple TV 航拍内容。这些素材不附带 Apple 或任何第三方的额外授权，也不代表 Tigris 与 Apple 有关联。对外分发、商用或再发布时，请确认素材授权；也可以在设置中改用 Tigris 字样动画或上传自己拥有权利的视频。截图中的影视封面、摄影作品和第三方图标版权归各自权利人所有。

## LG webOS 专区（独立平台 · 开发预览版）

> **状态：** Android APK 仍不能安装到 LG webOS。仓库已新增独立 `webos/` 源码、**非 Root 模块**和**已有 Root 用户的高级模块**、天气卡片，以及面向 webOS 的构建/安装脚本。**已有独立 webOS 开发预览 IPK；尚未实机验证 HOME 键接管，不是正式稳定版。** 不要将下面的源码预览安装说明误认为正式版下载。

- **无需 Root：** LG Developer Mode + Key Server 配对，安装普通 Tigris webOS 应用；应用启动、遥控器操作、Luna API 和 HDMI 输入源以实际固件权限为准。默认**不拦截 Home**，可通过电视自带 LG Quick Access 绑定数字键快速打开。
- **已有 Root：** 可以额外检测现有 Homebrew / Root 能力；Tigris **不会 Root、刷机或静默修改系统**。系统级 Home Helper 需要独立验证与恢复功能后才可启用，目前不包含在开发预览包中。
- **非 Root Home Helper：** 计划参考 Clean Your TV 的原生输入处理方案。**AI 可在正式安装前进行 SSH 只读预检，但只有装入隔离的安全探针、验证遥控器实体 Home 键与紧急退出后，才能宣称兼容**。当前探针仍待实现；仅连通 SSH 不等于可以接管 Home。
- **天气：** 新增独立模块，采用 Open-Meteo（30 分钟更新、6 小时缓存），用户明确打开并选城市后才请求网络；默认关闭，不申请定位权限。

### LG 用 AI 安装（先识别平台）

把下面内容发给能在电脑上执行命令的 AI Agent（如 Codex、Claude Code）。**LG webOS 不是 Android：不执行 `adb connect`、`adb shell` 或 Android 包管理命令。**

```text
我要在 LG webOS 电视上安装 Tigris。先判断电视系统，不要仅凭电视品牌推断。
请阅读：
https://github.com/lesnolie/Tigris/blob/main/docs/WEBOS_AI_INSTALL.md
https://github.com/lesnolie/Tigris/blob/main/webos/README.md
请使用 LG Developer Mode、Key Server、@webos-tools/cli 和 SSH/Luna（不是 ADB）。
先只读检查电视配对、固件和权限，并报告非 Root Home Helper 的实际状态。
需要电视端授权时请提醒我；不得索取或泄露私人配对密钥。
我同意后，才构建和安装 webOS 开发预览 IPK。
不要 Root、停用/覆盖 LG Home、未经确认重启或开启未测试的 HOME 拦截。
安装后验证应用/输入源、遥控器按键和原厂 LG Home 的恢复路径。
当前未实现的 Home Helper 必须报告为“未验证/未提供”，不能声称成功。
```

### LG 一行命令：先预检，再按需安装

在 macOS / Linux 上先安装 Node.js + `@webos-tools/cli`，通过 `ares-setup-device` 配对 LG Developer Mode，并将设备命名为 `myTV`。电视 IP 不等于 CLI 的设备别名。

```sh
# 只读预检，不安装、不接管 HOME
curl -fsSLo tigris-webos.sh https://raw.githubusercontent.com/lesnolie/Tigris/main/webos/install.sh && sh tigris-webos.sh --device myTV --preflight

# 用户确认后：从仓库源码构建 IPK，安装并启动普通桌面（仍不接管 HOME）
sh tigris-webos.sh --device myTV --install
```

**重要：** 以上是**代码合并至 main 后**可使用的源码预览入口，而非已发布的稳定 LG 安装包。命令会在确认 CLI 已配对后下载源码、使用官方工具构建；不会自动 Root 或安装未验证的 Home Helper。首次运行前应检查脚本内容。

### LG 手动安装和还原

```sh
npm install -g @webos-tools/cli
ares-setup-device                  # 按向导添加电视、完成 Dev Mode 配对
ares-setup-device --list
# Enable Key Server on the TV; enter the passphrase locally when prompted.
ares-novacom --device myTV --getkey
ares-device --system-info --device myTV
ares-package webos/app
ares-install --device myTV org.tigris.webos_0.1.0_all.ipk
ares-launch --device myTV org.tigris.webos
# 还原（仅移除 Tigris webOS，原厂 Home 未被改变）：
ares-install --device myTV --remove org.tigris.webos
```

Windows 可在 PowerShell 执行 `ares-*` 指令；一行 Shell 脚本需要 macOS/Linux 或兼容的 POSIX Shell。**配对码、SSH 私钥、IP 不要上传到公开 Issue。**

详细说明与英文版：**[LG webOS 独立安装指南](webos/README.md)** · **[AI 与 Home Helper 安全预检规范](docs/WEBOS_AI_INSTALL.md)** · **[官网 webOS 专区](https://tigris-tv.pages.dev/webos/)**。

---

## 2. 安装指南

> [!CAUTION]
> **不要停用或卸载电视原厂桌面。** 尤其是小米 / Redmi 的 `com.mitv.tvhome`（PatchWall）；强行停用后，部分电视会开机黑屏，只能通过 ADB 恢复。正确做法是保留原桌面，使用 Tigris 的默认 HOME 或桌面守护。
>
> **手动修改前先记录原配置。** 至少保存默认 HOME、屏保、无障碍服务和通知监听的原值。`install.sh` 会自动保存快照；手动安装请自行记录，缺少记录时不要猜包名。
>
> **只在自己的局域网开启 ADB。** 不要把电视调试端口暴露到公网。
>
> **签名不一致时不要直接卸载。** `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 表示新 APK 与原包签名不同；直接卸载会丢失设置和授权。应先找到原签名密钥重新签名。

Tigris 现有**正式 APK** 要求 Android 9 / API 28 或更高，不能安装到 LG webOS、三星 Tizen 等非 Android 系统。LG webOS 是上方介绍的**独立 IPK 开发预览项目**，并非现有 APK 的兼容品牌。

### 2.1 打开电视的 ADB 调试

1. 让电脑和电视连接同一个局域网。
2. 在电视的网络或关于页面找到 IP 地址，例如 `192.168.1.20`。
3. 按品牌打开 ADB。

| 电视 | 开启方法 |
|---|---|
| **索尼 BRAVIA** | 设置 → 系统 → 关于 → 在 Android TV 操作系统版本上连按确认键 7 次；再到 设置 → 系统 → 开发者选项，打开 **USB 调试**。 |
| **小米 / Redmi** | 设置 → 关于 → **产品型号**，连续按确认键直到提示进入开发者模式；再到 设置 → 账号与安全，打开 **ADB 调试** 和 **安装未知来源的应用**。 |
| **TCL / 雷鸟** | 设置 → 关于 → **本机信息**，依次按遥控器 **上、下、左、右**，间隔不要超过 1 秒；在底部出现的「更多」中打开 **ADB**。 |
| **原生 Android TV / Google TV、海信等** | 设置 → 设备偏好设置 → 关于，连续按 **版本号** 7 次；再到开发者选项打开 **USB 调试**，也可能叫「网络调试」或「ADB 调试」。 |

电脑第一次连接时，电视会显示「允许 USB 调试吗？」。用遥控器勾选 **始终允许**，再选 **允许**。

电脑需要安装 Android SDK Platform-Tools，并能运行 `adb version`。TCL 多屏互动推送还需要 Python 3。

### 2.2 让 AI Agent 安装（推荐）

适合 Codex、Claude Code，以及能在电脑上执行命令的 WorkBuddy、豆包电脑版 / 工作版、千问电脑版、Trae 等。把下面整段发给 Agent，并替换电视 IP：

```text
请帮我把开源电视桌面 Tigris 装到我的电视上，并设置为默认桌面或启用桌面守护。
电视 IP：<电视IP>
保持 ADB 常开：要

安装说明：
https://github.com/lesnolie/Tigris/blob/main/AGENTS.md
打不开时：
https://raw.githubusercontent.com/lesnolie/Tigris/main/AGENTS.md

请先完整阅读安装说明，再在我的电脑上亲自运行命令，不要只把步骤告诉我。
需要我在电视上操作时请提醒我。
不要未经确认重启、待机、关机、卸载旧版、清空配置或停用原厂桌面。
签名不匹配时先寻找原签名密钥，不要自动卸载。
请先记录默认 HOME、屏保、无障碍服务和通知监听，安装后分别验证安装、主页键、待机唤醒和开机。
没有实际测过的项目不要写成通过。

最后把下面命令的原始输出贴给我，每条 adb 都加 -s <电视IP>:5555：
- adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
- adb shell settings get secure enabled_accessibility_services
- 按主页键并等待两秒后：adb shell "dumpsys window | grep mCurrentFocus"
```

Agent 必须能在这台电脑上实际运行命令。手机聊天或网页版如果只能回复步骤，就不能完成安装。

验收时看按主页键后的 `mCurrentFocus` 是否包含 `com.leslie.tvhome`。守护版可能仍把原厂桌面保留为 HOME resolver，因此不能只看 `resolve-activity`；应以前台窗口和主页键结果为准。

### 2.3 一行命令（macOS / Linux）

电脑需要先安装 `adb`（Android SDK Platform-Tools）、`curl` 和 `python3`。脚本默认下载最新正式版，按版本号解析 APK 与对应的 SHA-256 校验文件，保存原桌面快照，覆盖安装时保留 App 配置。

```bash
curl -fsSL https://raw.githubusercontent.com/lesnolie/Tigris/main/install.sh \
  | KEEP_ADB=1 TV=192.168.1.20 bash
```

- 把 `192.168.1.20` 换成电视 IP。
- 小米电视想保持 ADB 常开时使用 `KEEP_ADB=1`；不需要可改为 `KEEP_ADB=0`。
- 指定版本：管道右侧写成 `KEEP_ADB=1 TV=192.168.1.20 VERSION=v2.0.6 bash`；旧版本未带版本号的文件也仍兼容。
- TCL / 雷鸟使用守护包：把末尾的 `bash` 换成 `bash -s -- -s guard`。
- 原始配置默认保存在 `~/.local/state/tigris/<电视IP>:5555/`；升级不会覆盖首次快照。

```bash
# TCL / 雷鸟守护包
curl -fsSL https://raw.githubusercontent.com/lesnolie/Tigris/main/install.sh \
  | KEEP_ADB=0 TV=192.168.1.20 bash -s -- -s guard
```

安装脚本已通过模拟安装与还原测试，尚未逐一完成 macOS / Linux 各品牌真机验证。脚本不会自动卸载已有包，也不会在签名不一致时清数据。

### 2.4 手动安装

这里的 **Windows** 指电脑系统，**PowerShell** 是 Windows 自带的命令行工具，两者不是互斥选项。PowerShell 中的 `curl` 可能是 `Invoke-WebRequest` 的别名，所以下载文件时写 `curl.exe`；Python 通常写 `python`，不是 `python3`。macOS / Linux 则使用 `curl` 和 `python3`。后面的 `adb` 命令在两个系统上相同。

#### 第一步：连接并备份

```powershell
adb connect 192.168.1.20:5555
adb -s 192.168.1.20:5555 get-state

adb -s 192.168.1.20:5555 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s 192.168.1.20:5555 shell settings get secure screensaver_components
adb -s 192.168.1.20:5555 shell settings get secure enabled_accessibility_services
adb -s 192.168.1.20:5555 shell settings get secure enabled_notification_listeners
```

把四条设置输出保存到记事本。`get-state` 应输出 `device`；如果是 `unauthorized`，回到电视允许本次调试。

#### 第二步：下载并安装

索尼、小米、原生 Android TV 等一般使用标准版。发布的 APK 均带版本号（如 `Tigris-v2.0.6.apk`），可在 [最新 Release](https://github.com/lesnolie/Tigris/releases/latest) 手动下载，也可使用校验 SHA-256 的下载助手：

Windows PowerShell：

```powershell
curl.exe -fsSL -o download-release.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/download-release.py
$apk = python .\download-release.py standard
adb -s 192.168.1.20:5555 install -r $apk
```

macOS / Linux：

```bash
curl -fsSL -o download-release.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/download-release.py
apk=$(python3 download-release.py standard)
adb -s 192.168.1.20:5555 install -r "$apk"
```

TCL / 雷鸟国行固件通常拒绝直接安装，也拒绝声明 HOME 的标准包，需要使用守护版和电视自带的「多屏互动」：

```powershell
curl.exe -fsSL -o download-release.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/download-release.py
$apk = python .\download-release.py guard
curl.exe -fsSL -o tcast-push.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/tcast-push.py
python .\tcast-push.py 192.168.1.20 $apk
```

macOS / Linux：

```bash
curl -fsSL -o download-release.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/download-release.py
apk=$(python3 download-release.py guard)
curl -fsSL -o tcast-push.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/tcast-push.py
python3 tcast-push.py 192.168.1.20 "$apk"
```

运行推送脚本时，电视角落会出现「多屏互动：正在发起应用推送」，用遥控器选择 **同意**。如果电脑防火墙询问，允许脚本访问局域网。

遇到安装失败先停止，处理错误后再继续授权。`INSTALL_FAILED_OLDER_SDK` 表示系统低于最低要求；`INSTALL_FAILED_UPDATE_INCOMPATIBLE` 表示签名不一致，不要直接卸载。

#### 第三步：按需授予系统权限

先设置并验证默认 HOME；只有需要守护或相关功能时才授权。以下权限让 Tigris 能恢复桌面守护、保持屏保和 ADB、从后台打开主屏幕。通知监听组件是 `.RevivalListener`，它不读取通知内容。

```powershell
adb -s 192.168.1.20:5555 shell pm grant com.leslie.tvhome android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.1.20:5555 shell cmd notification allow_listener com.leslie.tvhome/com.leslie.tvhome.RevivalListener
adb -s 192.168.1.20:5555 shell appops set com.leslie.tvhome SYSTEM_ALERT_WINDOW allow
```

仅在 TCL / 雷鸟上执行以下两条；其他电视报错可以忽略：

```powershell
adb -s 192.168.1.20:5555 shell appops set com.leslie.tvhome APP_AUTO_START allow
adb -s 192.168.1.20:5555 shell appops set com.leslie.tvhome APP_ASSOC_START allow
```

#### 第四步：设为默认桌面或启用守护

原生 Android TV、索尼等能直接接管 HOME 的电视：

```powershell
adb -s 192.168.1.20:5555 shell cmd package set-home-activity com.leslie.tvhome/.HomeActivity
adb -s 192.168.1.20:5555 shell am start -n com.leslie.tvhome/.HomeActivity
```

仅 Guard 包或实际按主页仍回原厂桌面的电视，不要停用原厂桌面，改为启用桌面守护：

```powershell
adb -s 192.168.1.20:5555 shell am broadcast -a com.leslie.tvhome.CONFIG -n com.leslie.tvhome/.ConfigReceiver --ez guard true --ez keep_adb true
adb -s 192.168.1.20:5555 shell am start -n com.leslie.tvhome/.HomeActivity
```

不需要保持 ADB 常开时，把 `--ez keep_adb true` 改成 `false`。

首次进入 Tigris 时，还可能出现「本机调试」提示。这是电视连接自己的 ADB，用于待机、关机和深度清理；按提示勾选「始终允许」。电脑已经获准连接，不代表电视自身的连接也已经授权。

#### 第五步：验收

```powershell
adb -s 192.168.1.20:5555 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s 192.168.1.20:5555 shell settings get secure enabled_accessibility_services
adb -s 192.168.1.20:5555 shell input keyevent KEYCODE_HOME
```

等待两秒，再运行：

```powershell
adb -s 192.168.1.20:5555 shell "dumpsys window | grep mCurrentFocus"
```

按主页后前台应包含 `com.leslie.tvhome`。安装成功不等于开机或待机唤醒已经通过；这两项需要分别测试。

### 2.5 索尼 BRAVIA 额外配置（可选）

背光亮度、原生电视音量和带名称的 HDMI 输入源需要注册一次 BRAVIA 本地控制客户端：

```bash
BODY='{"method":"actRegister","id":1,"version":"1.0","params":[{"clientid":"tvhome:1","nickname":"TVHome","level":"private"},[{"value":"yes","function":"WOL"}]]}'
curl -s http://192.168.1.20/sony/accessControl -d "$BODY"
curl -s -u ":<PIN>" http://192.168.1.20/sony/accessControl -d "$BODY"
```

上面是 macOS / Linux 命令。第一次请求后，电视会显示 4 位 PIN。把 `<PIN>` 换成屏幕上的数字，再执行第二条。返回 `{"result":[]}` 表示注册成功。

Windows PowerShell 使用文件传递 JSON，避免引号转义问题：

```powershell
$body = '{"method":"actRegister","id":1,"version":"1.0","params":[{"clientid":"tvhome:1","nickname":"TVHome","level":"private"},[{"value":"yes","function":"WOL"}]]}'
[System.IO.File]::WriteAllText((Join-Path (Get-Location) 'bravia-register.json'), $body)
curl.exe -s http://192.168.1.20/sony/accessControl --data-binary '@bravia-register.json'
# 看到电视上的 PIN 后，替换 <PIN> 再执行下一条
curl.exe -s -u ':<PIN>' http://192.168.1.20/sony/accessControl --data-binary '@bravia-register.json'
```

### 2.6 桌面守护

小米、TCL、海信、创维等国产固件常常只认自家桌面。小米上 `set-home-activity` 可能显示成功，但主页键仍回 PatchWall；待机时还会清理第三方 App。Tigris 用以下方式接管前台，不 root、不修改系统分区、不卸载或停用原厂桌面：

1. **桌面守护**：无障碍服务只监听前台窗口变化，不读取屏幕内容。遥控器上的主页键一定会切回 Tigris。开机、唤醒和被系统清掉之后，只有确认前台是原厂桌面才会自动切回；看不到前台、正在用应用或 HDMI 时不会切。开机头三分钟里的自动来回跳会被停住，避免和原厂桌面抢画面。主页键本身不受这条例限制。
2. **后台恢复**：通知监听服务不读取通知。系统结束 Tigris 进程后，Android 会重新绑定监听器，从而拉起进程并恢复桌面守护。
3. **保持 ADB 开启**：小米唤醒后会关闭 ADB。启用该选项后，Tigris 用 `WRITE_SECURE_SETTINGS` 自动重新打开，方便以后升级。
4. **TCL 守护版**：TCL 拒绝标准 HOME APK，因此使用不声明 HOME 的守护版（发布文件名如 `Tigris-guard-v2.0.6.apk`），通过「多屏互动」安装，并放行 `APP_AUTO_START` / `APP_ASSOC_START`。

| 权限或组件 | 用途 |
|---|---|
| `WRITE_SECURE_SETTINGS` | 经用户授权后，恢复被固件关闭的桌面守护、屏保及 ADB 状态；支持小米背光控制。 |
| 通知监听服务 | 在被系统结束进程后协助重新启动守护，不读取通知内容。 |
| `SYSTEM_ALERT_WINDOW` | 在部分固件允许的条件下，从后台启动桌面。 |
| TCL `APP_AUTO_START` / `APP_ASSOC_START` | 在适用的 TCL 固件上放行第三方应用自启动及关联启动。 |

守护默认关闭。AI 安装会按 [AGENTS.md](AGENTS.md) 授权并打开；手动安装后也可在 设置 → 桌面守护 中开启。索尼等原生 Android TV 通常不需要。

## 3. 还原与卸载

### 用安装脚本还原

使用安装时同一台电脑和同一个 `STATE_DIR`：

```bash
curl -fsSL https://raw.githubusercontent.com/lesnolie/Tigris/main/install.sh \
  | TV=192.168.1.20 bash -s -- restore
```

脚本会关闭守护和保持 ADB，恢复记录的原桌面；只有当前屏保仍指向 Tigris 时才恢复原屏保。默认保留 App 和配置。确定要同时卸载时加 `--uninstall`：

```bash
curl -fsSL https://raw.githubusercontent.com/lesnolie/Tigris/main/install.sh \
  | TV=192.168.1.20 bash -s -- restore --uninstall
```

快照缺失、内容无效或原桌面已经是 Tigris 时，脚本会停止，不会猜包名。

### 手动还原

把 `<原桌面>`、`<原屏保值>` 换成安装前记录的内容。不要用旧快照覆盖当前无障碍服务列表，因为期间可能新增了小爱、TCL 助手等其他服务。

```powershell
# 1. 关闭守护和保持 ADB
adb -s 192.168.1.20:5555 shell am broadcast -a com.leslie.tvhome.CONFIG -n com.leslie.tvhome/.ConfigReceiver --ez guard false --ez keep_adb false

# 2. 仅当当前屏保仍是 Tigris，且安装前记录到非空原值时恢复
adb -s 192.168.1.20:5555 shell settings put secure screensaver_components "<原屏保值>"

# 3. 恢复安装前记录的默认桌面
adb -s 192.168.1.20:5555 shell cmd package set-home-activity "<原桌面>"

# 4. 仅当通知监听由本次安装新增时，撤销它
adb -s 192.168.1.20:5555 shell cmd notification disallow_listener com.leslie.tvhome/com.leslie.tvhome.RevivalListener

# 5. 停止应用
adb -s 192.168.1.20:5555 shell am force-stop com.leslie.tvhome
```

若原屏保值为 `null` 或空值，且当前屏保仍指向 Tigris，用 `adb -s 192.168.1.20:5555 shell settings delete secure screensaver_components` 删除该设置，不要写入字面量 `null`。原桌面记录缺失时，按 [人工还原指引](AGENTS.md#restore) 先查明原组件。

按主页键，等待两秒，再查询 `mCurrentFocus`，确认显示原桌面；同时检查 `enabled_accessibility_services` 中其他服务仍在。确认后再决定是否卸载：

```powershell
adb -s 192.168.1.20:5555 uninstall com.leslie.tvhome
```

如果在 Tigris 中删除过预装 App，卸载前先进入 主屏幕设置 → 已删除的预装 App 恢复。交给 Agent 还原时可直接发送：

```text
请完整阅读 https://github.com/lesnolie/Tigris/blob/main/AGENTS.md 的 Restore 部分，
把电视（IP：<电视IP>）恢复到原桌面，Tigris 先不卸载。
请在我的电脑上亲自执行，保留其他无障碍服务，不要猜原桌面包名。
最后原样贴出 enabled_accessibility_services 和按主页键后的 mCurrentFocus 输出。
```

## 4. 兼容性与社区反馈

> 截至 2026-10-24。下表记录具体机型、系统版本和测试版本上的证据，不代表整个品牌保证可用。**能安装不等于能设默认桌面，也不等于亮度、HDMI、壁纸和开机接管全部正常。**

| 电视型号 | Android / 固件 | 结论 | 测试范围与限制 | 证据 |
|---|---|---|---|---|
| **Sony BRAVIA XR-55A80J** | Android TV 12 | ✅ 维护者实机验证 | 项目主要测试机；不代表所有索尼机型 | 维护者记录 |
| **Sony BRAVIA VH1** | Android TV 10 / API 29 | ✅ v1.9 已运行 | v1.8 曾出现连续返回键崩溃；未记录 2.0.4 全功能复测 | [#2](https://github.com/lesnolie/Tigris/issues/2) |
| **Sony KD-55X9500H** | Android TV 10 / API 29 | ✅ v1.9 社区运行过 | 旧版控制中心玻璃异常已有修复，待新版复测 | [#10](https://github.com/lesnolie/Tigris/pull/10) |
| **雷鸟 75R685C**（`tcl_mt5879_cn`） | Android 11 / API 30，国行 | ✅ Guard 版与多屏互动安装已实机验证 | TCL 拒绝直接 `adb install`；音量、设置、信号源已测，亮度按设计隐藏 | [#1](https://github.com/lesnolie/Tigris/pull/1) |
| **小米 MiTV-ANSP0** | Android 9 / API 28 | ⚠️ APK 可安装，旧版启动崩溃 | `BlendMode` 问题已在 v2.0+ 修复；未收到该机新版完整复测 | [#9](https://github.com/lesnolie/Tigris/issues/9) |
| **小米 MiTV-ASTR0** | Android 14 / 小米 OS3 | ⚠️ 可运行，但有功能异常 | 壁纸下载为 0，部分守护服务被系统拒绝；没有完整通过记录 | [#8](https://github.com/lesnolie/Tigris/issues/8) |
| **小米旧型号（型号未提供）** | 低于安装包最低版本 | ❌ `INSTALL_FAILED_OLDER_SDK` | 需要具体型号与 API 版本；不能据此判定全部小米电视 | [#3](https://github.com/lesnolie/Tigris/issues/3) |
| **创维 / 酷开 7S710_A3** | Android 8 / API 26 | ❌ 官方包不支持；社区 Fork 曾运行 | Fork 测试不等于官方包已支持 Android 8 | [#4](https://github.com/lesnolie/Tigris/pull/4) |

### 功能支持范围

| 功能 | Android TV 9+ | 需要本机调试授权 | 索尼 BRAVIA |
|---|:-:|:-:|:-:|
| 主屏幕、Dock、玻璃效果、投影 | ✅ | | |
| 顶部内容（B站 / 斗鱼） | ✅ 需安装对应 TV 版 App | | |
| 风景 / 二次元背景、相框 | ✅ | | |
| 控制中心界面、时钟 | ✅ | | |
| 释放内存 | ✅ 只清缓存 | ✅ 可结束后台 App | |
| 删除预装 App | | ✅ | |
| 待机、关机、重启 | | ✅ | |
| 相框设为系统屏保、空闲时长 | | ✅ | |
| 音量滑条 | ✅ Android 标准音量 | | ✅ 电视音量 |
| HDMI 输入源切换 | ✅ 大多数电视 | | ✅ 可显示当前输入源 |
| 亮度 / 背光 | 小米（需授权） | ✅ | ✅ |
| 桌面守护、被杀后复活 | 国产固件需要 | ✅ | 原生 HOME 通常不需要 |

**本机调试授权**：Tigris 通过 [dadb](https://github.com/mobile-dev-inc/dadb) 连接电视自己的 `127.0.0.1:5555`，执行待机、关机、深度清理等系统命令。电视必须保持「网络调试 / ADB 调试」开启；第一次操作时在电视上勾选「始终允许」。

**品牌差异**：

- **索尼**：BRAVIA IP 控制接口可提供背光、原生音量和 HDMI 输入源。未注册时自动回退到 Android 标准接口。
- **TCL / 雷鸟国行**：输入源打开 TCL 自带选择器，设置打开 TCL 原生设置，没有可用背光接口。安装需 Guard 版与多屏互动。
- **小米 / Redmi 国行**：背光使用 `picture_backlight`，输入源调用小米播放器，设置和 Wi-Fi 打开小米原生设置；以上功能仍需要更多社区实机复测。
- **酷开 / 创维**：目前只适配输入源选择器，尚未在官方新版上完成真机验证。
- **其他品牌**：使用 Android 标准接口。Android 没有统一背光 API，因此亮度滑条会隐藏；连接 ARC / eARC 功放时，标准音量可能控制电视本体。

### 提交兼容性反馈

欢迎使用 **[电视兼容性反馈模板](https://github.com/lesnolie/Tigris/issues/new/choose)** 提供机型、Android 版本、Tigris 版本、APK 类型、安装结果和日志。没有实机证据的结论会保留“待验证”。

自动化只读取模板中的结构化字段，为中英文 README 创建待审核 PR；不会把陌生用户的反馈直接当成确认兼容，也不会直接改 `main`。

<!-- TV_COMPATIBILITY_AUTO_START -->
| 品牌与型号 | Android | Tigris / 安装包 | 安装结果 | Issue 状态 | 来源 |
|---|---|---|---|---|---|
| Sony BRAVIA 4K GB（BRAVIA_ATV2_CN，国行，2017 平台） | Android 9 (API 28) | v2.0.11 / 标准版 | 已安装且能启动（自报） | 未关闭 | [#53](https://github.com/lesnolie/Tigris/issues/53) |
| GOOJODOQ HY300 Pro+ | Android 14 | v2.0.9 / 标准版 | 已安装且能启动（自报） | 未关闭 | [#47](https://github.com/lesnolie/Tigris/issues/47) |
| 创维酷开 85P3DMAX | Android 11 | 2.0.2 / 自定义 Fork | 已安装且能启动（自报） | 已关闭（未验证修复） | [#15](https://github.com/lesnolie/Tigris/issues/15) |
| 雷鸟 75S545C | Android 9 | V2.0.2 / 守护版 | 已安装且能启动（自报） | 已关闭（未验证修复） | [#14](https://github.com/lesnolie/Tigris/issues/14) |
<!-- TV_COMPATIBILITY_AUTO_END -->

### 适配新品牌

电视硬件控制代码集中在 [`app/src/main/java/com/leslie/tvhome/tv/`](app/src/main/java/com/leslie/tvhome/tv/)：

```text
tv/
├── TvControls.kt      # 选择第一个匹配的适配器
├── AndroidAdapter.kt  # 通用接口 + Android 标准实现
├── SonyAdapter.kt
├── TclAdapter.kt
├── XiaomiAdapter.kt
└── CoocaaAdapter.kt   # 酷开 / 创维，目前仅适配输入源
```

新品牌可新建 `<品牌>Adapter.kt` 继承 `AndroidAdapter`，只重写固件不同的部分，再注册到 `TvControls`。不会写代码也可以把下面内容交给 Agent，它会按 [AGENTS.md 的适配章节](AGENTS.md) 探测接口、修改、编译并验证：

```text
请按 https://github.com/lesnolie/Tigris 的 AGENTS.md 安装 Tigris。
如果亮度、音量或 HDMI 输入源在我的电视上不能用，请帮我适配。
电视 IP：<电视IP>
```

适配成功后欢迎提交 Pull Request，让同品牌用户直接复用。

## 5. 常见问题、开发与许可

### 常见问题

**需要 Root 吗？**

不需要，也不修改系统分区。Tigris 是普通 Android 应用，只通过 ADB 获取少数系统权限，或启用无障碍与通知监听。误操作 ADB、停用系统桌面或删除关键系统组件仍可能让电视无法正常开机；本项目的保修影响取决于厂商政策。

**只用 U 盘安装 APK 可以吗？**

可以打开基础桌面，但通常不能接管主页键、开机进入、待机唤醒、系统屏保、深度清理、关机重启或删除预装 App。国产电视的桌面守护也需要电脑 ADB 授权一次。项目没有逐一验证手机上的 ADB 工具。

**主页键为什么还是回到原厂桌面？**

标准版需要成功设为默认 HOME。小米、TCL 等固件可能仍抢 HOME，此时保留原厂桌面，授予权限并启用桌面守护。千万不要停用 `com.mitv.tvhome`。

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE` 是什么？**

新旧 APK 的签名不同，Android 拒绝覆盖安装。应找到原安装包对应的签名密钥并重新签名。直接卸载会丢失 Tigris 的设置和 ADB 授权，不应作为自动升级步骤。

**安装成功就代表开机也会进入 Tigris 吗？**

不代表。主页键、待机唤醒和冷启动是三项独立验证。安装器不会擅自让电视待机、关机或重启；需要用户配合逐项测试。

**`KEEP_ADB=1` 是什么？**

明确启用「保持 ADB 调试开启」。小米等固件待机唤醒后可能关闭 ADB，开启后 Tigris 会尝试自动恢复，方便以后升级；脚本未指定时保留已有选择，新安装默认关闭；还原时会关闭。

**Windows 应该选 PowerShell 还是其他方式？**

PowerShell 是 Windows 的命令行工具。下载时用 `curl.exe`，Python 用 `python`；ADB 命令与 macOS / Linux 相同。Windows 没有可靠的一行 Bash 安装前提，推荐交给 Agent，或按上面的 PowerShell 手动步骤执行。

**会占用大量存储或必须联网吗？**

航拍影片从 Apple 服务器在线流式播放，不会整段保存到电视。2.0.4 APK 另外内置一段默认开机视频和设置页背景，因此安装包本身包含媒体文件。断网时航拍会回退到照片，已缓存的背景仍可使用。

**原来的桌面、会员、小爱和账号还在吗？**

安装器不会卸载原厂桌面，也不清其他应用数据。原桌面可从应用列表打开；小爱、会员、账号和有线电视照常使用。固件开机广告由系统播放，不属于桌面，无法承诺去除。

**为什么某些电视没有亮度滑条？**

Android 没有统一的电视背光 API。目前只适配了索尼 BRAVIA 和小米的对应接口；其他品牌会隐藏无效滑条。音量或 HDMI 不正常时，可按兼容性章节提交机型信息适配。

**支持哪些系统？**

正式 APK 支持 Android 9 / API 28 及以上。LG webOS 不能安装 Android APK，请看上方独立 webOS IPK 开发预览；三星 Tizen 等其他非 Android 系统尚不支持。Android 8 及更老版本需要单独维护 Fork。

**投屏要另装吗？**

Tigris 不内置投屏服务。推荐开源、无广告的 [MiracastReceiver](https://github.com/weekdayjast/MiracastReceiver)（MIT），支持 AirPlay、DLNA 和部分 Miracast 场景。可在 Tigris 的更新中心打开或安装，它也能被单独更新。Windows / Android 的 Miracast 支持取决于电视固件，部分场景仍需要 root。

### 手动构建

需要 JDK 17、Android SDK `compileSdk 36` 和 Gradle Wrapper：

```bash
./gradlew assembleStandardRelease assembleGuardRelease
```

产物：

```text
app/build/outputs/apk/standard/release/app-standard-release.apk
app/build/outputs/apk/guard/release/app-guard-release.apk
```

本地构建使用本机签名，可能与正式 Release 不同。覆盖安装前先确认签名；不要为了安装本地包直接卸载正式版。

### 许可与第三方声明

代码采用 [MIT](LICENSE) 许可证。第三方代码、字体、图标和依赖的版权与许可信息见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

Tigris 是独立开源项目，与 Apple、Sony、小米、TCL、哔哩哔哩和斗鱼没有关联，也没有获得这些公司的赞助或背书。所有第三方商标、影视封面、摄影作品和视频素材归各自权利人所有。
