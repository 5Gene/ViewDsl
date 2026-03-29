package osp.spark.view.auxiliary

import android.R
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue

/**
 * 🎯 AttrsReuse（属性复用容器）
 *
 * 👉 核心目标：
 * - 复用系统 / AndroidX / Material 已定义的属性
 * - 一次解析 → 全量赋值 → 后续直接使用
 * - 统一属性解析入口（避免散落在各个 View 中）
 *
 * ---
 * 🧠 推荐使用方式（非常重要 ⭐）：
 *
 * val attrs = AttrsReuse()
 *      .fromTypedArray(context, attrs)   // ⭐ 主解析（必须）
 *      .fromTheme(context)              // ⭐ 补默认值（可选）
 *
 * 👉 Namespace 方式仅用于：
 * - 性能敏感场景
 * - 解析早期阶段（Inflater / 预解析）
 *
 * ---
 * 🧩 解析优先级（覆盖链）：
 *
 * XML（TypedArray） > XML（Namespace） > Theme
 */
class AttrsReuse {

    // =========================
    // 🌐 Namespace 常量
    // =========================
    private val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private val AUTO_NS = "http://schemas.android.com/apk/res-auto"

    // =========================
    // 📦 属性定义（统一管理）
    // =========================

    var enabled: Boolean = true
    var progress: Int = 0
    var secondaryProgress: Int = 0
    var max: Int = 100


    var text: CharSequence? = null
    var helperText: CharSequence? = null
    var subTitle: CharSequence? = null
    var hint: CharSequence? = null

    var elevation: Float = 0f

    var cornerRadius: Float = 0f
    var strokeWidth: Float = 0f
    var strokeColor: Int = 0

    var themeColors: ThemeColors? = null

    // =========================================================
    // 🔽 ① Namespace 方式（低级解析 🚀）
    // =========================================================

    /**
     * 🚀 从 AttributeSet 直接解析属性（低级 API）
     *
     * ---
     * ✅ 优点：
     * - 🚀 性能极高（无 TypedArray 分配）
     * - 可用于 LayoutInflater 阶段
     * - 无额外内存开销
     *
     * ❌ 缺点：
     * - ❌ 不支持 style / theme
     * - ❌ 不支持资源引用（如 @color / @dimen）
     * - ❌ app: 属性解析不可靠（可能得到 "@213xxx" 字符串）
     *
     * ⚠️ 坑 & 注意事项：
     * - getAttributeValue 返回原始字符串（不会解析资源）
     * - getAttributeIntValue 只能解析“纯数字”
     * - 如果 XML 使用 style/theme，此方法会失效
     * - 不建议单独作为最终解析方案
     *
     * ---
     * 📌 使用建议：
     * 👉 仅用于：
     * - 性能优化（提前读取简单值）
     * - 或作为 TypedArray 的 fallback
     *
     * 👉 ❌ 不推荐作为主解析方式
     */
    fun fromNamespace(attrs: AttributeSet?) = apply {
        if (attrs == null) return@apply

        enabled = attrs.getAttributeBooleanValue(ANDROID_NS, "enabled", true)
        progress = attrs.getIntMulti("progress", progress)
        secondaryProgress = attrs.getIntMulti("secondaryProgress", secondaryProgress)
        max = attrs.getIntMulti("max", max)

        text = attrs.getStringMulti("text")
        hint = attrs.getStringMulti("hint")
        subTitle = attrs.getStringMulti("subtitle")
        helperText = attrs.getStringMulti("helperText")


        // ⚠️ app 属性（仅兜底，不可靠）
        elevation = attrs.getFloatMulti("elevation", elevation)

        cornerRadius = attrs.getFloatMulti("cornerRadius", cornerRadius)
        strokeWidth = attrs.getFloatMulti("strokeWidth", strokeWidth)
        strokeColor = attrs.getColorMulti("strokeColor", strokeColor)
    }

    // =========================================================
    // 🔽 ② TypedArray（官方推荐 ⭐⭐⭐⭐⭐）
    // =========================================================

    /**
     * ⭐ 从 TypedArray 解析（官方标准方式，强烈推荐）
     *
     * ---
     * ✅ 优点：
     * - ⭐ 支持 XML + style + theme（完整解析链）
     * - ⭐ 自动解析资源引用（@color / @drawable / @dimen）
     * - ⭐ 与系统 View 行为完全一致
     * - ⭐ app: / android: 属性统一处理
     *
     * ❌ 缺点：
     * - 有少量对象创建开销（TypedArray）
     *
     * ⚠️ 坑 & 注意事项：
     * - ⚠️ attrIds 顺序必须与读取顺序完全一致
     * - ⚠️ 使用完必须调用 recycle()
     * - ⚠️ getXXX 类型必须匹配（否则可能 crash）
     *
     * ---
     * 📌 使用建议：
     * 👉 ⭐⭐⭐⭐⭐ 主解析方式（必须使用）
     * 👉 所有自定义 View 强烈建议统一走这个入口
     */
    fun fromTypedArray(context: Context, attrs: AttributeSet?) = apply {
        val attrIds = intArrayOf(
            R.attr.enabled,
            R.attr.progress,
            R.attr.secondaryProgress,
            R.attr.max,
            R.attr.text,
            R.attr.hint,

            androidx.appcompat.R.attr.subtitle,
            androidx.appcompat.R.attr.elevation,

            com.google.android.material.R.attr.helperText,
            com.google.android.material.R.attr.cornerRadius,
            com.google.android.material.R.attr.strokeWidth,
            com.google.android.material.R.attr.strokeColor
        )

        val ta: TypedArray = context.obtainStyledAttributes(attrs, attrIds)

        var i = 0
//        ta.hasValue(i)
        enabled = ta.getBoolean(i++, true)
        progress = ta.getInt(i++, progress)
        secondaryProgress = ta.getInt(i++, secondaryProgress)
        max = ta.getInt(i++, max)

        text = ta.getText(i++)
        hint = ta.getText(i++)

        subTitle = ta.getText(i++)
        elevation = ta.getDimension(i++, elevation)

        helperText = ta.getText(i++)
        cornerRadius = ta.getDimension(i++, cornerRadius)
        strokeWidth = ta.getDimension(i++, strokeWidth)
        strokeColor = ta.getColor(i++, strokeColor)

        ta.recycle()
    }

    // =========================================================
    // 🔽 ③ Theme 默认值（补充 🎨）
    // =========================================================

    /**
     * 🎨 从 Theme 中解析默认值（补充层）
     *
     * ---
     * ✅ 优点：
     * - 支持 ?attr/xxx
     * - 可获取主题默认配置（Material / AppCompat）
     *
     * ❌ 缺点：
     * - ❌ 无法读取 XML 中定义的属性
     * - ❌ 只能作为“补充层”
     *
     * ⚠️ 坑 & 注意事项：
     * - resolveAttribute 返回的是“最终值”（可能是资源或颜色值）
     * - 不能替代 TypedArray
     *
     * ---
     * 📌 使用建议：
     * 👉 放在最后调用（作为默认值补充）
     * 👉 只在属性未设置时生效（避免覆盖 XML）
     */
    fun readThemeColors(context: Context) = apply {
        val attrIds = intArrayOf(
            androidx.appcompat.R.attr.colorPrimary,
            R.attr.colorPrimary,
            com.google.android.material.R.attr.colorSecondary,
            R.attr.colorSecondary,
            com.google.android.material.R.attr.colorOnPrimary,
            com.google.android.material.R.attr.colorOnSecondary,
        )

        val typedArray = context.obtainStyledAttributes(attrIds)
        var i = 0
        fun getColor(): Int {
            val color = typedArray.getColor(i, Color.TRANSPARENT)
            if (color == Color.TRANSPARENT) {
                i++
                return getColor()
            }
            return color
        }

        themeColors = ThemeColors(
            getColor(),
            getColor(),
            getColor(),
            getColor(),
        )
        typedArray.recycle()
    }

    // =========================================================
    // 🔧 多 namespace 工具方法
    // =========================================================

    private fun AttributeSet.getIntMulti(name: String, def: Int): Int {
        val v1 = getAttributeIntValue(ANDROID_NS, name, Int.MIN_VALUE)
        if (v1 != Int.MIN_VALUE) return v1

        val v2 = getAttributeIntValue(AUTO_NS, name, Int.MIN_VALUE)
        if (v2 != Int.MIN_VALUE) return v2

        return def
    }

    private fun AttributeSet.getStringMulti(name: String): String? {
        return getAttributeValue(ANDROID_NS, name)
            ?: getAttributeValue(AUTO_NS, name)
    }

    private fun AttributeSet.getResMulti(name: String, def: Int): Int {
        val v1 = getAttributeResourceValue(ANDROID_NS, name, -1)
        if (v1 != -1) return v1

        val v2 = getAttributeResourceValue(AUTO_NS, name, -1)
        if (v2 != -1) return v2

        return def
    }

    private fun AttributeSet.getFloatMulti(name: String, def: Float): Float {
        val v = getStringMulti(name) ?: return def
        return v.toFloatOrNull() ?: def
    }

    private fun AttributeSet.getColorMulti(name: String, def: Int): Int {
        val v = getStringMulti(name) ?: return def
        return try {
            Color.parseColor(v)
        } catch (e: Exception) {
            def
        }
    }

    data class ThemeColors(val colorPrimary: Int, val colorSecondary: Int, val colorOnPrimary: Int, val colorOnSecondary: Int)
}

class ThemeAttrReader(private val context: Context) {

    private val tv = TypedValue()

    fun color(attr: Int): Int {
        context.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) {
            context.getColor(tv.resourceId)
        } else tv.data
    }

    fun dimen(attr: Int): Float {
        context.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) {
            context.resources.getDimension(tv.resourceId)
        } else {
            TypedValue.complexToDimension(
                tv.data,
                context.resources.displayMetrics
            )
        }
    }

    fun drawable(attr: Int): Drawable? {
        context.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) {
            context.getDrawable(tv.resourceId)
        } else null
    }

    fun resId(attr: Int): Int {
        context.theme.resolveAttribute(attr, tv, true)
        return tv.resourceId
    }

    fun string(attr: Int): String? {
        context.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) {
            context.getString(tv.resourceId)
        } else tv.string?.toString()
    }
}