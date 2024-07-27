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
import osp.june.dsl.VModifier
import osp.june.dsl.ViewCompose
import osp.june.dsl.background
import osp.june.dsl.constLayoutParams
import osp.june.dsl.icon
import osp.june.dsl.padding
import osp.june.dsl.safeAs
import osp.june.dsl.spacer
import osp.june.dsl.unaryPlus
import osp.june.dsl.vLayoutConstraint
import osp.sparkj.cartoon.wings.todp
import osp.sparkj.cartoon.wings.todpf

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            +Card(context)
            spacer(height = 16.todp)
            +QACard(context)
        })
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

            spacer(height = 18.todp)
        }
    }
}

class QACard @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    init {
        background = GradientDrawable().apply {
            cornerRadius = 12.todpf
            setColor(resources.getColor(R.color.purple_200))
        }
        gravity = Gravity.CENTER_VERTICAL
        setPadding(14.todp, 12.todp, 14.todp, 12.todp)
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
        }, 20.todp, 20.todp)

        addView(Space(context), 10.todp, 10.todp)
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