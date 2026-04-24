package osp.sparkj.viewdsl.bitmap.sticker.seg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import osp.sparkj.viewdsl.bitmap.sticker.core.RunCtx
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 基于 MediaPipe Image Segmenter 的人物抠图。
 * 官方文档：https://developers.google.com/mediapipe/solutions/vision/image_segmenter
 *
 * 使用 selfie_segmenter.tflite 模型，用户需要在
 * `app/src/main/assets/<modelAssetPath>` 放置该模型文件
 * （默认 `selfie_segmenter.tflite`，可从
 * https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite 下载）。
 *
 * @param context ApplicationContext，用于 MediaPipe 加载 assets 模型。
 * @param modelAssetPath assets 下的模型路径，默认 `selfie_segmenter.tflite`。
 * @param foregroundIndex 该模型类别掩码中"前景（人物）"的类别索引，selfie_segmenter 为 0。
 */
class MpSeg(
    private val context: Context,
    private val modelAssetPath: String = "selfie_segmenter.tflite",
    private val foregroundIndex: Int = 0
) : SegStep {

    override fun process(ctx: RunCtx, input: Bitmap): Bitmap {
        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(modelAssetPath)
                    .build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(true)
            .setOutputConfidenceMasks(false)
            .build()

        val segmenter = ImageSegmenter.createFromOptions(context, options)
        try {
            val mpImage = BitmapImageBuilder(input).build()
            val result = segmenter.segment(mpImage)
            val categoryMask = result.categoryMask().orElse(null)
                ?: return input.copy(Bitmap.Config.ARGB_8888, true)
            try {
                val buffer = ByteBufferExtractor.extract(categoryMask)
                    .order(ByteOrder.nativeOrder())
                val maskW = categoryMask.width
                val maskH = categoryMask.height
                return applyCategoryMask(input, buffer, maskW, maskH, foregroundIndex)
            } finally {
                categoryMask.close()
            }
        } finally {
            segmenter.close()
        }
    }

    private fun applyCategoryMask(
        src: Bitmap,
        buffer: ByteBuffer,
        maskW: Int,
        maskH: Int,
        foregroundIndex: Int
    ): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val categories = ByteArray(maskW * maskH)
        buffer.rewind()
        buffer.get(categories)

        val fg = foregroundIndex.toByte()
        val scaleX = maskW.toFloat() / w
        val scaleY = maskH.toFloat() / h

        var i = 0
        for (y in 0 until h) {
            val my = (y * scaleY).toInt().coerceIn(0, maskH - 1)
            for (x in 0 until w) {
                val mx = (x * scaleX).toInt().coerceIn(0, maskW - 1)
                if (categories[my * maskW + mx] != fg) {
                    pixels[i] = Color.TRANSPARENT
                }
                i++
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
