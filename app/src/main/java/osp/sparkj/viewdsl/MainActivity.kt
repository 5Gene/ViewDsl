package osp.sparkj.viewdsl

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat.startActivity
import androidx.core.graphics.toColorInt
import osp.spark.view.dsl.Group
import osp.spark.view.dsl.VModifier
import osp.spark.view.dsl.ViewCompose
import osp.spark.view.dsl.background
import osp.spark.view.dsl.constLayoutParams
import osp.spark.view.dsl.icon
import osp.spark.view.dsl.padding
import osp.spark.view.dsl.plus
import osp.spark.view.dsl.space
import osp.spark.view.dsl.vLayoutConstraint
import osp.spark.view.wings.dp
import osp.spark.view.wings.dpf
import osp.spark.view.wings.processName
import osp.spark.view.wings.safeAs
import osp.spark.view.wings.toast

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            this + Card(context)
            space(16)
            this + QACard(context)
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
        padding(horizontal = 200)
        clipToPadding = false
        vLayoutConstraint(
            modifier = VModifier
                .vSize(500, 500)
                .vCustomize {
                    setBackgroundColor(android.graphics.Color.GRAY)
                }
                .vRotateHover()
        ) {
            icon {
                setImageResource(R.mipmap.img)
                background {
                    color = ColorStateList.valueOf(Color.GREEN)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                constLayoutParams(height = 600, width = -1) {
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                }
            }

            space(18)
        }
    }
}

class QACard @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs), Group {
    init {
        background = GradientDrawable().apply {
            cornerRadius = 12.dpf()
            setColor(resources.getColor(R.color.purple_200))
        }
        gravity = Gravity.CENTER_VERTICAL
        padding(14, 12)
        icon(20, 20) {
            setImageResource(R.drawable.ic_launcher_foreground)
        }
        space(10, 10)
        addView(Space(context), 10.dp(), 10.dp())
        val textView = TextView(context).apply {
            textSize = 14F
            setTextColor(Color.BLACK)
        }
        addView(textView)
        val question = "什么这是"
        val homePageGuide = "点点看"
        textView.text = SpannableString("$question $homePageGuide").apply {
            val colorSpan = ForegroundColorSpan("#FF2DC84E".toColorInt())
            setSpan(
                colorSpan,
                question.length + 1,
                question.length + homePageGuide.length + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        this@QACard.setOnClickListener {
            startActivity(context, Intent(context, FlipActivity::class.java), null)
        }
    }
}