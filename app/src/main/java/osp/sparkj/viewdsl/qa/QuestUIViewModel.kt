package osp.sparkj.viewdsl.qa

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import osp.sparkj.viewdsl.qa.data.OptionA
import osp.sparkj.viewdsl.qa.data.Question
import osp.sparkj.viewdsl.qa.data.QuestionVote

class QuestUIViewModel : ViewModel() {

    private val _questionState = MutableLiveData(Question())
    val uiState: LiveData<Question>
        get() = _questionState


    fun voteOption(answer: Int) {
        val question = _questionState.value!!
        val questionVote = if (answer == OptionA) {
            QuestionVote(question.questionVote.optionA + 1, question.questionVote.optionB - 1)
        } else {
            QuestionVote(question.questionVote.optionA - 1, question.questionVote.optionB + 1)
        }
        _questionState.value = question.copy(userAnswer = answer, questionVote = questionVote)
    }

    fun feedBack(feel: Int) {
        _questionState.value?.run {
//            if (userFeedback != FeedbackNull) {
//                return
//            }
            _questionState.value = copy(userFeedback = feel)
        }
    }
}