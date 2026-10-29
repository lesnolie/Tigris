import type { Locale } from "./lang";

/* Beginner install copy. Ad-table cells are limited to what the repo documents.
   Sources are listed in the comment at the bottom of this file. */

export const IP_SLOT: Record<Locale, string> = {
  zh: "（把电视上的四段数字填在这里）",
  en: "(type the four numbers from the TV here)",
};

export const IP_EXAMPLE = "192.168.1.20";

export const glossary = {
  adb: {
    zh: {
      word: "ADB 调试",
      def: "让电脑通过家里的无线网络给电视安装应用的开关。菜单写成「ADB 调试」「USB 调试」或「网络调试」都是它。不用拆开电视。",
    },
    en: {
      word: "ADB debugging",
      def: "The switch that lets a computer install apps on the TV over your home network. Menus may say ADB debugging, USB debugging, or Network debugging. You do not open the TV.",
    },
  },
  ip: {
    zh: {
      word: "IP 地址",
      def: "电视在家里网络中的编号，四段数字用点分开，像 192.168.1.20。文档说大多数电视在「设置 → 网络」或「设置 → 关于」里。",
    },
    en: {
      word: "IP address",
      def: "The TV’s number on your home network: four groups of digits with dots, like 192.168.1.20. The guide says most TVs show it under Settings → Network or Settings → About.",
    },
  },
  ok: {
    zh: { word: "确认键", def: "遥控器正中间的圆键。文档里叫确认键，英文资料里常叫 OK。" },
    en: { word: "OK", def: "The round button in the middle of the remote. Chinese guides call it 确认键." },
  },
  devmode: {
    zh: { word: "开发者模式", def: "多出来的一组设置。小米和红米要在「产品型号」上连按确认键，直到电视提示它已打开。" },
    en: { word: "developer mode", def: "An extra group of settings. On Xiaomi and Redmi, press OK on Model until the TV says this is on." },
  },
  devopts: {
    zh: { word: "开发者选项", def: "进入开发者模式之后，设置里多出来的菜单。调试开关在这里。" },
    en: { word: "Developer options", def: "The menu that appears after developer mode is on. The debugging switch is in here." },
  },
  unknown: {
    zh: { word: "安装未知来源的应用", def: "允许电视安装不是它自己的应用商店里下载的应用。小米和红米安装时需要打开它。" },
    en: { word: "Unknown sources", def: "Lets the TV install an app that did not come from its own store. Xiaomi and Redmi need this switched on. Chinese menus say 安装未知来源的应用." },
  },
  wifi: {
    zh: { word: "同一个 Wi-Fi", def: "电脑和电视连的是家里同一个无线网络，不是手机流量。" },
    en: { word: "the same Wi-Fi", def: "The computer and the TV are on the same home wireless network, not mobile data." },
  },
  ai: {
    zh: {
      word: "AI",
      def: "装在电脑上、能替你操作这台电脑的助手。例如 WorkBuddy、Codex、Claude Code、豆包电脑版、千问电脑版、Trae。只会聊天的手机网页做不到。",
    },
    en: {
      word: "AI",
      def: "An assistant on your computer that can operate that computer. For example WorkBuddy, Codex, or Claude Code. A phone chat page that cannot run commands cannot do this install.",
    },
  },
  android: {
    zh: { word: "Android", def: "一种电视系统。小米、红米、雷鸟、TCL 和索尼的智能电视多数是它。" },
    en: { word: "Android", def: "A TV system. Most Xiaomi, Redmi, FFalcon, TCL and Sony smart TVs use it." },
  },
  webos: {
    zh: { word: "webOS", def: "LG 电视的系统。不能用这一页的安装。" },
    en: { word: "webOS", def: "The system on LG TVs. Do not use this install for it." },
  },
  tizen: {
    zh: { word: "Tizen", def: "三星电视的系统。不能用这一页的安装。" },
    en: { word: "Tizen", def: "The system on Samsung TVs. Do not use this install for it." },
  },
  vidaa: {
    zh: { word: "VIDAA", def: "海信的另一种系统。菜单如果和下面的步骤对不上，可能就是它，不能用这一页的安装。海信也有 Android 电视，那种可以。" },
    en: { word: "VIDAA", def: "Another Hisense system. If the menus do not match the steps below, it may be VIDAA, and this install does not apply. Some Hisense TVs are Android, and those can use Other Android TV." },
  },
  cast: {
    zh: { word: "多屏互动", def: "TCL 和雷鸟电视自己弹出的安装确认。看到「多屏互动：正在发起应用推送」时，选「同意」。" },
    en: { word: "Multi-screen install prompt", def: "The install question TCL and FFalcon TVs show themselves. The Chinese line is 「多屏互动：正在发起应用推送」. Choose 同意 (Agree)." },
  },
  homekey: {
    zh: { word: "主页键", def: "遥控器上画着房子的键，有的上面写着 Home。按下去应该回到主屏幕。" },
    en: { word: "Home key", def: "The remote button with a house icon. Some remotes print Home on it. It should open the main screen." },
  },
  bootad: {
    zh: { word: "开机广告", def: "电视刚亮、任何主屏幕出现之前，系统自己播放的广告。文档写明它不属于桌面，不能承诺去掉。" },
    en: { word: "Boot ads", def: "Ads the TV system plays after it lights up, before any home screen. The docs say these are not part of the launcher and removal cannot be promised." },
  },
  shelf: {
    zh: { word: "推荐位", def: "原厂桌面上的影片海报和应用推荐。Tigris 自己的主屏幕没有这一排。" },
    en: { word: "Recommendation row", def: "The posters and app suggestions on the TV’s original home screen. Tigris’s own home screen does not include that row." },
  },
  popup: {
    zh: { word: "弹窗广告", def: "使用过程中突然跳出来的广告。每种品牌都还没有实机记录说它已经消失。" },
    en: { word: "Popup ads", def: "Ads that jump up while you are watching. No brand has a hands-on record here saying they are gone." },
  },
  guard: {
    zh: { word: "桌面守护", def: "原厂桌面跳出来时，自动切回 Tigris。设置里用这个名字。安装时不用自己去打开。" },
    en: { word: "Home guard", def: "If the original home screen pops up, Tigris switches back. The Chinese setting name is 桌面守护. You do not turn this on yourself during these steps." },
  },
  stock: {
    zh: { word: "自带桌面", def: "电视出厂时的主屏幕。要留着，不要关掉。关掉以后，有的电视会黑屏。" },
    en: { word: "original home screen", def: "The home screen that came with the TV. Leave it in place. On some TVs, turning it off makes the screen stay black." },
  },
  always: {
    zh: { word: "始终允许", def: "下次这台电脑再连接时，电视不再反复询问。文档要求勾上它，再选「允许」。" },
    en: { word: "Always allow", def: "Lets this computer connect again without asking every time. The guide says to tick it, then choose Allow." },
  },
  unverified: {
    zh: { word: "未实机验证", def: "项目没有在真实电视上确认过这件事。" },
    en: { word: "Not checked on a real TV", def: "This project has not confirmed it on a physical television." },
  },
  report: {
    zh: { word: "用户自报", def: "某一位用户说自己的电视这样。项目没有再做官方测试。也可以叫社区反馈。" },
    en: { word: "User report", def: "One person said this about their own TV. This project did not run an official test. That is community feedback." },
  },
} as const;

export type TermId = keyof typeof glossary;

export type Run = string | { t: TermId; word?: string } | { href: string; word: string };

export type Mock =
  | { kind: "crumbs"; crumbs: string[] }
  | { kind: "dpad" }
  | { kind: "toggle"; label: string }
  | { kind: "ip" }
  | { kind: "dialog"; title: string; choices: string[] }
  | { kind: "quote"; text: string }
  | { kind: "home" }
  | { kind: "none" };

export interface StepModel {
  action: Run[];
  mock: Mock;
  see: Run[];
  stuck: Run[][];
  /** Plain-language problem sent to the site search. */
  help: string;
  /** Stable id so search can open this step’s stuck note. */
  key: string;
  ip?: boolean;
  prompt?: boolean;
}

export interface BrandModel {
  id: string;
  label: string;
  steps: StepModel[];
}

export interface AdsRow {
  brand: string;
  note: Run[];
}

export interface WizardModel {
  eyebrow: string;
  title: string;
  lede: string;
  termHint: string;
  groupsLabel: string;
  chooseStuck: Run[][];
  seeLabel: string;
  stuckLabel: string;
  helpLabel: string;
  restoreHelp: string;
  chooseHelp: string;
  chosenSee: string;
  brands: BrandModel[];
  promptLegend: string;
  glossaryTitle: string;
  glossaryLede: string;
  adsTitle: string;
  prepTitle: string;
  prep: Run[][];
  diagramOnce: string;
  adsNote: Run[];
  adsCaption: string;
  adsHead: [Run[], Run[]];
  adsRows: AdsRow[];
  restoreTitle: string;
  restoreLede: string;
  restoreAction: Run[];
  restoreSee: Run[];
  restoreStuck: Run[][];
  restorePrompt: string;
  restoreCommandLead: Run[];
  diagramLabel: string;
  exampleLabel: string;
  homeAlt: string;
  homeCaption: string;
  ipLabel: string;
  noscript: string;
  advancedSummary: string;
  advancedIntro: string;
  commandTitle: string;
}

const G = glossary;

function word(locale: Locale, id: TermId, override?: string): Run {
  return override ? { t: id, word: override } : { t: id };
}

function escapeHtml(s: string) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

export function renderRuns(runs: Run[], locale: Locale, prefix: string) {
  return runs
    .map((part, i) => {
      if (typeof part === "string") return escapeHtml(part);
      if ("href" in part) {
        const external = part.href.startsWith("http");
        const rel = external ? ' rel="noopener noreferrer"' : "";
        return `<a class="text-link" href="${escapeHtml(part.href)}"${rel}>${escapeHtml(part.word)}</a>`;
      }
      const g = G[part.t][locale];
      const label = part.word ?? g.word;
      const id = `${prefix}-${i}`;
      return `<span class="term-wrap"><a class="term" href="#gloss-${part.t}" data-term aria-expanded="false" aria-describedby="${id}">${escapeHtml(label)}</a><span class="term-tip" id="${id}" role="tooltip">${escapeHtml(g.def)}</span></span>`;
    })
    .join("");
}

export function plainRuns(runs: Run[], locale: Locale) {
  return runs
    .map((part) => {
      if (typeof part === "string") return part;
      if ("href" in part) return part.word;
      return part.word ?? G[part.t][locale].word;
    })
    .join("");
}

function installPrompt(locale: Locale, label: string, extra: string) {
  const slot = IP_SLOT[locale];
  if (locale === "zh") {
    return `请在我的电脑上把开源电视桌面 Tigris 装到我的电视上，并让遥控器上画着房子的键打开它。

我的电视：${label}
${extra}
电视上的四段数字：${slot}

请先阅读并亲自执行，不要只把步骤发给我：
https://tigris-tv.pages.dev/agent.md
打不开就用 https://raw.githubusercontent.com/lesnolie/Tigris/main/AGENTS.md

如果这台电视的系统不是安卓（小米、红米、雷鸟、TCL、索尼一般都是），就停下来告诉我。
先查看，再安装。留下电视出厂时的主屏幕，不要卸载或停用系统自带的应用。
不要重启或关掉电视，除非我同意。需要我在电视上点允许时，请提醒我。

做完后用白话告诉我：应用装上了没有；我自己按画着房子的键，看到的是不是 Tigris。没做的测试请写「还没测」。电视刚亮时系统自己播放的广告，不要说已经去掉。`;
  }
  return `On my computer, install the open-source TV home screen Tigris on my TV, and make the remote button with a house icon open it.

My TV: ${label}
${extra}
The four numbers on the TV: ${slot}

Read the guide and run it yourself. Do not only send me the steps:
https://tigris-tv.pages.dev/agent.md
If that will not open: https://raw.githubusercontent.com/lesnolie/Tigris/main/AGENTS.md

If this TV’s system is not Android — the system Xiaomi, Redmi, FFalcon, TCL and Sony usually run — stop and tell me.
Look first, then install. Leave the home screen that came with the TV. Do not uninstall or switch off apps that came with the TV.
Do not restart or power off the TV unless I agree. Tell me when I need to choose Allow on the TV.

Then, in plain words, tell me whether the app is installed, and whether I see Tigris after I press the house button myself. Write “not tested” for anything you did not do. Do not say the ad the system plays as the TV lights up is already gone.`;
}

function tail(locale: Locale): StepModel[] {
  const zh = locale === "zh";
  const allowWord = zh ? "USB 调试" : "USB debugging";
  return [
    {
      action: zh ? ["在「网络」或「关于」里找到", word(locale, "ip"), "，把四段数字记下来。"] : ["In Network or About, find the ", word(locale, "ip"), " and write down the four numbers."],
      mock: { kind: "ip" },
      see: zh ? ["四段用点分开的数字。"] : ["Four numbers separated by dots."],
      key: "ip",
      help: zh ? "找不到地址" : "Can't find the address",
      stuck: [
        zh
          ? ["在「网络」和「关于」里找写着 IP 的一行。"]
          : ["Look in Network and in About for a row labeled IP."],
        zh
          ? ["例子 192.168.1.20 要换成你电视上的数字。"]
          : ["192.168.1.20 is only an example. Use the number on your TV."],
      ],
    },
    {
      action: zh ? ["把这四段数字填进下面的框。"] : ["Type those four numbers into the box."],
      mock: { kind: "none" },
      key: "ip-fix",
      help: zh ? "地址填不对" : "The address looks wrong",
      ip: true,
      see: zh ? ["框里是你的数字。"] : ["Your numbers are in the box."],
      stuck: [
        zh
          ? ["可以先空着。复制之后，把文字里的「（把电视上的四段数字填在这里）」换成你的数字。"]
          : ["You can leave the box empty and replace “(type the four numbers from the TV here)” after you copy."],
      ],
    },
    {
      action: zh ? ["点「复制」。"] : ["Choose Copy."],
      mock: { kind: "none" },
      key: "copy",
      help: zh ? "复制不了" : "Copy failed",
      prompt: true,
      see: zh ? ["按钮上的字变成「已复制」。"] : ["The button now says Copied."],
      stuck: [
        zh
          ? ["在电脑或手机的浏览器里点，不是用电视遥控器。如果失败，就用手指或鼠标选中整段文字再复制。"]
          : ["Use the computer or phone browser, not the TV remote. If it fails, select the whole paragraph and copy it."],
      ],
    },
    {
      action: zh ? ["把复制的整段文字发给", word(locale, "ai"), "。"] : ["Send the whole copied paragraph to an ", word(locale, "ai"), "."],
      mock: { kind: "none" },
      key: "assistant",
      help: zh ? "助手不动手" : "The assistant won't do it",
      see: zh ? ["对话里出现整段文字，对方开始在你的电脑上操作。"] : ["The chat shows the whole paragraph, and the assistant starts working on your computer."],
      stuck: [
        zh
          ? ["如果对方只把步骤发回来，换一个能在你的电脑上执行的助手。"]
          : ["If it only sends the steps back, switch to an assistant that can run them on your computer."],
      ],
    },
    {
      action: zh
        ? ["电视弹出询问时，勾上", word(locale, "always"), "，再选「允许」。"]
        : ["When the TV asks, tick ", word(locale, "always"), ", then choose Allow."],
      mock: {
        kind: "dialog",
        title: zh ? "允许 USB 调试吗？" : "Allow USB debugging?",
        choices: zh ? ["始终允许", "允许"] : ["Always allow", "Allow"],
      },
      key: "connect",
      help: zh ? "连不上" : "Can't connect",
      see: zh ? ["这行询问关掉了。"] : ["The question has closed."],
      stuck: [
        zh
          ? ["询问里的「", word(locale, "adb", allowWord), "」就是刚才打开的那个开关。"]
          : ["“", word(locale, "adb", allowWord), "” in the question is the switch you turned on."],
      ],
    },
  ];
}

function homeStep(locale: Locale, extra: Run[][]): StepModel {
  const zh = locale === "zh";
  return {
    action: zh ? ["助手做完后，按遥控器上的", word(locale, "homekey"), "。"] : ["When the assistant is done, press the ", word(locale, "homekey"), " on the remote."],
    mock: { kind: "home" },
    key: "home",
    help: zh ? "按主页键还是原来的" : "Home button still shows the old screen",
    see: zh ? ["Tigris 的主屏幕：有时间，有壁纸，下面一排应用。"] : ["The Tigris home screen: a clock, a wallpaper, and a row of apps along the bottom."],
    stuck: [
      zh
        ? ["如果还是", word(locale, "stock"), "，把画面告诉助手，让它继续。不要自己关掉", word(locale, "stock"), "。"]
        : ["If you still see the ", word(locale, "stock"), ", tell the assistant and let it continue. Do not switch off the ", word(locale, "stock"), " yourself."],
      ...extra,
    ],
  };
}

function xiaomiLike(locale: Locale, _which: "xiaomi" | "redmi"): StepModel[] {
  const zh = locale === "zh";
  const head: StepModel[] = [
    {
      action: zh ? ["打开电视的「设置」。"] : ["Open Settings on the TV."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置"] : ["Settings"] },
      see: zh ? ["一列设置菜单。"] : ["A list of settings items."],
      key: "settings",
      help: zh ? "找不到设置" : "Can't find Settings",
      stuck: [
        zh ? ["在主界面找「设置」或齿轮。"] : ["Look for Settings or a gear icon on the home screen."],
      ],
    },
    {
      action: zh ? ["进入「关于」。"] : ["Open About."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置", "关于"] : ["Settings", "About"] },
      see: zh ? ["页面上有「产品型号」。"] : ["The page shows Model. Chinese menus say 产品型号."],
      key: "about",
      help: zh ? "找不到关于" : "Can't find About",
      stuck: [],
    },
    {
      action: zh
        ? ["在「产品型号」上连续按", word(locale, "ok"), "，直到电视提示已进入", word(locale, "devmode"), "。"]
        : ["On Model, keep pressing ", word(locale, "ok"), " until the TV says ", word(locale, "devmode"), " is on."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置", "关于", "产品型号"] : ["Settings", "About", "Model"] },
      see: zh ? ["电视提示", word(locale, "devmode"), "已经打开。"] : ["The TV says ", word(locale, "devmode"), " is on."],
      key: "devmode",
      help: zh ? "开发者模式没打开" : "Developer mode did not turn on",
      stuck: [
        zh ? ["要连按多次，不是按一下。没有任何提示时，再多按几次。"] : ["Press it several times, not once. If nothing appears, press it again."],
        zh ? ["英文资料把「产品型号」写成 Model。"] : ["The Chinese row is 产品型号."],
      ],
    },
    {
      action: zh ? ["打开「账号与安全」。"] : ["Open Account & security."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置", "账号与安全"] : ["Settings", "Account & security"] },
      see: zh
        ? ["页面上有", word(locale, "adb"), "和", word(locale, "unknown"), "。"]
        : ["The page shows ", word(locale, "adb"), " and ", word(locale, "unknown"), "."],
      key: "account",
      help: zh ? "找不到账号与安全" : "Can't find Account & security",
      stuck: [
        zh ? ["英文菜单可能写成 Account & security。"] : ["A Chinese menu says 账号与安全."],
      ],
    },
    {
      action: zh ? ["把", word(locale, "adb"), "的开关打开。"] : ["Turn on ", word(locale, "adb"), "."],
      mock: { kind: "toggle", label: zh ? "ADB 调试" : "ADB debugging" },
      see: zh ? ["这个开关是打开的。"] : ["The switch is on."],
      key: "adb",
      help: zh ? "调试开关没有" : "No debugging switch",
      stuck: [zh ? ["它和下面一项在同一个页面。开关也可能叫「USB 调试」或「网络调试」。"] : ["It is on the same page as the next switch. It may also say USB debugging or Network debugging."]],
    },
    {
      action: zh ? ["把", word(locale, "unknown"), "的开关打开。"] : ["Turn on ", word(locale, "unknown"), "."],
      mock: { kind: "toggle", label: zh ? "安装未知来源的应用" : "Unknown sources" },
      see: zh ? ["这个开关是打开的。"] : ["The switch is on."],
      key: "install-fail",
      help: zh ? "装不上" : "The app won't install",
      stuck: [zh ? ["它和上一项在同一个「账号与安全」里。"] : ["It is in the same Account & security page as the previous switch."]],
    },
  ];
  const wakeNote: Run[] = zh
    ? ["小米和红米从待机中被唤醒后，这个开关有时会自己关掉。回到打开开关的那一步，再开一次。"]
    : ["After wake from standby, Xiaomi and Redmi sometimes turn this switch off. Return to the step that turned it on."];
  const steps = [...head, ...tail(locale)];
  steps[steps.length - 1].stuck.push(wakeNote);
  steps.push(
    homeStep(locale, [
      zh
        ? ["有的小米、红米会先闪一下", word(locale, "stock"), "，再回到 Tigris。"]
        : ["Some Xiaomi and Redmi TVs flash the ", word(locale, "stock"), " and then return to Tigris."],
    ]),
  );
  return steps;
}

function tclLike(locale: Locale, _which: "tcl" | "ffalcon"): StepModel[] {
  const zh = locale === "zh";
  const head: StepModel[] = [
    {
      action: zh ? ["打开电视的「设置」。"] : ["Open Settings on the TV."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置"] : ["Settings"] },
      see: zh ? ["一列设置菜单。"] : ["A list of settings items."],
      key: "settings",
      help: zh ? "找不到设置" : "Can't find Settings",
      stuck: [
        zh ? ["在主界面找「设置」或齿轮。"] : ["Look for Settings or a gear icon on the home screen."],
      ],
    },
    {
      action: zh ? ["进入「关于」。"] : ["Open About."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置", "关于"] : ["Settings", "About"] },
      see: zh ? ["页面上有「本机信息」。"] : ["The page shows Device info. Chinese menus say 本机信息."],
      key: "about",
      help: zh ? "找不到关于" : "Can't find About",
      stuck: [],
    },
    {
      action: zh ? ["进入「本机信息」。"] : ["Open Device info."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置", "关于", "本机信息"] : ["Settings", "About", "Device info"] },
      see: zh ? ["本机信息这一页打开了。"] : ["The device info page is open."],
      key: "device-info",
      help: zh ? "找不到本机信息" : "Can't find Device info",
      stuck: [zh ? ["在「关于」里找「本机信息」。"] : ["Look in About for Device info."]],
    },
    {
      action: zh
        ? ["用遥控器依次按上、下、左、右，每一下间隔不要超过 1 秒。"]
        : ["On the remote, press Up, Down, Left, Right, with less than one second between presses."],
      mock: { kind: "dpad" },
      see: zh ? ["页面底部出现「更多」。"] : ["More appears at the bottom of the page. Chinese menus say 更多."],
      key: "more",
      help: zh ? "找不到更多" : "Can't find More",
      stuck: [
        zh ? ["四下要连着按。按慢了就不会出现，再试一次。"] : ["Press the four keys as one quick sequence. If More does not appear, try again faster."],
      ],
    },
    {
      action: zh ? ["在「更多」里把", word(locale, "adb", "ADB"), "打开。"] : ["Under More, turn ", word(locale, "adb", "ADB"), " on."],
      mock: { kind: "toggle", label: "ADB" },
      see: zh ? ["这个开关是打开的。"] : ["The switch is on."],
      key: "adb",
      help: zh ? "调试开关没有" : "No debugging switch",
      stuck: [zh ? ["「更多」在这一页的底部。没看到就回到「本机信息」，把上、下、左、右再按一遍。"] : ["More is at the bottom of this page. If it is missing, go back to Device info and press Up, Down, Left, Right again."]],
    },
  ];
  const steps = [...head, ...tail(locale)];
  steps.push({
    action: zh ? ["看到", word(locale, "cast"), "那行字时，选「同意」。"] : ["When you see the ", word(locale, "cast"), ", choose Agree."],
    mock: { kind: "quote", text: "多屏互动：正在发起应用推送" },
    key: "cast",
    help: zh ? "没有安装确认" : "No install prompt",
    see: zh ? ["你已经选了「同意」。"] : ["You have chosen Agree (同意)."],
    stuck: [
      zh ? ["这是电视在问你同不同意安装。"] : ["The TV is asking you to agree to the install."],
      zh ? ["如果一直不出现这行字，把屏幕上实际的字告诉助手。"] : ["If this line never appears, tell the assistant the exact words on the screen."],
    ],
  });
  steps.push(homeStep(locale, []));
  return steps;
}

function sonySteps(locale: Locale): StepModel[] {
  const zh = locale === "zh";
  const head: StepModel[] = [
    {
      action: zh ? ["打开电视的「设置」。"] : ["Open Settings on the TV."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置"] : ["Settings"] },
      see: zh ? ["一列设置菜单。"] : ["A list of settings items."],
      key: "settings",
      help: zh ? "找不到设置" : "Can't find Settings",
      stuck: [zh ? ["在主界面找「设置」或齿轮。"] : ["Look for Settings or a gear icon on the home screen."]],
    },
    {
      action: zh ? ["进入「系统」。"] : ["Open System."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置", "系统"] : ["Settings", "System"] },
      see: zh ? ["系统这一页打开了。"] : ["The System page is open."],
      key: "system",
      help: zh ? "找不到系统" : "Can't find System",
      stuck: [],
    },
    {
      action: zh ? ["进入「关于」。"] : ["Open About."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置", "系统", "关于"] : ["Settings", "System", "About"] },
      see: zh ? ["页面上有「Android TV 操作系统版本」。"] : ["The page shows the Android TV OS build row."],
      key: "version",
      help: zh ? "找不到版本" : "Can't find the version row",
      stuck: [zh ? ["有的机器这一行写成「版本」或 Build，指的是同一行。"] : ["Some menus label this row Build, 版本, or Android TV OS build. It is the same row."]],
    },
    {
      action: zh
        ? ["在「Android TV 操作系统版本」上连按", word(locale, "ok"), " 7 次。"]
        : ["On the Android TV OS build row, press ", word(locale, "ok"), " 7 times."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置", "系统", "关于", "Android TV 操作系统版本"] : ["Settings", "System", "About", "Build"] },
      see: zh ? ["电视提示已经进入", word(locale, "devmode"), "。"] : ["The TV says ", word(locale, "devmode"), " is on."],
      key: "devmode",
      help: zh ? "开发者模式没打开" : "Developer mode did not turn on",
      stuck: [zh ? ["要按满 7 次。这一行也可能写成「版本」或 Build。没出现提示就再数一遍。"] : ["Press it seven times. The row may also say 版本 or Android TV 操作系统版本. Count again if nothing appears."]],
    },
    {
      action: zh ? ["打开", word(locale, "devopts"), "。"] : ["Open ", word(locale, "devopts"), "."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置", "系统", "开发者选项"] : ["Settings", "System", "Developer options"] },
      see: zh ? ["开发者选项这一页打开了。"] : ["The Developer options page is open."],
      key: "devopts",
      help: zh ? "找不到开发者选项" : "Can't find Developer options",
      stuck: [zh ? ["连按之后才会出现。还没有的话，回到上一行再按几次，或把电视重启后再试。"] : ["It appears after the presses. If it is still missing, press the previous row again, or restart the TV and try once more."]],
    },
    {
      action: zh ? ["把", word(locale, "adb", "USB 调试"), "打开。"] : ["Turn on ", word(locale, "adb", "USB debugging"), "."],
      mock: { kind: "toggle", label: zh ? "USB 调试" : "USB debugging" },
      see: zh ? ["这个开关是打开的。"] : ["The switch is on."],
      key: "adb",
      help: zh ? "调试开关没有" : "No debugging switch",
      stuck: [zh ? ["有的菜单写成「网络调试」或「ADB 调试」，是同一个开关。"] : ["Some menus say Network debugging or ADB debugging. It is the same switch."]],
    },
  ];
  const steps = [...head, ...tail(locale)];
  steps.push(homeStep(locale, []));
  return steps;
}

function otherSteps(locale: Locale): StepModel[] {
  const zh = locale === "zh";
  const head: StepModel[] = [
    {
      action: zh ? ["打开电视的「设置」。"] : ["Open Settings on the TV."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置"] : ["Settings"] },
      see: zh ? ["一列设置菜单。"] : ["A list of settings items."],
      key: "menus",
      help: zh ? "菜单对不上" : "Menus don't match",
      stuck: [
        zh
          ? ["海信的 Android 电视也用这些菜单。完全对不上时，可能是", word(locale, "vidaa"), "、LG 的", word(locale, "webos"), "或三星的", word(locale, "tizen"), "，就停下来。"]
          : ["Hisense Android TVs use these menus too. If nothing matches, it may be ", word(locale, "vidaa"), ", LG ", word(locale, "webos"), ", or Samsung ", word(locale, "tizen"), ". Stop."],
      ],
    },
    {
      action: zh ? ["进入「设备偏好设置」。"] : ["Open Device Preferences."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置", "设备偏好设置"] : ["Settings", "Device Preferences"] },
      see: zh ? ["设备偏好设置这一页打开了。"] : ["Device Preferences is open."],
      key: "prefs",
      help: zh ? "找不到设备偏好设置" : "Can't find Device Preferences",
      stuck: [],
    },
    {
      action: zh ? ["进入「关于」。"] : ["Open About."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置", "设备偏好设置", "关于"] : ["Settings", "Device Preferences", "About"] },
      see: zh ? ["页面上有「版本号」。"] : ["The page shows Build."],
      key: "build",
      help: zh ? "找不到版本号" : "Can't find the build number",
      stuck: [zh ? ["有的机器写成 Build，指的是同一行。"] : ["Chinese menus may say 版本号. It is the same row."]],
    },
    {
      action: zh ? ["在「版本号」上连按", word(locale, "ok"), " 7 次。"] : ["On Build, press ", word(locale, "ok"), " 7 times."],
      mock: { kind: "crumbs", crumbs: zh ? ["设置", "设备偏好设置", "关于", "版本号"] : ["Settings", "Device Preferences", "About", "Build"] },
      see: zh ? ["电视提示已经可以打开", word(locale, "devopts"), "。"] : ["You can now open ", word(locale, "devopts"), "."],
      key: "devmode",
      help: zh ? "开发者模式没打开" : "Developer mode did not turn on",
      stuck: [zh ? ["要按满 7 次。这一行也可能写成 Build。没出现提示就再数一遍。"] : ["Press it seven times. The row may also say 版本号. Count again if nothing appears."]],
    },
    {
      action: zh ? ["打开", word(locale, "devopts"), "。"] : ["Open ", word(locale, "devopts"), "."],
      mock: { kind: "crumbs", crumbs: zh ? ["开发者选项"] : ["Developer options"] },
      see: zh ? ["开发者选项这一页打开了。"] : ["Developer options is open."],
      key: "devopts",
      help: zh ? "找不到开发者选项" : "Can't find Developer options",
      stuck: [zh ? ["连按之后才会出现。还没有的话，回到上一行再按几次，或把电视重启后再试。"] : ["It appears after the presses. If it is still missing, press the previous row again, or restart the TV and try once more."]],
    },
    {
      action: zh ? ["把", word(locale, "adb", "USB 调试"), "打开。"] : ["Turn on ", word(locale, "adb", "USB debugging"), "."],
      mock: { kind: "toggle", label: zh ? "USB 调试" : "USB debugging" },
      see: zh ? ["这个开关是打开的。"] : ["The switch is on."],
      key: "adb",
      help: zh ? "调试开关没有" : "No debugging switch",
      stuck: [zh ? ["有的菜单写成「网络调试」或「ADB 调试」，是同一个开关。"] : ["Some menus say Network debugging or ADB debugging. It is the same switch."]],
    },
  ];
  const steps = [...head, ...tail(locale)];
  steps.push(homeStep(locale, []));
  return steps;
}

function prompts(locale: Locale): Record<string, string> {
  const zh = locale === "zh";
  return {
    xiaomi: installPrompt(locale, zh ? "小米" : "Xiaomi", zh ? "文档里小米和红米的菜单相同。" : "The guide uses the same menu for Xiaomi and Redmi."),
    redmi: installPrompt(locale, zh ? "红米" : "Redmi", zh ? "文档里红米和小米的菜单相同。" : "The guide uses the same menu for Redmi and Xiaomi."),
    ffalcon: installPrompt(
      locale,
      zh ? "雷鸟" : "FFalcon (雷鸟)",
      zh ? "文档里雷鸟和 TCL 的菜单相同。如果电视角落出现「多屏互动：正在发起应用推送」，请提醒我选「同意」。" : "The guide uses the same menu for FFalcon (雷鸟) and TCL. If the corner says 「多屏互动：正在发起应用推送」, tell me to choose 同意.",
    ),
    tcl: installPrompt(
      locale,
      "TCL",
      zh ? "如果电视角落出现「多屏互动：正在发起应用推送」，请提醒我选「同意」。不要关掉电视出厂时的主屏幕。" : "If the corner says 「多屏互动：正在发起应用推送」, tell me to choose 同意. Do not switch off the home screen that came with the TV.",
    ),
    sony: installPrompt(locale, zh ? "索尼" : "Sony BRAVIA", zh ? "这是索尼 BRAVIA。" : "This is a Sony BRAVIA."),
    other: installPrompt(
      locale,
      zh ? "其他 Android 电视" : "Other Android TV",
      zh ? "请先认出品牌。如果是 LG、三星，或海信另一种对不上这些菜单的系统，就停下来告诉我。" : "Please identify the brand first. If it is an LG, a Samsung, or a Hisense whose menus do not match these steps, stop and tell me.",
    ),
  };
}

function ads(locale: Locale): { note: Run[]; caption: string; head: WizardModel["adsHead"]; rows: AdsRow[] } {
  const zh = locale === "zh";
  return {
    note: zh
      ? ["停在 Tigris 主屏幕时没有原厂", word(locale, "shelf"), "；", word(locale, "bootad"), "在进主屏幕之前由系统播放，不能去掉；", word(locale, "popup"), "还没测过。"]
      : ["On the Tigris home screen there is no original ", word(locale, "shelf"), "; ", word(locale, "bootad"), " play before that screen and cannot be removed; ", word(locale, "popup"), " have not been checked."],
    caption: zh
      ? "上面那一句对每个品牌都一样，所以表里不再重复。没测过的广告都算未实机验证。没有已批准的固件可以自动关广告。若主页键仍停在自带桌面，推荐位还在。"
      : "The sentence above is the same for every brand, so the table does not repeat it. Ads that were not checked count as not checked on a real TV. No firmware is approved for automatic ad removal. If the house button still opens the original home screen, the recommendation row is still there.",
    head: zh
      ? [["电视"], ["和上面那句不一样的地方"]]
      : [["TV"], ["What differs"]],
    rows: [
      {
        brand: zh ? "小米" : "Xiaomi",
        note: zh
          ? ["有的机器主页键仍打开自带桌面。MiTV-ANSP0、MiTV-ASTR0 是", word(locale, "report"), "，只说明能否安装或运行。"]
          : ["Some sets still open the original home screen. MiTV-ANSP0 and MiTV-ASTR0 are a ", word(locale, "report"), " about installing or running."],
      },
      {
        brand: zh ? "红米" : "Redmi",
        note: zh ? ["和小米同一套说明，没有单独记录。"] : ["Grouped with Xiaomi. No separate record."],
      },
      {
        brand: zh ? "雷鸟" : "FFalcon (雷鸟)",
        note: zh
          ? ["75R685C 实机装成功，音量、设置、信号源可用。75S545C 是", word(locale, "report"), "。"]
          : ["75R685C installed on a real TV; volume, settings and inputs worked. 75S545C is a ", word(locale, "report"), "."],
      },
      {
        brand: "TCL",
        note: zh ? ["和雷鸟同一套说明，没有单独记录。"] : ["Same notes as FFalcon. No separate record."],
      },
      {
        brand: zh ? "索尼" : "Sony",
        note: zh
          ? ["XR-55A80J 测过桌面、亮度、音量和信号源。"]
          : ["XR-55A80J was checked for the home screen, brightness, volume and inputs."],
      },
      {
        brand: zh ? "其他 Android 电视" : "Other Android TV",
        note: zh
          ? ["海信 2026-10-26：", word(locale, "report"), "用 WorkBuddy 装成功，没有型号。"]
          : ["Hisense, 2026-10-26: a ", word(locale, "report"), " of a WorkBuddy install, with no model."],
      },
    ],
  };
}

export function promptsFor(locale: Locale) {
  return prompts(locale);
}

export function restorePrompt(locale: Locale) {
  const slot = IP_SLOT[locale];
  if (locale === "zh") {
    return `请阅读 https://github.com/lesnolie/Tigris/blob/main/AGENTS.md 里标题是 Restore 的部分（意思是：换回原来的桌面），在这台电脑上亲自做。

电视上的四段数字：${slot}

把画着房子的键换回电视出厂时的主屏幕。先不要卸载 Tigris。不要猜原来的主屏幕叫什么名字。不要重启或关掉电视，除非我同意。
做完后用白话告诉我：我按画着房子的键，看到的是不是原来的主屏幕。`;
  }
  return `Read the Restore section of https://github.com/lesnolie/Tigris/blob/main/AGENTS.md (it means: put the original home screen back) and do it on this computer.

The four numbers on the TV: ${slot}

Point the house button back at the home screen that came with the TV. Do not uninstall Tigris yet. Do not guess the name of the original home screen. Do not restart or power off the TV unless I agree.
Then, in plain words, tell me whether the house button shows the original home screen.`;
}

export function getWizard(locale: Locale): WizardModel {
  const zh = locale === "zh";
  const ad = ads(locale);
  const webosHref = zh ? "/webos/" : "/en/webos/";
  return {
    eyebrow: "INSTALL",
    prepTitle: zh ? "先准备两样东西。" : "Two things first.",
    prep: [
      zh
        ? ["一台电脑，和电视连在同一个 Wi-Fi。"]
        : ["A computer on the same Wi-Fi as the TV."],
      zh
        ? ["一个能操作这台电脑的", word(locale, "ai"), "：它会在你的电脑上亲自安装，而不是只把步骤发给你。"]
        : ["An ", word(locale, "ai"), " that can operate that computer: it runs the install on your computer, instead of only sending you the steps."],
    ],
    diagramOnce: zh
      ? "下面的小图是示意图，按菜单名字绘制，不是电视照片。"
      : "The small pictures below are diagrams drawn from menu names, not photos of a TV.",
    title: zh ? "选出你的电视。" : "Choose your TV.",
    lede: zh ? "点下面的一个品牌。" : "Tap one brand below.",
    termHint: zh ? "带下划线的词，点一下就能看解释。" : "Underlined words open a short explanation.",
    groupsLabel: zh ? "电视品牌" : "TV brand",
    seeLabel: zh ? "你现在应该看到：" : "You should now see:",
    stuckLabel: zh ? "卡住了？" : "Stuck?",
    helpLabel: zh ? "搜搜看" : "Search for help",
    restoreHelp: zh ? "怎么换回去" : "How do I switch back",
    chooseHelp: zh ? "不知道选哪个" : "Not sure which brand",
    chosenSee: zh ? "的步骤。请从第 2 步接着做。" : " steps are below. Continue at step 2.",
    chooseStuck: [
      zh
        ? ["小米和红米菜单相同，机身上写红米就点红米。雷鸟和 TCL 不是同一个牌子，打开调试的按法相同。"]
        : ["Xiaomi and Redmi share a menu; if the set says Redmi, choose Redmi. FFalcon (雷鸟) and TCL are different brands and share the button sequence."],
      zh
        ? ["海信的 Android 电视、以及原生 Android 电视，点「其他 Android 电视」。"]
        : ["For a Hisense Android TV or a plain Android TV, choose Other Android TV."],
      zh
        ? ["LG 的", word(locale, "webos"), "请打开", { href: webosHref, word: "这一页" }, "。三星的", word(locale, "tizen"), "不能从这里安装。"]
        : ["For an LG ", word(locale, "webos"), " TV, open ", { href: webosHref, word: "this page" }, ". Samsung ", word(locale, "tizen"), " cannot be installed from here."],
    ],
    brands: [
      { id: "xiaomi", label: zh ? "小米" : "Xiaomi", steps: xiaomiLike(locale, "xiaomi") },
      { id: "redmi", label: zh ? "红米" : "Redmi", steps: xiaomiLike(locale, "redmi") },
      { id: "ffalcon", label: zh ? "雷鸟" : "FFalcon (雷鸟)", steps: tclLike(locale, "ffalcon") },
      { id: "tcl", label: "TCL", steps: tclLike(locale, "tcl") },
      { id: "sony", label: zh ? "索尼" : "Sony", steps: sonySteps(locale) },
      { id: "other", label: zh ? "其他 Android 电视" : "Other Android TV", steps: otherSteps(locale) },
    ],
    promptLegend: zh ? "上面这段是发给助手的，你只要复制。" : "The paragraph above is for the assistant. You only copy it.",
    glossaryTitle: zh ? "这些词是什么意思" : "What these words mean",
    glossaryLede: zh ? "安装说明里会碰到的词，都在这里。" : "Words you meet in these steps.",
    adsTitle: zh ? "广告会怎样" : "What happens to ads",
    adsNote: ad.note,
    adsCaption: ad.caption,
    adsHead: ad.head,
    adsRows: ad.rows,
    restoreTitle: zh ? "换回原来的桌面" : "Put the original home screen back",
    restoreLede: zh ? "可以换回去。" : "You can switch back.",
    restoreAction: zh
      ? ["复制下面的文字，发给帮你安装的那个", word(locale, "ai"), "。"]
      : ["Copy the paragraph below and send it to the ", word(locale, "ai"), " that installed it."],
    restoreSee: zh
      ? ["对方开始把", word(locale, "homekey"), "换回", word(locale, "stock"), "，并且先不删除 Tigris。"]
      : ["The assistant starts pointing the ", word(locale, "homekey"), " back at the ", word(locale, "stock"), ", and does not delete Tigris yet."],
    restoreStuck: [
      zh
        ? ["要用安装时的那台电脑。换了一台电脑，就不能猜原来的主屏幕叫什么。"]
        : ["Use the computer you installed from. A different computer must not guess the original home screen’s name."],
      zh
        ? ["只在 Tigris 里关掉", word(locale, "guard"), "，不会完整换回去。"]
        : ["Switching off ", word(locale, "guard"), " inside Tigris does not finish the return."],
    ],
    restorePrompt: restorePrompt(locale),
    restoreCommandLead: zh
      ? ["如果助手不方便，Mac 或 Linux 可以自己粘贴文档里的 install.sh restore。它会关掉自动切回，把画着房子的键还给安装时记下的原来的桌面，并且先不卸载 Tigris。Windows 请用上面的文字。电脑需要 bash、curl，以及 adb（电脑用来连接电视的程序，在", { href: "https://developer.android.com/tools/releases/platform-tools", word: "Android 平台工具" }, "里）。"]
      : ["If an assistant is not handy, Mac or Linux can paste the documented install.sh restore command. It turns off the automatic switch-back, returns the house button to the saved original home screen, and does not uninstall Tigris yet. On Windows, use the paragraph above. The computer needs bash, curl, and adb (the program that connects the computer to the TV, inside ", { href: "https://developer.android.com/tools/releases/platform-tools", word: "Android platform-tools" }, ")."],
    diagramLabel: "",
    exampleLabel: zh ? "举例，不是你的电视" : "Example, not your TV",
    homeAlt: zh
      ? "Tigris 主屏幕的真实照片：风景壁纸，上方有时间，下方一排应用图标。拍自索尼电视。"
      : "A real photo of the Tigris home screen: scenery wallpaper, the time above, and a row of app icons below. Photographed on a Sony TV.",
    homeCaption: zh
      ? "真实照片，拍自索尼 BRAVIA，用来对照成功时长什么样。你的壁纸和图标会不同。这不是菜单截图。"
      : "A real photo from a Sony BRAVIA, so you can compare a successful result. Your wallpaper and icons will differ. This is not a photo of a settings menu.",
    ipLabel: zh ? "电视上的四段数字" : "The four numbers on the TV",
    noscript: zh
      ? "浏览器没有打开脚本，所以每个品牌的步骤都列在下面。只读你的那一种。"
      : "Script is off, so every brand is listed below. Read only yours.",
    advancedSummary: zh ? "高级：自己用命令、手动步骤和完整限制" : "Advanced: commands, manual steps, and the full limits",
    advancedIntro: zh
      ? "日常用上面的步骤即可。这里是同一套安装的详细版，只有要自己动手时才需要。先装好 Git、Python 和 adb，再用下面这一条命令。策略表只是对照，不是另一套步骤。逐条命令和长说明都在最下面。"
      : "The steps above are the usual way. This is the same install in detail, for people who want to run it themselves. Install Git, Python and adb, then use the one command below. The strategy table is a lookup, not a second procedure. Line-by-line commands and the long notes are at the bottom.",
    commandTitle: zh ? "一条命令" : "One command",
  };
}

export const RESTORE_COMMAND = `curl -fsSL https://raw.githubusercontent.com/lesnolie/Tigris/main/install.sh | TV=${IP_EXAMPLE} bash -s -- restore`;

export const REPAIR_COMMAND = `curl -fsSL https://raw.githubusercontent.com/lesnolie/Tigris/main/install.sh | TV=${IP_EXAMPLE} bash -s -- repair`;

/** One action per step. Shown when Home still opens the original launcher. */
export function homeStill(locale: Locale): { title: string; lede: string; note: Run[]; steps: { action: string; see: string }[] } {
  const a80k = { href: "https://github.com/lesnolie/Tigris/issues/69", word: "索尼 BRAVIA XR-55A80K" };
  if (locale === "zh") {
    return {
      title: "按主页键还是原来的",
      lede: "装好之后按画着房子的键，画面还是原来的桌面时，按下面的顺序做。一步只做一件事。",
      note: [
        "一位用户在",
        a80k,
        "（Android 12，Google TV）上验证了 v2.0.15：设成默认桌面后，主页键回到 Tigris，HDMI 切换、亮度和音量都能用，其他索尼型号可能不一样。",
      ],
      steps: [
        {
          action: "先看是不是闪一下就回来了。",
          see: "小米和红米会先闪一下原来的桌面，大约半秒，然后回到 Tigris。这是正常的，不用再修。",
        },
        {
          action: "如果一直停在原来的桌面，把电视上的四段数字换进下面的命令，在安装时用的那台电脑上运行。",
          see: "电脑上出现 mFocusedApp，并且里面有 com.leslie.tvhome。这是修复，不会卸载，也不会关掉自带桌面。",
        },
        {
          action: "在电视上打开 Tigris 的设置，进入「桌面守护」，先关掉，再打开。",
          see: "这一行变成「开」。这一下会重新授权。",
        },
        {
          action: "如果系统是 Android 13 或更新，并且上一步没有变成「开」，打开 Tigris 的应用信息，点「允许受限设置」。",
          see: "允许之后，回到上一步，把桌面守护再开一次。",
        },
        {
          action: "电视待机再亮起之后，按一次遥控器上画着房子的键。",
          see: "画面回到 Tigris。只按一次即可。",
        },
        {
          action: "如果还是不行，看是不是下面这两种机器。",
          see: "有的 TCL、雷鸟会大约每一分钟清掉无障碍，例如雷鸟 75S78A Plus。坚果 S621 也修不好。这两种不要反复重装。",
        },
        {
          action: "不要自己关掉自带桌面。",
          see: "关掉以后，有的电视会黑屏，遥控器也开不回来。自带桌面要留着。",
        },
      ],
    };
  }
  return {
    title: "Home still opens the original screen",
    lede: "The house button still shows the old home screen after install. Do one thing at a time.",
    note: [
      "One person verified v2.0.15 on a ",
      { href: a80k.href, word: "Sony BRAVIA XR-55A80K" },
      " (Android 12, Google TV): it is the default home, the house button returns to Tigris, and HDMI, brightness and volume work, though other Sony models may differ.",
    ],
    steps: [
      {
        action: "Look first for a short flash that then returns.",
        see: "Xiaomi and Redmi flash the original home screen for about half a second, then return to Tigris. That is expected.",
      },
      {
        action: "If it stays on the old screen, put the TV’s four numbers into the command below and run it on the computer you installed from.",
        see: "The computer prints mFocusedApp, and that line contains com.leslie.tvhome. This repairs the guard. It does not uninstall Tigris or switch off the original home screen.",
      },
      {
        action: "On the TV, open Tigris settings, open 桌面守护 (home guard), turn it off, then turn it on.",
        see: "The row says On. That authorizes the guard again.",
      },
      {
        action: "On Android 13 or newer, if that row did not turn on, open Tigris app info and choose Allow restricted settings.",
        see: "Then go back and turn the home guard on again.",
      },
      {
        action: "After the TV wakes from standby, press the house button once.",
        see: "The screen returns to Tigris. One press is enough.",
      },
      {
        action: "If it is still the old screen, check whether the TV is one of these.",
        see: "Some TCL and FFalcon sets clear the accessibility service about once a minute, for example the FFalcon 75S78A Plus. The JMGO S621 cannot be fixed either. Do not keep reinstalling on those sets.",
      },
      {
        action: "Do not switch off the original home screen yourself.",
        see: "Some TVs then stay black, and the remote cannot turn them back on. Leave the original home screen installed.",
      },
    ],
  };
}

/*
Ad-table sources (do not render):

Boot ads, every Android row:
- README.md FAQ「原来的桌面、会员、小爱和账号还在吗」: firmware boot ads play before the launcher, are not part of the launcher, removal cannot be promised.
- docs/installation.md FAQ, same statement.
- docs/full-replacement.md: no model is verified to remove every ad; not one-click ad removal; third-party video-app ads are out of scope; VERIFIED_AD_FIRMWARES is empty so apply-ads refuses.
- tools/replacement_profiles.py: VERIFIED_AD_FIRMWARES = {}.
- AGENTS.md: ad maintenance stays diagnostic until an exact firmware is reviewed; the approved table is empty.
- docs/modules.md and docs/releases/v2.0.9.md: no ad-removal claim; ad changes not checked on a real TV.
- docs/modular-validation.md: Advertising unverified, no approved firmware.
- docs/installation-compatibility.md: Sony, TCL, Skyworth and Hisense do not run the Xiaomi disable commands.

Recommendation row:
- Tigris home is its own screen (clock, wallpaper, app row): README feature list and website/public/img/home-*.webp from a Sony BRAVIA recording (website/SOURCE-INVENTORY.md).
- That row is absent only while this screen is what the house button shows. If the original launcher stays in front, its posters remain.
- Xiaomi / Redmi may still open the original launcher (AGENTS.md, docs/installation-compatibility.md, docs/full-replacement.md). Beginner copy says 自带桌面 / original home screen.

Popup ads:
- docs/full-replacement.md verification table: judge boot ads, the home screen and system popups by watching them. A successful package disable is not a pass.
- No brand row in README or the validation notes records a popup pass.

Xiaomi status: README compatibility table MiTV-ANSP0 and MiTV-ASTR0 (install / runtime notes, not ad tests). docs/releases/v2.0.9.md: Xiaomi ads not checked on a real TV.
Redmi status: README and AGENTS.md group Redmi with Xiaomi for the debugging menu. No separate ad record.
FFalcon status: README 雷鸟 75R685C, Guard build via on-TV install, volume / settings / inputs checked, brightness hidden by design; ads not mentioned. README auto table 75S545C, user-reported install and launch.
TCL status: README and AGENTS.md group TCL with 雷鸟 for the debugging sequence. No separate TCL ad measurement.
Sony status: README XR-55A80J, maintainer’s main set for the home screen and picture controls, not for ads. Sony does not run Xiaomi ad-disable commands (docs/installation-compatibility.md).
Other / Hisense: README groups Hisense Android with stock Android TV for the debugging menu. Hisense native VIDAA is a different system (docs/installation-compatibility.md). 2026-10-26 WorkBuddy report on this site: install success only, model not supplied.
*/
