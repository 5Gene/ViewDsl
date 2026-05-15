package osp.sparkj.viewdsl.glvideo.seg

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * 人物分割抽象：把一帧 Bitmap 跑成单通道（0~255）的人物 mask。
 *
 * 工作模式：
 * - 预览：调用 [submit] 异步投递，下一帧再用 [latestMask] 拿结果（容忍 1~2 帧延迟）。
 * - 导出：调用 [process] 同步阻塞，确保该帧的 mask 与当前内容严格对齐。
 */
interface SegmentationProvider {

    /** 创建底层引擎。失败抛异常（例如 assets 模型缺失）。 */
    fun init()

    /** 异步投递一帧，结果稍后通过 [latestMask] 取。 */
    fun submit(bitmap: Bitmap, timestampMs: Long)

    /** 同步执行一帧分割并写入 [latestMask]，导出时使用。 */
    fun process(bitmap: Bitmap, timestampMs: Long)

    /** 最近一次完成的 mask 帧；尚未有结果返回 null。 */
    fun latestMask(): MaskFrame?

    fun release()
}

/**
 * 单通道蒙版的一帧快照。[data] 长度 = [width] * [height]，每字节 0(背景) ~ 255(人物)。
 *
 * 数据来自分割线程的本地 buffer，**仅在持有者范围内有效**，GL 线程上传一次后就可以丢弃。
 */
data class MaskFrame(
    val data: ByteBuffer,
    val width: Int,
    val height: Int,
)
