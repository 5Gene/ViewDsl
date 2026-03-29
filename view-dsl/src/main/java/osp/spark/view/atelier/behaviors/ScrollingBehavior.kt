package osp.spark.view.atelier.behaviors

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout

/**
 * 📜 滚动联动 Behavior
 * 使 View 根据 [GoogleMapLikeBehavior] 的滑动状态，同步调整自己的垂直偏移（Y轴）
 */
class ScrollingBehavior<V : View>(
    context: Context, attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<V>(context, attrs) {

    var peekHeight = 300
    var anchorPointY = 600
    var currentChildY = 0
    var anchorTopMargin = 0

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        // 计算联动率
        val rate = (parent.height - dependency.y - peekHeight) / (anchorPointY - peekHeight)

        // 计算当前 View 应有的 Y 偏移
        currentChildY = ((child.height + child.paddingTop + child.paddingBottom + child.top + child.bottom) * (1f - rate)).toInt()

        if (currentChildY <= 0) {
            child.y = currentChildY.toFloat()
        } else {
            child.y = 0f
            currentChildY = 0
        }
        return true
    }
}
