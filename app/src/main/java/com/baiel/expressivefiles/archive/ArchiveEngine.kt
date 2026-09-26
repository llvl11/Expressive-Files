package com.baiel.expressivefiles.archive

import com.baiel.expressivefiles.model.ArchiveEntryItem
import com.baiel.expressivefiles.model.ArchiveProgress
import com.baiel.expressivefiles.model.ArchiveType
import com.github.junrar.Archive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ArchiveEngine {

    // Large sequential chunks dominate archive throughput; 64 KiB halves the
    // syscall count of the previous 8 KiB loop with no memory cost.
    private const val BUFFER_SIZE = 64 * 1024

    // Streaming cancellation granularity: one cooperative-cancel probe per
    // ~8 MiB (at 64 KiB buffer) so a single huge entry can no longer pin the
    // Cancel button for tens of seconds.
    private const val CHECKPOINT_CHUNKS = 128

    /** Progress emitter shared by all long operations. */
    private class ProgressGate(private val onProgress: (ArchiveProgress) -> Unit) {
        fun emit(progress: () -> ArchiveProgress) {
            onProgress(progress())
        }
    }

    /**
     * Cooperative cancellation checkpoint called between entries of every
     * long operation. Throws CancellationException so the coroutine stops
     * without burning CPU on work nobody observes anymore.
     */
    private fun checkAlive(job: Job?) {
        if (job != null && !job.isActive) throw CancellationException("Archive job cancelled")
    }

    /**
     * InputStream filter injecting the same checkpoints INTO long stream
     * copies. Without it a single large entry could only be interrupted at an
     * entry boundary - the observed 10-30 s delay of the Cancel button.
     */
    private class CancellableStream(
        src: java.io.InputStream,
        private val job: Job?,
        private val chunksPerCheck: Int = CHECKPOINT_CHUNKS
    ) : java.io.FilterInputStream(src) {
        private var chunks = 0

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0 && ++chunks >= chunksPerCheck) {
                chunks = 0
                checkAlive(job)
            }
            return n
        }
    }

    /** Copies [input] to [output] through a checkpointing filter. */
    private fun copyWithCheckpoints(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        job: Job?
    ) {
        CancellableStream(input, job).use { guarded ->
            guarded.copyTo(output, BUFFER_SIZE)
        }
    }

    /**
     * List all entries in an archive without extracting it.
     */
    suspend fun listArchiveEntries(archiveFile: File): List<ArchiveEntryItem> = withContext(Dispatchers.IO) {
        val archiveType = ArchiveType.fromFileName(archiveFile.name)
        val entries = mutableListOf<ArchiveEntryItem>()

        try {
            when (archiveType) {
                ArchiveType.SEVEN_Z -> SevenZFile.builder().setFile(archiveFile).get().use { sevenZ ->
                    var entry: SevenZArchiveEntry? = sevenZ.nextEntry
                    while (entry != null) {
                        entries.add(
                            ArchiveEntryItem(
                                path = entry.name,
                                name = entry.name.trimEnd('/').substringAfterLast('/'),
                                isDirectory = entry.isDirectory,
                                size = entry.size,
                                // -1 = unknown: commons-compress 1.21 keeps
                                // SevenZArchiveEntry.compressedSize package-
                                // private, and reporting size here made every
                                // row read "12 MB (12 MB compressed)".
                                compressedSize = -1L,
                                lastModified = entry.lastModifiedDate?.time ?: 0L,
                                crc = entry.crcValue
                            )
                        )
                        entry = sevenZ.nextEntry
                    }
                }
                ArchiveType.TAR, ArchiveType.TAR_GZ -> createTarInputStream(archiveFile).use { tarIn ->
                    var entry = tarIn.nextEntry
                    while (entry != null) {
                        entries.add(
                            ArchiveEntryItem(
                                path = entry.name,
                                name = entry.name.trimEnd('/').substringAfterLast('/'),
                                isDirectory = entry.isDirectory,
                                size = entry.size,
                                // TAR stores no per-entry compressed size:
                                // -1 = unknown, the viewer then prints the plain
                                // size instead of "(x compressed)".
                                compressedSize = -1L,
                                lastModified = entry.lastModifiedDate?.time ?: 0L
                            )
                        )
                        entry = tarIn.nextEntry
                    }
                }
                ArchiveType.RAR -> Archive(archiveFile).use { rar ->
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
                                // RAR's field is an unsigned 32-bit CRC carried
                                // in an int: plain toLong() sign-extends high
                                // bit values negative.
                                crc = header.fileCRC.toLong() and 0xFFFFFFFFL
                            )
                        )
                    }
                }
                else -> listZipEntries(archiveFile, entries)
            }
        } catch (e: Exception) {
            // Propagate instead of returning a partial/empty list as success:
            // ArchiveViewerSheet wraps this call in try/catch and maps a throw
            // to its error state. Swallowing here showed a corrupt, truncated
            // or password-protected archive as a plausible "0 entries" listing.
            e.printStackTrace()
            throw e
        }

        entries
    }

    private fun listZipEntries(archiveFile: File, out: MutableList<ArchiveEntryItem>) {
        ZipFile(archiveFile).use { zip ->
            val enumEntries = zip.entries()
            while (enumEntries.hasMoreElements()) {
                val entry = enumEntries.nextElement()
                out.add(
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

    /**
     * Extract archive to destination directory.
     *
     * ZIP gets exact totals from its central directory for free.
     * Sequential formats (7z/TAR/RAR) cannot be counted without consuming the
     * stream, so instead of re-reading the entire archive they report an
     * indeterminate running count - halving total I/O for those formats.
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
        val gate = ProgressGate(onProgress)
        val job = kotlinx.coroutines.currentCoroutineContext()[Job]
        var processedCount = 0
        var skippedCount = 0
        val onSkippedExisting: () -> Unit = { skippedCount++ }

        try {
            when (archiveType) {
                ArchiveType.ZIP -> {
                    // The central directory knows the entry count up front, so
                    // report it: with totalFiles = -1 (old behavior) the popup
                    // and notification stayed indeterminate and never showed
                    // "n / total" for the most common format, contradicting the
                    // KDoc above.
                    var zipTotal = -1
                    extractZip(archiveFile, targetDir, job,
                        onTotal = { zipTotal = it },
                        onSkippedExisting = onSkippedExisting,
                        onEntryProcessed = { name ->
                            processedCount++
                            gate.emit {
                                ArchiveProgress(
                                    operation = "Extracting ZIP",
                                    currentFileName = name,
                                    filesProcessed = processedCount,
                                    totalFiles = zipTotal,
                                    targetFile = targetDir
                                )
                            }
                        }
                    )
                }
                else -> {
                    val op = when (archiveType) {
                        ArchiveType.SEVEN_Z -> "Extracting 7Z"
                        ArchiveType.TAR, ArchiveType.TAR_GZ -> "Extracting TAR"
                        ArchiveType.RAR -> "Extracting RAR"
                        else -> "Extracting Archive"
                    }
                    gate.emit {
                        ArchiveProgress(operation = op, isIndeterminate = true, targetFile = targetDir)
                    }
                    extractSequential(archiveFile, targetDir, archiveType, job,
                        onSkippedExisting = onSkippedExisting) { name ->
                        processedCount++
                        gate.emit {
                            ArchiveProgress(
                                operation = op,
                                currentFileName = name,
                                filesProcessed = processedCount,
                                isIndeterminate = true,
                                targetFile = targetDir
                            )
                        }
                    }
                }
            }

            gate.emit {
                ArchiveProgress(
                    operation = "Extraction Complete",
                    filesProcessed = processedCount,
                    totalFiles = processedCount.coerceAtLeast(1),
                    isComplete = true,
                    targetFile = targetDir,
                    skippedExisting = skippedCount
                )
            }
            true
        } catch (e: Exception) {
            // Cancellation is control flow, never an operation failure.
            if (e is CancellationException) throw e
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

    private fun extractZip(
        archiveFile: File,
        targetDir: File,
        job: Job?,
        onTotal: (Int) -> Unit,
        onSkippedExisting: () -> Unit,
        onEntryProcessed: (String) -> Unit
    ) {
        // Single open: the central directory is parsed once and provides every
        // entry's attributes; no separate counting pass is needed.
        ZipFile(archiveFile).use { zip ->
            onTotal(zip.size())
            val enumEntries = zip.entries()
            while (enumEntries.hasMoreElements()) {
                checkAlive(job)
                val entry = enumEntries.nextElement()
                val outFile = safeFileResolve(targetDir, entry.name) ?: continue

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else if (outFile.exists()) {
                    // Never truncate what is already there: without the
                    // auto-subfolder guard the target is the archive's own
                    // folder, and a silent overwrite would destroy user data.
                    onSkippedExisting()
                } else {
                    writeStreamToFile({ zip.getInputStream(entry) }, outFile, job)
                }
                onEntryProcessed(outFile.name)
            }
        }
    }

    /** Chunked, buffered write used by every extraction branch. */
    private inline fun writeStreamToFile(
        openInput: () -> java.io.InputStream,
        outFile: File,
        job: Job? = null
    ) {
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { rawOut ->
            BufferedOutputStream(rawOut, BUFFER_SIZE).use { output ->
                CancellableStream(openInput(), job).use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var len: Int
                    while (input.read(buffer).also { len = it } > 0) {
                        output.write(buffer, 0, len)
                    }
                }
            }
        }
    }

    private fun extractSequential(
        archiveFile: File,
        targetDir: File,
        archiveType: ArchiveType,
        job: Job?,
        onSkippedExisting: () -> Unit,
        onEntryProcessed: (String) -> Unit
    ) {
        when (archiveType) {
            ArchiveType.SEVEN_Z -> SevenZFile.builder().setFile(archiveFile).get().use { sevenZ ->
                var entry: SevenZArchiveEntry? = sevenZ.nextEntry
                val buffer = ByteArray(BUFFER_SIZE)

                while (entry != null) {
                    checkAlive(job)
                    val outFile = safeFileResolve(targetDir, entry.name)
                    if (outFile != null) {
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else if (outFile.exists()) {
                            // Same never-truncate rule as the ZIP branch; the
                            // entry stream is consumed on the next read anyway,
                            // so skipping costs no extra I/O.
                            onSkippedExisting()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { rawOut ->
                                BufferedOutputStream(rawOut, BUFFER_SIZE).use { out ->
                                    var len: Int
                                    var sinceCheckpoint = 0
                                    while (sevenZ.read(buffer).also { len = it } > 0) {
                                        out.write(buffer, 0, len)
                                        if (++sinceCheckpoint >= CHECKPOINT_CHUNKS) {
                                            sinceCheckpoint = 0
                                            checkAlive(job)
                                        }
                                    }
                                }
                            }
                        }
                        onEntryProcessed(outFile.name)
                    }
                    entry = sevenZ.nextEntry
                }
            }
            ArchiveType.RAR -> Archive(archiveFile).use { rar ->
                for (header in rar.fileHeaders) {
                    checkAlive(job)
                    val cleanName = header.fileName.replace('\\', '/')
                    val outFile = safeFileResolve(targetDir, cleanName) ?: continue

                    if (header.isDirectory) {
                        outFile.mkdirs()
                    } else if (outFile.exists()) {
                        onSkippedExisting()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                                rar.extractFile(header, bos)
                            }
                        }
                    }
                    onEntryProcessed(outFile.name)
                }
            }
            else -> createTarInputStream(archiveFile).use { tarIn ->
                var entry = tarIn.nextEntry
                val buffer = ByteArray(BUFFER_SIZE)

                while (entry != null) {
                    checkAlive(job)
                    val outFile = safeFileResolve(targetDir, entry.name)
                    if (outFile != null) {
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else if (outFile.exists()) {
                            onSkippedExisting()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { rawOut ->
                                BufferedOutputStream(rawOut, BUFFER_SIZE).use { out ->
                                    var len: Int
                                    var sinceCheckpoint = 0
                                    while (tarIn.read(buffer).also { len = it } > 0) {
                                        out.write(buffer, 0, len)
                                        if (++sinceCheckpoint >= CHECKPOINT_CHUNKS) {
                                            sinceCheckpoint = 0
                                            checkAlive(job)
                                        }
                                    }
                                }
                            }
                        }
                        onEntryProcessed(outFile.name)
                    }
                    entry = tarIn.nextEntry
                }
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
        // Refuse to touch a pre-existing archive: every writer below opens the
        // destination with FileOutputStream/SevenZOutputFile, which TRUNCATES
        // it to zero bytes, and the failure cleanup at the bottom of this
        // function deletes whatever is at that path - the user's original data
        // would be gone even though the operation reported failure/cancel.
        if (destinationArchive.exists()) return@withContext false

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

        // The destination can be part of the selection (user picks backup.zip
        // and names the new archive "backup"). Never collect it: it would be
        // opened for reading while the same path is being written.
        val destinationCanonical = try {
            destinationArchive.canonicalFile
        } catch (_: Exception) {
            destinationArchive.absoluteFile
        }
        for (src in sourceFiles) {
            val srcCanonical = try {
                src.canonicalFile
            } catch (_: Exception) {
                src.absoluteFile
            }
            if (srcCanonical == destinationCanonical) continue
            collectFiles(src, "")
        }
        if (allFiles.isEmpty()) return@withContext false

        // Entry names are only the bare file name for every top-level source,
        // so selecting the same name from two folders (DCIM/x.jpg + Pictures/
        // x.jpg) produced two "x.jpg" entries: ZipOutputStream throws
        // "duplicate entry" and aborts the whole compression, while TAR/7z
        // accept both and extraction silently keeps only the last. Qualify
        // collisions with the file's own folder, then with a counter.
        val usedNames = HashSet<String>()
        for (i in allFiles.indices) {
            val (file, relPath) = allFiles[i]
            if (usedNames.add(relPath)) continue
            val parentName = file.parentFile?.name.orEmpty()
            var candidate = if (parentName.isNotEmpty()) "$parentName/$relPath" else relPath
            var attempt = 2
            while (!usedNames.add(candidate)) {
                candidate = if (parentName.isNotEmpty()) {
                    "$parentName ($attempt)/$relPath"
                } else {
                    "$attempt/$relPath"
                }
                attempt++
            }
            allFiles[i] = file to candidate
        }

        val totalCount = allFiles.size.coerceAtLeast(1)
        var processed = 0
        val gate = ProgressGate(onProgress)
        val job = kotlinx.coroutines.currentCoroutineContext()[Job]

        destinationArchive.parentFile?.mkdirs()

        try {
            when (format) {
                ArchiveType.SEVEN_Z -> SevenZOutputFile(destinationArchive).use { sevenZOutput ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    for ((file, relPath) in allFiles) {
                        checkAlive(job)
                        val entry = sevenZOutput.createArchiveEntry(file, relPath)
                        sevenZOutput.putArchiveEntry(entry)
                        if (!file.isDirectory) {
                            FileInputStream(file).use { fis ->
                                BufferedInputStream(fis, BUFFER_SIZE).use { input ->
                                    // SevenZOutputFile is not a JDK OutputStream,
                                    // so checkpoint manually around its reads.
                                    var len: Int
                                    var sinceCheckpoint = 0
                                    while (input.read(buffer).also { len = it } > 0) {
                                        sevenZOutput.write(buffer, 0, len)
                                        if (++sinceCheckpoint >= CHECKPOINT_CHUNKS) {
                                            sinceCheckpoint = 0
                                            checkAlive(job)
                                        }
                                    }
                                }
                            }
                        }
                        sevenZOutput.closeArchiveEntry()
                        processed++
                        emitCreateProgress(gate, "Creating 7Z", file.name, processed, totalCount, destinationArchive)
                    }
                }
                // Plain TAR and gzip-compressed TAR share the identical entry
                // writing loop - only the underlying stream differs.
                ArchiveType.TAR, ArchiveType.TAR_GZ -> {
                    val stream = if (format == ArchiveType.TAR_GZ) {
                        GzipCompressorOutputStream(
                            BufferedOutputStream(FileOutputStream(destinationArchive), BUFFER_SIZE)
                        )
                    } else {
                        BufferedOutputStream(FileOutputStream(destinationArchive), BUFFER_SIZE)
                    }
                    val label = if (format == ArchiveType.TAR_GZ) "Creating TAR.GZ" else "Creating TAR"
                    TarArchiveOutputStream(stream).use { tarOut ->
                        tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                        for ((file, relPath) in allFiles) {
                            checkAlive(job)
                            val entry = TarArchiveEntry(file, relPath)
                            tarOut.putArchiveEntry(entry)
                            if (!file.isDirectory) {
                                FileInputStream(file).use { fis ->
                                    copyWithCheckpoints(fis, tarOut, job)
                                }
                            }
                            tarOut.closeArchiveEntry()
                            processed++
                            emitCreateProgress(gate, label, file.name, processed, totalCount, destinationArchive)
                        }
                    }
                }
                else -> ZipOutputStream(
                    BufferedOutputStream(FileOutputStream(destinationArchive), BUFFER_SIZE)
                ).use { zos ->
                    for ((file, relPath) in allFiles) {
                        checkAlive(job)
                        val entry = ZipEntry(relPath)
                        entry.time = file.lastModified()
                        zos.putNextEntry(entry)
                        if (!file.isDirectory) {
                            FileInputStream(file).use { fis ->
                                copyWithCheckpoints(fis, zos, job)
                            }
                        }
                        zos.closeEntry()
                        processed++
                        emitCreateProgress(gate, "Creating ZIP", file.name, processed, totalCount, destinationArchive)
                    }
                }
            }

            gate.emit {
                ArchiveProgress(
                    operation = "Compression Complete",
                    filesProcessed = totalCount,
                    totalFiles = totalCount,
                    isComplete = true,
                    targetFile = destinationArchive
                )
            }
            true
        } catch (e: Exception) {
            // A partially written archive is unreadable garbage; removing it
            // prevents ghost entries that fail later with confusing errors.
            // This cleanup MUST run before rethrowing CancellationException,
            // otherwise a canceled compression leaves a corrupt file behind.
            // Deleting is safe only because the exists() guard at the top
            // guarantees this run - and nobody else - created the destination.
            runCatching { if (destinationArchive.exists()) destinationArchive.delete() }
            // Cancellation is control flow, never an operation failure.
            if (e is CancellationException) throw e
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

    private fun emitCreateProgress(
        gate: ProgressGate,
        operation: String,
        fileName: String,
        processed: Int,
        total: Int,
        target: File
    ) {
        gate.emit {
            ArchiveProgress(
                operation = operation,
                currentFileName = fileName,
                filesProcessed = processed,
                totalFiles = total,
                targetFile = target
            )
        }
    }

    private fun createTarInputStream(archiveFile: File): TarArchiveInputStream {
        val rawInput: java.io.InputStream = BufferedInputStream(FileInputStream(archiveFile), BUFFER_SIZE)
        val name = archiveFile.name.lowercase()
        return try {
            val stream = when {
                name.endsWith(".tar.gz") || name.endsWith(".tgz") -> GzipCompressorInputStream(rawInput)
                name.endsWith(".tar.bz2") || name.endsWith(".tbz2") -> BZip2CompressorInputStream(rawInput)
                name.endsWith(".tar.xz") || name.endsWith(".txz") -> XZCompressorInputStream(rawInput)
                else -> rawInput
            }
            TarArchiveInputStream(stream)
        } catch (t: Throwable) {
            // The decompressor constructors read and validate the stream header
            // and throw on a corrupt archive. The FileInputStream opened above
            // would otherwise leak one descriptor per failed attempt.
            runCatching { rawInput.close() }
            throw t
        }
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

