package osp.sparkj.viewdsl.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.drawable.toBitmap

/**
 * 景深雾化遮罩 View，放在视频/相机预览的上层。
 *
 * ## 绘制顺序（saveLayer 内部）
 * ```
 * 1. 画全屏半透明白雾（匹配 MaskActivity #80FFFFFF）
 * 2. frameMask DST_OUT  → 静态形状蒙版（bg_mask.png）定义的区域把雾擦掉，露出清晰画面
 * 3. personMask DST_OUT → ML Kit 检测到的人物区域把雾擦掉，人物清晰突出
 * ```
 *
 * 效果等价于 MaskActivity 的三层叠加：
 * - frameMask 不透明处 → 清晰（等同 MaskActivity bg_mask 裁切）
 * - personMask 不透明处 → 清晰（等同 MaskActivity out_mask 裁切）
 * - 其余区域 → 半透明白雾压住（等同 MaskActivity #80FFFFFF 背景）
 *
 * ## 使用
 * ```kotlin
 * overlay.setFrameMask(getDrawable(R.drawable.bg_mask))   // 静态形状蒙版
 * overlay.updatePersonMask(mlKitMaskBitmap)               // ML Kit 动态人物蒙版
 * ```
 */
class DofOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 默认与 MaskActivity 一致：白色半透明（#80FFFFFF = alpha 128 ≈ 50%）
    var fogColor: Int = Color.WHITE
        set(value) {
            field = value; updateFogPaint(); invalidate()
        }

    var fogAlpha: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f); updateFogPaint(); invalidate()
        }

    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dstOutPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    // 静态形状蒙版（bg_mask.png）：DST_OUT 擦掉对应区域的雾，露出清晰背景
    private var frameMaskBitmap: Bitmap? = null
    private val frameMaskMatrix = Matrix()

    // ML Kit 动态人物蒙版：DST_OUT 擦掉人物区域的雾，人物清晰突出
    private var personMaskBitmap: Bitmap? = null
    private val personMaskMatrix = Matrix()

    private val srcRect = RectF()
    private val dstRect = RectF()

    init {
        updateFogPaint()
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // ── 静态形状蒙版 API ──────────────────────────────────────────────

    /** 设置静态形状蒙版（如 bg_mask.png），对应 MaskActivity 的 bg_mask 裁切层。 */
    fun setFrameMask(drawable: Drawable?) {
        frameMaskBitmap = drawable?.toBitmap(config = Bitmap.Config.ARGB_8888)
        updateMatrix(frameMaskBitmap, frameMaskMatrix)
        invalidate()
    }

    fun setFrameMask(bitmap: Bitmap?) {
        frameMaskBitmap = bitmap
        updateMatrix(frameMaskBitmap, frameMaskMatrix)
        invalidate()
    }

    // ── ML Kit 动态人物蒙版 API ───────────────────────────────────────

    /**
     * 更新 ML Kit 输出的人物蒙版，触发重绘。线程安全。
     * @param mask 白色=人物区域，黑色=背景
     */
    fun updatePersonMask(mask: Bitmap) {
        if (Thread.currentThread() == android.os.Looper.getMainLooper().thread) {
            applyPersonMask(mask)
        } else {
            post { applyPersonMask(mask) }
        }
    }

    /** 向后兼容旧名称 */
    fun updateMask(mask: Bitmap) = updatePersonMask(mask)

    private fun applyPersonMask(mask: Bitmap) {
        personMaskBitmap = mask
        updateMatrix(personMaskBitmap, personMaskMatrix)
        invalidate()
    }

    // ── 绘制 ─────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrix(frameMaskBitmap, frameMaskMatrix)
        updateMatrix(personMaskBitmap, personMaskMatrix)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        val saveCount = canvas.saveLayer(0f, 0f, w, h, null)

        // Step 1: 全屏白雾
        canvas.drawRect(0f, 0f, w, h, fogPaint)

        // Step 2: 静态形状蒙版擦雾（DST_OUT，bg_mask 不透明处清晰）
        frameMaskBitmap?.takeIf { !it.isRecycled }?.let {
            canvas.drawBitmap(it, frameMaskMatrix, dstOutPaint)
        }

        // Step 3: 人物蒙版擦雾（DST_OUT，ML Kit 检测到人物处清晰）
        personMaskBitmap?.takeIf { !it.isRecycled }?.let {
            canvas.drawBitmap(it, personMaskMatrix, dstOutPaint)
        }

        canvas.restoreToCount(saveCount)
    }

    // ── 工具 ─────────────────────────────────────────────────────────

    private fun updateFogPaint() {
        val a = (fogAlpha * 255).toInt().coerceIn(0, 255)
        fogPaint.color = Color.argb(a, Color.red(fogColor), Color.green(fogColor), Color.blue(fogColor))
    }

    /** 把 Bitmap 以 FIT_XY 拉伸到 View 尺寸，写入 [matrix]。 */
    private fun updateMatrix(bmp: Bitmap?, matrix: Matrix) {
        bmp ?: return
        val vw = width.toFloat();
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        srcRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        dstRect.set(0f, 0f, vw, vh)
        matrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL)
    }
}
