# Sony 50.119 空闲直接待机/关机：根因与修复

设备：Sony BRAVIA 4K VH2，Android 12 / API 31。日期：2026-11-02。

## 电视实机根因证据

保持原配置（应用相框 10 分钟、系统 screen_off_timeout=600000）在桌面自然等待，主机持续保存电源日志。用户报告刚刚再次待机；日志完整覆盖该次事件：

```text
12:04:12.243 TigrisBoot: event=resume ...
12:14:12.238 PowerManagerService: Nap time (uid 1000)...
12:14:12.245 DreamController: Starting dream: ...com.leslie.tvhome.PhotoDream
12:14:12.313 TigrisFrame: dismissing system photo dream: idle 32ms is below 600000ms
12:14:12.336 PowerManagerService: Going to sleep due to timeout (uid 1000)...
12:14:12.337 PowerManagerService: Sleeping (uid 1000)...
12:14:18.815 PowerManagerService: Waking up from Asleep ... WAKE_REASON_POWER_BUTTON
```

系统确实正常启动了屏保。Tigris 又用自己的主屏幕计时器检查已由系统触发的屏保；梦境接管窗口的生命周期切换把应用记录的空闲时间重置成 32 ms，PhotoDream 因此主动结束。系统空闲已超时，所以立即休眠，屏保几乎不可见。此缺陷在桌面已真实复现。

在独立 API 30 Android TV 模拟器停留于 Tigris 设置页，也复现：系统触发 PhotoDream → `Tigris home screen is not in front` → 梦境结束 → `Going to sleep due to timeout`。

另一个代码缺陷解释为何用户启用「按电源键彻底关机」后现象加重：KeeperService 直接把任何 ACTION_SCREEN_OFF 转为关机。这个广播同样由自动超时、系统待机产生，不能证明按过电源键。本次捕获时用户已切回「待机」，因此日志证明的是自动待机；没有声称捕获了旧开关开启时的实际 shutdown 命令。

## 修复

- 保留主屏幕自身的相框计时和播放检测；系统屏保使用系统已作出的空闲判断，不再次要求主屏幕独立计时器达到分钟数。
- 用活动生命周期跟踪 Tigris 自己可见的 Home、Settings、Updates、Wallpaper 页面，在梦境附着接管焦点时允许屏保。继续排除其他前台应用和可检测的外部播放，不把系统屏保扩展成任意抢占窗口。
- 全关机仍需用户启用选项及已有本机调试授权，但还必须在同一调试连接里读取系统电源状态：最近 5 秒、明确的 `power_button` 休眠、设备仍未唤醒，才允许关机。`timeout`、`inattentive`、应用、HDMI、未知原因一律不转成全关机。收到屏幕唤醒事件即取消排队检查；证据不可读取时不猜测原因。
- 保留上次更新安装修复及原设置键，使用同签名 `2.0.18-idlefix.1`（versionCode 25），无需卸载或清理数据。

## 验证

- Standard、Guard 各 158 项单元测试通过，共 316 项；包括超时/HDMI/未知休眠不能关机、唤醒取消、过期电源键证据无效以及 32 ms 计时回归。
- API 30 模拟器相同 30 秒系统超时下，修复前 Tigris 设置页直接休眠；修复后设置页和桌面均进入并保持 PhotoDream，电源状态为 Dreaming。
- 未通过关机/重启用户电视验证物理电源键功能；本次不发送电视关机、休眠或重启命令。

## 修复版实机验收

安装同签名 `2.0.18-idlefix.1`，保留桌面、应用和数据。测试前系统屏保触发时间为 600000 ms，临时缩短为 60000 ms，应用自身相框仍为原 10 分钟。

用户按电源键从旧版留下的待机状态开屏后，在 Home 上不操作：系统进入 PhotoDream，12:27:55 读取 `mWakefulness=Dreaming`、`mCurrentDreamIsWaking=false`；截图为完整照片、时钟和日期。没有再因应用记录的分钟数不足而退出。这也验证系统计时早于应用计时时不会触发旧缺陷。

12:28:33 退出第一轮屏保，打开 Tigris Settings；读取窗口确认 SettingsActivity 为前台。第二轮不操作，同样进入 PhotoDream；12:35:37 仍为 `mWakefulness=Dreaming`、`mCurrentDreamIsWaking=false`，实机截图为完整照片/时间/日期，未进入休眠。随后写回并读回 `screen_off_timeout=600000`，恢复原十分钟；应用相框分钟数及用户电源键选择没有修改。

实机截图位于 `C:/work/appletv/.tmp/tv50119-updates/idlefix-home.png`、`idlefix-settings.png`。最终版本 `2.0.18-idlefix.1`。桌面、设置两条路径通过缩短系统超时的自然空闲测试；没有使用手动“启动屏保”代替超时验收。

交付文件：`C:/work/appletv/.tmp/tv50119-updates/Tigris-v2.0.18-idlefix.1.apk`。
SHA-256：`32aafadb788e6648b9bb5e1e36e3a801f1577a062642fedd770a6bcf75176c62`。
签名 SHA-256：`7d2b6be8a86ce8981e368dde4920869b90c8206137934a6d1fc89a2d1d1b6114`。

## 官方机制依据

[DreamService.finish()/wakeUp()](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/service/dreams/DreamService.java)：结束梦境后，若没有用户活动或保持唤醒的条件，系统可以完全休眠；wakeUp 是梦境退出过程，不是 PowerManager 的设备唤醒保证。

[PowerManagerService.handleSandman()](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/power/PowerManagerService.java)：梦境已结束且仍满足超时条件时，调用系统休眠。

[PowerManager.sleepReasonToString()](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/os/PowerManager.java)：区分 `power_button`、`timeout`、`application`、`hdmi` 等原因。
