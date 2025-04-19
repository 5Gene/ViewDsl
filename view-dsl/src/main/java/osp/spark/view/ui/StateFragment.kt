package osp.spark.view.ui

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import osp.spark.view.dsl.preference.screen
import osp.spark.view.dsl.text

open class GodFragment(contentLayoutId: Int = 0) : Fragment(contentLayoutId) {

    fun <T> LiveData<T>.observer(observer: Observer<T>) {
        observe(viewLifecycleOwner, observer)
    }

    fun <T> Flow<T>.observer(collector: FlowCollector<T>) {
        viewLifecycleOwner.apply {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    collect(collector)
                }
            }
        }
    }
}

abstract class ViewDslFragment<D>(val data: D) : GodFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return LinearLayout(inflater.context).apply { onShowContent(data) }
    }

    abstract fun LinearLayout.onShowContent(data: D)
}

abstract class PrefDslFragment<D>(val data: D) : PreferenceFragmentCompat() {

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        screen {
            onShowContent(data)
        }
    }

    abstract fun PreferenceScreen.onShowContent(data: D)
}

abstract class ComposeFragment<D>(val data: D) : GodFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return ComposeView(inflater.context).apply {
            setContent {
                ShowContent(data)
            }
        }
    }

    @Composable
    abstract fun ShowContent(data: D)
}

class StateFragment(state: UIState<*>) : ViewDslFragment<UIState<*>>(state) {
    override fun LinearLayout.onShowContent(data: UIState<*>) {
        if (data is UIState.Loading) {
            val tips = data.tips

        } else if (data is UIState.Empty) {
            val illustration = data.illustration
            val tips = data.tips

        } else if (data is UIState.Error) {
            val illustration = data.illustration
            val tips = data.tips
        } else {
            text(-1, -1) {
                text = "state 错误"
                gravity = Gravity.CENTER
            }
        }
    }
}