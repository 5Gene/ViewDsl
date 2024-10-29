package osp.sparkj.viewdsl

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
import androidx.core.view.WindowCompat
import osp.spark.view.dsl.LayoutConstraint
import osp.spark.view.dsl.VModifier
import osp.spark.view.dsl.ViewCompose
import osp.spark.view.dsl.background
import osp.spark.view.dsl.constLayoutParams
import osp.spark.view.dsl.frameLayoutParams
import osp.spark.view.dsl.icon
import osp.spark.view.dsl.matchHorizontal
import osp.spark.view.dsl.matchVertical
import osp.spark.view.dsl.padding
import osp.spark.view.dsl.text
import osp.spark.view.dsl.vLayoutConstraint
import osp.sparkj.cartoon.wings.todp
import osp.sparkj.cartoon.wings.todpf

class FlipActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(FlipView(this))
//        setContentView(Touch3D(this))
    }
}

val topOffset = 126.todpf
val widthScale = .86F

class FlipView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), ViewCompose {
    init {
        clipChildren = false
        clipToPadding = false
        vLayoutConstraint(
            modifier = VModifier
                .vSize(-1, -1)
                .vFlipFather()
        ) {
            vLayoutConstraint(
                modifier = VModifier
                    .vSize(-1, -1)
                    .vFlipExpand(cardWidthFactor = widthScale, topOffset = topOffset)
            ) {
                icon(width = 0, height = 0) {
                    setImageResource(R.mipmap.img)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    constLayoutParams {
                        width = 0
                        height = 0
                        topToTop = PARENT_ID
                        startToStart = PARENT_ID
                        endToEnd = PARENT_ID
                        bottomToBottom = PARENT_ID
                    }
                }
            }

            flipCardWithView()
//            flipCardWithDraw()
        }
    }

    private fun LayoutConstraint.flipCardWithView() {

        val head = vLayoutConstraint(
            modifier = osp.spark.view.dsl.VModifier
                .vSizeFactor(widthScale, 0)
                .vFlipHeadView { v, p ->
                    v.alpha = 1 - p * 2
                }
        ) {
            constLayoutParams {
                height = topOffset.toInt()
                topToTop = PARENT_ID
                startToStart = PARENT_ID
                endToEnd = PARENT_ID
            }
            text(width = -1, height = -1) {
                padding(top = 16.todp)
                text = "发现新版本"
                textSize = 30F
                constLayoutParams {
                    width = -2
                    height = 100.todp
                    topToTop = PARENT_ID
                    topMargin = 35.todp
                    startToStart = PARENT_ID
                }
            }
        }
        vLayoutConstraint(
            modifier = osp.spark.view.dsl.VModifier
                .debug(Color.YELLOW)
                .vSizeFactor(widthScale, .66)
                .vFlipCardView(widthScale)
        ) {
//            shapeRound(radiusRatio = 8.todpf)
            background {
                cornerRadius = 13.todpf
                color = ColorStateList.valueOf(Color.BLUE)
            }
            constLayoutParams {
                topToBottom = head.id
                startToStart = PARENT_ID
                endToEnd = PARENT_ID
            }
        }
    }

    private fun LayoutConstraint.flipCardWithDraw() {
        vLayoutConstraint(
            modifier = osp.spark.view.dsl.VModifier
                .vSize(-1, -1)
                .vFlipCard(topOffset = topOffset)
        ) {
            padding(horizontal = 13.todp)
            icon(width = 0, height = 0) {
                setImageResource(R.mipmap.img)
                scaleType = ImageView.ScaleType.CENTER_CROP
                constLayoutParams {
                    matchHorizontal()
                    matchVertical()
                }
            }
        }
    }
}

class Touch3D @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ViewCompose {

    init {
        frameLayoutParams(-1, -1) {
            gravity = Gravity.CENTER
        }

        setBackgroundColor(Color.RED)

        vLayoutConstraint(
            modifier = VModifier
                .debug()
//                .v3DTouch2()
                .vRotateHover()
//                .vFold()
        ) {
            icon {
                setBackgroundColor(Color.BLACK)
                setImageResource(R.mipmap.img)
                scaleType = ImageView.ScaleType.CENTER_CROP

                constLayoutParams {
                    matchHorizontal()
                    matchVertical()
                }
            }
            frameLayoutParams(200.todp, 200.todp) {
                gravity = Gravity.CENTER
            }
        }
    }

}