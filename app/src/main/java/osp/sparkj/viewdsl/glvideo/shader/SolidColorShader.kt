package osp.sparkj.viewdsl.glvideo.shader

import android.opengl.GLES30
import osp.sparkj.viewdsl.glvideo.render.FboTexture

/**
 * 用形状蒙版裁切的纯色填充：`output = color × mask.a`（预乘）。
 *
 * 用作"纯色背景层"：bg_mask2 不透明区域内填颜色，其它透明。
 */
class SolidColorShader : BaseShaderProgram() {

    private var uMaskTex = -1
    private var uColor = -1

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
        uniform sampler2D uMaskTex;
        uniform vec4 uColor;       // RGBA 0~1，非预乘
        out vec4 fragColor;
        void main() {
            float m = texture(uMaskTex, vTexCoord).a;
            float a = uColor.a * m;
            fragColor = vec4(uColor.rgb * a, a);
        }
    """.trimIndent()

    override fun onProgramCreated() {
        uMaskTex = GLES30.glGetUniformLocation(programId, "uMaskTex")
        uColor = GLES30.glGetUniformLocation(programId, "uColor")
    }

    fun draw(maskTextureId: Int, color: FloatArray, target: FboTexture) {
        target.bind()
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTextureId)
        GLES30.glUniform1i(uMaskTex, 0)
        GLES30.glUniform4fv(uColor, 1, color, 0)

        drawFullscreenQuad()
        target.unbind()
    }
}
