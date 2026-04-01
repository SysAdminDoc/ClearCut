package com.novacut.editor.ui.projects

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

@Composable
fun ProjectListScreen(
    onProjectSelected: (String) -> Unit,
    onSettings: () -> Unit = {},
    pendingImportUri: android.net.Uri? = null,
    viewModel: ProjectListViewModel = hiltViewModel()
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    var showTemplateSheet by remember { mutableStateOf(false) }

    // Handle incoming video from external intent (ACTION_VIEW)
    LaunchedEffect(pendingImportUri) {
        if (pendingImportUri != null) {
            viewModel.createProjectFromImport(pendingImportUri) { projectId ->
                onProjectSelected(projectId)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Mocha.Base)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                color = Mocha.Crust,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = stringResource(R.string.cd_app_logo),
                            tint = Mocha.Mauve,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.projects_app_title),
                            color = Mocha.Text,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.projects_count, projects.size, if (projects.size != 1) "s" else ""),
                            color = Mocha.Subtext0,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onSettings, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Settings, stringResource(R.string.projects_settings), tint = Mocha.Subtext0, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text(stringResource(R.string.projects_search_placeholder), fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.projects_search),
                                tint = Mocha.Subtext0,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.setSearchQuery("") },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.projects_clear),
                                        tint = Mocha.Subtext0,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Mocha.Surface0,
                            unfocusedContainerColor = Mocha.Surface0,
                            focusedBorderColor = Mocha.Mauve,
                            unfocusedBorderColor = Mocha.Surface1,
                            cursorColor = Mocha.Mauve,
                            focusedTextColor = Mocha.Text,
                            unfocusedTextColor = Mocha.Text,
                            focusedPlaceholderColor = Mocha.Overlay0,
                            unfocusedPlaceholderColor = Mocha.Overlay0
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )

                    // Sort chips
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(SortMode.entries.toList()) { mode ->
                            FilterChip(
                                onClick = { viewModel.setSortMode(mode) },
                                label = { Text(mode.label, fontSize = 12.sp) },
                                selected = sortMode == mode,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Mocha.Surface0,
                                    selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                                    selectedLabelColor = Mocha.Mauve,
                                    labelColor = Mocha.Subtext0
                                ),
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }
            }

            if (projects.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = stringResource(R.string.cd_video_library),
                            tint = Mocha.Overlay0,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isNotEmpty()) stringResource(R.string.projects_no_matching)
                            else stringResource(R.string.projects_no_projects_yet),
                            color = Mocha.Subtext0,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (searchQuery.isNotEmpty()) stringResource(R.string.projects_try_different_search)
                            else stringResource(R.string.projects_tap_to_create),
                            color = Mocha.Overlay0,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

        // FAB
        FloatingActionButton(
            onClick = { showTemplateSheet = true },
            containerColor = Mocha.Mauve,
            contentColor = Mocha.Crust,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.projects_new_project))
        }

        // Template picker
        if (showTemplateSheet) {
            val userTemplates = remember { viewModel.getUserTemplates() }
            val ctx = LocalContext.current
            ProjectTemplateSheet(
                onTemplateSelected = { template ->
                    showTemplateSheet = false
                    val templateName = ctx.getString(template.nameResId)
                    viewModel.createProject(
                        name = if (template.id == "blank") "Untitled" else templateName
                    ) { id -> onProjectSelected(id) }
                },
                onUserTemplateSelected = { userTemplate ->
                    showTemplateSheet = false
                    viewModel.createFromTemplate(userTemplate) { id ->
                        onProjectSelected(id)
                    }
                },
                onDeleteUserTemplate = viewModel::deleteUserTemplate,
                userTemplates = userTemplates,
                onDismiss = { showTemplateSheet = false },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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
                    Mocha.Red.copy(alpha = 0.3f)
                else Mocha.Surface0.copy(alpha = 0.1f),
                label = "swipeBg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
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
            colors = CardDefaults.cardColors(containerColor = Mocha.Surface0),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Project thumbnail
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Mocha.Mantle),
                    contentAlignment = Alignment.Center
                ) {
                    if (project.thumbnailUri != null) {
                        val context = LocalContext.current
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
                            Icons.Default.Movie,
                            contentDescription = stringResource(R.string.cd_movie_placeholder),
                            tint = Mocha.Overlay0,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        project.name,
                        color = Mocha.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        Text(
                            formatDuration(project.durationMs),
                            color = Mocha.Subtext0,
                            fontSize = 12.sp
                        )
                        Text(
                            " \u00B7 ",
                            color = Mocha.Overlay0,
                            fontSize = 12.sp
                        )
                        Text(
                            formatDate(project.updatedAt),
                            color = Mocha.Subtext0,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        "${project.resolution.label} \u00B7 ${project.aspectRatio.label}",
                        color = Mocha.Overlay0,
                        fontSize = 11.sp
                    )
                }

                // Overflow menu
                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.projects_more_cd),
                            tint = Mocha.Overlay0,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        containerColor = Mocha.Surface1
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.projects_duplicate), color = Mocha.Text, fontSize = 14.sp) },
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
                            text = { Text(stringResource(R.string.projects_delete), color = Mocha.Red, fontSize = 14.sp) },
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
            containerColor = Mocha.Surface0,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:%02d".format(seconds)
}

private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}
