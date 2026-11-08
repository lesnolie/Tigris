package com.leslie.tvhome

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/** One app on the grid: a rounded 16:9 frost-grey tile with the name under it when focused. */
class TileCell(ctx: Context, val tileW: Int, val tileH: Int) : FrameLayout(ctx) {
    val card = FrameLayout(ctx)
    val image = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(TileSkin.FILL)
    }
    /** Real adaptive foreground, if the source APK exposes a separately usable layer. */
    val foreground = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.FIT_XY
        isClickable = false
    }
    /** GPU-composited movable light, drawn over the original app art. */
    private val specular = TvOSSpecular(ctx)
    val label: TextView = ctx.text(15f, bold = true).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        alpha = 0f
        ellipsize = android.text.TextUtils.TruncateAt.END
        setShadowLayer(dp(6).toFloat(), 0f, dp(1).toFloat(), Color.argb(140, 0, 0, 0))
    }
    private var jiggle: ObjectAnimator? = null
    /** Soft shadow under the card at rest, deeper while it is lifted. */
    private val shadow = ImageView(ctx).apply {
        setImageBitmap(shadowFor(ctx, tileW, tileH))
        alpha = TileSkin.SHADOW_REST_ALPHA
    }
    private val shadowPad = dp(SHADOW_PAD)
    /** A light-blue halo that appears only on the focused tile. */
    private val glow = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.FIT_XY
        setImageBitmap(glowFor(ctx, tileW, tileH))
        alpha = 0f
    }
    private val glowPad = dp(TileSkin.GLOW_PAD_DP)
    /** The hairline every tile has, and the brighter ring used for focus. */
    private val border = View(ctx).apply {
        background = ring(ctx, TileSkin.BORDER_DP, TileSkin.BORDER)
    }
    private val focusRing = View(ctx).apply {
        background = ring(ctx, TileSkin.FOCUS_RING_DP, TileSkin.FOCUS_RING)
        alpha = 0f
    }
    private val focusRenderer = TvOSFocusRenderer(card, foreground, specular, shadow,
        border, focusRing, glow, label, shadowPad)

    init {
        clipChildren = false
        clipToPadding = false
        isFocusable = true
        isClickable = true
        isLongClickable = true
        addView(shadow, LayoutParams(tileW + shadowPad * 2, tileH + shadowPad * 2))
        shadow.translationX = -shadowPad.toFloat()
        shadow.translationY = (-shadowPad + dp(4)).toFloat()
        addView(glow, LayoutParams(tileW + glowPad * 2, tileH + glowPad * 2))
        glow.translationX = -glowPad.toFloat()
        glow.translationY = -glowPad.toFloat()
        card.addView(image, LayoutParams(tileW, tileH))
        card.addView(foreground, LayoutParams(tileW, tileH))
        card.addView(specular, LayoutParams(tileW, tileH))
        card.clipRounded(dp(TileSkin.RADIUS_DP).toFloat())
        addView(card, LayoutParams(tileW, tileH))
        addView(border, LayoutParams(tileW, tileH))
        addView(focusRing, LayoutParams(tileW, tileH))
        // The name hangs below the tile without taking layout space, so rows keep an exact pitch.
        addView(label, LayoutParams(tileW + dp(30), LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        label.translationY = tileH + dp(17).toFloat()
    }

    /**
     * Hardware-accelerated focus animation; its current pose is the starting point of every
     * interrupted D-pad movement. A missing adaptive foreground uses whole-card 3D only.
     */
    fun setFocusedLook(f: Boolean, fromDx: Int, fromDy: Int, showLabel: Boolean = true, tvos: Boolean = true) {
        translationZ = if (f) 1f else 0f
        focusRenderer.focus(f, fromDx, fromDy, showLabel, tvos)
    }

    fun setArtwork(art: AppTileArt?) {
        image.setImageBitmap(art?.background)
        foreground.setImageBitmap(art?.foreground)
        foreground.visibility = if (art?.foreground != null) View.VISIBLE else View.GONE
    }

    fun setJiggle(on: Boolean) {
        jiggle?.cancel()
        jiggle = null
        card.rotation = 0f
        if (on) jiggle = ObjectAnimator.ofFloat(card, "rotation", -1.4f, 1.4f).apply {
            duration = 130
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    companion object {
        const val SHADOW_PAD = 30
        const val FOCUS_SCALE = 1.06f
        // 5/6-column Dock tiles have different sizes; one global bitmap silently stretches the
        // old shadow after switching layout. Cache by size.
        private val shadows = mutableMapOf<Pair<Int, Int>, android.graphics.Bitmap>()
        private val glows = mutableMapOf<Pair<Int, Int>, android.graphics.Bitmap>()

        @Synchronized
        fun shadowFor(ctx: Context, w: Int, h: Int): android.graphics.Bitmap =
            shadows.getOrPut(w to h) {
                LiquidGlass.shadow(w, h, ctx.dp(TileSkin.RADIUS_DP).toFloat(), ctx.dp(SHADOW_PAD))
            }

        @Synchronized
        fun glowFor(ctx: Context, w: Int, h: Int): android.graphics.Bitmap =
            glows.getOrPut(w to h) {
                val pad = ctx.dp(TileSkin.GLOW_PAD_DP)
                val b = android.graphics.Bitmap.createBitmap(
                    w + pad * 2, h + pad * 2, android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(b)
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = TileSkin.FOCUS_GLOW
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = ctx.dp(TileSkin.GLOW_DP).toFloat()
                    maskFilter = android.graphics.BlurMaskFilter(
                        ctx.dp(TileSkin.GLOW_BLUR_DP).toFloat(),
                        android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                val r = ctx.dp(TileSkin.RADIUS_DP).toFloat()
                c.drawRoundRect(android.graphics.RectF(
                    pad + 1f, pad + 1f, pad + w - 1f, pad + h - 1f), r, r, p)
                b
            }

        private fun ring(ctx: Context, widthDp: Float, color: Int) =
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = ctx.dp(TileSkin.RADIUS_DP).toFloat()
                setStroke(ctx.dp(widthDp), color)
                setColor(Color.TRANSPARENT)
            }
    }
}

class TileAdapter(
    private val repo: AppRepo,
    private val tileW: Int,
    private val tileH: Int,
    private val listener: Listener,
) : RecyclerView.Adapter<TileAdapter.VH>() {

    interface Listener {
        fun onTileFocused(pos: Int, cell: TileCell)
        fun onTileClicked(pos: Int, cell: TileCell)
        fun onTileLongPressed(pos: Int, cell: TileCell)
        /** Return true if the key was consumed (edge bumps, move mode). */
        fun onTileKey(pos: Int, cell: TileCell, keyCode: Int): Boolean
    }

    class VH(val cell: TileCell) : RecyclerView.ViewHolder(cell)

    var apps: MutableList<AppEntry> = mutableListOf()
        private set
    var movingPkg: String? = null
    private val loader = Executors.newFixedThreadPool(2)

    /** Tiles in the dock (the first row) hide their name, which would sit on the glass edge. */
    var dockCount = 0

    /** Tilt-in on focus ([Prefs.focusTvos]); otherwise only the zoom and shadow. */
    var tvosFocus = true

    /** Direction of the last D-pad move, used for the tilt-in. */
    var lastDx = 0
    var lastDy = 0

    /**
     * Updates only the tiles that changed, so a package event doesn't rebind
     * (and blank) the whole grid. [changed] is redrawn even if its entry is equal.
     */
    fun submit(list: List<AppEntry>, changed: String? = null) {
        val old = apps
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].pkg == list[n].pkg
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == list[n] && list[n].pkg != changed
        })
        apps = list.toMutableList()
        diff.dispatchUpdatesTo(this)
    }

    fun move(from: Int, to: Int) {
        val a = apps.removeAt(from)
        apps.add(to, a)
        notifyItemMoved(from, to)
    }

    override fun getItemCount() = apps.size
    override fun getItemId(position: Int) = apps[position].pkg.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val cell = TileCell(parent.context, tileW, tileH)
        cell.layoutParams = RecyclerView.LayoutParams(tileW, tileH)
        val vh = VH(cell)
        cell.setOnFocusChangeListener { _, f ->
            val p = vh.bindingAdapterPosition
            cell.setFocusedLook(f, lastDx, lastDy, showLabel = p >= dockCount, tvos = tvosFocus)
            if (f && p != RecyclerView.NO_POSITION) listener.onTileFocused(p, cell)
        }
        cell.setOnClickListener {
            vh.bindingAdapterPosition.takeIf { it >= 0 }?.let { listener.onTileClicked(it, cell) }
        }
        cell.setOnLongClickListener {
            vh.bindingAdapterPosition.takeIf { it >= 0 }?.let { listener.onTileLongPressed(it, cell) }
            true
        }
        cell.setOnKeyListener { _, code, e ->
            e.action == KeyEvent.ACTION_DOWN && vh.bindingAdapterPosition >= 0 &&
                listener.onTileKey(vh.bindingAdapterPosition, cell, code)
        }
        return vh
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val app = apps[position]
        val cell = h.cell
        cell.label.text = app.label
        val same = cell.tag == app.pkg
        cell.tag = app.pkg
        cell.setJiggle(app.pkg == movingPkg)
        val cached = repo.cachedArtwork(app.pkg)
        if (cached != null) {
            cell.setArtwork(cached)
        } else {
            // Rebinding the same app keeps the old image until the replacement is ready.
            if (!same) cell.setArtwork(null)
            loader.execute {
                val artwork = runCatching { repo.artwork(app) }.getOrNull()
                cell.post { if (cell.tag == app.pkg) cell.setArtwork(artwork) }
            }
        }
    }
}
