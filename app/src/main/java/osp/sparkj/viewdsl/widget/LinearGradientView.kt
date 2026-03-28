package osp.sparkj.viewdsl.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView

/**
 * 实现高斯模糊和线性透明渐变的自定义 View
 * 1. 对 View 进行高斯模糊处理 (支持 API 31+)
 * 2. 支持设置透明度的线性渐变（下往上：透明 -> 不透明）
 * 3. 渐变区域和起始位置可设置
 * 4. 支持触摸滑动控制渐变位置
 */
class LinearGradientView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var blurRadius = 20f

    // 渐变区域参数（0.0 - 1.0 相对高度）
    // gradientStart: 下方透明开始的位置 (较大 y 值)
    // gradientOffset: 渐变区域的跨度 (gradientStart - gradientEnd)
    private var gradientStart = 0f
    private var gradientOffset = 0.2f

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    init {
        // 复用系统属性 android.R.attr.progress (0-100) 映射为 gradientStart
        // 复用系统属性 android.R.attr.secondaryProgress (0-100) 映射为 gradientOffset
//        val systemAttrs = intArrayOf(android.R.attr.progress, android.R.attr.secondaryProgress)
//        context.obtainStyledAttributes(attrs, systemAttrs).use {
//            if (it.hasValue(0)) {
//                gradientStart = it.getInt(0, 0) / 100f
//            }
//            if (it.hasValue(1)) {
//                gradientOffset = it.getInt(1, 10) / 100f
//            }
//        }
        attrs?.let {
            // 直接从 android 命名空间读取属性，这种方式最稳妥，不需要声明 styleable
            val namespace = "http://schemas.android.com/apk/res/android"

            // 读取 android:progress (默认 0)
            val progress = it.getAttributeIntValue(namespace, "progress", 0)
            gradientStart = progress / 100f

            // 读取 android:secondaryProgress (默认 10)
            val secondary = it.getAttributeIntValue(namespace, "secondaryProgress", 10)
            gradientOffset = secondary / 100f

            // 可以在这里也读取一下 enabled 状态（虽然系统会自动处理 isEnabled 属性）
            isEnabled = it.getAttributeBooleanValue(namespace, "enabled", true)
        }
        println("===== $gradientOffset $gradientStart")
        updateBlurEffect()
    }

    /**
     * 1. 设置高斯模糊半径
     */
    fun setBlurRadius(radius: Float) {
        this.blurRadius = radius.coerceAtLeast(0.1f)
        updateBlurEffect()
        invalidate()
    }

    /**
     * 2. 设置渐变区域
     * @param start 下方透明开始的相对位置 (0.0 - 1.0)
     * @param end 上方完成不透明的相对位置 (0.0 - 1.0)
     */
    fun setGradientRange(start: Float, end: Float) {
        this.gradientStart = start
        this.gradientOffset = start - end
        invalidate()
    }

    /**
     * 3. 提供设置开始渐变位置的方法
     * 这里保持渐变范围跨度 (offset) 不变
     */
    fun setGradientStartPos(pos: Float) {
        this.gradientStart = pos.coerceIn(0f, 1f)
        invalidate()
    }

    private fun updateBlurEffect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (blurRadius > 0.1f) {
                // API 31+ 使用 RenderEffect 实现高效模糊
                setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP))
            } else {
                setRenderEffect(null)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (gradientStart <= 0f) {
            // 禁用高斯模糊，直接绘制原始内容
            super.onDraw(canvas)
            return
        }
        // 创建离屏缓冲层，以正确应用 PorterDuffXfermode
        val count = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // 1. 绘制原始内容 (ImageView 的图片)
        super.onDraw(canvas)

        // 2. 绘制渐变 Mask
        val h = height.toFloat()
        val yStart = gradientStart * h
        // 动态计算 gradientEnd 的 Y 坐标
        val yEnd = (gradientStart + gradientOffset) * h

        // 线性渐变：从 yEnd (不透明) 到 yStart (透明)
        // 使用多级 stops 模拟更自然的过渡（类似 Scrim 效果，增加中间节点）
        val colors = intArrayOf(
            Color.BLACK,
            Color.argb((255 * 0.92).toInt(), 0, 0, 0),
            Color.argb((255 * 0.74).toInt(), 0, 0, 0),
            Color.argb((255 * 0.52).toInt(), 0, 0, 0),
            Color.argb((255 * 0.32).toInt(), 0, 0, 0),
            Color.argb((255 * 0.15).toInt(), 0, 0, 0),
            Color.argb((255 * 0.05).toInt(), 0, 0, 0),
            Color.TRANSPARENT
        )
        val positions = floatArrayOf(
            0.0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 1.0f
        )

        val gradient = LinearGradient(
            0f, yStart, 0f, yEnd,
            colors,
            positions,
            Shader.TileMode.CLAMP
        )

        maskPaint.shader = gradient
        // 在整个 View 范围内绘制，shader 会根据坐标应用渐变
        canvas.drawRect(0f, 0f, width.toFloat(), h, maskPaint)

        canvas.restoreToCount(count)
    }

    private var lastTouchY = 0f

    /**
     * 4. 支持触摸事件通过上下滑动控制渐变位置
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 如果 View 被禁用，直接返回 false，不处理任何触摸逻辑
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val currentY = event.y
//                val deltaPercent = (currentY - lastTouchY) / height.toFloat()
//
//                // 更新 gradientStart，滑动时保持 offset 不变，从而动态改变渐变位置
//                gradientStart += deltaPercent
//
//                // 限制在合理范围内，避免完全滑出不可见
//                gradientStart = gradientStart.coerceIn(-0.5f, 1.5f)
                gradientStart = currentY / height.toFloat()

//                lastTouchY = currentY
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
