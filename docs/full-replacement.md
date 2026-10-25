# Tigris Full Replacement / 完整替换模式（第一、二阶段）

> **目标适用于所有品牌：** 日常使用 Tigris 而非厂商首页、实体 Home 返回 Tigris、开机和待机恢复稳定、尽可能屏蔽固件系统广告，保留 HDMI / 投屏 / 声音 / 设置 / OTA，并可恢复原厂状态。
>
> **当前实现边界：** 本阶段提供各品牌统一目标/安装检查、小米固件只读诊断、需逐个授权的小米系统广告候选处理及恢复。**未实机验证任何机型能完全替换并消除全部广告**。这不是“一键去除所有广告”，更不保证删除 PatchWall 或 FallbackHome 能安全完成。第三方影视 App 内广告不在该功能承诺之内。

## 系统分工

- **电视 APK 品牌模块（`tv/*Adapter.kt`）**：保留厂商的音量/亮度/HDMI/设置控制。不能把电脑 ADB 的权限直接当成普通 APK 的运行权限。
- **电脑端公共安装器（`install.sh` + `tools/check-install.py`）**：负责下载签名包、ADB 安装、快照、实际 HOME 验证及恢复；不将“命令返回成功”等同于用户拿遥控器测试通过。
- **电脑端品牌策略（`tools/replacement_profiles.py` + `tools/tv-replacement.py`）**：按照识别出的厂商选择只读诊断与显式授权的固件操作。品牌识别失败或不支持即不执行小米专属命令。
- LG webOS/VIDAA 原生系统并非 Android：分别需要独立平台安装和 Home 可行性研究，**不可执行 Android ADB 包管理**。

## 使用流程（电脑 / AI Agent）

连接电视、允许 ADB 调试后，先使用官方稳定签名包安装 Tigris，保留原厂状态：

```bash
TV=192.168.1.20 bash install.sh install -s standard
# 安装不等于真正接管，按遥控器物理 Home 另行验收
TV=192.168.1.20 bash install.sh verify
```

1. **统一预检（完全只读）：**

```bash
TV=192.168.1.20 bash install.sh full-plan
```

此命令输出 JSON，包括品牌、具体型号/固件指纹、当前 HOME 候选/解析结果、小米可能的 FallbackHome、系统广告候选包当前状态。对不确定的包名白名单、Home 接管、开机/待机/内存清理表现，必须报告 `unverified`。**不会修改系统包。**

2. **可选：处理某个小米广告候选组件：**

先看 `full-plan` 报告；人工确认它确实是本固件的广告组件、已经备份原状态，并确认需要时仍能连接电视执行恢复。这个阶段不是所有广告都一起禁用：每个包都需要单独指定。使用报告里 `device.fingerprint` 的完整值作为明确的固件确认：

```bash
TV=192.168.1.20 bash install.sh full-apply-ads \
  --ad-package com.miui.systemAdSolution \
  --confirm-fingerprint '<来自本机 full-plan 的完整 device.fingerprint>' \
  --acknowledge-recovery
```

这项操作使用 `pm disable-user --user 0`，不会从系统分区永久删除 APK。**风险仍然存在**：即使包名看起来只是广告，也可能影响厂商的其他功能。操作前保存原已启用/已禁用状态，失败时尝试反向恢复，但 **ADB 一旦被电视固件关闭，就不能承诺自动恢复成功**。操作完必须实测各功能。此前已经禁用的包不会被 Tigris 擅自启用。

3. **恢复这次被禁用的小米广告组件：**

```bash
TV=192.168.1.20 bash install.sh full-restore-ads
```

仅重新启用这次记录为“操作前已启用”的组件。恢复结果记录在 `~/.local/state/tigris/<TV_IP>:5555/replacement.json`；恢复后再次执行变更会归档旧快照而不是覆盖恢复记录。如固件经过 OTA 更换指纹，需要人工检查后用 `tools/tv-replacement.py restore-ads --allow-firmware-change`。

注意：`install.sh restore` 负责恢复之前记录的原厂 HOME。它与 `full-restore-ads` **是两项不同的恢复动作**，不能把只恢复广告包误认为原厂桌面已恢复。

## 第二阶段：小米原生 HOME 解析、试切换与激活

第二阶段加入 `tools/xiaomi_home.py`，**只针对检测到的 Xiaomi / Redmi Android 固件**执行原生 HOME 路由操作。TCL、Sony、海信、酷开等品牌绝不会运行小米专属 HOME 写操作。

- `full-home-inspect`：只读检查 Tigris 是否作为 HOME 候选、当前真正解析的是 PatchWall / FallbackHome / Tigris、Home Guard 是否启用。仅从 resolver 输出**不能证明**小米包名白名单存在或不存在。
- `full-home-probe`：仅在已有原生 PatchWall HOME、安装的是带 HOME 声明的标准版 Tigris、Home Guard 已关闭、原厂桌面仍可用、用户提供完整固件指纹与风险确认后，临时调用 `cmd package set-home-activity`，从系统设置界面发送 ADB Home 并观察焦点，**完成后无论结果如何恢复原始 HOME 设置**。失败时保留恢复快照。
- `full-home-activate`：满足相同条件且 HOME 解析和连续的 ADB Home 前台检查均通过，才**保留** Tigris 的默认 HOME 首选项；不禁用原厂组件。此时只能报告“候选通过、等待实体按键/冷启动/待机验证”，不能保证开机后仍保持。
- `full-home-restore`：依据当前设备与原固件快照恢复之前原生 HOME。若失联或解析未恢复，保留文件并明确报告需要人工恢复。每次已成功恢复的旧快照都会归档，防止记录被覆盖。

先从 `full-home-inspect` 和 `full-home-probe` 开始；`full-home-activate` 属于自愿的实验性步骤，不要无确认地在用户设备上执行：

```bash
TV=192.168.1.20 bash install.sh full-home-inspect
TV=192.168.1.20 bash install.sh full-home-probe \
  --confirm-fingerprint '<full-home-inspect 所示的完整固件指纹>' \
  --acknowledge-recovery
# 仅在 probe 返回 native_home_trial_candidate 且恢复结果为 true 后，
# 用户明确同意时才执行：
TV=192.168.1.20 bash install.sh full-home-activate \
  --confirm-fingerprint '<同一固件指纹>' \
  --acknowledge-recovery

# 实际回退：先在未重启、ADB 仍联通时测试
TV=192.168.1.20 bash install.sh full-home-restore
```

**特别注意：**
- 试切换和保留 Home 都需要 **Standard APK**，不能对不声明 HOME 的 Guard 版强行运行。
- 小米部分固件已存在“`set-home-activity` 成功仍回 PatchWall”的实例；更有 HyperOS TV 3 用户发现禁用 PatchWall 会造成 Home 无响应，必须重新启用原厂包并恢复首选记录（见 [clean your tv #1355](https://github.com/Gathin23/cleanyourtv/issues/1355)）。不能依靠模拟 Home 的短暂结果承诺真正的长期替换。
- **绝不在此流程自动执行** `pm uninstall --user 0 com.mitv.tvhome`、`pm disable-user --user 0 com.mitv.tvhome`、禁用整个 `com.android.tv.settings` 或更改包名；没有本机的救援验证之前不开放此类高风险固件操作。
- 如果实际 Home 仍然回小米桌面，输出 `native_home_not_proven`，现有可选 Guard 仍可作为过渡方案，不能在报告里冒充原生 HOME 解析成功。
- 只有**实体 Home、重启、待机唤醒、系统设置/HDMI/投屏和原厂恢复**等实机测试完成，才能为对应机型和固件建立“完整替换已验证”的档案。

## 小米技术研究：tv-fresh 的参考价值

参考 [wangrongding/tv-fresh](https://github.com/wangrongding/tv-fresh) 在特定 MIUI TV Android 9 设备上的逆向结果：`set-home-activity` 成功并不代表 HOME 路由生效；PatchWall 和 `com.android.tv.settings` 下的 FallbackHome 可能参与竞争。**这不是所有 MIUI / HyperOS 固件共有的已验证机制**。

**第一、二阶段都不提供自动禁用 PatchWall、`com.android.tv.settings` 或 FallbackHome 的命令。** 只有在具体固件上实际验证黑屏、ADB 断联后的恢复通道，才能考虑相应组件级策略。现已加入不禁用系统组件的原生 HOME 试切换与可选激活；失败时仍可使用现有 Home Guard，不能声称已完全替换。

## 统一验收表（每项通过 / 失败 / 未验证）

| 项目 | 方法 | 不能误判为通过的情形 |
|---|---|---|
| 安装 | 包名、签名、版本、APK flavor 检查 | 仅下载 APK |
| Home | 从其他 App 按遥控器**实体** Home | 只看 `set-home-activity Success` 或 ADB 合成 Home |
| 冷启动 | 经用户许可重启 | 仅打开 Tigris Activity |
| 待机唤醒 | 系统唤醒后不手动补救 | 只有守护状态显示“已启用” |
| 清理内存 | 使用电视清理器后测 Home | 强制停止的 App 未自动恢复 |
| 系统广告 | 观察开机、主屏、系统弹窗广告 | 只根据 `pm disable-user` 成功 |
| 系统功能 | 网络、设置、音量、HDMI、投屏、OTA | 未对真实功能做验证 |
| 回滚 | 原厂 HOME 和必要组件恢复、实体 Home 验证 | 只有可复制的恢复命令 |

Tigris 不能在没有设备或相应授权时自动完成实体按键、待机或冷启动验证。Agent 应报告“不支持 / 未验证”，而不是把这些项目标成成功。
