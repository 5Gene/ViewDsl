package osp.spark.view.atelier.behaviors

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat

/**
 * 🔗 跟随 Behavior
 * 使一个 View 根据 [GoogleMapLikeBehavior] 状态的变化来联动调整自己的 Y 轴位置
 */
class FollowBehavior<V : View>(
    context: Context?, attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<V>(context, attrs) {

    var peekHeight = 300
    var anchorPointY = 600
    var currentChildY = 0
    var anchorTopMargin = 0

    override fun layoutDependsOn(parent: CoordinatorLayout, child: V, dependency: View): Boolean =
        GoogleMapLikeBehavior.from(dependency) != null

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        parent.onLayoutChild(child, layoutDirection)
        ViewCompat.offsetTopAndBottom(child, 0)
        anchorPointY = parent.height - anchorTopMargin
        return true
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        super.onDependentViewChanged(parent, child, dependency)
        // 计算联动比率
        val rate = (parent.height - dependency.y - peekHeight) / (anchorPointY - peekHeight)
        currentChildY = ((parent.height + child.height) * (1f - rate)).toInt()

        if (currentChildY <= 0) {
            child.y = 0F
            currentChildY = 0
        } else {
            child.y = currentChildY.toFloat()
        }
        return true
    }
}
