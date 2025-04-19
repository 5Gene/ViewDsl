package osp.spark.view.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import osp.spark.view.dsl.box
import osp.spark.view.dsl.frameLayoutParams
import osp.spark.view.dsl.toolBar
import osp.spark.view.ui.UIState.Loading
import osp.spark.view.wings.classChange
import osp.spark.view.wings.findActualType
import osp.spark.view.wings.focusOn
import osp.spark.view.wings.log
import osp.spark.view.wings.mapNotNull
import kotlin.concurrent.thread

data class UIStateException(val code: Int, val msg: String) : Exception(msg, null, false, false)

sealed interface UIState<out D> {
    data class Loading(val tips: String) : UIState<Nothing>
    data class Success<D>(val loadingDialog: Boolean = false, val data: D) : UIState<D>
    data class Empty(val illustration: Int, val tips: String) : UIState<Nothing>
    data class Error(val illustration: Int? = null, val tips: String? = null) : UIState<Nothing>
}

interface UIEvent {
    //界面弹出toast
    data class Toast(val tips: String) : UIEvent
}

abstract class StateViewModel<D>(val stateHandle: SavedStateHandle) : ViewModel() {

    protected val _uiState = MutableLiveData(initialState())
    val uiState: LiveData<UIState<D>> = _uiState

    //<editor-fold desc="UI事件">
    private val _uiEvent = MutableLiveData<UIEvent>()
    val uiEvent: LiveData<UIEvent> = _uiEvent.distinctUntilChanged()//去掉粘性
    //</editor-fold>

    open fun initialState(): UIState<D> = Loading("")

    //扩展方法，更新MutableLiveData中数据的某项内容
    protected fun MutableLiveData<UIState<D>>.update(change: UIState<D>.() -> UIState<D>) {
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
                        showError(0, e.msg)
                    }

                    else -> {
                        showError(0, e.message ?: "")
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
        _uiState.postValue(Loading(""))
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
            if (this is UIState.Success<D>) {
                UIState.Success(data = update(data))
            } else {
                throw IllegalStateException("must be invoke in success state")
            }
        }
    }


    private fun showLoadingDialog() {
        _uiState.update {
            if (this is UIState.Success<D>) {
                copy(loadingDialog = true)
            } else {
                throw IllegalStateException("must be invoke in success state")
            }
        }
    }

    private fun finishLoadingDialog() = _uiState.update {
        if (this is UIState.Success<D>) {
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
        (it as UIState.Success<D>).data.transform()
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
//        val clazz = StateActivity::class.java
//        val superClass = clazz.genericSuperclass
//        val actualType = (superClass as ParameterizedType).actualTypeArguments[1] as Class<VM>
//        ViewModelProvider(
//            viewModelStore,
//            defaultViewModelProviderFactory,
//            defaultViewModelCreationExtras
//        )[actualType]
        val vmClass = this@StateActivity.findActualType<VM>(1)
        ViewModelProvider(
            viewModelStore,
            defaultViewModelProviderFactory,
            defaultViewModelCreationExtras
        )[vmClass]
    }

    protected val insetsController: WindowInsetsControllerCompat by lazy {
        WindowCompat.getInsetsController(window, window.decorView)
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
//      https://developer.android.google.cn/develop/ui/views/layout/edge-to-edge?hl=zh-cn
//      默认情况下，enableEdgeToEdge 会将系统栏设为透明，但在“三按钮”导航模式下，状态栏会显示半透明纱罩。系统图标和纱罩的颜色会根据系统浅色或深色主题来调整。
//      enableEdgeToEdge 方法会自动声明应用应全屏布局，并调整系统栏的颜色。
        enableEdgeToEdge()
//      https://developer.android.google.cn/jetpack/compose/layouts/insets?hl=zh-cn
        window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
        super.onCreate(savedInstanceState)
        //全屏 显示到status bar下面 在系统栏后布置您的应用 设置doctorView不要填充statusbar
        // 设置内容扩展到状态栏
        WindowCompat.setDecorFitsSystemWindows(window, false)
//        window.decorView.systemUiVisibility = (
//                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                )

        // 设置状态栏颜色为透明
        insetsController.isAppearanceLightStatusBars = true
        window.statusBarColor = 0x00000000

        setContentView(LinearLayout(this).apply {
            val showId = View.generateViewId()
            toolBar {
//            fitsSystemWindows = true
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
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // 可选，即使在使用 DrawerLayout 时也显示返回箭头
        supportActionBar?.setHomeButtonEnabled(true)    // 可选，启用返回按钮的点击

        val activityContent = findViewById<FrameLayout>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(activityContent) { v: View, insets: WindowInsetsCompat ->
//            v.updatePadding(bottom = insets.systemWindowInsetBottom)
            v.updatePadding(bottom = WindowInsetsCompat.Type.navigationBars())
            WindowInsetsCompat.CONSUMED
        }

//        vm.loadingState.observe(this) {
//            if (it) {
//                showDialogLoading(getString(com.heytap.health.base.R.string.lib_common_loading))
//            } else {
//                closeDialogLoading()
//            }
//        }
    }

    private fun FrameLayout.onUiStateChange(state: UIState<D>, showId: Int) {
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

    open fun fragmentFromState(state: UIState<D>): Fragment {
        val fragmentShow = if (state is UIState.Success<D>) {
            showSuccessFragment(state.data).apply {
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
    open fun showStateFragment(state: UIState<D>): Fragment = StateFragment(state)

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


    @Suppress("UNCHECKED_CAST")
    fun <R, T> LiveData<T>.observeOn(
        transform: (T) -> R = { this as R },
        observer: Observer<R>
    ) {
        focusOn(transform).observe(this@StateActivity, observer)
    }

    fun <R, T> Flow<T>.collectOn(transform: (T) -> R, collector: FlowCollector<R>) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                focusOn(transform).collect(collector)
            }
        }
    }
}