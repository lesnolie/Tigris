# Video wallpaper glass

## Cause

The moving wallpaper is drawn by a hardware `TextureView`. Android's software
Canvas capture cannot read that layer, so the Dock and Control Center glass used
the static fallback instead of the video scene. The liquid-glass renderer was
still active, but had no current video image to refract.

## Fix

- Sample the visible video into a bounded snapshot up to 640 px wide, every
  100 ms. Video playback remains at its original resolution; the app does not
  capture full-resolution frames.
- Use those snapshots to update the Dock glass and the scene captured behind the
  Control Center. Match the glass bake size to the sampled frame so it does not
  render unused pixels.
- Keep one glass bake in flight. Discard results from an old video or paused
  player so a late frame cannot replace the current scene.
- Use lighter frost and partial opacity for moving video, so the scene shows
  through the Dock. Static wallpaper keeps the original glass settings.

The Dock uses sampled frames rather than video-rate refraction. If a bake takes
longer than the sampling interval, the next one waits until it finishes.

## Android 11 and older

API 31+ still bakes those snapshots with `LiquidGlass` at the snapshot's own
scale. API 30 and below do not: the snapshot is only a small blur, and the dock
mask is drawn at the dock's full size so the corners are not the stretched
1 px ramp from the old video bake. The rules and the X9500G photos are in
[dock-glass-fallback.md](dock-glass-fallback.md). Capture width is unchanged.
The capture gap on API < 31 follows the fallback interval (800 ms, or 1.5 s on
a low-end device, which includes every Android 10 TV).

## Validation

Android CI passed for both release flavors, including the `VideoGlassFrames`
sequence tests. A signed `2.0.10-updater-test` build was installed on the Sony
BRAVIA for end-to-end checks. The moving wallpaper showed through the Dock glass
and the Dock followed scene changes without the previously visible lag. The
update page's right column also scrolled within its area without covering the
title or clipping its options. The screen recording showed no fatal Android
runtime errors.

The TV panel reports 3840×2160 at 60 Hz, while Android exposes a 1920×1080
logical display size. This check confirms the behavior on that TV; it does not
claim that the app rendered this test at a 3840×2160 logical resolution, or
certify smooth playback for every 4K video and TV decoder.
