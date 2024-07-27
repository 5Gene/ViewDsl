package osp.june.dsl

import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import osp.june.wings.MutableDSLMap


inline fun <reified T> Any?.safeAs(): T? = this as? T

interface ViewModifier {

    fun VModifier.toKeyMap(): Map<String, MutableList<VModifier.Element>> {
        val result = mutableMapOf<String, MutableList<VModifier.Element>>()
        foldIn(null) { _, element ->
            val key = element::class.simpleName!!
            result[key]?.add(element) ?: mutableListOf<VModifier.Element>().apply {
                add(element)
                result[key] = this
            }
            null
        }
        return result
    }

    private fun vDrawModifierAllDraw(
        locker: Locker, canvas: Canvas, superDraw: (Canvas) -> Unit, iterator: Iterator<VDrawTouchModifier>
    ) {
        if (iterator.hasNext()) {
            val vDrawModifier = iterator.next()
            if (iterator.hasNext()) {
                vDrawModifier.draw(locker, canvas) {
                    vDrawModifierAllDraw(locker, canvas, superDraw, iterator)
                }
            } else {
                vDrawModifier.draw(locker, canvas, superDraw)
            }
        }
    }

    fun List<VDrawTouchModifier>.allDraw(locker: Locker, canvas: Canvas, superDraw: (Canvas) -> Unit) {
        vDrawModifierAllDraw(locker, canvas, superDraw, iterator())
    }

    private fun vTouchModifierAllTouch(
        locker: Locker, event: MotionEvent, superTouch: () -> Boolean, iterator: Iterator<VDrawTouchModifier>
    ): Boolean {
        val vDrawModifier = iterator.next()
        return if (iterator.hasNext()) {
            vDrawModifier.dispatchTouchEvent(locker, event) {
                vTouchModifierAllTouch(locker, event, superTouch, iterator)
            }
        } else {
            vDrawModifier.dispatchTouchEvent(locker, event, superTouch)
        }
    }

    fun List<VDrawTouchModifier>.allTouch(
        locker: Locker, event: MotionEvent, superTouch: () -> Boolean
    ): Boolean {
        return vTouchModifierAllTouch(locker, event, superTouch, iterator())
    }

    class VDrawTouchModifier(
        private val attachedHandler: ((Locker) -> Unit)? = null,
        private val detachedHandler: ((Locker) -> Unit)? = null,
        private val drawHandler: ((Locker, Canvas, (Canvas) -> Unit) -> Unit) = { _, canvas, superDraw ->
            superDraw(
                canvas
            )
        },
        private val touchHandler: ((Locker, MotionEvent, () -> Boolean) -> Boolean) = { _, _, superTouch -> superTouch() }
    ) : VModifier.Element {

        fun draw(locker: Locker, canvas: Canvas, superDraw: (Canvas) -> Unit) {
            drawHandler.invoke(locker, canvas, superDraw)
        }

        fun dispatchTouchEvent(locker: Locker, event: MotionEvent, superTouch: () -> Boolean): Boolean {
            return touchHandler.invoke(locker, event, superTouch)
        }

        fun attachToWindow(locker: Locker) {
            attachedHandler?.invoke(locker)
        }

        fun detachedFromWindow(locker: Locker) {
            detachedHandler?.invoke(locker)
        }
    }

    class VCustomizeModifier(private val action: View.() -> Unit) : VModifier.Element {
        fun attach(view: View) {
            view.action()
        }
    }

    class VExtraMapModifier(private val mapData: MutableDSLMap<String, Any>) : VModifier.Element {
        fun extra(): Map<String, Any> = mapData
    }
}

fun interval(begin: Float, end: Float, t: Float): Float {
    return ((t - begin) / (end - begin)).coerceIn(0.0F, 1.0F)
}