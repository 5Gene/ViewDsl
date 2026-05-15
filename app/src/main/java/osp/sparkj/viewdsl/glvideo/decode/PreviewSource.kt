package osp.sparkj.viewdsl.glvideo.decode

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface

/**
 * 预览模式视频源：[MediaPlayer] 输出到一个 [Surface]（来自 OesTexture）。
 *
 * 优点：自动按帧率播放、循环、随时 seek 暂停；缺点：没法精确逐帧驱动（导出走 [ExportDecoder]）。
 */
class PreviewSource(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var pendingUri: Uri? = null
    private var pendingSurface: Surface? = null

    /** 视频实际尺寸就绪时回调（主线程）。 */
    var onVideoSize: ((width: Int, height: Int) -> Unit)? = null

    /** 准备完成后立即触发，调用方可决定是否 [play]。 */
    var onPrepared: (() -> Unit)? = null

    fun setUri(uri: Uri) {
        pendingUri = uri
        bindIfReady()
    }

    fun setSurface(surface: Surface?) {
        pendingSurface = surface
        // MediaPlayer 已存在 → 切 Surface
        mediaPlayer?.setSurface(surface)
        bindIfReady()
    }

    private fun bindIfReady() {
        val uri = pendingUri ?: return
        val surface = pendingSurface ?: return
        if (mediaPlayer != null) return  // 已经在跑了
        val mp = MediaPlayer()
        mediaPlayer = mp
        try {
            mp.setDataSource(context, uri)
            mp.setSurface(surface)
            mp.isLooping = true
            mp.setOnVideoSizeChangedListener { _, w, h ->
                if (w > 0 && h > 0) onVideoSize?.invoke(w, h)
            }
            mp.setOnPreparedListener {
                onPrepared?.invoke()
                it.start()
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            mp.release()
            mediaPlayer = null
            throw e
        }
    }

    fun play() {
        mediaPlayer?.start()
    }

    fun pause() {
        mediaPlayer?.pause()
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun release() {
        try {
            mediaPlayer?.release()
        } catch (_: Throwable) {
        }
        mediaPlayer = null
        pendingSurface = null
        pendingUri = null
    }
}
