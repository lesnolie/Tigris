import { initHeroMotion } from "./hero-motion";
import { searchHelp } from "../data/site-search";

/* Tigris website — progressive enhancement only. No framework, ~8 KB.
   All user-visible strings come from data-* attributes so the same script
   speaks Chinese on / and English on /en/. */
(() => {
  "use strict";

  const doc = document;
  const body = doc.body;
  const lang = (doc.documentElement.lang || "zh").toLowerCase();
  const isEnglish = lang.startsWith("en");

  /* Every user-facing string is supplied per locale by the layout. The English
     literals below are only a safety net if an attribute is missing. */
  const t = {
    copy: body?.dataset.copyLabel || (isEnglish ? "Copy" : "复制"),
    copied: body?.dataset.copied || (isEnglish ? "Copied" : "已复制"),
    copyFailed: body?.dataset.copyFailed || (isEnglish ? "Copy failed" : "复制失败"),
    compareTemplate:
      body?.dataset.compareValue || (isEnglish ? "Showing {p}% of the before image" : "显示 {p}% 的安装前画面"),
  };


  const host = document.querySelector<HTMLSelectElement>("#install-host");
  const system = document.querySelector<HTMLSelectElement>("#install-system");
  const action = document.querySelector<HTMLSelectElement>("#install-action");
  function updateQuickInstall() {
    document.querySelectorAll<HTMLElement>("[data-install-system]").forEach(el => { el.hidden = el.dataset.installSystem !== system?.value; });
    const commands = document.querySelector<HTMLElement>("#android-quick-commands");
    if (commands) commands.hidden = ["webos", "tizen", "vidaa"].includes(system?.value || "");
    document.querySelectorAll<HTMLElement>("[data-install-command]").forEach(el => { el.hidden = el.dataset.installCommand !== `${host?.value}-${action?.value}`; });
  }
  [host, system, action].forEach(el => el?.addEventListener("change", updateQuickInstall));
  updateQuickInstall();

  const shellStorageKey = "tigris-shell";
  const initialShell = (() => {
    try {
      const stored = window.localStorage.getItem(shellStorageKey);
      if (stored === "posix" || stored === "powershell") return stored;
    } catch {
      /* private mode or blocked storage: fall through */
    }
    return "powershell";
  })();

  /* ---------- header ---------- */
  const header = doc.querySelector(".site-header");
  const toggle = doc.querySelector<HTMLButtonElement>(".nav-toggle");
  const navLinks = doc.querySelector(".nav-links");

  const onScroll = () => {
    if (header) header.classList.toggle("is-scrolled", window.scrollY > 8);
  };
  onScroll();
  window.addEventListener("scroll", onScroll, { passive: true });

  if (toggle && header) {
    const setMenu = (open: boolean) => {
      header.classList.toggle("is-open", open);
      toggle.setAttribute("aria-expanded", String(open));
      const label = open ? toggle.dataset.labelClose : toggle.dataset.labelOpen;
      if (label) toggle.setAttribute("aria-label", label);
      doc.body.classList.toggle("menu-open", open);
    };
    toggle.addEventListener("click", () => setMenu(!header.classList.contains("is-open")));
    navLinks?.addEventListener("click", (e) => {
      if ((e.target as HTMLElement).closest("a")) setMenu(false);
    });
    doc.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && header.classList.contains("is-open")) {
        setMenu(false);
        toggle.focus();
      }
    });
  }

  /* ---------- before / after slider ---------- */
  doc.querySelectorAll<HTMLElement>("[data-compare]").forEach((compare) => {
    const handle = compare.querySelector<HTMLElement>(".compare-handle");
    if (!handle) return;
    const template = handle.dataset.valuetemplate || t.compareTemplate;
    let pos = 50;
    let dragging = false;

    const render = () => {
      compare.style.setProperty("--pos", pos + "%");
      handle.setAttribute("aria-valuenow", String(Math.round(pos)));
      handle.setAttribute("aria-valuetext", template.replace("{p}", String(Math.round(pos))));
    };

    const setFromX = (clientX: number) => {
      const rect = compare.getBoundingClientRect();
      const next = ((clientX - rect.left) / rect.width) * 100;
      pos = Math.min(100, Math.max(0, next));
      render();
    };

    compare.addEventListener("pointerdown", (e) => {
      if (e.button !== 0) return;
      dragging = true;
      compare.setPointerCapture?.(e.pointerId);
      setFromX(e.clientX);
      if (e.target !== handle) handle.focus({ preventScroll: true });
    });
    compare.addEventListener("pointermove", (e) => {
      if (!dragging) return;
      e.preventDefault();
      setFromX(e.clientX);
    });
    const stop = (e: PointerEvent) => {
      if (!dragging) return;
      dragging = false;
      compare.releasePointerCapture?.(e.pointerId);
    };
    compare.addEventListener("pointerup", stop);
    compare.addEventListener("pointercancel", stop);
    compare.addEventListener("lostpointercapture", () => { dragging = false; });

    handle.addEventListener("keydown", (e) => {
      const step = e.shiftKey ? 10 : 2;
      let next = pos;
      switch (e.key) {
        case "ArrowLeft":
        case "ArrowDown": next = pos - step; break;
        case "ArrowRight":
        case "ArrowUp": next = pos + step; break;
        case "Home": next = 0; break;
        case "End": next = 100; break;
        case "PageDown": next = pos - 20; break;
        case "PageUp": next = pos + 20; break;
        default: return;
      }
      e.preventDefault();
      pos = Math.min(100, Math.max(0, next));
      render();
    });
    compare.addEventListener("dblclick", () => { pos = 50; render(); });
    render();
  });

  /* ---------- aerial / photo slideshow ---------- */
  doc.querySelectorAll<HTMLElement>("[data-slideshow]").forEach((root) => {
    const slides = Array.from(root.querySelectorAll<HTMLElement>("[data-slide]"));
    const dots = Array.from(root.querySelectorAll<HTMLElement>(".aerial-dots i"));
    const caption = doc.querySelector<HTMLElement>("[data-slide-caption]");
    let index = 0;
    let timer: number | undefined;
    if (slides.length < 2) return;

    const show = (i: number) => {
      index = (i + slides.length) % slides.length;
      slides.forEach((s, n) => {
        s.classList.toggle("is-active", n === index);
        s.setAttribute("aria-hidden", n === index ? "false" : "true");
      });
      dots.forEach((d, n) => d.classList.toggle("is-on", n === index));
      if (caption) caption.textContent = slides[index].dataset.caption || "";
    };

    const start = () => {
      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
      stop();
      timer = window.setInterval(() => show(index + 1), 6000);
    };
    const stop = () => { if (timer) window.clearInterval(timer); timer = undefined; };

    root.querySelector("[data-slide-prev]")?.addEventListener("click", () => { show(index - 1); start(); });
    root.querySelector("[data-slide-next]")?.addEventListener("click", () => { show(index + 1); start(); });
    root.addEventListener("pointerenter", stop);
    root.addEventListener("pointerleave", start);
    root.addEventListener("focusin", stop);
    start();
  });

  /* ---------- install tabs ---------- */
  doc.querySelectorAll<HTMLElement>("[data-tabs]").forEach((tabs) => {
    const list = tabs.querySelector<HTMLElement>('[role="tablist"]');
    const buttons = Array.from(tabs.querySelectorAll<HTMLButtonElement>('[role="tab"]'));
    const panels = Array.from(tabs.querySelectorAll<HTMLElement>('[role="tabpanel"]'));
    if (!list || !buttons.length) return;

    const select = (i: number, focus = false) => {
      buttons.forEach((btn, n) => {
        const on = n === i;
        btn.setAttribute("aria-selected", String(on));
        btn.tabIndex = on ? 0 : -1;
        panels[n]?.toggleAttribute("hidden", !on);
        if (on && focus) btn.focus();
      });
    };

    buttons.forEach((btn, i) => btn.addEventListener("click", () => select(i)));
    list.addEventListener("keydown", (e) => {
      const current = buttons.findIndex((b) => b.getAttribute("aria-selected") === "true");
      let next = current;
      if (e.key === "ArrowRight") next = (current + 1) % buttons.length;
      else if (e.key === "ArrowLeft") next = (current - 1 + buttons.length) % buttons.length;
      else if (e.key === "Home") next = 0;
      else if (e.key === "End") next = buttons.length - 1;
      else return;
      e.preventDefault();
      select(next, true);
    });
    select(0);
  });

  /* ---------- beginner install wizard ---------- */
  const wizard = doc.querySelector<HTMLElement>("[data-wizard]");
  if (wizard) {
    const picks = Array.from(wizard.querySelectorAll<HTMLButtonElement>("[data-brand-pick]"));
    const panels = Array.from(wizard.querySelectorAll<HTMLElement>("[data-brand-panel]"));
    const live = wizard.querySelector<HTMLElement>("[data-wizard-live]");
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    const selectBrand = (id: string, focus = false) => {
      picks.forEach((btn, i) => {
        const on = btn.dataset.brandPick === id;
        btn.setAttribute("aria-checked", on ? "true" : "false");
        btn.classList.toggle("is-on", on);
        btn.tabIndex = on || (!id && i === 0) ? 0 : -1;
        if (on && focus) btn.focus();
      });
      panels.forEach((panel) => {
        panel.hidden = panel.dataset.brandPanel !== id;
      });
      const picked = picks.find((btn) => btn.dataset.brandPick === id);
      if (live && picked) {
        const name = picked.textContent?.trim() ?? "";
        live.textContent = isEnglish
          ? `${name} selected. The steps are below.`
          : `已选出${name}。步骤在下面。`;
      }
      if (id) {
        wizard.querySelector<HTMLElement>(`#steps-${id}`)?.scrollIntoView({
          behavior: reduceMotion ? "auto" : "smooth",
          block: "start",
        });
      }
    };

    picks.forEach((btn) => btn.addEventListener("click", () => selectBrand(btn.dataset.brandPick || "")));
    wizard.querySelector("[data-brand-group]")?.addEventListener("keydown", (event) => {
      const key = (event as KeyboardEvent).key;
      if (!["ArrowRight", "ArrowDown", "ArrowLeft", "ArrowUp", "Home", "End"].includes(key)) return;
      event.preventDefault();
      const checked = picks.findIndex((btn) => btn.getAttribute("aria-checked") === "true");
      const current = checked >= 0 ? checked : Math.max(0, picks.indexOf(document.activeElement as HTMLButtonElement));
      let next = current < 0 ? 0 : current;
      if (key === "ArrowRight" || key === "ArrowDown") next = (next + 1) % picks.length;
      else if (key === "ArrowLeft" || key === "ArrowUp") next = (next - 1 + picks.length) % picks.length;
      else if (key === "Home") next = 0;
      else if (key === "End") next = picks.length - 1;
      selectBrand(picks[next].dataset.brandPick || "", true);
    });

    const inputs = Array.from(wizard.querySelectorAll<HTMLInputElement>(".tv-ip"));
    const fills = Array.from(wizard.querySelectorAll<HTMLElement>("[data-ip-fill]"));
    const originals = new Map(fills.map((el) => [el, el.textContent || ""]));
    const applyIp = (raw: string) => {
      const ip = raw.trim();
      const slot = inputs[0]?.dataset.ipSlot || "";
      const example = inputs[0]?.dataset.ipExample || "";
      fills.forEach((el) => {
        let text = originals.get(el) || "";
        if (el.dataset.ipFill === "slot" && slot) text = text.split(slot).join(ip || slot);
        if (el.dataset.ipFill === "example" && example) text = text.split(example).join(ip || example);
        el.textContent = text;
      });
    };
    inputs.forEach((input) => {
      input.addEventListener("input", () => {
        inputs.forEach((other) => {
          if (other !== input) other.value = input.value;
        });
        applyIp(input.value);
      });
    });

    const closeTerms = () => {
      doc.querySelectorAll("a.term.is-open").forEach((el) => {
        el.classList.remove("is-open");
        el.setAttribute("aria-expanded", "false");
      });
    };
    doc.addEventListener("click", (event) => {
      const target = event.target as HTMLElement;
      const wrap = target.closest(".term-wrap");
      if (!wrap) {
        closeTerms();
        return;
      }
      const term = target.closest("a.term");
      if (!term) return;
      const mouse = event as MouseEvent;
      if (mouse.metaKey || mouse.ctrlKey || mouse.shiftKey || mouse.altKey || mouse.button) return;
      event.preventDefault();
      const willOpen = !term.classList.contains("is-open");
      closeTerms();
      if (willOpen) {
        term.classList.add("is-open");
        term.setAttribute("aria-expanded", "true");
      }
    });
  }

  /* ---------- install shell switch (PowerShell / macOS · Linux) ---------- */
  let currentShell: "posix" | "powershell" = initialShell;

  const applyShell = (shell: "posix" | "powershell") => {
    currentShell = shell;
    doc.querySelectorAll<HTMLElement>("[data-shell-panel]").forEach((panel) => {
      panel.hidden = panel.dataset.shellPanel !== shell;
    });
    doc.querySelectorAll<HTMLElement>("[data-cmd]").forEach((code) => {
      try {
        const parsed = JSON.parse(code.dataset.cmd || "{}") as Record<string, string>;
        const text = parsed[shell];
        if (text) code.textContent = text;
      } catch {
        /* leave the rendered text alone */
      }
    });
    doc.querySelectorAll<HTMLButtonElement>("[data-shell]").forEach((btn) => {
      const on = btn.dataset.shell === shell;
      btn.setAttribute("aria-pressed", String(on));
      btn.classList.toggle("is-on", on);
    });
    try {
      window.localStorage.setItem(shellStorageKey, shell);
    } catch {
      /* ignore storage failures */
    }
  };

  doc.querySelectorAll<HTMLButtonElement>("[data-shell]").forEach((btn) => {
    btn.addEventListener("click", () => applyShell(btn.dataset.shell === "posix" ? "posix" : "powershell"));
  });

  if (doc.querySelector("[data-cmd]")) applyShell(currentShell);

  /* ---------- copy buttons: label span, icon untouched ---------- */
  const fallbackCopy = (text: string) => {
    const area = doc.createElement("textarea");
    area.value = text;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.top = "-1000px";
    doc.body.appendChild(area);
    area.select();
    let ok = false;
    try {
      ok = doc.execCommand("copy");
    } catch {
      ok = false;
    }
    area.remove();
    return ok;
  };

  doc.querySelectorAll<HTMLButtonElement>("[data-copy]").forEach((btn) => {
    const label = btn.querySelector<HTMLElement>(".label");
    if (label && !label.textContent) label.textContent = t.copy;

    /* Resolve the text from the referenced element, then from a sibling <pre>
       so a mistyped selector degrades instead of silently doing nothing.
       Command blocks carry both shell variants in data-cmd, so copying always
       matches the shell the reader selected (not just what is displayed). */
    const resolveText = () => {
      const sel = btn.dataset.copy || "";
      let node: HTMLElement | null = sel ? doc.querySelector<HTMLElement>(sel) : null;
      if (!node) node = btn.parentElement?.querySelector<HTMLElement>("pre code") ?? null;
      if (!node) return "";
      const variants = node.dataset.cmd;
      if (variants) {
        try {
          const parsed = JSON.parse(variants) as Record<string, string>;
          const text = parsed[currentShell];
          if (text) return text.trim();
        } catch {
          /* fall back to whatever is rendered */
        }
      }
      return node.textContent?.trim() ?? "";
    };

    let reset: number | undefined;
    const flash = (ok: boolean) => {
      if (label) {
        label.textContent = ok ? t.copied : t.copyFailed;
        window.clearTimeout(reset);
        reset = window.setTimeout(() => { label.textContent = t.copy; }, 1800);
      }
      btn.classList.toggle("is-done", ok);
      btn.setAttribute("data-state", ok ? "copied" : "failed");
    };

    btn.addEventListener("click", async () => {
      const text = resolveText();
      if (!text) {
        flash(false);
        return;
      }
      let ok = false;
      try {
        await navigator.clipboard.writeText(text);
        ok = true;
      } catch {
        ok = fallbackCopy(text);
      }
      flash(ok);
    });
  });

  /* ---------- gallery lightbox ---------- */
  const lightbox = doc.querySelector<HTMLDialogElement>("[data-lightbox]");
  const triggers = Array.from(doc.querySelectorAll<HTMLButtonElement>("[data-lightbox-open]"));
  if (lightbox && triggers.length && typeof lightbox.showModal === "function") {
    const img = lightbox.querySelector<HTMLImageElement>("img");
    const video = lightbox.querySelector<HTMLVideoElement>("video");
    const caption = lightbox.querySelector<HTMLElement>("[data-lightbox-caption]");
    const counter = lightbox.querySelector<HTMLElement>("[data-lightbox-counter]");
    let index = 0;

    const data = triggers.map((el) => ({
      src: el.dataset.full || "",
      poster: el.dataset.poster || "",
      alt: el.dataset.alt || "",
      caption: el.dataset.caption || "",
      video: el.dataset.video !== undefined,
    }));

    const show = (i: number) => {
      index = (i + data.length) % data.length;
      const item = data[index];
      if (!img || !video) return;
      img.hidden = item.video;
      video.hidden = !item.video;
      if (item.video) {
        img.removeAttribute("src");
        video.muted = true;
        video.poster = item.poster;
        video.src = item.src;
        video.setAttribute("aria-label", item.alt);
        const startPlayback = () => {
          if (video.hidden) return;
          video.play().catch(() => {});
        };
        video.addEventListener("canplay", startPlayback, { once: true });
        video.load();
        requestAnimationFrame(startPlayback);
      } else {
        video.pause();
        video.removeAttribute("src");
        video.removeAttribute("poster");
        img.src = item.src;
        img.alt = item.alt;
      }
      if (caption) caption.textContent = item.caption;
      if (counter) counter.textContent = `${index + 1} / ${data.length}`;
    };

    triggers.forEach((el, i) => el.addEventListener("click", () => {
      /* Play only after the dialog is visible. Browsers reject play() while a closed
         dialog is inert, leaving the viewer on the video's black first frame. */
      lightbox.showModal();
      show(i);
    }));
    doc.querySelectorAll<HTMLElement>(".gallery figure").forEach((figure) => {
      figure.addEventListener("click", (event) => {
        if ((event.target as Element).closest("[data-lightbox-open]")) return;
        figure.querySelector<HTMLButtonElement>("[data-lightbox-open]")?.click();
      });
    });
    lightbox.querySelector("[data-prev]")?.addEventListener("click", () => show(index - 1));
    lightbox.querySelector("[data-next]")?.addEventListener("click", () => show(index + 1));
    lightbox.querySelector("[data-close]")?.addEventListener("click", () => lightbox.close());
    lightbox.addEventListener("close", () => video?.pause());
    lightbox.addEventListener("click", (e) => { if (e.target === lightbox) lightbox.close(); });
    lightbox.addEventListener("keydown", (e) => {
      if (e.key === "ArrowRight") show(index + 1);
      if (e.key === "ArrowLeft") show(index - 1);
    });
  }

  /* ---------- recordings: muted, play only while on screen; the hero button pauses them all ----------
     They are the product itself, so they also play with reduced motion (often just Windows with
     animation effects off); the button is the way to stop them. */
  const clipEls = Array.from(doc.querySelectorAll<HTMLVideoElement>("video[data-clip]"));
  const clipToggle = doc.querySelector<HTMLButtonElement>("[data-clip-toggle]");
  let clipsPaused = false;
  const onScreen = new Set<HTMLVideoElement>();
  if (clipEls.length && "IntersectionObserver" in window) {
    const watch = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          const v = entry.target as HTMLVideoElement;
          if (entry.intersectionRatio >= 0.2) {
            onScreen.add(v);
            if (!clipsPaused) v.play().catch(() => {});
          } else {
            onScreen.delete(v);
            v.pause();
          }
        });
      },
      { threshold: [0, 0.2] },
    );
    clipEls.forEach((v) => watch.observe(v));
  }
  clipToggle?.addEventListener("click", () => {
    clipsPaused = !clipsPaused;
    clipToggle.setAttribute("aria-pressed", String(clipsPaused));
    clipToggle.setAttribute("aria-label", (clipsPaused ? clipToggle.dataset.labelPlay : clipToggle.dataset.labelPause) || "");
    clipEls.forEach((v) => (clipsPaused ? v.pause() : onScreen.has(v) && v.play().catch(() => {})));
  });

  /* ---------- reveal on scroll ---------- */
  const items = Array.from(doc.querySelectorAll<HTMLElement>(".reveal"));
  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (!items.length || reduce || !("IntersectionObserver" in window)) {
    items.forEach((el) => el.classList.add("is-visible"));
  } else {
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add("is-visible");
          io.unobserve(entry.target);
        });
      },
      { rootMargin: "0px 0px -8% 0px", threshold: 0.08 }
    );
    items.forEach((el) => io.observe(el));
  }

  /* ---------- site search (Spotlight overlay; no shortcut is shown) ---------- */
  const searchDialog = doc.querySelector<HTMLDialogElement>("[data-search-dialog]");
  const searchInput = searchDialog?.querySelector<HTMLInputElement>("[data-search-input]");
  const searchResults = searchDialog?.querySelector<HTMLElement>("[data-search-results]");
  const searchEmpty = searchDialog?.querySelector<HTMLElement>("[data-search-empty]");
  const searchLocale = isEnglish ? "en" : "zh";

  const clearSearchMark = () => {
    doc.querySelectorAll(".is-search-hit").forEach((el) => el.classList.remove("is-search-hit"));
  };

  const showBrandPanel = (id: string) => {
    doc.querySelectorAll<HTMLButtonElement>("[data-brand-pick]").forEach((btn, i) => {
      const on = btn.dataset.brandPick === id;
      btn.setAttribute("aria-checked", on ? "true" : "false");
      btn.classList.toggle("is-on", on);
      btn.tabIndex = on || i === 0 ? 0 : -1;
    });
    doc.querySelectorAll<HTMLElement>("[data-brand-panel]").forEach((panel) => {
      panel.hidden = panel.dataset.brandPanel !== id;
    });
  };

  const jumpToHit = (hit: { stuckKey?: string; elementId?: string }) => {
    clearSearchMark();
    let node: HTMLElement | null = null;
    if (hit.stuckKey) {
      const nodes = Array.from(doc.querySelectorAll<HTMLElement>(`[data-stuck-key="${hit.stuckKey}"]`));
      const selected = doc.querySelector<HTMLElement>('[data-brand-pick][aria-checked="true"]')?.dataset.brandPick;
      node = nodes.find((item) => item.closest<HTMLElement>("[data-brand-panel]")?.dataset.brandPanel === selected) || nodes[0] || null;
    } else if (hit.elementId) {
      node = doc.getElementById(hit.elementId);
    }
    if (!node) return;
    const panel = node.closest<HTMLElement>("[data-brand-panel]");
    if (panel?.hidden && panel.dataset.brandPanel) showBrandPanel(panel.dataset.brandPanel);
    const details = node instanceof HTMLDetailsElement ? node : node.querySelector<HTMLDetailsElement>("details.stuck, details.faq");
    if (details) details.open = true;
    const mark = node.closest<HTMLElement>(".wizard-step") || node;
    mark.classList.add("is-search-hit");
    mark.scrollIntoView({ block: "center", behavior: "smooth" });
  };

  const renderSearch = (query: string) => {
    if (!searchResults || !searchEmpty) return;
    const hits = searchHelp(searchLocale, query);
    searchResults.replaceChildren();
    searchEmpty.hidden = hits.length > 0;
    hits.forEach((hit) => {
      const link = doc.createElement("a");
      link.href = hit.elementId ? `#${hit.elementId}` : `#stuck-${hit.stuckKey || "install"}`;
      link.className = "search-hit";
      const answer = doc.createElement("strong");
      answer.textContent = hit.answer;
      const section = doc.createElement("span");
      section.textContent = hit.section;
      link.append(answer, section);
      link.addEventListener("click", (event) => {
        event.preventDefault();
        searchDialog?.close();
        jumpToHit(hit);
      });
      searchResults.append(link);
    });
  };

  const openSearch = (query: string) => {
    if (!searchDialog || !searchInput) return;
    if (!searchDialog.open) searchDialog.showModal();
    searchInput.value = query;
    renderSearch(query);
    searchInput.focus();
    const end = query.length;
    searchInput.setSelectionRange(end, end);
  };

  doc.addEventListener("click", (event) => {
    const opener = (event.target as Element | null)?.closest<HTMLElement>("[data-search-open]");
    if (!opener || !searchDialog) return;
    event.preventDefault();
    openSearch(opener.dataset.searchQuery || "");
  });

  searchInput?.addEventListener("input", () => renderSearch(searchInput.value));
  searchInput?.addEventListener("keydown", (event) => {
    if (event.key !== "Enter") return;
    const first = searchResults?.querySelector<HTMLAnchorElement>("a.search-hit");
    if (!first) return;
    event.preventDefault();
    first.click();
  });
  searchDialog?.addEventListener("click", (event) => {
    if (event.target === searchDialog) searchDialog.close();
  });
  searchDialog?.addEventListener("close", () => {
    if (searchInput) searchInput.value = "";
  });
  doc.addEventListener("keydown", (event) => {
    const key = event.key.toLowerCase();
    const metaK = (event.metaKey || event.ctrlKey) && key === "k";
    const target = event.target instanceof HTMLElement ? event.target : null;
    const typing = !!target?.closest("input, textarea, select, [contenteditable='true']");
    const slash = key === "/" && !typing && !event.metaKey && !event.ctrlKey && !event.altKey;
    if (!metaK && !slash) return;
    event.preventDefault();
    if (metaK && searchDialog?.open) {
      searchDialog.close();
      return;
    }
    openSearch("");
  });

  initHeroMotion();
})();
