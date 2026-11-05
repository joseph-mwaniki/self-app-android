package com.example.selfapp.ui.screens

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import android.app.Activity
import android.content.Context
import android.media.RingtoneManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.example.selfapp.data.entity.*
import com.example.selfapp.service.DnsBlockVpnService
import com.example.selfapp.ui.theme.*
import com.example.selfapp.ui.viewmodel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Sidebar Screens Enum
enum class Screen(val title: String) {
    DASHBOARD("Dashboard"),
    COURSES("Courses"),
    PURGES("Purges"),
    WEEKLY_TESTS("Weekly Tests"),
    MONTHLY_TESTS("Monthly Tests"),
    DAILY_STREAK("Daily Streak"),
    APP_LOCK("App Lock"),
    PANIC_BUTTON("Panic Button"),
    DNS_BLOCKER("DNS Blocker"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    dashboardViewModel: DashboardViewModel,
    courseViewModel: CourseViewModel,
    purgeViewModel: PurgeViewModel,
    testViewModel: TestViewModel,
    appLockViewModel: AppLockViewModel,
    dnsBlockViewModel: DnsBlockViewModel,
    settingsViewModel: SettingsViewModel,
    panicViewModel: PanicViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Black,
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(listOf(SaaSBlue, Color.Transparent)),
                        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                    )
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "☣ SELFAPP CORE",
                    color = SaaSBlue,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    text = "LOCAL DISCIPLINE SHELL",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                Divider(
                    color = Color.DarkGray,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .padding(horizontal = 24.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(Screen.values()) { screen ->
                        val isSelected = currentScreen == screen
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = screen.title.uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    letterSpacing = 1.sp
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                currentScreen = screen
                                scope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = SaaSBlue.copy(alpha = 0.1f),
                                unselectedContainerColor = Color.Transparent,
                                selectedTextColor = SaaSBlue,
                                unselectedTextColor = Color.LightGray
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) SaaSBlue else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = currentScreen.title.uppercase(),
                            color = SaaSBlue,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Sidebar",
                                tint = SaaSBlue
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Black)
                )
            },
            containerColor = Black
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Black)
            ) {
                when (currentScreen) {
                    Screen.DASHBOARD -> DashboardScreen(dashboardViewModel)
                    Screen.COURSES -> CoursesScreen(courseViewModel)
                    Screen.PURGES -> PurgesScreen(purgeViewModel)
                    Screen.WEEKLY_TESTS -> TestsScreen(testViewModel, isMonthly = false)
                    Screen.MONTHLY_TESTS -> TestsScreen(testViewModel, isMonthly = true)
                    Screen.DAILY_STREAK -> StreaksScreen(dashboardViewModel)
                    Screen.APP_LOCK -> AppLockScreen(appLockViewModel)
                    Screen.PANIC_BUTTON -> PanicButtonScreen(panicViewModel, dashboardViewModel)
                    Screen.DNS_BLOCKER -> DnsBlockerScreen(dnsBlockViewModel)
                    Screen.SETTINGS -> SettingsScreen(settingsViewModel)
                }
            }
        }
    }
}

// --- 1. DASHBOARD SCREEN ---
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val progressList by viewModel.courseProgressList.collectAsState()
    val streak by viewModel.streakState.collectAsState()
    val points by viewModel.totalPoints.collectAsState()
    val dailyTasks by viewModel.dailyTasks.collectAsState()
    val testHistory by viewModel.testHistory.collectAsState()
    val activityLevels by viewModel.getActivityCalendar().collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Points Panel
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(1.dp, SaaSBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "TOTAL EARNED POINTS",
                            color = SaaSCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$points PTS",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "💎",
                        fontSize = 32.sp
                    )
                }
            }
        }

        // Activity Calendar Grid (LeetCode style)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "ACTIVITY CALENDAR",
                    color = SaaSBlue,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.height(120.dp).width(150.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        userScrollEnabled = false
                    ) {
                        items(activityLevels.size) { index ->
                            val level = activityLevels[index]
                            val color = when(level) {
                                1 -> SaaSBlue.copy(alpha = 0.3f)
                                2 -> SaaSBlue.copy(alpha = 0.5f)
                                3 -> SaaSBlue.copy(alpha = 0.8f)
                                4 -> SaaSBlue
                                else -> Color.DarkGray.copy(alpha = 0.3f)
                            }
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }

        // Daily Tasks Checklist
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "DAILY OBJECTIVES",
                    color = SaaSBlue,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                dailyTasks.forEach { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleTask(task) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (task.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (task.completed) NeonGreen else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = task.title,
                            color = if (task.completed) Color.Gray else Color.White,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Test History Log
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "TEST ACTIVITY FEED",
                    color = SaaSBlue,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (testHistory.isEmpty()) {
                    Text("No historical data available.", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                testHistory.take(5).forEach { result ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = result.type.uppercase(),
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${result.score}%",
                            color = if (result.score >= 70) NeonGreen else NeonRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Course Progress Cards
        item {
            Text(
                text = "COURSE PROGRESSION",
                color = Color.Gray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(progressList) { progress ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(1.dp, SaaSBlue.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = progress.course.title.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${(progress.percentage * 100).toInt()}%",
                            color = SaaSBlue,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Premium SaaS Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.percentage)
                                .fillMaxHeight()
                                .background(
                                    brush = Brush.horizontalGradient(listOf(SaaSBlue, SaaSCyan)),
                                    shape = CircleShape
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${progress.solvedCount} of ${progress.totalCount} modules resolved.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// --- 2. COURSES SCREEN ---
@Composable
fun CoursesScreen(viewModel: CourseViewModel) {
    val courses by viewModel.courses.collectAsState()
    val selectedCourseId by viewModel.selectedCourseId.collectAsState()
    val selectedTopicId by viewModel.selectedTopicId.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val topics by viewModel.topics.collectAsState()
    val selectedQuestion by viewModel.selectedQuestion.collectAsState()
    val transformations by viewModel.transformations.collectAsState()
    val submissions by viewModel.submissions.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isGrading by viewModel.isGrading.collectAsState()

    val context = LocalContext.current
    var reasoningUri by remember { mutableStateOf<Uri?>(null) }
    var solutionUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isSelectFolder by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        reasoningUri = uri
    }

    val solutionFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        solutionUris = uris
        isSelectFolder = false
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            solutionUris = listOf(uri)
            isSelectFolder = true
        }
    }

    if (viewModel.showAddCourseDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.showAddCourseDialog = false },
            title = { Text("ADD NEW COURSE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Course Title", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SaaSBlue,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Course Description", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SaaSBlue,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            viewModel.addCourse(title, desc)
                            viewModel.showAddCourseDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaaSBlue)
                ) {
                    Text("ADD", color = White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showAddCourseDialog = false }) {
                    Text("CANCEL", color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = SaaSCard
        )
    }

    if (viewModel.showAddTopicDialog) {
        var topicTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.showAddTopicDialog = false },
            title = { Text("ADD NEW TOPIC", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                OutlinedTextField(
                    value = topicTitle,
                    onValueChange = { topicTitle = it },
                    label = { Text("Topic Title", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SaaSBlue,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (topicTitle.isNotBlank()) {
                            viewModel.addTopic(topicTitle)
                            viewModel.showAddTopicDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaaSBlue)
                ) {
                    Text("ADD", color = White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showAddTopicDialog = false }) {
                    Text("CANCEL", color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = SaaSCard
        )
    }

    if (viewModel.showAddQuestionDialog) {
        var text by remember { mutableStateOf("") }
        var diff by remember { mutableStateOf("EASY") }
        var ptsStr by remember { mutableStateOf("10") }
        AlertDialog(
            onDismissRequest = { viewModel.showAddQuestionDialog = false },
            title = { Text("ADD NEW QUESTION", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Question Text", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SaaSBlue,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text("DIFFICULTY", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("EASY", "MEDIUM", "HARD").forEach { level ->
                            val isSel = diff == level
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSel) SaaSBlue.copy(alpha = 0.2f) else Black, RoundedCornerShape(4.dp))
                                    .border(1.dp, if (isSel) SaaSBlue else Color.DarkGray, RoundedCornerShape(4.dp))
                                    .clickable { diff = level }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(level, color = if (isSel) SaaSBlue else Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = ptsStr,
                        onValueChange = { ptsStr = it },
                        label = { Text("Points (e.g. 10)", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = SaaSBlue,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pts = ptsStr.toIntOrNull() ?: 10
                        if (text.isNotBlank()) {
                            viewModel.addQuestion(text, diff, pts)
                            viewModel.showAddQuestionDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaaSBlue)
                ) {
                    Text("ADD", color = White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showAddQuestionDialog = false }) {
                    Text("CANCEL", color = Color.Gray, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = SaaSCard
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back Navigation Panel
        if (selectedCourseId != null) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        if (selectedQuestion != null) {
                            viewModel.selectQuestion(null)
                            reasoningUri = null
                            solutionUris = emptyList()
                        } else if (selectedTopicId != null) {
                            viewModel.selectTopic(null)
                        } else {
                            viewModel.selectCourse(null)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = SaaSBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PREVIOUS SHELL",
                        color = SaaSBlue,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Dynamic State Rendering
        when {
            // Screen 2c: Question Detail & Submissions
            selectedQuestion != null -> {
                val q = selectedQuestion!!

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SaaSCard, RoundedCornerShape(12.dp))
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = q.difficulty + " • " + q.points + " PTS",
                                    color = if (q.difficulty == "HARD") NeonRed else if (q.difficulty == "MEDIUM") SaaSBlue else NeonGreen,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = q.questionText,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            IconButton(onClick = { viewModel.deleteQuestion(q) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Question", tint = NeonRed.copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                // Transformations Generator Panel
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SaaSCard, RoundedCornerShape(12.dp))
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "LLM TRANSFORMATIONS ENGINE",
                            color = SaaSBlue,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Generate 10 conceptual mutations of this question to test underlying principles.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isGenerating) {
                            CircularProgressIndicator(color = SaaSBlue, modifier = Modifier.size(24.dp))
                        } else {
                            Button(
                                onClick = { viewModel.generateTransformationsForSelected() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, SaaSBlue, RoundedCornerShape(20.dp))
                            ) {
                                Text(
                                    text = "TRIGGER 10 VARIATIONS",
                                    color = SaaSBlue,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (transformations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "STORED VARIATIONS (${transformations.size})",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            transformations.forEach { tr ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(Black)
                                        .padding(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = tr.transformedQuestion,
                                            color = Color.LightGray,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { viewModel.deleteTransformation(tr) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Variation", tint = NeonRed.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Submissions Upload Panel
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SaaSCard, RoundedCornerShape(12.dp))
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "SUBMISSION MANAGER",
                            color = SaaSBlue,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Reasoning File picker
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (reasoningUri != null) "✓ Reasoning Document Ready" else "Reasoning (TXT, PDF, MD, DOCX)",
                                color = if (reasoningUri != null) NeonGreen else Color.LightGray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Button(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Text("SELECT", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Solution Files picker
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val count = solutionUris.size
                            val pathStr = if (count > 0) {
                                if (isSelectFolder) "✓ Folder Selected" else "✓ $count Files Ready"
                            } else "Solution (Files or Directory)"

                            Text(
                                text = pathStr,
                                color = if (count > 0) NeonGreen else Color.LightGray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Row {
                                Button(
                                    onClick = { solutionFilesLauncher.launch(arrayOf("*/*")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                ) {
                                    Text("FILES", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Button(
                                    onClick = { folderLauncher.launch(null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                ) {
                                    Text("FOLDER", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (isGrading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = SaaSBlue, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "AI AUTO-GRADING IN PROGRESS...",
                                    color = SaaSBlue,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.submitQuestionSolution(
                                        reasoningUri = reasoningUri,
                                        solutionUris = solutionUris,
                                        isDirectory = isSelectFolder
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SaaSBlue),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = solutionUris.isNotEmpty()
                            ) {
                                Text(
                                    text = "COMMIT SUBMISSION",
                                    color = White,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Previous Submissions Logs
                if (submissions.isNotEmpty()) {
                    item {
                        Text(
                            text = "SUBMISSIONS FEEDBACK LOGS",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    items(submissions) { sub ->
                        val dateStr = remember(sub.submittedAt) { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(sub.submittedAt)) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SaaSCard, RoundedCornerShape(12.dp))
                                .border(1.dp, if (sub.status == "APPROVED") NeonGreen else NeonRed, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "STATUS: ${sub.status}",
                                    color = if (sub.status == "APPROVED") NeonGreen else NeonRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "SCORE: ${sub.score}/100",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Submitted: $dateStr",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (sub.feedback.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = Color.DarkGray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = sub.feedback,
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Screen 2b: Browse Topics & Questions
            selectedCourseId != null && selectedTopicId == null -> {
                item {
                    Text(
                        text = "TOPICS",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SaaSBlue, RoundedCornerShape(12.dp))
                            .clickable { viewModel.showAddTopicDialog = true }
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SaaSBlue.copy(alpha = 0.05f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Default.Add, tint = SaaSBlue, contentDescription = "Add Topic")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ADD NEW TOPIC",
                                color = SaaSBlue,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                items(topics) { topic ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SaaSCard, RoundedCornerShape(12.dp))
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                            .clickable { viewModel.selectTopic(topic.id) }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = topic.title.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            selectedTopicId != null -> {
                item {
                    Text(
                        text = "QUESTIONS",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SaaSBlue, RoundedCornerShape(12.dp))
                            .clickable { viewModel.showAddQuestionDialog = true }
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SaaSBlue.copy(alpha = 0.05f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Default.Add, tint = SaaSBlue, contentDescription = "Add Question")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ADD NEW QUESTION",
                                color = SaaSBlue,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                items(questions) { q ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SaaSCard, RoundedCornerShape(12.dp))
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                            .clickable { viewModel.selectQuestion(q) }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = q.difficulty + " • " + q.points + " PTS",
                                    color = if (q.difficulty == "HARD") NeonRed else if (q.difficulty == "MEDIUM") SaaSBlue else NeonGreen,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = q.questionText,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                            }
                            IconButton(onClick = { viewModel.deleteQuestion(q) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = NeonRed.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // Screen 2a: Browse Courses list
            else -> {
                item {
                    Text(
                        text = "COURSES IN SHELL",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SaaSBlue, RoundedCornerShape(12.dp))
                            .clickable { viewModel.showAddCourseDialog = true }
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SaaSBlue.copy(alpha = 0.05f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Default.Add, tint = SaaSBlue, contentDescription = "Add Course")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ADD NEW COURSE",
                                color = SaaSBlue,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                items(courses) { course ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SaaSCard, RoundedCornerShape(12.dp))
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                            .clickable { viewModel.selectCourse(course.id) }
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(
                                text = course.title.uppercase(),
                                color = SaaSBlue,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = course.description,
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- 3. PURGES SCREEN ---
@Composable
fun PurgesScreen(viewModel: PurgeViewModel) {
    val activePurge by viewModel.activePurgeTest.collectAsState()
    val allPurges by viewModel.purgeTests.collectAsState()
    val isGrading by viewModel.isGrading.collectAsState()

    var activeQuizAnswers = remember { mutableStateListOf<String>() }

    // If an active quiz starts, initialize the answers container
    LaunchedEffect(activePurge) {
        activePurge?.let {
            if (it.started && activeQuizAnswers.isEmpty()) {
                val itemRefs: List<String> = Gson().fromJson(it.questionsJson, object : TypeToken<List<String>>() {}.type)
                activeQuizAnswers.clear()
                activeQuizAnswers.addAll(List(itemRefs.size) { "" })
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "PURGE ENGINE CONTROL",
                color = SaaSBlue,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        val purge = activePurge
        if (purge == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaaSCard, RoundedCornerShape(12.dp))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "WAITING FOR NEXT PURGE COMMAND",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "A random purge event will trigger this week. Keep notifications active.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Purge Active
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaaSCard, RoundedCornerShape(12.dp))
                        .border(1.dp, NeonRed, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "☢ PURGE THREAT ACTIVE",
                        color = NeonRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var tickingTimeStr by remember { mutableStateOf("") }
                    LaunchedEffect(purge.expiresDate) {
                        while (true) {
                            val diff = purge.expiresDate - System.currentTimeMillis()
                            if (diff <= 0) {
                                tickingTimeStr = "EXPIRED"
                                break
                            }
                            val seconds = (diff / 1000) % 60
                            val minutes = (diff / (1000 * 60)) % 60
                            val hours = (diff / (1000 * 60 * 60))
                            tickingTimeStr = String.format("%02d : %02d : %02d SEC", hours, minutes, seconds)
                            delay(1000)
                        }
                    }

                    Text(
                        text = "TIME REMAINING: $tickingTimeStr",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!purge.started) {
                        Text(
                            text = "WARNING: Initiating the Purge generates a 10-question quiz immediately. The timer CANNOT be paused. If closed, the countdown expires in background.",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.startPurge(purge.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "INITIALIZE PURGE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        // Purge started: Quiz items
                        Text(
                            text = "QUIZ INTERFACE",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val refs: List<String> = remember(purge.questionsJson) {
                            Gson().fromJson(purge.questionsJson, object : TypeToken<List<String>>() {}.type)
                        }

                        refs.forEachIndexed { index, ref ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .background(Black)
                                    .border(1.dp, Color.DarkGray, RoundedCornerShape(4.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "QUESTION ${index + 1}:",
                                        color = NeonRed,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Solve conceptual transformation ID: $ref. Outline your mathematical reasoning and final algorithmic solution below.",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = activeQuizAnswers.getOrNull(index) ?: "",
                                        onValueChange = { activeQuizAnswers[index] = it },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = SaaSBlue,
                                            unfocusedBorderColor = Color.DarkGray,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        placeholder = { Text("Write implementation outline...", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 3
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isGrading) {
                            CircularProgressIndicator(color = NeonRed)
                        } else {
                            Button(
                                onClick = { viewModel.submitPurgeTest(purge.id, activeQuizAnswers.toList()) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "TRANSMIT SOLUTIONS",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // Historial logs
        if (allPurges.isNotEmpty()) {
            item {
                Text(
                    text = "PURGE LOG HISTORY",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            items(allPurges) { item ->
                val dateStr = remember(item.generatedDate) { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.generatedDate)) }
                val scoreColor = if (item.score >= 70) NeonGreen else NeonRed
                val textLabel = if (item.completed) "SCORE: ${item.score}/100" else "MISSED/EXPIRED"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaaSCard, RoundedCornerShape(12.dp))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PURGE CMD: $dateStr",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (item.completed) "Status: Finished" else "Status: Closed",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = textLabel,
                            color = scoreColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// --- 4. WEEKLY & MONTHLY TESTS SCREEN ---
@Composable
fun TestsScreen(viewModel: TestViewModel, isMonthly: Boolean) {
    val activeQuiz by viewModel.activeQuiz.collectAsState()
    val isGrading by viewModel.isGrading.collectAsState()

    var testCompletedFeedback by remember { mutableStateOf<String?>(null) }
    var testCompletedScore by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val titleText = if (isMonthly) "MONTHLY CHALLENGE SHELL" else "WEEKLY INTERVAL EXAM"
        val subtitle = if (isMonthly) "Harder questions. More variations. Double points." else "Every 7 days. Mixed topics and failed questions."

        item {
            Text(
                text = titleText,
                color = SaaSBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        val session = activeQuiz
        if (session == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaaSCard, RoundedCornerShape(12.dp))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TEST AVAILABLE",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                testCompletedFeedback = null
                                viewModel.startTest(isMonthly)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SaaSBlue)
                        ) {
                            Text("INITIALIZE TEST", color = White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            testCompletedFeedback?.let { feedback ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SaaSCard, RoundedCornerShape(12.dp))
                            .border(1.dp, if (testCompletedScore >= 70) NeonGreen else NeonRed, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "RESULTS TRANSMITTED",
                            color = if (testCompletedScore >= 70) NeonGreen else NeonRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "SCORE: $testCompletedScore / 100",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = feedback,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        } else {
            // Ticking Test Interface
            items(session.questions.size) { index ->
                val q = session.questions[index]
                var answerText by remember { mutableStateOf("") }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaaSCard, RoundedCornerShape(12.dp))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "QUESTION ${index + 1}",
                                color = SaaSBlue,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${q.points} PTS",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = q.text,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = answerText,
                            onValueChange = {
                                answerText = it
                                viewModel.submitAnswer(index, it)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SaaSBlue,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            placeholder = { Text("Write outline / mathematical proof...", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                if (isGrading) {
                    CircularProgressIndicator(color = SaaSBlue)
                } else {
                    Button(
                        onClick = {
                            viewModel.completeQuiz { score, fb ->
                                testCompletedScore = score
                                testCompletedFeedback = fb
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SaaSBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "TRANSMIT SOLUTIONS",
                            color = White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// --- 5. STREAKS SCREEN ---
@Composable
fun StreaksScreen(viewModel: DashboardViewModel) {
    val streak by viewModel.streakState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .border(2.dp, SaaSBlue, RoundedCornerShape(75.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🔥", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${streak?.currentDaily ?: 0} DAYS",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "STREAK STATISTICS",
                    color = SaaSBlue,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                StreakStatRow(label = "LONGEST DAILY STREAK", value = "${streak?.longestDaily ?: 0} Days")
                Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))
                StreakStatRow(label = "WEEKLY TEST STREAK", value = "${streak?.currentWeekly ?: 0} Weeks")
                Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))
                StreakStatRow(label = "MONTHLY TEST STREAK", value = "${streak?.currentMonthly ?: 0} Months")
                Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))
                StreakStatRow(label = "COMPLETED PURGES", value = "${streak?.currentPurge ?: 0}")
                Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))
                StreakStatRow(label = "MISSED PURGE EVENTS", value = "${streak?.missedPurges ?: 0}", isAlert = true)
            }
        }
    }
}

@Composable
fun StreakStatRow(label: String, value: String, isAlert: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = if (isAlert) NeonRed else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// --- 6. APP LOCK SCREEN ---
@Composable
fun AppLockScreen(viewModel: AppLockViewModel) {
    val lockedList by viewModel.lockedApps.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val context = LocalContext.current

    var packageNameInput by remember { mutableStateOf("") }
    var appNameInput by remember { mutableStateOf("") }
    var durationDaysInput by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("SELECT APP TO LOCK", color = Color.White, fontFamily = FontFamily.Monospace) },
            text = {
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(installedApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    appNameInput = app.name
                                    packageNameInput = app.packageName
                                    showAppPicker = false
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            app.icon?.let { iconDrawable ->
                                androidx.compose.foundation.Image(
                                    bitmap = iconDrawable.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(app.packageName, color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) {
                    Text("CANCEL", color = SaaSBlue, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = SaaSCard
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(1.dp, NeonRed, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "☣ CORE LOCK GENERATOR",
                    color = NeonRed,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showAppPicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SaaSBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SELECT FROM INSTALLED APPS", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = appNameInput,
                    onValueChange = { appNameInput = it },
                    label = { Text("App Name (e.g. Google Play Store)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SaaSBlue,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = packageNameInput,
                    onValueChange = { packageNameInput = it },
                    label = { Text("Package Name (e.g. com.android.vending)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SaaSBlue,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = durationDaysInput,
                    onValueChange = { durationDaysInput = it },
                    label = { Text("Lock Duration (Days)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SaaSBlue,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val days = durationDaysInput.toLongOrNull() ?: 1L
                        val expiry = System.currentTimeMillis() + (days * 24L * 60L * 60L * 1000L)
                        viewModel.lockApp(packageNameInput, appNameInput, expiry)
                        packageNameInput = ""
                        appNameInput = ""
                        durationDaysInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = appNameInput.isNotEmpty() && packageNameInput.isNotEmpty()
                ) {
                    Text("LOCK UNTIL EXPIRATION", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Kiosk / Device Owner enforcement status
        item {
            val isDeviceOwner = remember { com.example.selfapp.util.PanicManager.isDeviceOwner(context) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        if (isDeviceOwner) NeonGreen else NeonRed,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = if (isDeviceOwner) "✓ KIOSK / DEVICE OWNER ENFORCEMENT ACTIVE" else "⚠ DEVICE OWNER REQUIRED",
                        color = if (isDeviceOwner) NeonGreen else NeonRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isDeviceOwner)
                            "Locked apps are suspended natively at kernel/OS level. It is impossible to launch or bypass locked apps in Settings until expiration time."
                        else
                            "Device Owner is required for un-bypassable app locking. Provision Device Owner via ADB: adb shell dpm set-device-owner com.example.selfapp/.receiver.SelfDeviceAdminReceiver",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Spin Browser Exclusive Lock Card (Mandatory & Permanent under Device Owner)
        item {
            val diag = remember { com.example.selfapp.util.PanicManager.applyStrictSpinKioskPolicy(context) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        when (diag.state) {
                            com.example.selfapp.util.PanicManager.StrictKioskState.CONFIGURED_SUCCESS -> NeonGreen
                            com.example.selfapp.util.PanicManager.StrictKioskState.PARTIALLY_CONFIGURED -> Color(0xFFFFB300)
                            else -> NeonRed
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = "🌐 STRICT SPIN KIOSK ENFORCEMENT DIAGNOSTICS",
                    color = SaaSBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Device Owner Status:", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        if (diag.isDeviceOwner) "PROVISIONED" else "NOT PROVISIONED",
                        color = if (diag.isDeviceOwner) NeonGreen else NeonRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Spin Browser Installed:", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        if (diag.spinInstalled) "INSTALLED" else "MISSING",
                        color = if (diag.spinInstalled) NeonGreen else NeonRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Default Browser Set:", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        if (diag.persistentPreferredConfigured) "SUCCESS" else "FAILED",
                        color = if (diag.persistentPreferredConfigured) NeonGreen else NeonRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when (diag.state) {
                        com.example.selfapp.util.PanicManager.StrictKioskState.CONFIGURED_SUCCESS ->
                            "✓ STRICT KIOSK ACTIVE: All external web links resolve to Spin. Other browsers are suspended at OS level."
                        com.example.selfapp.util.PanicManager.StrictKioskState.FAILED_NOT_DEVICE_OWNER ->
                            "❌ REQUIRES DEVICE OWNER: Run ADB command: adb shell dpm set-device-owner com.example.selfapp/.receiver.SelfDeviceAdminReceiver"
                        com.example.selfapp.util.PanicManager.StrictKioskState.FAILED_SPIN_NOT_INSTALLED ->
                            "❌ REQUIRES SPIN BROWSER: Install 'Spin Safe Browser' (com.nationaleducationalmusic.spin) from Google Play Store."
                        else -> "⚠ KIOSK PARTIALLY CONFIGURED: ${diag.errorMessage ?: "Check device owner policies."}"
                    },
                    color = when (diag.state) {
                        com.example.selfapp.util.PanicManager.StrictKioskState.CONFIGURED_SUCCESS -> NeonGreen
                        com.example.selfapp.util.PanicManager.StrictKioskState.PARTIALLY_CONFIGURED -> Color(0xFFFFB300)
                        else -> NeonRed
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        item {
            Text(
                text = "LOCKED LIST (UNALTERABLE)",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        items(lockedList) { item ->
            val dateStr = remember(item.lockedUntil) { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.lockedUntil)) }
            val isExpired = System.currentTimeMillis() >= item.lockedUntil

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(1.dp, if (isExpired) Color.DarkGray else NeonRed, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = item.appName.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Expires: $dateStr",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (isExpired) {
                        Button(
                            onClick = { viewModel.unlockApp(item) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                        ) {
                            Text("UNLOCK", color = Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    } else {
                        Text(
                            text = "LOCKED",
                            color = NeonRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// --- 7. DNS BLOCKER SCREEN ---
@Composable
fun DnsBlockerScreen(viewModel: DnsBlockViewModel) {
    val blockedList by viewModel.blockedDomains.collectAsState()
    val context = LocalContext.current

    var domainInput by remember { mutableStateOf("") }
    var isVpnActive by remember { mutableStateOf(false) }

    // Start/Stop VPN Launcher
    val prepareVpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val intent = Intent(context, DnsBlockVpnService::class.java)
            context.startService(intent)
            isVpnActive = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Blocker status toggle
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(1.dp, SaaSBlue, RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VPN INTERCEPTOR SERVICE",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isVpnActive) "STATUS: ACTIVE" else "STATUS: STANDBY",
                            color = if (isVpnActive) NeonGreen else SaaSBlue,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Switch(
                        checked = isVpnActive,
                        onCheckedChange = { active ->
                            if (active) {
                                val intent = VpnService.prepare(context)
                                if (intent != null) {
                                    prepareVpnLauncher.launch(intent)
                                } else {
                                    context.startService(Intent(context, DnsBlockVpnService::class.java))
                                    isVpnActive = true
                                }
                            } else {
                                val intent = Intent(context, DnsBlockVpnService::class.java).apply {
                                    action = "ACTION_DISCONNECT"
                                }
                                context.startService(intent)
                                isVpnActive = false
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen, checkedTrackColor = Color(0xFF0F2415))
                    )
                }
            }
        }

        // Add domain card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "ADD DOMAIN TO BLOCKLIST",
                    color = SaaSBlue,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    placeholder = { Text("e.g. instagram.com", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SaaSBlue,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.addDomain(domainInput)
                        domainInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaaSBlue),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = domainInput.isNotEmpty()
                ) {
                    Text("COMMIT BLOCKED DOMAIN", color = White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Text(
                text = "BLOCKED DOMAINS LIST",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        items(blockedList) { item ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.domainName,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = { viewModel.removeDomain(item) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove domain",
                            tint = NeonRed
                        )
                    }
                }
            }
        }
    }
}

// --- 8. SETTINGS SCREEN ---
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settingsState by viewModel.settings.collectAsState()

    var provider by remember { mutableStateOf("GEMINI") }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var userPrompt by remember { mutableStateOf("") }

    var savedStatusVisible by remember { mutableStateOf(false) }

    LaunchedEffect(settingsState) {
        settingsState?.let {
            provider = it.provider
            apiKey = it.apiKey
            baseUrl = it.baseUrl
            modelName = it.modelName
            systemPrompt = it.customSystemPrompt
            userPrompt = it.customUserPrompt
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "OPEN SHELL LLM CONFIGURATION",
                color = SaaSBlue,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaaSCard, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Provider Selection
                Text("API PROVIDER", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("GEMINI", "OPENAI", "ANTHROPIC", "GROQ").forEach { prov ->
                        val isSel = provider == prov
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isSel) SaaSBlue.copy(alpha = 0.2f) else Black, RoundedCornerShape(4.dp))
                                .border(1.dp, if (isSel) SaaSBlue else Color.DarkGray, RoundedCornerShape(4.dp))
                                .clickable { provider = prov }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(prov, color = if (isSel) SaaSBlue else Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API KEY", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SaaSBlue,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Model name
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("MODEL NAME (e.g. gemini-1.5-flash)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SaaSBlue,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Custom Base URL
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("CUSTOM BASE URL (OPTIONAL)", color = Color.Gray) },
                    placeholder = { Text("https://my-proxy-url.com/v1", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SaaSBlue,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // System Prompt
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("SYSTEM PROMPT", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SaaSBlue,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                // User Prompt
                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    label = { Text("USER PROMPT TEMPLATE (Use {question})", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SaaSBlue,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val newSettings = LlmSettings(
                            provider = provider,
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            modelName = modelName,
                            customSystemPrompt = systemPrompt,
                            customUserPrompt = userPrompt
                        )
                        viewModel.saveSettings(newSettings)
                        savedStatusVisible = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaaSBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SAVE API CREDENTIALS", color = White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                AnimatedVisibility(visible = savedStatusVisible) {
                    Text(
                        text = "✓ API parameters updated in local Room database.",
                        color = NeonGreen,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        item {
            val context = LocalContext.current
            var selectedRingtoneName by remember { mutableStateOf<String?>(null) }

            // Load saved ringtone name
            LaunchedEffect(Unit) {
                val prefs = context.getSharedPreferences("self_app_prefs", Context.MODE_PRIVATE)
                val uriStr = prefs.getString("purge_ringtone_uri", null)
                if (uriStr != null) {
                    try {
                        val ringtone = RingtoneManager.getRingtone(context, Uri.parse(uriStr))
                        selectedRingtoneName = ringtone?.getTitle(context) ?: "Saved Ringtone"
                    } catch (e: Exception) {
                        selectedRingtoneName = "Saved Ringtone"
                    }
                }
            }

            val ringtonePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                    if (uri != null) {
                        context.getSharedPreferences("self_app_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("purge_ringtone_uri", uri.toString())
                            .apply()
                        try {
                            val ringtone = RingtoneManager.getRingtone(context, uri)
                            selectedRingtoneName = ringtone?.getTitle(context) ?: "Selected Ringtone"
                        } catch (e: Exception) {
                            selectedRingtoneName = "Selected Ringtone"
                        }
                    } else {
                        context.getSharedPreferences("self_app_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("purge_ringtone_uri", null)
                            .apply()
                        selectedRingtoneName = "Default Notification Sound"
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SaaSCard)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PURGE NOTIFICATION SETTINGS",
                        color = SaaSBlue,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Custom Ringtone",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = selectedRingtoneName ?: "Default Notification Sound",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Button(
                            onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Purge Ringtone")
                                    val currentUriStr = context.getSharedPreferences("self_app_prefs", Context.MODE_PRIVATE)
                                        .getString("purge_ringtone_uri", null)
                                    if (currentUriStr != null) {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                            currentUriStr.toUri())
                                    }
                                }
                                ringtonePickerLauncher.launch(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Text("SELECT", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
