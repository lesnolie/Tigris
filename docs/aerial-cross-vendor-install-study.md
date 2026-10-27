# Aerial 跨品牌适配研究与 Tigris 落地边界（2026-10-27）

> **唯一产品方向**：更像 tvOS、更容易安装和接管 HOME。本研究不授权任何电视系统修改。
> 来源主要是 Aerial 的公开源码与设备实测报告，其他项目仅作机制交叉参考。
> 机型、固件及 Android SDK 必须实际检测；Aerial 成功 ≠ Tigris 已在该机型验证。

## 先分清三个问题

1. **APK 能安装吗？** 有些 OEM 校验 HOME 声明、签名或 ADB 安装来源。
2. **实体 Home 能接管吗？** Android 的 preferred HOME/role、固件显式启动、OEM 白名单可能是不同机制。
3. **能持续保持吗？** 冷开机、用户解锁、待机唤醒、内存清理、应用覆盖升级后，Guard/系统进程有不同生命周期。

任何步骤单独成功，都不能宣称其他步骤已经通过。ADB 注入 Home 不等于实体遥控器 Home。

## 平台机制矩阵

| 设备家族 | Aerial 公开案例中的机制 | Tigris 当前处理 | 下一步的实机验收 |
|---|---|---|---|
| Sony BRAVIA / Android TV | 原生 HOME 通常可用；国行某些固件原厂 HOME 优先级更高，activity-alias 事件可能不匹配 | 保留原有 Sony standard/Guard 路径；不更改 runtime adapter | 实体 Home、冷启动、alias、注册弹窗、输入源 |
| TCL / FFalcon MT5879 | Verifier/LauncherStrategy 拒绝 ADB 或声明 HOME 的包；T-Cast 推送 Guard；`APP_AUTO_START` / `APP_ASSOC_START` 影响开机绑定 | 沿用现有 Guard/T-Cast/权限设置。仅在观察到安装失败后切换 | 安装后、守护实际绑定、冷启动、待机、恢复 |
| TCL m7221 | 部分固件开机由 `persist.sys.launcher.component` 指定组件，与 Android HOME resolver **相互独立**；Aerial 案例 Android 8 | **Tigris 最低 Android 9，不能直接支持该 Android 8 案例**；只用于架构研究 | 未来满足最低 SDK、精确固件、快照和用户授权后再研究，绝不照抄写属性 |
| TCL MT9655 | 可能只允许 ADB-over-TLS 且无配对入口；T-Cast 可以安装 Guard APK，但 HOME 授权与安装分离 | 检测到 ADB 未授权就停止主机自动化，提示现有设备侧安装；不宣传接管完成 | 是否能取得合法 ADB 授权、实体 Home 是否可用 |
| 小米 / Redmi MIUI/HyperOS | PatchWall HOME 白名单可能忽略成功的 set-home-activity；待机强杀；低内存 Android 9 可拒绝通知监听；禁用 PatchWall 可能黑屏 | `xiaomi_home.py` 只读诊断 + 单独授权可恢复试验；保留原厂 HOME | HOME 白名单、FallbackHome、监听绑定、复活、长按 Home 清理 |
| 创维 / 酷开 | `HOME.CC` / 显式组件 / 固件 XML 决定启动；部分固件强制停止或阻止第三方窗口拿到焦点 | 选择 `coocaa-diagnostic`，APK-only 和只读报告；不修改 `general_config.xml` | 固件代际、实体键、开机原桌面、窗口/焦点、退出恢复 |
| 海信 / Vidda Android | 部分 Android VIDAA 用 `persist.vt.use_home_pkg` 指定实体 Home；另有系统级开机拦截；原生 VIDAA 非 Android | 选择 `hisense-android-diagnostic`，不自动写入厂商属性 | 先确定 Android/原生 VIDAA，再看属性是否存在和 boot/Home 是否分开 |
| OPPO ColorOS TV | K9 案例校验桌面包名和 `MODERN_HOME` 分类；更改身份会引发升级与权限风险 | 选择 `oppo-diagnostic`；保留 Tigris 包名签名，不伪装别人的包名 | 安装失败码、HOME 解析、守护是否可用 |
| 极米 GMUI | 部分机型 ADB 入口隐藏、需用户明确授权；长按 Home 会做后台清理 | 选择 `xgimi-diagnostic`，仍是 APK-only | ADB 是否能授权、实体 Home、内存清理后行为 |
| 康佳 / KKTV | 部分文件管理器拒绝安装，但系统安装器覆盖升级可用；HOME 可能仍指向原厂 | 选择 `konka-diagnostic`，不卸载已有 Tigris 数据 | 安装来源、签名、HOME + Guard、冷启动 |
| 乐视 / eUI | 反复停止第三方应用；如果守护在未知前台强行拉回，会打断其他 App | 选择 `letv-diagnostic`，APK-only；默认不添加激进接管 | 进程停止频率、用户主动打开的 App 是否被误抢 |
| 普通 Android TV / 盒子 / 投影仪 | 多数走原生 HOME，但低配、假冒的设备属性、ADB 权限等存在差异 | 原有 `generic-android` 安全 APK-only；不根据未知设备猜系统属性 | 当前前台、实体 Home、开机、待机 |

## 其他项目的借鉴原则

- **Aerial**：可复用的是「错误原因分流 → 对应包类型 → HOME 验证 → 恢复」以及按固件记录机制、验证证据。禁止无条件拷贝厂商属性写入命令；Tigris 的 `tcast-push.py` 已有基于许可的来源说明。
- **Clean Your TV**：借鉴先备份、识别当前 HOME、禁止无安全退路的停用及恢复快照；清理系统包不属于默认安装流程。
- **tv-fresh**：仅将 MiTV-ASTP0 等具体固件报告作为**案例**，不能认定全部 MIUI/HyperOS 都允许停用 PatchWall。

## 当前真实实现与还没实现的部分

- [x] `tools/installation_strategies.json` 增加六个品牌家族的安全诊断分流（不改变 Sony/TCL/Xiaomi 的 executor）。
- [x] `tools/vendor_install_guidance.json` 十个家族的研究证据、机制和检查点。
- [x] `tools/vendor_install_guidance.py` 将研究提示加到 `installation_strategy.select()` 的返回计划；**不会授权或运行任何厂商固件命令**。
- [x] 现有的 AI、一键、手动安装共用上述策略表；中英文注册字段供官网/开发者使用。
- [ ] 按准确机芯/固件指纹的二级分类及真实命令选择仍需完整检测和设备验证；品牌名不是执行权限。
- [ ] ADB 不能授权时，T-Cast/本机文件安装的交互闭环仍有差异，必须逐机验证。
- [ ] 新增家族的实体遥控器、开机、待机、恢复尚未经过 Tigris 真机测试。

## 贡献者如何添加新兼容性

提交**实际设备型号、完整固件指纹、Android SDK/机芯、原厂 HOME 组件、HOME 解析、失败时的准确错误码**；
记录 `adb_home`、实体 Home、冷启动、待机唤醒、安装覆盖、恢复测试结果。
先写可复现的只读观察，再单独评审有恢复保证的写操作。
`tools/vendor_install_guidance.json` 是跨项目研究材料，
`tools/installation_strategies.json` 是安装器执行边界，
`app/.../tv/` 仅是运行时设备能力适配，
`app/.../features/` 的天气、Liquid Glass、Dock 等与品牌安装无关。

任何新固件都不得改变已经验证的 Sony/TCL 路径。
**禁止合并 PR、修改 main、正式发布**，除非维护者另行明确批准。

## 研究来源（源码和实机报告）

- https://github.com/pengchujin/aerial-tv/blob/main/docs/ARCHITECTURE.md
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-mt5879-cn.md
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-m7221-cn.md
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-mt9655-cn.md
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/xiaomi-mitv-mftr0.md
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/xiaomi-mitv-mftp0.md
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/skyworth-coocaa.md
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/hisense-vidaa.md
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/oppo-coloros-tv.md
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/xgimi-z7x.md
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/konka-kktv-u86v9.md
- https://github.com/Gathin23/cleanyourtv
- https://github.com/wangrongding/tv-fresh
