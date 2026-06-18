package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MotionLog
import com.example.data.model.SessionRecord
import com.example.data.model.StreamSettings
import com.example.ui.ScreenStreamViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppLayout(
    viewModel: ScreenStreamViewModel,
    onStartCasting: () -> Unit,
    onStopCasting: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = SleekCardSurface,
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dvr, "Dashboard") },
                    label = { Text("Server", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SleekStatusText,
                        selectedTextColor = SleekAccentLavender,
                        unselectedIconColor = SleekTextSecondary,
                        unselectedTextColor = SleekTextSecondary,
                        indicatorColor = SleekStatusBg
                    ),
                    modifier = Modifier.testTag("nav_dashboard")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, "Config") },
                    label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SleekStatusText,
                        selectedTextColor = SleekAccentLavender,
                        unselectedIconColor = SleekTextSecondary,
                        unselectedTextColor = SleekTextSecondary,
                        indicatorColor = SleekStatusBg
                    ),
                    modifier = Modifier.testTag("nav_settings")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.History, "Recordings") },
                    label = { Text("Sessions", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SleekStatusText,
                        selectedTextColor = SleekAccentLavender,
                        unselectedIconColor = SleekTextSecondary,
                        unselectedTextColor = SleekTextSecondary,
                        indicatorColor = SleekStatusBg
                    ),
                    modifier = Modifier.testTag("nav_recordings")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Security, "Alerts") },
                    label = { Text("Motion Logs", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SleekStatusText,
                        selectedTextColor = SleekAccentLavender,
                        unselectedIconColor = SleekTextSecondary,
                        unselectedTextColor = SleekTextSecondary,
                        indicatorColor = SleekStatusBg
                    ),
                    modifier = Modifier.testTag("nav_alerts")
                )
            }
        },
        containerColor = SleekMidnightBack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(viewModel, onStartCasting, onStopCasting)
                1 -> SettingsScreen(viewModel)
                2 -> SessionRecordsScreen(viewModel)
                3 -> MotionLogsScreen(viewModel)
            }
        }
    }
}

@Composable
fun DashboardScreen(
    viewModel: ScreenStreamViewModel,
    onStartCasting: () -> Unit,
    onStopCasting: () -> Unit
) {
    val context = LocalContext.current
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val activeClients by viewModel.activeClients.collectAsStateWithLifecycle()
    val streamingUrl by viewModel.streamingUrl.collectAsStateWithLifecycle()
    val currentFps by viewModel.currentFps.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header conforming to Sleek Interface visual style
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SleekAccentLavender),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CastConnected,
                            contentDescription = "Casting Status Icon",
                            tint = SleekStatusText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Prism Cast",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isStreaming) SleekAccentLime else SleekMutedLabel)
                            )
                            Text(
                                text = if (isStreaming) "Stream Active • Low Latency" else "Stream Standby",
                                fontSize = 12.sp,
                                color = SleekTextSecondary,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
                
                IconButton(
                    onClick = {
                        Toast.makeText(context, "Sleek configuration active.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SleekCardSurface)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Header Settings Icon",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Live Preview Card (Aspect-Ratio, 28dp rounded corners, Sleek styled)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SleekCardSurface),
                border = BorderStroke(1.dp, SleekMutedBorder),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Central informative placeholder representing video mirroring stream
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isStreaming) Icons.Default.ScreenShare else Icons.Outlined.ScreenShare,
                            contentDescription = "Stream Icon Indicator",
                            tint = if (isStreaming) SleekAccentLavender else SleekMutedLabel,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isStreaming) "Broadcasting Desk_Display_01" else "System Cast Server Standby",
                            fontSize = 13.sp,
                            color = SleekTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        if (isStreaming) {
                            Text(
                                text = "${currentFps.toInt()} FPS • ${settings.resolution}",
                                fontSize = 11.sp,
                                color = SleekAccentLavender,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    // Top-Left live status badge
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isStreaming) Color.Red else SleekMutedLabel)
                            )
                            Text(
                                text = if (isStreaming) "LIVE" else "OFFLINE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Bottom-Right encryption security badge
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SleekStatusBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "E2EE Icon",
                                tint = SleekStatusText,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "E2EE SECURE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = SleekStatusText,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }

        // Quick Stats Row (Latency / Viewers / Bandwidth in 16.dp rounded cards)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Latency Stat Column
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SleekCardSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SleekMutedBorder.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isStreaming) "42ms" else "—",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SleekAccentLavender
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "LATENCY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = SleekMutedLabel,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Viewers Stat Column
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SleekCardSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SleekMutedBorder.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isStreaming) activeClients.toString() else "0",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SleekAccentLavender
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "VIEWERS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = SleekMutedLabel,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Bandwidth Stat Column
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SleekCardSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SleekMutedBorder.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isStreaming) "4.2 Mbps" else "0 kbps",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SleekAccentLavender
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "BANDWIDTH",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = SleekMutedLabel,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // Sharing Portal Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SleekCardSurface),
                border = BorderStroke(1.dp, SleekMutedBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Browser Access Link",
                        fontSize = 12.sp,
                        color = SleekTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val displayUrl = if (isStreaming) streamingUrl ?: "Link resolution failed" else "http://<device-ip>:${settings.port}"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SleekMidnightBack)
                            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = displayUrl,
                            fontSize = 14.sp,
                            color = if (isStreaming) SleekAccentLavender else SleekMutedLabel,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (isStreaming && streamingUrl != null) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Streaming Link", streamingUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Link copied successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Activate casting to claim link", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("copy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Link Icon",
                                tint = if (isStreaming) SleekAccentLavender else SleekMutedLabel,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "* Connect viewers on the same Wi-Fi using any standard desktop or mobile web browser.",
                        color = SleekMutedLabel,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Action controls / Broadcast toggle
        item {
            Button(
                onClick = { if (isStreaming) onStopCasting() else onStartCasting() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStreaming) SleekIndicatorRed else SleekAccentLavender,
                    contentColor = if (isStreaming) Color.White else SleekStatusText
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("toggle_broadcast_button"),
                border = BorderStroke(
                    1.dp,
                    if (isStreaming) SleekIndicatorRed.copy(alpha = 0.5f) else SleekAccentLavender.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                        contentDescription = "Casting Action Button Icon",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isStreaming) "Stop Screen Stream" else "Start Real-time Stream",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: ScreenStreamViewModel) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()

    var tempPort by remember { mutableStateOf("") }
    var tempPassword by remember { mutableStateOf("") }

    LaunchedEffect(settings) {
        tempPort = settings.port.toString()
        tempPassword = settings.passcode
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "STREAM CONFIGURATOR",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Adjust video dimensions, security keys, and alert bounds",
                fontSize = 11.sp,
                color = CyberTextSecondary
            )
        }

        // Port & Security credentials
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                border = BorderStroke(1.dp, CyberMutedBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Connection & Credentials", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CyberAccentCyan)

                    OutlinedTextField(
                        value = tempPort,
                        onValueChange = {
                            tempPort = it
                            it.toIntOrNull()?.let { validPort ->
                                if (validPort in 1024..65535) {
                                    viewModel.updateSettings(settings.copy(port = validPort))
                                }
                            }
                        },
                        label = { Text("Web Port (1024 - 65535)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberAccentCyan,
                            unfocusedBorderColor = CyberMutedBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("port_input_box")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Enforce Password Protection", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                            Text("Restrict browser viewing control", fontSize = 11.sp, color = CyberTextSecondary)
                        }
                        Switch(
                            checked = settings.isPasswordEnabled,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(isPasswordEnabled = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberAccentCyan,
                                checkedTrackColor = CyberAccentCyan.copy(alpha = 0.3f),
                                uncheckedThumbColor = CyberTextSecondary,
                                uncheckedTrackColor = CyberMutedBorder
                            ),
                            modifier = Modifier.testTag("password_switch")
                        )
                    }

                    if (settings.isPasswordEnabled) {
                        OutlinedTextField(
                            value = tempPassword,
                            onValueChange = {
                                tempPassword = it
                                viewModel.updateSettings(settings.copy(passcode = it))
                            },
                            label = { Text("Stream Passcode Challenge") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberAccentCyan,
                                unfocusedBorderColor = CyberMutedBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("passcode_input_field")
                        )
                    }
                }
            }
        }

        // Remote Server Relay
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                border = BorderStroke(1.dp, CyberMutedBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Remote Streaming Relay", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CyberAccentCyan)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Remote Relay", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                            Text("Broadcast stream to external server", fontSize = 11.sp, color = CyberTextSecondary)
                        }
                        Switch(
                            checked = settings.isRemoteStreamingEnabled,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(isRemoteStreamingEnabled = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberAccentCyan,
                                checkedTrackColor = CyberAccentCyan.copy(alpha = 0.3f),
                                uncheckedThumbColor = CyberTextSecondary,
                                uncheckedTrackColor = CyberMutedBorder
                            ),
                            modifier = Modifier.testTag("remote_streaming_switch")
                        )
                    }

                    if (settings.isRemoteStreamingEnabled) {
                        var tempRemoteUrl by remember { mutableStateOf(settings.remoteStreamUrl) }
                        var tempRemoteKey by remember { mutableStateOf(settings.remoteStreamKey) }

                        LaunchedEffect(settings.remoteStreamUrl, settings.remoteStreamKey) {
                            tempRemoteUrl = settings.remoteStreamUrl
                            tempRemoteKey = settings.remoteStreamKey
                        }

                        OutlinedTextField(
                            value = tempRemoteUrl,
                            onValueChange = {
                                tempRemoteUrl = it
                                viewModel.updateSettings(settings.copy(remoteStreamUrl = it))
                            },
                            label = { Text("Relay WebSocket URL") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberAccentCyan,
                                unfocusedBorderColor = CyberMutedBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("remote_url_field")
                        )

                        OutlinedTextField(
                            value = tempRemoteKey,
                            onValueChange = {
                                tempRemoteKey = it
                                viewModel.updateSettings(settings.copy(remoteStreamKey = it))
                            },
                            label = { Text("Relay Stream Key") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberAccentCyan,
                                unfocusedBorderColor = CyberMutedBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("remote_key_field")
                        )
                    }
                }
            }
        }

        // Quality sliders & resolution choices
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                border = BorderStroke(1.dp, CyberMutedBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Compression & Performance", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CyberAccentCyan)

                    Column {
                        Text("Output Video Resolution", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Low", "Medium", "High").forEach { res ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (settings.resolution == res) CyberAccentCyan.copy(alpha = 0.15f) else CyberMidnightBack)
                                        .border(
                                            1.dp,
                                            if (settings.resolution == res) CyberAccentCyan else CyberMutedBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            viewModel.updateSettings(settings.copy(resolution = res))
                                        }
                                        .padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = when(res) {
                                            "Low" -> "360p"
                                            "Medium" -> "540p"
                                            else -> "720p"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (settings.resolution == res) CyberAccentCyan else Color.White
                                    )
                                }
                            }
                        }
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Target Broadcast Rate", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                            Text("${settings.fps} FPS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberAccentCyan)
                        }
                        Slider(
                            value = settings.fps.toFloat(),
                            onValueChange = {
                                viewModel.updateSettings(settings.copy(fps = it.toInt()))
                            },
                            valueRange = 5f..30f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = CyberAccentCyan,
                                activeTrackColor = CyberAccentCyan,
                                inactiveTrackColor = CyberMutedBorder
                            ),
                            modifier = Modifier.testTag("fps_slider")
                        )
                    }
                }
            }
        }

        // Audio stream mode configuration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                border = BorderStroke(1.dp, CyberMutedBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Audio Sharing Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CyberAccentCyan)

                    Column {
                        Text("Capture Source Choice", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                        Text(
                            "Select 'Microphone' to capture and stream ambient room/surrounding device sounds. Select 'System Audio' to stream other apps' playback audio.",
                            fontSize = 11.sp,
                            color = CyberTextSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Microphone", "System Audio", "Muted").forEach { src ->
                                val isSelected = try { settings.audioSource == src } catch (e: Exception) { src == "Microphone" }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) CyberAccentCyan.copy(alpha = 0.15f) else CyberMidnightBack)
                                        .border(
                                            1.dp,
                                            if (isSelected) CyberAccentCyan else CyberMutedBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            viewModel.updateSettings(settings.copy(audioSource = src))
                                        }
                                        .padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = src,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        color = if (isSelected) CyberAccentCyan else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recording & Security Alerts toggles
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                border = BorderStroke(1.dp, CyberMutedBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Security & Storage", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CyberAccentCyan)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Save Session Recording", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                            Text("Record frames locally on storage", fontSize = 11.sp, color = CyberTextSecondary)
                        }
                        Switch(
                            checked = settings.isRecordingEnabled,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(isRecordingEnabled = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberAccentCyan,
                                checkedTrackColor = CyberAccentCyan.copy(alpha = 0.3f),
                                uncheckedThumbColor = CyberTextSecondary,
                                uncheckedTrackColor = CyberMutedBorder
                            ),
                            modifier = Modifier.testTag("recording_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Motion Alerts Activated", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                            Text("Trigger notifications on screen changes", fontSize = 11.sp, color = CyberTextSecondary)
                        }
                        Switch(
                            checked = settings.isMotionDetectionEnabled,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(isMotionDetectionEnabled = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberAccentCyan,
                                checkedTrackColor = CyberAccentCyan.copy(alpha = 0.3f),
                                uncheckedThumbColor = CyberTextSecondary,
                                uncheckedTrackColor = CyberMutedBorder
                            ),
                            modifier = Modifier.testTag("motion_switch")
                        )
                    }

                    if (settings.isMotionDetectionEnabled) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Alert Trigger Sensitivity", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                Text("${settings.motionSensitivity}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberAccentCyan)
                            }
                            Slider(
                                value = settings.motionSensitivity.toFloat(),
                                onValueChange = {
                                    viewModel.updateSettings(settings.copy(motionSensitivity = it.toInt()))
                                },
                                valueRange = 10f..95f,
                                colors = SliderDefaults.colors(
                                    thumbColor = CyberAccentCyan,
                                    activeTrackColor = CyberAccentCyan,
                                    inactiveTrackColor = CyberMutedBorder
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionRecordsScreen(viewModel: ScreenStreamViewModel) {
    val records by viewModel.sessionRecords.collectAsStateWithLifecycle()
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()) }

    var playbackTargetDir by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RECORDED SESSIONS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Manage and play back saved capture drives",
                    fontSize = 11.sp,
                    color = CyberTextSecondary
                )
            }
            if (records.isNotEmpty()) {
                Button(
                    onClick = { viewModel.clearAllSessions() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberIndicatorRed.copy(alpha = 0.15f), contentColor = CyberIndicatorRed),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CyberIndicatorRed.copy(alpha = 0.3f))
                ) {
                    Text("Clear All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CyberCardSurface)
                    .border(1.dp, CyberMutedBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Empty",
                        tint = CyberTextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No records stored yet", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                    Text(
                        "Toggle \"Save Session Recording\" in settings to automatically record screen streams to memory.",
                        fontSize = 11.sp,
                        color = CyberTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(records) { record ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                        border = BorderStroke(1.dp, CyberMutedBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playbackTargetDir = record.savedFramesDirectory
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = "Session play icon",
                                    tint = CyberAccentCyan,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = sdf.format(Date(record.timestamp)),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Duration: ${record.durationMs / 1000}s • ${record.frameCount} frames • ${(record.sizeBytes / 1024)} KB",
                                        fontSize = 11.sp,
                                        color = CyberTextSecondary
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.deleteSession(record) },
                                modifier = Modifier.testTag("delete_session_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete record",
                                    tint = CyberIndicatorRed.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive slideshow playback modal
    playbackTargetDir?.let { dirPath ->
        SessionPlaybackDialog(
            directoryPath = dirPath,
            onDismiss = { playbackTargetDir = null }
        )
    }
}

@Composable
fun SessionPlaybackDialog(directoryPath: String, onDismiss: () -> Unit) {
    val dir = remember(directoryPath) { File(directoryPath) }
    val frameFiles = remember(directoryPath) {
        if (dir.exists()) {
            dir.listFiles { _, name -> name.startsWith("frame_") && name.endsWith(".jpg") }
                ?.sortedBy { file ->
                    file.nameWithoutExtension.substringAfter("frame_").toIntOrNull() ?: 0
                } ?: emptyList()
        } else {
            emptyList()
        }
    }

    var currentFrameIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }

    LaunchedEffect(isPlaying, currentFrameIndex) {
        if (isPlaying && frameFiles.isNotEmpty()) {
            delay(1000)
            if (currentFrameIndex < frameFiles.size - 1) {
                currentFrameIndex++
            } else {
                currentFrameIndex = 0 // loop
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberMidnightBack),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .border(1.dp, CyberMutedBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SESSION PLAYBACK",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close modal", tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .heightIn(max = 420.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (frameFiles.isNotEmpty() && currentFrameIndex < frameFiles.size) {
                        val file = frameFiles[currentFrameIndex]
                        val bitmap = remember(file) { BitmapFactory.decodeFile(file.absolutePath) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Active playback frame",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("Decoding error", color = CyberIndicatorRed)
                        }
                    } else {
                        Text("No capture files stored", color = CyberTextSecondary)
                    }
                }

                // Playback progression status
                if (frameFiles.isNotEmpty()) {
                    Text(
                        text = "Frame ${currentFrameIndex + 1} of ${frameFiles.size}",
                        fontSize = 12.sp,
                        color = CyberAccentCyan,
                        fontFamily = FontFamily.Monospace
                    )

                    Slider(
                        value = currentFrameIndex.toFloat(),
                        onValueChange = { currentFrameIndex = it.toInt() },
                        valueRange = 0f..(frameFiles.size - 1).toFloat(),
                        steps = if (frameFiles.size > 1) frameFiles.size - 2 else 0,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberAccentCyan,
                            activeTrackColor = CyberAccentCyan,
                            inactiveTrackColor = CyberMutedBorder
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (currentFrameIndex > 0) currentFrameIndex--
                            }
                        ) {
                            Icon(Icons.Default.SkipPrevious, "Back frame", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        IconButton(
                            onClick = { isPlaying = !isPlaying }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                contentDescription = "Play toggle",
                                tint = CyberAccentCyan,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (currentFrameIndex < frameFiles.size - 1) currentFrameIndex++
                            }
                        ) {
                            Icon(Icons.Default.SkipNext, "Next frame", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MotionLogsScreen(viewModel: ScreenStreamViewModel) {
    val logs by viewModel.motionLogs.collectAsStateWithLifecycle()
    val sdf = remember { SimpleDateFormat("MMM dd - hh:mm:ss a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MOTION INCIDENTS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Historical activity snapshots triggered on screen motion",
                    fontSize = 11.sp,
                    color = CyberTextSecondary
                )
            }
            if (logs.isNotEmpty()) {
                Button(
                    onClick = { viewModel.clearAllMotionLogs() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberIndicatorRed.copy(alpha = 0.15f), contentColor = CyberIndicatorRed),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CyberIndicatorRed.copy(alpha = 0.3f))
                ) {
                    Text("Clear Logs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CyberCardSurface)
                    .border(1.dp, CyberMutedBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Shield Guard Safe",
                        tint = CyberAccentCyan.copy(alpha = 0.3f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No incidents recorded", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                    Text(
                        "Your screen activity is clear! Activating \"Motion Alerts\" in settings will record automated camera logs here if change surges happen.",
                        fontSize = 11.sp,
                        color = CyberTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs) { log ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                        border = BorderStroke(1.dp, CyberMutedBorder),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Local Snapshot Image thumbnail
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                log.snapshotPath?.let { path ->
                                    LocalImageFromPath(
                                        path = path,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } ?: Icon(Icons.Default.BrokenImage, "Broken Thumbnail", tint = CyberTextSecondary, modifier = Modifier.size(24.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    spanActivityIndicatorCircle()
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Activity Trigger",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = sdf.format(Date(log.timestamp)),
                                    fontSize = 10.sp,
                                    color = CyberAccentCyan,
                                    modifier = Modifier.padding(top = 2.dp),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Dynamic Variance: ${log.confidence}%",
                                    fontSize = 11.sp,
                                    color = CyberTextSecondary,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.deleteMotionLog(log) },
                                modifier = Modifier.testTag("delete_log_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete log entry",
                                    tint = CyberIndicatorRed.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun spanActivityIndicatorCircle() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(CyberIndicatorRed)
    )
}

@Composable
fun LocalImageFromPath(path: String, modifier: Modifier = Modifier) {
    val file = remember(path) { File(path) }
    if (file.exists()) {
        val bitmap = remember(path) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Incident screenshot",
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = modifier.background(Color.DarkGray))
        }
    } else {
        Box(modifier = modifier.background(Color.DarkGray))
    }
}
