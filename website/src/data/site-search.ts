import type { Locale } from "./lang";

/** One search result: a one-line answer, the section name, and where to jump. */
export interface SearchHit {
  answer: string;
  section: string;
  /** Open this stuck note. Prefer the brand the reader already picked. */
  stuckKey?: string;
  /** Otherwise scroll to this element id and open it if it is a details element. */
  elementId?: string;
}

interface Row {
  stuckKey?: string;
  elementId?: string;
  zh: { answer: string; section: string; keys: Key[] };
  en: { answer: string; section: string; keys: Key[] };
}

type Key = string | { t: string; w: number };

/* Hand-curated. Keys are the words a beginner actually types, including the
   phrases on the 搜搜看 buttons. Each row points at a real step, stuck note,
   FAQ item, glossary entry, or section. */
const rows: Row[] = [
  {
    stuckKey: "adb",
    zh: {
      answer: "调试开关可能写成「ADB 调试」「USB 调试」或「网络调试」。",
      section: "安装 · 打开调试开关",
      keys: ["找不到开关", "没有开关", "调试开关没有", "adb 在哪", "usb 调试", "网络调试"],
    },
    en: {
      answer: "The debugging switch may say ADB debugging, USB debugging, or Network debugging.",
      section: "Install · turn on debugging",
      keys: ["can't find the switch", "no switch", "no debugging switch", "where is adb", "usb debugging"],
    },
  },
  {
    stuckKey: "home",
    zh: {
      answer: "按主页键如果还是原来的桌面，把画面告诉助手，让它继续。不要自己关掉自带桌面。",
      section: "安装 · 按主页键",
      keys: ["按主页键还是原来的", "主页没变", "还是原来的桌面", { t: "电视没反应", w: 100 }],
    },
    en: {
      answer: "If the house button still opens the old home screen, tell the assistant and let it continue. Do not switch off the original one yourself.",
      section: "Install · press the house button",
      keys: ["home button still shows the old screen", "still the original home", "home didn't change", { t: "tv not responding", w: 100 }],
    },
  },
  {
    stuckKey: "restore",
    zh: {
      answer: "别慌。复制「换回原来的桌面」里的文字，发给帮你安装的助手。用原来那台电脑。",
      section: "换回原来的桌面",
      keys: ["怎么换回去", "换回去", "恢复原来", "换回桌面", "不想要了", { t: "黑屏了", w: 120 }, { t: "黑屏", w: 90 }],
    },
    en: {
      answer: "Don't panic. Copy the paragraph under “Put the original home screen back” and send it to the assistant. Use the same computer.",
      section: "Put the original home screen back",
      keys: ["how do i switch back", "switch back", "go back", "restore original", "undo install", { t: "black screen", w: 120 }],
    },
  },
  {
    elementId: "faq-1",
    zh: {
      answer: "常见问题里有换回原来桌面的说明。先不要卸载。",
      section: "常见问题 · 怎么恢复原来的桌面",
      keys: ["怎么恢复原来的桌面", "恢复桌面"],
    },
    en: {
      answer: "The FAQ explains how to restore the original launcher. Do not uninstall yet.",
      section: "FAQ · restore the original launcher",
      keys: ["how do i restore the original launcher", "restore the launcher"],
    },
  },
  {
    elementId: "install-prep",
    zh: {
      answer: "电脑和电视要连在同一个 Wi-Fi。",
      section: "安装 · 先准备两样东西",
      keys: [{ t: "连不上", w: 100 }, { t: "can't connect", w: 100 }, "同一个 wifi", "同一网络"],
    },
    en: {
      answer: "The computer and the TV need to be on the same Wi-Fi.",
      section: "Install · two things first",
      keys: [{ t: "can't connect", w: 100 }, { t: "cannot connect", w: 100 }, "same wi-fi", "same wifi"],
    },
  },
  {
    stuckKey: "ip-fix",
    zh: {
      answer: "再对一下电视上的四段数字。例子 192.168.1.20 不是你的地址。",
      section: "安装 · 填上数字",
      keys: [{ t: "连不上", w: 90 }, "地址填不对", "ip 填错"],
    },
    en: {
      answer: "Check the four numbers on the TV again. 192.168.1.20 is only an example.",
      section: "Install · type the number",
      keys: [{ t: "can't connect", w: 90 }, { t: "cannot connect", w: 90 }, "the address looks wrong", "wrong address"],
    },
  },
  {
    stuckKey: "connect",
    zh: {
      answer: "小米、红米从待机唤醒后，调试开关有时会自己关掉，再打开一次。",
      section: "安装 · 小米 / 红米 · 允许调试",
      keys: [{ t: "连不上", w: 80 }, "唤醒后调试关了", "待机后连不上"],
    },
    en: {
      answer: "After a Xiaomi or Redmi TV wakes from standby, the debugging switch sometimes turns off. Turn it on again.",
      section: "Install · Xiaomi / Redmi · allow debugging",
      keys: [{ t: "can't connect", w: 80 }, { t: "cannot connect", w: 80 }, "debugging turned off", "after standby"],
    },
  },
  {
    stuckKey: "connect",
    zh: {
      answer: "电视弹出询问时，勾上「始终允许」，再选「允许」。",
      section: "安装 · 允许这台电脑",
      keys: [{ t: "连不上", w: 70 }, { t: "电视没反应", w: 80 }, "未授权", "unauthorized", "始终允许"],
    },
    en: {
      answer: "When the TV asks, tick Always allow, then choose Allow.",
      section: "Install · allow this computer",
      keys: [{ t: "can't connect", w: 70 }, { t: "cannot connect", w: 70 }, { t: "tv not responding", w: 80 }, "unauthorized", "always allow"],
    },
  },
  {
    elementId: "ads-by-brand",
    zh: {
      answer: "停在 Tigris 上就没有原厂推荐位。开机广告不能去掉。弹窗还没测过。",
      section: "广告会怎样",
      keys: ["广告还在", "还有广告", "广告没消失", "开机广告", "推荐位", "弹窗"],
    },
    en: {
      answer: "On the Tigris home screen the original recommendation row is gone. Boot ads cannot be removed. Popups have not been checked.",
      section: "What happens to ads",
      keys: ["ads are still there", "ads still showing", "still have ads", "boot ads", "popups"],
    },
  },
  {
    stuckKey: "ip",
    zh: {
      answer: "四段数字在「设置 → 网络」或「设置 → 关于」里。",
      section: "安装 · 找到电视上的数字",
      keys: ["ip 在哪", "ip在哪", "找不到地址", "找不到 ip", "电视地址", "四段数字"],
    },
    en: {
      answer: "The four numbers are under Settings → Network or Settings → About.",
      section: "Install · find the TV’s number",
      keys: ["where is the ip", "where's the ip", "ip address", "can't find the address", "four numbers"],
    },
  },
  {
    stuckKey: "more",
    zh: {
      answer: "在「本机信息」页依次按上、下、左、右，间隔不要超过 1 秒。「更多」在底部。",
      section: "安装 · 雷鸟 / TCL",
      keys: ["找不到更多", "没有更多", "上上下下左右"],
    },
    en: {
      answer: "On Device info, press Up, Down, Left, Right less than a second apart. More is at the bottom.",
      section: "Install · FFalcon / TCL",
      keys: ["can't find more", "no more menu", "up down left right"],
    },
  },
  {
    stuckKey: "settings",
    zh: {
      answer: "在电视主界面找「设置」或齿轮图标。",
      section: "安装 · 打开设置",
      keys: ["找不到设置", "设置在哪", "没有齿轮"],
    },
    en: {
      answer: "On the TV home screen, look for Settings or a gear icon.",
      section: "Install · open Settings",
      keys: ["can't find settings", "where is settings", "no gear"],
    },
  },
  {
    stuckKey: "about",
    zh: {
      answer: "小米、红米、雷鸟、TCL 走「设置 → 关于」。索尼是「设置 → 系统 → 关于」。",
      section: "安装 · 关于",
      keys: ["找不到关于", "关于在哪"],
    },
    en: {
      answer: "Xiaomi, Redmi, FFalcon and TCL use Settings → About. Sony uses Settings → System → About.",
      section: "Install · About",
      keys: ["can't find about", "where is about"],
    },
  },
  {
    stuckKey: "devmode",
    zh: {
      answer: "小米、红米在「产品型号」上连按确认键。索尼和其他电视在版本那一行连按 7 次。",
      section: "安装 · 开发者模式",
      keys: ["开发者模式没打开", "连按没反应", "按了没提示", { t: "电视没反应", w: 70 }],
    },
    en: {
      answer: "On Xiaomi and Redmi, keep pressing OK on Model. On Sony and other TVs, press the version row 7 times.",
      section: "Install · developer mode",
      keys: ["developer mode did not turn on", "nothing happened", "pressed seven times", { t: "tv not responding", w: 70 }],
    },
  },
  {
    stuckKey: "account",
    zh: {
      answer: "在「设置 → 账号与安全」里。英文菜单可能写成 Account & security。",
      section: "安装 · 小米 / 红米",
      keys: ["找不到账号与安全", "账号与安全"],
    },
    en: {
      answer: "It is under Settings → Account & security. Chinese menus say 账号与安全.",
      section: "Install · Xiaomi / Redmi",
      keys: ["can't find account & security", "account and security"],
    },
  },
  {
    stuckKey: "install-fail",
    zh: {
      answer: "小米、红米打开「安装未知来源的应用」。雷鸟、TCL 看到安装确认就选「同意」。",
      section: "安装 · 装上应用",
      keys: ["装不上", "安装失败", "未知来源"],
    },
    en: {
      answer: "On Xiaomi and Redmi, turn on Unknown sources. On FFalcon and TCL, choose Agree on the install prompt.",
      section: "Install · the app",
      keys: ["the app won't install", "install failed", "unknown sources"],
    },
  },
  {
    stuckKey: "device-info",
    zh: {
      answer: "雷鸟和 TCL：设置 → 关于 → 本机信息。",
      section: "安装 · 雷鸟 / TCL",
      keys: ["找不到本机信息", "本机信息"],
    },
    en: {
      answer: "FFalcon and TCL: Settings → About → Device info.",
      section: "Install · FFalcon / TCL",
      keys: ["can't find device info", "device info"],
    },
  },
  {
    stuckKey: "cast",
    zh: {
      answer: "角落如果出现「多屏互动：正在发起应用推送」，选「同意」。",
      section: "安装 · 雷鸟 / TCL",
      keys: ["没有安装确认", "多屏互动", "正在发起应用推送"],
    },
    en: {
      answer: "If the corner says 「多屏互动：正在发起应用推送」, choose 同意 (Agree).",
      section: "Install · FFalcon / TCL",
      keys: ["no install prompt", "multi-screen", "agree prompt"],
    },
  },
  {
    elementId: "install-prep",
    zh: {
      answer: "能在你的电脑上亲自安装的助手，不是只会把步骤发给你的聊天网页。",
      section: "安装 · 先准备两样东西",
      keys: ["什么是ai助手", "什么是 ai 助手", "ai助手是什么", "什么助手"],
    },
    en: {
      answer: "An assistant that can operate your computer and run the install, not a chat page that only sends you the steps.",
      section: "Install · two things first",
      keys: ["what is an ai assistant", "what kind of assistant", "what is ai"],
    },
  },
  {
    stuckKey: "assistant",
    zh: {
      answer: "要用能在你电脑上亲自执行的助手。只会聊天的网页做不到。",
      section: "安装 · 发给助手",
      keys: ["助手不动手", "只发步骤", "ai 没用"],
    },
    en: {
      answer: "Use an assistant that can run the install on your computer. A chat-only page cannot.",
      section: "Install · send to the assistant",
      keys: ["the assistant won't do it", "only sent the steps"],
    },
  },
  {
    stuckKey: "choose",
    zh: {
      answer: "红米选红米，否则小米。雷鸟和 TCL 按键相同。索尼选索尼。对不上就选「其他」。",
      section: "安装 · 选出你的电视",
      keys: ["不知道选哪个", "哪个品牌", "红米还是小米"],
    },
    en: {
      answer: "Choose Redmi if the set says Redmi, otherwise Xiaomi. FFalcon and TCL share the button steps. Choose Sony for Sony. Otherwise choose Other.",
      section: "Install · choose your TV",
      keys: ["not sure which brand", "which brand", "redmi or xiaomi"],
    },
  },
  {
    stuckKey: "menus",
    zh: {
      answer: "菜单完全对不上时，可能是海信 VIDAA、LG 或三星，这些步骤不适用。",
      section: "安装 · 其他 Android 电视",
      keys: ["菜单对不上", "vidaa", "不是安卓"],
    },
    en: {
      answer: "If the menus do not match at all, it may be Hisense VIDAA, an LG, or a Samsung. These steps do not apply.",
      section: "Install · other Android TV",
      keys: ["menus don't match", "vidaa", "not android"],
    },
  },
  {
    stuckKey: "system",
    zh: {
      answer: "索尼：设置 → 系统。",
      section: "安装 · 索尼",
      keys: ["找不到系统"],
    },
    en: {
      answer: "Sony: Settings → System.",
      section: "Install · Sony",
      keys: ["can't find system"],
    },
  },
  {
    stuckKey: "version",
    zh: {
      answer: "索尼在「关于」里的「Android TV 操作系统版本」，有的写成「版本」或 Build。",
      section: "安装 · 索尼",
      keys: ["找不到版本"],
    },
    en: {
      answer: "On Sony, the row is the Android TV OS build. It may say Build or 版本.",
      section: "Install · Sony",
      keys: ["can't find the version row"],
    },
  },
  {
    stuckKey: "build",
    zh: {
      answer: "其他 Android 电视在「关于」里的「版本号」，有的写成 Build。",
      section: "安装 · 其他 Android 电视",
      keys: ["找不到版本号"],
    },
    en: {
      answer: "On other Android TVs the row is Build. Chinese menus say 版本号.",
      section: "Install · other Android TV",
      keys: ["can't find the build number"],
    },
  },
  {
    stuckKey: "prefs",
    zh: {
      answer: "其他 Android 电视：设置 → 设备偏好设置。",
      section: "安装 · 其他 Android 电视",
      keys: ["找不到设备偏好设置"],
    },
    en: {
      answer: "Other Android TVs: Settings → Device Preferences.",
      section: "Install · other Android TV",
      keys: ["can't find device preferences"],
    },
  },
  {
    stuckKey: "devopts",
    zh: {
      answer: "连按版本那一行之后，「开发者选项」才会出现。",
      section: "安装 · 开发者选项",
      keys: ["找不到开发者选项"],
    },
    en: {
      answer: "Developer options appears after you press the version row.",
      section: "Install · developer options",
      keys: ["can't find developer options"],
    },
  },
  {
    stuckKey: "copy",
    zh: {
      answer: "在电脑或手机浏览器里点「复制」，不是用电视遥控器。",
      section: "安装 · 复制给助手",
      keys: ["复制不了", "复制失败"],
    },
    en: {
      answer: "Choose Copy in the computer or phone browser, not with the TV remote.",
      section: "Install · copy for the assistant",
      keys: ["copy failed", "can't copy"],
    },
  },
  {
    elementId: "faq-2",
    zh: {
      answer: "小米、红米不要停用自带桌面。停用后有的电视会黑屏。",
      section: "常见问题 · 为什么不能停用自带桌面",
      keys: ["为什么不能停用", "停用桌面", { t: "黑屏了", w: 80 }, { t: "黑屏", w: 70 }, { t: "按主页键还是原来的", w: 60 }],
    },
    en: {
      answer: "Do not disable the Xiaomi or Redmi home screen. Some sets then stay black.",
      section: "FAQ · why not to disable the original home screen",
      keys: ["why must i not disable", "disable the launcher", { t: "black screen", w: 80 }, { t: "home button still shows the old screen", w: 60 }],
    },
  },
  {
    elementId: "gloss-adb",
    zh: {
      answer: "ADB 调试就是让电脑通过家里的无线网络给电视安装应用的开关。",
      section: "词表 · ADB 调试",
      keys: ["什么是 adb", "adb 是什么"],
    },
    en: {
      answer: "ADB debugging is the switch that lets a computer install apps over your home network.",
      section: "Glossary · ADB debugging",
      keys: ["what is adb", "adb means"],
    },
  },
  {
    elementId: "gloss-guard",
    zh: {
      answer: "桌面守护的意思是：原厂桌面跳出来时，自动切回 Tigris。",
      section: "词表 · 桌面守护",
      keys: ["桌面守护", "什么是守护"],
    },
    en: {
      answer: "Home guard means: if the original home screen pops up, Tigris switches back.",
      section: "Glossary · home guard",
      keys: ["what is home guard", "home guard"],
    },
  },
];

function fold(value: string) {
  return value.trim().toLowerCase();
}

function keyText(key: Key) {
  return typeof key === "string" ? key : key.t;
}

function keyWeight(key: Key) {
  return typeof key === "string" ? 100 : key.w;
}

export function searchHelp(locale: Locale, raw: string): SearchHit[] {
  const query = fold(raw);
  const list = rows.map((row) => ({ ...row[locale], stuckKey: row.stuckKey, elementId: row.elementId }));
  const pack = (entry: (typeof list)[number]): SearchHit => ({
    answer: entry.answer,
    section: entry.section,
    stuckKey: entry.stuckKey,
    elementId: entry.elementId,
  });
  if (!query) return list.slice(0, 6).map(pack);
  const ranked = list
    .map((entry) => {
      let score = 0;
      let exact = false;
      for (const key of entry.keys) {
        const folded = fold(keyText(key));
        const weight = keyWeight(key);
        if (folded === query) {
          exact = true;
          score = Math.max(score, weight);
        } else if (folded.length >= 2 && query.length >= 2 && (query.includes(folded) || folded.includes(query))) {
          score = Math.max(score, Math.round(weight * 0.4));
        }
      }
      return { entry, score, exact };
    })
    .filter((item) => item.score > 0)
    .sort((a, b) => b.score - a.score);
  const exactHits = ranked.filter((item) => item.exact);
  const chosen = exactHits.length >= 2 ? exactHits.slice(0, 4) : exactHits.length === 0 ? ranked.slice(0, 3) : ranked.slice(0, 3);
  return chosen.map((item) => pack(item.entry));
}

export function searchPlaceholder(locale: Locale) {
  return locale === "zh" ? "遇到问题？搜一下" : "Stuck? Search here";
}

export function searchEmpty(locale: Locale) {
  return locale === "zh" ? "没有找到。换个说法试试。" : "Nothing matched. Try different words.";
}

export function searchLabel(locale: Locale) {
  return locale === "zh" ? "搜索安装说明" : "Search install help";
}
