package osp.spark.view.wings

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.EmbossMaskFilter
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.MaskFilterSpan
import android.text.style.QuoteSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TextAppearanceSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import osp.sparkj.cartoon.wings.todp

/**
 * https://segmentfault.com/a/1190000006186341
 *
 * https://juejin.cn/post/6844903458252800008
 *
 * # SpannableStringBuilderKt
 * ### ```androidx.core:core-ktx```里面有很多扩展方法
 * ```
 * buildSpannedString {
 *     bold {
 *         append("bold str")
 *     }
 *     italic {
 *         append("italic str")
 *     }
 *     underline {
 *         append("underline str")
 *     }
 *     color {
 *         append("color str")
 *     }
 *     backgroundColor {
 *         append("backgroundColor str")
 *     }
 *     strikeThrough {
 *         append("strikeThrough str")
 *     }
 *     subscript {
 *         append("subscript str")
 *     }
 * }
 * ```
 */


fun spannableString(text: CharSequence = "", build: SpannableStringBuilder.() -> Unit) = SpannableStringBuilder().apply(build)

fun SpannableStringBuilder.span(regex: String, vararg style: Any?) {
    regex.toRegex().findAll(this).forEach { result ->
        style.toList().filterNotNull().forEach {
            setSpan(it, result.range.first, result.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

private fun SpannableStringBuilder.addSpan(text: String, vararg style: Any?) {
    val start = length
    append(text)
    val end = length
    style.filterNotNull().forEach {
        setSpan(it, start, end + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

fun SpannableStringBuilder.size(size: Int, text: String, other: Any? = null) {
    addSpan(text, AbsoluteSizeSpan(size.todp), other)
}

fun SpannableStringBuilder.color(color: Int, text: String, other: Any? = null) {
    addSpan(text, ForegroundColorSpan(color), other)
}

fun SpannableStringBuilder.background(color: Int, text: String, other: Any? = null) {
    addSpan(text, BackgroundColorSpan(color), other)
}

/**
 * 段落开头添加一个圆点
 */
fun SpannableStringBuilder.bullet(gapWidth: Int, color: Int, text: String, other: Any? = null) {
    addSpan(text, BulletSpan(gapWidth, color), other)
}

/**
 * 在段落前边添加一个竖直的引用线
 */
fun SpannableStringBuilder.quote(color: Int, text: String, other: Any? = null) {
    addSpan(text, QuoteSpan(color), other)
}

fun SpannableStringBuilder.superscript(text: String, other: Any? = null) {
    addSpan(text, SuperscriptSpan(), other)
}

fun SpannableStringBuilder.del(text: String, other: Any? = null) {
    addSpan(text, StrikethroughSpan(), other)
}

fun SpannableStringBuilder.underLine(text: String, other: Any? = null) {
    addSpan(text, UnderlineSpan(), other)
}

fun SpannableStringBuilder.subscript(text: String, other: Any? = null) {
    addSpan(text, SubscriptSpan(), other)
}

fun SpannableStringBuilder.bold(text: String, other: Any? = null) {
    addSpan(text, StyleSpan(Typeface.BOLD), other)
}

fun SpannableStringBuilder.image(context: Context, regex: String, @DrawableRes res: Int, other: Any? = null) {
    span(regex, ImageSpan(context, res), other)
}

fun SpannableStringBuilder.textAppearance(context: Context, appearance: Int, text: String, other: Any? = null) {
    addSpan(text, TextAppearanceSpan(context, appearance), other)
}

@RequiresApi(Build.VERSION_CODES.P)
fun SpannableStringBuilder.typeface(typeface: Typeface, text: String, other: Any? = null) {
    addSpan(text, TypefaceSpan(typeface), other)
}

fun SpannableStringBuilder.typeface(family: String, text: String, other: Any? = null) {
    addSpan(text, TypefaceSpan(family), other)
}

/**
 * ## 模糊
 */
fun SpannableStringBuilder.blur(radius: Float, style: BlurMaskFilter.Blur = BlurMaskFilter.Blur.NORMAL, text: String, other: Any? = null) {
    addSpan(text, MaskFilterSpan(BlurMaskFilter(radius, style)), other)
}

/**
 * ## 浮雕
 * - direction：float数组，定义长度为3的数组标量[x,y,z]，来指定光源的方向
 * - ambient：环境光亮度，0~1
 * - specular：镜面反射系数
 * - blurRadius：模糊半径，必须>0
 */
fun SpannableStringBuilder.emboss(
    direction: FloatArray = FloatArray(3) { 1F },
    ambient: Float = .4F,
    specular: Float = 6F,
    blurRadius: Float = 3.5F,
    text: String,
    other: Any? = null
) {
    addSpan(text, MaskFilterSpan(EmbossMaskFilter(direction, ambient, specular, blurRadius)), other)
}
