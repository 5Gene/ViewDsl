package osp.sparkj.viewdsl.bitmap.sticker.core

import android.graphics.Bitmap

/**
 * 流水线中的单步处理。
 *
 * ### Step 之间如何拿到彼此的产物
 *
 * 1) **顺序链（主路径）**
 *    流水线内部用 `fold` 串联：上一个 step 返回的 Bitmap 就是下一个 step 的 `input`。
 *    这是 90% 场景下的用法，下一步无脑拿 `input` 就是上一步产物。
 *
 * 2) **跨步索引（可选）**
 *    当某个 step 要"跨过几步"去拿早期产物（例如 Stroke 之后还想拿最初的 Seg 结果），
 *    走 [RunCtx.put] / [RunCtx.get]，tag -> Bitmap 的 map：
 *
 *    ```kotlin
 *    class MySeg : SegStep {
 *        override fun process(ctx: RunCtx, input: Bitmap): Bitmap {
 *            val seg = doSeg(input)
 *            ctx.put("seg", seg)      // 记一下，后面可能还用得到
 *            return seg
 *        }
 *    }
 *    class SmartStroke : StrokeStep {
 *        override fun process(ctx: RunCtx, input: Bitmap): Bitmap {
 *            val segAlpha = ctx.get("seg") ?: input  // 知道 key 就能拿
 *            ...
 *        }
 *    }
 *    ```
 *    ctx 里保存的 Bitmap 生命周期由 [BitmapRecycler] 自动托管（已入池的不会被重复入池）。
 *
 * ### 返回值约定
 * - 返回新建 Bitmap（常规）：流水线纳入 [BitmapRecycler] 统一回收；
 * - 返回与 `input` 同一引用（pass-through）：流水线识别后不重复入池。
 */
interface StickerStep {
    fun process(ctx: RunCtx, input: Bitmap): Bitmap
}

/**
 * 单次 `execute()` 的运行上下文（短名 **RunCtx** = run context）。
 *
 * 承载：原图 [origin]、中间 Bitmap 回收 [recycler]、以及可选的 **tag → 中间产物**（跨步索引）。
 * 若只关心「上一步输出」，用 [StickerStep.process] 的 `input` 即可，不必用 map。
 *
 * @property origin 原图引用，全局只读；任何 step 都不应 recycle 它。
 * @property recycler 中间 Bitmap 回收器，外部资源已加入白名单。
 */
class RunCtx(
    val origin: Bitmap,
    val recycler: BitmapRecycler = BitmapRecycler()
) {

    private val artifacts = HashMap<String, Bitmap>()

    /** 以 [tag] 为 key 存入一个中间产物，供后续 step 跨步索引。 */
    fun put(tag: String, bmp: Bitmap) {
        artifacts[tag] = bmp
        recycler.add(bmp)
    }

    /** 按 [tag] 取出之前存入的中间产物；没有则返回 null。 */
    fun get(tag: String): Bitmap? = artifacts[tag]

    /** 产物数量（调试/断言用）。 */
    val artifactCount: Int get() = artifacts.size
}
