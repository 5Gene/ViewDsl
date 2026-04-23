package osp.sparkj.viewdsl.bitmap.sticker.core

import android.graphics.Bitmap

/**
 * 统一回收流水线内部产生的中间 Bitmap。
 *
 * 通过 [protect] 白名单保护外部传入的 bitmap（origin / 各种 mask），
 * 避免某个 step 退化成 pass-through 时误把外部资源回收掉。
 *
 * [remove] 用于把最终结果从池里剔除，避免 `execute` 返回值在 finally 里被误回收。
 */
class BitmapRecycler(private val protect: Set<Bitmap> = emptySet()) {

    private val pool = HashSet<Bitmap>()

    fun add(bmp: Bitmap?) {
        if (bmp == null) return
        if (bmp.isRecycled) return
        if (bmp in protect) return
        pool.add(bmp)
    }

    /** 从池里摘除 [bmp]，使其不被 [recycleAll] 回收（用于保护最终返回值）。 */
    fun remove(bmp: Bitmap) {
        pool.remove(bmp)
    }

    fun recycleAll() {
        pool.forEach { if (!it.isRecycled) it.recycle() }
        pool.clear()
    }
}
