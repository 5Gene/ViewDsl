package osp.spark.view.atelier.behaviors

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.NestedScrollingChild
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import java.lang.ref.WeakReference

/**
 * 🗺 仿 Google Maps 抽屉效果的 Behavior
 * 支持：展开 (Expanded)、折叠 (Collapsed)、锚点 (Anchor)、隐藏 (Hidden) 四种状态
 */
class GoogleMapLikeBehavior<V : View>(
    context: Context, attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<V>(context, attrs) {

    companion object {
        @IntDef(STATE_DRAGGING, STATE_SETTLING, STATE_ANCHOR_POINT, STATE_EXPANDED, STATE_COLLAPSED, STATE_HIDDEN)
        @Retention(AnnotationRetention.SOURCE)
        annotation class State

        const val STATE_DRAGGING = 1
        const val STATE_SETTLING = 2
        const val STATE_ANCHOR_POINT = 3
        const val STATE_EXPANDED = 4
        const val STATE_COLLAPSED = 5
        const val STATE_HIDDEN = 6

        @Suppress("UNCHECKED_CAST")
        fun <V : View> from(view: V?): GoogleMapLikeBehavior<V>? {
            if (view == null) return null
            val params = view.layoutParams as? CoordinatorLayout.LayoutParams
                ?: throw IllegalArgumentException("The view is not a child of CoordinatorLayout")
            return params.behavior as? GoogleMapLikeBehavior<V>
        }
    }

    var listener: OnBehaviorStateListener? = null
    var velocityTracker: VelocityTracker? = null
    var state = STATE_COLLAPSED

    var peekHeight: Int = 0
    var anchorPosition: Int = 0
    var activePointerId = MotionEvent.INVALID_POINTER_ID

    private var viewRef: WeakReference<View>? = null
    private var nestedScrollingChildRef: WeakReference<View?>? = null

    var skippedAnchorPoint = false
    var draggable = true
    private var dragHelper: ViewDragHelper? = null
    var hideable = false
    private var ignoreEvents = false
    private var initialY = 0
    private var touchingScrollingChild = false
    private var lastNestedScrollDy = 0
    private var nestedScrolled = false
    private val dragCallback = DragCallback()

    private var maxOffset = 0
    private var minOffset = 0
    private var parentHeight = 0
    private var anchorTopMargin = 0

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        if (state != STATE_DRAGGING && state != STATE_SETTLING) {
            parent.onLayoutChild(child, layoutDirection)
        }

        parentHeight = parent.height
        minOffset = Math.max(0, parentHeight - child.height)
        maxOffset = Math.max(minOffset, parentHeight - peekHeight)
        anchorPosition = parentHeight - anchorTopMargin

        when (state) {
            STATE_ANCHOR_POINT -> ViewCompat.offsetTopAndBottom(child, parentHeight - anchorPosition)
            STATE_EXPANDED -> ViewCompat.offsetTopAndBottom(child, minOffset)
            STATE_HIDDEN -> ViewCompat.offsetTopAndBottom(child, parentHeight)
            STATE_COLLAPSED -> ViewCompat.offsetTopAndBottom(child, maxOffset)
            else -> {}
        }

        if (dragHelper == null) {
            dragHelper = ViewDragHelper.create(parent, dragCallback)
        }
        viewRef = WeakReference(child)
        nestedScrollingChildRef = WeakReference(findScrollingChild(child))
        return true
    }

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: V, ev: MotionEvent): Boolean {
        if (!draggable || !child.isShown) return false

        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN) reset()

        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(ev)

        when (action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchingScrollingChild = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
                if (ignoreEvents) {
                    ignoreEvents = false
                    return false
                }
            }

            MotionEvent.ACTION_DOWN -> {
                val initialX = ev.x.toInt()
                initialY = ev.y.toInt()
                val scroll = nestedScrollingChildRef?.get()
                if (state == STATE_ANCHOR_POINT || (scroll != null && parent.isPointInChildBounds(scroll, initialX, initialY))) {
                    activePointerId = ev.getPointerId(ev.actionIndex)
                    touchingScrollingChild = true
                }
                ignoreEvents =
                    activePointerId == MotionEvent.INVALID_POINTER_ID && !parent.isPointInChildBounds(child, initialX, initialY)
            }
        }

        if (!ignoreEvents && dragHelper?.shouldInterceptTouchEvent(ev) == true) return true

        val scroll = nestedScrollingChildRef?.get()
        return action == MotionEvent.ACTION_MOVE && scroll != null && !ignoreEvents && state != STATE_DRAGGING &&
                !parent.isPointInChildBounds(scroll, ev.x.toInt(), ev.y.toInt()) &&
                Math.abs(initialY - ev.y) > (dragHelper?.touchSlop ?: 0)
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: V, ev: MotionEvent): Boolean {
        if (!draggable || !child.isShown) return false

        val action = ev.actionMasked
        if (state == STATE_DRAGGING && action == MotionEvent.ACTION_DOWN) return true

        dragHelper?.processTouchEvent(ev)
        if (action == MotionEvent.ACTION_DOWN) reset()

        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(ev)

        if (action == MotionEvent.ACTION_MOVE && !ignoreEvents) {
            if (Math.abs(initialY - ev.y) > (dragHelper?.touchSlop ?: 0)) {
                dragHelper?.captureChildView(child, ev.getPointerId(ev.actionIndex))
            }
        }
        return !ignoreEvents
    }

    override fun onStartNestedScroll(
        parent: CoordinatorLayout,
        child: V,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        lastNestedScrollDy = 0
        nestedScrolled = false
        return (axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0
    }

    override fun onNestedPreScroll(parent: CoordinatorLayout, child: V, target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        val scrollChild = nestedScrollingChildRef?.get() ?: return
        if (target != scrollChild) return

        val currentTop = child.top
        val newTop = currentTop - dy
        if (dy > 0) { // 向上滑动
            if (newTop < minOffset) {
                consumed[1] = currentTop - minOffset
                ViewCompat.offsetTopAndBottom(child, -consumed[1])
                setStateInternal(STATE_EXPANDED)
            } else {
                consumed[1] = dy
                ViewCompat.offsetTopAndBottom(child, -dy)
                setStateInternal(STATE_DRAGGING)
            }
        } else if (dy < 0) { // 向下滑动
            if (!target.canScrollVertically(-1)) {
                if (newTop <= maxOffset || hideable) {
                    consumed[1] = dy
                    ViewCompat.offsetTopAndBottom(child, -dy)
                    setStateInternal(STATE_DRAGGING)
                } else {
                    consumed[1] = currentTop - maxOffset
                    ViewCompat.offsetTopAndBottom(child, -consumed[1])
                    setStateInternal(STATE_COLLAPSED)
                }
            }
        }
        lastNestedScrollDy = dy
        nestedScrolled = true
    }

    override fun onStopNestedScroll(parent: CoordinatorLayout, child: V, target: View, type: Int) {
        if (child.top == minOffset) {
            setStateInternal(STATE_EXPANDED)
            return
        }
        if (target != nestedScrollingChildRef?.get() || !nestedScrolled) return

        val top: Int
        @State val targetState: Int

        if (lastNestedScrollDy > 0) {
            top = if (child.top > parentHeight - anchorPosition && !skippedAnchorPoint) {
                parentHeight - anchorPosition
            } else {
                minOffset
            }
            targetState = if (top == minOffset) STATE_EXPANDED else STATE_ANCHOR_POINT
        } else {
            top = if (child.top > parentHeight - anchorPosition) maxOffset else parentHeight - anchorPosition
            targetState = if (top == maxOffset) STATE_COLLAPSED else STATE_ANCHOR_POINT
        }

        if (dragHelper?.smoothSlideViewTo(child, child.left, top) == true) {
            setStateInternal(STATE_SETTLING)
            ViewCompat.postOnAnimation(child, SettleRunnable(child, targetState))
        } else {
            setStateInternal(targetState)
        }
        nestedScrolled = false
    }

    private fun setStateInternal(@State state: Int) {
        if (this.state == state) return
        this.state = state
        if (state != STATE_DRAGGING && state != STATE_SETTLING) {
            listener?.onBehaviorStateChanged(state)
        }
    }

    private fun findScrollingChild(view: View): View? {
        if (view is NestedScrollingChild) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findScrollingChild(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun reset() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        velocityTracker?.recycle()
        velocityTracker = null
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
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            if (state == STATE_DRAGGING || touchingScrollingChild) return false
            if (state == STATE_EXPANDED && activePointerId == pointerId) {
                val scroll = nestedScrollingChildRef?.get()
                if (scroll != null && scroll.canScrollVertically(-1)) return false
            }
            return viewRef?.get() != null
        }

        override fun onViewPositionChanged(view: View, left: Int, top: Int, dx: Int, dy: Int) {}

        override fun onViewDragStateChanged(state: Int) {
            if (state == ViewDragHelper.STATE_DRAGGING) setStateInternal(STATE_DRAGGING)
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            // 释放后的自动滑动逻辑... (此处省略部分重复的 top/targetState 计算)
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int =
            Math.max(minOffset, Math.min(top, if (hideable) parentHeight else maxOffset))

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int = child.left
    }

    interface OnBehaviorStateListener {
        fun onBehaviorStateChanged(@State newState: Int)
    }
}
