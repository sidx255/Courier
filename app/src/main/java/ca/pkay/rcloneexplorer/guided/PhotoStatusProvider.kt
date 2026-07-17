package ca.pkay.rcloneexplorer.guided

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.util.AppMode
import java.io.File
import java.util.Locale

class PhotoStatusProvider(context: Context) {

    enum class State {
        PROTECTED,
        WAITING
    }

    data class MediaStatus(
        val uri: Uri,
        val name: String,
        val relativePath: String,
        val size: Long,
        val state: State
    )

    sealed class Result {
        data class Success(val items: List<MediaStatus>) : Result()
        data object Unavailable : Result()
    }

    private val appContext = context.applicationContext

    fun load(limit: Int = 24): Result {
        val task = photoTask() ?: return Result.Success(emptyList())
        val localItems = queryRecentMedia(limit)
        if (localItems.isEmpty()) return Result.Success(emptyList())
        val existing = Rclone(appContext).getPathSizes(
            RemoteItem(task.remoteId, "smb"),
            task.remotePath,
            localItems.map(LocalMedia::relativePath)
        ) ?: return Result.Unavailable
        return Result.Success(localItems.map { item ->
            MediaStatus(
                item.uri,
                item.name,
                item.relativePath,
                item.size,
                if (existing[item.relativePath.lowercase(Locale.ROOT)] == item.size) {
                    State.PROTECTED
                } else {
                    State.WAITING
                }
            )
        })
    }

    private fun photoTask() = AppMode.getGuidedTaskCategories(appContext)
        .entries
        .firstOrNull { it.value == GuidedBackupManager.CATEGORY_PHOTOS }
        ?.key
        ?.let { ca.pkay.rcloneexplorer.Database.DatabaseHandler(appContext).getTask(it) }

    private fun queryRecentMedia(limit: Int): List<LocalMedia> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            projection += MediaStore.MediaColumns.DATA
        }
        val pathColumnName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }
        val pathPrefixes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("DCIM/%", "Pictures/%", "Movies/%")
        } else {
            val root = Environment.getExternalStorageDirectory().absolutePath + File.separator
            arrayOf("${root}DCIM/%", "${root}Pictures/%", "${root}Movies/%")
        }
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?) AND " +
                "($pathColumnName LIKE ? OR $pathColumnName LIKE ? OR $pathColumnName LIKE ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            *pathPrefixes
        )
        val results = mutableListOf<LocalMedia>()
        appContext.contentResolver.query(
            collection,
            projection.toTypedArray(),
            selection,
            args,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            }
            while (cursor.moveToNext() && results.size < limit) {
                val name = cursor.getString(nameColumn) ?: continue
                val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getString(pathColumn).orEmpty() + name
                } else {
                    val absolute = cursor.getString(pathColumn) ?: continue
                    absolute.removePrefix(Environment.getExternalStorageDirectory().absolutePath + File.separator)
                }.trimStart('/')
                if (!isProtectedMediaPath(relativePath)) continue
                val mediaType = cursor.getInt(typeColumn)
                val base = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                results += LocalMedia(
                    ContentUris.withAppendedId(base, cursor.getLong(idColumn)),
                    name,
                    relativePath,
                    cursor.getLong(sizeColumn)
                )
            }
        }
        return results
    }

    private fun isProtectedMediaPath(path: String): Boolean {
        return path.startsWith("DCIM/") || path.startsWith("Pictures/") || path.startsWith("Movies/")
    }

    private data class LocalMedia(
        val uri: Uri,
        val name: String,
        val relativePath: String,
        val size: Long
    )
}