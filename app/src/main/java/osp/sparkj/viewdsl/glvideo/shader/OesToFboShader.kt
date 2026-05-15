package osp.sparkj.viewdsl.glvideo.shader

import android.opengl.GLES11Ext
import android.opengl.GLES30
import osp.sparkj.viewdsl.glvideo.render.FboTexture

/**
 * OES（[GLES11Ext.GL_TEXTURE_EXTERNAL_OES]）外部纹理 → 普通 2D 纹理 FBO。
 *
 * MediaCodec / MediaPlayer 解出的视频帧只能通过 `samplerExternalOES` 取样；
 * 这一步把它"翻译"成通用 RGBA 纹理供后续 shader 使用，同时利用 SurfaceTexture 提供的
 * `uTexMatrix` 校正上下翻转 / 旋转。
 */
class OesToFboShader : BaseShaderProgram() {

    private var uTexMatrix = -1
    private var uOesTex = -1

    override fun vertexShaderSource(): String = """
        #version 300 es
        in vec4 aPosition;
        in vec2 aTexCoord;
        uniform mat4 uTexMatrix;
        out vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
        }
    """.trimIndent()

    override fun fragmentShaderSource(): String = """
        #version 300 es
        #extension GL_OES_EGL_image_external_essl3 : require
        precision mediump float;
        in vec2 vTexCoord;
        uniform samplerExternalOES uOesTex;
        out vec4 fragColor;
        void main() {
            fragColor = texture(uOesTex, vTexCoord);
        }
    """.trimIndent()

    override fun onProgramCreated() {
        uTexMatrix = GLES30.glGetUniformLocation(programId, "uTexMatrix")
        uOesTex = GLES30.glGetUniformLocation(programId, "uOesTex")
    }

    fun draw(oesTextureId: Int, texMatrix: FloatArray, target: FboTexture) {
        target.bind()
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glUniform1i(uOesTex, 0)
        GLES30.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)

        drawFullscreenQuad()
        target.unbind()
    }
}
