package osp.sparkj.viewdsl.glvideo.shader

import android.opengl.GLES30
import osp.sparkj.viewdsl.glvideo.render.FboTexture

/**
 * Porter-Duff Source Over：前景叠在背景上方。
 *
 * 假设两路输入都是 **预乘 alpha** 格式（[MaskShader] / [SolidColorShader] 都遵守这个约定）：
 * ```
 * out.rgb = src.rgb + dst.rgb * (1 - src.a)
 * out.a   = src.a   + dst.a   * (1 - src.a)
 * ```
 */
class BlendShader : BaseShaderProgram() {

    private var uForeground = -1
    private var uBackground = -1

    override fun vertexShaderSource(): String = """
        #version 300 es
        in vec4 aPosition;
        in vec2 aTexCoord;
        out vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    override fun fragmentShaderSource(): String = """
        #version 300 es
        precision mediump float;
        in vec2 vTexCoord;
        uniform sampler2D uForeground;
        uniform sampler2D uBackground;
        out vec4 fragColor;
        void main() {
            vec4 fg = texture(uForeground, vTexCoord);
            vec4 bg = texture(uBackground, vTexCoord);
            float oneMinusFa = 1.0 - fg.a;
            fragColor = vec4(fg.rgb + bg.rgb * oneMinusFa, fg.a + bg.a * oneMinusFa);
        }
    """.trimIndent()

    override fun onProgramCreated() {
        uForeground = GLES30.glGetUniformLocation(programId, "uForeground")
        uBackground = GLES30.glGetUniformLocation(programId, "uBackground")
    }

    fun draw(foregroundTex: Int, backgroundTex: Int, target: FboTexture) {
        target.bind()
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, foregroundTex)
        GLES30.glUniform1i(uForeground, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backgroundTex)
        GLES30.glUniform1i(uBackground, 1)

        drawFullscreenQuad()
        target.unbind()
    }
}
