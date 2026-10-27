# 首次使用欢迎动画（2026-10-27）

用户选定第一版：蓝天白云、白色手写 Tigris Logo，不加欢迎语。Logo 按实际笔画顺序从头写到尾，约 4.6 秒完成，随后渐显「按遥控器确认键，进入下一步」，停留等待确认。

## 实现

- `Welcome.kt` 为 HomeActivity 管理的原生全屏覆盖层。云光背景缓存为小位图，Logo 使用矢量轮廓、中心笔画及 `PathMeasure` 分段书写，不需要网络、视频或 WebView。
- 新偏好 `welcome_completed` 仅在实际首次欢迎的「下一步」操作时写入。随后重新创建 Home 不再自动展示；返回键退出不标记完成。
- 书写途中按确认键先完成书写，再按一次才进入桌面；其它方向键不会操作被覆盖的桌面。
- 设置「开机动画」分组增加「预览首次欢迎」。预览不修改首次完成状态，也不改原来的开机视频、音效或待机动画偏好。
- 页面暂停时停止帧回调，恢复后接着写；销毁时释放回调和素材工作线程。首次调试授权提示等到欢迎结束后再显示。
- 字形轮廓派生自 Pacifico，原字体遵循 SIL Open Font License 1.1，版权及完整许可随素材保存在 `assets/welcome/Pacifico-OFL.txt`。

## 验证

- Standard Debug、Standard Release、Guard Release 构建通过；两种 Release 的 Android Lint 检查通过。两种 Debug 的既有单元测试各 68 项通过。
- 中英文各 451 项资源检查与 `git diff --check` 通过。
- v2.0.14 发布复查补充：预览 Intent 标记在读取后立即清除，避免 Activity 重建时意外重播；`release-native-qa-zh.log`、`release-native-qa-en-large.log` 验证首次创建的预览标记已被消耗，原有欢迎状态测试继续通过。
- Android TV API 30 模拟器，中文正常字号和英文 1.3 倍字号原生测试均通过：素材离线加载、部分书写、遥控器隔离、暂停/恢复、提前确认、下一步焦点、首次完成保存、下次 Home 不重复、设置预览重放、返回取消以及预览状态隔离。
- 临时测试最初使用 `waitForIdleSync` 等待主线程空闲，该等待会越过整段连续动画。改为在主线程定时暂停动画后检查实际部分笔画；产品动画代码无需为测试改动。
- Sony BRAVIA 4K VH2，Android API 31，1920×1080：匹配原签名的 Standard Release 已覆盖安装，已直接播放原生欢迎动画。保存了书写中、完成提示及遥控器 UI 层级，确认下一步提示获得焦点。后续观察到电视已切换到视频播放界面，因此停止遥控器检查；不将模拟器的继续/预览检查记为实机操作验证。

证据目录：`C:/work/appletv/.tmp/welcome-preview-20261027/`。

- `native-final-build.log`：最终构建与静态检查。
- `native-qa-zh-final.log`、`native-qa-en-large-final.log`：最终 Debug APK 的原生行为测试。
- `tv-welcome.mp4`：实机动画短录像。
- `tv-welcome-writing.png`、`tv-welcome-ready.png`、`tv-welcome.xml`：书写与最终提示。
- `tv-before-welcome.apk`：本次覆盖安装前的 APK 备份。

本次没有重启、待机、修改 HOME、系统权限或守护设置，没有发布或推送代码。其它电视型号尚未验证。
