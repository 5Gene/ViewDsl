package osp.spark.view.atelier.behaviors

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout

/**
 * 📤 滚出 Behavior
 * 继承自 [AppBarLayout.ScrollingViewBehavior]，支持根据 [GoogleMapLikeBehavior] 的位置联动滚出
 */
class ScrollOutBehavior(
    context: Context, attrs: AttributeSet? = null
) : AppBarLayout.ScrollingViewBehavior(context, attrs) {

    var peekHeight = 300
    var anchorPointY = 600
    var currentChildY = 0
    var anchorTopMargin = 0

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean =
        GoogleMapLikeBehavior.from(dependency) != null

    override fun onLayoutChild(parent: CoordinatorLayout, child: View, layoutDirection: Int): Boolean {
        parent.onLayoutChild(child, layoutDirection)
        anchorPointY = parent.height - anchorTopMargin
        return true
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        super.onDependentViewChanged(parent, child, dependency)

        // 计算联动比率
        val rate = (parent.height - dependency.y - peekHeight) / anchorTopMargin.toFloat()

        // 计算偏移
        currentChildY = -((child.height + child.paddingTop + child.paddingBottom + child.top + child.bottom) * rate).toInt()

        if (currentChildY <= 0) {
            child.y = currentChildY.toFloat()
        } else {
            child.y = 0f
            currentChildY = 0
        }
        return true
    }
}
