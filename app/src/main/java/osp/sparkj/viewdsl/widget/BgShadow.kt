package osp.sparkj.viewdsl.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.core.view.drawToBitmap
import osp.sparkj.viewdsl.R

class BgShadow @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // 使用多级 stops 模拟更自然的非线性过渡 (类似于 Material Scrim 效果)
        private val GRADIENT_COLORS = intArrayOf(
            Color.BLACK,
            Color.argb((255 * 0.92).toInt(), 0, 0, 0),
            Color.argb((255 * 0.74).toInt(), 0, 0, 0),
            Color.argb((255 * 0.52).toInt(), 0, 0, 0),
            Color.argb((255 * 0.32).toInt(), 0, 0, 0),
            Color.argb((255 * 0.15).toInt(), 0, 0, 0),
            Color.argb((255 * 0.05).toInt(), 0, 0, 0),
            Color.TRANSPARENT
        )
        private val GRADIENT_POSITIONS = floatArrayOf(
            0.0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 1.0f
        )
    }

    private var bgBitmap: Bitmap? = null
    private val srcRect = Rect()
    private val dstRect = Rect()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    private var blurRadius = 25f

    fun updateBgBitmap() {
        val bgView = rootView.findViewById<View>(R.id.blur_background) ?: return
        if (bgView.width > 0 && bgView.height > 0) {
            // 获取原始背景截图
            val rawBitmap = bgView.drawToBitmap()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+ 优先使用 RenderEffect，性能更好
                bgBitmap = rawBitmap
//                setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP))
            } else {
                // 低版本使用缩放法实现平滑的视觉模糊
                val scale = 0.2f
                val w = (rawBitmap.width * scale).toInt().coerceAtLeast(1)
                val h = (rawBitmap.height * scale).toInt().coerceAtLeast(1)
                bgBitmap = Bitmap.createScaledBitmap(rawBitmap, w, h, true)
            }
            invalidate()
        }
    }

    /**
     * 设置模糊半径并刷新
     */
    fun setBlurRadius(radius: Float) {
        this.blurRadius = radius.coerceAtLeast(0.1f)
        updateBgBitmap()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        post { updateBgBitmap() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBgBitmap()
        // 高度变化时预先创建渐变 Shader，避免在 onDraw 中分配对象
        if (h > 0) {
            maskPaint.shader = LinearGradient(
                0f, h / 2f, 0f, h.toFloat(),
                GRADIENT_COLORS,
                GRADIENT_POSITIONS,
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = bgBitmap ?: return
        if (width <= 0 || height <= 0) return

        // 核心逻辑：从 bgBitmap 中截取当前 View 所在的区域进行绘制
        val currentTop = top
        srcRect.set(0, currentTop, width, currentTop + height)
        dstRect.set(0, 0, width, height)

        // 使用离屏缓冲实现渐变透明遮罩
        val count = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // 1. 绘制背景图
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)

        // 2. 绘制自然过渡的渐变遮罩
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)

        canvas.restoreToCount(count)
    }

    override fun offsetTopAndBottom(offset: Int) {
//        val topOffset = offset + height / 2
//        if (topOffset != 0) {
//            super.offsetTopAndBottom(topOffset)
//            invalidate()
//        }
        if (offset != 0) {
            super.offsetTopAndBottom(offset)
            invalidate()
        }
    }
}
