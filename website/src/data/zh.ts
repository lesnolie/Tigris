import type { Copy } from "./lang";
import { brand, cmds } from "./site";

/* Local alias keeps the copy readable. */
const C: typeof cmds = cmds;

export const zh = {
    skip: "跳到主要内容",
    navLabel: "主导航",
    menuLabel: "打开菜单",
    menuCloseLabel: "关闭菜单",
    nav: [
      { href: "#features", label: "功能" },
      { href: "#aerial", label: "航拍屏保" },
      { href: "#compare", label: "前后对比" },
      { href: "#install", label: "安装" },
      { href: "#webos", label: "LG webOS" },
      { href: "#compatibility", label: "兼容性" },
      { href: "#faq", label: "常见问题" },
    ],
    langSwitch: { href: "/en/", label: "EN", lang: "en" },
    headerCta: "前往下载",
    menuCta: "下载 Tigris APK",
    menuNote: "Android TV 9+ · 不需要 Root · 独立开源项目，与 Apple 无关联",
    copy: "复制",
    copied: "已复制",
    copyFailed: "复制失败",
    compareLabel: "对比国产电视桌面截图与 Tigris 电视实录",
    compareValue: (p: number) => `显示 ${p}% 的安装前画面`,
    slideshow: { prev: "上一张", next: "下一张" },
    lightbox: { close: "关闭", prev: "上一张", next: "下一张", name: "截图查看器" },
    year: 2026,

    meta: {
      title: "Tigris — 让电视桌面，重新惊艳。| Android TV 桌面",
      description:
        "Tigris 是一个开源的 Android TV 桌面：液态玻璃 Dock、Top Shelf 顶部内容、毛玻璃控制中心、Apple TV 航拍视频屏保与风景壁纸。支持 Android 9+，无需 root，索尼 BRAVIA 实机验证。",
      canonical: `${brand.site}/`,
      alts: [
        { hreflang: "zh-CN", href: `${brand.site}/` },
        { hreflang: "en", href: `${brand.site}/en/` },
        { hreflang: "x-default", href: `${brand.site}/` },
      ],
      ogLocale: "zh_CN",
      ogLocaleAlt: "en_US",
      ogTitle: "Tigris — 让电视桌面，重新惊艳。",
      ogDescription:
        "开源 Android TV 桌面：液态玻璃 Dock、Top Shelf、控制中心、Apple TV 航拍屏保。Android 9+，无需 root。",
    },

    hero: {
      eyebrow: "TIGRIS · ANDROID TV LAUNCHER",
      titleLines: ["让电视桌面，", "重新惊艳。"],
      sub: "把熟悉的 Android TV 换成 tvOS 般的沉浸入口。沉底的液态玻璃 Dock、会呼吸的顶部内容，还有 Apple TV 航拍视频做屏保——Tigris 把客厅的那块屏幕，重新变回它该有的样子。",
      primary: "前往下载",
      secondary: "查看安装方法",
      chips: ["Android TV 9+", "不需要 Root", "部分功能因电视品牌而异"],
      note: "独立开源项目，与 Apple 无任何关联。「Apple TV」为 Apple Inc. 的商标。",
      imageAlt:
        "Tigris 主屏幕实拍：风景壁纸上是液态玻璃 Dock，排列着云视听小电视、云视听极光、芒果 TV 与斗鱼等应用图标。",
      floating: [
        { title: "Liquid Glass", sub: "沉底玻璃 Dock" },
        { title: "航拍屏保", sub: "Apple TV 同款航拍视频" },
      ],
    },

    proof: [
      "索尼 BRAVIA 实机验证",
      "Apple TV 航拍屏保",
      "标准版 / 守护版两个 APK",
      "不需要 Root，随时可回退",
    ],

    compare: {
      eyebrow: "BEFORE / AFTER",
      title: "左右滑动，看见改变。",
      lede: "左边是用户提供的<strong>国产电视桌面截图</strong>，右边是 Tigris 的<strong>电视实录</strong>。拖动分界线，看看两种桌面的不同。",
      beforeLabel: "安装前",
      beforeSub: "国产电视 · 截图动态展示",
      afterLabel: "安装后",
      afterSub: "Tigris · 电视实录",
      beforeAlt: "用户提供的国产电视桌面截图，轻微平移缩放展示。",
      afterAlt: "Tigris 主屏幕实拍，风景照片上是液态玻璃 Dock。",
      hintKeys: "拖动、点击或聚焦把手后用 ← → 调整，Shift 加速，双击回到中间。",
      disclaimer: "左侧为用户提供的截图的动态展示，不是操作录像；两侧不是同一设备。",
    },

    aerial: {
      eyebrow: "AERIAL SCREENSAVER",
      title: "Apple TV 航拍，成为你的屏保。",
      lede: "空闲时，电视缓缓滑过地球：海岸线、冰川与城市在屏保里依次展开，地点名随镜头变化。左右键切换镜头，断网自动回到照片，不打断正在播放的视频。",
      bullets: [
        "可选「航拍视频」作为屏保内容，画面来自 Apple 服务器在线播放（约 15–20 Mbps）",
        "断网或缓冲失败时自动切回照片屏保，不影响下一次空闲进入",
        "遥控器闲置 1–30 分钟后自动进入「相框」，时间和照片说明也会做出防烙印的位置变化",
      ],
      caption: "相框屏保 · 必应每日照片（演示：照片模式）",
      frameAlt: "相框屏保实拍：水下章鱼照片，左下角是时间、日期与照片说明。",
      bulletsTitle: "怎么开启",
      attribution:
        "航拍视频由 Apple 的服务器在线提供，Tigris 不包含任何视频文件；「Apple TV」为 Apple Inc. 的商标。本项目与 Apple 无关联。",
    },

    features: {
      eyebrow: "FEATURES",
      title: "为十英尺距离重画一遍。",
      lede: "不是把手机界面放大，而是为客厅重新排版：更大的焦点、更少的层级、更安静的玻璃。",
    },

    film: {
      eyebrow: "FILM",
      title: "42 秒，认识 Tigris。",
      lede: "看看 Tigris 如何让你的电视桌面焕然一新。点击播放，开启声音。",
      label: "播放 Tigris 介绍片（有声音）",
    },
    gallery: {
      eyebrow: "IN MOTION",
      title: "让细节，动起来。",
      lede: "桌面、控制中心和航拍来自索尼 BRAVIA 实录；开机动画为项目所有者提供的默认素材预览。点击放大，观看完整片段。",
    },

    install: {
      eyebrow: "INSTALL",
      title: "选一种方式，把 Tigris 带回家。",
      lede: "一台和电视连在同一个 Wi-Fi 的电脑、电视的 IP 地址、打开的 ADB 调试，就够了。支持 Android 9 及以上，不需要 root。",
      packages: [
        {
          name: "标准版 APK",
          use: "Android 9+ 首先尝试：Sony BRAVIA、原生 Android TV / Google TV、小米 / Redmi 及可运行 Android 的其他电视。文件 Tigris-vX.Y.Z.apk；成功安装不代表实体 Home 已接管。",
          href: brand.apk,
        },
        {
          name: "兼容守护版（Guard）APK",
          use: "主要针对 TCL / 雷鸟等拒绝标准 HOME APK 的 Android 固件；不声明 HOME，安装后需授权守护。文件仍是 Tigris-guard-vX.Y.Z.apk，部分 TCL 需用电视「多屏互动」推送。不是所有 TCL 都必须使用。",
          href: brand.apkGuard,
        },
      ],
      prereq:
        "开始之前：电脑与电视在同一个局域网；电视已打开 ADB 调试；首次连接时电视会询问「允许 USB 调试吗？」，请勾选「始终允许」。电脑需要 Android SDK Platform-Tools。",
      shellNote:
        "先选择电脑系统，查看对应的准备步骤；再选择电视品牌。下方命令会同步切换，按顺序逐条运行。",
      shellLabels: { posix: "macOS / Linux", powershell: "Windows PowerShell" },
      placeholderNote:
        "示例中的 192.168.x.x 是占位符，请换成电视的实际 IP；BRAVIA 注册步骤里的 PIN 是电视屏幕上显示的四位数字，请按提示替换。",
      adbTable: {
        caption: "不同品牌电视开启 ADB 调试的方式",
        head: ["电视", "怎么打开 ADB"],
        rows: [
          [
            "小米 / Redmi",
            "设置 → 关于 → <strong>产品型号</strong>，连续按确认键进入开发者模式；再到 设置 → 账号与安全，打开 <strong>ADB 调试</strong> 和 <strong>安装未知来源的应用</strong>。",
          ],
          [
            "TCL / 雷鸟",
            "设置 → 关于 → <strong>本机信息</strong>，用遥控器依次按 <strong>上、下、左、右</strong>（间隔不超过 1 秒），底部出现「更多」，在其中打开 <strong>ADB</strong>。",
          ],
          [
            "索尼 BRAVIA",
            "设置 → 系统 → 关于 → 在 <strong>版本</strong> 上连按确认键 7 次；再到 设置 → 系统 → 开发者选项，打开 <strong>USB 调试</strong>。",
          ],
          [
            "原生 Android TV / Google TV、海信等",
            "设置 → 设备偏好设置 → 关于 → 连续按 <strong>版本号</strong> 7 次；再到 开发者选项，打开 <strong>USB 调试</strong>（也可能叫「网络调试」或「ADB 调试」）。",
          ],
        ],
      },
      tabsLabel: "选择电视品牌",
      tabs: [
        {
          id: "sony",
          label: "索尼 BRAVIA",
          intro:
            "完整验证的路径，包括亮度、电视音量和 HDMI 输入源。下面以 192.168.x.x 代指电视 IP。",
          steps: [
            {
              title: "连接电视",
              body: "在电脑上运行；电视出现授权框时选「允许」并勾选「始终允许」。",
              cmd: C.connect,
            },
            {
              title: "记录原来的桌面、屏保与无障碍服务",
              body: "卸载时需要把它们恢复，请先保存到记事本。",
              cmd: C.snapshotBeforeInstall,
            },
            {
              title: "下载并安装标准版",
              cmd: C.installStandard,
              note: "命令已适配上方选择的电脑系统。",
            },
            {
              title: "设为默认桌面并授权",
              body: "三条权限分别用于：守护与屏保、被杀后复活、开机后台打开主屏幕。",
              cmd: C.setHomeAndGrant,
            },
            {
              title: "可选：注册 BRAVIA IP 控制",
              body: "要用亮度、电视音量和 HDMI 输入源，需要注册一次。第一条命令后电视会显示 4 位 PIN，用它完成第二条。",
              cmd: C.braviaRegister,
              note: "此步骤分两次执行；看到电视上的 PIN 后，再替换 PIN 执行第二次请求。",
            },
            {
              title: "允许本机调试",
              body: "首次打开 Tigris 会请求本机调试授权，用于待机、关机、重启和彻底清理后台。电视上勾选「始终允许」，再打开控制中心确认不再显示「本机调试未授权」。",
            },
            {
              title: "截图验证",
              cmd: C.verify,
              note: "应当看到右上角的紧凑时钟、两个圆形按钮，以及底部玻璃 Dock。",
            },
          ],
        },
        {
          id: "tcl",
          label: "TCL / 雷鸟",
          intro:
            "国行固件拒绝普通的 adb install，也会拒绝声明桌面的 APK。请使用 guard 版，通过电视自带的「多屏互动」推送安装。",
          danger:
            "部分 TCL / 雷鸟固件没有可用的背光接口，亮度滑条会隐藏；输入源一栏会打开 TCL 自己的信号源选择器。这是系统限制，不是安装失败。",
          steps: [
            { title: "连接电视", cmd: C.connect },
            {
              title: "记录原来的桌面、屏保与无障碍服务",
              body: "卸载时需要用它们恢复。",
              cmd: C.snapshotBeforeInstall,
            },
            {
              title: "用多屏互动推送 guard 版",
              body: "脚本通过本机 8765 端口提供 APK，并连接电视的 6553 端口。电视角落出现「多屏互动：正在发起应用推送」时，用遥控器选择「同意」。",
              cmd: C.tclPush,
              note: "命令已适配所选电脑系统；防火墙提示请允许局域网访问 8765 端口。",
            },
            {
              title: "确认已安装",
              cmd: C.packagePath,
            },
            {
              title: "授权并打开桌面守护",
              body: "guard 版不声明 HOME，靠「桌面守护」保持在前台。TCL 默认拦截第三方 App 开机自启，需要同时放行两个 appops。",
              cmd: C.tclGrants,
            },
            {
              title: "检查守护是否在运行",
              cmd: C.tclCheck,
              note: "输出中应包含 `com.leslie.tvhome/com.leslie.tvhome.HomeGuardService`，最后一行应是 `com.leslie.tvhome/.HomeActivity`。",
            },
          ],
        },
        {
          id: "xiaomi",
          label: "小米 / Redmi",
          intro:
            "小米接受 set-home-activity，但按主页键仍可能回到 PatchWall。正确做法是保留原厂桌面，用「桌面守护」把 Tigris 拉回前台。",
          danger:
            "<strong>绝对不要停用小米自带桌面</strong>（com.mitv.tvhome）。停用后开机黑屏，只能通过 ADB 恢复。",
          steps: [
            { title: "连接电视", cmd: C.connect },
            {
              title: "记录原来的桌面、屏保与无障碍服务",
              cmd: C.snapshotBeforeInstall,
            },
            {
              title: "安装标准版",
              cmd: C.installStandard,
              note: "命令已适配上方选择的电脑系统。",
            },
            {
              title: "设为默认桌面并授权",
              cmd: C.xiaomiSetup,
              note: "设置后先运行上方统一检查器；Home 已正常则跳过守护。",
            },
            {
              title: "仅 Home 检查失败时启用守护",
              body: "keep_adb true 让 Tigris 在小米唤醒后自动把 ADB 重新打开，方便以后升级；不需要可以省略这个参数。",
              cmd: C.xiaomiGuard,
              note: "仅实际 Home 失败时执行；keep_adb 是单独的可选选择。",
            },
            {
              title: "检查",
              body: "按主页键，原厂桌面可能闪现约半秒，然后回到 Tigris。若守护没有启动，可在 设置 → 桌面守护 中手动打开（需要先完成本机调试授权）。",
            },
          ],
        },
        {
          id: "stock",
          label: "原生 Android TV",
          intro:
            "标准路径。安装后 Tigris 成为默认桌面，不修改系统分区，也不卸载任何应用。",
          steps: [
            {
              title: "连接并确认系统版本",
              cmd: C.connect,
              note: "再运行 `adb -s 192.168.x.x:5555 shell getprop ro.build.version.sdk`，返回值应不小于 28（Android 9）。",
            },
            {
              title: "记录原来的桌面、屏保与无障碍服务",
              cmd: C.snapshotBeforeInstall,
            },
            {
              title: "安装并设为默认桌面",
              cmd: C.stockInstall,
              note: "命令已适配上方选择的电脑系统。",
            },
            {
              title: "授予只有 ADB 能给的权限",
              cmd: `adb -s 192.168.x.x:5555 shell pm grant com.leslie.tvhome android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.x.x:5555 shell cmd notification allow_listener com.leslie.tvhome/com.leslie.tvhome.RevivalListener
adb -s 192.168.x.x:5555 shell appops set com.leslie.tvhome SYSTEM_ALERT_WINDOW allow`,
            },
            {
              title: "如果按主页键仍回到原厂桌面",
              body: "运行统一检查器；它会打开设置并发送 Home。失败后再配置守护，保留原厂桌面。",
              cmd: C.checkHome,
              note: "所有 Android 品牌使用同一标准；实体 Home、待机、重启分别验证。",
            },
          ],
        },
      ],
      advanced: {
        title: "高级：这些权限具体做什么",
        body: "安装命令里用 ADB 授予的三项权限，普通应用拿不到；它们只在设备本地生效，不联网传输任何内容。",
        rows: [
          {
            name: "WRITE_SECURE_SETTINGS",
            use: "系统关闭守护后重新打开、把相框设为系统屏保、小米唤醒后保持 ADB 开启",
          },
          {
            name: "通知监听（RevivalListener）",
            use: "被系统杀掉或开机后约半秒把 Tigris 重新拉起，不读取任何通知内容",
          },
          {
            name: "SYSTEM_ALERT_WINDOW",
            use: "开机后从后台打开主屏幕；不做悬浮窗广告或覆盖",
          },
          {
            name: "TCL APP_AUTO_START / APP_ASSOC_START",
            use: "只有 TCL 需要，允许第三方 App 开机自启",
          },
        ],
      },
      agent: {
        title: "让 AI 帮你安装（推荐）",
        body: "不熟悉命令行也可以：把下面这句话发给 WorkBuddy、Codex、Claude Code 等能在电脑执行命令的 Agent。它应先确认电视系统与 IP，再读取统一策略、安装验收，并保留恢复方案。",
        prompt:
          "请帮我安装 Tigris。电视 IP：<电视IP>。先阅读 https://tigris-tv.pages.dev/agent.md（备用：https://raw.githubusercontent.com/lesnolie/Tigris/main/AGENTS.md）。先确认电视是 Android、webOS 还是其他系统；非 Android 不执行 ADB/APK。Android 使用当前正式版源码中的 tools/install-plan.py 先检测，再按统一策略执行安装。保留已有配置、安装包类型和原桌面，不自动卸载、禁用系统组件或开启保持 ADB。需要电视端授权时提醒我。分别报告 APK 安装、模拟 Home、实体 Home、开机、待机和恢复结果；未测项目写未验证，不承诺去广告。",
        note: "过程中电视会弹出两三次确认框，需要你用遥控器选择「允许」。TCL 上会弹出「多屏互动：正在发起应用推送」，选择「同意」即可。",
      },
      safety:
        "<strong>不要在没有询问的情况下远程关机或重启电视。</strong>电视一旦断电，只能用遥控器重新打开。安装流程不需要卸载或停用任何系统应用。",
    },

    compatibility: {
      eyebrow: "COMPATIBILITY",
      title: "哪些功能，你的电视能用。",
      lede: "Tigris 会先尝试品牌接口，再退回 Android 标准接口。下表是当前实现的能力边界，而不是宣传口径。",
      caption: "Android TV 9+ · 标准版 / 守护版行为一致，仅桌面声明不同",
      head: ["功能", "任意 Android TV", "需要本机调试授权", "仅索尼 BRAVIA"],
      rows: [
        { feature: "主屏幕、Dock、玻璃效果、投影", any: "支持", debug: "—", sony: "—" },
        { feature: "顶部内容（B 站 / 斗鱼）", any: "支持，需装对应 TV 版 App", debug: "—", sony: "—" },
        { feature: "风景 / 二次元背景、相框", any: "支持", debug: "—", sony: "—" },
        { feature: "Apple TV 航拍视频屏保", any: "支持，需联网", debug: "—", sony: "—" },
        { feature: "控制中心界面、时钟", any: "支持", debug: "—", sony: "—" },
        { feature: "释放内存", any: "清理缓存", debug: "彻底结束后台 App", sony: "—" },
        { feature: "删除预装 App", any: "—", debug: "支持", sony: "—" },
        { feature: "待机、关机、重启", any: "—", debug: "支持", sony: "—" },
        { feature: "相框设为系统屏保、空闲时长", any: "—", debug: "支持", sony: "—" },
        { feature: "音量滑条", any: "Android 标准音量", debug: "—", sony: "电视音量" },
        { feature: "HDMI 输入源切换", any: "大多数电视", debug: "—", sony: "并显示当前输入源" },
        { feature: "亮度（背光）滑条", any: "小米，需 ADB 授权", debug: "—", sony: "支持" },
        { feature: "桌面守护、被杀后自动复活", any: "需电脑 ADB 授权一次", debug: "支持", sony: "—" },
      ],
      notes: [
        {
          title: "本机调试授权是什么",
          body: "Tigris 连接电视自己的 127.0.0.1:5555 执行系统命令。电视会弹出「允许调试吗？」，勾选「始终允许」即可，一次授权各品牌通用。",
        },
        {
          title: "索尼 BRAVIA",
          body: "注册 IP 控制后，亮度、音量和输入源都走电视自己的接口；输入源一栏还能显示当前信号。",
        },
        {
          title: "TCL / 雷鸟国行固件",
          body: "没有标准输入源注册，输入源会打开 TCL 自己的选择器；没有可用背光接口，亮度滑条隐藏；需用 guard 版。",
        },
        {
          title: "小米 / Redmi 国行固件",
          body: "背光走 picture_backlight，输入源走小米播放器；不要停用自带桌面，用桌面守护。",
        },
        {
          title: "酷开 / 创维",
          body: "没有标准输入源时，输入源会打开酷开桌面自己的信号源选择器；亮度和设置仍走 Android 标准接口。尚未实机验证。",
        },
        {
          title: "其他品牌",
          body: "音量用 AudioManager，输入源用 TvInputManager。Android 没有标准背光接口，亮度滑条会隐藏。每个品牌是 tv/ 目录里的一个适配器文件，欢迎提交你电视的适配。",
        },
        {
          title: "功放 / ARC · eARC",
          body: "标准音量控制的可能是电视本身而不是功放；CEC 音量行为取决于电视与功放固件。",
        },
      ],
      scope:
        "<strong>验证范围：</strong>完整验证仅在索尼 BRAVIA XR-55A80J（Android TV 12）上完成。TCL / 雷鸟与小米的适配来自社区实机笔记，尚未由本项目逐台验证。",
    },

    faq: {
      eyebrow: "FAQ",
      title: "安装之前，你大概会问。",
      items: [
        {
          q: "需要 root 吗？会不会影响保修？",
          a: [
            "不需要 root，也不修改系统分区。标准版只是通过 ADB 把自己设为默认桌面，并授予几个普通应用拿不到的权限。",
            "保修是否受影响取决于电视厂商的条款，本项目无法替你保证。真正的风险来自误用 ADB 命令，例如停用小米自带桌面导致黑屏。",
          ],
        },
        {
          q: "怎么恢复原来的桌面？",
          a: [
            "优先按 README 的还原流程，默认保留 Tigris。先关闭守护，恢复记录的默认 HOME；仅当前屏保属于 Tigris 时恢复原屏保。下面检查命令的 ORIGINAL_PACKAGE/ORIGINAL_ACTIVITY 替换为保存的原桌面组件；验证失败不要卸载。",
          ],
          cmds: [
            { text: cmds.uninstall },
            { text: cmds.restoreCheck },
              ],
        },
        {
          q: "为什么小米 / Redmi 不能停用自带桌面？",
          a: [
            "小米的 PatchWall（com.mitv.tvhome）由系统白名单强制接管主页键，停用后系统的兜底桌面同样拒绝第三方桌面，结果是开机黑屏。",
            "Tigris 改用「桌面守护」：原厂桌面一到前台就把 Tigris 拉回来，大约闪半秒，不停用或卸载任何系统应用。",
          ],
        },
        {
          q: "TCL / 雷鸟安装失败，提示 INSTALL_FAILED_VERIFICATION_FAILURE",
          a: [
            "这是 TCL 的安装校验在拒绝 adb 安装。请使用带版本号的守护版 APK（Tigris-guard-vX.Y.Z.apk），通过电视自带的「多屏互动」推送，命令见上方 TCL 标签页。",
            "如果脚本连不上 6553 端口，说明该机型没有多屏互动服务，可以把 APK 推到 /sdcard/Download/，再用电视上的文件管理器安装。",
          ],
        },
        {
          q: "为什么没有亮度滑条？",
          a: [
            "Android 没有调节背光的标准接口。索尼 BRAVIA 通过电视自己的 IP 控制接口提供亮度；小米国行固件可写 picture_backlight（需 ADB 授权）。其他品牌暂时失败时，滑条会隐藏而不是显示一个没有作用的控件。",
          ],
        },
        {
          q: "用功放 / ARC、eARC 时音量为什么不对？",
          a: [
            "用标准的 Android 音量接口时，控制的可能是电视本身而不是功放。索尼可以改用电视侧的音量接口，CEC 音量行为最终取决于电视与功放固件。",
          ],
        },
        {
          q: "ADB 一直提示 unauthorized 怎么办？",
          a: [
            "说明电视没有接受这台电脑的调试授权。请在电视上勾选「始终允许来自这台计算机」并点「允许」；小米每次唤醒会关闭 ADB，Tigris 的「保持 ADB 调试开启」可以在唤醒后自动打开。",
          ],
        },
        {
          q: "桌面守护会不会读取我的屏幕或通知？",
          a: [
            "不会。守护只监听窗口切换，判断原厂桌面是否来到前台，不读取屏幕内容；通知监听只用于在系统杀掉 Tigris 后把它重新拉起，不读取任何通知。",
          ],
        },
        {
          q: "怎么更新到新版本？",
          a: [
            "从 GitHub Releases 页面下载最新 APK，用 adb install -r 覆盖安装即可，设置和布局会保留。下载入口始终打开全部版本列表，不需要网站跟随版本更新。",
          ],
        },
        {
          q: "网站和 App 会收集数据吗？",
          a: [
            "网站是纯静态页面，不使用 Cookie、不加载第三方统计或广告脚本，字体使用系统字体。",
            "App 需要联网获取壁纸（必应、Wallhaven）与顶部内容（哔哩哔哩、斗鱼的公开网页接口）；选择航拍屏保时视频从 Apple 的服务器在线播放。没有账号体系。",
          ],
        },
        {
          q: "和 Apple 有关系吗？",
          a: [
            "没有。Tigris 是独立的开源项目，与 Apple、Sony、小米、TCL、哔哩哔哩、斗鱼均无关联，也未获其认可。设计语言受 tvOS 启发，但不包含 Apple 的代码、字体或素材。",
          ],
        },
      ],
    },

    finalCta: {
      eyebrow: "GET TIGRIS",
      title: "让每一次打开电视，都值得期待。",
      body: "下载 APK，打开 ADB，剩下的交给指南或一个 Agent。Android 9+，无需 root，随时可以回到原厂桌面。",
      primary: "前往 Releases 下载",
      secondary: "在 GitHub 查看源码",
    },

    footer: {
      blurb:
        "十二生肖开源系列的第三个项目：虎。把 Android TV 的桌面换成 tvOS 般的玻璃与航拍。",
      cols: [
        {
          title: "产品",
          links: [
            { href: "#features", label: "功能" },
            { href: "#aerial", label: "航拍屏保" },
            { href: brand.releases, label: "版本发布" },
            { href: brand.repo, label: "GitHub" },
          ],
        },
        {
          title: "安装",
          links: [
            { href: "#install", label: "安装指南" },
            { href: "#compatibility", label: "兼容性" },
            { href: "#faq", label: "常见问题" },
            { href: brand.agents, label: "AGENTS.md" },
          ],
        },
        {
          title: "项目",
          links: [
            { href: brand.readme, label: "README" },
            { href: brand.issues, label: "问题反馈" },
            { href: brand.license, label: "MIT 许可证" },
            { href: brand.notices, label: "第三方声明" },
          ],
        },
      ],
      legal:
        "© 2026 Tigris · 独立开源项目。与 Apple、Sony、小米、TCL、哔哩哔哩、斗鱼无关联，未获其认可或授权；「Apple TV」为 Apple Inc. 的商标，本站仅用于描述可选的航拍视频屏保。截图中的影片封面、照片与应用图标版权归各自权利人。航拍视频由 Apple 的服务器提供，本项目不包含视频文件。安装与使用风险由使用者自行承担。",
      langLink: { href: "/en/", label: "English", lang: "en" },
    },
} satisfies Copy;
