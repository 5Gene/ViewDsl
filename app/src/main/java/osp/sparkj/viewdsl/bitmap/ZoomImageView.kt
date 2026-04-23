package osp.sparkj.viewdsl.bitmap

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView

/**
 * 支持双击 / 双指缩放 + 拖动的图片控件。
 *
 * ## 能力
 * - **双击放大**：每次双击按 [doubleTapStep] 倍递增（默认 1.6x）；超过 [maxScale] 后回到 [minScale]。
 *   带 [animMs] 毫秒减速动画；
 * - **双指缩放**：以双指中点为焦点；
 * - **单指拖动**：缩放后可平移；
 * - **边界约束**：内容小于视口时居中；大于视口时不拖出视口；
 * - **外部同步**：[setTransform] / [getTransform] 使用 `(scale, tx, ty)` 作为 API。
 *   `scale = 1f` 表示当前 [scaleType] 下的基准；`tx/ty` 是在基准之上、view 坐标系下的 px 平移。
 *
 * ## 关于 scaleType
 * 控件内部始终以 [ScaleType.MATRIX] 运行（缩放/拖动需要），但**完全兼容 XML 的 `android:scaleType`**：
 * 用户设置的 scaleType 被当作"基准显示策略"装进 `baseMatrix`，随后 `user*` 变换在其之上叠加。
 * 支持：`fitXY / fitStart / fitCenter / fitEnd / center / centerCrop / centerInside / matrix`。
 *
 * ## 监听变换
 * 有两种方式：
 * - **单一便捷回调** [onTransformChanged]：只在**用户手势**引起的变化时触发（`setTransform` 不会）；
 * - **监听列表** [addOnTransformChangedListener] / [removeOnTransformChangedListener]：
 *   在**所有**变换更新（含 `setTransform`）时触发，用于自动同步场景。
 *
 * ## 自动跟随另一个 ZoomImageView（anchor）
 * 通过 XML 指定跟随目标（复用 CoordinatorLayout 的 `layout_anchor`）：
 * ```xml
 * <ZoomImageView android:id="@+id/zoom_a" .../>
 * <ZoomImageView android:id="@+id/zoom_b" app:layout_anchor="@id/zoom_a" .../>
 * ```
 * 上例中 `zoom_b` 会自动跟随 `zoom_a` 的缩放/移动。也可双向：两者互相指向对方，
 * 内部用"值相等短路"阻止回环。
 *
 * 代码里也能设：[setAnchor]。
 *
 * ## 手写同步也可以
 * ```kotlin
 * top.onTransformChanged = { s, tx, ty -> bottom.setTransform(s, tx, ty) }
 * bottom.onTransformChanged = { s, tx, ty -> top.setTransform(s, tx, ty) }
 * ```
 * [setTransform] 不会再触发 [onTransformChanged]，不会形成回环。
 */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    // ---------- 配置 ----------
    var minScale: Float = 1f
    var maxScale: Float = 6f
    var doubleTapStep: Float = 1.6f
    var animMs: Long = 220L

    /** 用户手势变化时回调；[setTransform] 触发不会走这里。 */
    var onTransformChanged: ((scale: Float, tx: Float, ty: Float) -> Unit)? = null

    /**
     * 变换监听器。跟 [onTransformChanged] 不同的是，**无论手势还是 [setTransform]**
     * 都会触发，适合做链式/自动同步。注册/注销用 [addOnTransformChangedListener]
     * / [removeOnTransformChangedListener]。
     */
    fun interface OnTransformChangedListener {
        fun onTransformChanged(scale: Float, tx: Float, ty: Float)
    }

    private val transformListeners = mutableListOf<OnTransformChangedListener>()

    /** 添加变换监听。同一实例重复添加会生效多次。 */
    fun addOnTransformChangedListener(l: OnTransformChangedListener) {
        transformListeners.add(l)
    }

    /** 移除变换监听。 */
    fun removeOnTransformChangedListener(l: OnTransformChangedListener) {
        transformListeners.remove(l)
    }

    // ---------- Anchor（自动跟随另一个 ZoomImageView） ----------
    private var anchorViewId: Int = View.NO_ID
    private var anchorView: ZoomImageView? = null

    /**
     * Anchor 订阅器：把目标控件的变换同步到自己。
     * **值相等短路**是双向 anchor 不形成回环的关键：接收到的值和自身已经一致就直接返回。
     */
    private val anchorListener = OnTransformChangedListener { s, tx, ty ->
        if (userScale == s && userTx == tx && userTy == ty) return@OnTransformChangedListener
        setTransform(s, tx, ty, animate = false)
    }

    // ---------- 用户相对状态（以 baseScaleType 的基准矩阵为 1f） ----------
    private var userScale = 1f
    private var userTx = 0f
    private var userTy = 0f

    /**
     * 用户期望的基准显示策略，对应原生 [android.widget.ImageView.ScaleType]。
     * 控件内部始终跑在 [ScaleType.MATRIX] 下，这个字段只影响 [baseMatrix] 的计算方式。
     *
     * 用 [lateinit] 是为了让 super 构造里对 `android:scaleType` 的设置先透传给父类保存，
     * 之后在 init{} 里再读回来，避免被 Kotlin 属性初始化器覆盖成默认值。
     */
    private lateinit var baseScaleType: ScaleType

    // ---------- 内部计算 ----------
    private val baseMatrix = Matrix() // drawable -> view 的 fit 基准
    private val displayMatrix = Matrix() // baseMatrix × user 变换
    private val tmp = FloatArray(9)
    private val tmpRect = RectF()
    private var anim: ValueAnimator? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val target = (userScale * d.scaleFactor).coerceIn(minScale, maxScale)
                val factor = if (userScale == 0f) 1f else target / userScale
                applyPivotScale(factor, d.focusX, d.focusY)
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val next = if (userScale * doubleTapStep > maxScale + 1e-3f) {
                    minScale
                } else {
                    (userScale * doubleTapStep).coerceAtMost(maxScale)
                }
                animateScaleAroundPivot(next, e.x, e.y)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (scaleDetector.isInProgress) return false
                userTx -= distanceX
                userTy -= distanceY
                updateDisplay(fire = true)
                return true
            }
        }
    )

    init {
        // 此时 super 已把 android:scaleType 的值设进去了，getScaleType() 就是 XML 想要的那个
        baseScaleType = super.getScaleType()
        super.setScaleType(ScaleType.MATRIX)

        // 直接按属性名从 AttributeSet 里取 layout_anchor（CoordinatorLayout 已定义的属性，
        // 这里仅复用不重复声明，也不引入任何 R 依赖）
        anchorViewId = attrs?.let { readLayoutAnchor(it) } ?: View.NO_ID
    }

    private fun readLayoutAnchor(attrs: AttributeSet): Int {
        for (i in 0 until attrs.attributeCount) {
            if (attrs.getAttributeName(i) == "layout_anchor") {
                return attrs.getAttributeResourceValue(i, View.NO_ID)
            }
        }
        return View.NO_ID
    }

    /**
     * 保存用户想要的基准 scaleType；控件内部始终切回 [ScaleType.MATRIX]。
     * 这样 XML `android:scaleType="centerCrop"` 或运行时 setScaleType(..) 都能生效。
     */
    override fun setScaleType(scaleType: ScaleType) {
        if (!::baseScaleType.isInitialized) {
            // super 构造期调用 —— 先原样丢给父类，init{} 会读回来
            super.setScaleType(scaleType)
            return
        }
        if (scaleType != ScaleType.MATRIX) {
            baseScaleType = scaleType
        }
        super.setScaleType(ScaleType.MATRIX)
        rebuildBase()
    }

    /** 对外暴露用户设置的 scaleType，而不是内部强制的 [ScaleType.MATRIX]。 */
    override fun getScaleType(): ScaleType =
        if (::baseScaleType.isInitialized) baseScaleType else super.getScaleType()

    // ---------- 外部 API ----------

    /**
     * 原样设置变换。不触发 [onTransformChanged]。
     *
     * @param scale 绝对缩放（1f = fit）
     * @param tx view 坐标系下的 X 平移（px）
     * @param ty view 坐标系下的 Y 平移（px）
     * @param animate 是否带动画
     */
    fun setTransform(scale: Float, tx: Float, ty: Float, animate: Boolean = false) {
        val s = scale.coerceIn(minScale, maxScale)
        if (animate) {
            animateTo(s, tx, ty)
        } else {
            userScale = s
            userTx = tx
            userTy = ty
            updateDisplay(fire = false)
        }
    }

    /** @return (scale, tx, ty)，对应 [setTransform] 的语义。 */
    fun getTransform(): Triple<Float, Float, Float> = Triple(userScale, userTx, userTy)

    fun resetTransform(animate: Boolean = true) {
        setTransform(1f, 0f, 0f, animate)
    }

    /**
     * 动态设置 anchor：跟随另一个 [ZoomImageView] 的变换。传 `null` 取消跟随。
     *
     * 绑定时会立刻同步一次对方的当前变换；控件必须已 attach 才能触发订阅效果（XML 配置的
     * [layout_anchor] 会在 [onAttachedToWindow] 里自动执行这一步）。
     */
    fun setAnchor(anchor: ZoomImageView?) {
        if (anchor === anchorView) return
        unbindAnchor()
        anchorViewId = anchor?.id ?: View.NO_ID
        anchorView = anchor
        if (anchor != null && anchor !== this) {
            anchor.addOnTransformChangedListener(anchorListener)
            val (s, tx, ty) = anchor.getTransform()
            setTransform(s, tx, ty, animate = false)
        }
    }

    // ---------- 触摸 ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawable == null) return super.onTouchEvent(event)
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        return true
    }

    // ---------- 生命周期 ----------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildBase()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bindAnchorById()
    }

    override fun onDetachedFromWindow() {
        unbindAnchor()
        super.onDetachedFromWindow()
    }

    /** 通过 [anchorViewId] 在当前根视图里找到目标并订阅。 */
    private fun bindAnchorById() {
        if (anchorViewId == View.NO_ID) return
        if (anchorView != null) return
        val v = rootView?.findViewById<View>(anchorViewId) ?: return
        if (v === this || v !is ZoomImageView) return
        anchorView = v
        v.addOnTransformChangedListener(anchorListener)
        val (s, tx, ty) = v.getTransform()
        setTransform(s, tx, ty, animate = false)
    }

    private fun unbindAnchor() {
        anchorView?.removeOnTransformChangedListener(anchorListener)
        anchorView = null
    }

    /**
     * 在真正 [setImageDrawable] 之前对 drawable 做处理（滤镜、圆角、贴纸流水线等）。
     *
     * - XML 里 `android:src` / `app:srcCompat`、以及代码里的 [setImageBitmap] / [setImageResource]
     *   在 AppCompat 实现里大都会归一到 [setImageDrawable]，**只覆写这一处即可覆盖绝大多数入口**；
     * - 返回 `null` 表示清空图片；返回新 Drawable 时注意是否要回收旧 Bitmap（自行约定）。
     *
     * 子类覆写示例：
     * ```kotlin
     * override fun transformIncomingDrawable(drawable: Drawable?): Drawable? {
     *     val bmp = drawable?.toBitmap() ?: return null
     *     val out = myPipeline(bmp)
     *     if (out !== bmp) bmp.recycle()
     *     return BitmapDrawable(resources, out)
     * }
     * ```
     */
    protected open fun transformIncomingDrawable(drawable: Drawable?): Drawable {
//        thread {
//            val input = drawable!!.toBitmap()
//            val ctx = RunCtx(input)
//
////            val bitmapDrawable = BitmapDrawable(MlKitSeg(.6f).process(ctx, input))
//            val bitmapDrawable = StickerPipeline()
//                .addStep(MlKitSeg(.6f))
//                .addStep(AlphaStroke())
////                .addStep(BlurStroke())
////                .addStep(ShaderStroke(width = 15, color = Color.WHITE))
////                .addStep(CropStep(KeepInside, personMask))
////                .addBgStep(CropStep(KeepInside, bgMask))
//                .execute(input)
//            post {
//                super.setImageDrawable(BitmapDrawable(bitmapDrawable))
//            }
////            setImageBitmap(MlKitSeg().process(ctx, input))
//        }
        return drawable!!
    }

    override fun setImageDrawable(drawable: Drawable?) {
        val processed = transformIncomingDrawable(drawable)
        super.setImageDrawable(processed)
        rebuildBase()
    }

    // ---------- 内部：矩阵装配 ----------

    /**
     * 根据 [baseScaleType] 计算 drawable → view 的基准矩阵（不含 user 变换）。
     *
     * 各 scaleType 的语义与 [android.widget.ImageView.ScaleType] 保持一致：
     * - `FIT_XY`         —— 拉伸铺满，不保持宽高比
     * - `FIT_START/CENTER/END` —— 保持宽高比完全装入，靠起始/居中/末尾对齐
     * - `CENTER`         —— 原尺寸，居中
     * - `CENTER_CROP`    —— 保持宽高比覆盖视口（max 缩放），居中
     * - `CENTER_INSIDE`  —— 比视口小则原尺寸居中，否则等同 `FIT_CENTER`
     * - `MATRIX`         —— 不做基准变换（识别为 identity）
     */
    private fun rebuildBase() {
        val d = drawable ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (vw <= 0f || vh <= 0f || dw <= 0f || dh <= 0f) return

        val type = if (::baseScaleType.isInitialized) baseScaleType else ScaleType.FIT_CENTER
        baseMatrix.reset()
        when (type) {
            ScaleType.FIT_XY -> {
                baseMatrix.setScale(vw / dw, vh / dh)
            }

            ScaleType.FIT_START, ScaleType.FIT_CENTER, ScaleType.FIT_END -> {
                val s = minOf(vw / dw, vh / dh)
                baseMatrix.setScale(s, s)
                val extraX = vw - dw * s
                val extraY = vh - dh * s
                when (type) {
                    ScaleType.FIT_START -> baseMatrix.postTranslate(0f, 0f)
                    ScaleType.FIT_END -> baseMatrix.postTranslate(extraX, extraY)
                    else -> baseMatrix.postTranslate(extraX / 2f, extraY / 2f)
                }
            }

            ScaleType.CENTER -> {
                baseMatrix.postTranslate((vw - dw) / 2f, (vh - dh) / 2f)
            }

            ScaleType.CENTER_CROP -> {
                val s = maxOf(vw / dw, vh / dh)
                baseMatrix.setScale(s, s)
                baseMatrix.postTranslate((vw - dw * s) / 2f, (vh - dh * s) / 2f)
            }

            ScaleType.CENTER_INSIDE -> {
                val s = if (dw <= vw && dh <= vh) 1f else minOf(vw / dw, vh / dh)
                baseMatrix.setScale(s, s)
                baseMatrix.postTranslate((vw - dw * s) / 2f, (vh - dh * s) / 2f)
            }

            ScaleType.MATRIX -> {
                // identity；使用方自己通过 setTransform/user 变换控制
            }

            null -> Unit
        }
        updateDisplay(fire = false)
    }

    /**
     * display = base × user
     * user 部分 = postScale(userScale) ∘ postTranslate(userTx, userTy)
     *
     * 每次装配前调用 [clampTranslation] 做**过程中钳制**：内容大于视口时边缘不能越过视口；
     * 小于视口时居中。这样缩放/拖动的每一帧都不会越界，不用等抬手后再拉回。
     *
     * 事件分发：
     * - [transformListeners]：任何变换（含 [setTransform]）都会通知，保证 anchor 链式传播；
     * - [onTransformChanged]：只在 `fire == true`（用户手势）时触发，保持与之前单回调的语义一致。
     */
    private fun updateDisplay(fire: Boolean) {
        clampTranslation()
        displayMatrix.set(baseMatrix)
        displayMatrix.postScale(userScale, userScale, 0f, 0f)
        displayMatrix.postTranslate(userTx, userTy)
        imageMatrix = displayMatrix
        if (transformListeners.isNotEmpty()) {
            // 拷贝一份防止监听器内部增删造成的并发修改
            val snapshot = transformListeners.toList()
            for (l in snapshot) l.onTransformChanged(userScale, userTx, userTy)
        }
        if (fire) onTransformChanged?.invoke(userScale, userTx, userTy)
    }

    /**
     * 就地修改 [userTx] / [userTy]，把显示矩形钳到视口内。
     *
     * 推导：display = baseMatrix × postScale(userScale, 0,0) × postTranslate(userTx, userTy)，
     * 故显示矩形 = (baseRect 按 userScale 围绕原点缩放后) + (userTx, userTy)。
     * 记 `scaledRect` 为前半段的结果，则最终 left = scaledRect.left + userTx，其它同理。
     *
     * - 内容宽 ≤ 视口宽：要求最终 left = (vw - scaledRect.width) / 2
     *   → userTx = (vw - scaledRect.width) / 2 - scaledRect.left
     * - 否则：left ∈ [vw - scaledRect.width, 0]
     *   → userTx ∈ [vw - scaledRect.right, -scaledRect.left]
     *
     * 垂直方向同理。
     */
    private fun clampTranslation() {
        val d = drawable ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        tmpRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        baseMatrix.mapRect(tmpRect)
        val scaledLeft = tmpRect.left * userScale
        val scaledTop = tmpRect.top * userScale
        val scaledW = tmpRect.width() * userScale
        val scaledH = tmpRect.height() * userScale

        userTx = if (scaledW <= vw) {
            (vw - scaledW) / 2f - scaledLeft
        } else {
            userTx.coerceIn(vw - (scaledLeft + scaledW), -scaledLeft)
        }
        userTy = if (scaledH <= vh) {
            (vh - scaledH) / 2f - scaledTop
        } else {
            userTy.coerceIn(vh - (scaledTop + scaledH), -scaledTop)
        }
    }

    /**
     * 以视口内的 (focusX, focusY) 为不动点缩放：
     * 视觉上锚点像素保持在原位，数学上等价于同时修改 userScale 与 userTx/Ty。
     */
    private fun applyPivotScale(factor: Float, focusX: Float, focusY: Float) {
        // 当前 display 矩阵下锚点在 content 坐标的位置不变 → 推导 tx/ty 调整量
        // 缩放前：p = base.scale*userScale*content + (base.trans + userT) → tx_new 关系
        // 直接基于 view 坐标下的焦点缩放公式：
        // tx' = focus - factor * (focus - tx_current_effective)
        // 其中 tx_current_effective 是 userTx 相对 focus 的偏移。
        userTx = focusX - factor * (focusX - userTx)
        userTy = focusY - factor * (focusY - userTy)
        userScale *= factor
        updateDisplay(fire = true)
    }

    // ---------- 动画 ----------

    private fun animateScaleAroundPivot(targetScale: Float, focusX: Float, focusY: Float) {
        anim?.cancel()
        val startScale = userScale
        val startTx = userTx
        val startTy = userTy
        val totalFactor = targetScale / startScale
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = animatedValue as Float
                val f = 1f + (totalFactor - 1f) * t
                userScale = startScale * f
                userTx = focusX - f * (focusX - startTx)
                userTy = focusY - f * (focusY - startTy)
                updateDisplay(fire = true)
            }
            start()
        }
    }

    private fun animateTo(targetScale: Float, targetTx: Float, targetTy: Float) {
        anim?.cancel()
        val s0 = userScale
        val tx0 = userTx
        val ty0 = userTy
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = animatedValue as Float
                userScale = s0 + (targetScale - s0) * t
                userTx = tx0 + (targetTx - tx0) * t
                userTy = ty0 + (targetTy - ty0) * t
                updateDisplay(fire = false)
            }
            start()
        }
    }
}

