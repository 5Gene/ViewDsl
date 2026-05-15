package osp.sparkj.viewdsl.glvideo.decode

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.view.Surface

/**
 * 导出模式视频解码器：[MediaExtractor] + [MediaCodec]，输出到 OES Surface，逐帧驱动。
 *
 * 调用顺序：
 * ```
 * init(uri, outputSurface)
 * while (!isEos) decodeOneFrame()
 * release()
 * ```
 *
 * [decodeOneFrame] 会一直工作到拿到一个输出帧或确认 EOS 为止；输出帧的时间戳从
 * `bufferInfo.presentationTimeUs` 取，单位微秒。
 */
class ExportDecoder(private val context: Context) {

    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var inputDone = false
    private var outputDone = false

    var videoWidth: Int = 0
        private set
    var videoHeight: Int = 0
        private set
    var durationUs: Long = 0L
        private set
    var frameRate: Int = 30
        private set

    val isEos: Boolean get() = outputDone

    fun init(uri: Uri, outputSurface: Surface) {
        val ex = MediaExtractor().also { extractor = it }
        context.contentResolver.openFileDescriptor(uri, "r").use { pfd ->
            if (pfd != null) {
                ex.setDataSource(pfd.fileDescriptor)
            } else {
                ex.setDataSource(context, uri, null)
            }
        }
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) {
                trackIndex = i
                format = f
                break
            }
        }
        require(trackIndex >= 0 && format != null) { "未找到视频轨道: $uri" }
        ex.selectTrack(trackIndex)

        videoWidth = format.getInteger(MediaFormat.KEY_WIDTH)
        videoHeight = format.getInteger(MediaFormat.KEY_HEIGHT)
        if (format.containsKey(MediaFormat.KEY_DURATION)) {
            durationUs = format.getLong(MediaFormat.KEY_DURATION)
        }
        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE)
        }

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val c = MediaCodec.createDecoderByType(mime).also { codec = it }
        c.configure(format, outputSurface, null, 0)
        c.start()

        inputDone = false
        outputDone = false
    }

    /**
     * 解一帧：把帧"渲染"到 outputSurface（OES 收到 onFrameAvailable）。
     *
     * @return 该帧的 presentationTimeUs；如果到达 EOS 返回 -1L；
     *         如果暂时没有输出可用（但未 EOS），返回 -2L（调用方可短暂 sleep 后再试）。
     */
    fun decodeOneFrame(): Long {
        val c = codec ?: return -1L
        if (outputDone) return -1L

        // 1. 喂输入
        if (!inputDone) {
            val inputIdx = c.dequeueInputBuffer(10_000L)
            if (inputIdx >= 0) {
                val buf = c.getInputBuffer(inputIdx)!!
                val ex = extractor!!
                val size = ex.readSampleData(buf, 0)
                if (size < 0) {
                    c.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    val pts = ex.sampleTime
                    c.queueInputBuffer(inputIdx, 0, size, pts, 0)
                    ex.advance()
                }
            }
        }

        // 2. 取输出
        val info = MediaCodec.BufferInfo()
        val outIdx = c.dequeueOutputBuffer(info, 10_000L)
        return when {
            outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> -2L
            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> -2L
            outIdx >= 0 -> {
                val render = info.size > 0
                c.releaseOutputBuffer(outIdx, render)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true
                    -1L
                } else if (render) {
                    info.presentationTimeUs
                } else {
                    -2L
                }
            }

            else -> -2L
        }
    }

    fun release() {
        try {
            codec?.stop()
        } catch (_: Throwable) {
        }
        try {
            codec?.release()
        } catch (_: Throwable) {
        }
        try {
            extractor?.release()
        } catch (_: Throwable) {
        }
        codec = null
        extractor = null
    }
}
