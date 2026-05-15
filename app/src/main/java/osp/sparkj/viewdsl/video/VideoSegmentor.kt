package osp.sparkj.viewdsl.video

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import osp.sparkj.viewdsl.camera.FrameSegmentor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 视频文件帧人物分割器，基于 ML Kit Selfie Segmentation STREAM_MODE。
 *
 * 与 [FrameSegmentor] 区别：输入为 [Bitmap]（来自 TextureView.getBitmap），
 * 适用于视频文件定时采帧场景。
 *
 * ## 跳帧保护
 * 若上一帧推理未完成，新帧直接丢弃（[AtomicBoolean]），避免积压导致 OOM 或延迟。
 *
 * @param confidenceThreshold 低于此置信度视为背景，默认 0.1（软化边缘）
 * @param onMaskReady mask 生成后回调，运行在内部单线程 Executor
 */
class VideoSegmentor(
    private val confidenceThreshold: Float = 0.1f,
    private val onMaskReady: (Bitmap) -> Unit
) {

    private val executor = Executors.newSingleThreadExecutor()
    private val processing = AtomicBoolean(false)

    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .enableRawSizeMask()
            .build()
    )

    /**
     * 提交一帧 Bitmap 进行人物分割。
     * 若上一帧未完成，本帧直接返回（跳帧）。
     * [frame] 不会被本类 recycle，调用方自行管理生命周期。
     */
    fun process(frame: Bitmap) {
        if (!processing.compareAndSet(false, true)) return
        val inputImage = InputImage.fromBitmap(frame, 0)
        segmenter.process(inputImage)
            .addOnSuccessListener(executor) { result ->
                try {
                    val mask = FrameSegmentor.buildMaskBitmap(
                        result.buffer,
                        result.width,
                        result.height,
                        confidenceThreshold
                    )
                    onMaskReady(mask)
                } finally {
                    processing.set(false)
                }
            }
            .addOnFailureListener {
                processing.set(false)
            }
    }

    fun close() {
        segmenter.close()
        executor.shutdown()
    }
}
