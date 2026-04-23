package osp.sparkj.viewdsl.bitmap.sticker.crop

import android.graphics.Bitmap
import osp.sparkj.viewdsl.bitmap.sticker.core.RunCtx
import osp.sparkj.viewdsl.bitmap.sticker.core.StickerStep

/**
 * 裁切 step：自包含 [strategy] 与 [mask]。
 *
 * 与 [osp.sparkj.viewdsl.bitmap.sticker.seg.SegStep] /
 * [osp.sparkj.viewdsl.bitmap.sticker.stroke.StrokeStep] 同级，不依赖任何流水线级配置。
 *
 * "同一策略同时作用两路"只是调用方的约定：给 person 路和 bg 路分别 addStep 时，
 * 传同一个 [strategy] 单例即可（比如都用 [KeepInside]）。
 */
class CropStep(
    private val strategy: CropStrategy,
    private val mask: Bitmap
) : StickerStep {
    override fun process(ctx: RunCtx, input: Bitmap): Bitmap =
        strategy.apply(input, mask)
}
