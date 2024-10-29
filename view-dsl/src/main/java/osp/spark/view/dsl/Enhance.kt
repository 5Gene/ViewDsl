package osp.spark.view.dsl

import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import osp.spark.view.wings.MutableDSLMap

internal data class RefValue<D>(var valu: D)

internal fun Float.set(check: Float.() -> Boolean = { this > 0 }, setValue: (Float) -> Unit) {
    if (check()) {
        setValue(this)
    }
}

fun View.transForm(
    rotateX: Float = 0F, rotateY: Float = 0F,
    translateX: Float = 0F, translateY: Float = 0F,
    nscaleX: Float = 1F, nscaleY: Float = 1F, locationZ: Float = 0F,
    widthFactor: Float = .5F, heightFactor: Float = .5F,
) {
    if (width == 0) {
        post {
            transForm(
                rotateX, rotateY, translateX, translateY, scaleX, scaleY, locationZ, widthFactor, heightFactor
            )
        }
        return
    }
    translateX.set {
        translationX = it
    }
    translateY.set {
        translationY = it
    }
    nscaleX.set({ this != 1F }) {
        this.scaleX = it
    }
    nscaleY.set({ this != 1F }) {
        this.scaleY = it
    }
//    https://stackoverflow.com/questions/24592731/android-flip-animation-not-flipping-smoothly
    locationZ.set({ this != 0F }) {
        cameraDistance = it
    }

    pivotX = width * widthFactor
    pivotY = height * heightFactor
    rotateX.set {
        rotationX = it
    }
    rotateY.set {
        rotationY = it
    }
}

interface ViewCompose : View3DModifier {

    fun VModifier.vSize(
        width: Number = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Number = ViewGroup.LayoutParams.WRAP_CONTENT
    ) = this.then(ViewModifier.VCustomizeModifier {
        layoutParams = ViewGroup.LayoutParams(width.toInt(), height.toInt())
    })

    fun VModifier.vSizeFactor(
        widthFactor: Number = 1F, heightFactor: Number = 1F
    ) = this.then(vExtra {
        "sizeFactor" put (widthFactor.toFloat() to heightFactor.toFloat())
    })

    /**
     * 宽：高
     */
    fun VModifier.vAspectRatio(
        aspectRatio: Number = 1F
    ) = this.then(vExtra {
        "aspectRatio" put aspectRatio.toFloat()
    })

    fun VModifier.vLayoutParam(param: ViewGroup.LayoutParams) = this.then(ViewModifier.VCustomizeModifier {
        layoutParams = param
    })

    fun VModifier.debug(color: Int = Color.parseColor("green")) = this.then(ViewModifier.VCustomizeModifier {
//        setBackgroundColor(Color.RED)
//        setBackgroundColor(Color.parseColor("red"))
        setBackgroundColor(color)
    })

    //Modifier.fillVector

    fun VModifier.vCustomize(customize: View.() -> Unit) = this.then(ViewModifier.VCustomizeModifier {
        this.customize()
    })

    fun VModifier.vExtra(mapData: MutableDSLMap<String, Any>.() -> Unit) = this.then(
        ViewModifier.VExtraMapModifier(MutableDSLMap<String, Any>().apply(mapData))
    )

    fun VModifier.vDraw(
        drawFront: ((Locker, Canvas) -> Unit)? = null, drawBehind: ((Locker, Canvas) -> Unit)? = null
    ) = this.then(ViewModifier.VDrawTouchModifier(drawHandler = { locker, canvas, superDraw ->
        drawBehind?.invoke(locker, canvas)
        superDraw(canvas)
        drawFront?.invoke(locker, canvas)
    }))
}
