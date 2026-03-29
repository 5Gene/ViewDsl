package osp.spark.view.atelier.behaviors

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * 🚀 FloatingActionButton 的自定义 Behavior
 * 支持随着 AppBarLayout 或 BottomNavigationBehavior 的状态自动调整位置
 */
class FabBehavior(
    context: Context?, attrs: AttributeSet? = null
) : FloatingActionButton.Behavior(context, attrs) {

    private var isScrollOut: Boolean = false
    private var parentHeight = 0

    override fun onLayoutChild(parent: CoordinatorLayout, child: FloatingActionButton, layoutDirection: Int): Boolean {
        parentHeight = parent.height
        return super.onLayoutChild(parent, child, layoutDirection)
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: FloatingActionButton, dependency: View): Boolean {
        return super.layoutDependsOn(parent, child, dependency) || hasBottomNavigationBehavior(dependency)
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: FloatingActionButton, dependency: View): Boolean {
        if (dependency is AppBarLayout) {
            super.onDependentViewChanged(parent, child, dependency)
        } else if (hasBottomNavigationBehavior(dependency)) {
            updateFabPosition(dependency, child)
        }
        return false
    }

    private fun hasBottomNavigationBehavior(dependency: View?): Boolean =
        BottomNavigationBehavior.from(dependency) != null

    private fun updateFabPosition(dependency: View, child: FloatingActionButton) {
        val top = dependency.y
        val layoutParams = child.layoutParams as CoordinatorLayout.LayoutParams
        val rate = if (isScrollOut) (parentHeight - top) / dependency.height else 1f
        child.y = top - (child.height + layoutParams.bottomMargin) * rate
    }
}
