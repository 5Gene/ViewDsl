package osp.sparkj.viewdsl.bitmap.sticker

import android.graphics.Bitmap
import osp.sparkj.viewdsl.bitmap.sticker.core.BitmapRecycler
import osp.sparkj.viewdsl.bitmap.sticker.core.RunCtx
import osp.sparkj.viewdsl.bitmap.sticker.core.StickerStep

/**
 * 出框特效流水线构建器（纯单列表版）。
 *
 * ## 设计思路
 * 不再区分"人物路 / 背景路"，维护一条 [steps] 列表，按添加顺序执行。
 * 合成也是一个普通 step（[ComposeStep]），调用方自己决定在哪个位置插入、用什么背景。
 * 这样流水线本身没有任何硬编码假设，自由度最高。
 *
 * ## 典型用法
 * ```kotlin
 * StickerPipeline()
 *     .addStep(MlKitSeg())                             // 1. 抠图
 *     .addStep(AlphaStroke(16, Color.WHITE))           // 2. 描边（可选）
 *     .addStep(CropStep(KeepInside, personMask))       // 3. 人物裁切
 *     .addStep(ComposeStep(CropStep(KeepInside, bgMask))) // 4. 合成（含背景处理）
 *     .execute(origin)
 * ```
 *
 * ## 内存安全
 * - `origin` 被加入 [BitmapRecycler] 白名单，绝不回收外部资源；
 * - `fold` 中 `next !== cur` 判定，pass-through step 不会重复入池；
 * - 最终返回值在 `finally` 之前从池中摘除（[BitmapRecycler.remove]），不会被误回收。
 *
 * ## 线程模型
 * `execute` 同步阻塞（MLKit / MediaPipe 内部已同步化），必须在后台线程调用。
 */
class StickerPipeline {

    private val steps = mutableListOf<StickerStep>()

    fun addStep(step: StickerStep) = apply { steps.add(step) }

    /**
     * @param origin 原始图片。生命周期由调用方管理，流水线绝不回收它。
     * @return 最终 Bitmap（调用方负责回收）。
     */
    fun execute(origin: Bitmap): Bitmap {
        val recycler = BitmapRecycler(protect = setOf(origin))
        val ctx = RunCtx(origin, recycler)
        try {
            val result = steps.fold(origin) { cur, step ->
                val next = step.process(ctx, cur)
                if (next !== cur) recycler.add(next)
                next
            }
            // 最终结果由调用方持有，从池里摘除避免被 recycleAll 误回收
            recycler.remove(result)
            return result
        } finally {
            recycler.recycleAll()
        }
    }
}
