package osp.sparkj.viewdsl.glvideo.render

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.view.Surface

/**
 * OES 外部纹理 + [SurfaceTexture] + [Surface] 三位一体的封装。
 *
 * MediaCodec / MediaPlayer 解码输出的帧落到 [surface]，
 * 经 [SurfaceTexture] 通知后，调 [updateTexImage] 把当前帧绑定到 [textureId]，
 * 配合纹理变换矩阵 [getTransformMatrix] 给到 OesToFboShader 即可正确显示。
 */
class OesTexture {

    var textureId: Int = GlUtils.NO_TEXTURE
        private set

    private var surfaceTexture: SurfaceTexture? = null

    var surface: Surface? = null
        private set

    /** 帧抵达时回调（SurfaceTexture 线程）。 */
    var onFrameAvailable: (() -> Unit)? = null

    fun create() {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )
        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener { onFrameAvailable?.invoke() }
        }
        surface = Surface(surfaceTexture)
    }

    /** 必须在 GL 线程调用：抓最新帧到 [textureId]。返回该帧的时间戳（纳秒）。 */
    fun updateTexImage(): Long {
        val st = surfaceTexture ?: return 0L
        st.updateTexImage()
        return st.timestamp
    }

    fun getTransformMatrix(out: FloatArray) {
        surfaceTexture?.getTransformMatrix(out)
    }

    fun setDefaultBufferSize(width: Int, height: Int) {
        surfaceTexture?.setDefaultBufferSize(width, height)
    }

    fun release() {
        surface?.release()
        surfaceTexture?.release()
        GlUtils.deleteTexture(textureId)
        surface = null
        surfaceTexture = null
        textureId = GlUtils.NO_TEXTURE
    }
}
