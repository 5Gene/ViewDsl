package osp.sparkj.viewdsl.qa.data

data class Question(
    val time: Long = System.currentTimeMillis(),
    val answerDetail: String = "答案描述:鬼压床通常发生在压力比较大、过度疲劳、作息不正常或者焦虑的情形下容易发生。若出现鬼压床的情况，无需惊慌，可配合深呼吸慢慢恢复对自己身体的控制。",
    val businessType: String = "所属业务类型",//所属业务类型
    val contentType: String = "所属内容类型",//所属内容类型
    val question: String = "题干:发生“鬼压床”的情况需要看医生吗？",
    val homePageGuide: String = "首页引导文案",
    val optionA: String = "不需要",
    val optionAnswer: Int = 1,//选项答案1:optionA 2:optionB（题型为01是非题时才有选项答案）
    val optionB: String = "需要",
    val questionType: String = "01",//题型 默认01是非题
    val questionVote: QuestionVote = QuestionVote(),
    val referenceSource: String = "知乎专栏",
    val serialNo: Int = 1,
    var userAnswer: Int? = null,//用户选择的答案 可能为空
    var notShow: Boolean = false,//不显示 首页点击后就不显示
    var userFeedback: Int = 0
)

//1:optionA 2:optionB
val OptionA = 1
val OptionB = 2

val FeedbackNull = 0
val FeedbackGood = 1
val FeedbackBad = 2

fun Question.isAnswerRight(): Boolean = userAnswer == optionAnswer

fun Question.isAnswerA(): Boolean = userAnswer == 1

fun Question.isAnswered(): Boolean = userAnswer != null

fun Question.isVoteType(): Boolean = questionType == "01"

sealed class FeedBack {
    object Good : FeedBack()
    object Bad : FeedBack()
}

data class QuestionVote(
    //投票比例整数[0,100]
    val optionA: Int = 98,
    val optionB: Int = 2
)