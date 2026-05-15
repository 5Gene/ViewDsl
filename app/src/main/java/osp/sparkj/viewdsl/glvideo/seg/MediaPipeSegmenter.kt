package osp.sparkj.viewdsl.glvideo.seg

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MediaPipe Selfie Segmenter（VIDEO 模式）。
 *
 * - 模型文件 `selfie_segmenter.tflite` 需放在 `app/src/main/assets/`，
 *   下载地址：https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite
 * - 输出 category mask（byte，类别索引），foregroundIndex（默认 0）= 255，其它 = 0
 * - 内部 HandlerThread 跑推理；预览模式 [submit] 异步丢帧，导出模式 [process] 同步阻塞
 */
class MediaPipeSegmenter(
    private val context: Context,
    private val modelAssetPath: String = "selfie_segmenter.tflite",
    private val foregroundIndex: Int = 0,
) : SegmentationProvider {

    private var segmenter: ImageSegmenter? = null
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null

    /** 投递并发保护：未完成时新提交的帧直接丢弃，避免堆积。 */
    private val busy = AtomicBoolean(false)

    private val lock = Any()
    private var lastMaskBuffer: ByteBuffer? = null
    private var lastMaskW: Int = 0
    private var lastMaskH: Int = 0

    override fun init() {
        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(
                BaseOptions.builder().setModelAssetPath(modelAssetPath).build()
            )
            .setRunningMode(RunningMode.VIDEO)
            .setOutputCategoryMask(true)
            .setOutputConfidenceMasks(false)
            .build()
        segmenter = ImageSegmenter.createFromOptions(context, options)

        workerThread = HandlerThread("GlMaskSegmenter").apply { start() }
        worker = Handler(workerThread!!.looper)
    }

    override fun submit(bitmap: Bitmap, timestampMs: Long) {
        if (!busy.compareAndSet(false, true)) return
        worker?.post {
            try {
                runSegmentation(bitmap, timestampMs)
            } finally {
                busy.set(false)
            }
        }
    }

    override fun process(bitmap: Bitmap, timestampMs: Long) {
        runSegmentation(bitmap, timestampMs)
    }

    private fun runSegmentation(bitmap: Bitmap, timestampMs: Long) {
        val seg = segmenter ?: return
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = seg.segmentForVideo(mpImage, timestampMs)
        val categoryMask = result.categoryMask().orElse(null) ?: return
        try {
            val src = ByteBufferExtractor.extract(categoryMask).order(ByteOrder.nativeOrder())
            val w = categoryMask.width
            val h = categoryMask.height
            // 转换为 0/255 单通道 byte buffer，正好可作为 R8 纹理上传
            val out = ByteBuffer.allocateDirect(w * h).order(ByteOrder.nativeOrder())
            val total = w * h
            val fg = foregroundIndex.toByte()
            src.rewind()
            for (i in 0 until total) {
                out.put(if (src.get(i) == fg) 0xFF.toByte() else 0x00.toByte())
            }
            out.rewind()
            synchronized(lock) {
                lastMaskBuffer = out
                lastMaskW = w
                lastMaskH = h
            }
        } finally {
            categoryMask.close()
        }
    }

    override fun latestMask(): MaskFrame? = synchronized(lock) {
        val buf = lastMaskBuffer ?: return null
        MaskFrame(buf, lastMaskW, lastMaskH)
    }

    override fun release() {
        try {
            worker?.removeCallbacksAndMessages(null)
            workerThread?.quitSafely()
        } catch (_: Throwable) {
        }
        try {
            segmenter?.close()
        } catch (_: Throwable) {
        }
        segmenter = null
        worker = null
        workerThread = null
        synchronized(lock) {
            lastMaskBuffer = null
            lastMaskW = 0
            lastMaskH = 0
        }
    }
}
