"""Prepare release documentation and social cards using the existing desktop capture."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/social/v2.0.27'
OUT.mkdir(parents=True, exist_ok=True)
NL = chr(10)
sections = {
    'README.md': '''## v2.0.27：品牌横图与堆叠应用画面

- **桌面图标**：统一 16:9，自动优先用应用自带品牌 TV banner；没有可用横图时统一白底，保留手动来源设置。去掉额外白框与光晕，选中放大至 1.12 倍，保留 Dock 液态玻璃。
- **应用切换**：画面卡片堆叠，左右选择、确定打开，默认选中最近使用的任务。没有截图仍可打开，深色画面不再被误删；菜单键可尝试系统切换器。
- **操作与阅读**：简化控制中心标签，保留电视及活动媒体控制；Top Shelf 支持遥控浏览，返回桌面尽量保留应用位置，文字大小可选 90% / 100% / 115% / 130%。

画面预览是 Android 11+ 已启用无障碍服务保存的截图，不是持续实时画面；Android 9/10、受保护内容或权限缺失时可能没有预览，但任务卡片仍保留。向上移除卡片；关闭应用依赖已授权的本机 ADB。版本码 46 高于 Preview 12 的 45，保留签名，可覆盖升级。详见 [v2.0.27 更新说明](docs/releases/v2.0.27.md)。''',
    'README.en.md': '''## v2.0.27: brand banners and stacked app previews

- **Home artwork:** uniform 16:9 cards automatically prefer the app's TV banner; missing banners use white cards with original icons. Manual overrides remain. Selection scales to 1.12x without added frames or halos; the Liquid Glass Dock stays.
- **App switching:** stacked screen cards, Left/Right to choose and OK to open, with the newest task selected first. Missing previews never hide tasks; dark frames remain valid. Menu attempts native Recents.
- **Controls and reading:** simpler Control Center tabs, retained TV and active-session media controls, remote Top Shelf browsing, improved Home return focus, and 90% / 100% / 115% / 130% text scale.

Previews are saved accessibility screenshots on Android 11+, not continuously live screens. Android 9/10, protected content or missing permissions may have no preview, while tasks remain usable. Up removes a card; force-stop requires authorized local ADB. Version code 46 upgrades Preview 12 (45) in place with the same signing identity. See the [v2.0.27 release notes](docs/releases/v2.0.27.md).'''
}
for name, section in sections.items():
    p = ROOT / name
    text = p.read_text(encoding='utf-8')
    if section in text:
        continue
    cutoff = text.index('## v2.0.26')
    intro, history = text[:cutoff], text[cutoff:]
    intro = intro.replace('v2.0.26', 'v2.0.27')
    lines = intro.splitlines()
    for i, line in enumerate(lines):
        if line.startswith('> **最新正式版为'):
            lines[i] = '> **最新正式版为 v2.0.27。** 品牌横向 TV banner、无白框选中放大、液态玻璃 Dock，以及堆叠应用截图。对应 Standard/Guard 类型覆盖升级；详见 [更新说明](docs/releases/v2.0.27.md)。'
        elif line.startswith('> **') and 'v2.0.27' in line:
            lines[i] = '> **Latest stable release: v2.0.27.** Brand banners, scale-only focus, Liquid Glass Dock and stacked app screenshots. Upgrade with the matching Standard/Guard package. See the [release notes](docs/releases/v2.0.27.md).'
    p.write_text(NL.join(lines).rstrip()+NL*2+section+NL*2+history,encoding='utf-8')
capture = Image.open(ROOT/'website/public/img/release-home.webp').convert('RGB')
FONT = Path('C:/Windows/Fonts')
def font(size, bold=False):
    return ImageFont.truetype(str(FONT/('segoeuib.ttf' if bold else 'segoeui.ttf')),size)
def label(d,xy,text,size,color='#F7F8FA',bold=False):
    d.text(xy,text,font=font(size,bold),fill=color)
def screen(im,x,y,w,h):
    pic=capture.resize((w,h),Image.Resampling.LANCZOS)
    mask=Image.new('L',(w,h),0)
    ImageDraw.Draw(mask).rounded_rectangle((0,0,w,h),radius=18,fill=255)
    shade=Image.new('RGBA',im.size)
    ImageDraw.Draw(shade).rounded_rectangle((x-5,y+16,x+w+5,y+h+20),radius=24,fill=(0,0,0,150))
    im.alpha_composite(shade.filter(ImageFilter.GaussianBlur(22)))
    im.paste(pic,(x,y),mask)
def poster(w,h,portrait):
    im=Image.new('RGBA',(w,h))
    d=ImageDraw.Draw(im)
    for y in range(h):
        t=y/h
        d.line((0,y,w,y),fill=(int(13+8*t),int(20+14*t),int(30+13*t),255))
    m=64 if portrait else 80
    label(d,(m,48 if portrait else 38),'T I G R I S   /   ANDROID TV',23,'#B9C7D6',True)
    bx,by=m,106 if portrait else 92
    d.rounded_rectangle((bx,by,bx+252,by+42),radius=21,fill='#A9EDD9')
    label(d,(bx+18,by+5),'2.0.27  /  OUT NOW',20,'#11251F',True)
    if portrait:
        label(d,(m,179),'Your TV.',86,bold=True)
        label(d,(m,270),'A clearer view.',86,bold=True)
        label(d,(m,390),'Brand banners. Stacked app previews.',30,'#C2CDDA')
        screen(im,m,482,w-2*m,(w-2*m)*9//16)
        d=ImageDraw.Draw(im)
        label(d,(m,1073),'BRAND BANNERS  /  SCALE-ONLY FOCUS',22,'#A9EDD9',True)
        label(d,(m,1120),'Liquid Glass Dock. Open-source Android TV.',26,'#D8E1EB')
        d.line((m,1192,w-m,1192),fill='#3B4757',width=1)
        label(d,(m,1220),'tigris-tv.pages.dev',28,bold=True)
        label(d,(m,1278),'Desktop capture from a preview build / Features vary by device',16,'#9DADBD')
    else:
        label(d,(m,166),'Your TV. A clearer view.',77,bold=True)
        label(d,(m,270),'Brand banners. Stacked app previews. Liquid Glass Dock.',29,'#C2CDDA')
        screen(im,m,340,920,517)
        d=ImageDraw.Draw(im)
        for i,(a,b) in enumerate([('01  BRAND BANNERS','Original TV artwork'),('02  CLEARER FOCUS','Scale up. No extra frames.'),('03  STACKED PREVIEWS','Switch between app screens')]):
            y=379+i*135
            label(d,(1060,y),a,23,'#A9EDD9',True)
            label(d,(1060,y+42),b,23,'#D8E1EB')
        label(d,(1060,809),'tigris-tv.pages.dev',26,bold=True)
        label(d,(m,870),'Desktop capture from a preview build / Features vary by device',15,'#9DADBD')
    return im.convert('RGB')
poster(1080,1350,True).save(OUT/'tigris-2.0.27-instagram.png',optimize=True)
poster(1600,900,False).save(OUT/'tigris-2.0.27-x.png',optimize=True)
print(OUT)
