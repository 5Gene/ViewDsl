package osp.june.dsl

import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.NO_ID
import android.view.ViewGroup.generateViewId
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.contains
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView


//限制使用最近的receiver
//对于有receiver的方法(A.()->Unit),限定作用域只在此方法内,方法内部的方法无法访问
@DslMarker
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
annotation class ViewDslScope

fun ViewGroup.addViewCheck(child: View, width: Int, height: Int) {
    if (child.parent != null) {
        return
    }
    if (child.checkId(id).layoutParams != null) {
        addView(child, child.layoutParams)
    } else {
        addView(child, width, height)
    }
}

operator fun View.unaryMinus() {
    (this.parent as ViewGroup).removeView(this)
}

operator fun ViewGroup.plus(child: View): LayoutParams {
    if (child in this) {
        return layoutParams
    }
    if (this@ViewGroup.findViewById<View>(id) != null) {
        return layoutParams
    }
    this@ViewGroup.addView(checkId())
    //在addViewInner中
//    if (!checkLayoutParams(params)) {
//      这段代码会把ViewGroup.LayoutParams转化成对应布局的param比如LinearLayout的转为LinearLayout.LayoutParams
//        params = generateLayoutParams(params);
//    }
    return layoutParams
}

fun ViewGroup.animateLayoutChange(transition: LayoutTransition = LayoutTransition()) {
    layoutTransition = transition
}

fun View.linearLayoutParams(
    width: Int = LayoutParams.MATCH_PARENT, height: Int = LayoutParams.WRAP_CONTENT,
    param: @ViewDslScope LinearLayout.LayoutParams.() -> Unit
) {
    layoutParams = LinearLayout.LayoutParams(width, height).apply(param)
}

inline fun View.frameLayoutParams(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    block: @ViewDslScope FrameLayout.LayoutParams.() -> Unit = {}
) {
    layoutParams = FrameLayout.LayoutParams(width, height).apply(block)
}

fun ConstraintLayout.LayoutParams.matchHorizontal() {
    width = 0
    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
}

fun ConstraintLayout.LayoutParams.matchVertical() {
    height = 0
    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
}

inline fun View.constLayoutParams(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    config: @ViewDslScope ConstraintLayout.LayoutParams.() -> Unit = {}
) {
    layoutParams = ConstraintLayout.LayoutParams(width, height).apply(config)
}

inline fun <reified T : ViewGroup> T.row(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    crossinline content: @ViewDslScope LinearLayout.() -> Unit
): LinearLayout {
    return (findViewById(id) ?: LinearLayout(context)).apply(content).also {
        it.orientation = LinearLayout.HORIZONTAL
        addViewCheck(it, width, height)
    }
}

inline fun ViewGroup.column(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    crossinline content: @ViewDslScope LinearLayout.() -> Unit
): LinearLayout {
    return (findViewById(id) ?: LinearLayout(context)).apply(content).also {
        it.orientation = LinearLayout.VERTICAL
        addViewCheck(it, width, height)
    }
}

inline fun <reified T : ViewGroup> T.linearlayout(
    width: Int = LayoutParams.WRAP_CONTENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    content: @ViewDslScope LinearLayout.() -> Unit
): LinearLayout {
    return (findViewById(id) ?: LinearLayout(context)).apply(content).also {
        addViewCheck(it, width, height)
    }
}

inline fun ViewGroup.icon(
    width: Int = LayoutParams.WRAP_CONTENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope ShapeableImageView.() -> Unit
): ImageView {
    //setShapeAppearanceModel()
    //setStroke...()
    return (findViewById(id) ?: ShapeableImageView(context)).apply(config).also {
        addViewCheck(it, width, height)
    }
}

inline fun ViewGroup.view(
    width: Int = LayoutParams.WRAP_CONTENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    supplyer: @ViewDslScope () -> View
): View {
    return (findViewById(id) ?: supplyer()).also {
        addViewCheck(it, width, height)
    }
}

inline fun ViewGroup.text(
    width: Int = LayoutParams.WRAP_CONTENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope TextView.() -> Unit
): TextView {
    return (findViewById(id) ?: TextView(context)).apply(config).also {
        addViewCheck(it, width, height)
    }
}

inline fun ViewGroup.button(
    width: Int = LayoutParams.WRAP_CONTENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope MaterialButton.() -> Unit
): Button {
    //setCornerRadius()
    //setRippleColor()
    //setIcon...()
    //setStroke...()
    //去掉button的内部上下内边距 下面两个内边距是为了 按压的时候显示深度阴影的
    //insetTop = 0
    //insetBottom = 0
    return (findViewById(id) ?: MaterialButton(context)).apply(config).also {
        addViewCheck(it, width, height)
    }
}

inline fun <reified T : ViewGroup> T.spacer(
    width: Int = LayoutParams.WRAP_CONTENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    color: Int = Color.TRANSPARENT,
    layoutParams: LayoutParams? = null
): View {
    return ((findViewById(id)
        ?: (if (color == Color.TRANSPARENT) Space(context) else View(context))).apply {
        setBackgroundColor(color)
    }).also {
        if (it.parent != null) {
            return@also
        }
        addView(it.checkId(id), layoutParams ?: LayoutParams(width, height))
    }
}

inline fun <reified T : View> T.background(config: @ViewDslScope GradientDrawable.() -> Unit) {
    background = GradientDrawable().apply(config)
}

fun StateListDrawable.active(config: @ViewDslScope GradientDrawable.() -> Unit) {
    val gradientDrawable = GradientDrawable().apply(config)
    addState(
        intArrayOf(android.R.attr.state_pressed),
        gradientDrawable
    )
    addState(
        intArrayOf(android.R.attr.state_selected),
        gradientDrawable
    )
    addState(
        intArrayOf(android.R.attr.state_checked),
        gradientDrawable
    )
}

fun StateListDrawable.default(config: @ViewDslScope GradientDrawable.() -> Unit) {
    addState(
        intArrayOf(),
        GradientDrawable().apply(config)
    )
}

inline fun <reified T : View> T.backgroundSelector(config: @ViewDslScope StateListDrawable.() -> Unit) {
    background = StateListDrawable().apply(config)
}

//    1.设置View的Z轴高度  android:elevation="10dp"   对应代码  setElevation();
//
//    2.设置View的Z轴高度  android:translationZ="10dp" 对应代码 setTranslationZ()
//
//    3.设置View阴影轮廓范围的模式  android:outlineProvider="" 对应代码  setOutlineProvider();
//
//    4.设置View的阴影颜色 android:outlineSpotShadowColor="#03A9F4" 对应代码 setOutlineSpotShadowColor()（请注意！此条属性必需在高于Android10版本包含10的版本才有效果）
//
//    5.设置View的阴影光环颜色，但是基本看不到，非常迷惑的属性。 android:outlineAmbientShadowColor="#03A9F4" 对应代码 setOutlineAmbientShadowColor（请注意！此条属性必需在高于Android10版本包含10的版本才有效果）
//
//    6.设置View自定义形状的阴影 setOutlineProvider(new ViewOutlineProvider(){ //略..});

//    请开启硬件加速功能 android:hardwareAccelerated="true" ，现在的设备一般是默认开启硬件加速的。但是如果你主动设置关闭会出现没有阴影效果的问题。
//    请检查好自己的Android版本，必需在5.0以上。设置阴影颜色效果必需需要在10.0以上。
//    阴影是绘制于父控件上的，所以控件与父控件的边界之间需有足够空间绘制出阴影才行。
//    如果未设置 android:outlineProvider="bounds" ，那么控件这个属性会默认为android:outlineProvider="background"， 这个时候View必须设置背景色，且不能为透明，否则会没有阴影。
//    不能将elevation 或者 translationZ 的值设置的比整个View还大，这样会有阴影效果。但是阴影的渐进效果会被拉的很长很长，会看不清楚阴影效果，你会错认觉得没有阴影效果。
//    https://www.cnblogs.com/guanxinjing/p/11151036.html
inline fun <reified T : View> T.shape(shadowColor: Int? = null, crossinline outline: (View, Outline) -> Unit) {
//        ViewOutlineProvider.BOUNDS
//        outline.setAlpha(0.0f); 可以设置透明度
    shadowColor?.run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outlineSpotShadowColor = this
        }
    }
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline(view, outline)
        }
    }
}

inline fun <reified T : View> T.shapeRoundHalfHeightRatio(radiusRatio: Number = 1F, shadowColor: Int? = null) {
    shape { view, outline ->
        outline.setRoundRect(0, 0, view.width, view.height, view.height / 2F * radiusRatio.toFloat())
    }
}

inline fun <reified T : View> T.shapeRound(radius: Number = 1F, shadowColor: Int? = null) {
    shape { view, outline ->
        outline.setRoundRect(0, 0, view.width, view.height, radius.toFloat())
    }
}

inline fun <reified T : View> T.padding(horizontal: Number? = null, vertical: Number? = null) {
    updatePadding(
        horizontal?.toInt() ?: paddingStart,
        vertical?.toInt() ?: paddingTop,
        horizontal?.toInt() ?: paddingEnd,
        vertical?.toInt() ?: paddingBottom
    )
}

inline fun <reified T : View> T.padding(
    left: Number = paddingStart,
    top: Number = paddingTop,
    right: Number = paddingEnd,
    bottom: Number = paddingBottom
) {
    updatePadding(
        left.toInt(), top.toInt(),
        right.toInt(), bottom.toInt()
    )
}

inline fun <reified T : View> T.padding(
    padding: Number,
) {
    setPadding(padding.toInt())
}

inline fun <reified T : View> T.visibility(visible: Boolean): T {
    return this.apply {
        visibility = if (visible) View.VISIBLE else View.GONE
    }
}

inline fun <reified T : View> T.isVisible(): Boolean = isVisible

inline fun <reified T : ViewGroup> T.canvas(
    width: Int,
    height: Int,
    id: Int = NO_ID,
    crossinline drawScope: @ViewDslScope CanvasView.() -> Unit
): CanvasView {
    return (findViewById(id) ?: CanvasView(context)).apply(drawScope).also {
        addViewCheck(it, width, height)
    }
//    addView(CanvasView(context = context).apply(drawScope), width, height)
}

inline fun <reified T : ViewGroup> T.vLayoutConstraint(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.MATCH_PARENT,
    modifier: VModifier = VModifier,
    id: Int = NO_ID,
    viewScope: @ViewDslScope LayoutConstraint.() -> Unit
): ConstraintLayout {
    return (findViewById(id) ?: LayoutConstraint(context = context, modifier)).apply(viewScope).also {
        addViewCheck(it, width, height)
    }
}

//<editor-fold desc="CanvasView">
@SuppressLint("ViewConstructor")
class CanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Locker by MapLocker() {

    private val size = RectF(0F, 0f, 0f, 0F)
    var drawIntoCanvas: (Canvas) -> Unit = {}
    var attachToWindow: () -> Unit = {}
    var detachedFromWindow: () -> Unit = {}
    private var dispatchTouchEvent: ((MotionEvent, () -> Boolean) -> Boolean)? = null
    private var onTouchEvent: ((MotionEvent, () -> Boolean) -> Boolean)? = null

    init {
        keepView(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        size.right = w.toFloat()
        size.bottom = h.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        drawIntoCanvas(canvas)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return dispatchTouchEvent?.invoke(ev) {
            super.dispatchTouchEvent(ev)
        } ?: super.dispatchTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return onTouchEvent?.invoke(event) {
            super.onTouchEvent(event)
        } ?: super.onTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachedFromWindow()
        animatorsCancel()
        clear()
    }
}
//</editor-fold>

//<editor-fold desc="CustomView">
@SuppressLint("ViewConstructor")
class LayoutConstraint constructor(
    context: Context, modifier: VModifier
) : ConstraintLayout(context), ViewCompose, Locker by MapLocker() {

    private val map = modifier.toKeyMap()
    private val cabinet = mutableMapOf<String, Any>()
    private val drawTouchModifiers: List<ViewModifier.VDrawTouchModifier>? =
        map[ViewModifier.VDrawTouchModifier::class.simpleName].safeAs<List<ViewModifier.VDrawTouchModifier>>()

    init {
        map[ViewModifier.VExtraMapModifier::class.simpleName]?.safeAs<MutableList<ViewModifier.VExtraMapModifier>>()
            ?.forEach {
                cabinet.putAll(it.extra())
            }
        cabinet["layoutId"]?.safeAs<Int>()?.let {
            inflate(context, it, this)
        }
        map[ViewModifier.VCustomizeModifier::class.simpleName]?.forEach {
            it.safeAs<ViewModifier.VCustomizeModifier>()?.attach(this)
        }
        keepView(this)
    }

    private val size = RectF(0F, 0f, 0f, 0F)
    private val dispatchDraw: ((Canvas, (Canvas) -> Unit) -> Unit) = { canvas, superDraw ->
        drawTouchModifiers?.allDraw(this, canvas, superDraw) ?: superDraw(canvas)
    }
    private var dispatchTouchEvent: ((MotionEvent, () -> Boolean) -> Boolean)? = { event, superTouch ->
        drawTouchModifiers?.allTouch(this, event, superTouch) ?: false
    }
    var attachToWindow: () -> Unit = {}
    var detachedFromWindow: () -> Unit = {}
    var drawIntoCanvas: (Canvas) -> Unit = {}
    private var onInterceptTouchEvent: ((MotionEvent, () -> Boolean) -> Boolean)? = null
    private var onTouchEvent: ((MotionEvent, () -> Boolean) -> Boolean)? = null


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightExactly = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
        cabinet.remove("sizeFactor")?.safeAs<Pair<Float, Float>>()?.run {
            val widthFactor = first
            val heightFactor = second
            val width = (MeasureSpec.getSize(widthMeasureSpec) * widthFactor).toInt()
            val height = (MeasureSpec.getSize(heightMeasureSpec) * heightFactor).toInt()
            applyMeasure(width, height, widthMeasureSpec, heightMeasureSpec)
        } ?: cabinet.remove("aspectRatio")?.safeAs<Float>()?.run {
            if (heightExactly) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            } else {
                //高:宽
                //先计算 宽 以宽为准
                val width = MeasureSpec.getSize(heightMeasureSpec)
                val height = (width * this).toInt()
                applyMeasure(width, height, widthMeasureSpec, heightMeasureSpec)
            }
        } ?: super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    @SuppressLint("WrongCall")
    private fun applyMeasure(width: Int, height: Int, widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (width == 0 && height == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } else if (width == 0) {
            layoutParams.height = height
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        } else if (height == 0) {
            layoutParams.width = width
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
        } else {
            //<editor-fold desc="必须这里设置layoutParams的宽和高 否则background宽高无效会全屏">
            layoutParams.width = width
            layoutParams.height = height
            //</editor-fold>
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        size.right = w.toFloat()
        size.bottom = h.toFloat()
    }

    //如果 子view 消费事件 一定会走dispatchTouchEvent
    //如果 自己 消费事件 一定会走dispatchTouchEvent 只有down会走 onInterceptTouchEvent
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
//        println(" dispatchTouchEvent > $ev")
        return dispatchTouchEvent?.invoke(ev) {
            super.dispatchTouchEvent(ev)
        } ?: super.dispatchTouchEvent(ev)
    }

    //如果 子view 消费事件 一定会走onInterceptTouchEvent
    //如果 自己 消费事件 只有down事件 会走onInterceptTouchEvent
    //如果 onInterceptTouchEvent 在down事件的时候返回true表示自己要消费事件
    // 后续事件直接从dispatchTouchEvent传入onTouchEvent
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
//        println(" onInterceptTouchEvent > $ev")
        return onInterceptTouchEvent?.invoke(ev) {
            super.onInterceptTouchEvent(ev)
        } ?: super.onInterceptTouchEvent(ev)
    }

    //如果自己消费事件 所有事件都会走 onTouchEvent
    //如果 子view 消费事件 所有事件都会 不走 onTouchEvent
    override fun onTouchEvent(event: MotionEvent): Boolean {
//        println(" onTouchEvent > $event")
        return onTouchEvent?.invoke(event) {
            super.onTouchEvent(event)
        } ?: super.onTouchEvent(event)
    }

//    override fun draw(canvas: Canvas?) {
//        super.draw(canvas)
//    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        drawIntoCanvas(canvas)
    }


    override fun draw(canvas: Canvas) {
//        postInvalidate 在有background的时候会触发 draw
//        postInvalidate 在mei有background的时候bu会触发 draw 直接dispatchDraw
        if (background == null) {
            super.draw(canvas)
            return
        }
        dispatchDraw.invoke(canvas) { ncanvas ->
            super.draw(ncanvas)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (background != null) {
            super.dispatchDraw(canvas)
            return
        }
        dispatchDraw.invoke(canvas) { ncanvas ->
            super.dispatchDraw(ncanvas)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        drawTouchModifiers?.forEach {
            it.attachToWindow(this)
        }
        attachToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        drawTouchModifiers?.forEach {
            it.detachedFromWindow(this)
        }
        detachedFromWindow()
        animatorsCancel()
        clear()
    }
}
//</editor-fold>

fun <T : View> T.checkId(idset: Int = NO_ID): T {
    if (id == NO_ID) {
        id = if (idset == NO_ID) generateViewId() else idset
    }
    return this
}


/**
 * 储物柜
 */
interface Locker {
    fun <T : Any> retrieve(key: String = "key", value: (() -> T)? = null): T?

}

fun Locker.view(): View = retrieve("view")!!

fun Locker.keepView(view: View) = retrieve("view") { view }

fun Locker.animatorsCancel() =
    retrieve<MutableMap<String, ValueAnimator>>("animator")?.values?.forEach {
        it.removeAllUpdateListeners()
        it.cancel()
    }

fun Locker.clear() = retrieve<Any>("")

fun Locker.animator(key: String, animator: (() -> ValueAnimator)? = null): ValueAnimator =
    findAnimator(key) ?: retrieve("animator") {
        mutableMapOf<String, ValueAnimator>()
    }!!.let {
        it[key] ?: animator!!.invoke().apply { it[key] = this }
    }

fun Locker.findValue(key: String): ValueAnimator? {
    fun parentFind(locker: Locker, key: String): ValueAnimator? {
        val animator = retrieve<ValueAnimator>(key)
        if (animator != null) {
            return animator
        }
        if (this is View) {
            val parent = this.parent
            if (parent is Locker) {
                return parentFind(parent, key)
            } else {
                return null
            }
        }
        return null
    }
    return parentFind(this, key)
}

fun <T : Any> Locker.find(key: String): T? {
    fun <T : Any> parentFind(locker: Locker, key: String): T? {
        val value = retrieve<T>(key)
        if (value != null) {
            return value
        }
        if (this is View) {
            val parent = this.parent
            if (parent is Locker) {
                return parentFind(parent, key)
            } else {
                return null
            }
        }
        return null
    }
    return parentFind(this, key)
}

fun Locker.findAnimator(key: String): ValueAnimator? {
    fun parentFindAnimator(locker: Locker, key: String): ValueAnimator? {
        val value = locker.retrieve<MutableMap<String, ValueAnimator>>("animator")?.get(key)
        if (value != null) {
            return value
        }
        if (locker is View) {
            val parent = locker.parent
            if (parent is Locker) {
                return parentFindAnimator(parent, key)
            } else {
                return null
            }
        }
        return null
    }
    return parentFindAnimator(this, key)
}

class MapLocker : Locker {
    private val lockerMap: MutableMap<String, Any> = mutableMapOf()
    override fun <T : Any> retrieve(key: String, value: (() -> T)?): T? {
        if (key.isEmpty()) {
            lockerMap.clear()
            return null
        }
        return try {
            (lockerMap[key] ?: value?.invoke()?.apply { lockerMap[key] = this }) as? T
        } catch (e: Exception) {
            null
        }
    }
}

fun View.topLayer(onAttach: FrameLayout.(AppCompatActivity) -> Unit) {
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            //这里开始已经可以获取宽高了
            (context as? AppCompatActivity)?.apply {
                onAttach(window.decorView.findViewById(android.R.id.content), this)
            }

        }

        override fun onViewDetachedFromWindow(v: View) {
            removeOnAttachStateChangeListener(this)
        }
    })

}

fun View.topLayerTip(more: ((TextView) -> Unit)? = null): MutableLiveData<String> {
    val source = MutableLiveData<String>()
    topLayer {
        text(width = -1, height = -1) {
            textSize = 16f
            source.observe(it) {
                text = it
            }
            more?.invoke(this)
        }
    }
    return source
}