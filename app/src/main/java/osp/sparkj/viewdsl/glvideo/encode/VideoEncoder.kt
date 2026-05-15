package osp.sparkj.viewdsl.glvideo.encode

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface

/**
 * H.264 编码器：从一个 input [Surface] 拿 GL 渲染帧 → MP4 文件。
 *
 * 用法：
 * ```
 * val encoder = VideoEncoder(outputPath, w, h, 30, 4_000_000)
 * val surface = encoder.init()
 * eglCore.createWindowSurface(surface)         // GL 渲染到这里
 * // ... 渲染每帧后调用 drainEncoder() ...
 * encoder.drainEncoder(endOfStream = true)
 * encoder.release()
 * ```
 */
class VideoEncoder(
    private val outputPath: String,
    private val width: Int,
    private val height: Int,
    private val frameRate: Int = 30,
    private val bitRate: Int = 6_000_000,
) {

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var trackIndex: Int = -1
    private var muxerStarted: Boolean = false

    /** 初始化编码器与 muxer，返回 GL 应渲染的 input Surface。 */
    fun init(): Surface {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            .apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also { codec = it }
        c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = c.createInputSurface()
        c.start()

        muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        return inputSurface!!
    }

    /**
     * 把编码器输出拉空，写入 muxer。每次 EGL swapBuffers 后调一次。
     * 最后一帧调用 `drainEncoder(true)` 标记 EOS。
     */
    fun drainEncoder(endOfStream: Boolean = false) {
        val c = codec ?: return
        if (endOfStream) c.signalEndOfInputStream()

        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = c.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    // EOS 路径：继续等
                }

                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    require(!muxerStarted) { "MUXER 不能二次启动" }
                    val newFormat = c.outputFormat
                    trackIndex = muxer!!.addTrack(newFormat)
                    muxer!!.start()
                    muxerStarted = true
                }

                outIdx >= 0 -> {
                    val data = c.getOutputBuffer(outIdx)!!
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        info.size = 0
                    }
                    if (info.size > 0 && muxerStarted) {
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        muxer!!.writeSampleData(trackIndex, data, info)
                    }
                    c.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
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
        if (muxerStarted) {
            try {
                muxer?.stop()
            } catch (_: Throwable) {
            }
        }
        try {
            muxer?.release()
        } catch (_: Throwable) {
        }
        try {
            inputSurface?.release()
        } catch (_: Throwable) {
        }
        codec = null
        muxer = null
        inputSurface = null
        muxerStarted = false
        trackIndex = -1
    }
}
