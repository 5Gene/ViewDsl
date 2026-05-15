package osp.sparkj.viewdsl.glvideo.shader

import android.opengl.GLES30

/**
 * 直通 shader：把一张 2D 纹理画到当前绑定的目标（不切 FBO，由调用方决定）。
 *
 * - 调用前用 `glViewport(0, 0, targetW, targetH)` + `glBindFramebuffer(0)` 切到默认帧缓冲
 *   （EGL window surface）
 * - 用于把 finalFbo 输出到预览 / encoder window surface
 *
 * 支持 Y 翻转：MediaCodec encoder input surface 在多数厂商上是上下颠倒的，可传 `flipY=true`。
 */
class PassthroughShader : BaseShaderProgram() {

    private var uInputTex = -1
    private var uFlipY = -1

    override fun vertexShaderSource(): String = """
        #version 300 es
        in vec4 aPosition;
        in vec2 aTexCoord;
        uniform int uFlipY;
        out vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uFlipY == 1) ? vec2(aTexCoord.x, 1.0 - aTexCoord.y) : aTexCoord;
        }
    """.trimIndent()

    override fun fragmentShaderSource(): String = """
        #version 300 es
        precision mediump float;
        in vec2 vTexCoord;
        uniform sampler2D uInputTex;
        out vec4 fragColor;
        void main() {
            fragColor = texture(uInputTex, vTexCoord);
        }
    """.trimIndent()

    override fun onProgramCreated() {
        uInputTex = GLES30.glGetUniformLocation(programId, "uInputTex")
        uFlipY = GLES30.glGetUniformLocation(programId, "uFlipY")
    }

    fun draw(inputTex: Int, viewportW: Int, viewportH: Int, flipY: Boolean = false) {
        GLES30.glViewport(0, 0, viewportW, viewportH)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTex)
        GLES30.glUniform1i(uInputTex, 0)
        GLES30.glUniform1i(uFlipY, if (flipY) 1 else 0)
        drawFullscreenQuad()
    }
}
