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
                            if (enabled) WindowsAutoStart.enable() else WindowsAutoStart.disable()
                            isAutoStartEnabled = WindowsAutoStart.isEnabled()
                            logger.info("Auto-start ${if (enabled) "enabled" else "disabled"}")
                            onSettingsChange(settings.copy(
                                startWithSystem = enabled,
                                hasEnabledAutoStart = enabled
                            ))
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
                
                TimePickerRow(
                    hour = if (settings.hasSetUpdateTime) settings.updateTimeHour else 7,
                    minute = if (settings.hasSetUpdateTime) settings.updateTimeMinute else 0,
                    onTimeChange = { hour, minute ->
                        logger.info("Update time changed to $hour:$minute")
                        val newSettings = settings.copy(
                            updateTimeHour = hour,
                            updateTimeMinute = minute,
                            hasSetUpdateTime = true
                        )
                        onSettingsChange(newSettings)
                    }
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
fun TimePickerRow(
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val timeString = remember(hour, minute) {
        String.format("%02d:%02d", hour, minute)
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
        Dialog(onDismissRequest = { showPicker = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                elevation = 8.dp,
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Select Update Time", style = MaterialTheme.typography.h6)
                    
                    var selectedHour by remember { mutableStateOf(hour) }
                    var selectedMinute by remember { mutableStateOf(minute) }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
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

                        Text(":")

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
                        TextButton(onClick = { showPicker = false }) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onTimeChange(selectedHour, selectedMinute)
                                showPicker = false
                            }
                        ) {
                            Text("Set")
                        }
                    }
                }
            }
        }
    }
} 