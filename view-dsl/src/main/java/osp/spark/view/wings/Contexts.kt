package osp.spark.view.wings

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.content.res.Resources
import android.graphics.Rect
import android.os.PowerManager
import android.util.DisplayMetrics
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt


fun Context.isInPreview() {
    //com.android.layoutlib.bridge.android.BridgeContext
    this.javaClass.simpleName.contains("BridgeContext")
}

@SuppressLint("DiscouragedApi")
fun String.toResId(defType: String = "drawable", context: Context): Int {
    return context.resources.getIdentifier(this, defType, context.packageName)
}

fun String.toAttrId(context: Context): Int {
    return toResId(defType = "attr", context)
}

fun Int.toDrawable(context: Context) = context.findDrawable(this)

fun Context.findDrawable(@DrawableRes id: Int) = ContextCompat.getDrawable(this, id)

fun Context.findDrawable(draName: String) = ContextCompat.getDrawable(this, draName.toResId(context = this))

fun Int.toColor(context: Context) = context.findColor(this)

fun Context.findColor(@ColorRes id: Int) = ContextCompat.getColor(this, id)

fun Context.findColor(colorName: String) = ContextCompat.getColor(
    this,
    colorName.toResId("string", context = this)
)

//android.graphics.Color  //这里也有很多扩展
inline val String.toColor: Int
    get() = toColorInt()

fun String.toString(context: Context) {
    context.findString(this)
}

fun String.toDrawable(context: Context) {
    context.findDrawable(this)
}

fun Context.findString(@StringRes str: Int) = getString(str)


fun Int.toString(context: Context) = context.findString(this)

fun Context.findString(strName: String) = findString(findStringRes(strName))


fun Context.findStringRes(strName: String): Int = strName.toResId("string", this)


fun Context.findQuantity(@PluralsRes id: Int, quantity: Int, vararg args: Any?) {
    resources.getQuantityString(id, quantity, args)
}


/**
 * 唤醒手机屏幕并解锁
 */
@SuppressLint("InvalidWakeLockTag", "MissingPermission")
fun Context.wakeUpAndUnlock() {
    // 获取电源管理器对象
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    val screenOn = pm.isScreenOn
    if (!screenOn) {
        // 获取PowerManager.WakeLock对象,后面的参数|表示同时传入两个值,最后的是LogCat里用的Tag
        val wl = pm.newWakeLock(
            PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "bright"
        )
        wl.acquire(10000) // 点亮屏幕
        wl.release() // 释放
    }
    // 屏幕解锁
    val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
    val keyguardLock = keyguardManager.newKeyguardLock("unLock")
    // 屏幕锁定
    keyguardLock.disableKeyguard() // 解锁
}


val screenMetrics: DisplayMetrics by lazy {
    val resources: Resources = Resources.getSystem()
    resources.displayMetrics
}

val screen: Rect by lazy {
    val screenWidth = screenMetrics.widthPixels
    val screenHeight = screenMetrics.heightPixels
    Rect(0, 0, screenWidth, screenHeight)
}


//注释语法
// https://www.jianshu.com/p/54e8964730b4

//如果是A.isAssignableFrom(B)
//确定一个类(B)是不是继承来自于另一个父类(A)，一个接口(A)是不是实现了另外一个接口(B)，或者两个类相同。主要，这里比较的维度不是实例对象，而是类本身

//ktx 扩展函数
/**
 *
 *  <h>获取系统服务</h>
 * val cm: ClipboardManager? = getSystemService()
 *   public inline fun <reified T : Any> Context.getSystemService(): T? =
 *     ContextCompat.getSystemService(this, T::class.java)
 * 获取属性
 * public inline fun Context.withStyledAttributes(
 *     @StyleRes resourceId: Int,
 *     attrs: IntArray,
 *     block: TypedArray.() -> Unit
 * ) {
 *     obtainStyledAttributes(resourceId, attrs).apply(block).recycle()
 * }
 *
 *  <b>xxx.toUri、xxx.toFile扩展方法</b>
 *  String.toUri   File.toUri   Uri.toFile
 *
 */

//<editor-fold desc="ViewGroup.xxx相关扩展方法">
/**
 *   ViewGroup.xxx相关扩展方法
 *   public inline operator fun ViewGroup.contains(view: View): Boolean = indexOfChild(view) != -1
 *   val group = LinearLayout(this)
 *   val isContain = view in group
 *   @see androidx.core.view.ViewGroupKt.iterator
 *   for (child in group) {
 *       //执行操作
 *   }
 *   val child = group[1] //取第一个child
 *    group += child  //group.addView(child)
 *    group -= child  //group.removeView(child)
 */
//</editor-fold>

//<editor-fold desc="Span相关">
/**
 *  core-ktx库的SpannableStringBuilder扩展
 *  https://juejin.cn/post/7116920821150400519
 *  @see androidx.core.text.SpannableStringBuilderKt.buildSpannedString
 *  val build = buildSpannedString {
 *      backgroundColor(Color.RED) {
 *          append("遮天是")
 *          bold {
 *              append("一群人")
 *          }
 *          append("的完美，完美是一个人的遮天")
 *      }
 *  }
 *  @see androidx.core.text.SpannableStringKt.set(android.text.Spannable, int, int, java.lang.Object)
 *  val s = "Hello, World!".toSpannable()
 *  s[0, 5] = UnderlineSpan()  //给0-5的位置添加下划线
 *  s[0..5] = UnderlineSpan()  //给0-5的位置添加下划线
 *
 *  ReplacementSpan这个Span使用非常灵活，它提供了方法draw()可自定义绘制你想要的布局效果
 *
 * */
//</editor-fold>
