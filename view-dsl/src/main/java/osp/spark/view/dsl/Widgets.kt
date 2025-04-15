@file:Suppress("UNCHECKED_CAST")

package osp.spark.view.dsl

import android.animation.LayoutTransition
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.NO_ID
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.utils.widget.ImageFilterButton
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.contains
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import osp.spark.view.auxiliary.CanvasView
import osp.spark.view.auxiliary.LayoutConstraint
import osp.spark.view.wings.alpha
import osp.spark.view.wings.checkId
import osp.spark.view.wings.dpf

//限制使用最近的receiver
//对于有receiver的方法(A.()->Unit),限定作用域只在此方法内,方法内部的方法无法访问
@DslMarker
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
annotation class ViewDslScope

fun ViewGroup.addViewCheck(id: Int, child: View, width: Int, height: Int) {
    if (child.parent != null) {
        return
    }
    if (child.checkId(id).layoutParams != null) {
        addView(child, child.layoutParams)
    } else {
        addView(child, width, height)
    }
}

inline fun <V : View> ViewGroup.addViewCheck(
    id: Int,
    childSupplier: () -> V,
    width: Int,
    height: Int,
    config: V.() -> Unit,
): V {
    val child = findViewById<V>(id) ?: childSupplier()
    child.config()
    if (child.parent != null) {
        return child
    }
    if (child.checkId(id).layoutParams != null) {
        addView(child, child.layoutParams)
    } else {
        addView(child, width, height)
    }
    return child
}

operator fun ViewGroup.plus(child: View): ViewGroup {
    if (child in this) {
        return this
    }
    addViewCheck(child.id, child, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    //在addViewInner中
//    if (!checkLayoutParams(params)) {
//      这段代码会把ViewGroup.LayoutParams转化成对应布局的param比如LinearLayout的转为LinearLayout.LayoutParams
//        params = generateLayoutParams(params);
//    }
    return this
}

operator fun ViewGroup.minus(child: View): ViewGroup {
    removeView(child)
    return this
}

fun ViewGroup.animateLayoutChange(transition: LayoutTransition = LayoutTransition()) {
    layoutTransition = transition
}

fun View.linearLayoutParams(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    weight: Float = 0F,
    block: (@ViewDslScope LinearLayout.LayoutParams.() -> Unit)? = null
) {
    val params = LinearLayout.LayoutParams(width, height)
    params.weight = weight
    block?.invoke(params)
    layoutParams = params
}

fun View.frameLayoutParams(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    gravity: Int = FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY,
    block: (@ViewDslScope FrameLayout.LayoutParams.() -> Unit)? = null
) {
    val params = FrameLayout.LayoutParams(width, height)
    params.gravity = gravity
    block?.invoke(params)
    layoutParams = params
}

fun View.constLayoutParams(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    config: @ViewDslScope ConstraintLayout.LayoutParams.() -> Unit
) {
    layoutParams = ConstraintLayout.LayoutParams(width, height).apply(config)
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

inline fun ViewGroup.recycleView(
    width: Int = LayoutParams.WRAP_CONTENT,
    height: Int = LayoutParams.MATCH_PARENT,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope RecyclerView.() -> Unit
): RecyclerView {
    return return addViewCheck(id, {
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        recyclerView
    }, width, height, config)
}

inline fun ViewGroup.toolBar(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope Toolbar.() -> Unit
): Toolbar {
    return addViewCheck(id, {
        Toolbar(context)
    }, width, height, config)
}

inline fun ViewGroup.box(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope FrameLayout.() -> Unit
): FrameLayout {
    return addViewCheck(id, {
        FrameLayout(context)
    }, width, height, config)
}

inline fun ViewGroup.row(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope LinearLayout.() -> Unit
): LinearLayout {
    return linearlayout(width, height, LinearLayout.HORIZONTAL, id, config)
}

inline fun ViewGroup.column(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope LinearLayout.() -> Unit
): LinearLayout {
    return linearlayout(width, height, LinearLayout.VERTICAL, id, config)
}

inline fun ViewGroup.linearlayout(
    width: Int = LayoutParams.WRAP_CONTENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    orientation: Int = LinearLayout.HORIZONTAL,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope LinearLayout.() -> Unit
): LinearLayout {
    return addViewCheck(id, {
        val linearLayout = LinearLayout(context)
        linearLayout.orientation = orientation
        linearLayout
    }, width, height, config)
}

inline fun ViewGroup.icon(
    width: Int = LayoutParams.WRAP_CONTENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope ShapeableImageView.() -> Unit
): ImageView {
    //setShapeAppearanceModel()
    //setStroke...()
    return addViewCheck(id, { ShapeableImageView(context) }, width, height, config)
}

/**
 * altSrc	为 src 图像提供替代图像以允许交叉淡入淡出
 * saturation	设置图像的饱和度。0 = 灰度，1 = 原始图像，2 = 高饱和度
 * brightness	设置图像的亮度。0 = 黑色，1 = 原始，2 = 两倍亮
 * warmth	这将调整图像的外观色温。1 = 中性，2 = 暖色，.5 = 冷
 * contrast	  这将设置对比。1 = 不变，0 = 灰色，2 = 高对比度
 * crossfade	设置两个图像之间的当前混合。0=src 1= altSrc 图像
 * round       设置圆角大小
 * roundPercent 	将曲率的拐角半径设置为较小边的分数。对于方格 1 将产生一个圆
 * overlay	定义 alt 图像是在原始图像上淡入，还是与原始图像交叉淡化。默认值为 true。对于半透明对象，设置为 false
 */
inline fun ViewGroup.iconFilter(
    width: Int = LayoutParams.WRAP_CONTENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope ImageFilterView.() -> Unit
): ImageView {
    return addViewCheck(id, {
        val imageFilterView = ImageFilterView(context)
        imageFilterView.roundPercent = 0.5f
        imageFilterView.round = 50.dpf()
        imageFilterView.saturation = 1.0f //饱和度？
        imageFilterView.crossfade = 1.0f //渐变进度 0显示src,1显示alt
        //设置两张图片
        imageFilterView.setImageResource(0)
        imageFilterView.setAltImageResource(0)
        imageFilterView
    }, width, height, config)
}

inline fun ViewGroup.viewRaw(
    width: Int = LayoutParams.WRAP_CONTENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    supplier: @ViewDslScope () -> View
): View {
    return addViewCheck(id, supplier, width, height) {}
}

inline fun ViewGroup.text(
    width: Int = LayoutParams.WRAP_CONTENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope TextView.() -> Unit
): TextView {
    return addViewCheck(id, { TextView(context) }, width, height, config)
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
    return addViewCheck(id, { MaterialButton(context) }, width, height, config)
}

/**
 * altSrc	为 src 图像提供替代图像以允许交叉淡入淡出
 * saturation	设置图像的饱和度。0 = 灰度，1 = 原始图像，2 = 高饱和度
 * brightness	设置图像的亮度。0 = 黑色，1 = 原始，2 = 两倍亮
 * warmth	这将调整图像的外观色温。1 = 中性，2 = 暖色，.5 = 冷
 * contrast	  这将设置对比。1 = 不变，0 = 灰色，2 = 高对比度
 * crossfade	设置两个图像之间的当前混合。0=src 1= altSrc 图像
 * round       设置圆角大小
 * roundPercent 	将曲率的拐角半径设置为较小边的分数。对于方格 1 将产生一个圆
 * overlay	定义 alt 图像是在原始图像上淡入，还是与原始图像交叉淡化。默认值为 true。对于半透明对象，设置为 false
 */
inline fun ViewGroup.imgFilter(
    width: Int = LayoutParams.WRAP_CONTENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope ImageFilterButton.() -> Unit
): ImageButton {
    return addViewCheck(id, { ImageFilterButton(context) }, width, height, config)
}

fun ViewGroup.spacer(
    width: Int,
    height: Int,
    id: Int = NO_ID,
): View {
    return addViewCheck(id, { Space(context) }, width, height) {}
}

fun ViewGroup.spacer(
    size: Int = 1,
    id: Int = NO_ID,
    config: (@ViewDslScope Space.() -> Unit)? = null
): View {
    return addViewCheck(id, { Space(context) }, size, size, config ?: {})
}

fun ViewGroup.line(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.WRAP_CONTENT,
    color: Int = Color.BLACK.alpha(.04F),
    id: Int = NO_ID,
    config: (@ViewDslScope View.() -> Unit)? = null
): View {
    return addViewCheck(id, {
        val space = Space(context)
        space.setBackgroundColor(color)
        space
    }, width, height, config ?: {})
}

inline fun ViewGroup.canvas(
    width: Int,
    height: Int,
    id: Int = NO_ID,
    crossinline config: @ViewDslScope CanvasView.() -> Unit
): CanvasView {
    return addViewCheck(id, { CanvasView(context) }, width, height, config)
}

inline fun ViewGroup.vLayoutConstraint(
    width: Int = LayoutParams.MATCH_PARENT,
    height: Int = LayoutParams.MATCH_PARENT,
    modifier: VModifier = VModifier,
    id: Int = NO_ID,
    config: @ViewDslScope LayoutConstraint.() -> Unit
): ConstraintLayout {
    return addViewCheck(id, { LayoutConstraint(context = context, modifier) }, width, height, config)
}


fun View.background(config: @ViewDslScope GradientDrawable.() -> Unit) {
    background = GradientDrawable().apply(config)
}

/**
 * ```
 * backgroundSelector {
 *     //必须先设置 active
 *     active {
 *
 *     }
 *     default {
 *
 *     }
 * }
 * ```
 */
fun View.backgroundSelector(config: @ViewDslScope StateListDrawable.() -> Unit) {
    background = StateListDrawable().apply(config)
}

fun StateListDrawable.default(strict: Boolean = true, config: @ViewDslScope GradientDrawable.() -> Unit) {
    if (strict && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && stateCount == 0) {
        throw IllegalStateException("you should set active { } first, not default")
    }
    addState(
        intArrayOf(),
        GradientDrawable().apply(config)
    )
}

fun StateListDrawable.active(config: @ViewDslScope GradientDrawable.() -> Unit) {
    val gradientDrawable = GradientDrawable().apply(config)
//    addState(
//         这样不行，设置无效
//        intArrayOf(
//            android.R.attr.state_pressed,
//            android.R.attr.state_selected,
//            android.R.attr.state_checked
//        ),
//        gradientDrawable
//    )
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
fun View.shape(shadowColor: Int? = null, outline: (View, Outline) -> Unit) {
//        ViewOutlineProvider.BOUNDS
//        outline.setAlpha(0.0f); 可以设置透明度
    if (shadowColor != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        outlineSpotShadowColor = shadowColor
    }
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline(view, outline)
        }
    }
}

fun View.shapeRound(radius: Number = 1F, shadowColor: Int? = null, bgColor: Int? = null) {
    shape(shadowColor) { view, outline ->
        if (bgColor != null) {
            view.setBackgroundColor(bgColor)
        }
        val radiusValue = radius.toFloat()
        if (radiusValue > 1) {
            outline.setRoundRect(0, 0, view.width, view.height, radiusValue)
        } else {
            outline.setRoundRect(0, 0, view.width, view.height, view.height * radiusValue)
        }
    }
}


//MATCH_PARENT，那么MeasureSpec模式通常是 EXACTLY，因为父容器给出了一个明确的尺寸。
//WRAP_CONTENT，那么MeasureSpec模式通常是 AT_MOST，这意味着视图可以自行决定其大小，但不能超过某个最大值。
//比如自定义视图需要测量未指定大小的内容时，可能会遇到 UNSPECIFIED 模式。

//EXACTLY：当父容器对子视图提出了一个确切的尺寸要求时使用，对应于MATCH_PARENT或具体的dp值。此时子视图应当按照这个确切的尺寸进行布局。
//AT_MOST：允许子视图最多达到指定的尺寸。这通常与WRAP_CONTENT一起使用，意味着子视图可以自由决定自己的大小，但不能超过给定的最大值。
//UNSPECIFIED：父容器不对子视图的尺寸做任何限制，这种情况比较少见，通常出现在某些特殊场景下，如测量列表项的高度时。

//MATCH_PARENT 和 WRAP_CONTENT 决定了视图希望如何布局自身，
//EXACTLY、AT_MOST 和 UNSPECIFIED 则是系统用来传达具体布局约束的方式。


