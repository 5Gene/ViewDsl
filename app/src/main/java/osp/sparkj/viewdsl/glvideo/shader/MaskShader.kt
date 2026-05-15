package osp.sparkj.viewdsl.glvideo.shader

import android.opengl.GLES30
import osp.sparkj.viewdsl.glvideo.render.FboTexture

/**
 * 通用蒙版：`output = content × mask.a`
 *
 * - content：任意 RGBA 纹理（视频帧、纯色背景等）
 * - mask：可以是 PNG 自身 alpha（[maskChannel] = MASK_ALPHA）或单通道 R8（MediaPipe，[maskChannel] = MASK_RED）
 *
 * 蒙版自动按 fit_xy 拉到 content 大小（采样坐标都用 vTexCoord，shader 不做 size 校正）。
 */
class MaskShader : BaseShaderProgram() {

    companion object {
        /** 蒙版采 .a 通道（普通 PNG）。 */
        const val MASK_ALPHA = 0

        /** 蒙版采 .r 通道（MediaPipe / 单通道 R8 纹理）。 */
        const val MASK_RED = 1
    }

    private var uContentTex = -1
    private var uMaskTex = -1
    private var uMaskChannel = -1

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
        uniform sampler2D uContentTex;
        uniform sampler2D uMaskTex;
        uniform int uMaskChannel; // 0=alpha, 1=red
        out vec4 fragColor;
        void main() {
            vec4 content = texture(uContentTex, vTexCoord);
            vec4 maskTexel = texture(uMaskTex, vTexCoord);
            float m = (uMaskChannel == 1) ? maskTexel.r : maskTexel.a;
            // 预乘 alpha：rgb 也跟着 mask 衰减，方便 BlendShader 直接相加
            fragColor = vec4(content.rgb * m, content.a * m);
        }
    """.trimIndent()

    override fun onProgramCreated() {
        uContentTex = GLES30.glGetUniformLocation(programId, "uContentTex")
        uMaskTex = GLES30.glGetUniformLocation(programId, "uMaskTex")
        uMaskChannel = GLES30.glGetUniformLocation(programId, "uMaskChannel")
    }

    fun draw(
        contentTextureId: Int,
        maskTextureId: Int,
        maskChannel: Int = MASK_ALPHA,
        target: FboTexture,
    ) {
        target.bind()
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, contentTextureId)
        GLES30.glUniform1i(uContentTex, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, maskTextureId)
        GLES30.glUniform1i(uMaskTex, 1)

        GLES30.glUniform1i(uMaskChannel, maskChannel)

        drawFullscreenQuad()
        target.unbind()
    }
}
