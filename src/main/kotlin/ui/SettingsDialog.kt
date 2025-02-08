import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.slf4j.LoggerFactory

@Composable
fun SettingsDialog(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onDismiss: () -> Unit
) {
    var isAutoStartEnabled by remember { mutableStateOf(WindowsAutoStart.isEnabled()) }
    var isNotificationsEnabled by remember { mutableStateOf(settings.notificationsEnabled) }
    val logger = LoggerFactory.getLogger("SettingsDialog")
    
    LaunchedEffect(Unit) {
        // Check initial auto-start state
        isAutoStartEnabled = WindowsAutoStart.isEnabled()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(400.dp).padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.h6
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Start with Windows")
                        Text(
                            "Launch automatically at system startup",
                            style = MaterialTheme.typography.caption
                        )
                    }
                    Switch(
                        checked = isAutoStartEnabled,
                        onCheckedChange = { enabled ->
                            try {
                                if (enabled) WindowsAutoStart.enable() else WindowsAutoStart.disable()
                                isAutoStartEnabled = WindowsAutoStart.isEnabled()
                                logger.info("Auto-start ${if (enabled) "enabled" else "disabled"}")
                                onSettingsChange(settings.copy(
                                    startWithSystem = enabled,
                                    hasEnabledAutoStart = enabled
                                ))
                            } catch (e: Exception) {
                                logger.error("Failed to ${if (enabled) "enable" else "disable"} auto-start", e)
                                isAutoStartEnabled = WindowsAutoStart.isEnabled()
                            }
                        }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Show Notifications")
                        Text(
                            "Display notifications when wallpaper changes",
                            style = MaterialTheme.typography.caption
                        )
                    }
                    Switch(
                        checked = isNotificationsEnabled,
                        onCheckedChange = { enabled ->
                            logger.info("Notifications ${if (enabled) "enabled" else "disabled"}")
                            onSettingsChange(settings.copy(notificationsEnabled = enabled))
                            isNotificationsEnabled = enabled
                        }
                    )
                }
                
                TimePickerSection(
                    settings = settings,
                    onSettingsChange = onSettingsChange
                )
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun TimePickerSection(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
) {
    val currentSettings by Settings.currentSettings.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    val timeString = remember(currentSettings.updateTimeHour, currentSettings.updateTimeMinute) {
        String.format("%02d:%02d", currentSettings.updateTimeHour, currentSettings.updateTimeMinute)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Update Time")
            Text(
                "Daily wallpaper update time",
                style = MaterialTheme.typography.caption
            )
        }
        TextButton(onClick = { showPicker = true }) {
            Text(timeString)
        }
    }

    if (showPicker) {
        TimePickerDialog(
            initialHour = currentSettings.updateTimeHour,
            initialMinute = currentSettings.updateTimeMinute,
            onTimeSelected = { hour, minute ->
                onSettingsChange(settings.copy(
                    updateTimeHour = hour,
                    updateTimeMinute = minute,
                    hasSetUpdateTime = true
                ))
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp,
            modifier = Modifier.width(320.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Select Update Time", style = MaterialTheme.typography.h6)
                
                var selectedHour by remember { mutableStateOf(initialHour) }
                var selectedMinute by remember { mutableStateOf(initialMinute) }

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hour")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { 
                                selectedHour = (selectedHour - 1).coerceIn(0, 23)
                            }) {
                                Text("-")
                            }
                            Text(
                                String.format("%02d", selectedHour),
                                modifier = Modifier.width(40.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(onClick = { 
                                selectedHour = (selectedHour + 1).coerceIn(0, 23)
                            }) {
                                Text("+")
                            }
                        }
                    }

                    // Separator with adjusted padding
                    Column(
                        modifier = Modifier.padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(":")
                    }

                    // Minute picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Minute")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { 
                                selectedMinute = (selectedMinute - 5).coerceIn(0, 55)
                            }) {
                                Text("-")
                            }
                            Text(
                                String.format("%02d", selectedMinute),
                                modifier = Modifier.width(40.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(onClick = { 
                                selectedMinute = (selectedMinute + 5).coerceIn(0, 55)
                            }) {
                                Text("+")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onTimeSelected(selectedHour, selectedMinute)
                            onDismiss()
                        }
                    ) {
                        Text("Set")
                    }
                }
            }
        }
    }
} 