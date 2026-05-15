package osp.sparkj.viewdsl.glvideo.render

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

/**
 * EGL 14 + OpenGL ES 3.0 环境封装。
 *
 * - 一个 [EGLContext]，可以挂载多个 [EGLSurface]（预览 SurfaceView / MediaCodec input / PBuffer）
 * - 配置带 [EGLExt.EGL_RECORDABLE_ANDROID]，是 MediaCodec input Surface 创建 windowSurface 的必要条件
 * - 配置带 8 位 alpha，渲染中允许透明通道
 */
class EglCore {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null

    fun init(sharedContext: EGLContext = EGL14.EGL_NO_CONTEXT) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        require(display != EGL14.EGL_NO_DISPLAY) { "无法获取 EGL Display" }

        val version = IntArray(2)
        require(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize 失败" }

        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        require(
            EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0) &&
                    numConfigs[0] > 0
        ) { "eglChooseConfig 找不到匹配项" }
        config = configs[0]

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, sharedContext, ctxAttribs, 0)
        checkError("eglCreateContext")
        require(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext 返回空上下文" }
    }

    /** 把 [surface]（SurfaceView / MediaCodec input）包装成 EGL window surface。 */
    fun createWindowSurface(surface: Surface): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        val es = EGL14.eglCreateWindowSurface(display, config, surface, attribs, 0)
        checkError("eglCreateWindowSurface")
        require(es != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface 返回空 surface" }
        return es
    }

    /** 离屏 PBuffer，没有可见目标时用。 */
    fun createOffscreenSurface(width: Int, height: Int): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        val es = EGL14.eglCreatePbufferSurface(display, config, attribs, 0)
        checkError("eglCreatePbufferSurface")
        return es
    }

    fun makeCurrent(surface: EGLSurface) {
        require(EGL14.eglMakeCurrent(display, surface, surface, context)) {
            "eglMakeCurrent 失败 0x${EGL14.eglGetError().toString(16)}"
        }
    }

    fun makeNothingCurrent() {
        EGL14.eglMakeCurrent(
            display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
        )
    }

    fun swapBuffers(surface: EGLSurface): Boolean = EGL14.eglSwapBuffers(display, surface)

    /** 设置当前 surface 的呈现时间戳（纳秒），写给 MediaCodec encoder 用。 */
    fun setPresentationTime(surface: EGLSurface, nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(display, surface, nsecs)
    }

    fun destroySurface(surface: EGLSurface) {
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
    }

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            makeNothingCurrent()
            EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        config = null
    }

    private fun checkError(op: String) {
        val err = EGL14.eglGetError()
        if (err != EGL14.EGL_SUCCESS) throw RuntimeException("$op: EGL error 0x${err.toString(16)}")
    }
}
