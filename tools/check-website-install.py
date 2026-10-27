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
    assert html.index('class="downloads') < html.index('apk-limits') < html.index('agent-card')
    assert p.links.count("https://github.com/lesnolie/Tigris/releases") >= 2
    assert "APK" in text and "≠" in text and "webOS" in text and "Tizen" in text
    assert "--platform android --serial 192.168.1.20:5555" in text
    assert "python tools/install-plan.py" in text and "python3 tools/install-plan.py" in text
    assert "install.sh restore" in text and "--execute" in text
    assert "v2.0.13" in text
    assert "Tigris-guard-v2.0.13.apk" in text and "Tigris-v2.0.13.apk" in text
    assert ("兼容守护版" if locale == "zh" else "Compatibility Guard") in text
    assert "Sony" in text and "TCL" in text and "Redmi" in text
    assert 'id="install-host"' in html and 'id="install-system"' in html
    assert 'id="install-action"' in html and 'id="android-quick-commands"' in html
    assert 'class="prereq hisense-report"' in html and "WorkBuddy" in text
    assert "Keep ADB enabled: yes" not in text and "保持 ADB 常开：要" not in text
    print(f"PASS {locale}: adjacent APK limits, official downloads, shared strategies, shell variants and restore")
