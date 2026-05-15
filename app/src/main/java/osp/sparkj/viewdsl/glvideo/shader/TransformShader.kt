package osp.sparkj.viewdsl.glvideo.shader

import android.opengl.GLES30
import osp.sparkj.viewdsl.glvideo.render.FboTexture

/**
 * 把"用户手势"（scale + tx + ty）应用到一张已有的 2D 纹理上。
 *
 * ## 坐标语义
 * 输入纹理和输出 FBO 都用规范 0~1 纹理坐标，原点左下。
 *
 * `uTexMatrix` 用列主序 3x3 仿射矩阵，作用在采样坐标上：
 * ```
 * sampleUv = uTexMatrix * vec3(outputUv, 1)
 * ```
 *
 * 视觉上 *fg 内容缩放/平移*，等价于*采样坐标做反向变换*，所以传进来的矩阵已经是逆矩阵
 * （由 [GlMaskVideoView] 根据 userScale / userTx / userTy / viewW / viewH 算好）。
 *
 * 越界采样填透明（CLAMP_TO_EDGE 的话边缘会被拉长，因此 shader 里手动检测越界，发挥
 * alpha 0 的"画布外是透明的"语义，跟 ZoomImageView 的 clamp 行为对齐）。
 */
class TransformShader : BaseShaderProgram() {

    private var uTexMatrix = -1
    private var uInputTex = -1

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
        uniform sampler2D uInputTex;
        uniform mat3 uTexMatrix;
        out vec4 fragColor;
        void main() {
            vec3 uv = uTexMatrix * vec3(vTexCoord, 1.0);
            // 越界 → 透明（视频实际位置外）
            if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                fragColor = vec4(0.0);
            } else {
                fragColor = texture(uInputTex, uv.xy);
            }
        }
    """.trimIndent()

    override fun onProgramCreated() {
        uTexMatrix = GLES30.glGetUniformLocation(programId, "uTexMatrix")
        uInputTex = GLES30.glGetUniformLocation(programId, "uInputTex")
    }

    /**
     * @param matrix3x3 column-major 3x3 矩阵，长度 9
     */
    fun draw(inputTextureId: Int, matrix3x3: FloatArray, target: FboTexture) {
        target.bind()
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTextureId)
        GLES30.glUniform1i(uInputTex, 0)
        GLES30.glUniformMatrix3fv(uTexMatrix, 1, false, matrix3x3, 0)

        drawFullscreenQuad()
        target.unbind()
    }
}
