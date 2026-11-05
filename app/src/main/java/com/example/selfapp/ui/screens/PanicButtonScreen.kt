package com.example.selfapp.ui.screens

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.selfapp.ui.theme.*
import com.example.selfapp.ui.viewmodel.DashboardViewModel
import com.example.selfapp.ui.viewmodel.PanicViewModel
import com.example.selfapp.util.PanicManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PanicButtonScreen(viewModel: PanicViewModel, dashboardViewModel: DashboardViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity

    val panicConfig by viewModel.panicConfig.collectAsState()
    val panicLogs by viewModel.panicLogs.collectAsState()
    val progressList by dashboardViewModel.courseProgressList.collectAsState()
    val points by dashboardViewModel.totalPoints.collectAsState()
    val streak by dashboardViewModel.streakState.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val activeNotifications by viewModel.activeNotifications.collectAsState()
    val isDeviceOwner by viewModel.isDeviceOwner.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()

    var durationMinutes by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf(setOf<String>()) }
    var showAppPicker by remember { mutableStateOf(false) }

    // Schedule state
    var startHourText by remember { mutableStateOf("09") }
    var startMinuteText by remember { mutableStateOf("00") }
    var endHourText by remember { mutableStateOf("17") }
    var endMinuteText by remember { mutableStateOf("00") }
    var scheduleEnabled by remember { mutableStateOf(false) }

    // Sync state from persisted config
    LaunchedEffect(panicConfig) {
        panicConfig?.let { cfg ->
            if (selectedPackages.isEmpty() && cfg.allowedPackagesJson.isNotEmpty()) {
                selectedPackages = PanicManager.parseAllowedPackages(cfg.allowedPackagesJson).toSet()
            }
            if (cfg.scheduledStartHour >= 0) {
                startHourText = String.format(Locale.getDefault(), "%02d", cfg.scheduledStartHour)
                startMinuteText = String.format(Locale.getDefault(), "%02d", cfg.scheduledStartMinute)
                endHourText = String.format(Locale.getDefault(), "%02d", cfg.scheduledEndHour)
                endMinuteText = String.format(Locale.getDefault(), "%02d", cfg.scheduledEndMinute)
            }
            scheduleEnabled = cfg.isScheduleEnabled
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshStatus()
    }

    val isActive = panicConfig?.isActive == true && (panicConfig?.expiresAt ?: 0L) > System.currentTimeMillis()

    // Countdown calculation
    var remainingSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(isActive, panicConfig?.expiresAt) {
        if (isActive) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            while (true) {
                val expires = panicConfig?.expiresAt ?: 0L
                val diff = (expires - System.currentTimeMillis()) / 1000L
                if (diff <= 0) {
                    remainingSeconds = 0
                    viewModel.deactivatePanic()
                    break
                } else {
                    remainingSeconds = diff
                }
                delay(1000)
            }
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("SELECT ALLOWED APPS", color = Color.White, fontFamily = FontFamily.Monospace) },
            text = {
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(installedApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedPackages.contains(app.packageName)) {
                                        selectedPackages = selectedPackages - app.packageName
                                    } else if (selectedPackages.size < 4) {
                                        selectedPackages = selectedPackages + app.packageName
                                    }
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            app.icon?.let {
                                Image(
                                    bitmap = it.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(app.name, color = Color.White, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = selectedPackages.contains(app.packageName),
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = SaaSBlue)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAppPicker = false }, colors = ButtonDefaults.buttonColors(containerColor = SaaSBlue)) {
                    Text("DONE")
                }
            },
            containerColor = SaaSCard
        )
    }

    if (isActive) {
        // --- SCREENSAVER / ACTIVE PANIC LAYOUT ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "DISCIPLINE SHELL ACTIVE",
                    color = SaaSBlue,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Countdown Timer
                val hours = remainingSeconds / 3600
                val minutes = (remainingSeconds % 3600) / 60
                val seconds = remainingSeconds % 60
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds),
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBox(label = "POINTS", value = points.toString())
                    StatBox(label = "STREAK", value = "${streak?.currentDaily ?: 0}D")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Allowed Apps Launch Shortcuts (Lock Task Enabled)
                val allowedList = PanicManager.parseAllowedPackages(panicConfig?.allowedPackagesJson ?: "[]")
                if (allowedList.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SaaSCard.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .border(1.dp, SaaSBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text("ALLOWED APPS (TAP TO LAUNCH)", color = SaaSBlue, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            allowedList.forEach { pkg ->
                                val app = installedApps.find { it.packageName == pkg }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            }
                                        }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(Black, RoundedCornerShape(8.dp))
                                            .border(1.dp, SaaSBlue, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        app?.icon?.let {
                                            Image(bitmap = it.toBitmap().asImageBitmap(), contentDescription = null, modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = app?.name?.take(8) ?: pkg.takeLast(8),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Notifications Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(SaaSCard.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text("ALLOWED NOTIFICATIONS", color = SaaSBlue, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(activeNotifications) { notification ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(notification.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(notification.text, color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Course Progress Bars
                progressList.take(2).forEach { progress ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(progress.course.title.uppercase(), color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("${(progress.percentage * 100).toInt()}%", color = SaaSBlue, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.DarkGray, CircleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.percentage)
                                    .fillMaxHeight()
                                    .background(SaaSBlue, CircleShape)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "FOCUS ON THE MISSION. NO DISTRACTIONS PERMITTED.",
                    color = Color.DarkGray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // --- CONFIGURATION & STATUS UI ---
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Provisioning & System Readiness Panel
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaaSCard, RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (isDeviceOwner) SaaSBlue else NeonRed.copy(alpha = 0.7f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROVISIONING & SYSTEM STATUS",
                            color = SaaSBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(onClick = { viewModel.refreshStatus() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = SaaSBlue)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Device Owner Status:", color = Color.LightGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Box(
                            modifier = Modifier
                                .background(if (isDeviceOwner) NeonGreen.copy(alpha = 0.2f) else NeonRed.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, if (isDeviceOwner) NeonGreen else NeonRed, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (isDeviceOwner) "YES" else "NO",
                                color = if (isDeviceOwner) NeonGreen else NeonRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Accessibility Service:", color = Color.LightGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Box(
                            modifier = Modifier
                                .background(if (isAccessibilityEnabled) NeonGreen.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .border(1.dp, if (isAccessibilityEnabled) NeonGreen else Color.DarkGray, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (isAccessibilityEnabled) "ON" else "OFF",
                                color = if (isAccessibilityEnabled) NeonGreen else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = when {
                            isDeviceOwner -> "Panic Mode Status: READY (Kiosk Enforcement via Lock Task)"
                            isAccessibilityEnabled -> "Panic Mode Status: LIMITED (Accessibility Overlay Mode)"
                            else -> "Panic Mode Status: UNPROVISIONED (Device Owner Required)"
                        },
                        color = when {
                            isDeviceOwner -> NeonGreen
                            isAccessibilityEnabled -> Color(0xFFFFB300)
                            else -> NeonRed
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    if (!isDeviceOwner) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "To enable core Device Owner Lock Task enforcement, run ADB command:",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Black)
                                .border(1.dp, Color.DarkGray, RoundedCornerShape(4.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "adb shell dpm set-device-owner com.example.selfapp/.receiver.SelfDeviceAdminReceiver",
                                color = SaaSBlue,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // 2. Scheduled Panic Mode Configuration
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SCHEDULED PANIC PERIOD",
                            color = SaaSBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Switch(
                            checked = scheduleEnabled,
                            onCheckedChange = { scheduleEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = SaaSBlue)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Set daily automatic start and end times for Panic Lock enforcement.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("START TIME", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Row {
                                OutlinedTextField(
                                    value = startHourText,
                                    onValueChange = { if (it.length <= 2) startHourText = it },
                                    label = { Text("HH", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                OutlinedTextField(
                                    value = startMinuteText,
                                    onValueChange = { if (it.length <= 2) startMinuteText = it },
                                    label = { Text("MM", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("END TIME", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Row {
                                OutlinedTextField(
                                    value = endHourText,
                                    onValueChange = { if (it.length <= 2) endHourText = it },
                                    label = { Text("HH", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                OutlinedTextField(
                                    value = endMinuteText,
                                    onValueChange = { if (it.length <= 2) endMinuteText = it },
                                    label = { Text("MM", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val sh = startHourText.toIntOrNull() ?: 9
                            val sm = startMinuteText.toIntOrNull() ?: 0
                            val eh = endHourText.toIntOrNull() ?: 17
                            val em = endMinuteText.toIntOrNull() ?: 0
                            viewModel.savePanicSchedule(sh, sm, eh, em, scheduleEnabled, selectedPackages.toList())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SaaSBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SAVE SCHEDULE CONFIG", color = White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // 3. Quick Select & Immediate Panic Activation
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaaSCard, RoundedCornerShape(12.dp))
                        .border(1.dp, SaaSBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        text = "IMMEDIATE PANIC LOCK",
                        color = SaaSBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Trigger an instant focused session with Lock Task Mode enforcement.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("QUICK SELECT (MINS)", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 3, 5, 10, 15).forEach { mins ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(SaaSCard, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (durationMinutes == mins.toString()) SaaSBlue else Color.DarkGray, RoundedCornerShape(8.dp))
                                    .clickable { durationMinutes = mins.toString() }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(mins.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = durationMinutes,
                        onValueChange = { durationMinutes = it },
                        label = { Text("Custom Duration (Minutes)", color = Color.Gray) },
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

                    Text("TARGET FOCUS APPS (MAX 4)", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("Selected applications will remain accessible during Lock Task Mode.", color = Color.DarkGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedPackages.forEach { pkg ->
                            val app = installedApps.find { it.packageName == pkg }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .border(1.dp, SaaSBlue, RoundedCornerShape(8.dp))
                                    .clickable { selectedPackages = selectedPackages - pkg },
                                contentAlignment = Alignment.Center
                            ) {
                                app?.icon?.let {
                                    Image(bitmap = it.toBitmap().asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                        if (selectedPackages.size < 4) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                                    .clickable { showAppPicker = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val mins = durationMinutes.toIntOrNull() ?: 1
                            viewModel.triggerPanic(mins, selectedPackages.toList())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SaaSBlue),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = durationMinutes.isNotEmpty()
                    ) {
                        Text("ACTIVATE PANIC LOCK", color = White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // 4. History Log
            item {
                Text(
                    text = "PANIC LOG HISTORY",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(panicLogs) { log ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(log.timestamp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaaSCard, RoundedCornerShape(12.dp))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "TRIGGERED: $dateStr", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Text(text = "Duration: ${log.durationMinutes} Minutes", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SaaSBlue.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = SaaSBlue, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
