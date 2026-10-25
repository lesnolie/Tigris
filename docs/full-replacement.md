# Tigris Full Replacement / 完整替换模式（第一阶段）

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

## 小米技术研究：tv-fresh 的参考价值

参考 [wangrongding/tv-fresh](https://github.com/wangrongding/tv-fresh) 在特定 MIUI TV Android 9 设备上的逆向结果：`set-home-activity` 成功并不代表 HOME 路由生效；PatchWall 和 `com.android.tv.settings` 下的 FallbackHome 可能参与竞争。**这不是所有 MIUI / HyperOS 固件共有的已验证机制**。

**本阶段不提供自动禁用 PatchWall、`com.android.tv.settings` 或 FallbackHome 的命令。** 下一阶段若要在某个固件提供“真正移除原厂桌面竞争”的高级接管，必须先记录固件指纹、HOME 候选、ADB/原厂设置恢复入口，在实机完成黑屏与断开 ADB 的救援验证，再设计具体固件的可选事务步骤。否则仍使用现有 Home Guard，并明确告知不是原生 HOME 完整替换。

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
