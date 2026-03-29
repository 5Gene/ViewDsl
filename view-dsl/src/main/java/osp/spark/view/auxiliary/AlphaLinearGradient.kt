package osp.spark.view.auxiliary

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
import android.util.Log
import android.view.MotionEvent
import android.view.View

class AlphaLinearGradient {
    companion object {
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
    }

    var height = 0f
    var width = 0f
    private var blurRadius = 20f
    private var isEnabled = false
    private var view: View? = null

    // 渐变区域参数（0.0 - 1.0 相对高度）
    // gradientStart: 下方透明开始的位置 (较大 y 值)
    // gradientOffset: 渐变区域的跨度 (gradientStart - gradientEnd)
    var gradientStart = 0f
    var gradientOffset = 0.1f
    val gradientMatrix = Matrix()
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    /**
     * 初始化属性逻辑
     */
    fun initAttrs(view: View, attrs: AttributeSet?) = apply {
        this.view = view
        // 复用系统属性 android.R.attr.progress (0-100) 映射为 gradientStart
        // 复用系统属性 android.R.attr.secondaryProgress (0-100) 映射为 gradientOffset
        AttrsReuse().fromNamespace(attrs).let {
            gradientStart = it.progress / 100f
            if (it.secondaryProgress > 0) {
                gradientOffset = it.secondaryProgress / 100f
            }
            isEnabled = it.enabled
        }
    }

    /**
     * 1. 设置高斯模糊半径
     */
    fun setBlurRadius(radius: Float) {
        this.blurRadius = radius.coerceAtLeast(0.1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (blurRadius > 0.1f) {
                // API 31+ 使用 RenderEffect 实现高效模糊
                view!!.setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP))
            } else {
                view!!.setRenderEffect(null)
            }
            doInvalidate()
        }
    }

    /**
     * 2. 提供设置渐变区域跨度的方法
     */
    fun updateGradientOffset(offset: Float) {
        this.gradientOffset = offset.coerceIn(0f, 1f)
        updateGradient()
        doInvalidate()
    }

    /**
     * 3. 提供设置开始渐变位置的方法
     */
    fun setGradientStartPos(pos: Float) {
        this.gradientStart = pos.coerceIn(0f, 1f)
        updateGradient()
        doInvalidate()
    }

    private fun updateGradient() {
        if (height <= 0) {
            Log.e("AlphaLinearGradient", "height <= 0 mabe not call onSizeChanged")
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

    fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        height = h.toFloat()
        width = w.toFloat()
        updateGradient()
    }

    inline fun onDraw(canvas: Canvas, drawContent: (Canvas) -> Unit) {
        if (gradientStart <= 0f || gradientOffset <= 0) {
            drawContent(canvas)
            return
        }
        // 创建离屏缓冲层
        val count = canvas.saveLayer(0f, 0f, width, height, null)

        // 1. 绘制原始内容
        drawContent(canvas)

        val translate = -height * (1 - gradientStart)
        gradientMatrix.setTranslate(0f, translate) // 设置平移
        // 2. 绘制渐变 Mask
        maskPaint.shader.setLocalMatrix(gradientMatrix)
        // 在整个 View 范围内绘制，shader 会根据坐标应用渐变
        canvas.drawRect(0f, 0f, width, height, maskPaint)

        canvas.restoreToCount(count)
    }

    /**
     * 4. 支持触摸事件通过上下滑动控制渐变位置
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val currentY = event.y
                gradientStart = currentY / height
                doInvalidate()
                return true
            }
        }
        return true
    }

    private fun doInvalidate() {
        view!!.invalidate()
    }
}