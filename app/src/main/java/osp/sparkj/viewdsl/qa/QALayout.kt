package osp.sparkj.viewdsl.qa

import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import osp.spark.view.auxiliary.Group
import osp.spark.view.wings.dp
import osp.spark.view.wings.safeAs

class QALayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), Group {

    var textView: TextView? = null
    private val viewModel: osp.sparkj.viewdsl.qa.QuestUIViewModel = osp.sparkj.viewdsl.qa.QuestUIViewModel()

    init {
        addView(QuestionLayout(context, null, 0), -1, -2)

//        viewModel.showTipsState.observe(context.safeAs<LifecycleOwner>()!!) {
//            if (it) {
//                onlyForShareTips()
//            } else if (textView != null) {
//                val tip = NearPopTipView((context as FragmentActivity).window)
//                tip.setContent("快把有用的知识告诉你的家人朋友吧！")
//                tip.setDismissOnTouchOutside(true)
//                tip.show(textView!!)
//            }
//        }
        viewModel.uiState.observe(context.safeAs<LifecycleOwner>()!!) {
            visibility = if (it.question.isEmpty()) View.GONE else View.VISIBLE
        }

    }

    private fun onlyForShareTips() {
        textView = TextView(context).apply {
            text = "分享"
            textSize = 14F
            setTextColor(Color.TRANSPARENT)
        }
        val layoutParams = LayoutParams(-1, 44.dp())
        layoutParams.topMargin = 16.dp()
        layoutParams.rightMargin = 16.dp()

        addView(FrameLayout(context).apply {
            addView(textView!!, LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                rightMargin = 20.dp()
            })
        }, layoutParams)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
//        if (!viewModel.homeNavi) {
//            //不是首页卡片导航来的不需要滚动
//            return
//        }
        //滚动到可见
        Looper.myQueue().addIdleHandler {
            calcutePosition()
            return@addIdleHandler false
        }
    }

    private fun calcutePosition() {
        val scrollView = parent.parent as ScrollView
        scrollView.smoothScrollTo(0, top)
    }
}