package osp.sparkj.viewdsl.bitmap.sticker.stroke

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import osp.sparkj.viewdsl.bitmap.sticker.core.RunCtx

/**
 * 发光型描边：把 input 的 alpha 通道用 [BlurMaskFilter] 做半径 [width] 的高斯模糊后
 * 用 [color] 填充，得到柔和的描边层；再把原图叠在上面。
 *
 * - 使用 `Bitmap.extractAlpha()` 得到 ALPHA_8，它能与 `Paint` 的 `maskFilter` 正确协同；
 * - 模糊会向外扩展 ~[width] 像素，因此结果 Bitmap 与 input 同尺寸足够容纳（前提是 input 四周留有透明边）。
 */
class BlurStroke(
    val width: Int = 16,
    val color: Int = Color.BLUE
) : StrokeStep {

    override fun process(ctx: RunCtx, input: Bitmap): Bitmap {
        if (width <= 0) return input

        val out = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val alphaBmp = input.extractAlpha()
        try {
            val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = this@BlurStroke.color
                maskFilter = BlurMaskFilter(width.toFloat(), BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawBitmap(alphaBmp, 0f, 0f, blurPaint)
        } finally {
            alphaBmp.recycle()
        }

        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(input, 0f, 0f, fgPaint)
        return out
    }
}
