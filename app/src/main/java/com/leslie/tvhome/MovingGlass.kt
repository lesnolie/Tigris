package com.leslie.tvhome

import android.graphics.*
import android.view.View
import kotlin.math.*

/**
 * GPU-backed screen-space lens for a texture that can move every frame.
 *
 * The dense rim is displaced along its normal, and red/blue are sampled at
 * slightly different distances. The three channels are composited in one
 * offscreen layer, so the caller still gets a normal translucent drawable.
 */
internal class MovingGlass(
    private val root: View,
    image: Bitmap,
    private val mapping: Mapping? = null,
) {
    internal data class Mapping(
        /** Texture origin in root coordinates. */
        val left: Float,
        val top: Float,
        /** Texture pixels per screen pixel. */
        val scale: Float,
    )

    private val shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val imageWidth = image.width
    private val imageHeight = image.height
    private val ownerToRoot = Matrix()
    private val transformValues = FloatArray(9)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        this.shader = this@MovingGlass.shader
    }
    private val channelFilters = arrayOf(
        channelFilter(1f, 0f, 0f),
        channelFilter(0f, 1f, 0f),
        channelFilter(0f, 0f, 1f),
    )
    private val restorePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val channelBlend = PorterDuffXfermode(PorterDuff.Mode.ADD)

    internal class Mesh(width: Float, height: Float, radius: Float, extreme: Boolean = true) {
        val vertices: FloatArray
        val offsets: FloatArray
        val dispersion: FloatArray
        val textures: Array<FloatArray>
        val indices: ShortArray
        val clip = Path().apply { addRoundRect(0f, 0f, width, height, radius, radius, Path.Direction.CW) }
        val lastTransform = FloatArray(9) { Float.NaN }
        var lastImageWidth = -1
        var lastImageHeight = -1
        var lastRootWidth = -1
        var lastRootHeight = -1
        var lastMapping: Mapping? = null
        var lastChromatic = false

        init {
            // Dense edges preserve the lens, with few triangles through the flat centre.
            fun axis(size: Float): FloatArray {
                val edge = if (!extreme) minOf(radius * 1.4f, height * .26f, size / 2) else minOf(44f, width / 3.5f, height / 3.5f)
                    .coerceAtLeast(4f)
                    .coerceAtMost(size / 2)
                return if (!extreme) floatArrayOf(0f, edge * .2f, edge * .5f, edge, size / 2,
                    size - edge, size - edge * .5f, size - edge * .2f, size)
                else floatArrayOf(0f, edge * .08f, edge * .18f, edge * .32f, edge * .5f,
                    edge * .72f, edge, size / 2, size - edge, size - edge * .72f,
                    size - edge * .5f, size - edge * .32f, size - edge * .18f, size - edge * .08f, size)
            }
            val xs = axis(width)
            val ys = axis(height)
            vertices = FloatArray(xs.size * ys.size * 2)
            offsets = FloatArray(vertices.size)
            dispersion = FloatArray(vertices.size)
            textures = Array(3) { FloatArray(vertices.size) }
            val zone = if (!extreme) minOf(height * .26f, radius * 1.4f) else minOf(44f, width / 3.5f, height / 3.5f)
                .coerceAtLeast(4f)
            for (y in ys.indices) for (x in xs.indices) {
                val i = (y * xs.size + x) * 2
                val px = xs[x]
                val py = ys[y]
                vertices[i] = px
                vertices[i + 1] = py
                val qx = abs(px - width / 2) - (width / 2 - radius)
                val qy = abs(py - height / 2) - (height / 2 - radius)
                val ox = max(qx, 0f)
                val oy = max(qy, 0f)
                val distance = sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - radius
                var nx = if (qx > 0 && qy > 0) ox / hypot(ox, oy).coerceAtLeast(.001f)
                    else if (qx > qy) 1f else 0f
                var ny = if (qx > 0 && qy > 0) oy / hypot(ox, oy).coerceAtLeast(.001f)
                    else if (qx > qy) 0f else 1f
                if (px < width / 2) nx = -nx
                if (py < height / 2) ny = -ny
                val t = (1 + distance / zone).coerceIn(0f, 1f)
                val lens = if (extreme && minOf(width, height) < 32f) 0f
                    else if (extreme) zone * 1.1f * t.pow(2.6f) else t * t * zone * .9f
                val direction = if (extreme) -1f else 1f
                offsets[i] = px + direction * nx * lens
                offsets[i + 1] = py + direction * ny * lens
                // Red bends more than blue. Keep the flat centre truly flat.
                val fringe = if (lens > .75f) min(lens * .15f, 5f) else 0f
                dispersion[i] = direction * nx * fringe
                dispersion[i + 1] = direction * ny * fringe
            }
            indices = ShortArray((xs.size - 1) * (ys.size - 1) * 6)
            var i = 0
            for (y in 0 until ys.size - 1) for (x in 0 until xs.size - 1) {
                val a = y * xs.size + x
                val b = a + 1
                val c = a + xs.size
                val d = c + 1
                for (v in intArrayOf(a, b, c, b, d, c)) indices[i++] = v.toShort()
            }
        }
    }

    fun draw(
        canvas: Canvas,
        owner: View,
        mesh: Mesh,
        alpha: Int,
        colorFilter: ColorFilter? = null,
        chromatic: Boolean = true,
    ) {
        ownerToRoot.reset()
        var current = owner
        while (current !== root) {
            ownerToRoot.postConcat(current.matrix)
            val parent = current.parent as? View ?: break
            ownerToRoot.postTranslate((current.left - parent.scrollX).toFloat(), (current.top - parent.scrollY).toFloat())
            current = parent
        }
        val fit = mapping == null
        val fitScale = if (fit) max(root.width.toFloat() / imageWidth, root.height.toFloat() / imageHeight) else 1f
        val cropX = if (fit) (root.width - imageWidth * fitScale) / 2 else 0f
        val cropY = if (fit) (root.height - imageHeight * fitScale) / 2 else 0f
        val first = if (chromatic) 0 else 1
        val last = if (chromatic) 2 else 1
        ownerToRoot.getValues(transformValues)
        val changed = !transformValues.contentEquals(mesh.lastTransform) || mesh.lastImageWidth != imageWidth ||
            mesh.lastImageHeight != imageHeight || mesh.lastRootWidth != root.width || mesh.lastRootHeight != root.height ||
            mesh.lastMapping != mapping || mesh.lastChromatic != chromatic
        if (changed) {
        transformValues.copyInto(mesh.lastTransform)
        mesh.lastImageWidth = imageWidth
        mesh.lastImageHeight = imageHeight
        mesh.lastRootWidth = root.width
        mesh.lastRootHeight = root.height
        mesh.lastMapping = mapping
        mesh.lastChromatic = chromatic
        for (channel in first..last) {
            val shift = when (channel) { 0 -> 1f; 2 -> -1f; else -> 0f }
            val texture = mesh.textures[channel]
            for (i in mesh.offsets.indices step 2) {
                val localX = mesh.offsets[i] + shift * mesh.dispersion[i]
                val localY = mesh.offsets[i + 1] + shift * mesh.dispersion[i + 1]
                texture[i] = localX
                texture[i + 1] = localY
            }
            ownerToRoot.mapPoints(texture)
            for (i in mesh.offsets.indices step 2) {
                val screenX = texture[i]
                val screenY = texture[i + 1]
                if (mapping != null) {
                    texture[i] = (screenX - mapping.left) * mapping.scale
                    texture[i + 1] = (screenY - mapping.top) * mapping.scale
                } else {
                    texture[i] = (screenX - cropX) / fitScale
                    texture[i + 1] = (screenY - cropY) / fitScale
                }
            }
        }
        }

        if (!chromatic) {
            // The ordinary path stays single-pass and avoids an offscreen layer.
            paint.alpha = alpha
            paint.colorFilter = colorFilter
            val saved = canvas.save()
            canvas.clipPath(mesh.clip)
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, mesh.vertices.size, mesh.vertices, 0,
                mesh.textures[1], 0, null, 0, mesh.indices, 0, mesh.indices.size, paint)
            canvas.restoreToCount(saved)
            paint.colorFilter = null
            return
        }

        // Paint the isolated channels into a layer with additive blending. Restoring
        // the layer then applies the glass tone and the surface's own opacity once.
        val restore = restorePaint.apply {
            this.alpha = alpha
            this.colorFilter = colorFilter
        }
        val save = canvas.saveLayer(0f, 0f, owner.width.toFloat(), owner.height.toFloat(), restore)
        canvas.clipPath(mesh.clip)
        paint.xfermode = channelBlend
        for (channel in 0..2) {
            paint.colorFilter = channelFilters[channel]
            paint.alpha = 255
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, mesh.vertices.size, mesh.vertices, 0,
                mesh.textures[channel], 0, null, 0, mesh.indices, 0, mesh.indices.size, paint)
        }
        paint.xfermode = null
        paint.colorFilter = null
        canvas.restoreToCount(save)
    }

    private companion object {
        fun channelFilter(r: Float, g: Float, b: Float): ColorMatrixColorFilter = ColorMatrixColorFilter(
            ColorMatrix(floatArrayOf(
                r, 0f, 0f, 0f, 0f,
                0f, g, 0f, 0f, 0f,
                0f, 0f, b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )),
        )
    }
}
