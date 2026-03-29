package osp.spark.view.atelier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

class BlurFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var blurRadius = 25
    private var downSample = 0.2f  // 降采样，越小越模糊但越快

    private var offscreenBitmap: Bitmap? = null
    private var offscreenCanvas: Canvas? = null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun dispatchDraw(canvas: Canvas) {
        if (width == 0 || height == 0) {
            super.dispatchDraw(canvas)
            return
        }

        val scaledWidth = (width * downSample).toInt()
        val scaledHeight = (height * downSample).toInt()

        if (offscreenBitmap == null ||
            offscreenBitmap?.width != scaledWidth ||
            offscreenBitmap?.height != scaledHeight
        ) {
            offscreenBitmap?.recycle()
            offscreenBitmap = Bitmap.createBitmap(
                scaledWidth,
                scaledHeight,
                Bitmap.Config.ARGB_8888
            )
            offscreenCanvas = Canvas(offscreenBitmap!!)
        }

        val bitmap = offscreenBitmap ?: return
        val offCanvas = offscreenCanvas ?: return

        // 清空
        bitmap.eraseColor(Color.TRANSPARENT)

        // 缩放后绘制子View
        offCanvas.save()
        offCanvas.scale(downSample, downSample)
        super.dispatchDraw(offCanvas)
        offCanvas.restore()

        // 模糊
        val blurred = blur(bitmap, blurRadius, false)

        // 绘制回原Canvas
        val src = Rect(0, 0, blurred.width, blurred.height)
        val dst = Rect(0, 0, width, height)
        canvas.drawBitmap(blurred, src, dst, paint)
        println("----------================ dispatchDraw")
    }

    fun blur(sentBitmap: Bitmap, radius: Int, canReuseInBitmap: Boolean): Bitmap {
        val bitmap = if (canReuseInBitmap) sentBitmap else sentBitmap.copy(sentBitmap.config!!, true)

        if (radius < 1) return bitmap

        val w = bitmap.width
        val h = bitmap.height

        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)

        var rsum: Int
        var gsum: Int
        var bsum: Int

        var x: Int
        var y: Int
        var i: Int
        var p: Int

        val vmin = IntArray(max(w, h))

        val dv = IntArray(256 * div)
        for (iIndex in dv.indices) {
            dv[iIndex] = iIndex / div
        }

        var yi = 0

        for (yIndex in 0 until h) {
            rsum = 0
            gsum = 0
            bsum = 0
            for (iIndex in -radius..radius) {
                p = pix[yi + min(wm, max(iIndex, 0))]
                rsum += p shr 16 and 0xff
                gsum += p shr 8 and 0xff
                bsum += p and 0xff
            }
            for (xIndex in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                if (yIndex == 0) {
                    vmin[xIndex] = min(xIndex + radius + 1, wm)
                }
                val p1 = pix[yIndex * w + vmin[xIndex]]
                val p2 = pix[yIndex * w + max(xIndex - radius, 0)]

                rsum += (p1 shr 16 and 0xff) - (p2 shr 16 and 0xff)
                gsum += (p1 shr 8 and 0xff) - (p2 shr 8 and 0xff)
                bsum += (p1 and 0xff) - (p2 and 0xff)

                yi++
            }
        }

        for (xIndex in 0 until w) {
            rsum = 0
            gsum = 0
            bsum = 0
            var yp = -radius * w
            for (iIndex in -radius..radius) {
                yi = max(0, yp) + xIndex
                rsum += r[yi]
                gsum += g[yi]
                bsum += b[yi]
                yp += w
            }
            yi = xIndex
            for (yIndex in 0 until h) {
                pix[yi] = (0xff shl 24) or
                        (dv[rsum] shl 16) or
                        (dv[gsum] shl 8) or
                        dv[bsum]

                if (xIndex == 0) {
                    vmin[yIndex] = min(yIndex + radius + 1, hm) * w
                }
                val p1 = xIndex + vmin[yIndex]
                val p2 = xIndex + max(yIndex - radius, 0) * w

                rsum += r[p1] - r[p2]
                gsum += g[p1] - g[p2]
                bsum += b[p1] - b[p2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }
}
