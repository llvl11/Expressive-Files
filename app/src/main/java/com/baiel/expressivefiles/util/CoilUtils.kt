@file:Suppress("GrazieInspection")

package com.baiel.expressivefiles.util

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.Decoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.memory.MemoryCache
import coil.request.Options
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("GrazieInspection")
class ApkIconFetcher(
    private val data: File,
    private val context: Context,
    private val requestedSize: coil.size.Size
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val cacheDir = context.cacheDir.resolve("apk_icons")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        // Size bucket in the key: the PNG cached below is downscaled to THIS
        // requester's dimensions, so without the bucket the first (small) view
        // would serve its bitmap to every later larger view forever.
        val reqW = (requestedSize.width as? coil.size.Dimension.Pixels)?.px ?: -1
        val reqH = (requestedSize.height as? coil.size.Dimension.Pixels)?.px ?: -1
        val key = apkIconCacheKey(data.absolutePath, data.lastModified(), reqW, reqH)
        val cacheFile = cacheDir.resolve("$key.png")

        if (cacheFile.exists()) {
            val cached = android.graphics.drawable.Drawable.createFromPath(cacheFile.absolutePath)
            if (cached != null) {
                return DrawableResult(
                    drawable = cached,
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            }
            // Corrupt cache entry: drop it and fall through to a fresh decode.
            cacheFile.delete()
        }

        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val archiveInfo = packageManager.getPackageArchiveInfo(data.path, 0) ?: return null
        archiveInfo.applicationInfo?.apply {
            sourceDir = data.path
            publicSourceDir = data.path
        }
        val icon = archiveInfo.applicationInfo?.loadIcon(packageManager) ?: return null

        // Downscale to the request size BEFORE caching: adaptive icons decode at up
        // to 432px, while list/grid thumbnails need 96-230px. Caching full-size PNGs
        // wastes native memory on every disk-cache hit as well.
        val source = icon.toBitmap()
        val scaled = scaleDownToRequest(source)

        // Save to persistent cache to avoid re-parsing manifest next time
        try {
            withContext(Dispatchers.IO) {
                cacheFile.outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return DrawableResult(
            drawable = scaled.toDrawable(context.resources),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    private fun scaleDownToRequest(source: Bitmap): Bitmap {
        val reqW = (requestedSize.width as? coil.size.Dimension.Pixels)?.px ?: -1
        val reqH = (requestedSize.height as? coil.size.Dimension.Pixels)?.px ?: -1
        if ((reqW <= 0 && reqH <= 0) ||
            (reqW >= source.width && reqH >= source.height)
        ) return source

        val scale = if (reqW > 0 && reqH > 0) {
            minOf(reqW.toFloat() / source.width, reqH.toFloat() / source.height)
        } else if (reqW > 0) reqW.toFloat() / source.width else reqH.toFloat() / source.height

        val targetW = (source.width * scale).toInt().coerceAtLeast(1)
        val targetH = (source.height * scale).toInt().coerceAtLeast(1)
        return source.scale(targetW, targetH)
    }

    class Factory(private val context: Context) : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.extension.lowercase() == "apk") {
                return ApkIconFetcher(data, context, options.size)
            }
            return null
        }
    }
}

/**
 * Disk-cache key for an APK icon that was downscaled to the request size.
 * Path + mtime identify the icon's content; reqWidth/reqHeight identify the
 * baked-in dimensions (-1 = original/undefined), because one APK renders in
 * list rows, grids and expressive cards at visibly different sizes.
 */
internal fun apkIconCacheKey(
    path: String,
    lastModified: Long,
    reqWidth: Int,
    reqHeight: Int
): String {
    val digest = MessageDigest.getInstance("MD5")
        .digest("$path:$lastModified:$reqWidth x$reqHeight".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

/**
 * Request-scoped pass-through decoder factory. Attached per-request to video
 * loads, it always returns a VideoFrameDecoder so container formats that
 * MimeTypeMap can't resolve (mkv, avi, ...) still decode instead of silently
 * falling back to icons.
 */
object DirectVideoDecoderFactory : Decoder.Factory {
    override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder =
        VideoFrameDecoder(result.source, options)

    override fun equals(other: Any?) = other === this
    override fun hashCode() = javaClass.hashCode()
}

fun createSharedImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            add(VideoFrameDecoder.Factory())
            add(ApkIconFetcher.Factory(context))
        }
        .memoryCache {
            MemoryCache.Builder(context)
                // Thumbnails are tiny and size-capped, so ~8% of the heap holds
                // hundreds of them. Oversizing the cache hoards memory without
                // extra scroll smoothness.
                .maxSizePercent(0.08)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizePercent(0.05)
                .build()
        }
        // Hardware bitmaps keep decoded pixels OUT of the Java heap (GPU-owned),
        // which is the single biggest lever against GC churn from scrolling.
        .allowHardware(true)
        .build()
}
