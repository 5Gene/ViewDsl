package osp.sparkj.viewdsl.bitmap.sticker.stroke

import osp.sparkj.viewdsl.bitmap.sticker.core.StickerStep

/**
 * 描边策略（可选步骤）。不向 [StickerPipeline] 添加此类型 step 即视为不描边。
 *
 * 输入：带 alpha 前景的 Bitmap（通常是 [SegStep] 产物）。
 * 输出：同尺寸 Bitmap，前景轮廓周围被 [color] 宽度为 [width] 的描边包裹。
 *
 * 三种实现：
 * - [ShaderStroke]：AGSL（API 33+），旧版本回退到 AlphaStroke；
 * - [AlphaStroke]：环绕多方向 offset 叠画的廉价描边；
 * - [BlurStroke]：alpha 模糊后着色，得到柔和发光型描边。
 */
interface StrokeStep : StickerStep
