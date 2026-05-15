package osp.sparkj.viewdsl.glvideo.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import androidx.core.graphics.scale
import osp.sparkj.viewdsl.glvideo.decode.ExportDecoder
import osp.sparkj.viewdsl.glvideo.decode.PreviewSource
import osp.sparkj.viewdsl.glvideo.encode.VideoEncoder
import osp.sparkj.viewdsl.glvideo.seg.MaskFrame
import osp.sparkj.viewdsl.glvideo.seg.MediaPipeSegmenter
import osp.sparkj.viewdsl.glvideo.seg.SegmentationProvider
import osp.sparkj.viewdsl.glvideo.shader.BlendShader
import osp.sparkj.viewdsl.glvideo.shader.MaskShader
import osp.sparkj.viewdsl.glvideo.shader.OesToFboShader
import osp.sparkj.viewdsl.glvideo.shader.PassthroughShader
import osp.sparkj.viewdsl.glvideo.shader.SolidColorShader
import osp.sparkj.viewdsl.glvideo.shader.TransformShader
import java.nio.IntBuffer
import java.util.concurrent.CountDownLatch

/**
 * GL 渲染线程：拥有 EGLContext + GL 资源（OES / FBO / Shader）+ 整套 pipeline。
 *
 * 所有公开方法都是线程安全的（内部 post 到自身 Handler），调用方无需关心线程。
 *
 * ## pipeline（与 plan 文档一致）
 * ```
 * OES -> oesToFbo -> rawFbo
 * rawFbo -> transform(user) -> txFbo
 * personMaskTex -> transform(user) -> personTxFbo
 * txFbo × personTxFbo (mask R) -> fgWithPersonFbo
 * fgWithPersonFbo × outMaskTex (mask A) -> fgFinalFbo
 * txFbo × bgMaskTex (mask A) -> bgFinalFbo            (solidColor != null 时换 SolidColorShader)
 * blend(fgFinal, bgFinal) -> finalFbo
 * passthrough(finalFbo) -> previewSurface 或 encoderSurface
 * ```
 */
class RenderThread(private val context: Context) {

    // ---------- 公开状态 / 回调 ----------

    /** 视频实际分辨率就绪（主线程回调）。 */
    var onVideoSize: ((width: Int, height: Int) -> Unit)? = null

    // ---------- 内部 ----------

    private val handlerThread = HandlerThread("GlMaskRenderThread").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private val eglCore = EglCore()
    private var previewSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var encoderSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var dummyPbuffer: EGLSurface = EGL14.EGL_NO_SURFACE

    // OES 输入
    private val oesTexture = OesTexture()
    private val previewSource = PreviewSource(context)

    // 蒙版 PNG 纹理
    private var fgMaskTex = GlUtils.NO_TEXTURE   // out_mask.png 等
    private var bgMaskTex = GlUtils.NO_TEXTURE   // bg_mask2.png 等

    // 人物 mask（MediaPipe R8）
    private var personMaskTex = GlUtils.NO_TEXTURE
    private var personMaskW = 0
    private var personMaskH = 0

    // FBO 流水线工位
    private var rawFbo: FboTexture? = null
    private var txFbo: FboTexture? = null
    private var personTxFbo: FboTexture? = null
    private var fgWithPersonFbo: FboTexture? = null
    private var fgFinalFbo: FboTexture? = null
    private var bgFinalFbo: FboTexture? = null
    private var finalFbo: FboTexture? = null
    private var fboWidth = 0
    private var fboHeight = 0

    // Shaders
    private val oesToFbo = OesToFboShader()
    private val transform = TransformShader()
    private val mask = MaskShader()
    private val solidBg = SolidColorShader()
    private val blend = BlendShader()
    private val passthrough = PassthroughShader()
    private var shadersReady = false

    // 用户变换 / 视图状态
    @Volatile
    private var userScale: Float = 1f
    @Volatile
    private var userTx: Float = 0f
    @Volatile
    private var userTy: Float = 0f
    @Volatile
    private var viewW: Int = 0
    @Volatile
    private var viewH: Int = 0
    @Volatile
    private var videoW: Int = 0
    @Volatile
    private var videoH: Int = 0
    @Volatile
    private var solidBgColor: Int? = null

    // 是否已经收到过一帧视频（用于"手势改了立刻重绘"）
    @Volatile
    private var hasFrame: Boolean = false

    // 人物分割
    private val segmenter: SegmentationProvider = MediaPipeSegmenter(context)
    private var segmenterReady = false
    private val oesMatrix = FloatArray(16)
    private var lastFrameTimestampNs: Long = 0L

    init {
        handler.post { setupGl() }
        oesTexture.onFrameAvailable = { handler.post { onFrameAvailable() } }
        previewSource.onVideoSize = { w, h ->
            handler.post {
                videoW = w
                videoH = h
                oesTexture.setDefaultBufferSize(w, h)
                ensureFbos()
            }
            onVideoSize?.invoke(w, h)
        }
    }

    // ============= 公开 API =============

    fun setPreviewSurface(surface: Surface?, w: Int, h: Int) = handler.post {
        viewW = w
        viewH = h
        if (previewSurface != EGL14.EGL_NO_SURFACE) {
            eglCore.makeNothingCurrent()
            eglCore.destroySurface(previewSurface)
            previewSurface = EGL14.EGL_NO_SURFACE
        }
        if (surface != null) {
            previewSurface = eglCore.createWindowSurface(surface)
            eglCore.makeCurrent(previewSurface)
            ensureFbos()
            renderIfReady()
        }
    }

    fun onPreviewSizeChanged(w: Int, h: Int) = handler.post {
        viewW = w
        viewH = h
        renderIfReady()
    }

    fun setVideoUri(uri: Uri) = handler.post {
        previewSource.setSurface(oesTexture.surface)
        previewSource.setUri(uri)
    }

    fun play() = handler.post { previewSource.play() }
    fun pause() = handler.post { previewSource.pause() }

    fun setBackgroundColor(color: Int?) = handler.post {
        solidBgColor = color
        renderIfReady()
    }

    fun setForegroundMask(bitmap: Bitmap?) = handler.post {
        ensureCurrent()
        GlUtils.deleteTexture(fgMaskTex)
        fgMaskTex = if (bitmap != null) GlUtils.loadTextureFromBitmap(bitmap) else GlUtils.NO_TEXTURE
        renderIfReady()
    }

    fun setBackgroundMask(bitmap: Bitmap?) = handler.post {
        ensureCurrent()
        GlUtils.deleteTexture(bgMaskTex)
        bgMaskTex = if (bitmap != null) GlUtils.loadTextureFromBitmap(bitmap) else GlUtils.NO_TEXTURE
        renderIfReady()
    }

    fun setUserTransform(scale: Float, tx: Float, ty: Float) = handler.post {
        userScale = scale
        userTx = tx
        userTy = ty
        renderIfReady()
    }

    fun requestRender() = handler.post { renderIfReady() }

    fun release() {
        val latch = CountDownLatch(1)
        handler.post {
            try {
                releaseGl()
            } finally {
                latch.countDown()
            }
        }
        try {
            latch.await()
        } catch (_: InterruptedException) {
        }
        handlerThread.quitSafely()
    }

    /** 导出 MP4。在后台线程跑，完成 / 失败时通过回调通知主线程。 */
    fun export(
        outputPath: String,
        outputWidth: Int,
        outputHeight: Int,
        bitRate: Int,
        sourceUri: Uri,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean, String?) -> Unit,
    ) = handler.post {
        try {
            runExport(outputPath, outputWidth, outputHeight, bitRate, sourceUri, onProgress)
            onComplete(true, null)
        } catch (e: Throwable) {
            onComplete(false, e.message)
        }
    }

    // ============= 内部：GL 设置 / 释放 =============

    private fun setupGl() {
        eglCore.init()
        // 启动期没 preview surface 也得先 makeCurrent 一个上下文才能创建 OES / shader
        dummyPbuffer = eglCore.createOffscreenSurface(1, 1)
        eglCore.makeCurrent(dummyPbuffer)

        oesTexture.create()

        // shaders
        oesToFbo.create()
        transform.create()
        mask.create()
        solidBg.create()
        blend.create()
        passthrough.create()
        shadersReady = true

        // segmenter（在 GL 线程初始化即可，内部自己再起 worker）
        try {
            segmenter.init()
            segmenterReady = true
        } catch (e: Throwable) {
            // 没放模型文件不应阻塞 GL；segmenter 不可用时按"全是人物"渲染（personMaskTex == 0 时 shader 后续会处理）
            segmenterReady = false
        }
    }

    private fun ensureFbos() {
        ensureCurrent()
        // 选用视频分辨率作为 FBO 尺寸（够清晰，且 mask 在这个分辨率下对齐）；
        // 视频未知时退化为 view 尺寸
        val w = if (videoW > 0) videoW else viewW
        val h = if (videoH > 0) videoH else viewH
        if (w <= 0 || h <= 0) return
        if (w == fboWidth && h == fboHeight && rawFbo != null) return

        releaseFbos()
        rawFbo = FboTexture(w, h).also { it.create() }
        txFbo = FboTexture(w, h).also { it.create() }
        personTxFbo = FboTexture(w, h).also { it.create() }
        fgWithPersonFbo = FboTexture(w, h).also { it.create() }
        fgFinalFbo = FboTexture(w, h).also { it.create() }
        bgFinalFbo = FboTexture(w, h).also { it.create() }
        finalFbo = FboTexture(w, h).also { it.create() }
        fboWidth = w
        fboHeight = h
    }

    private fun releaseFbos() {
        rawFbo?.release(); rawFbo = null
        txFbo?.release(); txFbo = null
        personTxFbo?.release(); personTxFbo = null
        fgWithPersonFbo?.release(); fgWithPersonFbo = null
        fgFinalFbo?.release(); fgFinalFbo = null
        bgFinalFbo?.release(); bgFinalFbo = null
        finalFbo?.release(); finalFbo = null
        fboWidth = 0
        fboHeight = 0
    }

    private fun releaseGl() {
        ensureCurrent()
        previewSource.release()
        segmenter.release()

        releaseFbos()
        GlUtils.deleteTexture(fgMaskTex); fgMaskTex = GlUtils.NO_TEXTURE
        GlUtils.deleteTexture(bgMaskTex); bgMaskTex = GlUtils.NO_TEXTURE
        GlUtils.deleteTexture(personMaskTex); personMaskTex = GlUtils.NO_TEXTURE
        oesTexture.release()

        oesToFbo.release()
        transform.release()
        mask.release()
        solidBg.release()
        blend.release()
        passthrough.release()
        shadersReady = false

        if (encoderSurface != EGL14.EGL_NO_SURFACE) eglCore.destroySurface(encoderSurface)
        if (previewSurface != EGL14.EGL_NO_SURFACE) eglCore.destroySurface(previewSurface)
        if (dummyPbuffer != EGL14.EGL_NO_SURFACE) eglCore.destroySurface(dummyPbuffer)
        eglCore.release()
    }

    private fun ensureCurrent() {
        val target = when {
            encoderSurface != EGL14.EGL_NO_SURFACE -> encoderSurface
            previewSurface != EGL14.EGL_NO_SURFACE -> previewSurface
            dummyPbuffer != EGL14.EGL_NO_SURFACE -> dummyPbuffer
            else -> return
        }
        eglCore.makeCurrent(target)
    }

    // ============= 帧驱动 =============

    private fun onFrameAvailable() {
        // 即便 previewSurface 没就绪，也要 update 一次免得 SurfaceTexture buffer 堆积
        ensureCurrent()
        oesTexture.updateTexImage()
        oesTexture.getTransformMatrix(oesMatrix)
        lastFrameTimestampNs = SystemClock.uptimeMillis() * 1_000_000L
        hasFrame = true

        // 投递分割（异步）
        submitSegmentation()

        // 上传最近一次完成的 person mask（异步路径）
        uploadLatestPersonMask()

        renderIfReady()
    }

    private fun renderIfReady() {
        if (!shadersReady) return
        if (!hasFrame) {
            // 没视频帧时清屏黑，避免预览闪
            if (previewSurface != EGL14.EGL_NO_SURFACE) {
                eglCore.makeCurrent(previewSurface)
                GLES30.glViewport(0, 0, viewW, viewH)
                GLES30.glClearColor(0f, 0f, 0f, 1f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                eglCore.swapBuffers(previewSurface)
            }
            return
        }
        ensureFbos()
        if (previewSurface != EGL14.EGL_NO_SURFACE) {
            eglCore.makeCurrent(previewSurface)
            runPipeline(targetW = viewW, targetH = viewH, flipY = false)
            eglCore.swapBuffers(previewSurface)
        }
    }

    /** 跑完整 pipeline，结果最后 passthrough 到当前绑定的窗口（viewport = targetW×H）。 */
    private fun runPipeline(targetW: Int, targetH: Int, flipY: Boolean) {
        val raw = rawFbo ?: return
        val tx = txFbo ?: return
        val personTx = personTxFbo ?: return
        val fgP = fgWithPersonFbo ?: return
        val fgFinal = fgFinalFbo ?: return
        val bgFinal = bgFinalFbo ?: return
        val final = finalFbo ?: return

        // 1. OES → raw
        oesToFbo.draw(oesTexture.textureId, oesMatrix, raw)

        // 2. raw → tx（用户手势）
        val txMat = buildUserMatrix()
        transform.draw(raw.textureId, txMat, tx)

        // 3. personMask → personTx（同步同样的变换）
        val maskChannelForPerson = if (personMaskTex != GlUtils.NO_TEXTURE) {
            transform.draw(personMaskTex, txMat, personTx)
            MaskShader.MASK_RED
        } else {
            // 没分割结果：默认整张图都是人物（personTx = 白色）
            // 用 transform 输入一张纯白纹理代价大；这里直接复用 tx，
            // 用 mask channel 取 alpha 时 tx.a 接近 1 ≈ 全保留，效果上等同"无分割"
            transform.draw(raw.textureId, txMat, personTx)
            MaskShader.MASK_ALPHA
        }

        // 4. fgP = tx × personTx
        mask.draw(tx.textureId, personTx.textureId, maskChannelForPerson, fgP)

        // 5. fgFinal = fgP × out_mask
        if (fgMaskTex != GlUtils.NO_TEXTURE) {
            mask.draw(fgP.textureId, fgMaskTex, MaskShader.MASK_ALPHA, fgFinal)
        } else {
            // 没前景蒙版 → 直接用 fgP
            mask.draw(fgP.textureId, fgP.textureId, MaskShader.MASK_ALPHA, fgFinal)
        }

        // 6. bgFinal = tx × bg_mask2  （视频背景）或 solidColor × bg_mask2（纯色背景）
        val color = solidBgColor
        if (color != null && bgMaskTex != GlUtils.NO_TEXTURE) {
            val rgba = floatArrayOf(
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f,
                Color.alpha(color) / 255f,
            )
            solidBg.draw(bgMaskTex, rgba, bgFinal)
        } else if (bgMaskTex != GlUtils.NO_TEXTURE) {
            mask.draw(tx.textureId, bgMaskTex, MaskShader.MASK_ALPHA, bgFinal)
        } else {
            // 没背景蒙版 → 透明（直接清屏）
            bgFinal.bind()
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            bgFinal.unbind()
        }

        // 7. blend fg over bg
        blend.draw(fgFinal.textureId, bgFinal.textureId, final)

        // 8. passthrough → 当前 EGL surface
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        passthrough.draw(final.textureId, targetW, targetH, flipY)
    }

    /**
     * 构造把"输出 uv → 输入 uv"的 3x3 列主序矩阵。
     *
     * 视觉上视频内容相对视口做 scale + translate（与 [osp.sparkj.viewdsl.bitmap.ZoomImageView] 一致）：
     * - userScale = 1 + 默认 fit_xy（view 与视频同比例由 onMeasure 保证）
     * - userTx / userTy 单位是 view 像素
     *
     * Android Y 朝下、GL 纹理 v 朝上，所以 ty 在矩阵里取负。
     */
    private fun buildUserMatrix(): FloatArray {
        val s = userScale.coerceAtLeast(0.0001f)
        val txN = if (viewW > 0) userTx / viewW else 0f
        val tyN = if (viewH > 0) userTy / viewH else 0f
        // 反向矩阵：u = (vTexCoord.x - txN) / s ;  v = (vTexCoord.y + tyN) / s
        // column-major 3x3
        return floatArrayOf(
            1f / s, 0f, 0f,
            0f, 1f / s, 0f,
            -txN / s, tyN / s, 1f,
        )
    }

    // ============= 分割：截最近一帧投递 =============

    private fun submitSegmentation() {
        if (!segmenterReady) return
        val raw = rawFbo ?: return
        val bmp = readRawAsBitmap(raw, 256) ?: return
        segmenter.submit(bmp, SystemClock.uptimeMillis())
    }

    /** 读 raw FBO 中的当前帧到一张 [maxSide] 边长的 Bitmap（缩放就在 glReadPixels 后做）。 */
    private fun readRawAsBitmap(raw: FboTexture, maxSide: Int): Bitmap? {
        val srcW = raw.width
        val srcH = raw.height
        val pixels = IntBuffer.allocate(srcW * srcH)
        raw.bind()
        GLES30.glReadPixels(0, 0, srcW, srcH, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
        raw.unbind()

        val arr = IntArray(srcW * srcH)
        pixels.rewind()
        pixels.get(arr)

        // glReadPixels 顺序与 Bitmap 不同：Y 翻转、且 RGBA -> ARGB
        val flipped = IntArray(arr.size)
        for (y in 0 until srcH) {
            val srcRow = y * srcW
            val dstRow = (srcH - 1 - y) * srcW
            for (x in 0 until srcW) {
                val c = arr[srcRow + x]
                val r = c and 0xFF
                val g = (c shr 8) and 0xFF
                val b = (c shr 16) and 0xFF
                val a = (c shr 24) and 0xFF
                flipped[dstRow + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val full = Bitmap.createBitmap(flipped, srcW, srcH, Bitmap.Config.ARGB_8888)
        val scale = maxSide.toFloat() / maxOf(srcW, srcH).coerceAtLeast(1)
        return if (scale >= 1f) full else full.scale(
            (srcW * scale).toInt().coerceAtLeast(1),
            (srcH * scale).toInt().coerceAtLeast(1),
        ).also { full.recycle() }
    }

    private fun uploadLatestPersonMask() {
        val frame: MaskFrame = segmenter.latestMask() ?: return
        ensureCurrent()
        if (personMaskTex == GlUtils.NO_TEXTURE ||
            personMaskW != frame.width || personMaskH != frame.height
        ) {
            GlUtils.deleteTexture(personMaskTex)
            personMaskTex = GlUtils.createR8Texture(frame.width, frame.height)
            personMaskW = frame.width
            personMaskH = frame.height
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, personMaskTex)
        frame.data.rewind()
        GLES30.glTexSubImage2D(
            GLES30.GL_TEXTURE_2D, 0, 0, 0,
            frame.width, frame.height,
            GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE,
            frame.data
        )
    }

    // ============= 导出 =============

    private fun runExport(
        outputPath: String,
        outputWidth: Int,
        outputHeight: Int,
        bitRate: Int,
        sourceUri: Uri,
        onProgress: (Float) -> Unit,
    ) {
        // 暂停预览解码（独占 OES 给导出解码器）
        val wasPlaying = previewSource.isPlaying()
        previewSource.pause()
        previewSource.setSurface(null)

        val encoder = VideoEncoder(outputPath, outputWidth, outputHeight, 30, bitRate)
        val inputSurface = encoder.init()
        encoderSurface = eglCore.createWindowSurface(inputSurface)

        val decoder = ExportDecoder(context)
        try {
            decoder.init(sourceUri, oesTexture.surface!!)
            videoW = decoder.videoWidth
            videoH = decoder.videoHeight
            oesTexture.setDefaultBufferSize(videoW, videoH)
            eglCore.makeCurrent(encoderSurface)
            ensureFbos()

            val totalUs = decoder.durationUs.coerceAtLeast(1)

            // 等待新帧的简单同步：用 AtomicBoolean + wait/notify 太重，
            // 这里用"自旋拉 OES updateTexImage"：解码出帧后 SurfaceTexture 会发 onFrameAvailable，
            // 但我们已经独占 handler 线程，下不到回调；改用直接调用 updateTexImage 检查 timestamp。
            var lastTimestamp = -1L
            while (!decoder.isEos) {
                val pts = decoder.decodeOneFrame()
                if (pts == -1L) break
                if (pts == -2L) continue
                // 等待 OES 写入完成；超时退出避免死锁
                val start = SystemClock.uptimeMillis()
                var ts = lastTimestamp
                while (ts == lastTimestamp && SystemClock.uptimeMillis() - start < 200) {
                    eglCore.makeCurrent(encoderSurface)
                    ts = oesTexture.updateTexImage()
                    if (ts == lastTimestamp) Thread.sleep(2)
                }
                lastTimestamp = ts
                oesTexture.getTransformMatrix(oesMatrix)
                hasFrame = true

                // 同步分割：导出强同步保证 mask 与帧严格对齐
                if (segmenterReady) {
                    val raw = rawFbo
                    if (raw != null) {
                        // 先把 raw FBO 填一份，给 segmenter 拍照
                        eglCore.makeCurrent(encoderSurface)
                        oesToFbo.draw(oesTexture.textureId, oesMatrix, raw)
                        val bmp = readRawAsBitmap(raw, 256)
                        if (bmp != null) segmenter.process(bmp, pts / 1000)
                        uploadLatestPersonMask()
                    }
                }

                eglCore.makeCurrent(encoderSurface)
                runPipeline(targetW = outputWidth, targetH = outputHeight, flipY = false)
                eglCore.setPresentationTime(encoderSurface, pts * 1000)
                eglCore.swapBuffers(encoderSurface)
                encoder.drainEncoder(false)

                onProgress((pts.toFloat() / totalUs).coerceIn(0f, 1f))
            }
            encoder.drainEncoder(true)
        } finally {
            try {
                decoder.release()
            } catch (_: Throwable) {
            }
            try {
                encoder.release()
            } catch (_: Throwable) {
            }
            if (encoderSurface != EGL14.EGL_NO_SURFACE) {
                eglCore.makeNothingCurrent()
                eglCore.destroySurface(encoderSurface)
                encoderSurface = EGL14.EGL_NO_SURFACE
            }
            // 恢复预览
            previewSource.setSurface(oesTexture.surface)
            if (wasPlaying) previewSource.play()
            renderIfReady()
        }
    }
}
