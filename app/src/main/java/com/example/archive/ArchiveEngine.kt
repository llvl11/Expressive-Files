package com.example.archive

import com.example.model.ArchiveEntryItem
import com.example.model.ArchiveProgress
import com.example.model.ArchiveType
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ArchiveEngine {

    /**
     * List all entries in an archive without extracting it.
     */
    suspend fun listArchiveEntries(archiveFile: File): List<ArchiveEntryItem> = withContext(Dispatchers.IO) {
        val archiveType = ArchiveType.fromFileName(archiveFile.name)
        val entries = mutableListOf<ArchiveEntryItem>()

        try {
            when (archiveType) {
                ArchiveType.ZIP -> {
                    ZipFile(archiveFile).use { zip ->
                        val enumEntries = zip.entries()
                        while (enumEntries.hasMoreElements()) {
                            val entry = enumEntries.nextElement()
                            entries.add(
                                ArchiveEntryItem(
                                    path = entry.name,
                                    name = entry.name.trimEnd('/').substringAfterLast('/'),
                                    isDirectory = entry.isDirectory,
                                    size = entry.size.coerceAtLeast(0L),
                                    compressedSize = entry.compressedSize.coerceAtLeast(0L),
                                    lastModified = entry.time.coerceAtLeast(0L),
                                    crc = entry.crc
                                )
                            )
                        }
                    }
                }
                ArchiveType.SEVEN_Z -> {
                    SevenZFile(archiveFile).use { sevenZ ->
                        var entry: SevenZArchiveEntry? = sevenZ.nextEntry
                        while (entry != null) {
                            entries.add(
                                ArchiveEntryItem(
                                    path = entry.name,
                                    name = entry.name.trimEnd('/').substringAfterLast('/'),
                                    isDirectory = entry.isDirectory,
                                    size = entry.size,
                                    compressedSize = entry.size,
                                    lastModified = entry.lastModifiedDate?.time ?: 0L,
                                    crc = entry.crcValue
                                )
                            )
                            entry = sevenZ.nextEntry
                        }
                    }
                }
                ArchiveType.TAR, ArchiveType.TAR_GZ -> {
                    createTarInputStream(archiveFile).use { tarIn ->
                        var entry: TarArchiveEntry? = tarIn.nextTarEntry
                        while (entry != null) {
                            entries.add(
                                ArchiveEntryItem(
                                    path = entry.name,
                                    name = entry.name.trimEnd('/').substringAfterLast('/'),
                                    isDirectory = entry.isDirectory,
                                    size = entry.size,
                                    compressedSize = entry.size,
                                    lastModified = entry.modTime?.time ?: 0L
                                )
                            )
                            entry = tarIn.nextTarEntry
                        }
                    }
                }
                ArchiveType.RAR -> {
                    Archive(archiveFile).use { rar ->
                        for (header in rar.fileHeaders) {
                            val name = header.fileName.replace('\\', '/')
                            entries.add(
                                ArchiveEntryItem(
                                    path = name,
                                    name = name.trimEnd('/').substringAfterLast('/'),
                                    isDirectory = header.isDirectory,
                                    size = header.unpSize,
                                    compressedSize = header.packSize,
                                    lastModified = header.mTime?.time ?: 0L,
                                    crc = header.fileCRC.toLong()
                                )
                            )
                        }
                    }
                }
                else -> {
                    // Fallback to zip attempt
                    try {
                        ZipFile(archiveFile).use { zip ->
                            val enumEntries = zip.entries()
                            while (enumEntries.hasMoreElements()) {
                                val entry = enumEntries.nextElement()
                                entries.add(
                                    ArchiveEntryItem(
                                        path = entry.name,
                                        name = entry.name.trimEnd('/').substringAfterLast('/'),
                                        isDirectory = entry.isDirectory,
                                        size = entry.size.coerceAtLeast(0L),
                                        compressedSize = entry.compressedSize.coerceAtLeast(0L),
                                        lastModified = entry.time.coerceAtLeast(0L),
                                        crc = entry.crc
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // Empty on failure
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        entries
    }

    /**
     * Extract archive to destination directory.
     */
    suspend fun extractArchive(
        archiveFile: File,
        targetDir: File,
        onProgress: (ArchiveProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val archiveType = ArchiveType.fromFileName(archiveFile.name)
        var totalFiles = 0
        var processedCount = 0

        try {
            // Count total entries first
            val entries = listArchiveEntries(archiveFile)
            totalFiles = entries.size.coerceAtLeast(1)

            onProgress(
                ArchiveProgress(
                    operation = "Extracting ${archiveFile.name}",
                    totalFiles = totalFiles,
                    isIndeterminate = totalFiles <= 1
                )
            )

            when (archiveType) {
                ArchiveType.ZIP -> extractZip(archiveFile, targetDir) { name ->
                    processedCount++
                    onProgress(
                        ArchiveProgress(
                            operation = "Extracting ZIP",
                            currentFileName = name,
                            filesProcessed = processedCount,
                            totalFiles = totalFiles,
                            targetFile = targetDir
                        )
                    )
                }
                ArchiveType.SEVEN_Z -> extract7z(archiveFile, targetDir) { name ->
                    processedCount++
                    onProgress(
                        ArchiveProgress(
                            operation = "Extracting 7Z",
                            currentFileName = name,
                            filesProcessed = processedCount,
                            totalFiles = totalFiles,
                            targetFile = targetDir
                        )
                    )
                }
                ArchiveType.TAR, ArchiveType.TAR_GZ -> extractTar(archiveFile, targetDir) { name ->
                    processedCount++
                    onProgress(
                        ArchiveProgress(
                            operation = "Extracting TAR",
                            currentFileName = name,
                            filesProcessed = processedCount,
                            totalFiles = totalFiles,
                            targetFile = targetDir
                        )
                    )
                }
                ArchiveType.RAR -> extractRar(archiveFile, targetDir) { name ->
                    processedCount++
                    onProgress(
                        ArchiveProgress(
                            operation = "Extracting RAR",
                            currentFileName = name,
                            filesProcessed = processedCount,
                            totalFiles = totalFiles,
                            targetFile = targetDir
                        )
                    )
                }
                else -> {
                    // Try zip extraction as fallback
                    extractZip(archiveFile, targetDir) { name ->
                        processedCount++
                        onProgress(
                            ArchiveProgress(
                                operation = "Extracting Archive",
                                currentFileName = name,
                                filesProcessed = processedCount,
                                totalFiles = totalFiles,
                                targetFile = targetDir
                            )
                        )
                    }
                }
            }

            onProgress(
                ArchiveProgress(
                    operation = "Extraction Complete",
                    filesProcessed = totalFiles,
                    totalFiles = totalFiles,
                    isComplete = true,
                    targetFile = targetDir
                )
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress(
                ArchiveProgress(
                    operation = "Extraction Failed",
                    isComplete = true,
                    error = e.localizedMessage ?: "Unknown error while extracting"
                )
            )
            false
        }
    }

    private fun extractZip(archiveFile: File, targetDir: File, onEntryProcessed: (String) -> Unit) {
        ZipFile(archiveFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outFile = safeFileResolve(targetDir, entry.name) ?: continue

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                onEntryProcessed(outFile.name)
            }
        }
    }

    private fun extract7z(archiveFile: File, targetDir: File, onEntryProcessed: (String) -> Unit) {
        SevenZFile(archiveFile).use { sevenZ ->
            var entry: SevenZArchiveEntry? = sevenZ.nextEntry
            val buffer = ByteArray(8192)

            while (entry != null) {
                val outFile = safeFileResolve(targetDir, entry.name)
                if (outFile != null) {
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            var len: Int
                            while (sevenZ.read(buffer).also { len = it } > 0) {
                                out.write(buffer, 0, len)
                            }
                        }
                    }
                    onEntryProcessed(outFile.name)
                }
                entry = sevenZ.nextEntry
            }
        }
    }

    private fun extractTar(archiveFile: File, targetDir: File, onEntryProcessed: (String) -> Unit) {
        createTarInputStream(archiveFile).use { tarIn ->
            val buffer = ByteArray(8192)
            var entry: TarArchiveEntry? = tarIn.nextTarEntry

            while (entry != null) {
                val outFile = safeFileResolve(targetDir, entry.name)
                if (outFile != null) {
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            var len: Int
                            while (tarIn.read(buffer).also { len = it } > 0) {
                                out.write(buffer, 0, len)
                            }
                        }
                    }
                    onEntryProcessed(outFile.name)
                }
                entry = tarIn.nextTarEntry
            }
        }
    }

    private fun extractRar(archiveFile: File, targetDir: File, onEntryProcessed: (String) -> Unit) {
        Archive(archiveFile).use { rar ->
            for (header in rar.fileHeaders) {
                val cleanName = header.fileName.replace('\\', '/')
                val outFile = safeFileResolve(targetDir, cleanName) ?: continue

                if (header.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        rar.extractFile(header, fos)
                    }
                }
                onEntryProcessed(outFile.name)
            }
        }
    }

    /**
     * Create archive from list of files/folders.
     */
    suspend fun createArchive(
        sourceFiles: List<File>,
        destinationArchive: File,
        format: ArchiveType,
        onProgress: (ArchiveProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val allFiles = mutableListOf<Pair<File, String>>() // File and relative path

        fun collectFiles(file: File, baseRelative: String) {
            val relPath = if (baseRelative.isEmpty()) file.name else "$baseRelative/${file.name}"
            if (file.isDirectory) {
                val children = file.listFiles() ?: emptyArray()
                if (children.isEmpty()) {
                    allFiles.add(Pair(file, "$relPath/"))
                } else {
                    for (child in children) {
                        collectFiles(child, relPath)
                    }
                }
            } else {
                allFiles.add(Pair(file, relPath))
            }
        }

        for (src in sourceFiles) {
            collectFiles(src, "")
        }

        val totalCount = allFiles.size.coerceAtLeast(1)
        var processed = 0

        destinationArchive.parentFile?.mkdirs()

        try {
            when (format) {
                ArchiveType.ZIP -> {
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(destinationArchive))).use { zos ->
                        for ((file, relPath) in allFiles) {
                            val entry = ZipEntry(relPath)
                            entry.time = file.lastModified()
                            zos.putNextEntry(entry)
                            if (!file.isDirectory) {
                                FileInputStream(file).use { fis ->
                                    fis.copyTo(zos)
                                }
                            }
                            zos.closeEntry()
                            processed++
                            onProgress(
                                ArchiveProgress(
                                    operation = "Creating ZIP",
                                    currentFileName = file.name,
                                    filesProcessed = processed,
                                    totalFiles = totalCount,
                                    targetFile = destinationArchive
                                )
                            )
                        }
                    }
                }
                ArchiveType.SEVEN_Z -> {
                    SevenZOutputFile(destinationArchive).use { sevenZOutput ->
                        for ((file, relPath) in allFiles) {
                            val entry = sevenZOutput.createArchiveEntry(file, relPath)
                            sevenZOutput.putArchiveEntry(entry)
                            if (!file.isDirectory) {
                                FileInputStream(file).use { fis ->
                                    val buffer = ByteArray(8192)
                                    var read: Int
                                    while (fis.read(buffer).also { read = it } > 0) {
                                        sevenZOutput.write(buffer, 0, read)
                                    }
                                }
                            }
                            sevenZOutput.closeArchiveEntry()
                            processed++
                            onProgress(
                                ArchiveProgress(
                                    operation = "Creating 7Z",
                                    currentFileName = file.name,
                                    filesProcessed = processed,
                                    totalFiles = totalCount,
                                    targetFile = destinationArchive
                                )
                            )
                        }
                    }
                }
                ArchiveType.TAR -> {
                    TarArchiveOutputStream(BufferedOutputStream(FileOutputStream(destinationArchive))).use { tarOut ->
                        tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                        for ((file, relPath) in allFiles) {
                            val entry = TarArchiveEntry(file, relPath)
                            tarOut.putArchiveEntry(entry)
                            if (!file.isDirectory) {
                                FileInputStream(file).use { fis ->
                                    fis.copyTo(tarOut)
                                }
                            }
                            tarOut.closeArchiveEntry()
                            processed++
                            onProgress(
                                ArchiveProgress(
                                    operation = "Creating TAR",
                                    currentFileName = file.name,
                                    filesProcessed = processed,
                                    totalFiles = totalCount,
                                    targetFile = destinationArchive
                                )
                            )
                        }
                    }
                }
                ArchiveType.TAR_GZ -> {
                    TarArchiveOutputStream(
                        GzipCompressorOutputStream(BufferedOutputStream(FileOutputStream(destinationArchive)))
                    ).use { tarOut ->
                        tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                        for ((file, relPath) in allFiles) {
                            val entry = TarArchiveEntry(file, relPath)
                            tarOut.putArchiveEntry(entry)
                            if (!file.isDirectory) {
                                FileInputStream(file).use { fis ->
                                    fis.copyTo(tarOut)
                                }
                            }
                            tarOut.closeArchiveEntry()
                            processed++
                            onProgress(
                                ArchiveProgress(
                                    operation = "Creating TAR.GZ",
                                    currentFileName = file.name,
                                    filesProcessed = processed,
                                    totalFiles = totalCount,
                                    targetFile = destinationArchive
                                )
                            )
                        }
                    }
                }
                else -> {
                    // Fallback to zip
                    return@withContext createArchive(sourceFiles, destinationArchive, ArchiveType.ZIP, onProgress)
                }
            }

            onProgress(
                ArchiveProgress(
                    operation = "Compression Complete",
                    filesProcessed = totalCount,
                    totalFiles = totalCount,
                    isComplete = true,
                    targetFile = destinationArchive
                )
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress(
                ArchiveProgress(
                    operation = "Compression Failed",
                    isComplete = true,
                    error = e.localizedMessage ?: "Unknown compression error"
                )
            )
            false
        }
    }

    private fun createTarInputStream(archiveFile: File): TarArchiveInputStream {
        val rawInput: InputStream = BufferedInputStream(FileInputStream(archiveFile))
        val name = archiveFile.name.lowercase()
        val stream = when {
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> GzipCompressorInputStream(rawInput)
            name.endsWith(".tar.bz2") || name.endsWith(".tbz2") -> BZip2CompressorInputStream(rawInput)
            name.endsWith(".tar.xz") || name.endsWith(".txz") -> XZCompressorInputStream(rawInput)
            else -> rawInput
        }
        return TarArchiveInputStream(stream)
    }

    /**
     * Prevents Zip Slip vulnerability by ensuring resolved file remains within targetDir.
     */
    private fun safeFileResolve(targetDir: File, entryPath: String): File? {
        val destFile = File(targetDir, entryPath)
        val canonicalDest = destFile.canonicalPath
        val canonicalTarget = targetDir.canonicalPath
        return if (canonicalDest.startsWith(canonicalTarget + File.separator) || canonicalDest == canonicalTarget) {
            destFile
        } else {
            null
        }
    }
}
