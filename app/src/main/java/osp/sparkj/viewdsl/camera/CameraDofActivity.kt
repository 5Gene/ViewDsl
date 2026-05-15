package osp.sparkj.viewdsl.camera

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import osp.sparkj.viewdsl.R
import osp.sparkj.viewdsl.databinding.ActivityCameraDofBinding

/**
 * 相机实时景深 Activity。
 *
 * 功能：
 * - 自动申请相机权限
 * - 权限通过后启动 [DofCameraView] 预览 + 实时人物分割
 * - FAB 切换前/后摄
 */
class CameraDofActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraDofBinding

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "需要相机权限才能使用此功能", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCameraDofBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.cameraDofRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }

        requestCameraPermission.launch(Manifest.permission.CAMERA)

        binding.fabFlip.setOnClickListener {
            binding.dofCamera.flipCamera()
        }
    }

    private fun startCamera() {
        // 与 MaskActivity 保持一致：应用 bg_mask 静态形状蒙版
        binding.dofCamera.setFrameMask(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_mask))
        binding.dofCamera.bindCamera(this)
    }
}
