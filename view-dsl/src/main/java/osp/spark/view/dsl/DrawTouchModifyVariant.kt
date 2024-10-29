package osp.spark.view.dsl

import android.animation.ValueAnimator
import android.graphics.PointF
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import osp.spark.view.wings.safeAs
import kotlin.math.absoluteValue

const val ANI_RATIO_TOUCHY = "touchY"
const val ANI_RATIO_PRESS = "press"
const val TOUCH_SLOP = "touchSlop"
const val TOUCH_EVENT = "event"
const val TOUCH_EVENT_LAST = "event_last"
const val TOUCH_EVENT_MOVE_PX = "move_px"
const val TOUCH_STATE_DOWN = "event_state_down"

const val deftime = 666L
const val presstime = 111L

val attachAnimator: ((Locker, Boolean) -> Unit) = { locker, invalidate ->
    with(locker.view()) {
        locker.retrieve(TOUCH_SLOP) { RefValue(ViewConfiguration.get(context).scaledTouchSlop.toFloat()) }
        val touchY = locker.animator(ANI_RATIO_TOUCHY) {
            ValueAnimator.ofFloat(0F, 1F).apply {
                duration = deftime
                interpolator = AccelerateDecelerateInterpolator()
            }
        }
        if (invalidate) {
            touchY.addUpdateListener {
                postInvalidate()
            }
        }
        val press = locker.animator(ANI_RATIO_PRESS) {
            ValueAnimator.ofFloat(0F, 1F).apply {
                duration = presstime
            }
        }
        if (invalidate) {
            press.addUpdateListener {
                postInvalidate()
            }
        }
    }
}

val touchPressAnimator: ((Locker, MotionEvent, () -> Boolean) -> Boolean) = { locker, event, superTouch ->
    val view = locker.view()
    val width = view.width
    val height = view.height
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            with(locker.animator(ANI_RATIO_PRESS)) {
                pause()
                val point = locker.retrieve(TOUCH_EVENT) { PointF(0F, 0F) }!!
                point.x = event.x.coerceIn(0F, width.toFloat())
                point.y = event.y.coerceIn(0F, height.toFloat())
                start()
            }
        }
        MotionEvent.ACTION_MOVE -> {
            val point = locker.retrieve(TOUCH_EVENT) { PointF(0F, 0F) }!!
            point.x = event.x.coerceIn(0F, width.toFloat())
            point.y = event.y.coerceIn(0F, height.toFloat())
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            val point = locker.retrieve(TOUCH_EVENT) { PointF(0F, 0F) }!!
            point.x = event.x.coerceIn(0F, width.toFloat())
            point.y = event.y.coerceIn(0F, height.toFloat())
            println("=============== upup   ${locker.animator(ANI_RATIO_PRESS)}")
            locker.animator(ANI_RATIO_PRESS).reverse()
        }
    }
    superTouch()
    true
}

val touchYAnimator: ((Locker, MotionEvent, () -> Boolean) -> Boolean) = { locker, event, superTouch ->
    with(locker.animator(ANI_RATIO_TOUCHY)) {
        if (!isRunning) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val last = locker.retrieve(TOUCH_EVENT_LAST) { PointF(0F, 0F) }!!
                    last.y = event.y
                    last.x = event.x
                    locker.retrieve(TOUCH_EVENT_MOVE_PX) { RefValue(0F) }!!.valu = 0F
                }
                MotionEvent.ACTION_MOVE -> {
                    val last = locker.retrieve(TOUCH_EVENT_LAST) { PointF(0F, 0F) }!!
                    val down = event.y - last.y > 0
                    val touchSlop = locker.retrieve<RefValue<Float>>(TOUCH_SLOP)!!.valu
                    val offset = event.y - last.y
                    if (offset.absoluteValue < touchSlop / 12) {
                        last.y = event.y
                        last.x = event.x
                        return@with
                    }
                    if (down && (animatedValue.safeAs<Float>()!! > .9)) {
                        last.y = event.y
                        last.x = event.x
                        return@with
                    }
                    if (!down && (animatedValue.safeAs<Float>()!! < .1)) {
                        last.y = event.y
                        last.x = event.x
                        return@with
                    }
                    locker.retrieve(TOUCH_STATE_DOWN) { RefValue(down) }?.valu = down
                    val moveY = locker.retrieve(TOUCH_EVENT_MOVE_PX) { RefValue(0F) }!!
                    last.y = event.y
                    last.x = event.x
                    moveY.valu = moveY.valu + offset
                    val fl = moveY.valu / locker.view().height
                    if (fl > 0) {
                        setCurrentFraction(fl)
                    } else {
                        setCurrentFraction(1 + fl)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val value = animatedValue as Float
                    if (value == 1F || value == 0F) {
                        return@with
                    }
                    val down = locker.retrieve<RefValue<Boolean>>(TOUCH_STATE_DOWN)!!.valu
                    val last = locker.retrieve<PointF>(TOUCH_EVENT_LAST)!!
                    last.y = event.y
                    last.x = event.x
                    if (down) {
                        if (value > 0.18F) {
                            start()
                        } else {
                            reverse()
                        }
                    } else {
                        if (value < 0.82F) {
                            reverse()
                        } else {
                            start()
                        }
                    }
                }
            }
        }
    }
    superTouch()
    true
}