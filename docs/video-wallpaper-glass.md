# Video wallpaper glass regression

## Cause

`HomeActivity.applyBackground()` starts the video but builds the Dock, expanded-grid
blur and scrolled Dock from the static graphite fallback. The video never updates
those textures. `ControlCenter.sampleBehind()` captures a software Canvas, which
cannot capture a TextureView's hardware video layer; it also sees that fallback.
The glass renderer itself is still enabled, but has no video detail to refract.

## Fix

- Capture the displayed TextureView at up to 480 px wide, at most twice per second.
  The full-resolution video keeps playing. No full-resolution video bitmap capture.
- Feed the same sampled scene to the existing Dock renderer, expanded-grid blur
  and scrolled FrostGlass. Bake the live Dock at half screen dimensions; static
  images retain their existing full-resolution bake and transition.
- Use a video-layer container that substitutes its latest snapshot only when
  drawing into a software Canvas. Normal on-screen drawing retains TextureView.
  This lets the Control Center capture the video and existing scene overlays,
  including Top Shelf. Refresh controls only while the Control Center is open.
- Keep one background bake in flight; discard its result after video/source changes
  or pause. Stop sampling with the player. Skip sampling while Top Shelf covers
  the video. On failure or pause, invalidate live work and rebuild fallback glass.

This is sampled glass, not video-rate refraction. The twice-per-second bound is
intentional; performance and smoothness must be assessed with actual videos.

## Validation

Both-flavor unit tests exercise a slow bake, source changes, pause/resume and late
callbacks. Both release builds and full Android lint are required.

Standalone Android graphics check on `192.168.50.106`, Sony BRAVIA 4K VH2 / Android 12:
loaded the actual debug classes in a temporary shell process, with **no application
replacement or preference changes**. Injected red and blue snapshot bitmaps into
the video layer, drew its software Canvas and rendered both Dock textures:

```
frame=ffff0000 capture=ffff0000 dock=ffff1010
frame=ff0000ff capture=ff0000ff dock=ff1919ff
PASS: software capture substitutes current snapshot; glass follows its colour;
      clearing the snapshot removes it from the captured scene.
```

This proves the Android software-rendering bridge and glass colour sampling.
It does not prove end-to-end TextureView capture, actual video frame rate, playback
smoothness or remote navigation. The installed `2.0.10-updater-test` does not include
this fix. No formal release or on-device app replacement was performed for this change.
