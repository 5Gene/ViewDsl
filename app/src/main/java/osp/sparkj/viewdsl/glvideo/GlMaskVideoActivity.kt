package osp.sparkj.viewdsl.glvideo

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import osp.sparkj.viewdsl.R
import osp.sparkj.viewdsl.databinding.ActivityGlMaskVideoBinding
import java.io.File

/**
 * GlMaskVideoView 演示 Activity。
 *
 * 交互：
 * - 右下 FAB：选视频（短按）/ 导出 MP4（长按）
 * - 左下 FAB：播放 / 暂停
 * - 中下 FAB：在"视频背景"和"纯色背景"之间切换
 * - 控件本身：双击 / 双指缩放 / 拖动
 */
class GlMaskVideoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGlMaskVideoBinding
    private var playing = false

    /**
     * 背景模式：
     * - false = 用视频自身作背景（默认）
     * - true = 用纯色作背景
     */
    private var usesSolidBg = false
    private val solidColor: Int = Color.parseColor("#FF4081")

    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        binding.glMaskVideo.setVideoSource(uri)
        playing = true
        updatePlayIcon()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityGlMaskVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.glMaskRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }

        // 默认蒙版与背景设置
        binding.glMaskVideo.setForegroundMask(
            ContextCompat.getDrawable(this, R.drawable.out_mask)
        )
        binding.glMaskVideo.setBackgroundMask(
            ContextCompat.getDrawable(this, R.drawable.bg_mask2)
        )
        binding.glMaskVideo.setSolidBackgroundColor(null) // 视频背景

        binding.fabPickVideo.setOnClickListener {
            pickVideo.launch(PickVisualMediaRequest(VideoOnly))
        }
        binding.fabPickVideo.setOnLongClickListener {
            exportNow()
            true
        }
        binding.fabPlayPause.setOnClickListener {
            if (playing) binding.glMaskVideo.pause() else binding.glMaskVideo.play()
            playing = !playing
            updatePlayIcon()
        }
        binding.fabBgMode.setOnClickListener {
            usesSolidBg = !usesSolidBg
            binding.glMaskVideo.setSolidBackgroundColor(if (usesSolidBg) solidColor else null)
            binding.tvStatus.text = if (usesSolidBg) "背景：纯色" else "背景：视频"
        }
    }

    private fun updatePlayIcon() {
        binding.fabPlayPause.setImageResource(
            if (playing) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun exportNow() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val out = File(dir, "gl_mask_export_${System.currentTimeMillis()}.mp4")
        binding.tvStatus.text = "导出中…"
        binding.glMaskVideo.export(
            outputPath = out.absolutePath,
            onProgress = { p ->
                binding.tvStatus.text = "导出中… ${(p * 100).toInt()}%"
            },
            onComplete = { ok, err ->
                binding.tvStatus.text = if (ok) "导出完成：${out.absolutePath}" else "导出失败：$err"
                Toast.makeText(
                    this,
                    if (ok) "导出完成" else "导出失败：$err",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    override fun onDestroy() {
        binding.glMaskVideo.release()
        super.onDestroy()
    }
}
