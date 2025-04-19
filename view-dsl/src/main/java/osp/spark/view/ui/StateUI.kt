package osp.spark.view.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import osp.spark.view.dsl.box
import osp.spark.view.dsl.frameLayoutParams
import osp.spark.view.dsl.toolBar
import osp.spark.view.ui.UIState.Loading
import osp.spark.view.wings.classChange
import osp.spark.view.wings.log
import osp.spark.view.wings.mapNotNull
import java.lang.reflect.ParameterizedType
import kotlin.concurrent.thread

data class UIStateException(val code: Int, val msg: String) : Exception(msg, null, false, false)

sealed interface UIState {
    data class Loading(val tips: String) : UIState
    data class Success<D>(val loadingDialog: Boolean = false, val data: D? = null) : UIState
    data class Empty(val illustration: Int, val tips: String) : UIState
    data class Error(val illustration: Int? = null, val tips: String? = null) : UIState
}

interface UIEvent {
    //界面弹出toast
    data class Toast(val tips: String) : UIEvent
}

abstract class StateViewModel<D>(val stateHandle: SavedStateHandle) : ViewModel() {

    //内部使用不对UI开放，防止UI层修改, 默认加载中
    protected val _uiState = MutableLiveData(initialState())

    //对UI层开放，UI层监听数据变化更新UI
    val uiState: LiveData<UIState> = _uiState

    //<editor-fold desc="UI事件">
    private val _uiEvent = MutableLiveData<UIEvent>()
    val uiEvent: LiveData<UIEvent> = _uiEvent.distinctUntilChanged()//去掉粘性
    //</editor-fold>

    open fun initialState(): UIState = Loading("")

    //扩展方法，更新MutableLiveData中数据的某项内容
    protected fun MutableLiveData<UIState>.update(change: UIState.() -> UIState) {
        postValue(value!!.change())
    }

    /**
     * 初始数据入口，请求数据入口，控制UI显示状态
     */
    fun request() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                showLoading()
                val result = doRequest(stateHandle)
                if (result == null) {
                    showEmpty(0, "")
                    return@launch
                }
                if (result is Collection<*> && result.isEmpty()) {
                    showEmpty(0, "")
                    return@launch
                }
                showSucceed(result)
            } catch (e: Exception) {
                e.message?.log("request")
                when (e) {
                    is UIStateException -> {
                        showError(0, "")
                    }

                    else -> {
                        showError(0, "")
                    }
                }
            }
        }
    }

    /**
     * Activity一创建就会立刻执行此方法
     *  - 用于请求UI所需要的数据，可执行耗时操作
     *  - 方法结束后UI页面状态会从加载中状态切换到加载成功状态显示业务具体
     *  - 方法返回null会显示空页面
     *  - 此方法内抛出UIStateException时会显示对应UI状态，不抛出UIStateException会显示默认错误页面
     *      - 所有状态都定义在 **[UIState]** 中
     */
    abstract suspend fun doRequest(stateHandle: SavedStateHandle): D?

    fun showLoading() {
        _uiState.postValue(UIState.Loading(""))
    }

    protected fun showSucceed(data: D) {
        _uiState.postValue(UIState.Success(data = data))
    }

    protected fun showEmpty(illustration: Int, tips: String) {
        _uiState.postValue(UIState.Empty(illustration, tips))
    }

    protected fun showError(illustration: Int, tips: String) {
        _uiState.postValue(UIState.Error(illustration, tips))
    }

    /**
     * UI操作后更新局部数据对应更新UI局部
     * ```
     * update {
     *    copy(data = new_data)
     * }
     * ```
     */
    protected fun update(update: D.() -> D) {
        _uiState.update {
            if (this is UIState.Success<*>) {
                UIState.Success(data = update(data!! as D))
            } else {
                throw IllegalStateException("must be invoke in success state")
            }
        }
    }


    private fun showLoadingDialog() {
        _uiState.update {
            if (this is UIState.Success<*>) {
                copy(loadingDialog = true)
            } else {
                throw IllegalStateException("must be invoke in success state")
            }
        }
    }

    private fun finishLoadingDialog() = _uiState.update {
        if (this is UIState.Success<*>) {
            copy(loadingDialog = false)
        } else {
            throw IllegalStateException("must be invoke in success state")
        }
    }

    /**
     * UI点击控件，执行耗时操作的时候，显示加载中弹窗，执行完之后隐藏弹窗
     */
    fun doWithLoadingDialog(onError: (() -> Unit)? = null, action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                showLoadingDialog()
                action()
            } catch (e: Exception) {
                e.message?.log("StateViewModel")
                onError?.invoke()
            } finally {
                finishLoadingDialog()
            }
        }
    }

    init {
        thread {
            request()
        }
    }

    /**
     * 箭头UI数据的局部更新
     */
    fun <R> focusOn(transform: D.() -> R?): LiveData<R> = uiState.mapNotNull {
        (this as UIState.Success<D>).data!!.transform()
    }

    override fun onCleared() {
        super.onCleared()
        "BasicStateViewModel $this onCleared ..".log()
    }
}

abstract class StateActivity<D, VM : StateViewModel<D>> : AppCompatActivity() {

    private var subContentView: View? = null

    /**
     * 抽象方法 title 返回活动标题的资源 ID。
     * @return Int 标题的资源 ID。
     */
    abstract fun title(): Int

    /**
     * 抽象属性 vm 表示用于设备状态管理的 ViewModel。
     * 此 ViewModel必须是 StateViewModel的子类
     */
    val vm: VM by lazy {
        val clazz = StateActivity::class.java
        val superClass = clazz.genericSuperclass
        val actualType = (superClass as ParameterizedType).actualTypeArguments[1] as Class<VM>
        ViewModelProvider(
            viewModelStore,
            defaultViewModelProviderFactory,
            defaultViewModelCreationExtras
        )[actualType]
    }

    override fun setTitle(title: CharSequence?) {
        val titleRes = title()
        if (titleRes == 0) {
            super.setTitle("")
        } else {
            super.setTitle(getString(titleRes))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            val showId = View.generateViewId()
            toolBar {
                setSupportActionBar(this)
            }
            box(id = showId) {
                // 监听设备连接状态变化
                vm.uiState.classChange()
                    .observe(this@StateActivity) { state ->
                        onUiStateChange(state, showId)
                    }
            }
            frameLayoutParams(-1, -1)
        })
        val activityContent = findViewById<FrameLayout>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(activityContent) { v: View, insets: WindowInsetsCompat ->
            v.updatePadding(bottom = insets.systemWindowInsetBottom)
            insets.consumeSystemWindowInsets()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true) // 可选，即使在使用 DrawerLayout 时也显示返回箭头
        supportActionBar?.setHomeButtonEnabled(true)    // 可选，启用返回按钮的点击

//        vm.loadingState.observe(this) {
//            if (it) {
//                showDialogLoading(getString(com.heytap.health.base.R.string.lib_common_loading))
//            } else {
//                closeDialogLoading()
//            }
//        }
    }

    private fun FrameLayout.onUiStateChange(state: UIState, showId: Int) {
        supportFragmentManager.commit(true) {
            setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            val fragmentShow = fragmentFromState(state)
            replace(showId, fragmentShow)
        }

        if (state is UIState.Success<*>) {
            if (subContentView == null) {
                subContentView = findViewById<FrameLayout>(android.R.id.content).addViewToActivityContent()
            }
            if (subContentView?.isVisible == false) {
                subContentView!!.isVisible = true
            }
        } else if (subContentView?.isVisible == true) {
            subContentView!!.isVisible = false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    open fun fragmentFromState(state: UIState): Fragment {
        val fragmentShow = if (state is UIState.Success<*>) {
            showSuccessFragment(state.data as D).apply {
                arguments = bundleOf("title" to title())
            }
        } else {
            showStateFragment(state)
        }
        return fragmentShow
    }

    /**
     * @param state UIState 具体要显示的状态
     * @return Fragment 具体显示的状态页面。
     */
    open fun showStateFragment(state: UIState): Fragment = StateFragment(state)

    /**
     * #### 数据加载成功之后要显示的fragment
     * - ***此方法在 [StateViewModel#doRequest()] 执行结束后执行***
     * - 子类必须实现，数据加载成功之后显示什么fragment
     *  - 已有3类实现了标题栏的基类Fragment
     *      - **PrefDslFragment，设置风格Fragment，通过PrefDsl构建页面不需要xml布局**
     *      - **ComposeFragment，使用compose绘制界面**
     *      - **ViewDslFragment，使用view绘制界面，通过ViesDsl构建页面不需要xml布局**
     *
     * @return Fragment 数据加载成功之后要显示的 fragment
     */
    abstract fun showSuccessFragment(data: D): Fragment

    /**
     * 加载成功之后，往Activity的content布局添加额外控件
     * - 比如：页面有底部按钮可以覆写此方法添加底部按钮到content
     */
    open fun FrameLayout.addViewToActivityContent(): View? = null
}