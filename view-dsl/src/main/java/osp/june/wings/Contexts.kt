package osp.june.wings

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


fun Context.isInPreview() {
    //com.android.layoutlib.bridge.android.BridgeContext
    this.javaClass.simpleName.contains("BridgeContext")
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

