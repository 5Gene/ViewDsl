package osp.spark.view.wings

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.TypedValue
import androidx.appcompat.content.res.AppCompatResources
import java.util.concurrent.TimeUnit

fun Int.vector2Bitmap(context: Context, block: ((Canvas) -> Unit)? = null): Bitmap {
    val drawable = AppCompatResources.getDrawable(context, this)!!
    val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    block?.invoke(canvas)
    return bitmap
}


//@FloatRange(from = 0.0, to = 1.0)
fun Int.alpha(alpha: Number) = Color.argb((alpha.toFloat() * 255).toInt(), Color.red(this), Color.green(this), Color.blue(this))

private val dpToPxIntCache = mutableMapOf<Float, Int>()
private val dpToPxFloatCache = mutableMapOf<Float, Float>()
private val spToPxFloatCache = mutableMapOf<Float, Float>()

fun Number.dp(context: Activity? = null): Int = dpToPxIntCache.getOrPut(this.toFloat()) {
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), (context?.resources ?: Resources.getSystem()).displayMetrics
    ).toInt()
}

fun Number.dpf(context: Activity? = null): Float = dpToPxFloatCache.getOrPut(this.toFloat()) {
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), (context?.resources ?: Resources.getSystem()).displayMetrics
    )
}

fun Number.sp(context: Activity? = null): Float = spToPxFloatCache.getOrPut(this.toFloat()) {
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, this.toFloat(), (context?.resources ?: Resources.getSystem()).displayMetrics
    )
}

fun Number.ceilToInt() = kotlin.math.ceil(this.toDouble()).toInt()

fun Long.sec2Day(): Long {
    return TimeUnit.SECONDS.toDays(this)
}

fun Long.mil2Minute(): Long {
    return TimeUnit.MILLISECONDS.toMinutes(this)
}
