package osp.sparkj.viewdsl.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import osp.sparkj.viewdsl.bitmap.DofOverlayView

/**
 * 相机实时景深 View。
 *
 * ## 内部结构（从下到上）
 * 1. [PreviewView]：CameraX 相机画面，硬件加速 GPU 直通，无 CPU 开销
 * 2. [DofOverlayView]：雾化遮罩，人物区域被 DST_OUT 挖洞
 *
 * ## 使用
 * ```kotlin
 * // Activity / Fragment 里，权限通过后：
 * binding.dofCamera.bindCamera(this)
 * // 切换前/后摄：
 * binding.dofCamera.flipCamera()
 * ```
 *
 * ## 景深强度调节
 * ```kotlin
 * binding.dofCamera.fogAlpha = 0.8f  // 0f=无效果, 1f=背景全黑
 * binding.dofCamera.fogColor = Color.BLACK
 * ```
 */
class DofCameraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val previewView = PreviewView(context).also {
        it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        it.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        addView(it)
    }

    private val overlayView = DofOverlayView(context).also {
        it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(it)
    }

    var fogAlpha: Float
        get() = overlayView.fogAlpha
        set(value) {
            overlayView.fogAlpha = value
        }

    var fogColor: Int
        get() = overlayView.fogColor
        set(value) {
            overlayView.fogColor = value
        }

    /** 设置静态形状蒙版（对应 MaskActivity 的 bg_mask），为 null 则不使用。 */
    fun setFrameMask(drawable: Drawable?) = overlayView.setFrameMask(drawable)
    fun setFrameMask(bitmap: Bitmap?) = overlayView.setFrameMask(bitmap)

    private var currentFacing = CameraSelector.LENS_FACING_BACK
    private var lifecycleOwner: LifecycleOwner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var segmentor: FrameSegmentor? = null

    /**
     * 绑定 CameraX 生命周期，开始相机预览 + 分割。
     * 可重复调用（会先解绑旧的再绑新的）。
     *
     * @param owner Activity / Fragment，用于 CameraX 生命周期绑定
     * @param facing [CameraSelector.LENS_FACING_BACK] 或 [CameraSelector.LENS_FACING_FRONT]
     */
    fun bindCamera(owner: LifecycleOwner, facing: Int = CameraSelector.LENS_FACING_BACK) {
        lifecycleOwner = owner
        currentFacing = facing
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    /** 切换前/后摄，重新绑定。 */
    fun flipCamera() {
        currentFacing = if (currentFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindUseCases()
    }

    private fun bindUseCases() {
        val owner = lifecycleOwner ?: return
        val provider = cameraProvider ?: return

        // 释放旧的 segmentor
        segmentor?.close()

        val newSegmentor = FrameSegmentor { mask ->
            // onMaskReady 在 segmentor.executor 上回调，post 到主线程更新 View
            overlayView.post { overlayView.updatePersonMask(mask) }
        }
        segmentor = newSegmentor

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(currentFacing)
            .build()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // 低分辨率够 ML Kit 用，减少 CPU 压力
            .setTargetResolution(android.util.Size(640, 480))
            .build()
            .also { ia ->
                ia.setAnalyzer(newSegmentor.executor) { proxy ->
                    newSegmentor.process(proxy)
                }
            }

        provider.unbindAll()
        provider.bindToLifecycle(owner, cameraSelector, preview, imageAnalysis)
    }

    override fun onDetachedFromWindow() {
        segmentor?.close()
        segmentor = null
        super.onDetachedFromWindow()
    }
}
