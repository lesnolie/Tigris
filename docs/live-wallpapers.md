# Dynamic wallpapers and optional media / 动态壁纸与可选素材

Release target: 2.0.10. This feature does not add hardware certification.
本功能随 2.0.10 发布，不新增电视实机认证。

## Use / 使用

- Open **Wallpapers** on the TV, scan its QR code with a phone on the same network.
  Choose **Upload a wallpaper video · up to 4K**. The original video is retained
  without transcoding, up to 1 GiB per file. Prefer MP4 with H.264 or HEVC; actual
  resolution, frame rate, codec and HDR support depend on the TV decoder.
  电视打开「壁纸」，同一局域网的手机扫码，点「上传桌面视频 · 支持 4K」。
  原文件不转码，单个文件最大 1 GiB；建议 MP4，实际播放能力取决于电视。
- Select **My video · 4K** or **Apple aerials · 4K** on either screen. Playback is
  silent and loops on Home. Aerials try 4K HEVC when hardware decoding is reported,
  then lower-resolution alternatives. Network/decoder failures use a static
  graphite background. Leaving Home, standby, the intro and destruction release
  the player. Returning Home retries playback.
  手机和电视均可选择「我的视频 · 4K」或「Apple 航拍 · 4K」。仅在桌面静音循环，
  离开桌面、待机和开场动画期间释放播放器；失败回退静态背景，返回桌面重试。
- MotionBGs content is imported as a downloaded video: open a wallpaper detail
  page, download its **4K Wallpaper** MP4 to the phone, then upload it here.
  A website page URL is not a video source. Remote video URL import and a MotionBGs
  catalogue/API are not implemented. The existing custom-image-link box accepts
  images only.
  MotionBGs 用法：先在作品页下载 4K MP4，再从手机上传。无需配置源地址或密钥；
  尚不支持直接粘贴作品网页或视频网址，已有自定义图片链接入口仍只处理图片。

## Wallhaven

An independent built-in **Wallhaven** photo source uses the
[official public API](https://wallhaven.cc/help/api), without a bundled API key.
Phone controls offer keyword, category, monthly-top/random/latest sorting and
minimum 4K/1080p resolution. Defaults: landscape, general, monthly top, minimum
3840×2160, 16:9, public SFW images. It retains source attribution, refreshes the
list about daily and limits new Wallhaven searches to one per minute. Failed
searches preserve cached photos. Displayed bitmap resolution still follows the
existing TV wallpaper-quality/memory setting. Wallhaven supplies still images.

独立内置 Wallhaven 图片源，无需密钥。手机可选关键词、分类、月榜/随机/最新、
4K/1080p 最低分辨率，默认 4K 横屏风景。保留图片来源，每日更新列表，搜索最多每分钟
一次；失败保留缓存。图片显示精度继续遵循电视的壁纸画质和内存设置。此来源为静态图片。

## Optional defaults / 默认素材按需下载

The original Settings PNG and intro MP4 now live in `optional-assets/`, outside
Android resources. The APK does not contain them. Settings and the phone page
provide explicit download/remove buttons. Downloads use an immutable existing
repository revision and verify exact size and SHA-256 before atomic replacement.
No automatic first-run download. Custom uploads take priority; without either,
the Settings page is plain and the intro uses the Tigris wordmark. A failed
download leaves existing media untouched. Removing downloaded defaults does not
remove custom uploads.

原素材移至不参与打包的 `optional-assets/`。电视设置和手机页面均提供下载/删除按钮；
没有首次启动自动下载。文件按固定版本及 SHA-256 校验后保存。自定义上传优先；无素材时
使用普通设置页和 Tigris 字样，失败保留旧素材。删除默认素材不删除自定义上传。

## Validation / 验证

- Unit tests cover bounded/truncated upload transfer, retaining the previous file
  on a failed transfer, atomic replacement, and encoded Wallhaven filters.
- Both standard and guard debug/release variants build. Each flavor passed all 47
  unit tests (zero failures/errors); TV/phone locale catalogues are checked.
- Live Wallhaven request returned HTTP 200 with 24 results for the default filter.
  Both optional asset URLs returned HTTP 200 with expected sizes and hashes.
- Phone UI was exercised against a local mock backend: aerial selection, keyword
  and category filtering, default downloads/removal, and raw video upload/removal
  with matching content length. Chinese UI at 390 px has no horizontal overflow.
  This is UI verification, **not Android server/hardware verification**.
- **Not yet verified on TV:** 4K decoding/crop/HDR, smoothness, playback resource
  release, standby/wake, Wi-Fi upload interruption, actual mobile-to-TV uploads,
  on-device optional downloads and remote-control navigation of the larger grid.

本地正式配置构建：standard 2,163,621 字节，guard 2,163,593 字节（约 2.16 MB）。
旧 2.0.9 发布包约 3.90 MB，减少约 45%。此大小为本地正式配置构建测量，正式下载以发布页为准。
APK 检查确认不再包含 MP4。上传视频和下载素材
会增加安装后的数据占用，但不会增加安装包体积。
