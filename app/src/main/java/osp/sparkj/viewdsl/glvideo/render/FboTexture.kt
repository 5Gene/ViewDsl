package osp.sparkj.viewdsl.glvideo.render

import android.opengl.GLES30

/**
 * Framebuffer Object + color attachment（RGBA8 2D 纹理）。
 *
 * 用 [bind] 把后续渲染重定向到这个 FBO（自动设 viewport），渲染完用 [unbind] 切回默认帧缓冲。
 * 渲染结果就是这张 [textureId]，可作为下一步 shader 的输入。
 */
class FboTexture(val width: Int, val height: Int) {

    var textureId: Int = GlUtils.NO_TEXTURE
        private set

    private var fboId: Int = 0

    fun create() {
        // 1. color attachment：RGBA 2D 纹理，保留 alpha
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        textureId = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        // 2. FBO 并挂载 attachment
        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        fboId = fboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, textureId, 0
        )

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("FBO 不完整: 0x${status.toString(16)}")
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glViewport(0, 0, width, height)
    }

    fun unbind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun release() {
        if (fboId != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
        GlUtils.deleteTexture(textureId)
        fboId = 0
        textureId = GlUtils.NO_TEXTURE
    }
}
