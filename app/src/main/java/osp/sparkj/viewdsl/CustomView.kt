package osp.sparkj.viewdsl

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.FrameLayout
import osp.spark.view.dsl.text
import osp.spark.view.wings.dp

class CustomView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    init {
        setBackgroundColor(Color.BLUE)
        text(100.dp(), 100) {
            text = "da666"
            setBackgroundColor(Color.RED)
        }
    }
}