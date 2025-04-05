package osp.sparkj.viewdsl

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat.startActivity
import androidx.core.graphics.toColorInt
import com.google.android.material.internal.EdgeToEdgeUtils
import osp.spark.view.auxiliary.Group
import osp.spark.view.dsl.VModifier
import osp.spark.view.dsl.ViewCompose
import osp.spark.view.dsl.background
import osp.spark.view.dsl.column
import osp.spark.view.dsl.constLayoutParams
import osp.spark.view.dsl.icon
import osp.spark.view.dsl.line
import osp.spark.view.dsl.plus
import osp.spark.view.dsl.spacer
import osp.spark.view.dsl.text
import osp.spark.view.dsl.vLayoutConstraint
import osp.spark.view.wings.dp
import osp.spark.view.wings.dpf
import osp.spark.view.wings.padding
import osp.spark.view.wings.processName
import osp.spark.view.wings.safeAs
import osp.spark.view.wings.toast
import osp.sparkj.viewdsl.compose.MediaSelectLayout
import osp.sparkj.viewdsl.compose.MediaSelectViewModel
import osp.sparkj.viewdsl.qa.QuestionLayout
import osp.sparkj.viewdsl.qa.QuestionView

class MainActivity : AppCompatActivity() {
    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel by viewModels<MediaSelectViewModel>()
        viewModel.registerForActivityResult(this::registerForActivityResult)
        EdgeToEdgeUtils.applyEdgeToEdge(window, true)
        setContentView(ScrollView(this).apply {
            Modifier.safeDrawingPadding()
//            overScrollMode = OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            padding(horizontal = 10.dp())
            column {
                fitsSystemWindows = true
                this + Card(context)
                spacer(8.dp())
                line(width = 300.dp(), height = 1.dp(), color = Color.RED)
                spacer(8.dp())
                this + QACard(context)
                spacer(8.dp())
                this + QuestionLayout(context)
                spacer(8.dp())
                this + QuestionView(context)
                spacer(18.dp())
                this + ComposeView(context = context).apply {
                    setContent {
                        MediaSelectLayout()
                    }
                }
            }
        })

        toast(processName + packageName)
    }
}


class Card @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), ViewCompose {
    init {
        parent.safeAs<ViewGroup>()?.clipChildren = false
        clipChildren = false
        background {
            color = ColorStateList.valueOf(Color.RED)
        }
        padding(horizontal = 34.dp())
        clipToPadding = false
        vLayoutConstraint(
            modifier = VModifier
                .vSize(180.dp(), 180.dp())
                .vCustomize {
                    setBackgroundColor(android.graphics.Color.GRAY)
                }
                .vRotateHover()
        ) {
            padding(10.dp())
            icon {
                setImageResource(R.mipmap.img)
                background {
                    color = ColorStateList.valueOf(Color.GREEN)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                constLayoutParams(height = 180.dp(), width = -1) {
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                }
            }
        }
    }
}

class QACard @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs), Group {
    init {
        background {
            cornerRadius = 12.dpf()
            setColor(resources.getColor(R.color.purple_200))
        }
        gravity = Gravity.CENTER_VERTICAL

        padding(14.dp(), 12.dp())

        icon(20.dp(), 20.dp()) {
            setImageResource(R.drawable.ic_launcher_foreground)
        }
        spacer(10.dp())
        text {
            textSize = 14F
            setTextColor(Color.BLACK)
            val question = "什么这是"
            val homePageGuide = "点点看"
            text = SpannableString("$question $homePageGuide").apply {
                val colorSpan = ForegroundColorSpan("#FF2DC84E".toColorInt())
                setSpan(
                    colorSpan,
                    question.length + 1,
                    question.length + homePageGuide.length + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        this@QACard.setOnClickListener {
            startActivity(context, Intent(context, FlipActivity::class.java), null)
        }
    }
}