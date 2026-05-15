package osp.sparkj.viewdsl.camera

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 相机帧人物分割器，基于 ML Kit Selfie Segmentation STREAM_MODE。
 *
 * ## STREAM_MODE vs SINGLE_IMAGE_MODE
 * STREAM_MODE 跨帧保持模型内部状态，连续帧推理速度比 SINGLE_IMAGE_MODE 快 3-5x，
 * 适合相机实时场景。
 *
 * ## 跳帧保护
 * [AtomicBoolean] 确保同一时间只有一帧在处理：上一帧未完成时新帧直接 close，
 * 配合 CameraX [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST] 始终处理最新帧。
 *
 * ## 输出
 * mask Bitmap：与输入帧等尺寸，人物区域接近白色（alpha 高），背景接近黑色（alpha 低）。
 * 通过 [onMaskReady] 回调在 [executor] 线程返回，调用方负责 post 到主线程更新 UI。
 *
 * @param onMaskReady 每帧 mask 生成后的回调，运行在内部单线程 Executor
 */
class FrameSegmentor(
    private val confidenceThreshold: Float = 0.1f,
    val onMaskReady: (Bitmap) -> Unit
) {

    val executor = Executors.newSingleThreadExecutor()

    private val processing = AtomicBoolean(false)

    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .enableRawSizeMask()
            .build()
    )

    /**
     * 处理一帧相机图像。若上一帧尚未处理完，本帧直接丢弃（[imageProxy] 立即 close）。
     * 调用方（CameraX ImageAnalysis）应在 [executor] 上设置 analyzer。
     */
    fun process(imageProxy: ImageProxy) {
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            processing.set(false)
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        segmenter.process(inputImage)
            .addOnSuccessListener(executor) { result ->
                try {
                    val mask = buildMaskBitmap(
                        result.buffer,
                        result.width,
                        result.height,
                        confidenceThreshold
                    )
                    onMaskReady(mask)
                } finally {
                    processing.set(false)
                    imageProxy.close()
                }
            }
            .addOnFailureListener {
                processing.set(false)
                imageProxy.close()
            }
    }

    fun close() {
        segmenter.close()
        executor.shutdown()
    }

    companion object {
        /**
         * 把 ML Kit 输出的 FloatBuffer mask 转成一张灰度 Bitmap：
         * 人物区域白色（alpha 高），背景黑色（alpha 低），供 [DofOverlayView] 的 DST_OUT 使用。
         */
        fun buildMaskBitmap(
            buffer: java.nio.ByteBuffer,
            maskW: Int,
            maskH: Int,
            threshold: Float
        ): Bitmap {
            buffer.order(ByteOrder.nativeOrder()).rewind()
            val floatBuf = buffer.asFloatBuffer()
            val pixels = IntArray(maskW * maskH)
            for (i in pixels.indices) {
                val confidence = floatBuf.get(i).coerceIn(0f, 1f)
                val alpha = if (confidence < threshold) 0
                else (confidence * 255).toInt().coerceIn(0, 255)
                // 白色 + 不同 alpha：DST_OUT 按 alpha 值橡皮擦雾层
                pixels[i] = Color.argb(alpha, 255, 255, 255)
            }
            val bmp = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, maskW, 0, 0, maskW, maskH)
            return bmp
        }
    }
}
