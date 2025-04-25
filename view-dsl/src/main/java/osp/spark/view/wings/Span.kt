package osp.spark.view.wings

import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.EmbossMaskFilter
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
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
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.core.text.bold
import androidx.core.text.buildSpannedString

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

/**
 * ```
 * val spanned = SpanBuilder()
 *     .text("你好，", color = Color.BLACK)
 *     .text("点击查看用户协议", color = Color.BLUE, underline = true) {
 *         Log.d("Click", "用户协议点击")
 *     }
 *     .text("，感谢支持。", italic = true)
 *     .build()
 *
 * textView.text = spanned
 * textView.movementMethod = LinkMovementMethod.getInstance()
 * ```
 */
class SpanBuilder {

    private val builder = SpannableStringBuilder()

    fun build(): SpannableStringBuilder = builder

    fun text(
        content: String,
        color: Int? = null,
        sizeSp: Int? = null,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false,
        clickable: ((View, String) -> Unit)? = null,
        customSpans: List<CharacterStyle> = emptyList()
    ): SpanBuilder {
        val start = builder.length
        builder.append(content)
        val end = builder.length

        if (color != null) builder.setSpan(ForegroundColorSpan(color), start, end)
        if (sizeSp != null) builder.setSpan(AbsoluteSizeSpan(sizeSp, true), start, end)
        if (bold) builder.setSpan(StyleSpan(Typeface.BOLD), start, end)
        if (italic) builder.setSpan(StyleSpan(Typeface.ITALIC), start, end)
        if (underline) builder.setSpan(UnderlineSpan(), start, end)

        if (clickable != null) {
            builder.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    clickable(widget, content)
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false  // 交由 underline 控制
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        customSpans.forEach {
            builder.setSpan(it, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return this
    }

    private fun SpannableStringBuilder.setSpan(span: Any, start: Int, end: Int) {
        this.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

fun buildSpan(text: CharSequence? = null, build: SpannableStringBuilder.() -> Unit) = if (text == null) {
    SpannableStringBuilder().apply(build)
} else {
    SpannableStringBuilder(text).apply(build)
}

private fun SpannableStringBuilder.addSpan(text: String, vararg style: Any): SpannableStringBuilder {
    val start = length
    append(text)
    style.forEach {
        setSpan(it, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return this
}

fun SpannableStringBuilder.size(text: String, size: Int, vararg other: Any) = addSpan(text, AbsoluteSizeSpan(size.dp()), *other)

fun SpannableStringBuilder.color(text: String, color: Int, vararg other: Any) = addSpan(text, ForegroundColorSpan(color), *other)

fun SpannableStringBuilder.background(text: String, color: Int, vararg other: Any) = addSpan(text, BackgroundColorSpan(color), *other)

/**
 * 段落开头添加一个圆点
 */
fun SpannableStringBuilder.bullet(
    text: String,
    @ColorInt color: Int,
    gapWidth: Int = 2,
    @IntRange(from = 0) bulletRadius: Int = 4,
    vararg other: Any
) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        addSpan(text, BulletSpan(gapWidth, color, bulletRadius), *other)
    } else {
        addSpan(text, BulletSpan(gapWidth, color), *other)
    }

/**
 * 在段落前边添加一个竖直的引用线
 */
fun SpannableStringBuilder.quote(
    text: String, @ColorInt color: Int,
    @IntRange(from = 0) stripeWidth: Int = 2,
    @IntRange(from = 0) gapWidth: Int = 2,
    vararg other: Any
) {
    addSpan(text, QuoteSpan(color), *other)
}

fun SpannableStringBuilder.superscript(text: String, vararg other: Any) = addSpan(text, SuperscriptSpan(), *other)

fun SpannableStringBuilder.del(text: String, vararg other: Any) = addSpan(text, StrikethroughSpan(), *other)

fun SpannableStringBuilder.underLine(text: String, vararg other: Any) = addSpan(text, UnderlineSpan(), *other)

fun SpannableStringBuilder.subscript(text: String, vararg other: Any) = addSpan(text, SubscriptSpan(), *other)

fun SpannableStringBuilder.bold(text: String, vararg other: Any) = addSpan(text, StyleSpan(Typeface.BOLD), *other)

fun SpannableStringBuilder.image(text: String, @DrawableRes res: Int, vararg other: Any) =
    addSpan(text, ImageSpan(God.godContext, res), *other)

fun SpannableStringBuilder.textAppearance(text: String, appearance: Int, vararg other: Any) = addSpan(
    text, TextAppearanceSpan(God.godContext, appearance), *other
)

@RequiresApi(Build.VERSION_CODES.P)
fun SpannableStringBuilder.typeface(text: String, typeface: Typeface, vararg other: Any) = addSpan(text, TypefaceSpan(typeface), *other)

fun SpannableStringBuilder.typeface(text: String, family: String, vararg other: Any) = addSpan(text, TypefaceSpan(family), *other)

/**
 * ## 模糊
 */
fun SpannableStringBuilder.blur(
    text: String,
    radius: Float,
    style: BlurMaskFilter.Blur = BlurMaskFilter.Blur.NORMAL,
    vararg other: Any
) = addSpan(text, MaskFilterSpan(BlurMaskFilter(radius, style)), *other)

/**
 * ## 浮雕
 * - direction：float数组，定义长度为3的数组标量[x,y,z]，来指定光源的方向
 * - ambient：环境光亮度，0~1
 * - specular：镜面反射系数
 * - blurRadius：模糊半径，必须>0
 */
fun SpannableStringBuilder.emboss(
    text: String,
    direction: FloatArray = FloatArray(3) { 1F },
    ambient: Float = .4F,
    specular: Float = 6F,
    blurRadius: Float = 3.5F,
    vararg other: Any
) = addSpan(text, MaskFilterSpan(EmbossMaskFilter(direction, ambient, specular, blurRadius)), *other)

fun SpannableStringBuilder.click(
    text: String,
    color: Int,
    underlineText: Boolean = true,
    onClick: (View, String) -> Unit
) = click(text, {
    it.color = color
    it.isUnderlineText = underlineText
}, onClick)

fun SpannableStringBuilder.click(
    text: String,
    style: (TextPaint) -> Unit,
    onClick: (View, String) -> Unit
) = addSpan(text, object : ClickableSpan() {
    override fun onClick(widget: View) {
        onClick(widget, text)
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        style(ds)
    }
})

fun SpannableStringBuilder.spanByKey(regex: String, vararg style: Any?): SpannableStringBuilder {
    regex.toRegex().findAll(this).forEach { result ->
        style.forEach {
            setSpan(it, result.range.first, result.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return this
}

fun CharSequence.spanByKeys(keys: Map<String, Any>): CharSequence {
    return keys.entries.fold(SpannableStringBuilder(this)) { acc, (key, style) ->
        when (style) {
            is Collection<*> -> acc.spanByKey(key, *style.toTypedArray())
            is Array<*> -> acc.spanByKey(key, *style)
            else -> acc.spanByKey(key, style)
        }
    }
}

fun main() {
    buildSpan {
        size("dd", 33, ForegroundColorSpan(Color.RED))
            .color("", 0)
    }
    buildSpannedString {
        bold {
            append("bold str")
        }
    }
    "".spanByKeys(mapOf("" to arrayOf(ForegroundColorSpan(Color.RED), BackgroundColorSpan(Color.GRAY))))
}
