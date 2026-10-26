# Tigris AI adaptive installation / AI 自适应安装策略

## 产品目标

安装 Tigris 后，尽可能由系统原生 HOME 直接进入 Tigris，不再依赖原厂桌面抢回；
在用户授权且能够恢复的前提下减少系统级广告与不必要的后台负担，
保留设置、HDMI、投屏、声音、OTA 和恢复原厂桌面的能力。

**回归红线**：不得为修复 Xiaomi/Redmi 修改已经成功的 Sony、TCL/雷鸟正常路径、APK 行为、
原有守护机制或已通过的功能。只有明确证据证明有问题，才在对应固件下选择处理方案。

## 核心原则：按能力和实测表现路由，而非按机型复制脚本

1. **识别**：先确认 Android 或其他 OS，再读取 manufacturer / brand / model / fingerprint / SDK / firmware。
   名称只能用于选择调查方向，不能单凭“MIUI”或“HyperOS”认定 HOME 机制。
2. **无损诊断**：读取 HOME 候选、resolver、是否安装 Tigris Standard、是否开启 Guard、
   PatchWall / FallbackHome 以及后台限制线索。
3. **选择**：先用已经成功的标准安装及验证路径。Xiaomi 只有在 live diagnostics 观察到
   PatchWall 是当前 HOME、Tigris 是候选、Guard 不干扰时，才向用户建议可恢复的 HOME probe。
4. **分步验证**：合成 ADB Home 与实体遥控器 Home、冷开机、待机唤醒、清理内存分别记录；
   没有实机就保持 unverified，不得写成兼容成功。
5. **广告与性能单独处理**：广告包名来自 GitHub 只能当候选，不能因为机型相似就自动禁用。
   每一项先确认存在、具体作用、固件差异和恢复方式，再由用户决定。不得假设禁用越多越流畅。
6. **恢复**：保留原厂 HOME、核心系统组件和操作前状态。对于无法恢复或诊断不完整的固件，
   不执行影响系统可用性的变更。

## AI 运行入口

`TV=<IP> bash install.sh full-plan`

报告新增 `adaptive_install`：提供 live 观察、下一步建议及明示的“未验证”状态。
它是**只读建议**，不会安装 APK、修改 HOME、禁用任何广告组件或代替用户确认。
AI 必须根据实际报告继续工作，不得从历史 GitHub 案例直接跳到风险操作。

| 观察结果 | AI 下一步 | 自动风险操作 |
| --- | --- | --- |
| Sony / TCL / 雷鸟 / 其他非小米品牌 | 复用既有安装、HOME 验证与守护兜底 | 无 |
| 小米尚未安装 | 安装官方签名 Standard，再重新诊断 | 无 |
| 小米 Tigris 不在 HOME 候选 | 检查 APK flavor、Activity 声明、固件过滤 | 无 |
| 小米 native resolver 已指向 Tigris | 待实机 Home、开机、唤醒、清理器验证 | 无 |
| 小米 PatchWall 当前占有 HOME，Tigris 为候选 | **建议**用户授权 `full-home-probe`，成功也先恢复 | 无 |
| 小米 FallbackHome / 其它 / 解析不明 | 只读收集证据，保持原厂可用 | 无 |

`full-home-activate` 必须在 probe 成功之后另行明确授权；它只能保留 HOME preference，
不能以此证明实体遥控器 Home、重启、广告移除或性能优化。

## 外部项目是案例证据，不是机型通用白名单

- [wangrongding/tv-fresh](https://github.com/wangrongding/tv-fresh)：MiTV-ASTP0 / Android 9 的
  HOME 冲突与广告清理经验。其禁用 PatchWall / Settings 宿主的方案不能在 Tigris 跨设备自动执行。
- [cleanyourtv 的 MiTV-ANSP0 档案](https://github.com/Gathin23/cleanyourtv/blob/main/docs/devices/xiaomi-mitv-ansp0-jobs.txt)：
  这台 Android 9 设备**保留 PatchWall 启用**就能长期使用首选 HOME；盲目禁用反而会进入 FallbackHome。
- [HyperOS TV 3 / MiTV-MFTR0 #1355](https://github.com/Gathin23/cleanyourtv/issues/1355)：
  已报告 `set-home-activity` 返回成功但仍进 PatchWall，甚至存在禁用后实体 Home 不响应的案例。
- [Redmi MiTV-ASTR0 档案](https://github.com/Gathin23/cleanyourtv/blob/main/docs/devices/xiaomi-mitv-astr0-blueplanet.md)：
  不同 HyperOS 固件存在特殊 HOME 身份筛选迹象；不应自动尝试包名伪装或重签名迁移。

建立设备知识库时，每条规则应有 `source_url`、`model`、`fingerprint/firmware`、
`Android SDK`、`observed behavior`、`recovery proof` 和 `evidence level`。
只读历史报告可以辅助诊断；只有满足适用固件条件并经过本机验证，才能升级为经验证的动作。

## 验收边界

**回归：** Sony 安装/HOME/遥控器/HDMI、TCL/雷鸟成功案例和 Guard fallback 均维持原行为。
本变更只新增 `full-plan` 报告字段与独立单元测试，不修改 Standard/Guard APK、
通常的 `install`、`verify`、`restore` 命令执行流程。

**实机：** 连接未知小米固件时首先只读；需要时提示用户拿实体遥控器验证；
没有真机就只能写“未验证”，不能声称广告全清或系统加速。
