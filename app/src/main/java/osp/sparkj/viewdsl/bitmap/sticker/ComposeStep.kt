package osp.sparkj.viewdsl.bitmap.sticker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import osp.sparkj.viewdsl.bitmap.sticker.core.RunCtx
import osp.sparkj.viewdsl.bitmap.sticker.core.StickerStep

/**
 * 合成 step：把任意数量的图层按顺序叠画，最后把 `input`（上一步产物）画在最顶层。
 *
 * 图层顺序：[layers][0] → [layers][1] → ... → `input`（顶层）
 *
 * 外部已处理好的 Bitmap 直接传入，不需要再经 StickerStep 处理。
 * 调用方负责 [layers] 中各 Bitmap 的生命周期，[ComposeStep] 不会回收它们。
 *
 * ### 典型用法
 * ```kotlin
 * val bgBitmap   = KeepInside.apply(origin, bgMask)
 * val decorBitmap = ...  // 装饰层
 *
 * StickerPipeline()
 *     .addStep(MlKitSeg())
 *     .addStep(AlphaStroke(16, Color.WHITE))
 *     .addStep(CropStep(KeepInside, personMask))
 *     .addStep(ComposeStep(bgBitmap, decorBitmap))  // 任意多张底图
 *     .execute(origin)
 * ```
 */
class ComposeStep(vararg layers: Bitmap) : StickerStep {

    private val layers: List<Bitmap> = layers.toList()

    override fun process(ctx: RunCtx, input: Bitmap): Bitmap {
        val w = ctx.origin.width
        val h = ctx.origin.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        layers.forEach { layer -> canvas.drawBitmap(layer, 0f, 0f, paint) }
        canvas.drawBitmap(input, 0f, 0f, paint)
        return out
    }
}
