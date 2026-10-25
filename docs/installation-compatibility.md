# 安装兼容性：手动、AI 与安装器共用规则

安装成功不等于 Home 接管成功。先确认平台，再安装、验证；失败才按具体固件处理。保留原厂桌面，不自动卸载、改包名、修改白名单或重启。

## 完整替换模式（逐步落地）

所有品牌目标相同：用 Tigris 代替厂商桌面、减少系统广告、保持电视核心功能和恢复能力；但每种固件的具体处理不同。当前新增电脑端 `TV=<IP> bash install.sh full-plan`，读取固件和 HOME 竞争信息。小米广告候选仅可在逐个确认和备份后处理，**不承诺所有广告已消除或 HOME 已彻底替换**。Sony / TCL / 创维 / 海信不会运行小米禁用命令。详见 [完整替换开发与使用指南](full-replacement.md)。

## 两个手动入口

**只用遥控器 / U 盘**：从官方 [Releases](https://github.com/lesnolie/Tigris/releases) 下载标准 APK（有 HOME 安装限制的固件用 Guard 兼容包），使用电视允许的方式安装并打开。如果系统提供默认桌面选择器，选择 Tigris；打开其他应用后按遥控器 Home。仍回原厂桌面时，进入 Tigris 设置 → 安装与 Home 检查 / 桌面守护。应用仍可打开，完整接管可能需要 ADB 或专用适配；U 盘安装不会自动授予系统权限。

**电脑 / ADB（Android 调试桥）**：先按 README 保存原 HOME、屏保、无障碍服务和通知监听，再安装正确包。标准包尝试设置默认 HOME；Guard 包不声明 HOME，直接配置守护。不要因品牌直接开启守护。若 Home 正常，无须检查小米包名白名单，也不必授予守护权限。

所有 Android 品牌使用同一个检查器。Windows 下载用 `curl.exe`，运行用 `python`；macOS/Linux 用 `curl` 和 `python3`：

```powershell
curl.exe -fsSL -o check-install.py https://raw.githubusercontent.com/lesnolie/Tigris/main/tools/check-install.py
python check-install.py inspect --serial 192.168.1.20:5555
# 下一条会打开电视系统设置并按 Home，离开当前节目；不会待机或重启。
python check-install.py verify --serial 192.168.1.20:5555 --report verification.json
```

`inspect` 只读；`verify` 从非 Tigris 窗口发出 Home，连续观察前台。退出码 0 表示 ADB Home 检查通过，1 表示失败，2 表示无法验证。无法打开设置、前台信息缺失、只看到短暂的 Tigris，均不能作为通过。设置选择器需要用户先操作，再重新检查。

一行安装器也使用此检查器。克隆仓库后可执行 `TV=192.168.1.20 bash install.sh inspect` 或 `verify`；默认安装将结果保存在原快照目录的 `verification.json`。恢复时必须验证原 HOME，失败则保留 Tigris，不继续卸载。

## 按电视品牌排查

| 系统 | 失败后检查 | 边界 |
|---|---|---|
| Sony / 原生 Android TV | 默认 HOME、实体遥控器 Home | 不因品牌宣称兼容 |
| Google TV | HOME 解析、原厂桌面是否重新进入前台 | 可能需要 ADB / 守护 |
| 小米 / Redmi | 首次 Home 失败：HOME 候选和固件包名筛选；唤醒后失败：待机清理、后台启动、守护绑定 | 只读查询不能证明白名单；不改包名，不停用 PatchWall |
| TCL / 雷鸟 | APK 安装校验、HOME 声明限制；安装后再查守护和自启动 | 仅受限固件用 Guard / 多屏互动，安装拒绝不是小米白名单 |
| 海信 Android / 创维酷开 | Home 接管、后台启动、无障碍服务实际绑定 | 按型号固件验证；非 Android 系统不能装 APK |
| LG webOS | Developer Mode、SSH 配对、设备固件、原厂 Home 保持可用 | 独立 IPK 源码预览，不用 ADB；Home Helper 尚未交付 |

小米 Home 失败时，检查器额外读取 `disable_back_home_whitelist`，它只是待机机制线索，不能证明第三方 HOME 包名允许列表。不要把其他应用的包名加入通用安装命令。未知固件应保留“未确认”，必要时单独研究固件实现。

## 四项验收

分别记录：**APK/IPK 安装、实体 Home、待机唤醒、重启**，每项使用“通过 / 失败 / 未验证”。ADB 合成 Home 另列，不冒充实体遥控器。电视端配置状态也不是实测结果。

- Home：从其他应用按实体 Home；检查是否稳定进入 Tigris。
- 待机唤醒：由用户操作或明确同意后测试，不用手动打开 Tigris 补救来冒充自动恢复。
- 重启：同样需用户同意；未测试就写“未验证”。
- LG 普通预览不接管 Home，应报告“Home 替换未提供”，并确认原厂 Home 正常。Developer Mode 有有效期，应按 LG 界面续期；失效后开发安装的应用可能被删除。

守护服务“在启用列表里”和“实际已绑定运行”是不同证据。守护无法运行时给出权限/服务诊断，不能宣称白名单已确认或永久无法适配。
