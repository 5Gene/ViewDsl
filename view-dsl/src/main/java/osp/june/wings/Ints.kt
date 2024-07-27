package osp.june.wings

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import androidx.appcompat.content.res.AppCompatResources

context(Context)
fun Int.getString() = getText(this)

context(Context)
fun Int.getColor() = getColor(this)

context(Context)
@SuppressLint("UseCompatLoadingForDrawables")
fun Int.getDrawable() = getDrawable(this)

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

val Number.todp: Int
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), Resources.getSystem().displayMetrics
    ).toInt()


fun Number.ceilToInt() = kotlin.math.ceil(this.toDouble()).toInt()

context(View)
val Number.todp: Int
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), context.resources.displayMetrics
    ).toInt()

val Number.todpf: Float
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), Resources.getSystem().displayMetrics
    )

context(View)
val Number.todpf: Float
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), context.resources.displayMetrics
    )

val Number.tosp: Float
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), Resources.getSystem().displayMetrics
    )
