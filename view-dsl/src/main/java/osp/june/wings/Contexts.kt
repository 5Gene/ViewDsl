@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package osp.june.wings

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.content.res.Resources
import android.graphics.Rect
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat


fun Context.isInPreview() {
    //com.android.layoutlib.bridge.android.BridgeContext
    this.javaClass.simpleName.contains("BridgeContext")
}

// 定义自定义属性并在主题配置
// 1 attrs.xml 定义属性 <color name="theme_color_blue">#ff0071d3</color>
// 2 主题style.xml 中设置属性
//  <style name="BlueTheme" parent="@android:style/Theme.Black.NoTitleBar">
//    <item name="theme_color">#000fff</item>
//  </style>
//https://stackoverflow.com/questions/17277618/get-color-value-programmatically-when-its-a-reference-theme
fun Context.attrValueFromTheme(res: Int) {
    TypedValue().apply {
//        MaterialColors.getColor()
        theme.resolveAttribute(res, this, true)
    }.data
}

fun Context.getValueFromAttr() {
    val attrs = intArrayOf(android.R.attr.textSize)
//    解析 style
//    context.obtainStyledAttributes(布局中的attrs, 主题格式R.styleable.xxx, theme中配置的style, 默认样式 R.style.xxxx)
    val typedArray = this.obtainStyledAttributes(attrs)
    val dimension = typedArray.getDimension(0, 0F)
    typedArray.recycle()
}


//  public MultiStateLayout(Context context) {
//    super(context, null);
//  }
//
//  public MultiStateLayout(Context context, AttributeSet attrs) {
//    this(context, attrs, R.attr.jmultistate); //jmultistate主题里的属性
//  }
//
//  public MultiStateLayout(Context context, AttributeSet attrs, int defStyleAttr) {
//    super(context, attrs, defStyleAttr);
//    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MultiStateLayout, defStyleAttr, R.style.Jmultistate_style);
//    layout_error_resid = a.getResourceId(R.styleable.MultiStateLayout_error, View.NO_ID);
//    layout_loading_resid = a.getResourceId(R.styleable.MultiStateLayout_loading, View.NO_ID);
//    layout_empty_resid = a.getResourceId(R.styleable.MultiStateLayout_empty, View.NO_ID);
//    mLayoutState = a.getInt(R.styleable.MultiStateLayout_state, LayoutState.STATE_UNMODIFY);
//    a.recycle();
//  }


fun Int.toDrawable(context: Context) = context.findDrawable(this)

fun Context.findDrawable(@DrawableRes id: Int) = ContextCompat.getDrawable(this, id)

fun Context.findDrawable(draName: String) = ContextCompat.getDrawable(this, draName.toResId(context = this))


fun Int.toColor(context: Context) = context.findColor(this)

fun Context.findColor(@ColorRes id: Int) = ContextCompat.getColor(this, id)
fun Context.findColor(colorName: String) = ContextCompat.getColor(
    this,
    colorName.toResId("string", context = this)
)


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

