package osp.spark.view.atelier

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.core.view.forEach

/**
 * 🧱 **WIFrameLayout (Window Insets FrameLayout)**
 *
 * 这是一个为了解决 Android 系统状态栏/导航栏分发“截断”问题而生的容器 ✨。
 * WI 代表 Window Insets，它确保了全员分发的公平性。
 *
 * 💡 **它解决了什么痛点？**
 * 在原生 [FrameLayout] 中，如果一个子 View 消耗了 WindowInsets（比如设置了 fitsSystemWindows="true"），
 * 默认的分发逻辑通常会停止，导致“同级”的其他子 View 拿不到这些信息，从而无法适配沉浸式。
 * 这个类强制“雨露均沾”，让每个子 View 都能收到系统的分发 📢。
 *
 * 🛠 **使用场景：**
 * 1️⃣ **沉浸式界面**：当你的容器里有多个 View（如自定义 Toolbar、侧滑菜单、悬浮按钮）都需要根据状态栏高度调整位置或内边距时。
 * 2️⃣ **动态添加 View**：通过监听层级变化，新加入的子 View 也能第一时间获得最新的系统缩进信息。
 *
 * ---
 * 🧐 **科普：fitsSystemWindows 到底是什么鬼？**
 *
 * 🌟 **当设置 android:fitsSystemWindows="true" 时：**
 * 系统会帮你给这个 View 加上 padding，确保它的内容【不会】被状态栏、导航栏遮挡。
 * 🚀 **效果**：就像穿了一件“防撞衣”，自动离屏幕边缘留出安全距离。
 *
 * 🌑 **当设置 android:fitsSystemWindows="false" (默认) 时：**
 * View 会完全无视系统栏，直接铺满整个屏幕，哪怕被状态栏压在下面也不管。
 * 🚀 **效果**：内容直接冲到状态栏后面，适合做真正的全屏背景图 🌌。
 *
 * ---
 * 🎨 **分发逻辑可视化：**
 *
 * ```text
 *    [ WIFrameLayout ]  <-- 收到系统 Insets ✉️
 *          |
 *          +-- 子View A (收到 ✉️) fits=true  --> 自动加 Padding 🟢
 *          |
 *          +-- 子View B (收到 ✉️) fits=false --> 保持原位铺满 🔵
 *          |
 *          +-- 子View C (收到 ✉️) ...        --> 全员收到，不掉队！✅
 * ```
 *
 * @author yun.
 * @date 2021/8/19
 * @since [https://github.com/ZuYun]
 */
class WIFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    init {
        // 监听层级变化：每当有新成员加入，都重新请求一次分发，保证新同学不掉队 🏃‍♂️
        setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                requestApplyInsets()
            }

            override fun onChildViewRemoved(parent: View?, child: View?) {
            }
        })
    }

    /**
     * 核心魔法 🪄：手动分发 Insets 给每一个子 View
     */
    override fun onApplyWindowInsets(insets: WindowInsets?): WindowInsets {
        // 强制遍历，确保每一个子 View 都有机会执行 dispatchApplyWindowInsets
        forEach {
            it.dispatchApplyWindowInsets(insets)
        }
        // 调用 super 保持原有的 View 树流程正常进行
        return super.onApplyWindowInsets(insets)
    }

}
