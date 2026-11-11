/* Structural copy contract shared by zh.ts and en.ts. Optional fields are
   declared here so neither locale's `satisfies` narrows them away. */
import type { FaqItem, Step } from "./interfaces";

export type Locale = "zh" | "en";

export interface Copy {
  skip: string;
  navLabel: string;
  menuLabel: string;
  menuCloseLabel: string;
  nav: { href: string; label: string }[];
  langSwitch: { href: string; label: string; lang: string };
  headerCta: string;
  menuCta: string;
  menuNote: string;
  copy: string;
  copied: string;
  copyFailed: string;
  compareLabel: string;
  compareValue: (percent: number) => string;
  slideshow: { prev: string; next: string };
  lightbox: { close: string; prev: string; next: string; name: string };
  year: number;
  meta: {
    title: string;
    description: string;
    canonical: string;
    alts: { hreflang: string; href: string }[];
    ogLocale: string;
    ogLocaleAlt: string;
    ogTitle: string;
    ogDescription: string;
  };
  hero: {
    eyebrow: string;
    titleLines: [string, string];
    sub: string;
    primary: string;
    secondary: string;
    chips: string[];
    note: string;
    imageAlt: string;
    floating: { title: string; sub: string }[];
  };
  proof: string[];
  compare: {
    eyebrow: string;
    title: string;
    lede: string;
    beforeLabel: string;
    beforeSub: string;
    afterLabel: string;
    afterSub: string;
    beforeAlt: string;
    afterAlt: string;
    hintKeys: string;
    disclaimer: string;
  };
  aerial: {
    eyebrow: string;
    title: string;
    lede: string;
    bullets: string[];
    caption: string;
    frameAlt: string;
    bulletsTitle: string;
    attribution: string;
  };
  features: { eyebrow: string; title: string; lede: string };
  film: { eyebrow: string; title: string; lede: string; label: string; classicTitle: string; classicLabel: string };
  gallery: { eyebrow: string; title: string; lede: string };
  install: {
    eyebrow: string;
    title: string;
    lede: string;
    packages: { name: string; use: string; href: string }[];
    prereq: string;
    shellNote: string;
    shellLabels: { posix: string; powershell: string };
    placeholderNote: string;
    adbTable: { caption: string; head: string[]; rows: string[][] };
    tabsLabel: string;
    tabs: {
      id: string;
      label: string;
      intro?: string;
      danger?: string;
      steps: Step[];
    }[];
    advanced: {
      title: string;
      body: string;
      rows: { name: string; use: string }[];
    };
    agent: { title: string; body: string; prompt: string; note: string };
    safety: string;
  };
  compatibility: {
    eyebrow: string;
    title: string;
    lede: string;
    caption: string;
    head: string[];
    rows: { feature: string; any: string; debug: string; sony: string }[];
    notes: { title: string; body: string }[];
    scope: string;
  };
  faq: { eyebrow: string; title: string; items: FaqItem[] };
  finalCta: {
    eyebrow: string;
    title: string;
    body: string;
    primary: string;
    secondary: string;
  };
  footer: {
    blurb: string;
    cols: { title: string; links: { href: string; label: string }[] }[];
    legal: string;
    langLink: { href: string; label: string; lang: string };
  };
}
