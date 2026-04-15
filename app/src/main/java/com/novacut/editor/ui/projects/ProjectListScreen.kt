package com.novacut.editor.ui.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.novacut.editor.R
import com.novacut.editor.model.Project
import com.novacut.editor.model.SortMode
import com.novacut.editor.ui.theme.Mocha
import java.util.Locale

@Composable
fun ProjectListScreen(
    onProjectSelected: (String) -> Unit,
    onSettings: () -> Unit = {},
    pendingImportUri: android.net.Uri? = null,
    onPendingImportHandled: () -> Unit = {},
    viewModel: ProjectListViewModel = hiltViewModel()
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val userTemplates by viewModel.userTemplates.collectAsStateWithLifecycle()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Mocha.Midnight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Mocha.Midnight,
                            Mocha.Base.copy(alpha = 0.98f),
                            Mocha.Midnight
                        )
                    )
                )
        )

        val importTemplate = { templateImportLauncher.launch(arrayOf("*/*")) }

        Column(modifier = Modifier.fillMaxSize()) {
            ProjectHomeHero(
                projectCount = projects.size,
                savedTemplateCount = userTemplates.size,
                searchQuery = searchQuery,
                sortMode = sortMode,
                onSearchQueryChanged = viewModel::setSearchQuery,
                onClearSearch = { viewModel.setSearchQuery("") },
                onSortModeChanged = viewModel::setSortMode,
                onCreateProject = { showTemplateSheet = true },
                onImportTemplate = importTemplate,
                onSettings = onSettings
            )

            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ProjectEmptyState(
                        hasActiveSearch = searchQuery.isNotEmpty(),
                        onCreateProject = { showTemplateSheet = true },
                        onImportTemplate = importTemplate,
                        onClearSearch = { viewModel.setSearchQuery("") }
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.projects_recent),
                    color = Mocha.Text,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onProjectSelected(project.id) },
                            onDelete = { viewModel.deleteProject(project) },
                            onDuplicate = { viewModel.duplicateProject(project) }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showTemplateSheet = true },
            containerColor = Mocha.Rosewater,
            contentColor = Mocha.Midnight,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.projects_new_project)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.projects_new_project),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
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
    onSettings: () -> Unit
) {
    Surface(
        color = Mocha.Panel,
        shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Mocha.PanelHighest.copy(alpha = 0.94f),
                            Mocha.Panel.copy(alpha = 0.98f),
                            Mocha.Mantle
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Mocha.Mauve.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(999.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.22f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = stringResource(R.string.cd_app_logo),
                                tint = Mocha.Rosewater,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.projects_app_title),
                                color = Mocha.Text,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        color = Mocha.PanelHighest,
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke)
                    ) {
                        IconButton(
                            onClick = onSettings,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.projects_settings),
                                tint = Mocha.Subtext1
                            )
                        }
                    }
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
                            accent = Mocha.Mauve
                        )
                    }
                    item {
                        HeroMetricPill(
                            label = stringResource(R.string.projects_templates_count, projectTemplates.size),
                            accent = Mocha.Sapphire
                        )
                    }
                    if (savedTemplateCount > 0) {
                        item {
                            HeroMetricPill(
                                label = stringResource(R.string.projects_saved_templates_count, savedTemplateCount),
                                accent = Mocha.Rosewater
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onCreateProject,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Mocha.Rosewater,
                            contentColor = Mocha.Midnight
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.projects_new_project),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.projects_new_project),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = onImportTemplate,
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStrokeStrong),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Mocha.Text),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = stringResource(R.string.template_import),
                            tint = Mocha.Subtext1,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.template_import),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

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
                    shape = RoundedCornerShape(18.dp),
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
                    textStyle = LocalTextStyle.current.copy(color = Mocha.Text)
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SortMode.entries.toList()) { mode ->
                        FilterChip(
                            onClick = { onSortModeChanged(mode) },
                            label = {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            selected = sortMode == mode,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Mocha.PanelRaised,
                                selectedContainerColor = Mocha.Mauve.copy(alpha = 0.16f),
                                selectedLabelColor = Mocha.Rosewater,
                                labelColor = Mocha.Subtext0
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetricPill(
    label: String,
    accent: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.2f))
    ) {
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ProjectEmptyState(
    hasActiveSearch: Boolean,
    onCreateProject: () -> Unit,
    onImportTemplate: () -> Unit,
    onClearSearch: () -> Unit
) {
    Surface(
        color = Mocha.Panel,
        shape = RoundedCornerShape(28.dp),
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = Mocha.Mauve.copy(alpha = 0.14f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Mauve.copy(alpha = 0.22f))
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = stringResource(R.string.cd_video_library),
                        tint = Mocha.Rosewater,
                        modifier = Modifier
                            .padding(18.dp)
                            .size(30.dp)
                    )
                }

                Text(
                    text = if (hasActiveSearch) {
                        stringResource(R.string.projects_no_matching)
                    } else {
                        stringResource(R.string.projects_ready_title)
                    },
                    color = Mocha.Text,
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = if (hasActiveSearch) {
                        stringResource(R.string.projects_try_different_search)
                    } else {
                        stringResource(R.string.projects_ready_body)
                    },
                    color = Mocha.Subtext0,
                    style = MaterialTheme.typography.bodyLarge
                )

                if (hasActiveSearch) {
                    OutlinedButton(
                        onClick = onClearSearch,
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStrokeStrong)
                    ) {
                        Text(
                            text = stringResource(R.string.projects_clear),
                            color = Mocha.Text,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = onCreateProject,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Mocha.Rosewater,
                                contentColor = Mocha.Midnight
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.projects_create_first),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = onImportTemplate,
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStrokeStrong),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.template_import),
                                color = Mocha.Text,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showDeleteConfirm = true
                false
            } else false
        }
    )

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
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.projects_delete_cd),
                    tint = Mocha.Red
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = Mocha.Panel),
            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f)),
            shape = RoundedCornerShape(24.dp)
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

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProjectMetadataChip(text = project.resolution.label, accent = Mocha.Rosewater)
                            ProjectMetadataChip(text = project.aspectRatio.label, accent = Mocha.Mauve)
                            ProjectMetadataChip(text = formatDuration(project.durationMs), accent = Mocha.Sapphire)
                        }

                        Text(
                            text = stringResource(R.string.projects_updated, formatDate(project.updatedAt)),
                            color = Mocha.Subtext0,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Box {
                        Surface(
                            color = Mocha.PanelHighest,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke)
                        ) {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.size(36.dp)
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
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.projects_delete_title), color = Mocha.Text) },
            text = { Text(stringResource(R.string.projects_delete_message, project.name), color = Mocha.Subtext0) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.projects_delete), color = Mocha.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = Mocha.Subtext0)
                }
            },
            containerColor = Mocha.PanelHighest,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun ProjectThumbnail(project: Project) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(20.dp))
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
            shape = RoundedCornerShape(10.dp),
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
        shape = RoundedCornerShape(999.dp),
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
