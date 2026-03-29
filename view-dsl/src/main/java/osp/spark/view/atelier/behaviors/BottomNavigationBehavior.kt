package osp.spark.view.atelier.behaviors

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat

/**
 * 实现底部导航栏随滑动自动隐藏/显示的 Behavior
 */
class BottomNavigationBehavior<V : View>(
    context: Context?, attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<V>(context, attrs) {

    companion object {
        const val SCROLL_UP = 1
        const val SCROLL_DOWN = -1
        const val ANIMATION_DURATION = 300L

        @Suppress("UNCHECKED_CAST")
        fun <V : View> from(view: V?): BottomNavigationBehavior<V>? {
            if (view == null) return null
            val params = view.layoutParams as? CoordinatorLayout.LayoutParams
                ?: throw IllegalArgumentException("The view is not a child of CoordinatorLayout")
            return params.behavior as? BottomNavigationBehavior<V>
        }
    }

    private var animating = false
    private var animatingDirection = 0

    private val animationListener = object : AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: Animator) {
            animating = true
        }

        override fun onAnimationCancel(animation: Animator) {
            animating = false
        }

        override fun onAnimationEnd(animation: Animator) {
            animating = false
        }
    }

    private var scrollOutAnimator: ObjectAnimator? = null
    private var scrollInAnimator: ObjectAnimator? = null

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        val parentHeight = parent.height
        // 先让父布局完成测量和布局
        parent.onLayoutChild(child, layoutDirection)
        // 初始位置：放置在底部
        ViewCompat.offsetTopAndBottom(child, parentHeight - child.height)

        // 准备动画对象（只需初始化一次或在尺寸变化时更新）
        setupAnimators(child)

        return true
    }

    private fun setupAnimators(child: V) {
        val height = child.height.toFloat()
        if (height <= 0) return

        scrollOutAnimator = ObjectAnimator.ofFloat(child, "translationY", 0f, height).apply {
            duration = ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addListener(animationListener)
        }
        scrollInAnimator = ObjectAnimator.ofFloat(child, "translationY", height, 0f).apply {
            duration = ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addListener(animationListener)
        }
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout, child: V,
        directTargetChild: View, target: View, nestedScrollAxes: Int, type: Int
    ): Boolean {
        return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout, child: V, target: View,
        dx: Int, dy: Int, consumed: IntArray, type: Int
    ) {
        super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)

        // 如果正在执行动画，则不处理新的滑动
        if (animating) return

        // dy > 0 表示向上滑动（手指往上移，内容往下滚），此时隐藏底部栏
        val direction = if (dy > 0) SCROLL_DOWN else if (dy < 0) SCROLL_UP else 0

        if (direction == 0 || animatingDirection == direction) return

        animatingDirection = direction
        if (direction == SCROLL_DOWN) {
            scrollOutAnimation()
        } else {
            scrollInAnimation()
        }
    }

    private fun scrollOutAnimation() {
        scrollInAnimator?.cancel()
        scrollOutAnimator?.start()
    }

    private fun scrollInAnimation() {
        scrollOutAnimator?.cancel()
        scrollInAnimator?.start()
    }
}
