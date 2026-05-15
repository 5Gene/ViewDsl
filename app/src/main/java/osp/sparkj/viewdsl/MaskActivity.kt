package osp.sparkj.viewdsl

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView.ScaleType
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import osp.sparkj.viewdsl.bitmap.ZoomImageView
import osp.sparkj.viewdsl.databinding.ActivityBitmapBinding

class MaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBitmapBinding

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        allZoomViews().forEach { it.setImageURI(uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBitmapBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            // top 不加 padding，让内容延伸到状态栏下面；左右底保留安全区
//            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
//            insets
//        }

        setupViews()

        // 短按：打开相册选图，更新全部 ZoomImageView
        binding.fabShare.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ImageOnly))
        }

        // 长按：截取 bg_mask_layout 和 dof_mask_layout，显示在底部预览区
        binding.fabShare.setOnLongClickListener {
            captureAndPreview()
            true
        }
    }

    private fun setupViews() {
        // ── 顶层：outer ─────────────────────────────────────────────────────
        // 主交互层，origin_img / bg_img 均以它为 anchor 跟随缩放和移动
        binding.outer.apply {
            scaleType = ScaleType.CENTER_CROP
            setImageResource(R.drawable.chutian2)
        }

        // ── 中间层：bg_img（经 bg_mask 裁切）────────────────────────────────
        binding.bgImg.apply {
            scaleType = ScaleType.CENTER_CROP
            setImageResource(R.drawable.chutian2)
            setAnchor(binding.outer)
        }

        // ── 底层：origin_img（背景原图，无蒙版）──────────────────────────────
        binding.originImg.apply {
            scaleType = ScaleType.CENTER_CROP
            setImageResource(R.drawable.chutian2)
            setAnchor(binding.outer)
        }
    }

    private fun captureAndPreview() {
        binding.ivBgCapture.setImageBitmap(binding.bgMaskLayout.capture())
        binding.ivDofCapture.setImageBitmap(binding.dofMaskLayout.capture())
    }

    private fun allZoomViews(): List<ZoomImageView> = listOf(
        binding.originImg,
        binding.bgImg,
        binding.outer,
    )
}
