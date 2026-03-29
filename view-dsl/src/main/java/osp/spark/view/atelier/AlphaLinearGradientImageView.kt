package osp.spark.view.atelier

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import osp.spark.view.auxiliary.AlphaLinearGradient

class AlphaLinearGradientImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private val alphaLinearGradient = AlphaLinearGradient()

    init {
        alphaLinearGradient.initAttrs(this, attrs)
    }

    /**
     * 测试代码：延迟 1s 后执行 gradientOffset 从 0 到 1 的动画，持续 10s
     */
    fun startTestAnimation() {
        postDelayed({
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 20000 // 10秒
                addUpdateListener { animation ->
                    setGradientOffset(animation.animatedValue as Float)
                }
                start()
            }
        }, 1000) // 延迟1秒
    }

    /**
     * 1. 设置高斯模糊半径
     */
    fun setBlurRadius(radius: Float) {
        alphaLinearGradient.setBlurRadius(radius)
    }

    /**
     * 2. 提供设置渐变区域跨度的方法
     */
    fun setGradientOffset(offset: Float) {
        alphaLinearGradient.updateGradientOffset(offset)
    }

    /**
     * 3. 提供设置开始渐变位置的方法
     */
    fun setGradientStartPos(pos: Float) {
        alphaLinearGradient.setGradientStartPos(pos)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        alphaLinearGradient.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onDraw(canvas: Canvas) {
        alphaLinearGradient.onDraw(canvas) { super.onDraw(canvas) }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (alphaLinearGradient.onTouchEvent(event!!)) {
            return true
        }
        return super.onTouchEvent(event)
    }
}