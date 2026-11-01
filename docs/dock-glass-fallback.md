# Dock glass on Android 12 and older / Android 12 及更早的 Dock 玻璃

Issue [#77](https://github.com/lesnolie/Tigris/issues/77). Sony BRAVIA X9500G (KD-65X9500G), Android 10, Tigris 2.0.17, standard APK, Apple TV aerial wallpaper. The photos are the left and right ends of the bottom dock over a night-city aerial. The left corner is chunky; the right corner fades out instead of turning a clean rounded corner.

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
| Aerial or local video | A `PixelCopy` of the `TextureView` (`VideoWallpaper`), at most 480 px wide, or 320 px when [Device.isLowEnd](../app/src/main/java/com/leslie/tvhome/Prefs.kt) is true. The bake is then scaled by `min(0.5, frameWidth / screenWidth)`, with frost 0.3 and opacity 0.5. One bake at a time (`VideoGlassFrames`). Capture gap 500 ms, or 1 s on a low-end device. |

`Device.isLowEnd` is true when the TV reports low RAM, has under about 2.5 GB, has fewer than 4 cores, **or SDK < 30**. Android 10 is always low-end for this switch, even on a 2019 BRAVIA. That flag also defaults the Control Center to the flat panel and focus animation to the simple style. The Control Center is a separate drawable (`ControlCenter.GlassSurface`): a quarter-size screen blur inside `drawRoundRect`, not the dock bitmap. This change does not touch it.

静态壁纸和 Top Shelf 按 Dock 的真实像素烘焙，圆角遮罩和 1 像素抗锯齿在同一分辨率里，所以角是顺的。高光从左上更亮，对边是更弱的反光。

航拍或本地视频先 `PixelCopy`（普通最多 480 宽，低配 320），再按 `min(0.5, 帧宽/屏宽)` 缩小烘焙，雾度 0.3、不透明度 0.5。低配包含 **SDK < 30**，所以 Android 10 一律走低配，哪怕是 2019 年的 BRAVIA。控制中心是另一条路径（四分之一屏模糊 + `drawRoundRect`），低配默认是扁平面板。这次没改控制中心。

### API 30 and older, before this change / 改之前的 API 30 及更早

The same functions. On the X9500G (API 29) a 1080p UI and a 320 px aerial snapshot produce `scale ≈ 320/1920 ≈ 1/6`. A dock on the order of 1700×200 px is baked at about 280×33 and stretched back up.

X9500G（API 29）1080p 界面配 320 宽的航拍快照，缩放约 1/6。大约 1700×200 的 Dock 只烤成 280×33，再被拉回去。

### What the photos are showing / 照片里的问题从哪来

1. **The mask is baked in snapshot pixels, then stretched.** The coverage ramp is `(0.5 - sdf)` — about one bake pixel. After a 6× `FIT_XY`, that ramp is several screen pixels. The left corner (where the specular is strongest and refraction samples with nearest-neighbour) looks stepped. The right corner, already the dimmer reflection, looks like the edge dissolves.
2. **`glassOpacity = 0.5` multiplies that ramp.** The whole slab is half transparent, so the stretched fringe is wider and the right side reads as "not closed".
3. **Refraction and the rim are in bake pixels too.** `rimW = 2.2` and the pull zone are huge once the bitmap is scaled up, so the highlight does not follow a screen-pixel rounded corner.
4. **The sample under the bake is filled black first.** If the snapshot does not cover the margin, the rim mixes in black. Nearest-neighbour `sample()` makes that blocky at the corner.
5. **`TextureView` is not in a software screenshot.** Playback stays on the hardware layer. Glass only sees `PixelCopy`. On some codecs the copy fails and the graphite still bake remains; on this set the copy is succeeding often enough that the dock shows a dark, smeared night scene rather than a flat placeholder. The failure mode is the stretched bake, not a missing renderer.
6. **Scrolling uses a second path.** `FrostGlass` clips with `canvas.clipPath` (not anti-aliased on a hardware canvas) and then strokes an inset round rect **with the original radius**. The stroke and the fill disagree at the corner. That path is the scrolled dock, not the resting dock in the photos. Pre-31 scroll drawing now uses an anti-aliased `drawRoundRect` and a matching stroke radius. API 31+ scroll drawing is unchanged.

遮罩、1 像素抗锯齿、折射和高光都写在那张小图里，再被放大。左角台阶、右角发虚，就是放大后的遮罩加上 0.5 不透明度。视频层本身截不到软件画布，只能靠 `PixelCopy`；这台电视多半是拷到了，但拷贝被缩小烘焙。滚动时的 `FrostGlass` 用了没有抗锯齿的 `clipPath`，描边半径又没随内缩减小。照片是静止 Dock，主因是第 1–3 点；滚动路径在 API 31 以下一并改了。

## Fallback added for API < 31 / API 31 以下的新回退

`DockGlassFallback` (API 30 and below only):

- Blur a **small** center-crop of the backdrop (longest edge 96–240 px, two or three box passes). This is the in-process replacement for RenderScript `ScriptIntrinsicBlur`, which is not a dependency. The full screen is never blurred.
- Composite the dock at **its own pixel size**. The rounded mask and the rim use the same lighting formula as `LiquidGlass`, in screen pixels, so the corner radius matches the tiles' glass and both ends keep an edge.
- Still wallpapers and shelf art rebuild when the picture changes, not on a timer.
- Video rebuilds at most every 1.5 s on a low-end device and every 800 ms otherwise (`shouldRefreshVideo`). `VideoWallpaper`'s capture gap uses that interval only when SDK < 31. API 31+ stays at 500 ms / 1 s, and the capture width is unchanged on every API.
- If no frame can be read, the dock is a frosted tint (cool gray, lighter at the top, same rim) at about 95% alpha. Interior alpha does not fade toward the corners; only the 1 px coverage ramp does. A successful snapshot replaces the tint with the blurred frame inside that same mask.
- `Glass.kt`, the API 31+ bake arguments (`frostStrength = 0.3`, `glassOpacity = 0.5`, scaled rect), Control Center glass, and low-end defaults are untouched.

只在 API 30 及以下启用。先把背景缩成一条短边最多 96–240 的图做盒式模糊（不模糊整屏，也不引入 RenderScript），再按 Dock 的真实尺寸合成圆角和边缘高光。静态壁纸只在换图时重算。视频低配最多 1.5 秒一次，其它 800 毫秒一次；API 31+ 的采样间隔和烘焙参数不动。拷不到帧时用均匀的磨砂色，角上只有 1 像素遮罩，不会一边实一边虚。

## Checks / 验证

Unit tests cover the API gate, the blur size, the refresh cap, symmetric corner alpha, rim light on both ends, and that a low-resolution mask stretched by nearest-neighbour has a wider partial-alpha band than the full-size mask.

`DockGlassFallbackTest.previewsShowTheOldVideoCornersAndTheFallback` writes software pictures (a night-city stand-in, not a TV photograph):

- `01-api31-static-reference.png` — reproduction of full-size `LiquidGlass.bake` (what API 31+ still does for a still wallpaper).
- `02-api29-video-before.png` — reproduction of the old video path: bake at `160/960` (the same ratio as 320 px on a 1920 px Android 10 UI), opacity 0.5, then stretch.
- `03-api29-video-after.png` — new video fallback with a blurred snapshot.
- `04-api29-video-tint-only.png` — new fallback when the snapshot is missing.
- `05-api29-static-after.png` — new still-wallpaper fallback.
- `06-corners-before-after.png` — 3× nearest zoom of the left and right corners, before on the top row and after on the bottom row.

No API 29 or 33 emulator was run here. Do not read these pictures as a BRAVIA retest.

单测锁住了 API 分界、模糊尺寸、刷新上限、左右圆角透明度和「小图遮罩拉大后半透明带更宽」。预览图是夜间城市的软件绘制，不是模拟器，也不是这台索尼的复测。
