package osp.spark.view.auxiliary

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.contains
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.findViewTreeLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import osp.spark.view.dsl.VModifier
import osp.spark.view.dsl.ViewCompose
import osp.spark.view.dsl.ViewModifier
import osp.spark.view.dsl.addViewCheck
import osp.spark.view.dsl.minus
import osp.spark.view.dsl.plus
import osp.spark.view.wings.focusOn
import osp.spark.view.wings.safeAs
import kotlin.coroutines.CoroutineContext


class ViewCoroutineScope(val view: View, override val coroutineContext: CoroutineContext) : CoroutineScope,
    View.OnAttachStateChangeListener {
    init {
        view.addOnAttachStateChangeListener(this)
    }

    override fun onViewAttachedToWindow(v: View) {

    }

    override fun onViewDetachedFromWindow(v: View) {
        coroutineContext.cancel()
        view.removeOnAttachStateChangeListener(this)
    }
}

//<editor-fold desc="CanvasView">
@SuppressLint("ViewConstructor")
class CanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Locker by MapLocker() {

    private val size = RectF(0F, 0f, 0f, 0F)
    var drawIntoCanvas: Canvas.() -> Unit = {}
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
        canvas.drawIntoCanvas()
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

    var attachToWindow: () -> Unit = {}
    var detachedFromWindow: () -> Unit = {}
    var drawIntoCanvas: (Canvas) -> Unit = {}
    private val size = RectF(0F, 0f, 0f, 0F)
    private val dispatchDraw: ((Canvas, (Canvas) -> Unit) -> Unit) = { canvas, superDraw ->
        drawTouchModifiers?.allDraw(this, canvas, superDraw) ?: superDraw(canvas)
    }
    private var dispatchTouchEvent: ((MotionEvent, () -> Boolean) -> Boolean)? = { event, superTouch ->
        drawTouchModifiers?.allTouch(this, event, superTouch) ?: false
    }
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

@JvmInline
value class _Tag(val view: View) {
    infix fun <B> Int.line(that: B): B = that.apply { view.setTag(this@line, that) }

    /**
     * 红线只牵一次，值只设置一次
     */
    inline infix fun <reified B> Int.redLine(that: B): B = view.getTag(this).safeAs<B>() ?: (this line that)
}


interface Group {

    operator fun View.unaryMinus() {
        (this.parent as ViewGroup).removeView(this)
    }

    operator fun View.unaryPlus(): LayoutParams {
        val viewGroup = this@Group as ViewGroup
        if (this in viewGroup) {
            return layoutParams
        }
        viewGroup.addViewCheck(id, this, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        //在addViewInner中
//    if (!checkLayoutParams(params)) {
//      这段代码会把ViewGroup.LayoutParams转化成对应布局的param比如LinearLayout的转为LinearLayout.LayoutParams
//        params = generateLayoutParams(params);
//    }
        return layoutParams
    }


    @Suppress("UNCHECKED_CAST")
    fun <R, T> LiveData<T>.focus(
        transform: T.() -> R = { this as R },
        observer: Observer<R>
    ) {
        val view = this@Group as View
        /**
         * 在ComponentActivity的setContent()方法中 会调用initViewTreeOwners() 之后才可以用findxxx方法
         * findxxx 不能在view的构造方法中使用
         */
        (view.findViewTreeLifecycleOwner() ?: view.context.safeAs<ComponentActivity>())?.apply {
            focusOn(transform).observe(this, observer)
        } ?: focusOn(transform).observeForever(observer)
    }
}

private class Demo @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), Group {
    init {
        val tv = TextView(context)
        -tv
        this - tv
        +tv
        this + tv
    }
}