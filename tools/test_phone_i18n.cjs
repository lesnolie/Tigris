const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const vm = require("node:vm");

const root = path.resolve(__dirname, "..");
const html = fs.readFileSync(path.join(root, "app/src/main/assets/web/index.html"), "utf8");
const code = fs.readFileSync(path.join(root, "app/src/main/assets/web/i18n.js"), "utf8");
const server = fs.readFileSync(path.join(root, "app/src/main/java/com/leslie/tvhome/PhoneServer.kt"), "utf8");

function language(id) {
  const window = {navigator: {languages: [id], language: id}};
  vm.runInNewContext(code, {window});
  return window.TigrisPhoneI18n;
}

const en = language("en-GB"), zh = language("zh-CN"), fallback = language("de-DE");
assert.equal(en.lang, "en");
assert.equal(zh.lang, "zh");
assert.equal(fallback.lang, "en");
assert.equal(en.t("wallpaper"), "Home Screen Wallpaper");
assert.equal(zh.t("wallpaper"), "主屏幕背景");
assert.equal(en.t("uploadedPhotos", 2), "Uploaded 2 photos");
assert.equal(zh.t("uploadedPhotos", 2), "已上传 2 张");
assert.equal(en.sourceLabel("daily", "风景照片"), "Landscape Photos");
assert.equal(zh.sourceLabel("blue", "Blue"), "蓝色");
assert.equal(en.sourceLabel("video_aerial", "Apple 航拍"), "Apple aerials · 4K");
assert.equal(zh.sourceLabel("video_local", "My video"), "我的视频 · 4K");
assert.equal(en.sourceLabel("wallhaven", "wallhaven"), "Wallhaven");
assert.equal(en.sourceLabel("new_source", "Preserved label"), "Preserved label");
assert.match(html, /<script src="i18n\.js"><\/script>/);
assert.match(html, /apply\(document\)/);
assert.match(server, /path == "\/i18n\.js" && !post/);
assert.match(server, /"application\/javascript; charset=utf-8"/);
assert.doesNotMatch(html, /[\u4e00-\u9fff]/, "Do not hard-code Chinese in English HTML/JS");

// Ensure the localized strings cover every visible static and dynamic string key.
const manifest = code.match(/var messages = ([\s\S]*?);\r?\n  function t/);
assert.ok(manifest, "Phone locale dictionaries must be parsable");
const dictionaries = JSON.parse(manifest[1]);
const keys = new Set([
  ...Array.from(html.matchAll(/data-i18n(?:-placeholder)?="([^"]+)"/g), m => m[1]),
  ...Array.from(html.matchAll(/\bt\('([^']+)'/g), m => m[1])
]);
for (const key of keys) {
  assert.ok(dictionaries.en[key], "Missing English: " + key);
  assert.ok(dictionaries.zh[key], "Missing Chinese: " + key);
}
for (const lang of ["en", "zh"]) {
  for (const [key, value] of Object.entries(dictionaries[lang])) {
    assert.ok(value.length, "Empty " + lang + ": " + key);
    const matches = [...value.matchAll(/\{(\d+)\}/g)].map(x => x[1]);
    const other = dictionaries[lang === "en" ? "zh" : "en"][key];
    assert.ok(other, "Missing language counterpart: " + key);
    assert.deepEqual(matches, [...other.matchAll(/\{(\d+)\}/g)].map(x => x[1]),
      "Missing interpolation in " + key);
  }
}
console.log("Phone companion language coverage, dynamic copy, API route and browser fallback PASS");
