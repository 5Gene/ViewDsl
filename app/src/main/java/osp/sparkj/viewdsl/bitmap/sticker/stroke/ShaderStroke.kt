package osp.sparkj.viewdsl.bitmap.sticker.stroke

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import osp.sparkj.viewdsl.bitmap.sticker.core.RunCtx

/**
 * 高品质描边。
 *
 * **离线 Bitmap 流水线**（当前使用场景）：
 * `RuntimeShader` 要求硬件加速 Canvas，而 `Canvas(bitmap)` 始终是软件 Canvas，
 * 因此 Bitmap 处理路径始终委托给 [AlphaStroke]（CPU 圆周偏移描边）。
 * `samples` 默认 32，品质已接近 AGSL 效果。
 *
 * **View 级实时渲染**（未来扩展点）：
 * 若需要 AGSL 品质可通过 [buildRuntimeShader] 拿到 [RuntimeShader]，
 * 配合 `View.setRenderEffect(RenderEffect.createShaderEffect(shader))` 在 View 层应用，
 * 不走 Bitmap 流水线。
 */
class ShaderStroke(
    val width: Int,
    val color: Int,
    private val alphaThreshold: Float = 0.1f,
    private val samples: Int = 32
) : StrokeStep {

    override fun process(ctx: RunCtx, input: Bitmap): Bitmap {
        if (width <= 0) return input
        // RuntimeShader 是 GPU 着色器，Android 平台要求它只能在硬件加速 Canvas 上绘制。
        // 离线 Bitmap 处理使用 Canvas(bitmap)，该 Canvas 始终是软件渲染（Software Canvas），
        // 调用 canvas.drawRect 时会直接抛出：
        //   IllegalArgumentException: Software rendering doesn't support RuntimeShader
        // 这是平台硬性限制，无法绕过，因此 Bitmap 流水线路径统一委托给 AlphaStroke（CPU 实现）。
        // 若需要 AGSL 品质，可通过 buildRuntimeShader() + View.setRenderEffect() 在 View 层应用。
        return AlphaStroke(width, color, samples).process(ctx, input)
    }

    /**
     * 返回用于 View 层实时渲染的 [RuntimeShader]（Android 13+）。
     *
     * 使用方式：
     * ```kotlin
     * val shader = ShaderStroke(15, Color.WHITE).buildRuntimeShader(bitmap)
     * view.setRenderEffect(RenderEffect.createShaderEffect(shader))
     * ```
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun buildRuntimeShader(src: Bitmap): RuntimeShader {
        val agsl = """
            uniform shader src;
            uniform float width;
            uniform float threshold;
            layout(color) uniform half4 strokeColor;

            half4 main(float2 p) {
                half4 c = src.eval(p);
                if (c.a > 0.5) return c;
                float maxA = 0.0;
                for (int r = 1; r <= 3; r++) {
                    float radius = width * float(r) / 3.0;
                    for (int i = 0; i < 16; i++) {
                        float angle = 6.2831853 * float(i) / 16.0;
                        float2 offset = float2(cos(angle), sin(angle)) * radius;
                        maxA = max(maxA, src.eval(p + offset).a);
                    }
                }
                if (maxA > threshold) return strokeColor;
                return half4(0.0);
            }
        """.trimIndent()

        return RuntimeShader(agsl).apply {
            setInputShader("src", BitmapShader(src, Shader.TileMode.DECAL, Shader.TileMode.DECAL))
            setFloatUniform("width", width.toFloat())
            setFloatUniform("threshold", alphaThreshold)
            setColorUniform("strokeColor", color)
        }
    }
}
