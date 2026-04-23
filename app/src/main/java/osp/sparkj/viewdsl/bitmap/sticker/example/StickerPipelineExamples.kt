package osp.sparkj.viewdsl.bitmap.sticker.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import osp.sparkj.viewdsl.bitmap.sticker.ComposeStep
import osp.sparkj.viewdsl.bitmap.sticker.StickerPipeline
import osp.sparkj.viewdsl.bitmap.sticker.crop.CropStep
import osp.sparkj.viewdsl.bitmap.sticker.crop.KeepInside
import osp.sparkj.viewdsl.bitmap.sticker.crop.KeepOutside
import osp.sparkj.viewdsl.bitmap.sticker.seg.MlKitSeg
import osp.sparkj.viewdsl.bitmap.sticker.seg.MpSeg
import osp.sparkj.viewdsl.bitmap.sticker.stroke.AlphaStroke
import osp.sparkj.viewdsl.bitmap.sticker.stroke.BlurStroke
import osp.sparkj.viewdsl.bitmap.sticker.stroke.ShaderStroke

/**
 * 单列表流水线调用样例。
 *
 * [ComposeStep] 直接接收已处理好的 Bitmap，调用方在执行前自行准备各图层。
 */
object StickerPipelineExamples {

    /**
     * 场景 A：标准出框
     * bg 和 person 各自裁切后合成。
     */
    fun sceneA(origin: Bitmap, bgMask: Bitmap, personMask: Bitmap): Bitmap {
        val bg = KeepInside.apply(origin, bgMask)
        return try {
            StickerPipeline()
                .addStep(MlKitSeg())
                .addStep(ShaderStroke(width = 15, color = Color.WHITE))
                .addStep(CropStep(KeepInside, personMask))
                .addStep(ComposeStep(bg))           // bg 作为底图
                .execute(origin)
        } finally {
            bg.recycle()
        }
    }

    /**
     * 场景 B：MediaPipe + 无描边
     */
    fun sceneB(context: Context, origin: Bitmap, bgMask: Bitmap, personMask: Bitmap): Bitmap {
        val bg = KeepInside.apply(origin, bgMask)
        return try {
            StickerPipeline()
                .addStep(MpSeg(context))
                .addStep(CropStep(KeepInside, personMask))
                .addStep(ComposeStep(bg))
                .execute(origin)
        } finally {
            bg.recycle()
        }
    }

    /**
     * 场景 C：多图层合成（底图 + 装饰图 + 人物）
     * [ComposeStep] 支持任意多张 Bitmap，按传入顺序从下往上叠。
     */
    fun sceneC(origin: Bitmap, bgMask: Bitmap, personMask: Bitmap, decor: Bitmap): Bitmap {
        val bg = KeepInside.apply(origin, bgMask)
        return try {
            StickerPipeline()
                .addStep(MlKitSeg())
                .addStep(CropStep(KeepInside, personMask))
                .addStep(BlurStroke(width = 12, color = Color.WHITE))
                .addStep(ComposeStep(bg, decor))    // bg → decor → person（顶层）
                .execute(origin)
        } finally {
            bg.recycle()
        }
    }

    /**
     * 场景 D：反向裁切
     */
    fun sceneD(origin: Bitmap, bgMask: Bitmap, personMask: Bitmap): Bitmap {
        val bg = KeepOutside.apply(origin, bgMask)
        return try {
            StickerPipeline()
                .addStep(MlKitSeg())
                .addStep(AlphaStroke(width = 10, color = Color.RED))
                .addStep(CropStep(KeepOutside, personMask))
                .addStep(ComposeStep(bg))
                .execute(origin)
        } finally {
            bg.recycle()
        }
    }

    /**
     * 场景 E：不合成，只要抠图结果
     */
    fun sceneE(origin: Bitmap): Bitmap =
        StickerPipeline()
            .addStep(MlKitSeg())
            .execute(origin)
}
