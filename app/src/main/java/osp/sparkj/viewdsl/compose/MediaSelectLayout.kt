package osp.sparkj.viewdsl.compose

import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.SingleMimeType
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import osp.spark.view.wings.toThumbnail
import osp.sparkj.viewdsl.R

class MediaItem(val uri: Uri? = null) {
    override fun hashCode(): Int {
        return 0
    }

    override fun equals(other: Any?): Boolean {
        return uri == (other as? MediaItem)?.uri
    }
}

class MediaSelectViewModel : ViewModel() {

    var maxCount = 5
    val mediasData = MutableLiveData<List<MediaItem>>(listOf(MediaItem()))
    private var pickMedia: ActivityResultLauncher<PickVisualMediaRequest>? = null

    fun registerForActivityResult(
        activityResultRegister: (
            ActivityResultContract<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>, ActivityResultCallback<List<@JvmSuppressWildcards Uri>>
        ) -> ActivityResultLauncher<PickVisualMediaRequest>
    ) {
        pickMedia = activityResultRegister(PickMultipleVisualMedia(maxCount)) { uris ->
            // Callback is invoked after the user selects a media item or closes the
            // photo picker.
            if (uris.isNotEmpty()) {
                Log.d("PhotoPicker", "Selected Uris: $uris")
//                选出的视频图片都是被单独复制出来的不是原文件
//                uris.forEach {
//                    println("-------------------")
//                    it.takePermission()
//                    println(it)
//                    println(it.scheme)
//                    println(it.path)
//                    println(it.authority)
//                    println(it.toFile())
//                    println(it.toFile()!!.length().showFileSize())
//                    println(it.fileDetails())
//                    println(it.toFileName())
//                    println(it.toPath())
//                    println(File(it.toPath()).length().showFileSize())
//                }
                addAll(uris.map { MediaItem(it) })
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }
    }

    fun launch() {
        pickMedia!!.launch(PickVisualMediaRequest(SingleMimeType("*")))
//        pickMedia!!.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
    }

    fun remove(mediaItem: MediaItem) {
        val mediaDatas = mediasData.value!!.toMutableList()
        mediaDatas.remove(mediaItem)
        if (mediaDatas.last().uri != null) {
            mediaDatas.add(MediaItem())
        }
        mediasData.postValue(mediaDatas)
    }

    fun addAll(medias: List<MediaItem>) {
        val mediaDatas = mediasData.value!!.toMutableList()
        val addMediaItem = mediaDatas.removeAt(mediaDatas.size - 1)
        val newMedias = mediaDatas.toMutableSet()
        newMedias.addAll(medias)
        if (newMedias.size < maxCount) {
            newMedias.add(addMediaItem)
            mediasData.postValue(newMedias.toList())
        } else {
            val medias = newMedias.toMutableList()
            mediasData.postValue(medias.subList(0, maxCount))
        }
    }
}


//https://developer.android.com/develop/ui/compose/performance/bestpractices?hl=zh-cn

@Composable
fun MediaSelectLayout() {
    val mediaSelectViewModel = viewModel(MediaSelectViewModel::class)
    val medias by mediaSelectViewModel.mediasData.observeAsState()
    LazyRow {
        items(medias!!, key = { it.uri?.hashCode() ?: "" }) {
            MediaPreview(it) {
                if (it.uri == null) {
                    mediaSelectViewModel.launch()
                } else {
                    mediaSelectViewModel.remove(it)
                }
            }
        }
    }
}


@Composable
fun LazyItemScope.MediaPreview(
    item: MediaItem = MediaItem(),
    onClick: () -> Unit
) {
    Box(
        Modifier
            .animateItem(
                fadeInSpec = tween(durationMillis = 250),
                fadeOutSpec = tween(durationMillis = 100),
                placementSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)
            )
            .padding(end = 4.dp)
            .clickable {
                onClick()
            }) {
        if (item.uri != null) {
            val bitmap = remember {
                item.uri.toThumbnail()!!.asImageBitmap()
            }
            Image(
                bitmap,
                null,
                Modifier
                    .size(50.dp)
                    .padding(top = 6.dp, end = 6.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Image(
                painterResource(R.drawable.icon_media_remove),
                "",
                Modifier
                    .size(16.dp)
                    .align(Alignment.TopEnd)
            )
        } else {
            Image(
                painterResource(R.drawable.icon_media_add),
                "",
                Modifier
                    .size(50.dp)
                    .padding(top = 6.dp, end = 6.dp)
                    .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
            )
        }
    }
}