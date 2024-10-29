package osp.spark.view.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.core.view.forEach

/**
 * @author yun.
 * @date 2021/8/19
 * @des [一句话描述]
 * @since [https://github.com/ZuYun]
 * <p><a href="https://github.com/ZuYun">github</a>
 */
class WIFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    init {
        setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                requestApplyInsets()
            }

            override fun onChildViewRemoved(parent: View?, child: View?) {

            }
        })
    }


    override fun onApplyWindowInsets(insets: WindowInsets?): WindowInsets {
        forEach {
            it.dispatchApplyWindowInsets(insets)
        }
        return super.onApplyWindowInsets(insets)
    }

}