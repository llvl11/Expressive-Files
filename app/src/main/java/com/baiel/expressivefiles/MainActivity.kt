package com.baiel.expressivefiles

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import com.baiel.expressivefiles.ui.components.SquigglyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.baiel.expressivefiles.data.SettingsRepository
import com.baiel.expressivefiles.model.FileType
import com.baiel.expressivefiles.ui.components.ArchiveViewerSheet
import com.baiel.expressivefiles.ui.components.ChunkyButton
import com.baiel.expressivefiles.ui.components.CreateArchiveDialog
import com.baiel.expressivefiles.ui.components.DialogActionType
import com.baiel.expressivefiles.ui.components.FileActionSheet
import com.baiel.expressivefiles.ui.components.NewItemDialog
import com.baiel.expressivefiles.ui.screens.HomeScreen
import com.baiel.expressivefiles.ui.screens.SettingsScreen
import com.baiel.expressivefiles.ui.screens.StorageAnalysisScreen
import com.baiel.expressivefiles.ui.screens.StorageCategoryFilesScreen
import com.baiel.expressivefiles.ui.theme.ChunkyTileShape
import com.baiel.expressivefiles.ui.theme.ExpressiveFilesTheme
import com.baiel.expressivefiles.viewmodel.FileViewModel
import com.baiel.expressivefiles.viewmodel.localizedProgressOperation
import java.util.Locale

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val STORAGE_ANALYSIS = "storage_analysis"
    const val STORAGE_CATEGORY_FILES = "storage_category_files/{category}"
    fun storageCategoryFiles(category: FileType) = "storage_category_files/${category.name}"
}

class MainActivity : ComponentActivity() {

    private val fileViewModel: FileViewModel by viewModels()

    /**
     * Locale is applied HERE - before resources of any kind are touched - from
     * the synchronous preference read. Russian is the default; the in-app
     * toggle persists a new tag and recreates the activity so every layer
     * (Compose, toasts, notifications) re-resolves at once.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrapLanguage(newBase))
    }

    private fun wrapLanguage(base: Context): Context {
        val tag = SettingsRepository(base).loadLanguageTag()
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        handleShowArchiveIntent(intent)

        setContent {
            val settings by fileViewModel.settings.collectAsStateWithLifecycle()

            ExpressiveFilesTheme(
                themeMode = settings.themeMode,
                colorPalette = settings.colorPalette,
                osType = settings.osType,
                pitchBlack = settings.pitchBlack
            ) {
                // No full-screen Surface here: the window background plus the
                // Scaffold's containerColor already paint the base layer, and a
                // third identical full-screen layer is pure GPU overdraw.
                AppNavigation(viewModel = fileViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Cold-start deliveries arrive through onCreate; warm-start ones
        // (notification "Show dialog" while the app is in the recents stack)
        // land here because the activity is launched singleTop-style.
        handleShowArchiveIntent(intent)
    }

    /** Notification "Show dialog" action: reopen the archive progress popup. */
    private fun handleShowArchiveIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ArchiveActionReceiver.EXTRA_SHOW_ARCHIVE_DIALOG, false) == true) {
            fileViewModel.showArchiveDialogPopup()
        }
    }

    override fun onResume() {
        super.onResume()
        fileViewModel.refreshPermission()
    }
}

@Composable
fun AppNavigation(viewModel: FileViewModel) {
    val navController = rememberNavController()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            // Pure, fully-opaque full-width translations for BOTH directions.
            // Fades on route pops produced the "page shrinks/vanishes while the
            // underlying screen loads in" artifact; opacity changes also fight
            // the predictive-back seek. Opaque slides read identically to the
            // in-folder header animation and commit cleanly.
            enterTransition = { slideInHorizontally(tween(300)) { it } },
            exitTransition = { slideOutHorizontally(tween(300)) { -it } },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it } },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {
                        navController.navigate(Routes.SETTINGS)
                    },
                    onNavigateToStorageAnalysis = {
                        navController.navigate(Routes.STORAGE_ANALYSIS)
                    }
                )
            }

            composable(Routes.SETTINGS) {
                // Classic BackHandler swallows gesture previews for THIS page
                // only (it implements no animation callback), so back from
                // settings runs the clean post-commit slide. Home keeps its own
                // PredictiveBackHandler finger-following effect.
                CommitBackHandler { navController.popBackStack() }
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Routes.STORAGE_ANALYSIS) {
                CommitBackHandler { navController.popBackStack() }
                StorageAnalysisScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onOpenCategory = { category ->
                        navController.navigate(Routes.storageCategoryFiles(category))
                    }
                )
            }

            composable(
                route = Routes.STORAGE_CATEGORY_FILES,
                arguments = listOf(navArgument("category") { type = NavType.StringType })
            ) { backStackEntry ->
                CommitBackHandler {
                    viewModel.clearSelection()
                    navController.popBackStack()
                }
                val category = backStackEntry.arguments?.getString("category")
                    ?.let { name -> runCatching { FileType.valueOf(name) }.getOrNull() }
                if (category == null) {
                    // Unknown category value: never strand the user on a blank page.
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    StorageCategoryFilesScreen(
                        viewModel = viewModel,
                        category = category,
                        onNavigateBack = {
                            viewModel.clearSelection()
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
        GlobalFileDialogs(viewModel = viewModel)
        ArchiveOperationDialog(viewModel = viewModel)
    }
}

/**
 * Non-blocking popup for a running compression/extraction. It surfaces the
 * progress that used to go to an Android notification (which is no longer
 * posted at all - completion is announced with a toast only).
 *   Hide            - buffered one-shot: closes the popup right away; the
 *                     operation keeps running and its completion still
 *                     arrives as a toast.
 *   Cancel          - buffered one-shot: the popup hides immediately and the
 *                     operation is interrupted in the background (a partial
 *                     archive is cleaned up).
 *   Back / outside  - same as Hide.
 */
@Composable
private fun ArchiveOperationDialog(viewModel: FileViewModel) {
    if (!viewModel.showArchiveDialog.collectAsStateWithLifecycle().value) return
    // Non-delegated read so null can be ruled out for the render body below.
    val current = viewModel.archiveProgress.collectAsStateWithLifecycle().value ?: return

    val context = LocalContext.current

    // Buffered cancel: the first tap wins and tears the popup down at once;
    // repeated taps are ignored while the cancellation unwinds in background.
    var cancelHandled by remember { mutableStateOf(false) }

    // Ask for POST_NOTIFICATIONS at the moment it matters - pressing Hide -
    // instead of at cold start, where launch-time requests are frequently
    // suppressed by OEM skins. Without the permission the hidden task simply
    // runs without a notification; the completion toast still arrives.
    val notifOffText = stringResource(R.string.progress_notifications_off)
    var wantsNotificationPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.hideArchiveDialog()
        if (!granted) {
            Toast.makeText(context, notifOffText, Toast.LENGTH_LONG).show()
        }
    }

    fun onHideClicked() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.hideArchiveDialog()
        } else {
            wantsNotificationPermission = true
        }
    }

    LaunchedEffect(wantsNotificationPermission) {
        if (wantsNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            wantsNotificationPermission = false
        }
    }

    Dialog(onDismissRequest = { viewModel.hideArchiveDialog() }) {
        Surface(
            shape = ChunkyTileShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = localizedProgressOperation(context, current.operation),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val detail = current.currentFileName.ifBlank { stringResource(R.string.progress_working) }
                        Text(
                            text = if (current.totalFiles > 0)
                                "${current.filesProcessed}/${current.totalFiles} • $detail"
                            else detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SquigglyProgressIndicator(
                    progress = if (!current.isIndeterminate &&
                        (current.totalFiles > 0 || current.totalBytes > 0)
                    ) current.progressFloat else null
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChunkyButton(
                        text = stringResource(R.string.progress_hide),
                        onClick = { onHideClicked() },
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    ChunkyButton(
                        text = stringResource(R.string.progress_cancel),
                        onClick = {
                            if (!cancelHandled) {
                                cancelHandled = true
                                viewModel.cancelArchive()
                            }
                        },
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Gesture-back WITHOUT the live predictive preview: classic BackHandler does
 * not expose progress events, so the system skips per-frame previews and only
 * invokes it once the gesture commits - routes then play their standard slide.
 * Used on non-folder pages where the seek preview looked broken; the folders
 * view keeps its dedicated subtle PredictiveBackHandler instead.
 */
@Composable
private fun CommitBackHandler(onBack: () -> Unit) {
    BackHandler(enabled = true, onBack = onBack)
}

@Composable
private fun GlobalFileDialogs(viewModel: FileViewModel) {
    val showCreateArchive by viewModel.showCreateArchiveDialog.collectAsStateWithLifecycle()
    val showNewItem by viewModel.showNewItemDialog.collectAsStateWithLifecycle()
    val renameTargetItem by viewModel.renameTargetItem.collectAsStateWithLifecycle()
    val activeArchiveInspectItem by viewModel.activeArchiveInspectItem.collectAsStateWithLifecycle()
    val activeActionSheetItem by viewModel.activeActionSheetItem.collectAsStateWithLifecycle()

    if (showCreateArchive) {
        val files by viewModel.files.collectAsStateWithLifecycle()
        val categoryFiles by viewModel.categoryFiles.collectAsStateWithLifecycle()
        val selectedPaths by viewModel.selectedPaths.collectAsStateWithLifecycle()
        val settings by viewModel.settings.collectAsStateWithLifecycle()

        val selectedFiles = remember(files, categoryFiles, selectedPaths) {
            (files + categoryFiles).filter { it.path in selectedPaths }.distinctBy { it.path }
        }

        CreateArchiveDialog(
            selectedFiles = selectedFiles,
            defaultFormat = settings.defaultArchiveType,
            onDismiss = { viewModel.dismissCreateArchive() },
            onCompress = { name, format -> viewModel.createArchive(name, format) }
        )
    }
    showNewItem?.let { type ->
        NewItemDialog(
            viewModel = viewModel,
            type = type,
            onDismiss = { viewModel.dismissNewItem() },
            onConfirm = { name ->
                if (type == DialogActionType.DELETE_CONFIRM) {
                    viewModel.confirmDelete()
                } else {
                    viewModel.dismissNewItem()
                    if (type == DialogActionType.NEW_FOLDER) viewModel.createFolder(name) else viewModel.createNewFile(name)
                }
            }
        )
    }
    renameTargetItem?.let { target ->
        NewItemDialog(
            viewModel = viewModel,
            type = DialogActionType.RENAME,
            initialValue = target.name,
            onDismiss = { viewModel.closeRename() },
            onConfirm = { newName ->
                viewModel.closeRename()
                viewModel.renameFile(target, newName)
            }
        )
    }
    activeArchiveInspectItem?.let { archiveItem ->
        ArchiveViewerSheet(
            archiveItem = archiveItem,
            onDismiss = { viewModel.closeArchiveInspector() },
            onExtractArchive = { viewModel.extractArchive(it) }
        )
    }
    activeActionSheetItem?.let { item ->
        FileActionSheet(
            targetItem = item,
            onDismiss = { viewModel.closeActionSheet() },
            onOpen = { viewModel.openFile(it) },
            onOpenWithExternalApp = { viewModel.openFileWithExternalApp(it) },
            onCopy = { viewModel.toggleSelection(it); viewModel.copySelected() },
            onCut = { viewModel.toggleSelection(it); viewModel.cutSelected() },
            onRename = { viewModel.startRename(it) },
            onDelete = { viewModel.deleteSingleFile(it) },
            onShare = { viewModel.shareFile(it) },
            onCompress = { viewModel.toggleSelection(it); viewModel.requestCreateArchive() },
            onExtract = { viewModel.extractArchive(it) },
            onInspectArchive = { viewModel.showArchiveInspector(it) }
        )
    }
}


