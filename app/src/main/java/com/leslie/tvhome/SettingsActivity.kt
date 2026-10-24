package com.leslie.tvhome

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.leslie.tvhome.tv.TvControls

/**
 * tvOS-style settings: the title centred on top, a large icon on the left with a
 * description of the focused row under it, and grouped rows on the right.
 * Sub-pages (keep list, hidden apps) reuse the same list.
 */
class SettingsActivity : Activity() {
    companion object {
        const val SONY_DREAM = "com.sony.dtv.braviashow/com.sony.dtv.braviashow.BraviaDayDreamService"
        const val AVATAR_FILE = "profile_avatar.png"
        fun avatarFile(ctx: android.content.Context) = java.io.File(ctx.filesDir, AVATAR_FILE).takeIf { it.isFile }
        const val ICON_FILE = "settings_icon.png"
        const val BACKDROP_FILE = "settings_bg.jpg"

        /**
         * A picture of the user's own behind the whole settings page (Apple TV's, a TV on a blue haze,
         * say), with the TV where the big icon would be; uploaded from the phone page or pushed over adb.
         */
        fun backdropFile(ctx: android.content.Context): java.io.File? =
            listOfNotNull(java.io.File(ctx.filesDir, BACKDROP_FILE), ctx.getExternalFilesDir(null)?.let { java.io.File(it, BACKDROP_FILE) })
                .firstOrNull { it.isFile && it.length() > 0 }

        /**
         * A picture of the user's own for the settings icon (Apple TV's, say), uploaded from the phone
         * page or pushed over adb to Android/data/com.leslie.tvhome/files; null for the built-in gear.
         * Like the boot video, it is not part of the app.
         */
        fun customIcon(ctx: android.content.Context): java.io.File? = if (Prefs(ctx).ownIcon) iconFile(ctx) else null

        /** The uploaded picture whether or not it is in use ([Prefs.ownIcon]). */
        fun iconFile(ctx: android.content.Context): java.io.File? =
            listOfNotNull(java.io.File(ctx.filesDir, ICON_FILE), ctx.getExternalFilesDir(null)?.let { java.io.File(it, ICON_FILE) })
                .firstOrNull { it.isFile && it.length() > 0 }

        /** The icon decoded at no more than about [width] px wide: a 1280 px picture shown at 600 needn't take 6 MB. */
        fun decodeIcon(f: java.io.File, width: Int): android.graphics.Bitmap? {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.path, o)
            var sample = 1
            while (o.outWidth / (sample * 2) >= width) sample *= 2
            return android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        }

        private val IDLE_TEXT = Color.argb(235, 255, 255, 255)
        private val IDLE_VALUE = Color.argb(130, 255, 255, 255)
        private val FOCUS_VALUE = Color.argb(140, 0, 0, 0)
    }

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepo
    private lateinit var list: LinearLayout
    private lateinit var heading: TextView
    private lateinit var about: TextView
    private lateinit var icon: ImageView
    private lateinit var backdrop: ImageView
    private lateinit var scroll: ScrollView
    private var bundledBackdrop = false
    private var page = Page.MAIN
    /** The main-page row that opened the current sub-page, focused again on the way back. */
    private var opener = 0

    private enum class Page { MAIN, KEEP, HIDDEN, REMOVED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepo(this)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(28, 28, 30)) }
        backdrop = ImageView(this).apply {
            scaleType = ImageView.ScaleType.MATRIX
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> placeBackdrop(v as ImageView) }
        }
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        heading = text(28f, weight = 600).apply { gravity = Gravity.CENTER }
        root.addView(heading, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { topMargin = dp(40) })

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(48), dp(84), dp(32), 0)
        }
        icon = ImageView(this).apply {
            showIcon(this)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipRounded(dp(18).toFloat())
            clipToOutline = true
        }
        left.addView(icon, LinearLayout.LayoutParams(-1, dp(207)))
        about = text(15f, Color.argb(150, 255, 255, 255)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setLineSpacing(0f, 1.3f)
        }
        left.addView(about, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(26) })
        showBackdrop()
        body.addView(left, LinearLayout.LayoutParams(0, -1, 1f))

        // Not clipped, so a focused row can grow past the list's edges.
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            clipChildren = false
            setPadding(dp(12), dp(16), dp(12), dp(60))
            addView(list)
        }
        body.addView(scroll, LinearLayout.LayoutParams(0, -1, 1.05f).apply { marginEnd = dp(52) })
        root.addView(body, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(84) })
        setContentView(root)
        show(Page.MAIN)
    }

    private fun show(p: Page, focusIndex: Int = 0) {
        page = p
        list.removeAllViews()
        when (p) {
            Page.MAIN -> main()
            Page.KEEP -> appPicker("释放内存时保留", prefs.keep) { pkg, on ->
                prefs.keep = if (on) prefs.keep + pkg else prefs.keep - pkg
            }
            Page.HIDDEN -> appPicker("隐藏的 App", prefs.hidden, includeHidden = true) { pkg, on ->
                prefs.hidden = if (on) prefs.hidden + pkg else prefs.hidden - pkg
            }
            Page.REMOVED -> removedApps()
        }
        list.post {
            val target = list.getChildAt(focusIndex.coerceIn(0, maxOf(list.childCount - 1, 0)))
            (target?.takeIf { it.isFocusable } ?: (0 until list.childCount).map { list.getChildAt(it) }.firstOrNull { it.isFocusable })
                ?.requestFocus()
        }
    }

    /** The uploaded background, whose TV stands in for the big icon; or the plain dark page. RGB_565: half the memory. */
    private fun showBackdrop() {
        val file = if (prefs.settingsBg) backdropFile(this) else null
        bundledBackdrop = prefs.settingsBg && file == null
        val bmp = file?.let {
            android.graphics.BitmapFactory.decodeFile(it.path, android.graphics.BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            })
        } ?: if (bundledBackdrop) android.graphics.BitmapFactory.decodeResource(resources, R.drawable.default_settings_bg,
            android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }) else null
        backdrop.setImageBitmap(bmp)
        placeBackdrop(backdrop)
        icon.visibility = if (bmp != null) android.view.View.INVISIBLE else android.view.View.VISIBLE
        // Below the TV in the picture, which reaches a little lower than the icon would.
        (about.layoutParams as LinearLayout.LayoutParams).topMargin = dp(if (bmp != null) 62 else 26)
        about.requestLayout()
    }

    /**
     * Fills the screen, 8% larger than needed and against the right edge: a subject in the left
     * third of a 16:9 picture (where the icon would be) then sits over the description column.
     */
    private fun placeBackdrop(v: ImageView) {
        val d = v.drawable ?: return
        if (v.width == 0 || d.intrinsicWidth <= 0) return
        // The bundled artwork sits further left, clear of the focused right-hand buttons.
        val s = maxOf(v.width / d.intrinsicWidth.toFloat(), v.height / d.intrinsicHeight.toFloat()) * if (bundledBackdrop) 1.12f else 1.08f
        v.imageMatrix = android.graphics.Matrix().apply {
            setScale(s, s)
            postTranslate(v.width - d.intrinsicWidth * s, (v.height - d.intrinsicHeight * s) / 2)
        }
    }

    private fun showIcon(v: ImageView) {
        val own = customIcon(this)?.let { decodeIcon(it, dp(380)) }
        if (own != null) v.setImageBitmap(own) else v.setImageResource(R.drawable.banner_settings)
    }

    private fun main() {
        heading.text = "主屏幕设置"
        about.text = ""

        section("软件")
        row("软件更新与投屏", "检查 Tigris 和 MiracastReceiver 的新版本，安装投屏组件或开启自动更新。", { "打开" }) {
            startActivity(Intent(this, UpdateActivity::class.java))
        }
        section("外观")
        row("时间格式", "主屏幕和控制中心里的时间用 12 小时制（11:49 AM）还是 24 小时制（23:49）。",
            { if (prefs.clock24) "24 小时" else "12 小时" }) { prefs.clock24 = !prefs.clock24 }
        row("时钟位置", "右上角的小时钟，或者左上角带日期的大时钟（之前的样式），也可以不显示。", {
            when (prefs.clockStyle) { Prefs.CLOCK_LARGE -> "左上角大时钟"; Prefs.CLOCK_OFF -> "不显示"; else -> "右上角" }
        }) {
            val opts = listOf(Prefs.CLOCK_CORNER, Prefs.CLOCK_LARGE, Prefs.CLOCK_OFF)
            prefs.clockStyle = opts[(opts.indexOf(prefs.clockStyle) + 1) % opts.size]
        }
        if (backdropFile(this) != null) row("设置页背景", "设置页用上传的背景图，或者普通的深色背景。", {
            if (prefs.settingsBg) "图片" else "普通"
        }) {
            prefs.settingsBg = !prefs.settingsBg
            showBackdrop()
        }
        if (iconFile(this) != null) row("设置图标", "这个设置的图标：自己上传的图片，或者自带的齿轮。", {
            if (prefs.ownIcon) "上传的图片" else "齿轮"
        }) {
            prefs.ownIcon = !prefs.ownIcon
            showIcon(icon)
        }
        row("上传头像", "手机扫描二维码，在网页的「头像」中上传照片，返回桌面后显示。", { "手机上传" }) {
            startActivity(Intent(this, WallpaperActivity::class.java).putExtra("upload_avatar", true))
        }
        row("显示右上角头像", "右上角的头像，按它可以打开主屏幕设置。", { onOff(prefs.showAvatar) }) { prefs.showAvatar = !prefs.showAvatar }
        row("聚焦动画", "tvOS：选中的 App 放大、跟着方向倾斜，表面有一道光。简洁：只放大。" +
            "\n“自动”在配置较低的电视上用简洁。", {
            auto(prefs.focusStyle, prefs.focusTvos(this), "tvOS", "简洁")
        }) {
            prefs.focusStyle = next(prefs.focusStyle, Prefs.FOCUS_TVOS, Prefs.FOCUS_SIMPLE)
        }
        row("控制中心样式", "玻璃：透出背后画面的毛玻璃，选中的按钮会照亮旁边的按钮。面板：之前的深色面板，更省性能。" +
            "\n“自动”在配置较低的电视上用面板。", {
            auto(prefs.ccStyle, prefs.ccGlass(this), "玻璃", "面板")
        }) {
            prefs.ccStyle = next(prefs.ccStyle, Prefs.CC_GLASS, Prefs.CC_PANEL)
        }

        section("开机动画")
        val video = Intro.videoFile(this) != null
        row("开机动画", "开机后第一次显示主屏幕时播放，最后标志放大进入桌面。按任意键可以跳过。" +
            if (video) "" else "\n默认播放内置开机视频，也可上传自己的视频。", {
            when (prefs.intro) { Prefs.INTRO_BOOT -> "开机时"; Prefs.INTRO_WAKE -> "开机和唤醒时"; else -> "关" }
        }) {
            val opts = listOf(Prefs.INTRO_OFF, Prefs.INTRO_BOOT, Prefs.INTRO_WAKE)
            prefs.intro = opts[(opts.indexOf(prefs.intro) + 1) % opts.size]
            // This boot counts as played: turning it on doesn't start it on the next return home.
            prefs.introBoot = Intro.bootStamp(this)
        }
        row("动画内容", "上传的视频：比如 Apple TV 的开机动画，结尾的标志放大进入桌面。Tigris 字样：Tigris 字样淡入后放大。" +
            if (video) "" else "\n没有上传视频时，视频模式使用内置动画。", {
            if (prefs.introSource == Prefs.INTRO_WORDMARK) "Tigris 字样" else if (video) "上传的视频" else "内置视频"
        }) {
            prefs.introSource = if (prefs.introSource == Prefs.INTRO_WORDMARK) Prefs.INTRO_VIDEO else Prefs.INTRO_WORDMARK
        }
        row("唤醒后等待", "从待机唤醒时，先黑屏这么久再播放动画。不少电视（尤其是 OLED）在系统亮屏后过几秒画面才真正亮起；看不到动画开头就调长，黑屏太久就调短。", {
            if (prefs.wakeDelay == 0) "不等待" else "${prefs.wakeDelay / 1000.0} 秒".replace(".0 ", " ")
        }) {
            val opts = listOf(0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 10_000)
            prefs.wakeDelay = opts.firstOrNull { it > prefs.wakeDelay } ?: 0
        }
        row("开机动画声音", "播放动画视频里的声音。", { onOff(prefs.introSound) }) { prefs.introSound = !prefs.introSound }
        row("预览开机动画", "现在回到主屏幕播放一次。", { "" }, chevron = true) {
            startActivity(Intent(this, HomeActivity::class.java)
                .putExtra(Intro.EXTRA_PREVIEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        section("背景与相框")
        row("背景", "主屏幕的背景：主题、壁纸来源或者自己的照片。", {
            Backgrounds.THEMES[prefs.background]?.first ?: Wallpapers.label(Wallpapers.sourceFor(prefs.background)).let {
                if (prefs.background == Wallpapers.MINE) it else "$it（自动更换）"
            }
        }, chevron = true) { startActivity(Intent(this, WallpaperActivity::class.java)) }
        row("壁纸画质", "自动：仅在高内存的 4K 设备使用 UHD；1080p：兼容模式；4K：强制尝试高分辨率，旧电视可能变慢。Bing UHD 不存在时自动回退。", {
            when (prefs.wallQuality) {
                MediaQuality.UHD -> "优先 4K"
                MediaQuality.COMPAT -> "1080p"
                else -> "自动"
            }
        }) {
            prefs.wallQuality = when (prefs.wallQuality) {
                MediaQuality.AUTO -> MediaQuality.UHD
                MediaQuality.UHD -> MediaQuality.COMPAT
                else -> MediaQuality.AUTO
            }
            Thread {
                // Refresh the queue because Bing URLs and file names depend on the requested size.
                runCatching { Wallpapers(applicationContext, Wallpapers.sourceFor(prefs.background)).refresh(force = true) }
            }.start()
        }
        row("照片更换频率", "自动更换的背景多久换一张。", { minutesLabel(prefs.wallMinutes) }) {
            val opts = listOf(15, 30, 60, 180, 1440)
            prefs.wallMinutes = opts[(opts.indexOf(prefs.wallMinutes) + 1) % opts.size]
        }
        row("立即换一张", "现在从壁纸来源取一张新照片。照片要显示时才下载，旧的过几天自动清理。",
            { "已有 ${Wallpapers(this, Wallpapers.sourceFor(prefs.background)).files().size} 张" }) { v ->
            v.text = "正在下载…"
            Thread {
                val w = Wallpapers(applicationContext, Wallpapers.sourceFor(prefs.background))
                w.refresh()
                w.advance(prefs.wallMinutes, force = true)
                runOnUiThread { v.text = "已有 ${w.files().size} 张" }
            }.start()
        }
        row("电视空闲时显示相框", "电视空闲一段时间后全屏轮播照片或航拍视频，代替电视自带的屏保。",
            { onOff(prefs.frameScreensaver) }) { v ->
            val on = !prefs.frameScreensaver
            val current = Settings.Secure.getString(contentResolver, "screensaver_components").orEmpty()
            val mine = "$packageName/${PhotoDream::class.java.name}"
            // Whatever the TV had before goes back when the frame is turned off (Sony's own if unknown).
            if (on && !current.startsWith(packageName)) prefs.prevScreensaver = current
            val comp = if (on) mine else prefs.prevScreensaver.ifEmpty { SONY_DREAM }
            v.text = "正在设置…"
            Thread {
                val ok = putSecure("screensaver_components", comp) && putSecure("screensaver_enabled", "1")
                if (ok) prefs.frameScreensaver = on
                runOnUiThread { v.text = if (ok) onOff(prefs.frameScreensaver) else "需要先授权本机调试" }
            }.start()
        }
        row("屏保内容", "相框里放背景照片，还是 Apple TV 的航拍视频。",
            { if (prefs.saverMode == Prefs.SAVER_AERIAL) "Apple TV 航拍视频" else "照片" }) {
            prefs.saverMode = if (prefs.saverMode == Prefs.SAVER_AERIAL) Prefs.SAVER_PHOTOS else Prefs.SAVER_AERIAL
        }
        row("航拍画质", "自动：仅在高内存且具备 4K HEVC 硬解的 4K 电视尝试高清；4K：有硬解时优先尝试；兼容：使用可用的 2K 视频。播放失败会回退。", {
            when (prefs.aerialQuality) {
                MediaQuality.UHD -> "优先 4K"
                MediaQuality.COMPAT -> "兼容"
                else -> "自动"
            }
        }) {
            prefs.aerialQuality = when (prefs.aerialQuality) {
                MediaQuality.AUTO -> MediaQuality.UHD
                MediaQuality.UHD -> MediaQuality.COMPAT
                else -> MediaQuality.AUTO
            }
        }
        row("空闲多久显示相框", "多久没有操作后显示相框。", { minutesLabel(idleMinutes()) }) { v ->
            val opts = listOf(1, 3, 5, 10, 15, 30)
            val next = opts[(opts.indexOf(idleMinutes()) + 1) % opts.size]
            v.text = "正在设置…"
            Thread {
                val ok = LocalAdb.shell(applicationContext, "settings put system screen_off_timeout ${next * 60_000}") != null
                runOnUiThread { v.text = if (ok) minutesLabel(next) else "需要先授权本机调试" }
            }.start()
        }
        row("相框每张照片停留", "相框里每张照片显示多久。", { "${prefs.frameSeconds} 秒" }) {
            val opts = listOf(15, 30, 60, 120)
            prefs.frameSeconds = opts[(opts.indexOf(prefs.frameSeconds) + 1) % opts.size]
        }

        section("内存与电源")
        row("待机时自动释放内存", "待机时仅清理缓存进程，不强制停止应用；无法确认前台时跳过。", { onOff(prefs.cleanOnStandby) }) {
            prefs.cleanOnStandby = !prefs.cleanOnStandby
        }
        row("定时释放内存", "定时清理缓存进程，不强制停止应用。新安装默认关闭。", {
            if (prefs.cleanIntervalHours == 0) "关闭" else "每 ${prefs.cleanIntervalHours} 小时"
        }) {
            val opts = listOf(0, 2, 4, 8)
            prefs.cleanIntervalHours = opts[(opts.indexOf(prefs.cleanIntervalHours) + 1) % opts.size]
            KeeperService.start(this, KeeperService.ACTION_RESCHEDULE)
        }
        row("立即释放内存", "现在关掉后台的 App。", { "可用 ${Cleaner.availMb(this)} MB" }) { v ->
            v.text = "正在释放…"
            Thread {
                val r = Cleaner.clean(applicationContext)
                runOnUiThread { v.text = r.description() }
            }.start()
        }
        row("释放内存时保留的 App", "释放内存时不会被关掉的 App，比如投屏和音乐。", { "${prefs.keep.size} 个" }, chevron = true) {
            show(Page.KEEP)
        }
        row("按遥控器电源键时", "待机：和平时一样。彻底关机：省电，但开机要等更久。", {
            if (prefs.shutdownOnPowerKey) "彻底关机" else "待机"
        }) {
            prefs.shutdownOnPowerKey = !prefs.shutdownOnPowerKey
            if (prefs.shutdownOnPowerKey && !prefs.adbAuthorized)
                Toast.makeText(this, "需要先授权本机调试才会生效", Toast.LENGTH_LONG).show()
        }

        section("App")
        row("最近打开的 App 自动置顶", "开启时，成功打开的应用或文件夹会移动到最前面；关闭后保持当前排列，只通过长按「编辑主屏幕」手动调整，重新启动应用也不会打乱顺序。",
            { onOff(prefs.recentFirst) }) { prefs.recentFirst = !prefs.recentFirst }
        row("隐藏的 App", "不在主屏幕上显示的 App。", { "${prefs.hidden.size} 个" }, chevron = true) { show(Page.HIDDEN) }
        row("已删除的预装 App", "从长按菜单里删除的预装 App，可以在这里恢复。", { "${prefs.removed.size} 个" }, chevron = true) {
            show(Page.REMOVED)
        }

        section("系统")
        row("本机调试授权", "允许主屏幕通过本机调试完成关机、释放内存等系统操作。授权时在弹窗中选择“始终允许”。",
            { if (prefs.adbAuthorized) "已授权" else "未授权，按确认键授权" }) { v ->
            v.text = "请在弹窗中选择“始终允许”"
            Thread {
                val ok = LocalAdb.shell(applicationContext, "echo ok", 60_000) != null
                runOnUiThread { v.text = if (ok) "已授权" else "未授权，按确认键重试" }
            }.start()
        }
        // Only where the TV keeps Home for its own launcher; on Sony or stock Android TV Tigris is the real home.
        if (Guard.needed(this)) row("桌面守护", "按主页键回到原厂桌面时，自动回到这里。",
            { if (!prefs.guard) "关" else if (HomeGuardService.isRunning) "开" else "开（未运行）" }) { v ->
            val on = !prefs.guard
            v.text = "正在设置…"
            Thread {
                val ok = (Guard.hasSecureSettings(this) || Guard.grantViaLocalAdb(applicationContext)) && Guard.setEnabled(applicationContext, on)
                runOnUiThread {
                    v.text = if (!ok) "需要先授权本机调试" else if (on) "开" else "关"
                    if (ok && on) Toast.makeText(this, "按主页键回到原厂桌面时会自动回到这里", Toast.LENGTH_LONG).show()
                }
            }.start()
        }
        if (Guard.isXiaomi() || prefs.keepAdb) row("保持 ADB 调试开启", "防止系统自动关掉 ADB 调试。", { onOff(prefs.keepAdb) }) { v ->
            val on = !prefs.keepAdb
            v.text = "正在设置…"
            Thread {
                val ok = !on || Guard.hasSecureSettings(this) || Guard.grantViaLocalAdb(applicationContext)
                if (ok) { prefs.keepAdb = on; Guard.ensureAdb(applicationContext) }
                runOnUiThread { v.text = if (ok) onOff(prefs.keepAdb) else "需要先授权本机调试" }
            }.start()
        }
        row("系统设置", "打开电视自带的设置。", { "" }, chevron = true) { runCatching { startActivity(TvControls(this).settingsIntent()) } }
        row("关于", "Tigris 主屏幕，仿 tvOS 的 Android TV 桌面。",
            { "版本 " + packageManager.getPackageInfo(packageName, 0).versionName }) {}
    }

    private fun appPicker(title: String, selected: Set<String>, includeHidden: Boolean = false, toggle: (String, Boolean) -> Unit) {
        heading.text = title
        about.text = if (page == Page.KEEP) "选中的 App 在释放内存时不会被关掉。" else "选中的 App 不会出现在主屏幕上。"
        val apps = if (includeHidden) repo.allApps().sortedBy { it.label } else repo.allApps().filter { !repo.isSystem(it.pkg) }.sortedBy { it.label }
        val sel = selected.toMutableSet()
        for (a in apps) {
            row(a.label, null, { if (a.pkg in sel) "✓" else "" }) { v ->
                val on = a.pkg !in sel
                if (on) sel += a.pkg else sel -= a.pkg
                toggle(a.pkg, on)
                v.text = if (on) "✓" else ""
            }
        }
    }

    /** Preinstalled apps deleted from the long-press menu; selecting one reinstalls it from the system partition. */
    private fun removedApps() {
        heading.text = "已删除的预装 App"
        about.text = "选择一个 App，从系统分区重新安装它。"
        if (prefs.removed.isEmpty()) row("没有已删除的 App", null, { "" }) {}
        for (pkg in prefs.removed.sorted()) {
            val label = try {
                packageManager.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES).loadLabel(packageManager).toString()
            } catch (e: Exception) { pkg }
            row(label, null, { "恢复" }) { v ->
                v.text = "正在恢复…"
                Thread {
                    val ok = LocalAdb.shell(applicationContext, "cmd package install-existing $pkg")?.contains("installed") == true
                    if (ok) prefs.removed = prefs.removed - pkg
                    runOnUiThread { v.text = if (ok) "已恢复" else "恢复失败" }
                }.start()
            }
        }
    }

    /** A small grey group title above a run of rows. */
    private fun section(title: String) {
        val first = list.childCount == 0
        list.addView(text(14f, Color.argb(120, 255, 255, 255), weight = 500).apply { text = title },
            LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(18); topMargin = if (first) 0 else dp(22); bottomMargin = dp(8)
            })
    }

    /**
     * One settings row; [value] is re-read after each click, [desc] shows under the icon while
     * the row is focused, and [chevron] marks a row that opens another screen.
     */
    private fun row(label: String, desc: String?, value: () -> String, chevron: Boolean = false, onClick: (TextView) -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(dp(20), 0, dp(if (chevron) 14 else 20), 0)
        }
        val l = text(17f, IDLE_TEXT, weight = 500).apply {
            text = label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val v = text(16f, IDLE_VALUE).apply { text = value() }
        r.addView(l, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(v, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
        val arrow = if (chevron) text(24f, IDLE_VALUE, weight = 300).apply { text = "›" } else null
        arrow?.let { r.addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10); bottomMargin = dp(2) }) }
        val idle = rounded(Color.argb(20, 255, 255, 255), dp(14).toFloat())
        val focus = rounded(Color.WHITE, dp(14).toFloat())
        r.background = idle
        r.setOnFocusChangeListener { _, f ->
            r.background = if (f) focus else idle
            l.setTextColor(if (f) Color.BLACK else IDLE_TEXT)
            v.setTextColor(if (f) FOCUS_VALUE else IDLE_VALUE)
            arrow?.setTextColor(if (f) FOCUS_VALUE else IDLE_VALUE)
            if (f && desc != null) about.text = desc
            r.tweenScale(if (f) 1.03f else 1f, 160)
        }
        r.setOnClickListener {
            if (page == Page.MAIN) opener = list.indexOfChild(r)
            onClick(v)
            if (page == Page.MAIN && !v.text.startsWith("正在") && !v.text.startsWith("请在")) v.text = value()
        }
        list.addView(r, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(6) })
    }

    private fun onOff(b: Boolean) = if (b) "开" else "关"

    /** "自动（…）" with what auto picks on this TV, or the forced choice. */
    private fun auto(pref: String, on: Boolean, yes: String, no: String) =
        if (pref == Prefs.AUTO) "自动（${if (on) yes else no}）" else if (on) yes else no

    /** The next of 自动 → [a] → [b]. */
    private fun next(pref: String, a: String, b: String) = when (pref) { Prefs.AUTO -> a; a -> b; else -> Prefs.AUTO }

    /** A secure setting, written directly once WRITE_SECURE_SETTINGS is granted, otherwise through local adb. Blocking. */
    private fun putSecure(key: String, value: String): Boolean =
        if (Guard.hasSecureSettings(this)) runCatching { Settings.Secure.putString(contentResolver, key, value) }.getOrDefault(false)
        else LocalAdb.shell(applicationContext, "settings put secure $key $value") != null

    private fun minutesLabel(m: Int) = when {
        m >= 1440 -> "每天"
        m >= 60 -> "${m / 60} 小时"
        else -> "$m 分钟"
    }

    /** On Android TV the screensaver starts after the "screen off" timeout. */
    private fun idleMinutes() = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 600_000) / 60_000

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && page != Page.MAIN) {
            show(Page.MAIN, opener)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (keyCode == KeyEvent.KEYCODE_BACK && page != Page.MAIN) true else super.onKeyDown(keyCode, event)
}
