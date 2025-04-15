package osp.spark.view.ui

import android.R
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
import osp.spark.view.wings.log
import java.lang.reflect.ParameterizedType
import kotlin.concurrent.thread

data class UIStateException(val code: Int, val msg: String) : Exception(msg, null, false, false)

sealed interface UIState {
    data class Loading(val tips: String) : UIState
    data class Success<D>(val data: D? = null) : UIState
    data class Empty(val illustration: Int, val tips: String) : UIState
    data class Error(val illustration: Int? = null, val tips: String? = null) : UIState
}

interface UIEvent {
    //界面中间显示loading弹簧
    data class Loading(val tips: String) : UIEvent
    object None : UIEvent

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
    val uiEvent: LiveData<UIEvent> = _uiEvent
    //</editor-fold>

    open fun initialState(): UIState = Loading("")

    //扩展方法，更新MutableLiveData中数据的某项内容
    protected fun MutableLiveData<UIState>.update(change: UIState.() -> UIState) {
        thread {
            value = value!!.change()
        }
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
                showSucceed { result }
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
     *      - 所有状态都定义在 **[DevicePageType]** 中
     */
    abstract suspend fun doRequest(stateHandle: SavedStateHandle): D?

    fun showLoading() {
        _uiState.postValue(UIState.Loading(""))
    }

    fun showSucceed(update: (D?) -> D) {
        _uiState.update {
            if (this is UIState.Success<*>) {
                UIState.Success(update(data!! as D))
            } else {
                UIState.Success(update(null))
            }
        }
    }

    protected fun showEmpty(illustration: Int, tips: String) {
        _uiState.postValue(UIState.Empty(illustration, tips))
    }

    protected fun showError(illustration: Int, tips: String) {
        _uiState.postValue(UIState.Error(illustration, tips))
    }

    protected fun update(update: D.() -> D) {
        _uiState.update {
            if (this is UIState.Success<*>) {
                UIState.Success(update(data!! as D))
            } else {
                throw IllegalStateException("must be invoke in success state")
            }
        }
    }


    private fun eventLoading() {
        _uiEvent.postValue(UIEvent.Loading(""))
    }

    private fun eventFinishLoading() = _uiEvent.postValue(UIEvent.None)

    /**
     * UI点击控件，执行耗时操作的时候，显示加载中弹窗，执行完之后隐藏弹窗
     */
    fun doWithLoadingEvent(onError: (() -> Unit)? = null, action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                eventLoading()
                action()
            } catch (e: Exception) {
                e.message?.log("StateViewModel")
                onError?.invoke()
            } finally {
                eventFinishLoading()
            }
        }
    }

    init {
        thread {
            request()
        }
    }

    override fun onCleared() {
        super.onCleared()
        "BasicStateViewModel $this onCleared ..".log()
    }
}

abstract class StateActivity<D, VM : StateViewModel<D>> : AppCompatActivity() {

    var subContentView: View? = null

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


    /**
     * 覆盖 setTitle 方法，根据 title 方法返回的资源 ID 设置活动标题。
     * 如果资源 ID 为 0，则设置为空字符串。
     * @param title CharSequence? 要设置的标题，在此重写中未使用。
     */
    override fun setTitle(title: CharSequence?) {
        val titleRes = title()
        if (titleRes == 0) {
            super.setTitle("")
        } else {
            super.setTitle(getString(titleRes))
        }
    }

    /**
     * 处理活动创建，初始化 UI 和观察者。
     * 设置活动的主题、全屏模式和布局。
     * 观察设备连接状态变化和加载状态以相应地更新 UI。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            val showId = View.generateViewId()
            toolBar {
                setSupportActionBar(this)
            }
            box(id = showId) {
                // 监听设备连接状态变化
                vm.uiState.distinctUntilChanged()
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
            setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            val fragmentShow = fragmentFromState(state)
            replace(showId, fragmentShow)
        }

        if (state is UIState.Success<*>) {
            if (subContentView == null) {
                subContentView = findViewById<FrameLayout>(R.id.content).addViewToActivityContent()
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

    /**
     * ## 作用：
     *   #### 根据传入的不同state，显示不同的ui，ui通过fragment提供
     *
     * - UI层通过ViewModel提供的uiState来更新对应的UI状态(加载中，加载成功，加载失败)
     * - 在BasicDeviceStateViewModel中的request()方法是请求数据的入口，默认uiState的状态是：加载中
     * - 在BasicDeviceStateViewModel中控制UI显示什么state
     *
     * @param state DevicePageType 当前设备页面类型，表示连接状态。
     * @return Fragment 基于设备连接状态要显示的片段。
     *
     */
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
     * 创建并返回一个带有指定标题和状态的状态片段。
     * - 此方法主要显示loading和error的状态，根据不同的error显示不同的错误页面
     *
     * @param state UIState 要在片段中显示的设备页面类型。
     * @return Fragment 一个初始化了给定标题和状态的 StateFragment 实例。
     */
    open fun showStateFragment(state: UIState): Fragment = StateFragment(state)

    /**
     * - 子类必须实现，数据加载成功之后显示什么fragment
     * - 由于Activity没有实现标题栏，所以fragment需要自己实现标题栏
     *  - 已有3类实现了标题栏的基类Fragment
     *      - **BasicPreferenceFragment，卡片风格Fragment，比如设置页面**
     *      - **BasicComposeToolbarFragment，使用compose绘制界面**
     *      - **BasicViewToolbarFragment，使用view绘制界面，通过ViesDsl构建页面不需要xml布局**
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