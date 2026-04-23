package osp.sparkj.viewdsl.bitmap.sticker.seg

import osp.sparkj.viewdsl.bitmap.sticker.core.StickerStep

/**
 * 人物分割（抠图）策略。
 *
 * 输入：任意 Bitmap（一般是 origin，也可以是上一个 step 的输出）。
 * 输出：一张尺寸与输入一致的 ARGB_8888 Bitmap，前景像素保留、背景像素 alpha 为 0。
 *
 * 必须实现 Google 的两个方案：
 * - [MlKitSeg]：com.google.mlkit:segmentation-selfie
 * - [MpSeg]：com.google.mediapipe:tasks-vision（selfie_segmenter.tflite）
 */
interface SegStep : StickerStep
