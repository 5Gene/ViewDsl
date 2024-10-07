package osp.sparkj.viewdsl

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import osp.june.dsl.preference.PrefCategory
import osp.june.dsl.preference.PrefWidget
import osp.june.dsl.preference.buildScreen
import osp.june.dsl.preference.category
import osp.june.dsl.preference.checkBox
import osp.june.dsl.preference.linearLayout
import osp.june.dsl.preference.screen
import osp.june.dsl.preference.seekBar
import osp.june.dsl.preference.switch
import osp.june.dsl.preference.switchCompat
import osp.june.dsl.text

class MyPreferenceFragmentCompat : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        byBuildScreen()
//        byDsl()
    }
}

private fun MyPreferenceFragmentCompat.byDsl() {
    screen {
        category("title") {
            switch("te", "标题", "描述", R.drawable.ic_launcher_background)
            switchCompat("te3", "标题", "描述", R.drawable.ic_launcher_background)
        }
        checkBox("s7s", "cb") {
            println("0000000000   $isChecked")
        }
        seekBar("s00s", "cb3") {
            println("0000000000   $value")
        }

        category("title") {
            checkBox("s7s", "cb") {
                println("0000000000   $isChecked")
            }
            seekBar("s00s", "cb3") {
                println("0000000000   $value")
            }
        }
        //switchCompat("te", "标题", "描述", R.drawable.ic_launcher_background)
    }
}

private fun MyPreferenceFragmentCompat.byBuildScreen() {
    buildScreen(
        listOf(
            PrefCategory("你好",
                listOf<PrefWidget>(
                    object : PrefWidget {
                        override fun PreferenceGroup.content() {
                            checkBox("s7s", "cb", "描述", R.drawable.ic_launcher_background) {
                                println("0000000000   $isChecked")
                            }
                            switch("te", "标题", "描述", R.drawable.ic_launcher_background)
                        }
                    }
                )
            ),
            PrefCategory("世界",
                listOf<PrefWidget>(
                    object : PrefWidget {
                        override fun PreferenceGroup.content() {

                            seekBar("s00s", "cb3", "描述", R.drawable.ic_launcher_background) {
                                println("0000000000   $value")
                            }

                            linearLayout {
                                isClickable = true
                                text {
                                    text = "拉拉"
                                }
                            }
                        }
                    }
                )
            ),
        ),
    )
}

class PreferenceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.commit {
            val myPreferenceFragmentCompat: androidx.fragment.app.Fragment = MyPreferenceFragmentCompat()
            replace(android.R.id.content, myPreferenceFragmentCompat)
        }
    }
}

