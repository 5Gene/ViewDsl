package osp.spark.view.wings

import android.content.Context
import android.content.res.TypedArray
import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.withStyledAttributes
import com.google.android.material.color.MaterialColors

/**
 * ### Context.obtainStyledAttributes(set:AttributeSet, attrs: IntArray, defStyleAttr: Int, defStyleRes: Int)
 * **读取定义的attrs属性配置，先从xml读取，如果没有则看主题是否有配置默认样式，如果主题没配置那么使用固定样式**
 *
 * **`set > defStyleAttr > defStyleRes`**
 *
 * context.obtainStyledAttributes(attrs, R.styleable.custom_style, R.attr.custom_style_in_theme, R.style.custom_style_default)
 * - **AttributeSet**: `attrs`:
 *      - 代表布局文件中通过 XML 声明的属性集合。可以通过它访问在 XML 中定义的属性
 *      - 作用：如果布局文件中为视图配置了自定义属性，obtainStyledAttributes 会尝试从这里获取这些属性值
 * - **attrs: IntArray**: `R.styleable.custom_style`:
 *      - 是一个属性数组，通常在 attrs.xml 中定义，包含了自定义属性的集合
 *      - 作用：指定哪些属性需要从 AttributeSet 中获取。
 * - **defStyleAttr: Int**: `R.attr.custom_style_in_theme`:
 *      - 是一个“主题内的默认样式”标识符。允许主题中配置样式`<item name="custom_style_in_theme">@style/custom_style_in_theme_set</item>`
 *      - 作用：可以给不同主题配置不同样式，当从AttributeSet没取到custom_style相关属性的时候从主题找到主题中配置的样式，
 *
 * - **defStyleRes: Int**: `R.style.custom_style_default`:
 *      - 是一个样式资源 ID（R.style 中定义的样式），表示应用的默认样式，固定的样式。
 *      - 作用：布局xml没设置，主题没设置，则使用此默认样式，用于确保控件始终有样式，即便布局文件或主题中没有指定任何属性值
 **/

//https://stackoverflow.com/questions/17277618/get-color-value-programmatically-when-its-a-reference-theme
/**
 * - 作用：直接从主题中读取指定的属性值，而不查看布局文件的 AttributeSet
 * - 适用于仅需要从当前主题中获取某个样式值的场景，而不依赖于 XML 布局的自定义属性。
 *
 * TypedValue 是 Android 中用于表示不同类型的资源值的类。使用 TypedValue 时，需要根据资源的具体类型来选择合适的字段读取数据，例如 data、string、float 等字段。以下是具体的字段含义和用法：
 * - data：用于存储整数值，如颜色、布尔值、尺寸、引用 ID 等。它是一个万能字段，支持直接存储很多简单类型的值。
 * - string：用于存储字符串值。通常用于保存资源中的字符串值，或 XML 中的文本。
 * - float：用于存储浮点数值。一般用于尺寸类资源，如 dp、sp 等单位值。
 * - resourceId：用于存储资源 ID（R.drawable.someDrawable 等），通常用于引用图片、布局等资源。
 * - type：表示 TypedValue 的类型（如 TYPE_STRING, TYPE_DIMENSION, TYPE_INT_COLOR_RGB8 等），用来判断 TypedValue 当前存储的数据类型
 */
fun Context.getThemeAttrValue(@AttrRes attr: Int): TypedValue? {
    val typedValue = TypedValue()
    //如果 resolveRefs 为 true：
    //  资源引用将被递归解析，直到找到最终的资源值。这意味着 outValue 将包含最终的资源值，而不是引用。
    //如果 resolveRefs 为 false：
    //  资源引用不会被解析，outValue 可能会是一个 TYPE_REFERENCE 类型的值。也就是说，outValue 将直接包含资源引用，而不是引用指向的实际值。
    if (theme.resolveAttribute(attr, typedValue, true)) {
        return typedValue
    }
    //com.google.android.material.resources.MaterialAttributes,可参考使用方式
    return null
}

@ColorInt
fun Context.getThemeColor(@AttrRes attr: Int): Int? {
    return MaterialColors.getColorOrNull(this, attr)
}

@ColorInt
fun Context.getAttrColor(@AttrRes attr: Int): Int? {
    var attrColor = 0
    try {
        getAttrsValues(attr) {
            attrColor = it.getColor(0, -1)
        }
        return attrColor
    } catch (e: Exception) {
        e.printStackTrace()
        return getThemeAttrValue(attr)?.data
    }
}

fun Context.getAttrString(@AttrRes attr: Int): String? {
    var attrString: String? = null
    try {
        getAttrsValues(attr) {
            attrString = it.getString(0)
        }
        return attrString
    } catch (e: Exception) {
        e.printStackTrace()
        return getThemeAttrValue(attr)?.string?.toString()
    }
}

/**
 * - 首先读取布局文件中的属性值（即 AttributeSet 中的属性），如果布局文件中没有定义该属性，则会尝试从主题中获取默认值
 * - 适用场景：这种机制非常适用于自定义视图开发，因为它可以先从 XML 布局中读取指定的属性值，如果没有定义，则回退到主题中的默认属性，提供更灵活的配置方式
 *
 * ```
 * getAttrsValues(android.R.attr.textSize){
 *    val textSize = getDimension(0, 0F)
 * }
 * getAttrsValues(R.attr.colorPrimary, android.R.attr.textSize){
 *    val colorPrimary = getColor(0, 0)
 *    val textSize = getDimension(1, 0F)
 * }
 * ```
 * 都没取到会抛出Npe
 */
fun Context.getAttrsValues(@AttrRes vararg attrs: Int, reader: (TypedArray) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        obtainStyledAttributes(intArrayOf(*attrs)).use {
            reader(it)
        }
//        withStyledAttributes(0, intArrayOf(*attrs), reader)
    } else {
//        obtainStyledAttributes(intArrayOf(*attrs)).apply(reader).recycle()
        withStyledAttributes(0, intArrayOf(*attrs), reader)
//public inline fun Context.withStyledAttributes(
//    set: AttributeSet? = null,
//    attrs: IntArray,
//    @AttrRes defStyleAttr: Int = 0,
//    @StyleRes defStyleRes: Int = 0,
//    block: TypedArray.() -> Unit
//)
    }
}


fun Context.hasAttrValue(@AttrRes attr: Int): Boolean {
    return theme.resolveAttribute(attr, TypedValue(), false)
}