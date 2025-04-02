@file:Suppress("UNCHECKED_CAST")

package osp.spark.view.dsl.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference
import androidx.preference.SwitchPreferenceCompat
import osp.spark.view.dsl.R
import osp.spark.view.dsl.ViewDslScope
import osp.spark.view.wings.isNotNullOrEmpty

//https://developer.android.google.cn/develop/ui/views/components/settings?hl=zh-cn

//val preferences = PreferenceManager.getDefaultSharedPreferences(this).all
//preferences.forEach {
//    Log.d("Preferences", "${it.key} -> ${it.value}")
//}

//<!-- Default style for PreferenceScreen. -->
//<attr name="preferenceScreenStyle" format="reference" />
//<!-- Default style for the PreferenceActivity. -->
//<attr name="preferenceActivityStyle" format="reference" />
//<!-- Default style for Headers pane in PreferenceActivity. -->
//<attr name="preferenceFragmentStyle" format="reference" />
//<!-- Preference fragment list style -->
//<attr name="preferenceFragmentListStyle" format="reference" />

//    <style name="BasePreferenceThemeOverlay">
//          这配置了fragment的布局
//        <item name="preferenceFragmentListStyle">@style/PreferenceFragmentList.Material</item>
//        <item name="preferenceFragmentCompatStyle">@style/PreferenceFragment.Material</item>
//        <item name="preferenceFragmentStyle">@style/PreferenceFragment.Material</item>

//        <item name="android:scrollbars">vertical</item>
//        <item name="checkBoxPreferenceStyle">@style/Preference.CheckBoxPreference.Material</item>
//        <item name="dialogPreferenceStyle">@style/Preference.DialogPreference.Material</item>
//        <item name="dropdownPreferenceStyle">@style/Preference.DropDown.Material</item>
//        <item name="editTextPreferenceStyle">@style/Preference.DialogPreference.EditTextPreference.Material</item>
//        <item name="preferenceCategoryStyle">@style/Preference.Category.Material</item>
//        <item name="preferenceScreenStyle">@style/Preference.PreferenceScreen.Material</item>
//        <item name="preferenceStyle">@style/Preference.Material</item>
//        <item name="seekBarPreferenceStyle">@style/Preference.SeekBarPreference.Material</item>
//        <item name="switchPreferenceCompatStyle">@style/Preference.SwitchPreferenceCompat.Material</item>
//        <item name="switchPreferenceStyle">@style/Preference.SwitchPreference.Material</item>
//        <item name="preferenceCategoryTitleTextAppearance">@style/TextAppearance.AppCompat.Body2</item>
//    </style>


fun <T : Preference> PreferenceGroup.addPreference(
    key: String? = null,
    title: Any? = null,
    summary: Any? = null,
    iconRes: Int? = null,
    preference: T,
    content: @ViewDslScope() (T.() -> Unit)? = null
) {
    addPreference(preference.apply {
        setKey(key)
        isPersistent = !key.isNotNullOrEmpty()
        iconRes?.let {
            setIcon(it)
        }
        title?.let {
            if (it is Int) {
                setTitle(it)
            } else if (it is CharSequence) {
                setTitle(it)
            } else {
                setTitle(it.toString())
            }
        }
        summary?.let {
            if (it is Int) {
                setSummary(it)
            } else if (it is CharSequence) {
                setSummary(it)
            } else {
                setSummary(it.toString())
            }
        }
//        content?.invoke(this)
    })
    //对于PreferenceCategory必须放后面,先把category添加进screen
    //对于其他,最好放后面,那么代码块中可以同步拿到当前值
    content?.invoke(preference)
}

//@ViewDslScope PreferenceScreen.() -> Unit, 限定PreferenceScreen作用域只在次内部方法,内内部无法访问PreferenceScreen对象
inline fun PreferenceFragmentCompat.screen(content: @ViewDslScope PreferenceScreen.() -> Unit) {
    preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
    preferenceScreen.content()
}

//PreferenceCategory并不是类似ViewGroup包裹内部preference
//而是在PreferenceFragmentCompat中有个RecycleView统一加载所有preference,PreferenceCategory只是其中一个item显示小标题而已
fun PreferenceScreen.category(title: Any? = null, content: (@ViewDslScope PreferenceCategory.() -> Unit)? = null) {
//    val category = PreferenceCategory(context, null)
//    addPreference(category)//必须先加进去否则会报错
//    category.content()
    addPreference(key = "", title = title, preference = PreferenceCategory(context, null), content = content)
}

fun PreferenceGroup.linearLayout(once: Boolean = true, content: (@ViewDslScope LinearLayout.() -> Unit)? = null) {
    layout(R.layout.preference_layout_dsl, once, content)
}

/**
 * PreferenceCategory和PreferenceScreen都可引用
 */
fun <T> PreferenceGroup.layout(layout: Int, once: Boolean = true, content: (@ViewDslScope T.() -> Unit)? = null) {
    addPreference(LayoutPreference<T>(context).apply {
        layoutResource = layout
        this.content = content
        this.once = once
    })
}

fun PreferenceGroup.layout(layout: Int, content: (@ViewDslScope Preference.() -> Unit)? = null) {
    addPreference(Preference(context).apply {
        layoutResource = layout
        content?.invoke(this)
    })
}

private class LayoutPreference<T>(context: Context) : Preference(context) {
    var content: (T.() -> Unit)? = {}
    var once = true

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        content?.invoke((holder.itemView as T))
        if (once) {
            content = null
        }
    }
}


//直接调用构造函数：几乎没有额外的开销，非常高效。
//反射调用构造函数：有显著的开销，特别是当需要频繁调用时，性能差异会变得更加明显。
//通过反射调用构造函数, 这种方式使用反射来查找和调用构造函数。反射的开销主要在于：
// - 查找构造函数：使用反射查找构造函数 (getConstructor) 会有一定的性能开销。
// - 调用构造函数：通过反射调用构造函数 (newInstance) 也是较慢的操作，因为它涉及更多的底层操作和安全检查。
@Deprecated("直接调用构造函数：几乎没有额外的开销，非常高效。\n反射调用构造函数：有显著的开销，特别是当需要频繁调用时，性能差异会变得更加明显。")
inline fun <reified T : Preference> PreferenceGroup.addPreference(
    key: String,
    title: Any? = null,
    summary: Any? = null,
    iconRes: Int? = null,
    noinline content: (T.() -> Unit)? = null
) {
    val preference = T::class.java.getConstructor(Context::class.java, AttributeSet::class.java).newInstance(context, null)
    addPreference(key, title, summary, iconRes, preference, content)
}


/**
 * ## 自定义switchCompat样式
 *  - 只要主题里面配置switchPreferenceCompatStyle,设置为自定义style即可,
 *  - 自定义style继承Preference.SwitchPreference.Material
 * ```xml
 * <item name="switchPreferenceStyle">@style/Preference.SwitchPreference.Material</item>
 * ```
 * 如果key所在的preference没设置或者状态是off,那么此preference会被disable
 * ```
 * dependency = "key"
 * ```
 */
fun PreferenceGroup.switch(
    key: String,
    title: Any? = null,
    summary: Any? = null,
    iconRes: Int? = null,
    content: @ViewDslScope (SwitchPreference.() -> Unit)? = null
) {
    addPreference(key, title, summary, iconRes, SwitchPreference(context, null), content)
}

/**
 * ## 自定义switchCompat样式
 *  - 只要主题里面配置switchPreferenceCompatStyle,设置为自定义style即可,
 *  - 自定义style继承Preference.SwitchPreferenceCompat.Material
 * ```xml
 * <item name="switchPreferenceCompatStyle">@style/Preference.SwitchPreferenceCompat.Material</item>
 * ```
 */
fun PreferenceGroup.switchCompat(
    key: String,
    title: Any? = null,
    summary: Any? = null,
    iconRes: Int? = null,
    content: @ViewDslScope (SwitchPreferenceCompat.() -> Unit)? = null
) {
    addPreference(key, title, summary, iconRes, SwitchPreferenceCompat(context, null), content)
}


fun PreferenceGroup.seekBar(
    key: String,
    title: Any? = null,
    summary: Any? = null,
    iconRes: Int? = null,
    content: @ViewDslScope (SeekBarPreference.() -> Unit)? = null
) {
    addPreference(key, title, summary, iconRes, SeekBarPreference(context, null), content)
}

fun PreferenceGroup.checkBox(
    key: String,
    title: Any? = null,
    summary: Any? = null,
    iconRes: Int? = null,
    content: @ViewDslScope (CheckBoxPreference.() -> Unit)? = null
) {
    addPreference(key, title, summary, iconRes, CheckBoxPreference(context, null), content)
}

fun PreferenceGroup.singleSelect(
    key: String,
    title: Any? = null,
    summary: Any? = null,
    iconRes: Int? = null,
    content: @ViewDslScope (ListPreference.() -> Unit)? = null
) {
    addPreference(key, title, summary, iconRes, ListPreference(context, null), content)
}

fun PreferenceGroup.multiSelect(
    key: String,
    title: Any? = null,
    summary: Any? = null,
    iconRes: Int? = null,
    content: @ViewDslScope (MultiSelectListPreference.() -> Unit)? = null
) {
    addPreference(key, title, summary, iconRes, MultiSelectListPreference(context, null), content)
}

fun PreferenceGroup.editText(
    key: String,
    title: Any? = null,
    summary: Any? = null,
    iconRes: Int? = null,
    content: @ViewDslScope (EditTextPreference.() -> Unit)? = null
) {
    addPreference(key, title, summary, iconRes, EditTextPreference(context, null), content)
}

fun PreferenceGroup.seekbar(
    key: String,
    title: Any? = null,
    summary: Any? = null,
    iconRes: Int? = null,
    content: @ViewDslScope (SeekBarPreference.() -> Unit)? = null
) {
    addPreference(key, title, summary, iconRes, SeekBarPreference(context, null), content)
}

fun Preference.onClick(click: () -> Unit) {
    setOnPreferenceClickListener { _ ->
        click()
        true
    }
}

interface PrefWidget {
    fun PreferenceGroup.content()
}

class PrefCategory(val title: String? = null, val widgets: List<PrefWidget>)

fun PreferenceFragmentCompat.buildScreen(categories: List<PrefCategory>) {
    screen {
        categories.forEach { category ->
            category {
                category.title?.let {
                    title = it
                }
                category.widgets.forEach { widget ->
                    widget.apply {
                        this@category.content()
                    }
                }
            }
        }
    }
}


private class UrlPreference(context: Context) : Preference(context) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val imageView = holder.findViewById(android.R.id.icon) as ImageView
        //显示地址图片
    }
}

