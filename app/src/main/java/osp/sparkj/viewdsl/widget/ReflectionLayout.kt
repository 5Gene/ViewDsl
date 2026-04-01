package osp.sparkj.viewdsl.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * 自定义倒影布局
 * 特性：支持 X 轴 3D 旋转视角，具备圆弧形消隐渐变，且能突破父布局裁切限制。
 * 动画效果：倒影从底部中心点向外“扩散”显现。
 */
class ReflectionLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix3D = Matrix()
    private val camera = Camera()

    // 倒影配置参数
    private var reflectionGap = 0f           // 倒影与原图的间距
    private var rotationX = 40f              // 围绕 X 轴旋转的角度，模拟地面透视
    private var reflectionHeightRatio = .5f // 倒影高度占原图的比例

    // 动画相关
    private var animationProgress = 0f       // 动画进度 (0f -> 1f)
    private var animator: ValueAnimator? = null

    init {
        // FrameLayout 默认不调用 draw，需手动开启以确保 dispatchDraw 后的逻辑执行
        setWillNotDraw(false)

        // 设置遮罩画笔，使用 DST_IN 模式：只保留目标（倒影）与源（渐变遮罩）相交的部分
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    /**
     * 启动“扩散”动画
     * 倒影将从与原图的接触点开始，像波纹或水渍一样向外扩散显示
     */
    private fun startSpreadAnimation() {
        if (animator?.isRunning == true) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2500 // 扩散过程稍慢一点更有质感
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * 强制刷新包含倒影在内的整个区域
     */
    private fun invalidateWithReflection() {
        val p = parent as? ViewGroup
        if (p != null) {
            val reflectionBottom = height + reflectionGap + height * reflectionHeightRatio
            // 扩大刷新范围到倒影底部
            p.invalidate(left, top, right, (top + reflectionBottom).toInt())
        } else {
            invalidate()
        }
    }

    /**
     * 【注意事项：突破裁切限制】
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        var p = parent
        while (p != null && p is ViewGroup) {
            p.clipChildren = false
            p.clipToPadding = false
            p = p.parent
        }
        if (visibility == View.VISIBLE) {
            startSpreadAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) {
            startSpreadAnimation()
        } else {
            animator?.cancel()
            animationProgress = 0f
            invalidate()
        }
    }

    /**
     * 根据动画进度动态更新扩散遮罩
     */
    private fun updateMaskShader(w: Float, h: Float) {
        val reflectionH = h * reflectionHeightRatio
        val currentRadius = reflectionH * animationProgress

        if (currentRadius <= 0f) {
            maskPaint.shader = null
            return
        }

        // 优化渐变：增加更多层次，通过多级 Alpha 值模拟柔和的消隐感
        maskPaint.shader = RadialGradient(
            w / 2f, h, // 圆心：View 底部中心
            currentRadius,
            intArrayOf(
                Color.argb(230, 0, 0, 0), // 初始点
                Color.argb(210, 0, 0, 0), // 核心层
                Color.argb(150, 0, 0, 0), // 中间过渡层
                Color.argb(80, 0, 0, 0),  // 扩散层
                Color.argb(30, 0, 0, 0),  // 边缘消隐层
                Color.TRANSPARENT         // 完全透明
            ),
            floatArrayOf(0f, 0.2f, 0.45f, 0.7f, 0.9f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        // 1. 正常绘制原内容
        super.dispatchDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0 || animationProgress <= 0f) return

        // 更新扩散遮罩
        updateMaskShader(w, h)

        // 2. 准备绘制倒影层
        val reflectionBottom = reflectionGap + h * (1.1f + reflectionHeightRatio)
        val count = canvas.saveLayer(0f, 0f, w, reflectionBottom, null)

        canvas.save()

        // --- 3. 3D 变换逻辑 ---
        matrix3D.reset()
        camera.save()
        // 【注意事项 4：Camera 透视修正】
        // 必须设置 Location 的 Z 轴距离，否则 rotateX 会产生极大的近大远小形变，导致倒影“飞走”。
        camera.setLocation(0f, 0f, -8f * resources.displayMetrics.density)
        camera.rotateX(rotationX)
        camera.getMatrix(matrix3D)
        camera.restore()

        // 调整变换中心点到 View 的底部中心
        val centerX = w / 2f
        val centerY = h
        matrix3D.preTranslate(-centerX, -centerY)
        matrix3D.postTranslate(centerX, centerY)

        // 应用 3D 旋转矩阵
        canvas.concat(matrix3D)

        // 垂直镜像翻转
        canvas.scale(1f, -1f, centerX, centerY)
        canvas.translate(0f, -reflectionGap)

        // 4. 再次调用 dispatchDraw 绘制镜像内容（生成所有子 View 的倒影）
        super.dispatchDraw(canvas)

        canvas.restore()

        // 5. 绘制扩散渐变遮罩
        // 遮罩区域覆盖整个倒影高度
        canvas.drawRect(0f, h, w, reflectionBottom, maskPaint)

        // 恢复画布图层
        canvas.restoreToCount(count)
    }
}
