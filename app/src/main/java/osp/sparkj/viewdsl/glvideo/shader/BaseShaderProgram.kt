package osp.sparkj.viewdsl.glvideo.shader

import android.opengl.GLES30
import osp.sparkj.viewdsl.glvideo.render.GlUtils

/**
 * Shader 基类：负责编译/链接 + 全屏四边形 VBO 共享。
 *
 * 子类约定：
 * - 在 [create] 里调 [buildAndCacheLocations]，把 attribute / uniform 位置一次性查出来缓存
 * - 子类 `draw(...)` 自己负责 bind FBO、绑纹理、传 uniform、最后调 [drawFullscreenQuad]
 */
abstract class BaseShaderProgram {

    protected var programId: Int = 0
        private set

    /** 全屏四边形 VBO（position2 + texCoord2 交错），4 个顶点，stride 16 字节。 */
    private var vbo: Int = 0

    protected var aPosition: Int = -1
        private set
    protected var aTexCoord: Int = -1
        private set

    /** 子类在 onProgramCreated 后里继续查 uniform location。 */
    protected open fun onProgramCreated() = Unit

    fun create() {
        programId = GlUtils.buildProgram(vertexShaderSource(), fragmentShaderSource())
        aPosition = GLES30.glGetAttribLocation(programId, "aPosition")
        aTexCoord = GLES30.glGetAttribLocation(programId, "aTexCoord")
        vbo = createFullscreenVbo()
        onProgramCreated()
    }

    protected abstract fun vertexShaderSource(): String
    protected abstract fun fragmentShaderSource(): String

    fun use() = GLES30.glUseProgram(programId)

    /** 绑定 VBO 并把 aPosition / aTexCoord 指针接上；子类 draw 末尾调一次此函数即出图。 */
    protected fun drawFullscreenQuad() {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val stride = 4 * 4
        GLES30.glEnableVertexAttribArray(aPosition)
        GLES30.glVertexAttribPointer(aPosition, 2, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(aTexCoord)
        GLES30.glVertexAttribPointer(aTexCoord, 2, GLES30.GL_FLOAT, false, stride, 2 * 4)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(aPosition)
        GLES30.glDisableVertexAttribArray(aTexCoord)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun createFullscreenVbo(): Int {
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        val id = ids[0]
        val buf = GlUtils.allocFloatBuffer(GlUtils.FULLSCREEN_VERTICES)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, id)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            buf.capacity() * 4,
            buf,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        return id
    }

    open fun release() {
        if (programId != 0) GLES30.glDeleteProgram(programId)
        if (vbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
        programId = 0
        vbo = 0
    }
}
