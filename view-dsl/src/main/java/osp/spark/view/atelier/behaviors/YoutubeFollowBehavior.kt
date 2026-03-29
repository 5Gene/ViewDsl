package osp.spark.view.atelier.behaviors

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat

/**
 * 🔗 YouTube 联动 Behavior
 * 使一个内容 View 跟随 [YoutubeLikeBehavior] 的视频面板进行联动调整
 * 包括：透明度渐变、位置同步缩放、高度动态调整
 */
class YoutubeFollowBehavior<V : View>(
    context: Context, attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<V>(context, attrs) {

    private var shrinkRate: Float = 0.5f
    private var mediaHeight: Float = 600f
    private var marginBottom: Int = 0
    private var marginRight: Int = 0

    private var shrinkContentMarginTop = 0
    private var parentHeight = 0
    private var sizeAdjusted = false

    override fun layoutDependsOn(parent: CoordinatorLayout, child: V, dependency: View): Boolean =
        YoutubeLikeBehavior.from(dependency) != null

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        parentHeight = parent.height
        // 计算缩放后的内容 Y 轴起始偏移
        shrinkContentMarginTop = (parentHeight - mediaHeight + mediaHeight * shrinkRate / 2).toInt() - marginBottom

        parent.onLayoutChild(child, layoutDirection)

        // 初始放置在媒体 View 下方
        ViewCompat.offsetTopAndBottom(child, mediaHeight.toInt())

        if (!sizeAdjusted) {
            sizeAdjusted = true
            val lp = child.layoutParams
            lp.height = (parentHeight - mediaHeight).toInt()
            child.layoutParams = lp
        }
        return true
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        super.onDependentViewChanged(parent, child, dependency)

        // 计算联动比率（根据 dependency.y 的位置）
        val rate = dependency.y / shrinkContentMarginTop

        // 设置透明度（向上滑时变不透明，向下滑时变透明）
        child.alpha = (1f - rate).coerceIn(0f, 1f)

        // 同步位置
        child.y = dependency.y + mediaHeight
        return true
    }
}
