# Dock glass on Android 12 and older / Android 12 及更早的 Dock 玻璃

Issue [#77](https://github.com/lesnolie/Tigris/issues/77). Sony BRAVIA X9500G (KD-65X9500G), Android 10, Tigris 2.0.17, standard APK, a moving video wallpaper. The photos are the left and right ends of the bottom dock over a night-city video. The left corner is chunky; the right corner fades out instead of turning a clean rounded corner.

索尼 BRAVIA X9500G，Android 10，Tigris 2.0.17 标准版，背景是 Apple TV 航拍。照片里 Dock 左端圆角发碎，右端圆角发虚、像没封上。

This document is source investigation. It is not a claim that a physical TV was retested.

本文是源码结论，不是这台电视已经复测通过。

## How the dock is drawn today / 现在 Dock 怎么画

There is **no** `RenderEffect` branch, on API 31 or below. Android 12's blur API is not used for the dock. Every API level bakes [LiquidGlass](../app/src/main/java/com/leslie/tvhome/Glass.kt) on the CPU into a bitmap, and an `ImageView` with `FIT_XY` shows that bitmap. `Glass.kt` is unchanged by the fallback.

Dock **没有**按 API 31 分叉去用 `RenderEffect`。Android 12 的模糊接口没有用在 Dock 上。所有版本都是 CPU 上的 [LiquidGlass](../app/src/main/java/com/leslie/tvhome/Glass.kt) 烤成一张图，再用 `FIT_XY` 的 `ImageView` 贴上去。这次没有改 `Glass.kt`。

### API 31 and newer (unchanged) / API 31 及以上（保持原样）

| Background | What runs |
|---|---|
| Still wallpaper or shelf art | `LiquidGlass.bake` at the dock's full pixel size. Frost 1, opacity 1. Blur radius 7, two box passes, on a strip a little larger than the dock. The rounded mask is a 1 px signed-distance ramp in those same pixels, so the corner stays smooth. Rim light is stronger toward the top left (`facing = -(nx * 0.45 + ny * 0.9)`), with a weaker reflection on the opposite edge. |
| Moving video (streamed or a local file) | A `PixelCopy` of the `TextureView` (`VideoWallpaper`), at most 480 px wide, or 320 px when [Device.isLowEnd](../app/src/main/java/com/leslie/tvhome/Prefs.kt) is true. The bake is then scaled by `min(0.5, frameWidth / screenWidth)`, with frost 0.3 and opacity 0.5. One bake at a time (`VideoGlassFrames`). Capture gap 500 ms, or 1 s on a low-end device. |

`Device.isLowEnd` is true when the TV reports low RAM, has under about 2.5 GB, has fewer than 4 cores, **or SDK < 30**. Android 10 is always low-end for this switch, even on a 2019 BRAVIA. That flag also defaults the Control Center to the flat panel and focus animation to the simple style. The Control Center is a separate drawable (`ControlCenter.GlassSurface`): a quarter-size screen blur inside `drawRoundRect`, not the dock bitmap. This change does not touch it.

静态壁纸和 Top Shelf 按 Dock 的真实像素烘焙，圆角遮罩和 1 像素抗锯齿在同一分辨率里，所以角是顺的。高光从左上更亮，对边是更弱的反光。

航拍或本地视频先 `PixelCopy`（普通最多 480 宽，低配 320），再按 `min(0.5, 帧宽/屏宽)` 缩小烘焙，雾度 0.3、不透明度 0.5。低配包含 **SDK < 30**，所以 Android 10 一律走低配，哪怕是 2019 年的 BRAVIA。控制中心是另一条路径（四分之一屏模糊 + `drawRoundRect`），低配默认是扁平面板。这次没改控制中心。

### API 30 and older, before this change / 改之前的 API 30 及更早

The same functions. On the X9500G (API 29) a 1080p UI and a 320 px video snapshot produce `scale ≈ 320/1920 ≈ 1/6`. A dock on the order of 1700×200 px is baked at about 280×33 and stretched back up.

X9500G（API 29）1080p 界面配 320 宽的航拍快照，缩放约 1/6。大约 1700×200 的 Dock 只烤成 280×33，再被拉回去。

### What the photos are showing / 照片里的问题从哪来

1. **The mask is baked in snapshot pixels, then stretched.** The coverage ramp is `(0.5 - sdf)` — about one bake pixel. After a 6× `FIT_XY`, that ramp is several screen pixels. The left corner (where the specular is strongest and refraction samples with nearest-neighbour) looks stepped. The right corner, already the dimmer reflection, looks like the edge dissolves.
2. **`glassOpacity = 0.5` multiplies that ramp.** The whole slab is half transparent, so the stretched fringe is wider and the right side reads as "not closed".
3. **Refraction and the rim are in bake pixels too.** `rimW = 2.2` and the pull zone are huge once the bitmap is scaled up, so the highlight does not follow a screen-pixel rounded corner.
4. **The sample under the bake is filled black first.** If the snapshot does not cover the margin, the rim mixes in black. Nearest-neighbour `sample()` makes that blocky at the corner.
5. **`TextureView` is not in a software screenshot.** Playback stays on the hardware layer. Glass only sees `PixelCopy`. On some codecs the copy fails and the graphite still bake remains; on this set the copy is succeeding often enough that the dock shows a dark, smeared night scene rather than a flat placeholder. The failure mode is the stretched bake, not a missing renderer.
6. **Scrolling uses a second path.** `FrostGlass` clips with `canvas.clipPath` (not anti-aliased on a hardware canvas) and then strokes an inset round rect **with the original radius**. The stroke and the fill disagree at the corner. That path is the scrolled dock, not the resting dock in the photos. Pre-31 scroll drawing now uses an anti-aliased `drawRoundRect` and a matching stroke radius. API 31+ scroll drawing is unchanged.

遮罩、1 像素抗锯齿、折射和高光都写在那张小图里，再被放大。左角台阶、右角发虚，就是放大后的遮罩加上 0.5 不透明度。视频层本身截不到软件画布，只能靠 `PixelCopy`；这台电视多半是拷到了，但拷贝被缩小烘焙。滚动时的 `FrostGlass` 用了没有抗锯齿的 `clipPath`，描边半径又没随内缩减小。照片是静止 Dock，主因是第 1–3 点；滚动路径在 API 31 以下一并改了。

## What API < 31 does now / API 31 以下现在怎么画

`DockGlassFallback` runs only on API 30 and below. The TextureView keeps playing under the dock.

- **Video frost.** A PixelCopy about 320 px wide (180 px tall on 16:9) is box-blurred on the CPU (radius 12, three passes). That is strong enough that a frame a few seconds old does not draw a second sharp picture over the moving video. The dock is composited at **its own pixel size**: the rounded mask is a 1 px coverage ramp, and the rim is brighter than the body. The body's alpha is about **55%** (`VIDEO_FROST_ALPHA` 140), so the live video shows through. A new frost crossfades in over 700 ms.
- **How often.** About every 4 s on Home. A TV with the low-RAM flag, under 1.8 GB, or only one or two cores waits 10 s. Android 10 by itself is not treated as weak. API 31+ still copies every 500 ms (1 s on a low-end device) at the old width and still bakes with `LiquidGlass`.
- **Slow copy.** If a PixelCopy takes longer than 150 ms, that frame is discarded, further copies stop, and the dock becomes a light translucent veil plus the same full-size rim. No frost.
- **Before the first frame**, the dock is that veil, not an opaque slab.
- **Scrolling** on API 30 and below uses an anti-aliased `drawRoundRect` and a stroke whose radius shrinks with the inset. While video is showing, the scrolled fill uses the same 55% alpha, or the veil if copies were too slow. API 31+ scroll drawing is unchanged.
- Still wallpapers and shelf art are still a full-size mask over a small blur, rebuilt when the picture changes, and they stay opaque because nothing is moving underneath.
- `Glass.kt` and the API 31+ bake arguments are untouched.

视频继续在 Dock 下面的 TextureView 里播。API 30 及以下大约每 4 秒拷一张约 320×180 的图，在 CPU 上做比较重的盒式模糊，再按 Dock 的真实像素画圆角和亮边。底板大约 55% 不透明，所以能看见正在动的画面；新的一层用 700 毫秒淡入。内存很少、不到 1.8 GB、或只有一两个核时改成 10 秒一次。某次 PixelCopy 超过 150 毫秒就丢掉这一帧，不再继续拷，Dock 改成一层很淡的罩加上同一条亮边。静态壁纸仍然是不透明的全尺寸遮罩。API 31+ 的烘焙和采样不变。

## Checks / 验证

Unit tests cover the API gate, the blur size, the refresh cap, symmetric corner alpha, rim light on both ends, and that a low-resolution mask stretched by nearest-neighbour has a wider partial-alpha band than the full-size mask.

`DockGlassFallbackTest.previewsShowTheOldVideoCornersAndTheFallback` writes software pictures (a night-city stand-in, not a TV photograph):

- `01-api31-static-reference.png` — reproduction of full-size `LiquidGlass.bake` (what API 31+ still does for a still wallpaper).
- `02-api29-video-before.png` — reproduction of the old video path: bake at `160/960` (the same ratio as 320 px on a 1920 px Android 10 UI), opacity 0.5, then stretch.
- `03-api29-video-after.png` — software rendering of the new video frost: full-size corners, about 55% opacity, so the picture shows through. Not a photograph of a TV.
- `04-api29-video-veil.png` — software rendering of the light veil used when a snapshot is missing or too slow.
- `05-api29-static-after.png` — new still-wallpaper fallback.
- `06-corners-before-after.png` — 3× nearest zoom of the left and right corners, before on the top row and after on the bottom row.

No API 29 or 33 emulator was run here. Do not read these pictures as a BRAVIA retest.

单测锁住了 API 分界、模糊尺寸、刷新上限、左右圆角透明度和「小图遮罩拉大后半透明带更宽」。预览图是夜间城市的软件绘制，不是模拟器，也不是这台索尼的复测。
