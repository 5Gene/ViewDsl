package osp.spark.view.wings

import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia


//https://android-docs.cn/training/data-storage/shared/photopicker?hl=zh-cn

interface ActivityResultRegister<I, O> {

    /**
     * 必须在activity的onCreate中执行
     */
    abstract fun registerForActivityResult(
        activityResultRegister: (
            ActivityResultContract<I, O>,
            ActivityResultCallback<O>
        ) -> ActivityResultLauncher<I>
    )
}

//Kotlin 会将某些泛型类型转换为带有通配符的 Java 泛型类型（例如 List<Uri> 转换为 List<? extends Uri>）。这种转换有时会导致不必要的复杂性或警告。
//使用 @JvmSuppressWildcards 可以告诉编译器不要为该泛型类型生成通配符，而是直接使用原始类型。
class VisualMediaPicker : ActivityResultRegister<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>> {

    /**
     * VisualMediaPicker.registerForActivityResult(this::registerForActivityResult);
     */
    override fun registerForActivityResult(
        activityResultRegister: (
            ActivityResultContract<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>, ActivityResultCallback<List<@JvmSuppressWildcards Uri>>
        ) -> ActivityResultLauncher<PickVisualMediaRequest>
    ) {
        val launcher = activityResultRegister(PickMultipleVisualMedia(10), { _ -> })
    }
}