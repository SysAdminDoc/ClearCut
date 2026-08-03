package com.novacut.editor.ui.editor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ProjectArchive
import com.novacut.editor.engine.ProjectDocument
import com.novacut.editor.engine.db.ProjectDao
import com.novacut.editor.engine.sanitizeFileName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns archive/backup file I/O and the project-ID lookup needed by imports.
 *
 * It deliberately returns typed transfer results instead of UI copy. The
 * ViewModel remains responsible for applying an imported state and presenting
 * the report, while this coordinator owns temporary files, Downloads handoff,
 * archive destinations, and the Room snapshot used for collision detection.
 */
@Singleton
class EditorProjectTransferCoordinator internal constructor(
    private val backupTempDirectory: File,
    private val archiveDirectory: File,
    private val importRootDirectory: File,
    private val nowMs: () -> Long,
    private val estimateArchiveSize: suspend (AutoSaveState) -> Long,
    private val exportArchive: suspend (ProjectDocument, File) -> Boolean,
    private val copyToDownloads: suspend (File, String) -> String,
    private val existingProjectIds: suspend () -> Set<String>,
    private val importArchive: suspend (Uri, File, Set<String>) -> ProjectArchive.ImportResult,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        projectDao: ProjectDao,
    ) : this(
        backupTempDirectory = File(context.cacheDir, "backup_exports"),
        archiveDirectory = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "archives",
        ),
        importRootDirectory = context.filesDir,
        nowMs = System::currentTimeMillis,
        estimateArchiveSize = { state ->
            ProjectArchive.estimateArchiveSize(context, state)
        },
        exportArchive = { document, outputFile ->
            ProjectArchive.exportArchive(context, document, outputFile)
        },
        copyToDownloads = { sourceFile, fileName ->
            copyFileToDownloads(context, sourceFile, fileName)
        },
        existingProjectIds = {
            projectDao.getAllProjectsSnapshot().map { it.id }.toSet()
        },
        importArchive = { uri, targetDir, projectIds ->
            ProjectArchive.importArchiveWithReport(
                context = context,
                archiveUri = uri,
                targetDir = targetDir,
                existingProjectIds = projectIds,
            )
        },
    )

    suspend fun estimateBackupSize(state: AutoSaveState): Long = withContext(Dispatchers.IO) {
        estimateArchiveSize(state)
    }

    suspend fun exportBackup(document: ProjectDocument, fileName: String): String? =
        withContext(Dispatchers.IO) {
            backupTempDirectory.mkdirs()
            val temporaryFile = File.createTempFile("backup-", ".clearcut", backupTempDirectory)
            try {
                if (!exportArchive(document, temporaryFile)) {
                    null
                } else {
                    copyToDownloads(temporaryFile, fileName)
                }
            } finally {
                temporaryFile.delete()
            }
        }

    suspend fun exportArchive(document: ProjectDocument, projectName: String): File? =
        withContext(Dispatchers.IO) {
            archiveDirectory.mkdirs()
            val outputFile = File(
                archiveDirectory,
                "${sanitizeFileName(projectName, fallback = "ClearCut")}.clearcut",
            )
            if (exportArchive(document, outputFile)) outputFile else null
        }

    suspend fun importBackup(uri: Uri): ProjectArchive.ImportResult = withContext(Dispatchers.IO) {
        val targetDirectory = File(importRootDirectory, "imported_${nowMs()}")
        val projectIds = runCatching { existingProjectIds() }.getOrDefault(emptySet())
        importArchive(uri, targetDirectory, projectIds)
    }

    private companion object {
        fun copyFileToDownloads(context: Context, sourceFile: File, fileName: String): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/ClearCut",
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val contentUri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: throw IllegalStateException("Could not create backup destination")
                try {
                    resolver.openOutputStream(contentUri)?.use { output ->
                        sourceFile.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Could not open backup destination")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(contentUri, values, null, null)
                    fileName
                } catch (error: Exception) {
                    resolver.delete(contentUri, null, null)
                    throw error
                }
            } else {
                val downloadsRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(context.filesDir, "downloads")
                val destination = File(File(downloadsRoot, "ClearCut").apply { mkdirs() }, fileName)
                sourceFile.copyTo(destination, overwrite = true)
                destination.name
            }
        }
    }
}
