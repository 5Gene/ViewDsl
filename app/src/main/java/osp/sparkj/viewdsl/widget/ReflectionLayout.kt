package osp.sparkj.viewdsl.widget

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
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * 自定义倒影布局
 * 特性：支持 X 轴 3D 旋转视角，具备圆弧形消隐渐变，且能突破父布局裁切限制。
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

    init {
        // FrameLayout 默认不调用 draw，需手动开启以确保 dispatchDraw 后的逻辑执行
        setWillNotDraw(false)

        // 设置遮罩画笔，使用 DST_IN 模式：只保留目标（倒影）与源（渐变遮罩）相交的部分
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    /**
     * 【注意事项 1：突破裁切限制】
     * 默认情况下，父布局会裁切超出其边界的子 View 绘制内容。
     * 必须递归关闭所有父布局的 clipChildren 和 clipToPadding，倒影才能在 View 边界外完整显示。
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        var p = parent
        while (p != null && p is ViewGroup) {
            p.clipChildren = false
            p.clipToPadding = false
            p = p.parent
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            // 【注意事项 2：圆弧形渐变实现】
            // 使用 RadialGradient（径向渐变）代替线性渐变，圆心设在底部中点。
            // 这样倒影的边缘会呈现圆弧状自然消隐，比直线切断更具立体感。
            val radius = h * reflectionHeightRatio
            maskPaint.shader = RadialGradient(
                w / 2f, h.toFloat(), // 圆心：View 底部中心
                radius,
                intArrayOf(
                    Color.argb(255, 0, 0, 0), // 顶部接触处（圆心处）最清晰
                    Color.argb(100, 0, 0, 0),  // 中间自然过渡
                    Color.TRANSPARENT         // 弧形边缘完全透明
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // 1. 正常绘制原内容（子 View 等）
        super.dispatchDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 2. 准备绘制倒影
        // 【注意事项 3：saveLayer 范围】
        // 必须从 0,0 开始 saveLayer。如果仅从 h 开始，在硬件加速下可能因为
        // 与当前 Canvas 裁切区无交集而导致绘制内容为空（即“没效果”）。
        val reflectionBottom = h * (1.1f + reflectionHeightRatio)
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

        // 5. 绘制弧形渐变遮罩矩形
        canvas.drawRect(0f, h, w, reflectionBottom, maskPaint)

        // 恢复画布图层
        canvas.restoreToCount(count)
//        canvas.drawRect(0f, h, w, reflectionBottom, maskPaint)
    }

    /**
     * 【注意事项 5：重绘区域扩充】
     * 系统默认只刷新 View 自身的 bounds。因为倒影在 View 下方，
     * 当 View 内容改变时，需要手动通知父布局刷新包含倒影在内的更大区域。
     */
//    override fun invalidate() {
//        val parent = parent as? ViewGroup
//        if (parent != null) {
//            val reflectionBottom = height * (1 + reflectionHeightRatio)
//            parent.invalidate(left, top, right, (top + reflectionBottom).toInt())
//        }
//        super.invalidate()
//    }
}
