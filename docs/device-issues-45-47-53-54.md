# Sony 与 HY300 问题修复记录（2026-10-27）

基于 v2.0.14（`02be310`）检查。四个 Issue 均保持开放；本分支不修改正式版标签，不合并 PR #46，也不把模拟测试当作报告机型的验收。

| Issue | 代码修复 / 排查方向 | 关闭前仍需要的证据 |
|---|---|---|
| [#45](https://github.com/lesnolie/Tigris/issues/45) Sony 注册循环 | 后台读取从不触发注册；显式写入才申请 cookie。失败时间在应用私有配置中保存，半小时退避跨进程生效。借鉴 [PR #46](https://github.com/lesnolie/Tigris/pull/46) 的读写分离与退避思路，并补齐“每次启动仍弹一次”的残留。 | 未配对 Sony 上反复进入桌面/控制中心、取消注册、重启应用后，确认没有自动注册循环；已经配对的音量/亮度功能回归。 |
| [#47](https://github.com/lesnolie/Tigris/issues/47) HY300 Pro+ 授权失败 | 显式授权区分密钥准备、端口连接、认证拒绝、握手超时、协议错误、连接关闭和 shell 检查失败；Home 与设置均显示具体说明。只在实际命令检查成功后记录授权成功；不覆盖残缺的既有密钥对。 | HY300 的实际失败类别、准确固件版本、电脑 `adb devices -l` 状态，以及设备是否仅提供无线 TLS 配对。当前不能声称修复了设备授权机制。 |
| [#53](https://github.com/lesnolie/Tigris/issues/53) Sony Android 9 守护与卡顿 | HOME 候选同时收集 alias 和 targetActivity，并补全相对类名；保持按包与具体类匹配、负优先级过滤、用户主动进入原厂桌面时放行以及有限重试。注册循环使用 #45 修复；视频背景已有 PixelCopy 异步取帧。 | 报告中的 Android 9 固件验证实体 Home、原厂桌面主动打开/返回、连续方向键和视频背景下的 ANR；待机/唤醒另行验收。 |
| [#54](https://github.com/lesnolie/Tigris/issues/54) Sony HDMI 写入失败 | BRAVIA 切源失败后，以物理端口号匹配 Android 原生输入的 HDMI 标题，使用其 passthrough 地址切换；匹配不唯一或系统拒绝时打开电视设置并提示手动选择。 | K-55XR50 国行固件实际验证 403 后能否切到指定 HDMI；系统输入是否可见/允许第三方打开。不能仅凭 Intent 启动就宣称切源成功。 |

## 关键边界

- `getPlayingContentInfo` 在部分 Sony 上也要求鉴权，不能按 get/set 名字推断其权限。本实现允许它复用已取得的 cookie，但没有 cookie 或被拒绝时静默返回未知，绝不为了轮询当前输入源发起 `actRegister`。因此首次显式写入取得 cookie 前，当前输入的识别可能暂不可用。
- 已配对的显式写入仍走原 BRAVIA 注册与 cookie 流程。错误 cookie 最多重新认证一次，第二次鉴权拒绝进入持久退避；读取不触发认证重试。普通 HTTP 403 即使返回非 JSON 也按鉴权拒绝处理。
- 手动在外部完成 Sony PIN 配对不会自动清除应用已记录的退避时间；窗口到期后再重试写入。本分支没有新增完整的 PIN 配对界面，因此不把写控制在所有国行 Sony 上恢复当作已验证结论。
- **不能直接 `super.setInput(original)`**：原始 `extInput:` 是 Sony 地址，Android 需要 `content://android.media.tv/passthrough/...`。既有置顶输入保存格式保持不变，在运行时翻译；不按列表顺序、用户重命名标签或 vendor HW 数字猜端口。
- 标准 Android SDK 的 `TvInputInfo` 不公开 HDMI 物理端口号；本分支只接受原生 HDMI 标题能唯一匹配的情况，其它情况交由用户在电视设置里选择。API 契约参考 [TvInputInfo 官方文档](https://developer.android.com/reference/android/media/tv/TvInputInfo)。
- HY300 的“无线调试已打开”不证明 `127.0.0.1:5555` 可连接。本分支没有自动开启 TCP 端口、改变 ADB 模式、清空信任密钥、尝试绕过配对或授予新的权限。错误信息只记录类别和阶段，不记录密钥、配对码、命令内容或服务器原始输出。

## 验证

新增回归测试覆盖注册次数/跨实例退避、成功 cookie 复用、失败重试上限、时钟回拨、HOME alias 与 targetActivity、HDMI 精确匹配/歧义/旧地址以及本机调试错误分类。两种安装包均执行相同单元测试与 Release 构建、Lint 检查。

Standard 与 Guard 各 89 项单元测试通过；Standard Debug、Standard Release、Guard Release 构建通过，两种 Release Lint 零错误。463 对中英文资源与 `git diff --check` 通过。

额外 Android TV 模拟器检查使用假的本机 HTTP 连接，实际运行 `SonyAdapter` 的 JSON 请求路径，验证公共/受保护读取均不会注册、注册失败跨客户端重建后仍退避、配对成功仍可写、非 JSON 403 有限重试、HDMI 失败不把 `extInput:` 转交 Android，以及残缺密钥不被覆盖。工具只在测试 APK 中使用，不在产品内启用替代网络或模拟成功路径。

本地构建和原生检查证据保存于 `C:/work/appletv/.tmp/sony-issue-fixes-20261027/`。这些不是四位报告者对应设备的实测结果；尚不能关闭任一 Issue。
