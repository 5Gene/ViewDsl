package osp.spark.view.wings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Looper
import android.os.Parcelable
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.annotation.ColorRes
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

//照片选择器
//https://android-docs.cn/training/data-storage/shared/photopicker?hl=zh-cn#compose

fun Activity.runOnIdleHandler(once: Boolean = true, handler: () -> Unit) {
    Looper.getMainLooper().queue.addIdleHandler {
        handler()
        once
    }
}

//class IntentReader<V>(val translate: (String, Any?) -> V) : ReadOnlyProperty<Activity, V> {
//    override fun getValue(thisRef: Activity, property: KProperty<*>): V {
//        return translate(property.name, thisRef.intent?.extras?.get(property.name))
//    }
//}

class IntentReader<V>(val translate: (Any?) -> V) : ReadOnlyProperty<Activity, V> {

    override fun getValue(thisRef: Activity, property: KProperty<*>): V {
        val value = thisRef.intent?.extras?.get(property.name)
        if (value is Bundle) {
            val binder = value.getBinder(property.name)
            if (binder is BitmapBinder) {
                return translate(binder.safeAs<BitmapBinder>()?.bitmap)
            } else {
                throw java.lang.RuntimeException("no support for Bundle")
            }
        }
        return translate(value)
    }
}

/**
 * 不支持解析 Bundle
 *
 * ```
 * private val key_name by intent<String>("default value")
 *
 * ```
 */
inline fun <reified T : Any> Activity.intent(
    defaultValue: T
) = IntentReader {
    it?.safeAs<T>() ?: defaultValue
}

/**
 *
 * 不支持解析 Bundle
 *
 * ```
 * private val key_name by intent<String>()
 *
 * ```
 */
inline fun <reified T : Any> Activity.intent() = IntentReader {
    it?.safeAs<T>()
}

/**
 * Example:
 *
 * ```
 * context.startActivity<ProfileActivity>()
 * ```
 */
inline fun <reified T : Activity> Context.startActivity() {
    val intent = Intent(this, T::class.java)
    startActivity(intent)
}

/**
 * 感谢 Kotlin/anko
 *
 * Example：
 *
 * ```
 * context.startActivity<ProfileActivity>(
 *         key to "value",
 *         key1 to "vaue2"
 * )
 * ```
 */
inline fun <reified T : Activity> Context.startActivity(vararg params: Pair<String, Any>) {
    val intent = intent(T::class.java) {
        putAll(params)
    }
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

/**
 * Example：
 *
 * ```
 * context.startActivity<ProfileActivity> {
 *         key to "value"
 *         key1 to "vaue2"
 * }
 * ```
 */
inline fun <reified T : Any> Context.startActivity(params: MutableDSLMap<String, Any>.() -> Unit) {
    val intent = intent(T::class.java, params)
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

/**
 * Example：
 *
 * ```
 * context.startActivityForResult<ProfileActivity>(KEY_REQUEST_CODE,
 *         key to "value",
 *         key1 to "vaue2"
 * )
 * ```
 */
inline fun <reified T : Any> Context.startActivityForResult(
    requestCode: Int,
    vararg params: Pair<String, Any>
) {
    if (this is Activity) {
        val intent = intent(T::class.java) {
            putAll(params)
        }
        startActivityForResult(intent, requestCode)
    }
}

/**
 * Example：
 *
 * ```
 * context.startActivityForResult<ProfileActivity>(KEY_REQUEST_CODE) {
 *         key to "value"
 *         key1 to "vaue2"
 * }
 * ```
 */
inline fun <reified C : Class<*>> Context.startActivityForResult(
    requestCode: Int,
    params: MutableDSLMap<String, Any>.() -> Unit = {}
) {
    if (this is Activity) {
        val intent = intent<C>(params)
        startActivityForResult(intent, requestCode)
    }
}


/**
 * ```
 * intent {
 *     "key" to "value"
 *     "key2" to 666
 * }
 * ```
 */
inline fun Context.intent(
    targetClass: Class<*>,
    params: MutableDSLMap<String, Any>.() -> Unit
): Intent = Intent(this, targetClass).apply {
    MutableDSLMap<String, Any>().apply(params).forEach {
        inflate(it)
    }
}

inline fun <reified C : Class<*>> Context.intent(
    params: MutableDSLMap<String, Any>.() -> Unit
): Intent = Intent(this, C::class.java).apply {
    MutableDSLMap<String, Any>().apply(params).forEach {
        inflate(it)
    }
}

/**
 *  ```
 * intent("action") {
 *     "key" to "value"
 *     "key2" to 666
 * }
 *  ```
 */
fun intent(
    action: String,
    params: MutableDSLMap<String, Any>.() -> Unit
): Intent = Intent().apply {
    setAction(action)
    dslMapOf<String, Any>().apply(params).forEach {
        inflate(it)
    }
}

// 感谢 Kotlin/anko
fun Intent.inflate(it: Map.Entry<String, Any>) {
    val value = it.value
    val key = it.key
//    androidx.core.os.BundleKt.bundleOf(kotlin.Pair<java.lang.String,? extends java.lang.Object>...)
    when (value) {
        is Bitmap -> putExtra(key, Bundle().apply {
            putBinder(key, BitmapBinder(value))
        })
        // Scalars
        is Int -> putExtra(key, value)
        is Long -> putExtra(key, value)
        is String -> putExtra(key, value)
        is Float -> putExtra(key, value)
        is Double -> putExtra(key, value)
        is Char -> putExtra(key, value)
        is Short -> putExtra(key, value)
        is Boolean -> putExtra(key, value)
        // Scalar arrays
        is BooleanArray -> putExtra(key, value)
        is IntArray -> putExtra(key, value)
        is LongArray -> putExtra(key, value)
        is FloatArray -> putExtra(key, value)
        is DoubleArray -> putExtra(key, value)
        is CharArray -> putExtra(key, value)
        is ShortArray -> putExtra(key, value)
        // References
        is Bundle -> putExtra(key, value)
        is Parcelable -> putExtra(key, value)
        is CharSequence -> putExtra(key, value)
        is java.io.Serializable -> putExtra(key, value)
        else -> throw IllegalArgumentException("Intent extra $key has wrong type ${value.javaClass.name}")
    }
}
// 感谢 Kotlin/anko

class BitmapBinder(val bitmap: Bitmap) : Binder()

fun View.activity(): Activity? {
    var context = context
    while (context is ContextWrapper) {
        if (context is Activity) {
            return context
        }
        context = context.baseContext
    }
    return null
}


inline fun <reified T : Any> View.startActivity(params: MutableDSLMap<String, Any>.() -> Unit = {}) {
    activity()?.startActivity<T>(params)
}


fun Context.setStatusBarColor(@ColorRes colorResId: Int) {
    if (this is Activity) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = findColor(colorResId)
        WindowInsetsControllerCompat(window, window.decorView)
    }
}

var Activity.statusBarColor: Int
    get() = throw IllegalAccessException()
    set(value) {
        setStatusBarColor(value)
    }


//跳转到设置界面 对应设置项闪烁
//https://stackoverflow.com/questions/62979001/highlighting-a-menu-item-in-system-settings/63214655#63214655
private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"
private const val EXTRA_SYSTEM_ALERT_WINDOW = "system_alert_window"

fun askForOverlayPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    ).highlightSettingsTo(EXTRA_SYSTEM_ALERT_WINDOW)
    context.startActivity(intent)
}

private fun Intent.highlightSettingsTo(string: String): Intent {
    putExtra(EXTRA_FRAGMENT_ARG_KEY, string)
    val bundle = bundleOf(EXTRA_FRAGMENT_ARG_KEY to string)
    putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, bundle)
    return this
}