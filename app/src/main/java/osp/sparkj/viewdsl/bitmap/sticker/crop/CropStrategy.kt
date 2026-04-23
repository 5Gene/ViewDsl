package osp.sparkj.viewdsl.bitmap.sticker.crop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/**
 * 裁切算法（策略）。无状态，单例复用。
 *
 * 同一个 [CropStrategy] 会同时作用在 "原图 × bgMask" 与 "人物 × personMask" 两路上，
 * 调用方通过构造 `StickerPipeline` 时注入的 strategy 选择。
 */
interface CropStrategy {
    fun apply(src: Bitmap, mask: Bitmap): Bitmap
}

/**
 * 保留蒙版内部：SRC_IN —— 取 src 与 mask 不透明区域的交集。
 * 正向裁切，典型用法：人物出框 / 原图按前景框裁切。
 */
object KeepInside : CropStrategy {
    override fun apply(src: Bitmap, mask: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(mask, 0f, 0f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        paint.xfermode = null
        return out
    }
}

/**
 * 剔除蒙版内部：DST_OUT —— 从 src 中减去 mask 不透明区域。
 * 反向裁切，典型用法：背景镂空。
 */
object KeepOutside : CropStrategy {
    override fun apply(src: Bitmap, mask: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, 0f, 0f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        canvas.drawBitmap(mask, 0f, 0f, paint)
        paint.xfermode = null
        return out
    }
}
