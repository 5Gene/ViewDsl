package osp.sparkj.viewdsl.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toBitmap

/**
 * 把整个 View（background + 子 View）用蒙版裁成指定形状的 FrameLayout。
 *
 * ## 作用域：整张 View，不只是子 View
 * 蒙版作用在**整张 View 最终绘制的结果**上：
 * - 蒙版不透明处 → View 正常显示（background / 子 View 都在）
 * - 蒙版透明处   → 真正"镂空"：对父容器也是透明的
 *
 * 所以**不传子 View 也能看到蒙版形状**（只显示 background 被裁成蒙版形状）。
 *
 * ## 画面结构
 * ```
 *  ┌────────────────────────┐
 *  │ View 的全部绘制（被裁） │
 *  │   ┌────────┐           │
 *  │   │ 镂空   │  → 对父    │
 *  │   └────────┘    容器透明 │
 *  └────────────────────────┘
 *  ↓ 镂空外显示：android:background + 子 View
 * ```
 *
 * ## XML 用法
 * ```xml
 * <osp.sparkj.viewdsl.bitmap.MaskFrameLayout
 *     android:layout_width="200dp"
 *     android:layout_height="200dp"
 *     android:background="@drawable/pattern_bg"     ← 镂空外显示
 *     android:foreground="@drawable/mask_shape"     ← 拦截用作蒙版
 *     app:maskMode="keepOpaque">                    ← 默认：标准 alpha 蒙版
 *
 *     <!-- 子 View 可选，也会被一起裁 -->
 * </osp.sparkj.viewdsl.bitmap.MaskFrameLayout>
 * ```
 *
 * ## 蒙版模式
 *
 * ### [MaskMode.KEEP_OPAQUE]（默认，`keepOpaque`）—— 标准 alpha 蒙版
 * `View层 × mask.alpha / 255`（DST_IN）
 * - 蒙版**不透明** → 保留 View
 * - 蒙版**透明**   → 镂空
 *
 * 举例：蒙版"中间透明、边缘不透明" → **中间镂空**，边缘显示 View。
 *
 * ### [MaskMode.KEEP_TRANSPARENT]（`keepTransparent`）—— 反向
 * `View层 × (1 - mask.alpha / 255)`（DST_OUT）
 * - 蒙版**透明**   → 保留 View
 * - 蒙版**不透明** → 镂空
 *
 * 举例：蒙版"中间不透明、边缘透明" → **中间镂空**，边缘显示 View。
 *
 * > 记忆口诀：**模式名里"保留"的那种蒙版像素，对应的 View 就被保留**。
 *
 * ## 注意
 * - 蒙版自动 FILL 缩放到 View 尺寸；
 * - `saveLayer + Xfermode` 依赖硬件加速，init 时主动开启硬件层兜底；
 * - 代码切换蒙版：[setMaskDrawable] / [setMaskBitmap]；切换模式：[maskMode]。
 */
class MaskedFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class MaskMode {
        /** 保留蒙版不透明区域的子 View（DST_IN）；蒙版透明处镂空透出 background。 */
        KEEP_OPAQUE,

        /** 保留蒙版透明区域的子 View（DST_OUT）；蒙版不透明处镂空透出 background。 */
        KEEP_TRANSPARENT
    }

    var maskMode: MaskMode = MaskMode.KEEP_OPAQUE
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private var maskBitmap: Bitmap? = null
    private val maskMatrix = Matrix()

    // 复用 RectF，避免 onDraw / onSizeChanged 里分配
    private val srcRect = RectF()
    private val dstRect = RectF()
    private val dstInPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val dstOutPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    init {
        // ViewGroup 默认 willNotDraw=true，开启自绘让 dispatchDraw 的 saveLayer 生效
        setWillNotDraw(false)
        // saveLayer + Xfermode 在软件画布上结果不可靠，显式开启硬件层兜底
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * 拦截 `android:foreground` 与代码 `setForeground(...)`：用作蒙版，不调用 super，
     * 避免被 FrameLayout 作为普通前景再绘制一遍覆盖在最上层。
     */
    override fun setForeground(foreground: Drawable?) {
        setMaskDrawable(foreground)
    }

    /** 从 Drawable 提取蒙版 Bitmap（传 null 清除）。 */
    fun setMaskDrawable(drawable: Drawable?) {
        maskBitmap = drawable?.toBitmap(config = Bitmap.Config.ARGB_8888)
        updateMaskMatrix()
        invalidate()
    }

    /** 直接设置蒙版 Bitmap（生命周期由调用方管理）。 */
    fun setMaskBitmap(bitmap: Bitmap?) {
        maskBitmap = bitmap
        updateMaskMatrix()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMaskMatrix()
    }

    /**
     * 把蒙版 Bitmap 以 **FIT_XY**（等价 [Matrix.ScaleToFit.FILL]）的方式拉伸到 View 尺寸：
     * X、Y 独立缩放，不保持宽高比，蒙版总是恰好填满整张 View。
     *
     * 对应 ImageView.ScaleType.FIT_XY。
     */
    private fun updateMaskMatrix() {
        val mask = maskBitmap ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        srcRect.set(0f, 0f, mask.width.toFloat(), mask.height.toFloat())
        dstRect.set(0f, 0f, vw, vh)
        maskMatrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL)
    }

    /**
     * 绘制流程（作用域包住**整张 View**）：
     * 1. `saveLayer` 开一张离屏图层，覆盖整张 View；
     * 2. `super.draw(canvas)` 把本来会画到主 Canvas 的东西（background、onDraw、
     *    dispatchDraw、原生 foreground 等）全部画进离屏层；
     * 3. 用蒙版 + Porter-Duff 对离屏层做裁切：
     *    - [MaskMode.KEEP_OPAQUE]     → DST_IN：离屏层 × mask.alpha
     *    - [MaskMode.KEEP_TRANSPARENT] → DST_OUT：离屏层 × (1 - mask.alpha)
     * 4. `restoreToCount` 把离屏层合成回主 Canvas；被裁掉的像素是透明的，所以
     *    "镂空区域"对父容器是真透明，即使没有子 View 也能看到蒙版形状。
     *
     * ⚠ 关键点：拦截在 `draw`（而不是 `dispatchDraw`），是因为 background 是在
     * `View.draw()` 内部最先画出来的，只有在这里 saveLayer 才能把它也一并纳入裁切。
     */
    override fun draw(canvas: Canvas) {
        val mask = maskBitmap
        if (mask == null || mask.isRecycled) {
            super.draw(canvas)
            return
        }

        val saveCount = canvas.saveLayer(
            0f, 0f, width.toFloat(), height.toFloat(), null
        )
        super.draw(canvas)
        val paint = if (maskMode == MaskMode.KEEP_OPAQUE) dstInPaint else dstOutPaint
        canvas.drawBitmap(mask, maskMatrix, paint)
        canvas.restoreToCount(saveCount)
    }
}
