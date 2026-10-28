#!/usr/bin/env python3
"""Check built installation pages, not just translation source strings."""
from html.parser import HTMLParser
from pathlib import Path


class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
        self.text = []

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            self.links.append(dict(attrs).get("href", ""))

    def handle_data(self, data):
        self.text.append(data)


root = Path(__file__).resolve().parent.parent
for locale, name in (("zh", "index.html"), ("en", "en/index.html")):
    html = (root / "website/dist" / name).read_text(encoding="utf-8")
    p = Page()
    p.feed(html)
    text = " ".join(p.text)
    beginner, _advanced = html.split('id="advanced-install"', 1)
    assert html.index('id="install-wizard"') < html.index('id="ads-by-brand"') < html.index('id="restore-home"') < html.index('id="advanced-install"')
    assert html.index('id="advanced-install"') < html.index('class="downloads') < html.index("apk-limits")
    assert p.links.count("https://github.com/lesnolie/Tigris/releases") >= 2
    assert "APK" in text and "≠" in text and "webOS" in text and "Tizen" in text
    assert "tools/install-plan.py" in text and "--execute" in text and "192.168.1.20:5555" in text
    assert "python3" in text and "install.sh restore" in text
    assert "v2.0.14" in text
    assert "Tigris-guard-v2.0.14.apk" in text and "Tigris-v2.0.14.apk" in text
    assert ("兼容守护版" if locale == "zh" else "Compatibility Guard") in text
    assert "Sony" in text and "TCL" in text and "Redmi" in text
    assert 'id="install-host"' in html and 'id="install-system"' in html
    assert 'id="install-action"' in html and 'id="android-quick-commands"' in html
    assert 'class="prereq hisense-report"' in html and "WorkBuddy" in text
    assert "Keep ADB enabled: yes" not in text and "保持 ADB 常开：要" not in text
    assert 'id="cmd-agent-xiaomi"' in html and 'data-brand-pick="ffalcon"' in html
    assert 'id="gloss-adb"' in html and 'class="see-now"' in html and 'class="stuck"' in html
    assert ("你现在应该看到" if locale == "zh" else "You should now see") in text
    assert ("卡住了？" if locale == "zh" else "Stuck?") in text
    assert ("示意图" if locale == "zh" else "Diagram, drawn from") in text
    for banned in ("PatchWall", "FallbackHome", "T-Cast", "set-home", "接管"):
        assert banned not in beginner, banned
    print(f"PASS {locale}: beginner wizard, ad table, restore, and one advanced source")
