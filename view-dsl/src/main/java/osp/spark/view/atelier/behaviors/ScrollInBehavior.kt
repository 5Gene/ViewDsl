package osp.spark.view.atelier.behaviors

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout

/**
 * 📥 滚入 Behavior
 * 当 [GoogleMapLikeBehavior] 展开时，控制指定的 View（如 Toolbar）滚入视野并切换标题
 */
class ScrollInBehavior<V : View>(
    context: Context, attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<V>(context, attrs) {

    var peekHeight = 300
    var anchorPointY = 600
    var currentChildY = 0
    var anchorTopMargin = 0

    var toolbar: Toolbar? = null
    var title = ""

    override fun layoutDependsOn(parent: CoordinatorLayout, child: V, dependency: View): Boolean =
        GoogleMapLikeBehavior.from(dependency) != null

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        parent.onLayoutChild(child, layoutDirection)
        anchorPointY = parent.height - anchorTopMargin

        // 如果 child 是 AppBarLayout，尝试找到内部的 Toolbar
        if (child is AppBarLayout) {
            for (i in 0 until child.childCount) {
                val v = child.getChildAt(i)
                if (v is Toolbar) {
                    toolbar = v
                    break
                }
            }
        }
        return true
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        super.onDependentViewChanged(parent, child, dependency)

        // 计算偏移量
        val rate = (dependency.y - anchorTopMargin) / (parent.height - anchorTopMargin - peekHeight)
        currentChildY = -((child.height + child.paddingTop + child.paddingBottom + child.top + child.bottom) * rate).toInt()

        if (currentChildY <= 0) {
            child.y = currentChildY.toFloat()
        } else {
            child.y = 0f
            currentChildY = 0
        }

        // 背景和标题联动的 alpha/渐变逻辑
        val drawable = child.background?.mutate()
        if (drawable != null) {
            val bounds = drawable.bounds
            var heightRate = (bounds.bottom * 2 - dependency.y) / (bounds.bottom) - 1f
            heightRate = heightRate.coerceIn(0f, 1f)

            if (heightRate >= 1f) {
                toolbar?.title = title
            } else {
                toolbar?.title = ""
            }
            drawable.setBounds(0, (bounds.bottom - bounds.bottom * heightRate).toInt(), bounds.right, bounds.bottom)
            child.background = drawable
        }
        return true
    }
}
