package com.leslie.tvhome

import android.graphics.*
import android.view.View
import kotlin.math.*

/** GPU texture coordinates follow the screen every frame; no per-row bitmap rebakes while scrolling. */
internal class MovingGlass(private val root: View, image: Bitmap) {
    private val shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val imageWidth = image.width
    private val imageHeight = image.height
    private val origin = IntArray(2)
    private val location = IntArray(2)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = this@MovingGlass.shader }

    internal class Mesh(width: Float, height: Float, radius: Float) {
        val vertices: FloatArray
        val offsets: FloatArray
        val texture: FloatArray
        val indices: ShortArray
        val clip = Path().apply { addRoundRect(0f, 0f, width, height, radius, radius, Path.Direction.CW) }
        init {
            // Dense edges preserve the lens, with few triangles through the flat centre.
            fun axis(size: Float): FloatArray {
                val edge = minOf(radius * 1.4f, height * .26f, size / 2)
                return floatArrayOf(0f, edge * .2f, edge * .5f, edge, size / 2,
                    size - edge, size - edge * .5f, size - edge * .2f, size)
            }
            val xs = axis(width); val ys = axis(height)
            vertices = FloatArray(xs.size * ys.size * 2)
            offsets = FloatArray(vertices.size); texture = FloatArray(vertices.size)
            val zone = minOf(height * .26f, radius * 1.4f)
            for (y in ys.indices) for (x in xs.indices) {
                val i = (y * xs.size + x) * 2
                val px = xs[x]; val py = ys[y]
                vertices[i] = px; vertices[i + 1] = py
                val qx = abs(px - width / 2) - (width / 2 - radius)
                val qy = abs(py - height / 2) - (height / 2 - radius)
                val ox = max(qx, 0f); val oy = max(qy, 0f)
                val distance = sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - radius
                var nx = if (qx > 0 && qy > 0) ox / hypot(ox, oy).coerceAtLeast(.001f) else if (qx > qy) 1f else 0f
                var ny = if (qx > 0 && qy > 0) oy / hypot(ox, oy).coerceAtLeast(.001f) else if (qx > qy) 0f else 1f
                if (px < width / 2) nx = -nx
                if (py < height / 2) ny = -ny
                val t = (1 + distance / zone.coerceAtLeast(.001f)).coerceIn(0f, 1f)
                offsets[i] = px + nx * t * t * zone * .9f
                offsets[i + 1] = py + ny * t * t * zone * .9f
            }
            indices = ShortArray((xs.size - 1) * (ys.size - 1) * 6)
            var i = 0
            for (y in 0 until ys.size - 1) for (x in 0 until xs.size - 1) {
                val a = y * xs.size + x; val b = a + 1; val c = a + xs.size; val d = c + 1
                for (v in intArrayOf(a, b, c, b, d, c)) indices[i++] = v.toShort()
            }
        }
    }

    fun draw(canvas: Canvas, owner: View, mesh: Mesh, alpha: Int) {
        root.getLocationInWindow(origin)
        owner.getLocationInWindow(location)
        val scale = max(root.width.toFloat() / imageWidth, root.height.toFloat() / imageHeight)
        val cropX = (root.width - imageWidth * scale) / 2
        val cropY = (root.height - imageHeight * scale) / 2
        for (i in mesh.offsets.indices step 2) {
            mesh.texture[i] = (location[0] - origin[0] + mesh.offsets[i] * owner.scaleX - cropX) / scale
            mesh.texture[i + 1] = (location[1] - origin[1] + mesh.offsets[i + 1] * owner.scaleY - cropY) / scale
        }
        paint.alpha = alpha
        val saved = canvas.save()
        canvas.clipPath(mesh.clip)
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, mesh.vertices.size, mesh.vertices, 0,
            mesh.texture, 0, null, 0, mesh.indices, 0, mesh.indices.size, paint)
        canvas.restoreToCount(saved)
    }
}
