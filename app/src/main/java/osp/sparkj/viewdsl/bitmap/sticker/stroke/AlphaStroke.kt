package osp.sparkj.viewdsl.bitmap.sticker.stroke

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import osp.sparkj.viewdsl.bitmap.sticker.core.RunCtx
import kotlin.math.cos
import kotlin.math.sin

/**
 * 环绕偏移描边：在以 (0,0) 为圆心、半径 = [width] 的圆周上按 [samples] 步绘制描边层，
 * 再把原图叠在最顶层。
 *
 * ### 颜色实现说明
 * 历史上曾尝试两种滤镜方案：
 * 1. `PorterDuffColorFilter(color, SRC_IN)`：在 `ColorFilter` 里 bitmap 是 Src、filter color 是 Dst，
 *    `SRC_IN = bitmap × color.alpha / 255`，当 color 有满 alpha 时结果 = 原图，颜色完全无效。
 * 2. `ColorMatrixColorFilter`：在 premultiplied alpha 管线中，半透明像素的平移项（R/G/B 常数列）
 *    会与 alpha 耦合，导致颜色偏差或仍显示为黑色。
 *
 * 最终采用**两步 stamp 法**（完全规避滤镜）：
 *   Step A — 先把 [color] 填满 stamp 画布（drawColor），
 *   Step B — 再用 `DST_IN` 把 input 的 alpha 形状"盖"上去，
 *            得到"颜色 = [color]、alpha = input.alpha"的纯色剪影（stamp）；
 *   绘制时直接拿 stamp 偏移叠画，不再依赖任何 ColorFilter。
 *
 * `DST_IN` 语义：result = Dst × Src.alpha / 255
 *   - Dst = 纯 [color] 填充（A=255, RGB=strokeColor）
 *   - Src = input（作为 alpha 蒙版）
 *   - input 不透明处 → result = strokeColor（满色）
 *   - input 透明处   → result = 透明
 *   - input 半透明处 → result = strokeColor 但 alpha 与 input 一致
 */
class AlphaStroke(
    val width: Int = 26,
    val color: Int = Color.WHITE,
    private val samples: Int = 24
) : StrokeStep {

    override fun process(ctx: RunCtx, input: Bitmap): Bitmap {
        if (width <= 0) return input

        // Step A+B：生成纯色剪影 stamp
        val stamp = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val stampCanvas = Canvas(stamp)
        stampCanvas.drawColor(color)                           // 填满描边色
        val dstIn = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        stampCanvas.drawBitmap(input, 0f, 0f, dstIn)          // 用 input alpha 做蒙版

        // 环绕偏移叠画 stamp
        val out = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val w = width.toFloat()
        for (i in 0 until samples) {
            val a = 2 * Math.PI * i / samples
            canvas.drawBitmap(stamp, (w * cos(a)).toFloat(), (w * sin(a)).toFloat(), paint)
        }
        // 把原图叠在最顶层（描边显现在轮廓外圈）
        canvas.drawBitmap(input, 0f, 0f, paint)

        stamp.recycle()
        return out
    }
}
