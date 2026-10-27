import registry from "../../../tools/installation_strategies.json";
export const systems = [
  { id: "android", strategy: "generic-android", zh: "Android TV / Google TV", en: "Android TV / Google TV" },
  { id: "sony", strategy: "sony-native", zh: "Sony Android TV", en: "Sony Android TV" },
  { id: "tcl", strategy: "tcl-compatible", zh: "TCL / 雷鸟 Android", en: "TCL / FFalcon Android" },
  { id: "xiaomi", strategy: "xiaomi-diagnostic", zh: "小米 / Redmi（MIUI / HyperOS TV）", en: "Xiaomi / Redmi (MIUI / HyperOS TV)" },
  { id: "hisense", strategy: "hisense-android-diagnostic", zh: "海信 / Vidda Android（非原生 VIDAA）", en: "Hisense / Vidda Android (not native VIDAA)" },
  { id: "coocaa", strategy: "coocaa-diagnostic", zh: "创维 / 酷开 Android", en: "Skyworth / Coocaa Android" },
  { id: "oppo", strategy: "oppo-diagnostic", zh: "OPPO / ColorOS TV Android", en: "OPPO / ColorOS TV Android" },
  { id: "xgimi", strategy: "xgimi-diagnostic", zh: "极米 / GMUI Android", en: "XGIMI / GMUI Android" },
  { id: "konka", strategy: "konka-diagnostic", zh: "康佳 / KKTV Android", en: "Konka / KKTV Android" },
  { id: "letv", strategy: "letv-diagnostic", zh: "乐视 / LeTV Android", en: "LeTV / eUI Android" },
  { id: "webos", strategy: null, zh: "LG webOS", en: "LG webOS" },
  { id: "tizen", strategy: null, zh: "Samsung Tizen", en: "Samsung Tizen" },
  { id: "vidaa", strategy: null, zh: "海信原生 VIDAA / 系统不确定", en: "Hisense native VIDAA / unsure of OS" },
] as const;
export function strategyFor(id: string) {
  return registry.strategies.find(s => s.id === systems.find(s => s.id === id)?.strategy);
}
// Brand selection is guidance, not a substitute for observed device identity.
export function quickCommand(shell: "powershell" | "posix", mode: "plan" | "execute") {
  const flag = mode === "execute" ? " --execute" : "";
  const repo = "https://github.com/lesnolie/Tigris.git";
  if (shell === "powershell") return `$tv = "192.168.1.20:5555"
$tigrisDir = Join-Path $env:TEMP ("tigris-" + [guid]::NewGuid().ToString("N"))
git clone --depth 1 --branch v2.0.13 ${repo} $tigrisDir
if ($LASTEXITCODE -ne 0) { throw "Tigris source download failed" }
python (Join-Path $tigrisDir "tools/install-plan.py") --platform android --serial $tv${flag}`;
  return `TV="192.168.1.20:5555"
TIGRIS_DIR=$(mktemp -d) &&
git clone --depth 1 --branch v2.0.13 ${repo} "$TIGRIS_DIR" &&
python3 "$TIGRIS_DIR/tools/install-plan.py" --platform android --serial "$TV"${flag}`;
}
