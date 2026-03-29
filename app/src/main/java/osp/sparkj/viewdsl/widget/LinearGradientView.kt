package osp.sparkj.viewdsl.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
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
    private var gradientOffset = 0.1f

    // 线性渐变：从 yEnd (不透明) 到 yStart (透明)
    // 使用多级 stops 模拟更自然的过渡（类似 Scrim 效果，增加中间节点）
    private val colors = intArrayOf(
        Color.BLACK,
        Color.argb((255 * 0.92).toInt(), 0, 0, 0),
        Color.argb((255 * 0.74).toInt(), 0, 0, 0),
        Color.argb((255 * 0.52).toInt(), 0, 0, 0),
        Color.argb((255 * 0.32).toInt(), 0, 0, 0),
        Color.argb((255 * 0.15).toInt(), 0, 0, 0),
        Color.argb((255 * 0.05).toInt(), 0, 0, 0),
        Color.TRANSPARENT
    )
    private val positions = floatArrayOf(
        0.0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 1.0f
    )
    private val gradientMatrix = Matrix()
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
            // 直接从 android 命名空间读取属性
            val namespace = "http://schemas.android.com/apk/res/android"

            // 读取 android:progress (默认 0)
            val progress = it.getAttributeIntValue(namespace, "progress", 0)
            gradientStart = progress / 100f

            // 读取 android:secondaryProgress (默认 10)
            val secondary = it.getAttributeIntValue(namespace, "secondaryProgress", 15)
            gradientOffset = secondary / 100f

            // 可以在这里也读取一下 enabled 状态（虽然系统会自动处理 isEnabled 属性）
            isEnabled = it.getAttributeBooleanValue(namespace, "enabled", true)
        }
        updateBlurEffect()

//        startTestAnimation()
    }

    /**
     * 测试代码：延迟 1s 后执行 gradientOffset 从 0 到 1 的动画，持续 10s
     */
    fun startTestAnimation() {
        postDelayed({
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 20000 // 10秒
                addUpdateListener { animation ->
                    gradientOffset = animation.animatedValue as Float
                    updateGradient()
                    invalidate()
                }
                start()
            }
        }, 1000) // 延迟1秒
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
     * 3. 提供设置开始渐变位置的方法
     */
    fun setGradientStartPos(pos: Float) {
        this.gradientStart = pos.coerceIn(0f, 1f)
        updateGradient()
        invalidate()
    }

    /**
     * 4. 提供设置渐变区域跨度的方法
     */
    fun setGradientOffset(offset: Float) {
        this.gradientOffset = offset.coerceIn(0f, 1f)
        updateGradient()
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

    private fun updateGradient() {
        if (height <= 0) {
            return
        }

        val h = height.toFloat()
        val translate = -h * (1 - gradientStart)
        gradientMatrix.setTranslate(0f, translate) // 设置平移
        val yStart = h
        val yEnd = yStart + gradientOffset * h
        maskPaint.shader = LinearGradient(
            0f, yStart, 0f, yEnd,
            colors,
            positions,
            Shader.TileMode.CLAMP
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGradient()
    }

    override fun onDraw(canvas: Canvas) {
        if (gradientStart <= 0f || gradientOffset <= 0) {
            super.onDraw(canvas)
            return
        }
        // 创建离屏缓冲层
        val count = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // 1. 绘制原始内容
        super.onDraw(canvas)

        val translate = -height * (1 - gradientStart)
        gradientMatrix.setTranslate(0f, translate) // 设置平移
        // 2. 绘制渐变 Mask
        maskPaint.shader.setLocalMatrix(gradientMatrix)
        // 在整个 View 范围内绘制，shader 会根据坐标应用渐变
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)

        canvas.restoreToCount(count)
    }

    /**
     * 4. 支持触摸事件通过上下滑动控制渐变位置
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasOnLongClickListeners()) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val currentY = event.y
                gradientStart = currentY / height.toFloat()
                invalidate()
                return true
            }
        }
        return true
    }
}
