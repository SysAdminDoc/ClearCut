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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.novacut.editor.R
import com.novacut.editor.model.Project
import com.novacut.editor.model.ProjectFilterMode
import com.novacut.editor.model.SortMode
import com.novacut.editor.ui.editor.PremiumSnackbarHost
import com.novacut.editor.ui.editor.ToastSeverity
import com.novacut.editor.ui.editor.inferSeverity
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.NovaCutChromeIconButton
import com.novacut.editor.ui.theme.NovaCutDialogIcon
import com.novacut.editor.ui.theme.NovaCutFilterChip
import com.novacut.editor.ui.theme.NovaCutHeroCard
import com.novacut.editor.ui.theme.NovaCutMetricPill
import com.novacut.editor.ui.theme.NovaCutPrimaryButton
import com.novacut.editor.ui.theme.NovaCutScreenBackground
import com.novacut.editor.ui.theme.NovaCutSectionHeader
import com.novacut.editor.ui.theme.NovaCutSecondaryButton
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.Spacing
import com.novacut.editor.ui.theme.TouchTarget
import java.util.Locale

private const val PROJECT_RENAME_MAX_CHARS = 80

@Composable
fun ProjectListScreen(
    onProjectSelected: (String) -> Unit,
    onSettings: () -> Unit = {},
    pendingImportUri: android.net.Uri? = null,
    onPendingImportHandled: () -> Unit = {},
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

    // Handle incoming video from external intent (ACTION_VIEW)
    LaunchedEffect(pendingImportUri) {
        if (pendingImportUri != null) {
            onPendingImportHandled()
            viewModel.createProjectFromImport(pendingImportUri) { projectId ->
                onProjectSelected(projectId)
            }
        }
    }

    NovaCutScreenBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        val importTemplate = { templateImportLauncher.launch(arrayOf("*/*")) }

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
                showSearch = hasAnyProjects,
                showSortControls = projects.isNotEmpty(),
                actionsEnabled = actionsEnabled
            )

            if (hasAnyProjects) {
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
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                    contentAlignment = Alignment.Center
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
                NovaCutSectionHeader(
                    title = if (hasActiveSearch) {
                        buildString {
                            append(projects.size)
                            append(if (projects.size == 1) " result" else " results")
                        }
                    } else if (hasActiveFilter) {
                        filterMode.label
                    } else {
                        stringResource(R.string.projects_recent)
                    },
                    description = if (hasActiveSearch && hasActiveFilter) {
                        "Filtered by ${filterMode.label.lowercase(Locale.getDefault())}, sorted by ${sortMode.label.lowercase(Locale.getDefault())}."
                    } else if (hasActiveSearch) {
                        "Sorted by ${sortMode.label.lowercase(Locale.getDefault())}."
                    } else if (hasActiveFilter) {
                        "${projects.size} of $projectTotalCount projects, sorted by ${sortMode.label.lowercase(Locale.getDefault())}."
                    } else {
                        "Pick up where you left off, duplicate a cut, or jump into a template."
                    },
                    modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = 14.dp, bottom = Spacing.sm),
                    trailing = {
                        NovaCutMetricPill(
                            text = sortMode.label,
                            accent = Mocha.Sapphire,
                            icon = Icons.Default.FilterList
                        )
                    }
                )

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
    NovaCutHeroCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = Radius.xxl, bottomEnd = Radius.xxl),
        accent = Mocha.Mauve
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NovaCutMetricPill(
                text = stringResource(R.string.projects_app_title),
                accent = Mocha.Mauve,
                icon = Icons.Default.Movie
            )
            Spacer(modifier = Modifier.weight(1f))
            NovaCutChromeIconButton(
                icon = Icons.Default.Settings,
                contentDescription = stringResource(R.string.projects_settings),
                onClick = onSettings
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

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                enabled = actionsEnabled
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SortMode.entries.toList()) { mode ->
                    NovaCutFilterChip(
                        onClick = { onSortModeChanged(mode) },
                        text = mode.label,
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
    NovaCutMetricPill(text = label, accent = accent, icon = icon)
}

@Composable
private fun ProjectOperationCard(
    operation: ProjectListOperationState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = Mocha.PanelHighest,
        shape = RoundedCornerShape(Radius.xl),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.26f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(ProjectFilterMode.entries.toList()) { mode ->
            NovaCutFilterChip(
                onClick = { onFilterModeChanged(mode) },
                text = mode.label,
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
        shape = RoundedCornerShape(Radius.xxl),
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
                .padding(horizontal = 24.dp, vertical = 28.dp)
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
                    shape = CircleShape,
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
                            .padding(18.dp)
                            .size(30.dp)
                    )
                }

                Text(
                    text = projectEmptyStateTitle(
                        isConstrainedEmpty = isConstrainedEmpty,
                        hasActiveSearch = hasActiveSearch,
                        hasActiveFilter = hasActiveFilter,
                        filterLabel = filterMode.label
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
                        enabled = actionsEnabled
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
        enabled = enabled
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
    enabled: Boolean = true
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackActions = maxWidth < 360.dp
        if (stackActions) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NovaCutPrimaryButton(
                    text = primaryLabel,
                    icon = primaryIcon,
                    onClick = onPrimary,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                )
                NovaCutSecondaryButton(
                    text = secondaryLabel,
                    icon = secondaryIcon,
                    onClick = onSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = Mocha.Text,
                    enabled = enabled
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NovaCutPrimaryButton(
                    text = primaryLabel,
                    icon = primaryIcon,
                    onClick = onPrimary,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
                NovaCutSecondaryButton(
                    text = secondaryLabel,
                    icon = secondaryIcon,
                    onClick = onSecondary,
                    modifier = Modifier.weight(1f),
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
            shape = RoundedCornerShape(Radius.xl)
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
                NovaCutDialogIcon(
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
                NovaCutSecondaryButton(
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
                NovaCutSecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showDeleteConfirm = false }
                )
            },
            containerColor = Mocha.PanelHighest,
            titleContentColor = Mocha.Text,
            textContentColor = Mocha.Subtext0,
            shape = RoundedCornerShape(Radius.xxl)
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
                NovaCutDialogIcon(
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
                NovaCutPrimaryButton(
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
                NovaCutSecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showRenameDialog = false }
                )
            },
            containerColor = Mocha.PanelHighest,
            titleContentColor = Mocha.Text,
            textContentColor = Mocha.Subtext0,
            shape = RoundedCornerShape(Radius.xxl)
        )
    }
}

@Composable
private fun ProjectThumbnail(project: Project) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(Radius.xl))
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
                    .crossfade(true)
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
