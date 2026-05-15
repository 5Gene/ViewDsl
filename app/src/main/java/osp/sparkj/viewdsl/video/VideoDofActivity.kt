package osp.sparkj.viewdsl.video

import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import osp.sparkj.viewdsl.R
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import osp.sparkj.viewdsl.databinding.ActivityVideoDofBinding

/**
 * 视频文件景深 Activity。
 *
 * 功能：
 * - FAB（右下角）打开系统文件选取器选一个视频文件
 * - 选中后自动播放，并实时叠加景深遮罩
 * - FAB（左下角）播放/暂停切换
 */
class VideoDofActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoDofBinding
    private var isPlaying = false

    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        binding.dofVideoFile.setVideoUri(uri)
        isPlaying = true
        updatePlayPauseIcon()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVideoDofBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.videoDofRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }

        // 与 MaskActivity 保持一致：应用 bg_mask 静态形状蒙版
        binding.dofVideoFile.setFrameMask(ContextCompat.getDrawable(this, R.drawable.bg_mask))

        // 选择视频文件
        binding.fabPickVideo.setOnClickListener {
            pickVideo.launch(PickVisualMediaRequest(VideoOnly))
        }

        // 播放 / 暂停切换
        binding.fabPlayPause.setOnClickListener {
            if (isPlaying) {
                binding.dofVideoFile.pause()
                isPlaying = false
            } else {
                binding.dofVideoFile.play()
                isPlaying = true
            }
            updatePlayPauseIcon()
        }
    }

    private fun updatePlayPauseIcon() {
        val icon = if (isPlaying)
            android.R.drawable.ic_media_pause
        else
            android.R.drawable.ic_media_play
        binding.fabPlayPause.setImageResource(icon)
    }

    override fun onDestroy() {
        binding.dofVideoFile.release()
        super.onDestroy()
    }
}
