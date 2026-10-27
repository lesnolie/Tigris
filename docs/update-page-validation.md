# 更新页液态玻璃验收（2026-10-27）

基于最新 `origin/main` 的 `96f7356`（v2.0.13），本地分支 `fix/tvos-liquid-glass-updates`。

## 修改范围

- 仅重排「应用与更新」界面：左右分栏、桌面/可选应用分组、自动更新开关。
- `UpdateGlass` 使用已有 `LiquidGlass.bake` 计算背景采样、边缘折射和高光；后台限尺寸生成纹理，布局/滚动变化合并刷新，销毁后拒绝旧结果。
- 焦点平滑提亮、放大和移动光斑；降低乳白遮罩，应用行也采样折射背景。初始三行完整展示，按钮隐藏时焦点返回检查更新。
- 保留每应用一个操作、安装确认、更新校验、按需下载及原有配置键。未改主页/控制中心玻璃和厂商适配层。

## 已执行检查

| 检查 | 结果 |
|---|---|
| Standard / Guard Debug 构建 | 通过 |
| Standard / Guard JVM 单元测试 | 各 68 项通过，零失败 |
| Standard / Guard Android lint | 通过；修改文件无 lint 报告 |
| 中英文资源及格式参数检查 | 440 对资源通过 |
| Git diff 空白检查 | 通过 |
| Android TV API 30 模拟器，1920×1080、320 dpi | 原生界面截图，非 HTML 模型 |
| 中文、默认字体 | 三行完整显示，版本可见 |
| 英文、系统字体 1.3 倍 | 三行完整显示；左侧内容可滚动 |
| 遥控器右/下方向键 | 焦点能进入应用操作，玻璃提亮、浮起 |
| 自动更新开关 | 开/关持久化正确；无障碍节点提供可勾选状态 |
| 玻璃纹理 | 已确认焦点控件持有生成完成的折射纹理，未停在加载兜底样式 |
| 下载/失败/待确认状态（模拟） | 进度替换旧操作、焦点回退、重试和继续安装入口正常 |

原生检查使用临时 Instrumentation 工具，通过 Gradle init 脚本注入测试源码；该工具未加入产品源码或生产安装包。

## 本地证据

位于 `C:/work/appletv/.tmp/update-page-review-20261027/`：

- `final-build.log`：两种安装包、测试和 lint。
- `qa-zh.log` / `qa-en-large.log`：每种情景 12 项原生断言。
- `native/zh-initial.png`、`native/zh-right.png`、`native/zh-toggle.png`：默认页面及焦点。
- `native/en-large-initial.png`：英文大字体。
- `native/*-download.png` / `native/*-confirmation.png`：模拟安装流程状态。

## Sony 电视实测（后续授权安装）

用户随后明确要求安装到电视测试，并要求加强玻璃感。最终材质降低了乳白遮罩和背景模糊，加入应用行折射与移动焦点光斑；此前模拟器图片只代表首轮布局，最终视觉以实机图片为准。

- 设备：Sony BRAVIA 4K VH2，Android API 31，ADB `192.168.50.109:5555`。
- 安装：Standard Release 本地构建，`adb install -r` 成功；安装前核对新旧 APK 的 SHA-256 签名证书相同。未卸载旧应用，原版本备份为 `tv-before.apk`。
- 最终材质代码的 Standard Release、Guard Debug 构建及两种 flavor 的各 68 项单元测试通过；Standard Release lint 通过。日志为 `clear-glass-build.log`。
- 现有黑白山景壁纸、已安装的 MiracastReceiver 1.2.8 / HGTV 1.2.6 和自动更新设置仍在。
- 更新页可正常进入，三个应用完整可见；方向键导航和焦点高光实际可用。
- 自动更新开关实际切换并恢复为原来的「已开启」；节点提供正确的 Switch 勾选状态。
- 「检查更新」实际进入「正在处理…」，随后恢复「检查更新」；三应用均显示最新版本。此次没有安装或更新第三方应用。
- `tv-interaction.json`、`tv-check-result.xml`、`tv-verification.json` 保存操作与验收原始输出；`tv-clear-final.png` 为最终实机截图，`tv-gfxinfo.txt` 为局部导航渲染统计，不代表整机性能认证。
- ADB Home 测试后默认解析器和实际前台均为 `com.leslie.tvhome/.HomeActivity`；无障碍服务为空，与安装前一致。随后重新进入更新页，停留在「检查更新」。

## 尚未验证

未测试遥控器实体 Home 按键、待机唤醒、冷启动、HDMI、音量和真实 APK 下载/安装确认。此前模拟的安装状态不代表实际下载或安装成功。此次未发布版本，未改默认 Home、系统权限或其他电视设置。
