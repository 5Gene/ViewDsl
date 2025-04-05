package osp.sparkj.viewdsl.qa

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.graphics.toColorInt
import androidx.core.view.updatePadding
import androidx.lifecycle.Observer
import com.airbnb.lottie.LottieAnimationView
import osp.spark.view.auxiliary.Group
import osp.spark.view.dsl.animateLayoutChange
import osp.spark.view.dsl.background
import osp.spark.view.dsl.button
import osp.spark.view.dsl.canvas
import osp.spark.view.dsl.column
import osp.spark.view.dsl.icon
import osp.spark.view.dsl.line
import osp.spark.view.dsl.linearLayoutParams
import osp.spark.view.dsl.row
import osp.spark.view.dsl.shapeRound
import osp.spark.view.dsl.spacer
import osp.spark.view.dsl.text
import osp.spark.view.dsl.viewRaw
import osp.spark.view.wings.alpha
import osp.spark.view.wings.dp
import osp.spark.view.wings.dpf
import osp.spark.view.wings.getThemeColor
import osp.spark.view.wings.padding
import osp.spark.view.wings.removeInnerPaddingAndShadow
import osp.spark.view.wings.safeAs
import osp.spark.view.wings.viewModels
import osp.spark.view.wings.visibility
import osp.sparkj.viewdsl.R
import osp.sparkj.viewdsl.qa.data.FeedbackBad
import osp.sparkj.viewdsl.qa.data.FeedbackGood
import osp.sparkj.viewdsl.qa.data.OptionA
import osp.sparkj.viewdsl.qa.data.OptionB
import osp.sparkj.viewdsl.qa.data.Question
import osp.sparkj.viewdsl.qa.data.isAnswerA
import osp.sparkj.viewdsl.qa.data.isAnswerRight
import osp.sparkj.viewdsl.qa.data.isAnswered
import osp.sparkj.viewdsl.qa.data.isVoteType
import java.text.NumberFormat

val fontSans =
    android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)

val colorOptionA = "#7366FF".toColorInt()
val colorOptionB = "#FF813A".toColorInt()

class QuestionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs), Group {

    private val viewModel: QuestUIViewModel by viewModels()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isForceDarkAllowed = true
        }
        layoutParams = LayoutParams(-1, -2)
        orientation = VERTICAL
        val question = viewModel.uiState.value!!
        updatePadding(bottom = 12.dp())
        //标题
        titleWidget(question)
        line()
        if (question.isVoteType()) {
            //投票类型
            voteWidget(question)
        } else {
            //知识类型
            knowledge(question)
        }
        feedbackWidget()
        shapeRound(14.dpf(), Color.GREEN, context.getThemeColor(com.google.android.material.R.attr.colorSurface)!!)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        //MATCH_PARENT -> EXACTLY,填满父布局，给多少用多少
        //WRAP_CONTENT -> AT_MOST,需要多少用多少，但是不能超过给定的大小
        //MeasureSpec.getSize(),获取可以使用的具体大小
        println(
            "EXACTLY:${MeasureSpec.EXACTLY},UNSPECIFIED:${MeasureSpec.UNSPECIFIED},AT_MOST:${MeasureSpec.AT_MOST} =>${
                MeasureSpec.getMode(
                    widthMeasureSpec
                )
            }"
        )
        println("====== size:${MeasureSpec.getSize(widthMeasureSpec)}========")
    }

    private fun voteWidget(question: Question) {
        column {
            setPadding(16.dp(), 16.dp(), 16.dp(), 0)
            text {
                text = question.question
                textSize = 16F
                setTextColor(blackColor())
            }

            val dp12 = 12.dp()
            spacer(dp12)

            val percent = question.questionVote.optionA / 100F
            var ani = 1F
            canvas(width = -1, height = 30.dp()) {
                var animator: ValueAnimator? = null
                attachToWindow = {
                    animator = ValueAnimator.ofFloat(0F, 1F).apply {
                        addUpdateListener {
                            ani = it.animatedValue as Float
                            invalidate()
                        }
                        duration = 666
                        interpolator = AccelerateDecelerateInterpolator()
                        start()
                    }
                }
                detachedFromWindow = {
                    animator?.cancel()
                }
                drawIntoCanvas = {
                    val radius = 6.dp().toFloat()
                    val dp4 = 4.dp()
                    val dp5 = 5.dp()
                    val miniWidth = 42.dp().toFloat()
                    val width = width.toFloat() - dp4
                    val maxWidth = width - miniWidth
                    val height = height.toFloat()

                    val widthA = (width * percent).coerceAtMost(maxWidth).coerceAtLeast(miniWidth) * ani

                    val paint = retrieve { Paint(Paint.ANTI_ALIAS_FLAG) }!!

                    //left
                    paint.color = colorOptionA
                    drawPath(Path().apply {
                        moveTo(radius, 0F)
                        cubicTo(radius, 0F, 0F, 0F, 0F, radius)
                        lineTo(0F, height - radius)
                        cubicTo(0F, height - radius, 0F, height, radius, height)
                        lineTo(widthA - dp5, height)
                        lineTo(widthA + dp5, 0F)
                        close()
                    }, paint)

                    //right
                    paint.color = colorOptionB
                    val widthB =
                        (width * (1 - percent)).coerceAtMost(maxWidth).coerceAtLeast(miniWidth) * ani
                    drawPath(Path().apply {
                        moveTo(width - radius, 0F)
                        cubicTo(width - radius, 0F, width, 0F, width, radius)
                        lineTo(width, height - radius)
                        cubicTo(
                            width,
                            height - radius,
                            width,
                            height,
                            width - radius,
                            height
                        )
                        lineTo(width - widthB - dp5, height)
                        lineTo(width - widthB + dp5, 0F)
                        close()
                    }, paint)

                    //draw text
                    paint.color = Color.WHITE
                    paint.textSize = 14.dp().toFloat()
                    val percentInstance = NumberFormat.getPercentInstance().also { formart ->
                        formart.maximumFractionDigits = 0
                    }
                    val textA = percentInstance.format(percent)
                    val textB = percentInstance.format(1 - percent)

                    val rect = android.graphics.Rect()
                    paint.getTextBounds(textB, 0, textB.length, rect)
                    val textHeight = rect.height()
                    val textY = height / 2 + textHeight / 2
                    drawText(textA, dp12.toFloat(), textY, paint)
                    drawText(
                        textB,
                        width - rect.width() - dp12,
                        textY,
                        paint
                    )

                }
            }
            if (!question.isAnswered()) {
                voteOptionsWidget(this, question)
            }

            animateLayoutChange()
            focus({ isAnswered() }) {
                if (it == true) {
                    voteResultWidget(viewModel.uiState.value!!)
                }
            }
        }
    }

    private fun LinearLayout.voteResultWidget(question: Question) {
        column {
            updatePadding(bottom = 8.dp())
            row {
                updatePadding(top = 4.dp(), bottom = 20.dp())
                val answerA = question.isAnswerA()
                text {
                    textSize = 14F
                    text = if (answerA) "已选择:${question.optionA}" else question.optionA
                    setTextColor(colorOptionA)
                }
                spacer {
                    linearLayoutParams(weight = 1f)
                }
                text {
                    textSize = 14F
                    text = if (answerA) question.optionB else "已选择:${question.optionB}"
                    setTextColor(colorOptionB)
                }
            }

            column {
                padding(horizontal = 12.dp(), vertical = 12.dp())
                background {
                    setColor(blackColor())
                    alpha = (.04 * 255).toInt()
                    cornerRadius = 8.dp().toFloat()
                }

                row {
                    gravity = Gravity.CENTER_VERTICAL
                    val answerRight = question.isAnswerRight()
                    icon {
                        setImageResource(if (answerRight) R.drawable.ic_baseline_emoji_emotions else R.drawable.ic_baseline_error)
                        imageTintList =
                            ColorStateList.valueOf(if (answerRight) "#FF2DC84E".toColorInt() else colorOptionB)
                        linearLayoutParams(18.dp(), 18.dp()) {}
                    }
                    spacer(2.dp())
                    text {
                        text = if (answerRight) "回答正确" else "回答错误"
                        textSize = 16F
                        setTextColor(if (answerRight) "#FF2DC84E".toColorInt() else colorOptionB)
                    }
                }
                spacer(9.dp())
                text {
                    text = question.answerDetail
                    textSize = 14F
                    alpha = .85F
                    setTextColor(blackColor())
                }
                spacer(9.dp())
                text {
                    text = question.referenceSource
                    textSize = 10F
                    alpha = .3F
                    setTextColor(blackColor())
                }
            }
        }
    }

    private fun voteOptionsWidget(parent: LinearLayout, question: Question) {
        parent.row {
            focus({ !isAnswered() }) {
                visibility(it)
            }
            updatePadding(top = 20.dp(), bottom = 11.dp())
            button {
                setOnClickListener {
                    viewModel.voteOption(OptionA)
                }
                removeInnerPaddingAndShadow(20.dp())
                setBackgroundColor(colorOptionA.alpha(.1F))
                setTextColor(colorOptionA)
                text = question.optionA
                linearLayoutParams(-1, 40.dp()) {
                    weight = 1F
                }
            }

            spacer(8)

            button {
                setOnClickListener {
                    viewModel.voteOption(OptionB)
                }
                removeInnerPaddingAndShadow(20.dp())
                setBackgroundColor(colorOptionB.alpha(.1F))
                setTextColor(colorOptionB)
                text = question.optionB
                linearLayoutParams(-1, 40.dp()) {
                    weight = 1F
                }
            }

        }
    }

    private fun knowledge(question: Question) {
        column {
            padding(horizontal = 16.dp(), vertical = 16.dp())
            text {
                text = question.question
                textSize = 16F
                setTextColor(blackColor())
            }
            spacer(12.dp())
            text {
                text = question.answerDetail
                textSize = 14F
                alpha = .65F
                setTextColor(blackColor())
            }
            spacer(6.dp())
            text {
                text = question.referenceSource
                textSize = 10F
                alpha = .3F
                setTextColor(blackColor())
            }
        }

    }

    private fun LinearLayout.feedBackBtn(
        check: (Int?) -> Boolean?,
        imageRes: Int,
        alphaVal: (Int?) -> Float,
        colorInt: (Boolean?) -> Int,
        clickListener: OnClickListener,
        content: String
    ) {
        row {
            gravity = Gravity.CENTER
            fun clickAbleChange(able: Boolean) {
                isClickable = able
            }

            val dp20 = 23.dp()
            val icon = viewRaw(dp20, dp20) {
                LottieAnimationView(context).apply {
                    setAnimation(imageRes)
                    focus({
                        userFeedback
                    }) {
                        val state = check(it)
                        if (state == true) {
                            if (!isAnimating) {
                                progress = 1F
                            }
                            clickAbleChange(false)
                        } else {
                            if (isAnimating) {
                                cancelAnimation()
                            }
                            progress = 0F
                            clickAbleChange(true)
                        }
                        alpha = alphaVal(it)
                    }
                }
            }

            setOnClickListener {
                icon.safeAs<LottieAnimationView>()?.playAnimation()
                clickListener.onClick(it)
            }

            spacer(4.dp())

            background {
                setColor(Color.TRANSPARENT)
            }
            text {
                text = content
                textSize = 14F
                focus({ userFeedback }) {
                    val state = check(it)
                    setTextColor(colorInt(state))
                    alpha = alphaVal(it)
                }
            }

            linearLayoutParams(-2, 44.dp()) {
                weight = 1F
            }
        }
    }

    private fun feedbackWidget() {
        row(height = 44.dp()) {
            focus({
                !isVoteType() || isAnswered()
            }) {
                visibility(it == true)
            }
            gravity = Gravity.CENTER_VERTICAL

            feedBackBtn(
                check = {
                    it == FeedbackBad
                },
                imageRes = R.raw.kudos,
                alphaVal = {
                    if (it == FeedbackGood) .3F else 1F
                },
                colorInt = {
                    if (it == true) colorOptionB else blackColor()
                },
                clickListener = {
                    viewModel.feedBack(FeedbackBad)
                },
                content = "需改进"
            )

            line(width = 1.dp(), height = 24.dp(), color = dividingLineColor())

            feedBackBtn(
                check = {
                    it == FeedbackGood
                },
                imageRes = R.raw.kudos,
                alphaVal = {
                    if (it == FeedbackBad) .3F else 1F
                },
                colorInt = {
                    if (it == true) colorOptionB else blackColor()
                },
                clickListener = {
                    viewModel.feedBack(FeedbackGood)
                },
                content = "有用"
            )
        }
    }

    private fun titleWidget(question: Question) {
        row(height = 44.dp()) {
            animateLayoutChange()
            gravity = Gravity.CENTER_VERTICAL
            padding(horizontal = 16.dp())
            text {
                text = "涨知识"
                textSize = 14F
                typeface = fontSans
                setTextColor(blackColor())
            }
            spacer(6.dp())
            text {
                text = "火热投票中"
                textSize = 10F
                setTextColor(whiteColor())
                padding(horizontal = 3.dp(), vertical = 1.dp())
                background {
                    cornerRadius = 3.dp().toFloat()
                    setColor("#FF2DC84E".toColorInt())
                }
                visibility(question.isVoteType())
            }
            spacer {
                linearLayoutParams(weight = 1f)
            }
            text {
                text = "分享"
                textSize = 14F
                alpha = .65F
                setTextColor(blackColor())
                padding(horizontal = 4.dp())
                focus({
                    !isVoteType() || isAnswered()
                }) {
                    visibility(it == true)
                }
                OnClickListener {
                    println("分享 分享")
                }
            }
        }
    }

    override fun isInEditMode(): Boolean {
        return true
    }

    private fun <R> focus(transform: Question.() -> R, observer: Observer<R>) =
        viewModel.uiState.focus(transform, observer)

}

fun whiteColor() = Color.WHITE

fun dividingLineColor() = Color.BLACK.alpha(.04F)

fun blackColor() = Color.BLACK
