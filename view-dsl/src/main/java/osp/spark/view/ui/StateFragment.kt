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
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import osp.spark.view.dsl.preference.screen
import osp.spark.view.dsl.text

abstract class ViewDslFragment<D>(val data: D) : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return LinearLayout(inflater.context).apply { onShowContent(data) }
    }

    abstract fun LinearLayout.onShowContent(data: D)
}

abstract class PrefDslFragment<D>(val data: D) : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        screen {
            onShowContent(data)
        }
    }

    abstract fun PreferenceScreen.onShowContent(data: D)
}

abstract class PrefDslDialogFragment<D>(val data: D) : PreferenceDialogFragmentCompat() {


    override fun onCreateDialogView(context: Context): View? {
        return super.onCreateDialogView(context)
    }

    abstract fun PreferenceScreen.onShowContent(data: D)
}

abstract class ComposeFragment<D>(val data: D) : Fragment() {

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

class StateFragment(state: UIState) : ViewDslFragment<UIState>(state) {
    override fun LinearLayout.onShowContent(data: UIState) {
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