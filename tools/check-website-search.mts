/**
 * Query → first-result checks for the website install search.
 * Runs against the matcher the page bundles, before and with `pnpm check`.
 */
import assert from "node:assert/strict";
import { searchHelp, searchSuggest } from "../website/src/data/site-search.ts";
import type { Locale, SearchHit } from "../website/src/data/site-search.ts";

interface Expectation {
  locale: Locale;
  query: string;
  first: string;
  count?: number;
  ids?: string[];
  answerIncludes?: string;
  suggested?: boolean;
  labels?: string[];
}

const cases: Expectation[] = [
  {
    locale: "zh",
    query: "连不上",
    first: "connect-wifi",
    count: 4,
    ids: ["connect-wifi", "connect-address", "connect-wake", "connect-allow"],
    answerIncludes: "同一个 Wi-Fi",
  },
  { locale: "zh", query: "黑屏了", first: "restore", answerIncludes: "别慌" },
  { locale: "zh", query: "按主页键还是原来的", first: "home", answerIncludes: "原来的桌面" },
  { locale: "zh", query: "电视没反应", first: "home" },
  { locale: "zh", query: "什么是AI助手", first: "what-ai", answerIncludes: "亲自安装" },
  { locale: "zh", query: "用哪个AI", first: "what-ai", answerIncludes: "亲自安装" },
  {
    locale: "zh",
    query: "电视弹窗了",
    first: "connect-allow",
    ids: ["connect-allow", "dialog-cast", "ads"],
    answerIncludes: "允许 USB 调试",
  },
  {
    locale: "en",
    query: "can't connect",
    first: "connect-wifi",
    count: 4,
    ids: ["connect-wifi", "connect-address", "connect-wake", "connect-allow"],
    answerIncludes: "same Wi-Fi",
  },
  { locale: "en", query: "black screen", first: "restore", answerIncludes: "Don't panic" },
  { locale: "en", query: "home button still shows the old screen", first: "home", answerIncludes: "old home screen" },
  { locale: "en", query: "tv not responding", first: "home" },
  { locale: "en", query: "what is an ai assistant", first: "what-ai", answerIncludes: "operate your computer" },
  { locale: "en", query: "which AI", first: "what-ai", answerIncludes: "operate your computer" },
  {
    locale: "en",
    query: "popup on TV",
    first: "connect-allow",
    ids: ["connect-allow", "dialog-cast", "ads"],
    answerIncludes: "Allow USB debugging",
  },
  {
    locale: "zh",
    query: "卡住了",
    first: "home",
    count: 4,
    labels: ["按主页键还是原来的", "连不上", "黑屏了", "怎么换回去"],
  },
  { locale: "zh", query: "不会弄", first: "home", count: 4 },
  { locale: "zh", query: "怎么办", first: "home", count: 4 },
  {
    locale: "en",
    query: "help",
    first: "home",
    count: 4,
    labels: ["Home button still shows the old screen", "Can't connect", "Black screen", "How do I switch back"],
  },
  { locale: "en", query: "stuck", first: "home", count: 4 },
  {
    locale: "zh",
    query: "zzzz-没有这个说法",
    first: "home",
    count: 4,
    suggested: true,
    labels: ["按主页键还是原来的", "连不上", "黑屏了", "怎么换回去"],
  },
  {
    locale: "en",
    query: "zzzz-not-a-real-problem",
    first: "home",
    count: 4,
    suggested: true,
    labels: ["Home button still shows the old screen", "Can't connect", "Black screen", "How do I switch back"],
  },
];

function describe(hit: SearchHit | undefined) {
  if (!hit) return "(none)";
  return `${hit.id} | ${hit.label || hit.answer}`;
}

for (const item of cases) {
  const hits = searchHelp(item.locale, item.query);
  const first = hits[0];
  assert.ok(first, `${item.locale} ${item.query} returned nothing`);
  assert.equal(first.id, item.first, `${item.locale} “${item.query}” first was ${describe(first)}`);
  if (item.count !== undefined) {
    assert.equal(hits.length, item.count, `${item.locale} “${item.query}” count ${hits.length}: ${hits.map(describe).join(" || ")}`);
  }
  if (item.ids) assert.deepEqual(hits.map((hit) => hit.id), item.ids, item.query);
  if (item.answerIncludes) assert.ok(first.answer.includes(item.answerIncludes), first.answer);
  if (item.labels) assert.deepEqual(hits.map((hit) => hit.label), item.labels, item.query);
  const suggested = hits.every((hit) => hit.suggested);
  assert.equal(suggested, item.suggested === true, `${item.query} suggested=${suggested}`);
}

const zhAi = searchHelp("zh", "什么是AI助手")[0];
const zhWhich = searchHelp("zh", "用哪个AI")[0];
assert.equal(zhAi.id, zhWhich.id);
assert.equal(zhAi.answer, zhWhich.answer);

const enAi = searchHelp("en", "what is an ai assistant")[0];
const enWhich = searchHelp("en", "which AI")[0];
assert.equal(enAi.id, enWhich.id);
assert.equal(enAi.answer, enWhich.answer);

for (const text of [searchSuggest("zh"), searchSuggest("en")]) {
  assert.ok(text.length > 0);
  assert.equal(/换个说法试试|Try different words/i.test(text), false, text);
}

console.log(`site search: ${cases.length} query checks passed`);
