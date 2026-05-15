package osp.sparkj.viewdsl.glvideo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toBitmap
import osp.sparkj.viewdsl.glvideo.render.RenderThread

/**
 * 基于 OpenGL ES 3.0 的视频景深控件。
 *
 * ## 功能
 * - 单视频源；前景 = 视频 × MediaPipe 人物分割 × `out_mask.png`
 * - 背景 = 视频（或纯色）× `bg_mask2.png`
 * - 与 [osp.sparkj.viewdsl.bitmap.ZoomImageView] 一致的双击 / 双指 / 拖动手势
 * - [export] 把当前所见（含 zoom/drag）烧录为 MP4
 *
 * ## XML 用法
 * ```xml
 * <osp.sparkj.viewdsl.glvideo.GlMaskVideoView
 *     android:id="@+id/gl_mask_video"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" />
 * ```
 * 高度按视频宽高比自动计算，所以建议用 `wrap_content` 配合 `match_parent` 宽度。
 *
 * ## 代码用法
 * ```kotlin
 * view.setForegroundMask(getDrawable(R.drawable.out_mask))
 * view.setBackgroundMask(getDrawable(R.drawable.bg_mask2))
 * view.setBackgroundColor(Color.parseColor("#FF4081"))   // 或不调 = 用视频做背景
 * view.setVideoSource(uri)
 * view.play()
 *
 * view.export("/sdcard/Movies/out.mp4", onProgress = { ... }, onComplete = { ok, err -> ... })
 * ```
 *
 * ## 依赖
 * `app/src/main/assets/selfie_segmenter.tflite` 需自行放置（MediaPipe 模型，缺失时分割回退到"全部当人物"）。
 */
class GlMaskVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    // ---------- 配置 ----------
    var minScale: Float = 1f
    var maxScale: Float = 6f
    var doubleTapStep: Float = 1.6f
    var animMs: Long = 220L

    /** 用户手势导致的变换变化（[setTransform] 不会触发）。 */
    var onTransformChanged: ((scale: Float, tx: Float, ty: Float) -> Unit)? = null

    // ---------- 内部 ----------
    private val surfaceView = SurfaceView(context).also { sv ->
        sv.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(sv)
    }

    private val renderThread = RenderThread(context)

    private var videoW: Int = 0
    private var videoH: Int = 0
    private var pendingUri: Uri? = null

    // 用户变换状态
    private var userScale: Float = 1f
    private var userTx: Float = 0f
    private var userTy: Float = 0f
    private var anim: ValueAnimator? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val target = (userScale * d.scaleFactor).coerceIn(minScale, maxScale)
                val factor = if (userScale == 0f) 1f else target / userScale
                applyPivotScale(factor, d.focusX, d.focusY)
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val next = if (userScale * doubleTapStep > maxScale + 1e-3f) {
                    minScale
                } else {
                    (userScale * doubleTapStep).coerceAtMost(maxScale)
                }
                animateScaleAroundPivot(next, e.x, e.y)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (scaleDetector.isInProgress) return false
                userTx -= distanceX
                userTy -= distanceY
                applyTransform(fire = true)
                return true
            }
        }
    )

    init {
        renderThread.onVideoSize = { w, h ->
            post {
                videoW = w
                videoH = h
                requestLayout()
            }
        }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                renderThread.setPreviewSurface(holder.surface, surfaceView.width, surfaceView.height)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
                renderThread.onPreviewSizeChanged(w, h)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                renderThread.setPreviewSurface(null, 0, 0)
            }
        })
    }

    // ============= 视频比例 =============

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (videoW > 0 && videoH > 0) {
            (w.toLong() * videoH / videoW).toInt()
        } else {
            MeasureSpec.getSize(heightMeasureSpec)
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY),
        )
    }

    // ============= 视频源 / 蒙版 / 背景 =============

    /** 设置视频源（前景背景共用）。 */
    fun setVideoSource(uri: Uri) {
        pendingUri = uri
        renderThread.setVideoUri(uri)
    }

    /** 前景蒙版（默认对应 out_mask.png）。 */
    fun setForegroundMask(drawable: Drawable?) {
        renderThread.setForegroundMask(drawable?.toBitmap(config = Bitmap.Config.ARGB_8888))
    }

    fun setForegroundMask(bitmap: Bitmap?) {
        renderThread.setForegroundMask(bitmap)
    }

    /** 背景蒙版（默认对应 bg_mask2.png）。 */
    fun setBackgroundMask(drawable: Drawable?) {
        renderThread.setBackgroundMask(drawable?.toBitmap(config = Bitmap.Config.ARGB_8888))
    }

    fun setBackgroundMask(bitmap: Bitmap?) {
        renderThread.setBackgroundMask(bitmap)
    }

    /** 纯色背景；`null` 表示用视频作为背景。 */
    fun setSolidBackgroundColor(color: Int?) {
        renderThread.setBackgroundColor(color)
    }

    fun play() = renderThread.play()
    fun pause() = renderThread.pause()

    // ============= 变换 API =============

    fun setTransform(scale: Float, tx: Float, ty: Float, animate: Boolean = false) {
        val s = scale.coerceIn(minScale, maxScale)
        if (animate) {
            animateTo(s, tx, ty)
        } else {
            userScale = s
            userTx = tx
            userTy = ty
            applyTransform(fire = false)
        }
    }

    fun getTransform(): Triple<Float, Float, Float> = Triple(userScale, userTx, userTy)

    fun resetTransform(animate: Boolean = true) = setTransform(1f, 0f, 0f, animate)

    // ============= 导出 =============

    /**
     * 把当前所见（含 zoom/drag）烧录为 MP4。
     *
     * @param outputPath 输出文件路径
     * @param width 输出像素宽，默认为控件实际宽度
     * @param height 输出像素高，默认为控件实际高度
     * @param bitRate 视频比特率，默认 6Mbps
     */
    fun export(
        outputPath: String,
        width: Int = this.width,
        height: Int = this.height,
        bitRate: Int = 6_000_000,
        onProgress: ((Float) -> Unit)? = null,
        onComplete: ((Boolean, String?) -> Unit)? = null,
    ) {
        val uri = pendingUri ?: run {
            onComplete?.invoke(false, "未设置 video source")
            return
        }
        val w = if (width > 0) width else (videoW.takeIf { it > 0 } ?: 1080)
        val h = if (height > 0) height else (videoH.takeIf { it > 0 } ?: 1920)
        renderThread.export(
            outputPath = outputPath,
            outputWidth = w,
            outputHeight = h,
            bitRate = bitRate,
            sourceUri = uri,
            onProgress = { p -> post { onProgress?.invoke(p) } },
            onComplete = { ok, err -> post { onComplete?.invoke(ok, err) } },
        )
    }

    // ============= 触摸 =============

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (videoW <= 0 || videoH <= 0) return super.onTouchEvent(event)
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        return true
    }

    // ============= 内部：transform 计算 =============

    private fun applyPivotScale(factor: Float, focusX: Float, focusY: Float) {
        userTx = focusX - factor * (focusX - userTx)
        userTy = focusY - factor * (focusY - userTy)
        userScale *= factor
        applyTransform(fire = true)
    }

    private fun animateScaleAroundPivot(targetScale: Float, focusX: Float, focusY: Float) {
        anim?.cancel()
        val startScale = userScale
        val startTx = userTx
        val startTy = userTy
        val totalFactor = targetScale / startScale
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = animatedValue as Float
                val f = 1f + (totalFactor - 1f) * t
                userScale = startScale * f
                userTx = focusX - f * (focusX - startTx)
                userTy = focusY - f * (focusY - startTy)
                applyTransform(fire = true)
            }
            start()
        }
    }

    private fun animateTo(targetScale: Float, targetTx: Float, targetTy: Float) {
        anim?.cancel()
        val s0 = userScale
        val tx0 = userTx
        val ty0 = userTy
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = animatedValue as Float
                userScale = s0 + (targetScale - s0) * t
                userTx = tx0 + (targetTx - tx0) * t
                userTy = ty0 + (targetTy - ty0) * t
                applyTransform(fire = false)
            }
            start()
        }
    }

    /**
     * 边界 clamp：onMeasure 保证 view 比例 = 视频比例，所以"装入"的基矩形 = (0,0,viewW,viewH)。
     * 因此：
     * - scale >= 1：内容覆盖视口，tx ∈ [viewW(1-scale), 0]，ty 同理
     * - scale  < 1：居中，tx = viewW(1-scale)/2
     */
    private fun clamp() {
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        userScale = userScale.coerceIn(minScale, maxScale)
        val scaledW = vw * userScale
        val scaledH = vh * userScale
        userTx = if (scaledW <= vw) (vw - scaledW) / 2f
        else userTx.coerceIn(vw - scaledW, 0f)
        userTy = if (scaledH <= vh) (vh - scaledH) / 2f
        else userTy.coerceIn(vh - scaledH, 0f)
    }

    private fun applyTransform(fire: Boolean) {
        clamp()
        renderThread.setUserTransform(userScale, userTx, userTy)
        if (fire) onTransformChanged?.invoke(userScale, userTx, userTy)
    }

    // ============= 生命周期 =============

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        anim?.cancel()
    }

    /** Activity onDestroy 时调用，释放 GL / 解码 / 编码 资源。 */
    fun release() {
        renderThread.release()
    }
}
