package osp.spark.view.wings

import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import java.io.File

//照片选择
//https://android-docs.cn/training/data-storage/shared/photopicker?hl=zh-cn
//https://developer.android.com/training/data-storage/shared/photopicker?hl=zh-cn

/**
 * 默认情况下，系统会授予应用对媒体文件的访问权限，直到设备重启或应用停止运行。
 * 如果您的应用执行长时间运行的工作（例如在后台上传大型文件），您可能需要将此访问权限保留更长时间。
 * 为此，请调用 takePersistableUriPermission() 方法
 */
fun Uri.takePermission() {
    godContext.contentResolver.takePersistableUriPermission(
        this, Intent.FLAG_GRANT_READ_URI_PERMISSION
    )
}

fun Uri.toFile(): File? {
    try {//"image/jpeg", "image/png", "image/gif", "video/mp4"
        val mimeType = godContext.contentResolver.getType(this)
        if (mimeType == null) {
            return null
        }
        val extension = mimeType.substringAfterLast('/')
        val fileName = "temp_file_${hashCode()}.$extension"
        val tempFile = File(godContext.cacheDir, "temp/$fileName")
        if (tempFile.exists()) {
            return tempFile
        }
        tempFile.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
        godContext.contentResolver.openInputStream(this)?.use { inputStream ->
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return tempFile
    } catch (e: SecurityException) {
        val tips = """
            当您的应用打开文件以进行读取或写入时，系统会授予您的应用该文件的 URI 权限，该权限持续到用户设备重启
            为了在设备重启后保留对文件的访问权限并创造更好的用户体验，您的应用可以“获取”系统提供的持久化 URI 权限授予，如下面的代码片段所示
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(this, takeFlags)
            
            https://android-docs.cn/training/data-storage/shared/documents-files?hl=zh-cn
        """.trimIndent()
        throw SecurityException("$tips \n${e.message}", e.cause)
    }
}

fun Uri.toThumbnail(
    size: Size = Size(520, 520),
): Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    godContext.contentResolver.loadThumbnail(this, size, null)
} else {
    MediaStore.Images.Thumbnails.getThumbnail(
        godContext.contentResolver,
        parseId(),
        MediaStore.Images.Thumbnails.MINI_KIND, null
    ) ?: MediaStore.Video.Thumbnails.getThumbnail(
        godContext.contentResolver,
        parseId(),
        MediaStore.Video.Thumbnails.MINI_KIND, null
    )
}


// 扩展函数，用于从Uri中提取ID
fun Uri.parseId(): Long {
    return this.lastPathSegment?.split(":")?.get(1)?.toLongOrNull() ?: 0L
}


fun Uri.fileDetails(): Triple<String?, String?, String?> {
    val name = toFileName()
    val type = godContext.contentResolver.getType(this)
    val path = toPath()
    return Triple(name, type, path)
}

fun Uri.toFileName(): String? {
    var result: String? = null
    if (scheme == "content") {
        val cursor: Cursor? = godContext.contentResolver.query(this, null, null, null, null)
        cursor.use {
            if (it != null && it.moveToFirst()) {
                result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
    }
    if (result == null) {
        result = path?.let {
            val idx = it.lastIndexOf('/')
            if (idx != -1) it.substring(idx + 1) else it
        }
    }
    return result
}

fun Uri.toPath(): String? {
    if (DocumentsContract.isDocumentUri(godContext, this)) {
        if (isExternalStorageDocument()) {
            val docId = DocumentsContract.getDocumentId(this)
            val split = docId.split(":").toTypedArray()
            val type = split[0]
            if ("primary".equals(type, ignoreCase = true)) {
                return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
            }
        } else if (isDownloadsDocument()) {
            val id = DocumentsContract.getDocumentId(this)
            val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id))
            return getDataColumn(contentUri, null, null)
        } else if (isMediaDocument()) {
            val docId = DocumentsContract.getDocumentId(this)
            val split = docId.split(":").toTypedArray()
            val type = split[0]
            var contentUri: Uri? = null
            when (type) {
                "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else -> contentUri = MediaStore.Files.getContentUri("external")
            }
            val selection = "_id=?"
            val selectionArgs = arrayOf(split[1])
            return getDataColumn(contentUri, selection, selectionArgs)
        }
    } else if ("content".equals(scheme, ignoreCase = true)) {
        return getDataColumn(this, null, null)
    } else if ("file".equals(scheme, ignoreCase = true)) {
        return path
    }
    return null
}

private fun getDataColumn(uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
    var cursor: Cursor? = null
    val column = "_data"
    val projection = arrayOf(column)
    try {
        cursor = uri?.let { godContext.contentResolver.query(it, projection, selection, selectionArgs, null) }
        if (cursor != null && cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndexOrThrow(column)
            return cursor.getString(columnIndex)
        }
    } finally {
        cursor?.close()
    }
    return null
}

private fun Uri.isExternalStorageDocument(): Boolean {
    return "com.android.externalstorage.documents" == authority
}

private fun Uri.isDownloadsDocument(): Boolean {
    return "com.android.providers.downloads.documents" == authority
}

private fun Uri.isMediaDocument(): Boolean {
    return "com.android.providers.media.documents" == authority
}

