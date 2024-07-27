package osp.june.wings

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File


//https://developer.android.google.cn/training/data-storage/shared/media?hl=zh-cn
//https://ppting.me/2020/04/19/2020_04_19_how_to_use_Android_MediaStore_Api/
/**
 * @param more 如果是视频文件 可以补充 视频长度，宽高等信息
 * @param destFolder 目标父文件夹 根据文件类型有限制，不传会有默认路径比如视频默认存放在Movies下
 */
fun File.copyTo(
    context: Context,
    destFolder: String? = null,
    deleteOrigin: Boolean = false,
    more: (ContentValues.() -> Unit)? = null
) {
    //todo destFolder没传值的时候不同文件保存的默认路径
    val resolver = context.contentResolver
//    MediaStore.Images.Media.insertImage
//    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//    只要文件可通过 MediaStore.Images、MediaStore.Video 或 MediaStore.Audio 查询进行查看，则表示该文件也可以通过 MediaStore.Files 查询进行查看
//    MediaStore.Files.getContentUri("external") 只能存在[Download, Documents]
//    MediaStore.Video.Media.EXTERNAL_CONTENT_URI 只能存在[Movies, DCIM, Pictures]
//    MediaStore.Images.Media.EXTERNAL_CONTENT_URI 只能存在[DCIM, Pictures]
//    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI 只能存在[Alarms, Music, Notifications, Podcasts, Ringtones]
    val (mediaType, uri) = typeUri()
    resolver.insert(uri, ContentValues().apply {
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType())
        put(MediaStore.Files.FileColumns.MEDIA_TYPE, mediaType)
        val date = System.currentTimeMillis() / 1000
//        put(MediaStore.Files.FileColumns.DATE_ADDED, date)
        put(MediaStore.MediaColumns.DATE_ADDED, date)
//        put(MediaStore.Images.Media.DATE_ADDED, date)
//        put(MediaStore.Files.FileColumns.DATE_MODIFIED, date)
        put(MediaStore.MediaColumns.DATE_MODIFIED, date)
//        put(MediaStore.Images.Media.DATE_MODIFIED, date)
//        put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
//        relative_path	指定保存的文件目录，
//        例如上文我们将这个图片保存到了 Pictures/DemoPicture 文件夹下，如果不设置这个值，则会被默认保存到对应的媒体类型的文件夹下，
//        例如，图片文件(mimeType = image/*)会被保存到 Pictures(Environment#DIRECTORY_PICTURES) 中，需要注意的是，不能将文件放置到不对应的顶级文件夹下，
//        比如将一个 mimeType 为 audio/mpeg 放大 Pictures 这样的行为是不被允许的，也就是如果设置 MIME_TYPE = audia/*
//        并将 RELATIVE_PATH 设置为 Environment#DIRECTORY_PICTURES 这样是会 Throw IllegalArgumentException 的
//        图片(image/*)	DCIM,Pictures
//        音频(audio/*)	Alarms, Music, Notifications, Podcasts, Ringtones
//        视频(video/*)	Movies
//        文档(file/*)	Documents,Download
        destFolder?.let {
            //相对目录 Pictures/xxx
            put(MediaStore.MediaColumns.RELATIVE_PATH, destFolder)
        }
        put(MediaStore.MediaColumns.SIZE, length())
//        put(MediaStore.Images.Media.RELATIVE_PATH, destPath)
        //通过检查 MediaStore.Images.Media.IS_PENDING 列的值，可以确定图像是否处于待定状态。如果该列的值为 1，则表示图像处于待定状态；如果值为 0，则表示图像处于可用状态。
        put(MediaStore.Files.FileColumns.IS_PENDING, 0)
        more?.invoke(this)
    })?.run {
        Log.d("FileOperate", "copyTo() called insert succeed Uri:$this path:${this.path}")
        context.contentResolver.openOutputStream(this)?.use { outputStream ->
            inputStream().use { fileInputStream ->
                fileInputStream.copyTo(outputStream)
            }
        }
        if (deleteOrigin) {
            delete()
//            deleteRecursively()//删除文件夹
        }
    }
}


fun File.del(context: Context): Boolean {
//    val (_, uri) = typeUri()
    val uri = MediaStore.Files.getContentUri("external")
    val selection = "${MediaStore.MediaColumns.SIZE}=?AND${MediaStore.MediaColumns.DISPLAY_NAME}=?"
    val selectionArgs = arrayOf(length().toString(), name)
    // 执行删除操作，并获取删除的行数
    val result = context.contentResolver.delete(uri, selection, selectionArgs)
    return result > 0
}


fun File.typeUri(): Pair<Int, Uri> {
    val mimeType = mimeType()
    Log.d("FileOperate", "typeUri() mimeType:$mimeType")
    return if (mimeType.startsWith("video", true)) {
        Pair(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    } else if (mimeType.startsWith("audio", true)) {
        Pair(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    } else if (mimeType.startsWith("image", true)) {
        Pair(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mimeType.startsWith("text", true)) {
        Pair(MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT, MediaStore.Files.getContentUri("external"))
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        Regex(".*[stl|webvtt|stl|sbv|ass|ttml|dfxp]", RegexOption.IGNORE_CASE).matches(mimeType)
    ) {
        //字幕
        Pair(MediaStore.Files.FileColumns.MEDIA_TYPE_SUBTITLE, MediaStore.Files.getContentUri("external"))
    } else {
        Pair(MediaStore.Files.FileColumns.MEDIA_TYPE_NONE, MediaStore.Files.getContentUri("external"))
    }
}

fun File.mimeType(): String {
    var extension = MimeTypeMap.getFileExtensionFromUrl(path)
    if (TextUtils.isEmpty(extension)) {
        extension = name.substring(name.lastIndexOf(".") + 1)
    }
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/$extension"
}

//https://developer.android.google.cn/training/data-storage/manage-all-files?hl=zh-cn
fun getManagerAllFileIntent(): Intent? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
        return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }
    return null
}

/**
 * 判断是否文件操作自由，并请求权限，可以在任意目录创建删除任意文件，不包括私有目录
 * @param dialog 没权限的时候弹窗提示
 */
fun Activity.freeFileOperate(
    dialog: (() -> Unit) -> Unit = { it() }
): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
        dialog {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
        return false
    }
    return true
}