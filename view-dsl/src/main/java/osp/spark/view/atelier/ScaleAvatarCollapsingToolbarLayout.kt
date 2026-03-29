package osp.spark.view.atelier

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout

class ScaleAvatarCollapsingToolbarLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CollapsingToolbarLayout(context, attrs, defStyleAttr) {

    var pinHeightRatio: Float = 0.7f
    var avatarScaleRatio: Float = 0.8f

    private var pinView: View? = null
    private var avatarView: View? = null

    private val offsetUpdateListener = AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
        val totalScrollRange = appBarLayout.totalScrollRange
        if (totalScrollRange == 0) return@OnOffsetChangedListener

        val progress = Math.abs(verticalOffset).toFloat() / totalScrollRange

        // Requirement 2: Handle avatar scaling
        // "滚动到pin高度的时候大小缩小到80%" - assuming this means when fully collapsed
        avatarView?.let { avatar ->
            val scale = 1f - (1f - avatarScaleRatio) * progress
            avatar.scaleX = scale
            avatar.scaleY = scale
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (parent as? AppBarLayout)?.addOnOffsetChangedListener(offsetUpdateListener)
    }

    override fun onDetachedFromWindow() {
        (parent as? AppBarLayout)?.removeOnOffsetChangedListener(offsetUpdateListener)
        super.onDetachedFromWindow()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        findViews()
    }

    private fun findViews() {
        // Requirement 2: Find tag "avatar"
        avatarView = findViewWithTag("avatar")
        // Requirement 1: Find pin view
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as? LayoutParams
            if (lp?.collapseMode == LayoutParams.COLLAPSE_MODE_PIN) {
                pinView = child
                break
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // If pinView had wrap_content, we can adjust it based on measured height
        pinView!!.let { pin ->
            val targetHeight = (measuredHeight * pinHeightRatio).toInt()
            val lp = pin.layoutParams
            if (lp.height != targetHeight) {
                lp.height = targetHeight
                // We don't setLayoutParams here to avoid infinite measure loops,
                // instead we might just re-measure it with exactly this height.
                val pinWidthSpec = getChildMeasureSpec(widthMeasureSpec, 0, lp.width)
                val pinHeightSpec = MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY)
                pin.measure(pinWidthSpec, pinHeightSpec)
            }
        }
    }
}
