import type { Locale } from "./lang";

export interface SearchHit {
  title: string;
  body: string;
  href: string;
}

interface Entry {
  zh: SearchHit & { keys: string[] };
  en: SearchHit & { keys: string[] };
}

function hrefFor(locale: Locale, href: string) {
  if (href.startsWith("#")) return locale === "zh" ? `/${href}` : `/en/${href}`;
  return href;
}

const entries: Entry[] = [
  {
    zh: {
      title: "找不到设置",
      body: "在电视主界面找「设置」或齿轮图标，然后进入。",
      href: "#install",
      keys: ["找不到设置", "设置在哪", "没有设置"],
    },
    en: {
      title: "Can't find Settings",
      body: "On the TV home screen, look for Settings or a gear icon, then open it.",
      href: "#install",
      keys: ["can't find settings", "where is settings", "no settings"],
    },
  },
  {
    zh: {
      title: "找不到关于",
      body: "小米、红米、雷鸟、TCL：设置 → 关于。索尼再进一层：设置 → 系统 → 关于。",
      href: "#install",
      keys: ["找不到关于", "关于在哪"],
    },
    en: {
      title: "Can't find About",
      body: "Xiaomi, Redmi, FFalcon and TCL: Settings → About. Sony goes one level deeper: Settings → System → About.",
      href: "#install",
      keys: ["can't find about", "where is about"],
    },
  },
  {
    zh: {
      title: "找不到更多",
      body: "雷鸟和 TCL：打开 设置 → 关于 → 本机信息，再依次按上、下、左、右，间隔不要超过 1 秒。「更多」出现在页面底部。",
      href: "#install",
      keys: ["找不到更多", "没有更多", "上上下下"],
    },
    en: {
      title: "Can't find More",
      body: "FFalcon and TCL: open Settings → About → Device info, then press Up, Down, Left, Right with less than one second between presses. More appears at the bottom.",
      href: "#install",
      keys: ["can't find more", "no more menu", "up down left right"],
    },
  },
  {
    zh: {
      title: "找不到地址",
      body: "四段数字在 设置 → 网络，或 设置 → 关于。电脑和电视要连同一个 Wi-Fi。例子 192.168.1.20 要换成你电视上的数字。",
      href: "#install",
      keys: ["找不到地址", "找不到 ip", "ip 地址", "四段数字"],
    },
    en: {
      title: "Can't find the address",
      body: "The four numbers are under Settings → Network, or Settings → About. The computer and TV need the same Wi-Fi. Replace the example 192.168.1.20 with the number on your TV.",
      href: "#install",
      keys: ["can't find the address", "can't find ip", "ip address", "four numbers"],
    },
  },
  {
    zh: {
      title: "连不上",
      body: "电视问「允许 USB 调试吗？」时，勾上「始终允许」，再选「允许」。小米和红米从待机唤醒后，调试开关有时会自己关掉，回到那一步再打开。",
      href: "#install",
      keys: ["连不上", "连接失败", "未授权", "允许调试"],
    },
    en: {
      title: "Can't connect",
      body: "When the TV asks “Allow USB debugging?”, tick Always allow, then choose Allow. After Xiaomi or Redmi wakes from standby, the switch sometimes turns off. Open it again.",
      href: "#install",
      keys: ["can't connect", "connection failed", "unauthorized", "allow debugging"],
    },
  },
  {
    zh: {
      title: "黑屏了",
      body: "不要关掉电视出厂时的主屏幕。小米、红米关掉自带桌面后，有的电视会一直黑屏。把它重新打开。Tigris 会留着原来的桌面。",
      href: "#install",
      keys: ["黑屏了", "黑屏", "主页没变", "关掉桌面"],
    },
    en: {
      title: "Black screen",
      body: "Do not switch off the home screen that came with the TV. On some Xiaomi and Redmi sets, turning that off leaves a black screen. Turn it back on. Tigris leaves the original home screen in place.",
      href: "#install",
      keys: ["black screen", "home didn't change", "turned off the launcher"],
    },
  },
  {
    zh: {
      title: "怎么换回去",
      body: "把「换回原来的桌面」里的那段文字复制给帮你安装的助手。要用安装时的那台电脑，先不卸载 Tigris。",
      href: "#restore-home",
      keys: ["怎么换回去", "恢复原来", "换回桌面", "卸载"],
    },
    en: {
      title: "How do I switch back",
      body: "Copy the paragraph under “Put the original home screen back” and send it to the assistant that installed Tigris. Use the same computer. Do not uninstall Tigris yet.",
      href: "#restore-home",
      keys: ["how do i switch back", "restore", "original home", "uninstall"],
    },
  },
  {
    zh: {
      title: "调试开关没有",
      body: "这个开关可能写成「ADB 调试」「USB 调试」「网络调试」或 ADB。小米在「账号与安全」里。雷鸟和 TCL 在「更多」里。索尼在「开发者选项」里。",
      href: "#install",
      keys: ["调试开关没有", "adb", "usb 调试", "网络调试"],
    },
    en: {
      title: "No debugging switch",
      body: "The switch may say ADB debugging, USB debugging, Network debugging, or ADB. Xiaomi: Account & security. FFalcon and TCL: More. Sony: Developer options.",
      href: "#install",
      keys: ["no debugging switch", "adb", "usb debugging", "network debugging"],
    },
  },
  {
    zh: {
      title: "开发者模式没打开",
      body: "小米和红米：在「产品型号」上连按确认键，直到电视提示已打开。索尼和其他 Android 电视：在版本那一行连按 7 次。",
      href: "#install",
      keys: ["开发者模式没打开", "连按", "按了没反应"],
    },
    en: {
      title: "Developer mode did not turn on",
      body: "Xiaomi and Redmi: keep pressing OK on Model until the TV says it is on. Sony and other Android TVs: press the version row 7 times.",
      href: "#install",
      keys: ["developer mode did not turn on", "pressed and nothing", "seven times"],
    },
  },
  {
    zh: {
      title: "装不上",
      body: "小米和红米要打开「安装未知来源的应用」。雷鸟和 TCL 如果角落出现「多屏互动：正在发起应用推送」，选「同意」。",
      href: "#install",
      keys: ["装不上", "安装失败", "未知来源", "没有安装确认"],
    },
    en: {
      title: "The app won't install",
      body: "On Xiaomi and Redmi, turn on Unknown sources. On FFalcon and TCL, if the corner says 「多屏互动：正在发起应用推送」, choose 同意 (Agree).",
      href: "#install",
      keys: ["the app won't install", "install failed", "unknown sources", "no install prompt"],
    },
  },
  {
    zh: {
      title: "助手不动手",
      body: "要用能在你的电脑上亲自执行的助手，例如 WorkBuddy、Codex 或 Claude Code。只会聊天的网页做不到。",
      href: "#install",
      keys: ["助手不动手", "只发步骤", "ai 没用"],
    },
    en: {
      title: "The assistant won't do it",
      body: "Use an assistant that can run the install on your computer, such as WorkBuddy, Codex, or Claude Code. A chat page that only replies cannot.",
      href: "#install",
      keys: ["the assistant won't do it", "only sent steps", "ai did nothing"],
    },
  },
  {
    zh: {
      title: "不知道选哪个",
      body: "机身写红米就选红米，否则小米。雷鸟和 TCL 是两个牌子，按键步骤相同。索尼选索尼。海信 Android 和其他 Android 电视选「其他」。LG 不能用这里的步骤。",
      href: "#install",
      keys: ["不知道选哪个", "哪个品牌", "红米还是小米"],
    },
    en: {
      title: "Not sure which brand",
      body: "If the set says Redmi, choose Redmi; otherwise Xiaomi. FFalcon and TCL are different brands with the same button steps. Choose Sony for Sony. Hisense Android and other Android TVs use Other. LG does not use these steps.",
      href: "#install",
      keys: ["not sure which brand", "which brand", "redmi or xiaomi"],
    },
  },
  {
    zh: {
      title: "菜单对不上",
      body: "海信如果是自己的 VIDAA 系统，或者电视是 LG、三星，这些 Android 步骤不适用。LG 有单独的说明。",
      href: "/webos/",
      keys: ["菜单对不上", "vidaa", "海信", "webos", "三星"],
    },
    en: {
      title: "Menus don't match",
      body: "If a Hisense TV uses VIDAA, or the TV is an LG or Samsung, these Android steps do not apply. LG has a separate guide.",
      href: "/en/webos/",
      keys: ["menus don't match", "vidaa", "hisense", "webos", "samsung"],
    },
  },
  {
    zh: {
      title: "开机广告和推荐位",
      body: "停在 Tigris 主屏幕时没有原厂推荐位。开机广告在进主屏幕之前由系统播放，不能去掉。弹窗广告还没测过。",
      href: "#ads-by-brand",
      keys: ["广告", "开机广告", "推荐位", "弹窗"],
    },
    en: {
      title: "Boot ads and recommendation rows",
      body: "On the Tigris home screen there is no original recommendation row. Boot ads play before that screen and cannot be removed. Popup ads have not been checked.",
      href: "#ads-by-brand",
      keys: ["ads", "boot ads", "recommendation", "popup"],
    },
  },
];

function fold(value: string) {
  return value.trim().toLowerCase();
}

export function searchHelp(locale: Locale, raw: string): SearchHit[] {
  const query = fold(raw);
  const list = entries.map((entry) => entry[locale]);
  if (!query) {
    return list.slice(0, 6).map((entry) => ({ title: entry.title, body: entry.body, href: hrefFor(locale, entry.href) }));
  }
  return list
    .map((entry) => {
      let score = 0;
      for (const key of entry.keys) {
        const folded = fold(key);
        if (folded === query) score += 100;
        else if (query.includes(folded) || folded.includes(query)) score += 40;
      }
      if (fold(entry.title).includes(query)) score += 25;
      if (fold(entry.body).includes(query)) score += 8;
      return { entry, score };
    })
    .filter((item) => item.score > 0)
    .sort((a, b) => b.score - a.score)
    .map(({ entry }) => ({ title: entry.title, body: entry.body, href: hrefFor(locale, entry.href) }));
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
