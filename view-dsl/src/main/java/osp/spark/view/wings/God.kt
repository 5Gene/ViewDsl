@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package osp.spark.view.wings

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityThread
import android.app.AppGlobals
import android.app.Application
import android.content.ComponentCallbacks
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.Toast
import androidx.annotation.StringRes
import java.util.*

/**
 * @author yun.
 * @date 2021/8/13
 * @des [一句话描述]
 * @since [https://github.com/ZuYun]
 * <p><a href="https://github.com/ZuYun">github</a>
 */

val processName: String by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Application.getProcessName()
    } else {
        //这个方法在android4.3.1上就已经有了
        ActivityThread.currentProcessName()
    }
}

val packageName: String by lazy {
    godContext.packageName
}

@SuppressLint("StaticFieldLeak")
var sTopActivity: Activity? = null

val godContext: Application by lazy {
    val application = AppGlobals.getInitialApplication() ?: ActivityThread.currentApplication()
    application?.also {
        it.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks by noOpDelegate<Application.ActivityLifecycleCallbacks>() {
            // A -> B
            //A.onPause() -> B.onCreate() -> B.onStart() -> B.onResume() -> A.onStop()
            // A <- B
            //B.onPause() -> A.onRestart() -> A.onStart() -> A.onResume() -> B.onStop()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                sTopActivity = activity
            }

            override fun onActivityStarted(activity: Activity) {
                sTopActivity = activity
            }

            override fun onActivityStopped(activity: Activity) {
                if (activity == sTopActivity) {
                    sTopActivity = null
                }
            }
        })
        it.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                "onConfigurationChanged >> $isPhoneState_".log()
            }

            override fun onLowMemory() {
                "onLowMemory >> ".log()
            }
        })
    }
    application
}

private val sResource: Resources by lazy {
    Resources.getSystem()
}

val isPhone: Boolean by lazy {
    val displayMetrics = sResource.displayMetrics
    val min = displayMetrics.widthPixels.coerceAtMost(displayMetrics.heightPixels)
    val max = displayMetrics.widthPixels.coerceAtLeast(displayMetrics.heightPixels)
    val proportion = min * 1F / max
    //0.75，0.66666，0.6，0.56，0.46  ，0.45
    proportion <= 0.75F
}

val isPhoneState: Boolean
    get() = isPhoneState_


private val isPhoneState_: Boolean
    get() {
        val displayMetrics = godContext.resources.displayMetrics
        val min = displayMetrics.widthPixels.coerceAtMost(displayMetrics.heightPixels)
        val max = displayMetrics.widthPixels.coerceAtLeast(displayMetrics.heightPixels)
        val proportion = min * 1F / max
        //0.75，0.66666，0.6，0.56，0.46  ，0.45
        return proportion <= 0.75F
    }


val mainHandler: Handler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    object : Handler(Looper.getMainLooper()) {
        var toastShowing = false
        val toast by lazy {
            Toast.makeText(godContext, "spark", Toast.LENGTH_SHORT).also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    it.addCallback(object : Toast.Callback() {
                        override fun onToastShown() {
                            toastShowing = true
                        }

                        override fun onToastHidden() {
                            toastShowing = false
                        }
                    })
                }
            }
        }

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what != 0) {
                return
            }
            if (msg.obj is CharSequence) {
                toast.setText(msg.obj as CharSequence)
            } else {
                toast.setText(msg.obj as Int)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                toastShowing = if (toast.view != null) {
                    toast.view!!.isShown
                } else {
                    false
                }
            }
            if (!toastShowing) {
                toast.show()
            }
        }
    }
}

fun toast(msg: CharSequence) {
    mainHandler.sendMessage(Message.obtain().also {
        it.obj = msg
    })
}

fun toast(@StringRes strInt: Int) {
    mainHandler.sendMessage(Message.obtain().also {
        it.obj = strInt
    })
}

fun runOnUiThread(action: Runnable) {
    mainHandler.post(action)
}

fun startActivity(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    godContext.startActivity(intent)
}