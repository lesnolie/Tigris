# 192.168.50.119 更新安装卡住：诊断与恢复

日期：2026-11-02。Sony BRAVIA 4K VH2，Android 12，Tigris 2.0.18（versionCode 25）。屏保问题已按用户要求暂停；测试时临时修改的相框空闲时间已恢复为原来的 10 分钟。

## 已确认的直接原因

两项更新都停在 `pending`，保存着确认请求。系统中对应的旧会话仍存在：HGTV 为 1488890721，投屏为 1680917317；两者进度为 0，均已 sealed，投屏会话尚未 committed。点击红果的「继续安装」可以启动系统安装器，但安装器立即报告 `Session -1 in funky state; ignoring` 并退出。

这里的 `-1` **不能证明请求中的编号丢失**。Android 12 官方实现先查询 SessionInfo，只有通过有效性检查后才给成员 mSessionId 赋值；会话不存在、未 sealed 或 resolvedBaseCodePath 为空都可产生该日志。电视 dumpsys 显示旧会话已 sealed，故现有任务仍无法被安装器正常使用。

进一步在独立 Android TV API 30 模拟器中复现：先创建完整安装任务并获得确认请求，重启前可显示确认页；重启模拟器后，完全相同的请求立刻产生 `Session -1 in funky state; ignoring`。重新提交同一个会话后，进度从 0 回到 0.8，系统重新回传确认请求，安装页可正常打开，确认后返回 `INSTALL_SUCCEEDED`。这证明重启后的会话恢复缺失足以产生电视上的相同症状；没有原始中断时的日志，不能追溯两个旧任务最初的具体中断时间。

源码依据：[Android 12 PackageInstallerActivity](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/packages/PackageInstaller/src/com/android/packageinstaller/PackageInstallerActivity.java)。

机制依据：[PackageInstallerSession.onAfterSessionRead](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/pm/PackageInstallerSession.java) 对普通会话不重新解析 APK；[Session.commit() 文档](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/content/pm/PackageInstaller.java) 明确支持重启后重新提交未完成会话。应用应重新提交并使用新回调，而不是永久重放旧 Activity。

Tigris 的 `pending()` 只检查 mySessions 是否包含同包任务；`confirm()` 只验证任务存在且确认 Activity 可启动，不验证该页面能否完成安装。更新页又缺失提示中提到的「取消旧任务并重试」入口，于是用户只能反复打开被系统拒绝的旧任务。

## 本次电视恢复结果

从各应用官方 GitHub Release 下载，校验发布 SHA-256、包名、APK 签名后通过 `adb install -r` 覆盖更新，未卸载应用或清理应用数据。

| 应用 | 之前 | 恢复后 | 官方发布 SHA-256 |
| --- | --- | --- | --- |
| MiracastReceiver | 1.2.8 / code 13 | 1.2.9 / code 14 | 950d8ab96b390364c67039653d421ee243485d7f71743b288d9e44f9cd4d0f5a |
| HGTV | 1.2.6 / code 63 | 1.2.9.1 / code 67 | 813d88e2a91cc6b1a9f876f14c1d03c49a476893bff5fc48c8854f2c77764aab |

HGTV 现装包与新版的签名都为 `27da90725c2bcec6c334096ee438877b480875dfaa1339b5dbedc5fd9fcee019`，与仓库记录的首次安装固定证书不同。此次已通过实际现装包证明可覆盖升级；没有修改产品的首次安装信任策略。

更新完成后点击 Tigris「检查更新」，应用自身清除了旧任务。实机验证：两个应用显示「最新版本」「打开」，系统 Active install sessions 为空。未测试两个应用的播放或投屏功能。

## 安装流程修复

- 等待安装确认时增加「强制重试」，只清理选定应用的安装任务，保留原应用和数据；复用已有完整 APK 或重新下载，之后仍执行原有哈希、包名、版本和签名校验。
- 「继续安装」重新提交本应用拥有且编号与预期一致的会话，由系统重新解析并回传新确认页；通知也经过更新页恢复，不直接重放旧页面。
- 已验证 APK 保存在应用私有 files/updates 中，打开确认页不再删除；安装成功后才清理，重试复用前再次核对发布大小和哈希。
- 取消会话前同步清除预期会话编号，避免异步失败回调覆盖后续任务状态。
- 检查发现已是最新版本时，即使系统已回收旧会话，也清除遗留 pending 界面状态。
- 返回更新页时核对实际安装版本与确切会话编号，修复丢失回调、进程退出后遗留的下载/准备状态，缺失会话显示可重试状态。

构建为同签名专用修复版 `2.0.18-updatefix.1`，versionCode 仍为 25，可保留数据覆盖 2.0.18，后续更高 versionCode 的正式版仍可覆盖。已覆盖安装到电视，确认更新页正常、两个可选应用仍显示最新版本及打开。未修改屏保代码或设置，未对电视执行重启/待机测试。

验证：两种构建的单元测试、构建、发布版/Guard 静态检查和 `git diff --check` 通过。测试工具仅放在电脑的独立目录和模拟器；没有将测试端点或测试包加入电视的生产应用。

## 最终回归证据

- Standard 和 Guard 各 154 项单元测试全部通过（合计 308 项）；发布版构建、发布版与 Guard 静态检查通过。
- API 30 Android TV 模拟器使用无功能、无原生库的旧版/新版测试目标，沿用可选应用包名和生产 UpdateResult 回调路由。真实投屏 APK 不包含模拟器所需 x86 ABI，因此这里不声称测试了投屏功能或其官方 APK 的模拟器兼容性。
- 直接调用真实 Updates 的强制重试代码：会话 620961533 替换为 1715864280，旧会话取消结果未污染新状态，日志记录 `reusing verified download`，未重下 APK。
- 模拟器重启后，在真实更新页点击 Continue；会话 1715864280 被重新提交，显示有效系统确认页。点 INSTALL 后实际版本由 1.2.7/code 12 变为 1.2.9/code 14，真实成功回调把 phase 改为 done、available=false，卡片显示 Up to date / Open。
- 模拟系统丢失会话：生产 reconcile 清除旧编号和确认请求，phase=failed，状态明确提示安装中断、可重试。下载文件仍保留。
- 电视最终保留原投屏 1.2.9、红果 1.2.9.1，更新页两个应用均显示最新版本和打开。安装故障恢复机制的重启测试在独立模拟器完成；没有重启电视、降级电视上的第三方应用或清除用户数据。

交付 APK：`C:/work/appletv/.tmp/tv50119-updates/Tigris-v2.0.18-updatefix.1.apk`。
SHA-256：`76a67a8f58aeded617f78ebe66332f3073b6879fe361636f5d78d1a485162d3e`。
签名 SHA-256：`7d2b6be8a86ce8981e368dde4920869b90c8206137934a6d1fc89a2d1d1b6114`，与现装正式版一致。
