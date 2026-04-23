package osp.sparkj.viewdsl.bitmap.sticker.seg

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import osp.sparkj.viewdsl.bitmap.sticker.core.RunCtx
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 基于 MLKit Selfie Segmentation 的人物抠图。
 *
 * - 走 SINGLE_IMAGE_MODE；
 * - 通过 [Tasks.await] 把 MLKit 的异步 Task 同步掉，保持流水线单线程顺序模型；
 * - 输出保持与 input 一致的尺寸：用 mask 中每个像素的 foreground confidence (0f..1f)
 *   乘以原图像素 alpha，得到带前景的 ARGB_8888 位图。
 *
 * 注意：调用方需要在 **后台线程**（`Dispatchers.Default` / 专用 worker）执行 `pipeline.execute(...)`，
 * 本类内部会阻塞当前线程直到 MLKit 推理完成。
 *
 * @param confidenceThreshold 低于该值视为背景，默认 0.1 以软化边缘。
 */
class MlKitSeg(
    private val confidenceThreshold: Float = 0.1f
) : SegStep {

    override fun process(ctx: RunCtx, input: Bitmap): Bitmap {
        val segmenter: Segmenter = Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build()
        )
        try {
            val image = InputImage.fromBitmap(input, 0)
            val mask = Tasks.await(segmenter.process(image))
            return applyMask(input, mask.buffer, mask.width, mask.height, confidenceThreshold)
        } finally {
            segmenter.close()
        }
    }

    private fun applyMask(
        src: Bitmap,
        buffer: ByteBuffer,
        maskW: Int,
        maskH: Int,
        threshold: Float
    ): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        buffer.order(ByteOrder.nativeOrder())
        buffer.rewind()
        val confidence = FloatArray(maskW * maskH)
        buffer.asFloatBuffer().get(confidence)

        val scaleX = maskW.toFloat() / w
        val scaleY = maskH.toFloat() / h

        var i = 0
        for (y in 0 until h) {
            val my = (y * scaleY).toInt().coerceIn(0, maskH - 1)
            for (x in 0 until w) {
                val mx = (x * scaleX).toInt().coerceIn(0, maskW - 1)
                val c = confidence[my * maskW + mx]
                if (c < threshold) {
                    pixels[i] = Color.TRANSPARENT
                } else {
                    val src0 = pixels[i]
                    val a = (Color.alpha(src0) * c).toInt().coerceIn(0, 255)
                    pixels[i] = Color.argb(a, Color.red(src0), Color.green(src0), Color.blue(src0))
                }
                i++
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
