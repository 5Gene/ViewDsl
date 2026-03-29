package osp.spark.view.atelier.behaviors

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.IntDef
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.lang.ref.WeakReference

/**
 * 📺 仿 YouTube 播放器效果的 Behavior
 * 支持：全屏 (Expanded)、缩小到右下角 (Shrink)、向左/右滑动关闭 (ToLeft/ToRight)
 * 同时联动隐藏底部导航栏
 */
class YoutubeLikeBehavior<V : View>(
    context: Context, attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<V>(context, attrs) {

    companion object {
        @IntDef(
            STATE_DRAGGING,
            STATE_SETTLING,
            STATE_EXPANDED,
            STATE_SHRINK,
            STATE_TO_LEFT,
            STATE_TO_RIGHT,
            STATE_HIDDEN,
            STATE_SHRINK_DRAGGING
        )
        @Retention(AnnotationRetention.SOURCE)
        annotation class State

        const val STATE_DRAGGING = 1
        const val STATE_SETTLING = 2
        const val STATE_EXPANDED = 3
        const val STATE_SHRINK = 4
        const val STATE_TO_LEFT = 5
        const val STATE_TO_RIGHT = 6
        const val STATE_HIDDEN = 7
        const val STATE_SHRINK_DRAGGING = 8

        const val REMOVE_THRESHOLD = 90
        const val ANIMATION_DURATION = 300L

        const val SCROLL_UP = 1
        const val SCROLL_DOWN = -1

        @Suppress("UNCHECKED_CAST")
        fun <V : View> from(view: V?): YoutubeLikeBehavior<V>? {
            if (view == null) return null
            val params = view.layoutParams as? CoordinatorLayout.LayoutParams
                ?: throw IllegalArgumentException("The view is not a child of CoordinatorLayout")
            return params.behavior as? YoutubeLikeBehavior<V>
        }
    }

    var listener: OnBehaviorStateListener? = null
    private var velocityTracker: VelocityTracker? = null
    private var viewRef: WeakReference<View>? = null
    private val dragCallback = DragCallback()
    private var dragHelper: ViewDragHelper? = null

    @State
    var state = STATE_EXPANDED
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var ignoreEvents = false
    var draggable = true
    private var initialX = 0
    private var initialY = 0

    private var shrinkRate: Float = 0.5f
    private var marginBottom: Int = 0
    private var marginRight: Int = 0

    private var parentHeight = 0
    private var parentWidth = 0
    private var leftMargin = 0
    private var shrinkMarginTop = 0
    private var bottomNavigationHeight = 0

    private var animating = false
    private var animatingDirection = 0
    private var scrollOutAnimator: ObjectAnimator? = null
    private var scrollInAnimator: ObjectAnimator? = null

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        if (state != STATE_DRAGGING && state != STATE_SETTLING) {
            parent.onLayoutChild(child, layoutDirection)
        }

        // 查找底部导航栏
        for (i in 0 until parent.childCount) {
            val v = parent.getChildAt(i)
            if (v is BottomNavigationView) {
                bottomNavigationHeight = v.height
                break
            }
        }

        setupAnimators(child)

        parentHeight = parent.height - bottomNavigationHeight
        parentWidth = parent.width
        shrinkMarginTop = (parentHeight - (child.height / 2f * (1 + shrinkRate))).toInt() - marginBottom
        leftMargin = (child.width / 4f).toInt() - marginRight

        when (state) {
            STATE_EXPANDED -> ViewCompat.offsetTopAndBottom(child, 0)
            STATE_SHRINK -> {
                child.scaleX = shrinkRate
                child.scaleY = shrinkRate
                child.translationX = (leftMargin * 2f * (1f - shrinkRate))
                ViewCompat.offsetTopAndBottom(child, shrinkMarginTop)
            }

            else -> {}
        }

        if (dragHelper == null) {
            dragHelper = ViewDragHelper.create(parent, dragCallback)
        }
        viewRef = WeakReference(child)
        return true
    }

    private fun setupAnimators(child: V) {
        val listener = object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                animating = true
            }

            override fun onAnimationEnd(animation: Animator) {
                animating = false
            }

            override fun onAnimationCancel(animation: Animator) {
                animating = false
            }
        }
        scrollOutAnimator = ObjectAnimator.ofFloat(child, "translationY", 0f, bottomNavigationHeight.toFloat()).apply {
            duration = ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addListener(listener)
        }
        scrollInAnimator = ObjectAnimator.ofFloat(child, "translationY", bottomNavigationHeight.toFloat(), 0f).apply {
            duration = ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addListener(listener)
        }
    }

    // --- Nested Scroll 联动逻辑 (之前遗漏的部分) ---

    override fun onStartNestedScroll(
        parent: CoordinatorLayout,
        child: V,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        return (axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0
    }

    override fun onNestedPreScroll(parent: CoordinatorLayout, child: V, target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        if (animating) return
        val direction = if (dy >= 0) SCROLL_DOWN else SCROLL_UP
        if (animatingDirection == direction) return

        animatingDirection = direction
        if (direction == SCROLL_DOWN) {
            scrollInAnimator?.cancel()
            scrollOutAnimator?.start()
        } else {
            scrollOutAnimator?.cancel()
            scrollInAnimator?.start()
        }
    }

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: V, ev: MotionEvent): Boolean {
        if (!draggable || !child.isShown) return false
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN) reset()
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(ev)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = ev.x.toInt()
                initialY = ev.y.toInt()
                ignoreEvents = !parent.isPointInChildBounds(child, initialX, initialY)
            }
        }
        return !ignoreEvents && dragHelper?.shouldInterceptTouchEvent(ev) == true
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: V, ev: MotionEvent): Boolean {
        if (!draggable || !child.isShown) return false
        dragHelper?.processTouchEvent(ev)
        return !ignoreEvents
    }

    private fun reset() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun setStateInternal(@State state: Int) {
        if (this.state == state) return
        this.state = state
        if (state != STATE_DRAGGING && state != STATE_SETTLING) {
            listener?.onBehaviorStateChanged(state)
        }
    }

    inner class SettleRunnable(val view: View, @State val targetState: Int) : Runnable {
        override fun run() {
            if (dragHelper?.continueSettling(true) == true) {
                ViewCompat.postOnAnimation(view, this)
            } else {
                setStateInternal(targetState)
            }
        }
    }

    inner class DragCallback : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean =
            state != STATE_DRAGGING && viewRef?.get() != null

        override fun onViewPositionChanged(view: View, left: Int, top: Int, dx: Int, dy: Int) {
            val rate = (shrinkRate - 1) / shrinkMarginTop * top.toFloat() + 1
            view.scaleX = rate
            view.scaleY = rate
            view.translationX = (leftMargin * 2f * (1f - rate))
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val targetState = if (releasedChild.top < parentHeight / 2) STATE_EXPANDED else STATE_SHRINK
            val top = if (targetState == STATE_EXPANDED) 0 else shrinkMarginTop
            if (dragHelper?.settleCapturedViewAt(0, top) == true) {
                setStateInternal(STATE_SETTLING)
                ViewCompat.postOnAnimation(releasedChild, SettleRunnable(releasedChild, targetState))
            } else {
                setStateInternal(targetState)
            }
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int =
            top.coerceIn(0, shrinkMarginTop)

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int =
            if (state == STATE_SHRINK_DRAGGING) left.coerceIn(-parentWidth, parentWidth) else 0
    }

    interface OnBehaviorStateListener {
        fun onBehaviorStateChanged(@State newState: Int)
    }
}
