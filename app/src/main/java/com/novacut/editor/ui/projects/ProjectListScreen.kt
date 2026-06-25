package com.novacut.editor.ui.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import com.novacut.editor.R
import com.novacut.editor.engine.IncomingDocumentImportPreview
import com.novacut.editor.engine.IncomingDocumentImportStatus
import com.novacut.editor.engine.IncomingDocumentItem
import com.novacut.editor.engine.IncomingMediaItem
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Project
import com.novacut.editor.model.ProjectFilterMode
import com.novacut.editor.model.SortMode
import com.novacut.editor.ui.ClearCutTestTags
import com.novacut.editor.ui.editor.PremiumSnackbarHost
import com.novacut.editor.ui.editor.ToastSeverity
import com.novacut.editor.ui.editor.inferSeverity
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.ClearCutChromeIconButton
import com.novacut.editor.ui.theme.ClearCutDialogIcon
import com.novacut.editor.ui.theme.ClearCutFilterChip
import com.novacut.editor.ui.theme.ClearCutHeroCard
import com.novacut.editor.ui.theme.ClearCutMetricPill
import com.novacut.editor.ui.theme.ClearCutPrimaryButton
import com.novacut.editor.ui.theme.ClearCutScreenBackground
import com.novacut.editor.ui.theme.ClearCutSectionHeader
import com.novacut.editor.ui.theme.ClearCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import com.novacut.editor.ui.theme.TouchTarget
import java.util.Locale

private const val PROJECT_RENAME_MAX_CHARS = 80

@Composable
fun ProjectListScreen(
    onProjectSelected: (String) -> Unit,
    onSettings: () -> Unit = {},
    pendingImportItems: List<IncomingMediaItem> = emptyList(),
    onPendingImportHandled: () -> Unit = {},
    pendingDocumentItems: List<IncomingDocumentItem> = emptyList(),
    onPendingDocumentImportHandled: () -> Unit = {},
    viewModel: ProjectListViewModel = hiltViewModel()
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val projectTotalCount by viewModel.projectTotalCount.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val filterMode by viewModel.filterMode.collectAsStateWithLifecycle()
    val userTemplates by viewModel.userTemplates.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val operationState by viewModel.operationState.collectAsStateWithLifecycle()
    val documentImportPreview by viewModel.documentImportPreview.collectAsStateWithLifecycle()
    val actionsEnabled = operationState == null
    val hasAnyProjects = projectTotalCount > 0
    var showTemplateSheet by remember { mutableStateOf(false) }
    val templateImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importTemplate(uri)
        }
    }

    LaunchedEffect(pendingImportItems) {
        if (pendingImportItems.isNotEmpty()) {
            val items = pendingImportItems
            onPendingImportHandled()
            viewModel.createProjectFromImports(items) { projectId ->
                onProjectSelected(projectId)
            }
        }
    }

    LaunchedEffect(pendingDocumentItems) {
        if (pendingDocumentItems.isNotEmpty()) {
            val items = pendingDocumentItems
            onPendingDocumentImportHandled()
            viewModel.previewIncomingDocuments(items)
        }
    }

    ClearCutScreenBackground(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ClearCutTestTags.PROJECTS_SCREEN)
    ) {
        val importTemplate = { templateImportLauncher.launch(arrayOf("*/*")) }
        val showCollectionControls = projectTotalCount > 1 ||
            searchQuery.isNotBlank() ||
            filterMode != ProjectFilterMode.ALL

        Column(modifier = Modifier.fillMaxSize()) {
            ProjectHomeHero(
                projectCount = projectTotalCount,
                savedTemplateCount = userTemplates.size,
                searchQuery = searchQuery,
                sortMode = sortMode,
                onSearchQueryChanged = viewModel::setSearchQuery,
                onClearSearch = { viewModel.setSearchQuery("") },
                onSortModeChanged = viewModel::setSortMode,
                onCreateProject = { showTemplateSheet = true },
                onImportTemplate = importTemplate,
                onSettings = onSettings,
                showProjectActions = projects.isNotEmpty(),
                showSearch = showCollectionControls,
                showSortControls = showCollectionControls && projects.isNotEmpty(),
                actionsEnabled = actionsEnabled
            )

            ProjectHomeReadinessRow(
                projectCount = projectTotalCount,
                savedTemplateCount = userTemplates.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            )

            if (showCollectionControls) {
                ProjectFilterChipsRow(
                    filterMode = filterMode,
                    onFilterModeChanged = viewModel::setFilterMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                )
            }

            AnimatedVisibility(
                visible = operationState != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                operationState?.let { operation ->
                    ProjectOperationCard(
                        operation = operation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                    )
                }
            }

            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                    contentAlignment = Alignment.TopCenter
                ) {
                    ProjectEmptyState(
                        projectTotalCount = projectTotalCount,
                        searchQuery = searchQuery,
                        filterMode = filterMode,
                        onCreateProject = { showTemplateSheet = true },
                        onImportTemplate = importTemplate,
                        onShowAllProjects = {
                            viewModel.setSearchQuery("")
                            viewModel.setFilterMode(ProjectFilterMode.ALL)
                        },
                        actionsEnabled = actionsEnabled
                    )
                }
            } else {
                val hasActiveSearch = searchQuery.isNotBlank()
                val hasActiveFilter = filterMode != ProjectFilterMode.ALL
                val sortLabel = sortMode.localizedLabel()
                val filterLabel = filterMode.localizedLabel()
                ClearCutSectionHeader(
                    title = if (hasActiveSearch) {
                        if (projects.size == 1) {
                            stringResource(R.string.projects_results_count_one)
                        } else {
                            stringResource(R.string.projects_results_count_many, projects.size)
                        }
                    } else if (hasActiveFilter) {
                        filterLabel
                    } else {
                        stringResource(R.string.projects_recent)
                    },
                    description = if (hasActiveSearch && hasActiveFilter) {
                        stringResource(
                            R.string.projects_filtered_sorted_summary,
                            filterLabel.lowercase(Locale.getDefault()),
                            sortLabel.lowercase(Locale.getDefault())
                        )
                    } else if (hasActiveSearch) {
                        stringResource(
                            R.string.projects_sorted_summary,
                            sortLabel.lowercase(Locale.getDefault())
                        )
                    } else if (hasActiveFilter) {
                        stringResource(
                            R.string.projects_filter_count_sorted_summary,
                            projects.size,
                            projectTotalCount,
                            sortLabel.lowercase(Locale.getDefault())
                        )
                    } else {
                        stringResource(R.string.projects_recent_subtitle)
                    },
                    modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = 14.dp, bottom = Spacing.sm),
                    trailing = {
                        ClearCutMetricPill(
                            text = sortLabel,
                            accent = Mocha.Sapphire,
                            icon = Icons.Default.FilterList
                        )
                    }
                )

                val trashed by viewModel.trashedProjects.collectAsStateWithLifecycle()
                var showTrash by remember { mutableStateOf(false) }
                var confirmEmptyTrash by remember { mutableStateOf(false) }
                var pendingDeleteForever by remember { mutableStateOf<Project?>(null) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onProjectSelected(project.id) },
                            onRename = { newName -> viewModel.renameProject(project, newName) },
                            onDelete = { viewModel.deleteProject(project) },
                            onDuplicate = { viewModel.duplicateProject(project) }
                        )
                    }

                    if (trashed.isNotEmpty()) {
                        item(key = "__trash_header") {
                            TrashSectionHeader(
                                count = trashed.size,
                                expanded = showTrash,
                                onToggle = { showTrash = !showTrash },
                                onEmptyTrash = { confirmEmptyTrash = true }
                            )
                        }

                        if (showTrash) {
                            items(trashed, key = { "trash_${it.id}" }) { project ->
                                TrashedProjectCard(
                                    project = project,
                                    onRestore = { viewModel.restoreProject(project) },
                                    onDeleteForever = { pendingDeleteForever = project }
                                )
                            }
                        }
                    }
                }

                // Permanent deletions get an explicit confirmation: the trash IS
                // the undo path, so purging it must not ride on a single mis-tap
                // (the button sits directly beside the expand/collapse header).
                if (confirmEmptyTrash) {
                    AlertDialog(
                        onDismissRequest = { confirmEmptyTrash = false },
                        icon = { ClearCutDialogIcon(icon = Icons.Default.DeleteForever, accent = Mocha.Red) },
                        title = {
                            Text(
                                text = stringResource(R.string.trash_empty_confirm_title),
                                color = Mocha.Text,
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        text = {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.trash_empty_confirm_message,
                                    trashed.size,
                                    trashed.size
                                ),
                                color = Mocha.Subtext0,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            ClearCutSecondaryButton(
                                text = stringResource(R.string.trash_empty_confirm_action),
                                onClick = {
                                    viewModel.emptyTrash()
                                    confirmEmptyTrash = false
                                },
                                icon = Icons.Default.DeleteForever,
                                contentColor = Mocha.Red
                            )
                        },
                        dismissButton = {
                            ClearCutSecondaryButton(
                                text = stringResource(R.string.cancel),
                                onClick = { confirmEmptyTrash = false }
                            )
                        },
                        containerColor = Mocha.PanelHighest,
                        titleContentColor = Mocha.Text,
                        textContentColor = Mocha.Subtext0,
                        shape = RoundedCornerShape(Radius.xxl)
                    )
                }

                pendingDeleteForever?.let { doomed ->
                    AlertDialog(
                        onDismissRequest = { pendingDeleteForever = null },
                        icon = { ClearCutDialogIcon(icon = Icons.Default.DeleteForever, accent = Mocha.Red) },
                        title = {
                            Text(
                                text = stringResource(R.string.trash_delete_forever_title),
                                color = Mocha.Text,
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.trash_delete_forever_message, doomed.name),
                                color = Mocha.Subtext0,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            ClearCutSecondaryButton(
                                text = stringResource(R.string.trash_delete_forever_action),
                                onClick = {
                                    viewModel.deleteProjectForever(doomed)
                                    pendingDeleteForever = null
                                },
                                icon = Icons.Default.DeleteForever,
                                contentColor = Mocha.Red
                            )
                        },
                        dismissButton = {
                            ClearCutSecondaryButton(
                                text = stringResource(R.string.cancel),
                                onClick = { pendingDeleteForever = null }
                            )
                        },
                        containerColor = Mocha.PanelHighest,
                        titleContentColor = Mocha.Text,
                        textContentColor = Mocha.Subtext0,
                        shape = RoundedCornerShape(Radius.xxl)
                    )
                }
            }
        }

        // Template picker
        if (showTemplateSheet) {
            val ctx = LocalContext.current
            ProjectTemplateSheet(
                onTemplateSelected = { template ->
                    showTemplateSheet = false
                    val templateName = ctx.getString(template.nameResId)
                    viewModel.createProject(
                        name = if (template.id == "blank") ctx.getString(R.string.project_untitled) else templateName,
                        aspectRatio = template.aspectRatio,
                        templateId = template.id,
                        trackTypes = template.tracks
                    ) { id -> onProjectSelected(id) }
                },
                onUserTemplateSelected = { userTemplate ->
                    showTemplateSheet = false
                    viewModel.createFromTemplate(userTemplate) { id ->
                        onProjectSelected(id)
                    }
                },
                onShareTemplate = viewModel::shareTemplate,
                onImportTemplate = importTemplate,
                onDeleteUserTemplate = viewModel::deleteUserTemplate,
                userTemplates = userTemplates,
                onDismiss = { showTemplateSheet = false },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        documentImportPreview?.let { preview ->
            IncomingDocumentImportDialog(
                preview = preview,
                onConfirm = viewModel::importPreviewedDocument,
                onDismiss = viewModel::dismissDocumentImportPreview
            )
        }

        PremiumSnackbarHost(
            message = toastMessage,
            severity = toastMessage?.let(::inferSeverity) ?: ToastSeverity.Info,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
        )
    }
}

@Composable
private fun IncomingDocumentImportDialog(
    preview: IncomingDocumentImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = when (preview.status) {
        IncomingDocumentImportStatus.READY,
        IncomingDocumentImportStatus.IMPORTED -> Mocha.Green
        IncomingDocumentImportStatus.BLOCKED -> Mocha.Yellow
        IncomingDocumentImportStatus.INVALID -> Mocha.Red
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            ClearCutDialogIcon(
                icon = when (preview.status) {
                    IncomingDocumentImportStatus.READY -> Icons.Default.Description
                    IncomingDocumentImportStatus.IMPORTED -> Icons.Default.TaskAlt
                    IncomingDocumentImportStatus.BLOCKED -> Icons.Default.PendingActions
                    IncomingDocumentImportStatus.INVALID -> Icons.Default.ReportProblem
                },
                accent = accent
            )
        },
        title = {
            Text(
                text = preview.title,
                color = Mocha.Text,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = preview.body,
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyMedium
                )
                preview.details.forEach { detail ->
                    DocumentReportLine(text = detail, color = Mocha.Text)
                }
                preview.warnings.forEach { warning ->
                    DocumentReportLine(text = warning, color = Mocha.Yellow)
                }
            }
        },
        confirmButton = {
            if (preview.canImportNow) {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.project_document_import_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        containerColor = Mocha.PanelHighest,
        titleContentColor = Mocha.Text,
        textContentColor = Mocha.Subtext0
    )
}

@Composable
private fun DocumentReportLine(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Circle,
            contentDescription = null,
            tint = color.copy(alpha = 0.84f),
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
        )
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ProjectHomeHero(
    projectCount: Int,
    savedTemplateCount: Int,
    searchQuery: String,
    sortMode: SortMode,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSortModeChanged: (SortMode) -> Unit,
    onCreateProject: () -> Unit,
    onImportTemplate: () -> Unit,
    onSettings: () -> Unit,
    showProjectActions: Boolean,
    showSearch: Boolean,
    showSortControls: Boolean,
    actionsEnabled: Boolean
) {
    ClearCutHeroCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = Radius.xl, bottomEnd = Radius.xl),
        accent = Mocha.Mauve
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClearCutMetricPill(
                text = stringResource(R.string.projects_app_title),
                accent = Mocha.Mauve,
                icon = Icons.Default.Movie
            )
            Spacer(modifier = Modifier.weight(1f))
            ClearCutChromeIconButton(
                icon = Icons.Default.Settings,
                contentDescription = stringResource(R.string.projects_settings),
                onClick = onSettings,
                modifier = Modifier.testTag(ClearCutTestTags.PROJECTS_SETTINGS)
            )
        }

        Text(
            text = stringResource(R.string.projects_headline),
            color = Mocha.Text,
            style = MaterialTheme.typography.displayMedium
        )

        Text(
            text = stringResource(R.string.projects_subtitle),
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.bodyLarge
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item {
                HeroMetricPill(
                    label = stringResource(
                        R.string.projects_count,
                        projectCount,
                        if (projectCount != 1) "s" else ""
                    ),
                    accent = Mocha.Mauve,
                    icon = Icons.Default.Folder
                )
            }
            item {
                HeroMetricPill(
                    label = stringResource(R.string.projects_templates_count, projectTemplates.size),
                    accent = Mocha.Sapphire,
                    icon = Icons.Default.DashboardCustomize
                )
            }
            if (savedTemplateCount > 0) {
                item {
                    HeroMetricPill(
                        label = stringResource(R.string.projects_saved_templates_count, savedTemplateCount),
                        accent = Mocha.Rosewater,
                        icon = Icons.Default.BookmarkAdded
                    )
                }
            }
        }

        if (showProjectActions) {
            ProjectActionRow(
                primaryLabel = stringResource(R.string.projects_new_project),
                primaryIcon = Icons.Default.Add,
                onPrimary = onCreateProject,
                secondaryLabel = stringResource(R.string.template_import),
                secondaryIcon = Icons.Default.FileOpen,
                onSecondary = onImportTemplate,
                enabled = actionsEnabled,
                primaryTestTag = ClearCutTestTags.PROJECTS_CREATE_PROJECT
            )
        }

        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = {
                    Text(
                        text = stringResource(R.string.projects_search_placeholder),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.projects_search),
                        tint = Mocha.Subtext0,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = onClearSearch) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.projects_clear),
                                tint = Mocha.Subtext0,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.lg),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Mocha.PanelRaised.copy(alpha = 0.92f),
                    unfocusedContainerColor = Mocha.PanelRaised.copy(alpha = 0.82f),
                    focusedBorderColor = Mocha.Mauve.copy(alpha = 0.55f),
                    unfocusedBorderColor = Mocha.CardStroke,
                    cursorColor = Mocha.Rosewater,
                    focusedTextColor = Mocha.Text,
                    unfocusedTextColor = Mocha.Text,
                    focusedPlaceholderColor = Mocha.Overlay1,
                    unfocusedPlaceholderColor = Mocha.Overlay1
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Mocha.Text)
            )
        }

        if (showSortControls) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(SortMode.entries.toList()) { mode ->
                    ClearCutFilterChip(
                        onClick = { onSortModeChanged(mode) },
                        text = mode.localizedLabel(),
                        selected = sortMode == mode,
                        accent = Mocha.Rosewater,
                        icon = if (sortMode == mode) Icons.Default.Check else null
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetricPill(
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    ClearCutMetricPill(text = label, accent = accent, icon = icon)
}

@Composable
private fun ProjectHomeReadinessRow(
    projectCount: Int,
    savedTemplateCount: Int,
    modifier: Modifier = Modifier
) {
    val exportDefaults = remember { ExportConfig() }
    val mediaHealthValue = if (projectCount == 0) {
        stringResource(R.string.projects_media_health_ready_value)
    } else {
        pluralStringResource(
            R.plurals.projects_media_health_projects_value,
            projectCount,
            projectCount
        )
    }
    BoxWithConstraints(modifier = modifier) {
        val stackCards = maxWidth < 520.dp
        val arrangement = Arrangement.spacedBy(Spacing.sm)
        if (stackCards) {
            Column(verticalArrangement = arrangement) {
                ProjectReadinessCard(
                    title = stringResource(R.string.projects_media_health_title),
                    value = mediaHealthValue,
                    body = stringResource(R.string.projects_media_health_body),
                    icon = Icons.Default.Verified,
                    accent = Mocha.Green,
                    modifier = Modifier.fillMaxWidth()
                )
                ProjectReadinessCard(
                    title = stringResource(R.string.projects_render_ready_title),
                    value = stringResource(
                        R.string.projects_render_ready_value,
                        exportDefaults.codec.label,
                        exportDefaults.resolution.label,
                        exportDefaults.frameRate
                    ),
                    body = stringResource(R.string.projects_render_ready_body, projectTemplates.size + savedTemplateCount),
                    icon = Icons.Default.Speed,
                    accent = Mocha.Mauve,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(horizontalArrangement = arrangement) {
                ProjectReadinessCard(
                    title = stringResource(R.string.projects_media_health_title),
                    value = mediaHealthValue,
                    body = stringResource(R.string.projects_media_health_body),
                    icon = Icons.Default.Verified,
                    accent = Mocha.Green,
                    modifier = Modifier.weight(1f)
                )
                ProjectReadinessCard(
                    title = stringResource(R.string.projects_render_ready_title),
                    value = stringResource(
                        R.string.projects_render_ready_value,
                        exportDefaults.codec.label,
                        exportDefaults.resolution.label,
                        exportDefaults.frameRate
                    ),
                    body = stringResource(R.string.projects_render_ready_body, projectTemplates.size + savedTemplateCount),
                    icon = Icons.Default.Speed,
                    accent = Mocha.Mauve,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProjectReadinessCard(
    title: String,
    value: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.semantics {
            contentDescription = "$title. $value. $body"
        },
        color = Mocha.Panel.copy(alpha = 0.92f),
        shape = RoundedCornerShape(Radius.lg),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.88f))
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = accent.copy(alpha = 0.13f),
                shape = RoundedCornerShape(Radius.md),
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.28f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    color = accent,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = body,
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProjectOperationCard(
    operation: ProjectListOperationState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(Radius.lg),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.26f))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(
                    color = Mocha.Mauve.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(Radius.lg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.22f))
                ) {
                    CircularProgressIndicator(
                        color = Mocha.Mauve,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = operation.title,
                        color = Mocha.Text,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = operation.description,
                        color = Mocha.Subtext0,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(Radius.sm)),
                color = Mocha.Mauve,
                trackColor = Mocha.Surface1
            )
        }
    }
}

@Composable
private fun ProjectFilterChipsRow(
    filterMode: ProjectFilterMode,
    onFilterModeChanged: (ProjectFilterMode) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(ProjectFilterMode.entries.toList()) { mode ->
            ClearCutFilterChip(
                onClick = { onFilterModeChanged(mode) },
                text = mode.localizedLabel(),
                selected = filterMode == mode,
                accent = Mocha.Mauve,
                icon = if (filterMode == mode) Icons.Default.Check else null
            )
        }
    }
}

@Composable
private fun ProjectEmptyState(
    projectTotalCount: Int,
    searchQuery: String,
    filterMode: ProjectFilterMode,
    onCreateProject: () -> Unit,
    onImportTemplate: () -> Unit,
    onShowAllProjects: () -> Unit,
    actionsEnabled: Boolean
) {
    val hasAnyProjects = projectTotalCount > 0
    val hasActiveSearch = searchQuery.isNotBlank()
    val hasActiveFilter = filterMode != ProjectFilterMode.ALL
    val isConstrainedEmpty = hasAnyProjects && (hasActiveSearch || hasActiveFilter)

    Surface(
        color = Mocha.Panel,
        shape = RoundedCornerShape(Radius.xl),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Mocha.PanelHighest.copy(alpha = 0.92f),
                            Mocha.Panel
                        )
                    )
                )
                .padding(horizontal = Spacing.xxl, vertical = Spacing.xxxl)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(
                    color = if (isConstrainedEmpty) {
                        Mocha.Sapphire.copy(alpha = 0.14f)
                    } else {
                        Mocha.Mauve.copy(alpha = 0.14f)
                    },
                    shape = RoundedCornerShape(Radius.lg),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isConstrainedEmpty) {
                            Mocha.Sapphire.copy(alpha = 0.24f)
                        } else {
                            Mocha.Mauve.copy(alpha = 0.22f)
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (isConstrainedEmpty) Icons.Default.Search else Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = if (isConstrainedEmpty) Mocha.Sapphire else Mocha.Rosewater,
                        modifier = Modifier
                            .padding(Spacing.lg)
                            .size(30.dp)
                    )
                }

                Text(
                    text = projectEmptyStateTitle(
                        isConstrainedEmpty = isConstrainedEmpty,
                        hasActiveSearch = hasActiveSearch,
                        hasActiveFilter = hasActiveFilter,
                        filterLabel = filterMode.localizedLabel()
                    ),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = projectEmptyStateBody(
                        isConstrainedEmpty = isConstrainedEmpty,
                        hasActiveSearch = hasActiveSearch,
                        hasActiveFilter = hasActiveFilter
                    ),
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                if (isConstrainedEmpty) {
                    ProjectActionRow(
                        primaryLabel = stringResource(R.string.projects_show_all),
                        primaryIcon = Icons.Default.Clear,
                        onPrimary = onShowAllProjects,
                        secondaryLabel = stringResource(R.string.projects_new_project),
                        secondaryIcon = Icons.Default.Add,
                        onSecondary = onCreateProject,
                        enabled = actionsEnabled,
                        secondaryTestTag = ClearCutTestTags.PROJECTS_CREATE_PROJECT
                    )
                } else {
                    ProjectEmptyStateActions(
                        onCreateProject = onCreateProject,
                        onImportTemplate = onImportTemplate,
                        enabled = actionsEnabled
                    )
                }
            }
        }
    }
}

@Composable
private fun projectEmptyStateTitle(
    isConstrainedEmpty: Boolean,
    hasActiveSearch: Boolean,
    hasActiveFilter: Boolean,
    filterLabel: String
): String = when {
    !isConstrainedEmpty -> stringResource(R.string.projects_ready_title)
    hasActiveSearch && hasActiveFilter -> stringResource(R.string.projects_no_matching)
    hasActiveFilter -> stringResource(R.string.projects_no_filter_results, filterLabel)
    else -> stringResource(R.string.projects_no_matching)
}

@Composable
private fun projectEmptyStateBody(
    isConstrainedEmpty: Boolean,
    hasActiveSearch: Boolean,
    hasActiveFilter: Boolean
): String = when {
    !isConstrainedEmpty -> stringResource(R.string.projects_ready_body)
    hasActiveSearch && hasActiveFilter -> stringResource(R.string.projects_try_different_view)
    hasActiveFilter -> stringResource(R.string.projects_filter_empty_body)
    else -> stringResource(R.string.projects_try_different_search)
}

@Composable
private fun ProjectEmptyStateActions(
    onCreateProject: () -> Unit,
    onImportTemplate: () -> Unit,
    enabled: Boolean
) {
    ProjectActionRow(
        primaryLabel = stringResource(R.string.projects_create_first),
        primaryIcon = Icons.Default.Add,
        onPrimary = onCreateProject,
        secondaryLabel = stringResource(R.string.template_import),
        secondaryIcon = Icons.Default.FileOpen,
        onSecondary = onImportTemplate,
        enabled = enabled,
        primaryTestTag = ClearCutTestTags.PROJECTS_CREATE_PROJECT
    )
}

@Composable
private fun ProjectActionRow(
    primaryLabel: String,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    secondaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onSecondary: () -> Unit,
    enabled: Boolean = true,
    primaryTestTag: String? = null,
    secondaryTestTag: String? = null
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackActions = maxWidth < 360.dp
        if (stackActions) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ClearCutPrimaryButton(
                    text = primaryLabel,
                    icon = primaryIcon,
                    onClick = onPrimary,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(primaryTestTag?.let { Modifier.testTag(it) } ?: Modifier)
                )
                ClearCutSecondaryButton(
                    text = secondaryLabel,
                    icon = secondaryIcon,
                    onClick = onSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(secondaryTestTag?.let { Modifier.testTag(it) } ?: Modifier),
                    contentColor = Mocha.Text,
                    enabled = enabled
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ClearCutPrimaryButton(
                    text = primaryLabel,
                    icon = primaryIcon,
                    onClick = onPrimary,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1.15f)
                        .then(primaryTestTag?.let { Modifier.testTag(it) } ?: Modifier)
                )
                ClearCutSecondaryButton(
                    text = secondaryLabel,
                    icon = secondaryIcon,
                    onClick = onSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .then(secondaryTestTag?.let { Modifier.testTag(it) } ?: Modifier),
                    contentColor = Mocha.Text,
                    enabled = enabled
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val projectDuration = formatDuration(project.durationMs)
    val updatedLabel = formatDate(project.updatedAt)
    val projectCardDescription = stringResource(
        R.string.projects_card_cd,
        project.name,
        projectDuration,
        updatedLabel
    )

    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            showDeleteConfirm = true
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                    Mocha.Red.copy(alpha = 0.24f)
                else Mocha.Panel.copy(alpha = 0.45f),
                label = "swipeBg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.projects_delete),
                        color = Mocha.Red,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.projects_delete_cd),
                        tint = Mocha.Red
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 124.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = projectCardDescription
                },
            colors = CardDefaults.cardColors(containerColor = Mocha.Panel),
            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
            shape = RoundedCornerShape(Radius.lg)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Mocha.PanelHighest.copy(alpha = 0.72f),
                                Mocha.Panel.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProjectThumbnail(project = project)

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            project.name,
                            color = Mocha.Text,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ProjectMetadataChip(text = project.resolution.label, accent = Mocha.Rosewater)
                            ProjectMetadataChip(text = "${project.frameRate} fps", accent = Mocha.Mauve)
                            ProjectMetadataChip(text = projectDuration, accent = Mocha.Sapphire)
                            if (project.templateId != null) {
                                ProjectMetadataChip(
                                    text = stringResource(R.string.projects_template_badge),
                                    accent = Mocha.Green
                                )
                            }
                            if (project.proxyEnabled) {
                                ProjectMetadataChip(
                                    text = stringResource(R.string.projects_proxy_badge),
                                    accent = Mocha.Teal
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.projects_updated, updatedLabel),
                            color = Mocha.Subtext0,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Box {
                        Surface(
                            color = Mocha.PanelHighest,
                            shape = RoundedCornerShape(Radius.lg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke)
                        ) {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.size(TouchTarget.minimum)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.projects_more_cd),
                                    tint = Mocha.Subtext0,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            containerColor = Mocha.PanelHighest
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.projects_rename), color = Mocha.Text) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.projects_rename),
                                        tint = Mocha.Subtext0,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.projects_duplicate), color = Mocha.Text) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.cd_duplicate_project),
                                        tint = Mocha.Subtext0,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    onDuplicate()
                                    showOverflowMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.projects_delete), color = Mocha.Red) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.cd_delete_project),
                                        tint = Mocha.Red,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Mocha.Overlay1,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                ClearCutDialogIcon(
                    icon = Icons.Default.Delete,
                    accent = Mocha.Red
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.projects_delete_title),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.projects_delete_message, project.name),
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                ClearCutSecondaryButton(
                    text = stringResource(R.string.projects_delete),
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    icon = Icons.Default.Delete,
                    contentColor = Mocha.Red
                )
            },
            dismissButton = {
                ClearCutSecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showDeleteConfirm = false }
                )
            },
            containerColor = Mocha.PanelHighest,
            titleContentColor = Mocha.Text,
            textContentColor = Mocha.Subtext0,
            shape = RoundedCornerShape(Radius.xl)
        )
    }

    if (showRenameDialog) {
        var projectName by remember(project.name) { mutableStateOf(project.name) }
        val trimmedProjectName = projectName.trim()
        val canSubmitRename = trimmedProjectName.isNotBlank() && trimmedProjectName != project.name
        val renameSupportingText = if (trimmedProjectName.isBlank()) {
            stringResource(R.string.projects_rename_required)
        } else {
            stringResource(R.string.projects_rename_helper)
        }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            icon = {
                ClearCutDialogIcon(
                    icon = Icons.Default.Edit,
                    accent = Mocha.Rosewater
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.projects_rename_title),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it.take(PROJECT_RENAME_MAX_CHARS) },
                    singleLine = true,
                    isError = trimmedProjectName.isBlank(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canSubmitRename) {
                                onRename(trimmedProjectName)
                                showRenameDialog = false
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.projects_rename_hint),
                            color = Mocha.Overlay1
                        )
                    },
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = renameSupportingText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${projectName.length}/$PROJECT_RENAME_MAX_CHARS",
                                maxLines = 1
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Mocha.Text,
                        unfocusedTextColor = Mocha.Text,
                        errorTextColor = Mocha.Text,
                        cursorColor = Mocha.Rosewater,
                        focusedBorderColor = Mocha.Mauve,
                        unfocusedBorderColor = Mocha.CardStroke,
                        errorBorderColor = Mocha.Red,
                        focusedContainerColor = Mocha.PanelRaised,
                        unfocusedContainerColor = Mocha.PanelRaised
                    )
                )
            },
            confirmButton = {
                ClearCutPrimaryButton(
                    text = stringResource(R.string.done),
                    onClick = {
                        onRename(trimmedProjectName)
                        showRenameDialog = false
                    },
                    enabled = canSubmitRename,
                    icon = Icons.Default.Check
                )
            },
            dismissButton = {
                ClearCutSecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showRenameDialog = false }
                )
            },
            containerColor = Mocha.PanelHighest,
            titleContentColor = Mocha.Text,
            textContentColor = Mocha.Subtext0,
            shape = RoundedCornerShape(Radius.xl)
        )
    }
}

@Composable
private fun ProjectThumbnail(project: Project) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Mocha.Mauve.copy(alpha = 0.26f),
                        Mocha.PanelHighest
                    )
                )
            )
    ) {
        if (project.thumbnailUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(android.net.Uri.parse(project.thumbnailUri))
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .build(),
                contentDescription = stringResource(R.string.projects_thumbnail_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = stringResource(R.string.cd_movie_placeholder),
                tint = Mocha.Rosewater,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            )
        }

        Surface(
            color = Mocha.Midnight.copy(alpha = 0.78f),
            shape = RoundedCornerShape(Radius.sm),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = project.aspectRatio.label,
                color = Mocha.Text,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ProjectMetadataChip(
    text: String,
    accent: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(Radius.sm),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun TrashSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEmptyTrash: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle),
        color = Mocha.Panel,
        shape = RoundedCornerShape(Radius.lg),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Surface(
                color = Mocha.Red.copy(alpha = 0.12f),
                shape = RoundedCornerShape(Radius.lg),
                border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Red.copy(alpha = 0.22f))
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = Mocha.Red,
                    modifier = Modifier
                        .padding(Spacing.sm)
                        .size(18.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.trash_title),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = pluralStringResource(R.plurals.trash_kept_summary, count, count),
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (expanded) {
                TextButton(
                    onClick = onEmptyTrash,
                    shape = RoundedCornerShape(Radius.md),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Mocha.Red,
                        containerColor = Mocha.Red.copy(alpha = 0.08f)
                    )
                ) {
                    Text(stringResource(R.string.trash_empty_button), style = MaterialTheme.typography.labelMedium)
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) {
                    stringResource(R.string.trash_collapse_cd)
                } else {
                    stringResource(R.string.trash_expand_cd)
                },
                tint = Mocha.Subtext0,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Composable
private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> stringResource(R.string.projects_just_now)
        diff < 3_600_000 -> stringResource(R.string.projects_minutes_ago, diff / 60_000)
        diff < 86_400_000 -> stringResource(R.string.projects_hours_ago, diff / 3_600_000)
        diff < 604_800_000 -> stringResource(R.string.projects_days_ago, diff / 86_400_000)
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}

@Composable
private fun SortMode.localizedLabel(): String = when (this) {
    SortMode.DATE_DESC -> stringResource(R.string.project_sort_recent)
    SortMode.DATE_ASC -> stringResource(R.string.project_sort_oldest)
    SortMode.NAME_ASC -> stringResource(R.string.project_sort_name_asc)
    SortMode.NAME_DESC -> stringResource(R.string.project_sort_name_desc)
    SortMode.DURATION_DESC -> stringResource(R.string.project_sort_longest)
}

@Composable
private fun ProjectFilterMode.localizedLabel(): String = when (this) {
    ProjectFilterMode.ALL -> stringResource(R.string.project_filter_all)
    ProjectFilterMode.RECENT_7D -> stringResource(R.string.project_filter_this_week)
    ProjectFilterMode.LONG -> stringResource(R.string.project_filter_long)
    ProjectFilterMode.SHORT -> stringResource(R.string.project_filter_short)
    ProjectFilterMode.EMPTY -> stringResource(R.string.project_filter_empty)
}

@Composable
private fun TrashedProjectCard(
    project: Project,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Mocha.Panel.copy(alpha = 0.72f),
        shape = RoundedCornerShape(Radius.lg),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Surface(
                color = Mocha.Red.copy(alpha = 0.10f),
                shape = RoundedCornerShape(Radius.md),
                border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Red.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = Mocha.Red,
                    modifier = Modifier
                        .padding(Spacing.sm)
                        .size(18.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = project.name,
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                project.deletedAtEpochMs?.let { deletedAt ->
                    val daysAgo = ((System.currentTimeMillis() - deletedAt) / 86_400_000).toInt()
                    val daysLeft = (30 - daysAgo).coerceAtLeast(0)
                    Text(
                        text = pluralStringResource(R.plurals.trash_auto_delete_in, daysLeft, daysLeft),
                        color = Mocha.Overlay1,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ClearCutChromeIconButton(
                    icon = Icons.Default.RestoreFromTrash,
                    contentDescription = stringResource(R.string.trash_restore_cd),
                    onClick = onRestore,
                    tint = Mocha.Green,
                    containerColor = Mocha.Green.copy(alpha = 0.08f),
                    borderColor = Mocha.Green.copy(alpha = 0.18f)
                )
                ClearCutChromeIconButton(
                    icon = Icons.Default.DeleteForever,
                    contentDescription = stringResource(R.string.trash_delete_forever_cd),
                    onClick = onDeleteForever,
                    tint = Mocha.Red,
                    containerColor = Mocha.Red.copy(alpha = 0.08f),
                    borderColor = Mocha.Red.copy(alpha = 0.18f)
                )
            }
        }
    }
}
