package osp.sparkj.viewdsl.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import osp.sparkj.viewdsl.bitmap.DofOverlayView

/**
 * 视频文件景深播放 View。
 *
 * ## 内部结构（从下到上）
 * 1. [TextureView]：MediaPlayer 视频输出，硬件 GPU 直通，保持视频宽高比
 * 2. [DofOverlayView]：雾化遮罩，支持静态 frameMask + ML Kit 动态人物蒙版
 *
 * ## 宽高比
 * 宽度以父布局为准，高度根据视频实际宽高比自动计算，不拉伸变形。
 *
 * ## 使用
 * ```kotlin
 * view.setFrameMask(getDrawable(R.drawable.bg_mask))  // 静态形状蒙版
 * view.setVideoUri(uri)   // 设置视频并自动播放
 * view.pause()
 * view.play()
 * view.release()          // Activity.onDestroy 时调用
 * ```
 */
class DofVideoFileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val SAMPLE_INTERVAL_MS = 100L
    }

    private val textureView = TextureView(context).also {
        it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
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

    // ── 视频宽高比 ────────────────────────────────────────────────────

    private var videoWidth = 0
    private var videoHeight = 0

    /**
     * 宽度固定为父布局宽度，高度按视频宽高比计算。
     * 视频尺寸未知时保持 MATCH_PARENT 行为。
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (videoWidth > 0 && videoHeight > 0) {
            (w.toLong() * videoHeight / videoWidth).toInt()
        } else {
            MeasureSpec.getSize(heightMeasureSpec)
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        )
    }

    // ── 蒙版 ──────────────────────────────────────────────────────────

    /** 设置静态形状蒙版（对应 MaskActivity 的 bg_mask）。 */
    fun setFrameMask(drawable: Drawable?) = overlayView.setFrameMask(drawable)
    fun setFrameMask(bitmap: Bitmap?) = overlayView.setFrameMask(bitmap)

    // ── 播放控制 ──────────────────────────────────────────────────────

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var segmentor: VideoSegmentor? = null
    private var samplingActive = false
    private var surfaceReady = false
    private var playerPrepared = false

    private val sampleRunnable = object : Runnable {
        override fun run() {
            if (!samplingActive) return
            sampleFrame()
            mainHandler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    init {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
                surfaceReady = true
                // MediaPlayer 已 prepare 完成但还没 surface → 现在绑定并播放
                mediaPlayer?.let { mp ->
                    mp.setSurface(Surface(surface))
                    if (playerPrepared && !mp.isPlaying) {
                        mp.start()
                        startSampling()
                    }
                }
            }

            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean {
                surfaceReady = false
                return true
            }

            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
        }
    }

    /**
     * 设置视频 URI 并自动播放。
     * 内部处理 Surface/MediaPlayer 初始化竞争条件：
     * 无论 Surface 先就绪还是 MediaPlayer 先 prepare，都能正确开始播放。
     */
    fun setVideoUri(uri: Uri) {
        releaseInternal()
        playerPrepared = false

        val player = MediaPlayer().also { mediaPlayer = it }
        player.setDataSource(context, uri)
        player.isLooping = true

        // 视频尺寸就绪 → 更新宽高比，触发重新 measure
        player.setOnVideoSizeChangedListener { _, w, h ->
            if (w > 0 && h > 0 && (videoWidth != w || videoHeight != h)) {
                videoWidth = w
                videoHeight = h
                post { requestLayout() }
            }
        }

        player.setOnPreparedListener { mp ->
            playerPrepared = true
            // Surface 已就绪 → 直接播放；否则等 onSurfaceTextureAvailable
            if (surfaceReady) {
                textureView.surfaceTexture?.let { mp.setSurface(Surface(it)) }
                mp.start()
                startSampling()
            }
        }

        player.prepareAsync()

        segmentor = VideoSegmentor { mask ->
            overlayView.updatePersonMask(mask)
        }
    }

    fun play() {
        mediaPlayer?.start()
        startSampling()
    }

    fun pause() {
        mediaPlayer?.pause()
        stopSampling()
    }

    fun release() = releaseInternal()

    private fun releaseInternal() {
        stopSampling()
        mediaPlayer?.release()
        mediaPlayer = null
        playerPrepared = false
        segmentor?.close()
        segmentor = null
    }

    private fun startSampling() {
        if (samplingActive) return
        samplingActive = true
        mainHandler.postDelayed(sampleRunnable, SAMPLE_INTERVAL_MS)
    }

    private fun stopSampling() {
        samplingActive = false
        mainHandler.removeCallbacks(sampleRunnable)
    }

    private fun sampleFrame() {
        val seg = segmentor ?: return
        val bmp = try {
            textureView.getBitmap()
        } catch (e: Exception) {
            null
        } ?: return
        seg.process(bmp)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mediaPlayer?.isPlaying == true) startSampling()
    }

    override fun onDetachedFromWindow() {
        stopSampling()
        super.onDetachedFromWindow()
    }
}
