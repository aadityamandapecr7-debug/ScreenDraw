package com.screendraw.app

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF

enum class ShapeType { FREEHAND, LINE, RECTANGLE, TRIANGLE, CIRCLE, OCTAGON, CUBE }

/** One thing on the canvas: a stroke or a shape. Everything is movable/recolorable. */
class DrawObject(
    val type: ShapeType,
    var color: Int,
    var strokeWidth: Float,
    val isEraserStroke: Boolean = false
) {
    // Freehand strokes store raw points (used to rebuild the path & for hit-testing/move).
    val freehandPoints = mutableListOf<PointF>()

    // Shapes/cube just need a bounding box: (startX,startY) to (endX,endY).
    var startX = 0f
    var startY = 0f
    var endX = 0f
    var endY = 0f

    fun boundingBox(): RectF {
        return if (type == ShapeType.FREEHAND) {
            if (freehandPoints.isEmpty()) return RectF()
            var minX = freehandPoints[0].x; var maxX = minX
            var minY = freehandPoints[0].y; var maxY = minY
            for (p in freehandPoints) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
            RectF(minX - 20, minY - 20, maxX + 20, maxY + 20)
        } else {
            RectF(minOf(startX, endX) - 12, minOf(startY, endY) - 12,
                maxOf(startX, endX) + 12, maxOf(startY, endY) + 12)
        }
    }

    fun translate(dx: Float, dy: Float) {
        if (type == ShapeType.FREEHAND) {
            for (p in freehandPoints) { p.x += dx; p.y += dy }
        } else {
            startX += dx; startY += dy; endX += dx; endY += dy
        }
    }

    fun toPath(): Path {
        val path = Path()
        when (type) {
            ShapeType.FREEHAND -> {
                if (freehandPoints.isNotEmpty()) {
                    path.moveTo(freehandPoints[0].x, freehandPoints[0].y)
                    for (i in 1 until freehandPoints.size) {
                        path.lineTo(freehandPoints[i].x, freehandPoints[i].y)
                    }
                }
            }
            ShapeType.LINE -> {
                path.moveTo(startX, startY)
                path.lineTo(endX, endY)
            }
            ShapeType.RECTANGLE -> {
                path.addRect(minOf(startX, endX), minOf(startY, endY), maxOf(startX, endX), maxOf(startY, endY), Path.Direction.CW)
            }
            ShapeType.CIRCLE -> {
                val cx = (startX + endX) / 2f
                val cy = (startY + endY) / 2f
                val r = Math.hypot((endX - startX).toDouble(), (endY - startY).toDouble()).toFloat() / 2f
                path.addCircle(cx, cy, r, Path.Direction.CW)
            }
            ShapeType.TRIANGLE -> {
                val l = minOf(startX, endX); val r = maxOf(startX, endX)
                val t = minOf(startY, endY); val b = maxOf(startY, endY)
                path.moveTo((l + r) / 2f, t)
                path.lineTo(r, b)
                path.lineTo(l, b)
                path.close()
            }
            ShapeType.OCTAGON -> {
                val l = minOf(startX, endX); val r = maxOf(startX, endX)
                val t = minOf(startY, endY); val b = maxOf(startY, endY)
                val w = r - l; val h = b - t
                val cut = 0.3f
                path.moveTo(l + w * cut, t)
                path.lineTo(r - w * cut, t)
                path.lineTo(r, t + h * cut)
                path.lineTo(r, b - h * cut)
                path.lineTo(r - w * cut, b)
                path.lineTo(l + w * cut, b)
                path.lineTo(l, b - h * cut)
                path.lineTo(l, t + h * cut)
                path.close()
            }
            ShapeType.CUBE -> {
                // Simple wireframe cube: front face + back face (offset) + connecting edges.
                val l = minOf(startX, endX); val r = maxOf(startX, endX)
                val t = minOf(startY, endY); val b = maxOf(startY, endY)
                val depth = (r - l) * 0.35f
                // front face
                path.addRect(l, t + depth, r - depth, b, Path.Direction.CW)
                // back face
                path.moveTo(l + depth, t)
                path.lineTo(r, t)
                path.lineTo(r, b - depth)
                path.lineTo(r - depth, b - depth)
                path.moveTo(r, t)
                path.lineTo(r - depth, t + depth)
                // connecting edges (front-top-left to back-top-left, etc.)
                path.moveTo(l, t + depth); path.lineTo(l + depth, t)
                path.moveTo(r - depth, t + depth); path.lineTo(r, t)
                path.moveTo(l, b); path.lineTo(l + depth, b - depth)
                path.moveTo(r - depth, b); path.lineTo(r, b - depth)
            }
        }
        return path
    }

    fun toPaint(): Paint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = if (type == ShapeType.FREEHAND || type == ShapeType.LINE || type == ShapeType.CUBE)
            Paint.Style.STROKE else Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@DrawObject.strokeWidth
        color = this@DrawObject.color
        if (isEraserStroke) {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        }
    }

    /** Rough hit test used by Select tool and the object-eraser. */
    fun containsPoint(x: Float, y: Float, tolerance: Float = 40f): Boolean {
        val box = boundingBox()
        if (!box.contains(x, y)) return false
        if (type == ShapeType.FREEHAND) {
            for (p in freehandPoints) {
                if (Math.hypot((p.x - x).toDouble(), (p.y - y).toDouble()) <= tolerance) return true
            }
            return false
        }
        return true // for shapes, bounding-box hit is good enough and feels natural to the user
    }
}
