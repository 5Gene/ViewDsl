package osp.sparkj.viewdsl.qa

import android.content.Context
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import osp.spark.view.wings.toast
import osp.sparkj.viewdsl.MainActivity
import osp.sparkj.viewdsl.R
import osp.sparkj.viewdsl.qa.data.FeedbackBad
import osp.sparkj.viewdsl.qa.data.FeedbackGood
import osp.sparkj.viewdsl.qa.data.FeedbackNull
import osp.sparkj.viewdsl.qa.data.OptionA
import osp.sparkj.viewdsl.qa.data.OptionB
import osp.sparkj.viewdsl.qa.data.Question
import osp.sparkj.viewdsl.qa.data.isAnswerA
import osp.sparkj.viewdsl.qa.data.isAnswerRight
import osp.sparkj.viewdsl.qa.data.isAnswered
import osp.sparkj.viewdsl.qa.data.isVoteType
import osp.sparkj.viewdsl.qa.theme.ColorOptionA
import osp.sparkj.viewdsl.qa.theme.ColorOptionB
import osp.sparkj.viewdsl.qa.theme.questionTheme
import java.text.NumberFormat

class QuestionLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isForceDarkAllowed = false
        }
    }

    private val viewModel: QuestUIViewModel =
        ViewModelProvider(context as MainActivity)[QuestUIViewModel::class.java]

    @Composable
    override fun Content() {
        questionTheme {
            val question = viewModel.uiState.value!!
            Box(modifier = Modifier.padding(16.dp)) {
                QuestionCard(question) {
                    if (question.isVoteType()) {
                        VoteWidget(question)
                    } else {
                        Knowlage(question)
                    }
                }
            }
        }
    }

    @Composable
    fun QuestionCard(question: Question, content: @Composable () -> Unit) {
        Card(
            elevation = 0.dp,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "涨知识",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "火热投票中",
                        modifier = Modifier
                            .wrapContentHeight()
                            .background(
                                color = MaterialTheme.colors.primary,
                                shape = RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                        color = Color.White,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.weight(1F))
                    val showShare = obst {
                        !isVoteType() || isAnswered()
                    }
                    AnimatedVisibility(visible = showShare) {
                        Text(
                            "分享",
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .alpha(.65F)
                                .clickable {
                                    toast("分享 分享")
                                },
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(
                    modifier = Modifier
                        .height(1.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .alpha(.4F)
                        .background(color = Color.Black)
                )
                Box(
                    modifier = Modifier.padding(
                        top = 16.dp,
                        bottom = 8.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                ) {
                    content()
                }
                val showFeedback = obst {
                    !isVoteType() || isAnswered()
                }
                AnimatedVisibility(showFeedback) {
                    FeedbackWidget()
                }
            }
        }
    }

    @Composable
    private fun FeedbackWidget() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            val feedBack = obst {
                userFeedback
            }
            val bad = feedBack == FeedbackBad
            val good = feedBack == FeedbackGood
            Row(
                modifier = Modifier
                    .weight(1F)
                    .height(44.dp)
                    .clickable(enabled = feedBack == FeedbackNull) {
                        viewModel.feedBack(FeedbackBad)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    tint = if (bad) ColorOptionB else Color.Unspecified,
                    painter = painterResource(id = R.drawable.ic_baseline_insert_comment),
                    modifier = Modifier
                        .size(20.dp)
                        .align(alignment = Alignment.CenterVertically)
                        .alpha(if (good) .3F else 1F),
                    contentDescription = null // decorative element
                )
                Text(
                    "需改进",
                    color = if (bad) ColorOptionB else Color.Unspecified,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .alpha(if (good) .3F else 1F),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
            )
            Row(
                modifier = Modifier
                    .weight(1F)
                    .height(44.dp)
                    .clickable(enabled = feedBack == FeedbackNull) {
                        viewModel.feedBack(FeedbackGood)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    tint = if (good) ColorOptionB else Color.Unspecified,
                    painter = painterResource(id = R.drawable.ic_baseline_thumb_up_alt),
                    modifier = Modifier
                        .size(20.dp)
                        .align(alignment = Alignment.CenterVertically)
                        .alpha(if (bad) .3F else 1F),
                    contentDescription = null // decorative element
                )
                Text(
                    "有用",
                    color = if (good) ColorOptionB else Color.Unspecified,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .alpha(if (bad) .3F else 1F),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }

    @Composable
    fun Knowlage(question: Question) {
        Column {
            Text(question.question, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                question.answerDetail, fontSize = 14.sp, modifier = Modifier
                    .padding(top = 12.dp)
                    .alpha(.65F)
            )
            Text(
                question.referenceSource, fontSize = 10.sp, modifier = Modifier
                    .padding(top = 6.dp)
                    .alpha(.3F)
            )
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun VoteWidget(question: Question) {
        Column {
            Text(
                question.question,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp),
                fontWeight = FontWeight.Medium
            )
            val percent = obst {
                questionVote.optionA / 100F
            }
            ComparisonChart(percent)
            val showVoteState = obst {
                !isAnswered()
            }
            AnimatedContent(targetState = showVoteState) {
                when (it) {
                    true -> VoteOptionsWidget(question)
                    else -> VoteResultWidget(question)
                }
            }
        }
    }

    @Composable
    fun ComparisonChart(percent: Float) {
        val train = remember {
            MutableTransitionState<Float>(0F).apply {
                targetState = 1F
            }
        }

        val updateTransition = updateTransition(train, "")
        val ani by updateTransition.animateFloat(transitionSpec = {
            tween(
                delayMillis = 100,
                durationMillis = 300,
                easing = FastOutLinearInEasing
            )
        }, label = "") {
            it
        }
        Canvas(
            modifier = Modifier
                .height(30.dp)
                .fillMaxWidth()
        ) {
            if (ani < 0.01) {
                return@Canvas
            }
            val radius = 6.dp.toPx()
            val dp4 = 4.dp.toPx()
            val dp5 = 5.dp.toPx()
            val miniWidth = 44.dp.toPx()
            val width = size.width - dp4
            val maxWidth = width - miniWidth
            val height = size.height

            val widthA = (width * percent).coerceAtMost(maxWidth).coerceAtLeast(miniWidth) * ani
            //left
            drawPath(Path().apply {
                moveTo(radius, 0F)
                cubicTo(radius, 0F, 0F, 0F, 0F, radius)
                lineTo(0F, height - radius)
                cubicTo(0F, height - radius, 0F, height, radius, height)
                lineTo(widthA - dp5, height)
                lineTo(widthA + dp5, 0F)
                close()
            }, color = ColorOptionA)

            //right
            val widthB =
                (width * (1 - percent)).coerceAtMost(maxWidth).coerceAtLeast(miniWidth) * ani
            drawPath(Path().apply {
                moveTo(size.width - radius, 0F)
                cubicTo(size.width - radius, 0F, size.width, 0F, size.width, radius)
                lineTo(size.width, height - radius)
                cubicTo(
                    size.width,
                    height - radius,
                    size.width,
                    height,
                    size.width - radius,
                    height
                )
                lineTo(size.width - widthB - dp5, height)
                lineTo(size.width - widthB + dp5, 0F)
                close()
            }, color = ColorOptionB)

            drawIntoCanvas {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 14.sp.toPx()
                }
                val percentInstance = NumberFormat.getPercentInstance().also {
                    it.maximumFractionDigits = 0
                }

                val textA = percentInstance.format(percent)
                val textB = percentInstance.format(1 - percent)

                val rect = android.graphics.Rect()
                paint.getTextBounds(textB, 0, textB.length, rect)
                val textHeight = rect.height()
                val textY = size.height / 2 + textHeight / 2
                it.nativeCanvas.drawText(textA, 12.dp.toPx(), textY, paint)
                it.nativeCanvas.drawText(
                    textB,
                    size.width - rect.width() - 12.dp.toPx(),
                    textY,
                    paint
                )
            }
        }
    }

    @Composable
    fun VoteOptionsWidget(question: Question) {
        Row(modifier = Modifier.padding(bottom = 15.dp, top = 20.dp)) {
            TextButton(
                onClick = {
                    viewModel.voteOption(OptionA)
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = ColorOptionA.copy(alpha = .1F)),
                shape = CircleShape,
                elevation = ButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp
                ),
                modifier = Modifier
                    .weight(1F)
                    .height(40.dp),
            ) {
                Text(question.optionA, color = ColorOptionA)
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = {
                    viewModel.voteOption(OptionB)
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = ColorOptionB.copy(alpha = .1F)),
                shape = CircleShape,
                elevation = ButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp
                ),
                modifier = Modifier
                    .weight(1F)
                    .height(40.dp),
            ) {
                Text(question.optionB, color = ColorOptionB)
            }
        }
    }

    @Composable
    fun VoteResultWidget(question: Question) {
        Column {
            Row(
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 20.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val answerA = obst {
                    isAnswerA()
                }
                Text(
                    if (answerA) "已选择:${question.optionA}" else question.optionA,
                    color = ColorOptionA
                )
                Text(
                    if (answerA) question.optionB else "已选择:${question.optionB}",
                    color = ColorOptionB
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colors.onSurface.copy(alpha = .04F),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                val answerRight = obst {
                    isAnswerRight()
                }
                Row {
                    Icon(
                        tint = if (answerRight) MaterialTheme.colors.primary else ColorOptionB,
                        painter = if (answerRight) painterResource(id = R.drawable.ic_baseline_emoji_emotions) else painterResource(
                            id = R.drawable.ic_baseline_error
                        ),
                        modifier = Modifier
                            .size(18.dp)
                            .align(alignment = Alignment.CenterVertically),
                        contentDescription = null // decorative element
                    )
                    Spacer(modifier = Modifier.width(2.5.dp))
                    Text(
                        if (answerRight) "回答正确" else "回答错误",
                        fontSize = 16.sp,
                        color = if (answerRight) MaterialTheme.colors.primary else ColorOptionB,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    question.answerDetail,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(vertical = 9.dp)
                        .alpha(.85F)
                )

                Text(
                    question.referenceSource,
                    fontSize = 10.sp,
                    modifier = Modifier.alpha(.3F),
                )
            }
        }
    }

    @Composable
    fun <R> obst(transform: Question.() -> R) = viewModel.uiState.focusOnAsState(transform)


    //androidx.compose.runtime.livedata.LiveDataAdapterKt.observeAsState(androidx.lifecycle.LiveData<T>, androidx.compose.runtime.Composer, int)
    @Composable
    fun <R, T> LiveData<T>.focusOnAsState(transform: T.() -> R): R {
        val lifecycleOwner = LocalLifecycleOwner.current
        if (value == null) {
            throw RuntimeException("LiveData must init data")
        }
        var state by remember { mutableStateOf(transform(value!!)) }
//    var state by remember { mutableStateOf(value!!.transform()) }
        DisposableEffect(this, lifecycleOwner) {
            val observer = Observer<T> {
                val data = transform(it)
                if (state != data) {
                    state = data
                }
            }
            observe(lifecycleOwner, observer)
            onDispose { removeObserver(observer) }
        }
        return state
    }
}
