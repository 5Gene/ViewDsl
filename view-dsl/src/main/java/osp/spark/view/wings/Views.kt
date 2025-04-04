package osp.spark.view.wings

import android.text.InputFilter
import android.view.View
import android.view.View.NO_ID
import android.view.View.generateViewId
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.forEach
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import osp.spark.view.auxiliary.ViewCoroutineScope
import osp.spark.view.auxiliary._Tag
import osp.spark.view.dsl.text

fun Float.set(check: Float.() -> Boolean = { this > 0 }, setValue: (Float) -> Unit) {
    if (check()) {
        setValue(this)
    }
}

fun View.transForm(
    rotateX: Float = 0F, rotateY: Float = 0F,
    translateX: Float = 0F, translateY: Float = 0F,
    nscaleX: Float = 1F, nscaleY: Float = 1F, locationZ: Float = 0F,
    widthFactor: Float = .5F, heightFactor: Float = .5F,
) {
    if (width == 0) {
        post {
            transForm(
                rotateX, rotateY, translateX, translateY, scaleX, scaleY, locationZ, widthFactor, heightFactor
            )
        }
        return
    }
    translateX.set {
        translationX = it
    }
    translateY.set {
        translationY = it
    }
    nscaleX.set({ this != 1F }) {
        this.scaleX = it
    }
    nscaleY.set({ this != 1F }) {
        this.scaleY = it
    }
//    https://stackoverflow.com/questions/24592731/android-flip-animation-not-flipping-smoothly
    locationZ.set({ this != 0F }) {
        cameraDistance = it
    }

    pivotX = width * widthFactor
    pivotY = height * heightFactor
    rotateX.set {
        rotationX = it
    }
    rotateY.set {
        rotationY = it
    }
}

fun MaterialButton.removeInnerPaddingAndShadow(radius: Int = 0) {
    cornerRadius = radius
    //<editor-fold desc="去掉button的内部上下内边距 下面两个内边距是为了 按压的时候显示深度阴影的">
    insetTop = 0
    insetBottom = 0
    //</editor-fold>
    //移除按压的阴影效果
    stateListAnimator = null
}

/**
 * 向下遍历子 View
 */
fun View.forEachDeep(ignore: View? = null, action: View.() -> Unit) {
    action()
    if (this is ViewGroup) {
        forEach {
            if (it != ignore) {
                it.forEachDeep(action = action)
            }
        }
    }
}

fun View.forEachDeepWithIndex(ignore: View? = null, deep: Int = 0, index: Int = 0, action: View.(Int, Int) -> Unit): Int {
    action(deep, index)
    var newIndex = index
    if (this is ViewGroup) {
        val nextDeep = deep + 1
        forEach {
            if (it != ignore) {
                newIndex = it.forEachDeepWithIndex(ignore, nextDeep, ++newIndex, action)
            }
        }
    }
    return newIndex
}

/**
 * 上下遍历所有View
 */
fun View.forEachViewTreeWithIndex(child: View? = null, deep: Int = 0, index: Int = 0, action: View.(Int, Int) -> Unit): Int {
    var nextIndex = forEachDeepWithIndex(child, deep, index, action)
    val viewParent = parent
    if (viewParent is ViewGroup) {
        nextIndex = viewParent.forEachViewTreeWithIndex(this, deep - 1, ++nextIndex, action)
    }
    return nextIndex
}

/**
 * ViewTree上下遍历所有View
 */
fun View.forEachViewTree(child: View? = null, action: View.() -> Unit) {
    forEachDeep(child, action)
    val viewParent = parent
    if (viewParent is ViewGroup) {
        viewParent.forEachViewTree(this, action)
    }
}

/**
 * 此view以及所有深度子View都修改enable
 * isSelected,view中会调用dispatchSetSelected会传递，但只会传递一级
 */
fun View.setEnableDeep(enable: Boolean) {
    forEachDeep {
        isEnabled = enable
    }
}

fun View.setSelectedDeep(enable: Boolean) {
    forEachDeep {
        if (this is ViewGroup) {
            isSelected = enable
        }
    }
}

fun View.padding(horizontal: Int? = null, vertical: Int? = null) {
    updatePadding(
        horizontal ?: paddingStart,
        vertical ?: paddingTop,
        horizontal ?: paddingEnd,
        vertical ?: paddingBottom
    )
}

fun View.padding(
    left: Number? = null,
    top: Number? = null,
    right: Number? = null,
    bottom: Number? = null
) {
    throw IllegalStateException("请使用 -> View.updatePadding()")
}

fun View.padding(padding: Int) {
    setPadding(padding)
}

fun View.visibility(visible: Boolean) {
    isVisible = visible
//        visibility = if (visible) View.VISIBLE else View.GONE
}

fun View.isVisible(): Boolean = isVisible

fun View.visible() {
    isVisible = true
//    visibility = View.VISIBLE
}


fun View.gone() {
    isGone = true
//    visibility = View.GONE
}


fun View.invisible() {
    isInvisible = true
//    visibility = View.INVISIBLE
}


fun View.showShortSnackbar(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_SHORT).show()
}


fun View.showShortSnackbar(@StringRes stringResId: Int) {
    Snackbar.make(this, stringResId, Snackbar.LENGTH_SHORT).show()
}


fun View.showLongSnackbar(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_LONG).show()
}


fun View.showLongSnackbar(@StringRes stringResId: Int) {
    Snackbar.make(this, stringResId, Snackbar.LENGTH_LONG).show()
}


fun View.showActionSnackBar(
    message: String,
    actionName: String,
    block: () -> Unit
) {
    Snackbar.make(this, message, Snackbar.LENGTH_LONG)
        .setAction(actionName) {
            block()
        }.show()
}

inline fun <reified VM : ViewModel> View.viewModels(
    noinline extrasProducer: (() -> CreationExtras)? = null,
    noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
): Lazy<VM> {
    val activity: AppCompatActivity = context.safeAs()!!
    return activity.viewModels<VM>(extrasProducer, factoryProducer)
//    val factoryPromise = factoryProducer ?: {
//        activity.defaultViewModelProviderFactory
//    }
////    只有在Activity的setContent被调用的时候才会执行 initViewTreeOwners()
////    findViewTreeLifecycleOwner() //只有在view被添加进activity之后 不能在构造方法中使用
////    findViewTreeViewModelStoreOwner()
//    return ViewModelLazy(
//        VM::class,
//        { activity.viewModelStore },
//        factoryPromise,
//        { extrasProducer?.invoke() ?: activity.defaultViewModelCreationExtras }
//    )
}

private val JOB_KEY = generateViewId()

val View.viewScope: CoroutineScope
    get() {
        return tag {
            JOB_KEY.hashCode() redLine ViewCoroutineScope(this@viewScope, SupervisorJob() + Dispatchers.Main.immediate)
        }
    }


inline fun <reified B> View.tag(block: _Tag.() -> B) = _Tag(this).run(block)


inline fun <reified B> Int.toTag(view: View) = view.getTag(this).safeAs<B>()


fun View.topLayer(onAttach: FrameLayout.(AppCompatActivity) -> Unit) {
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            //这里开始已经可以获取宽高了
            (context as? AppCompatActivity)?.apply {
                onAttach(window.decorView.findViewById(android.R.id.content), this)
            }

        }

        override fun onViewDetachedFromWindow(v: View) {
            removeOnAttachStateChangeListener(this)
        }
    })

}

fun View.topLayerTip(more: ((TextView) -> Unit)? = null): MutableLiveData<String> {
    val source = MutableLiveData<String>()
    topLayer {
        text(width = -1, height = -1) {
            textSize = 16f
            source.observe(it) {
                text = it
            }
            more?.invoke(this)
        }
    }
    return source
}

//https://developer.android.com/develop/ui/compose/layouts/insets?hl=zh-cn
fun ViewGroup.windowInset() {
    setOnApplyWindowInsetsListener(object : View.OnApplyWindowInsetsListener {
        override fun onApplyWindowInsets(v: View, insets: WindowInsets): WindowInsets {
            return insets
        }
    })
}

fun View.checkId(idset: Int = NO_ID): View {
    if (id == NO_ID) {
        id = if (idset == NO_ID) generateViewId() else idset
    }
    return this
}

fun EditText.maxLength(max: Int) {
    filters = arrayOf(InputFilter.LengthFilter(max))
}
