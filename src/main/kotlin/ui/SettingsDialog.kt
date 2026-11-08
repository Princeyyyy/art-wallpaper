import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            modifier = Modifier.width(400.dp),
            shape = RoundedCornerShape(8.dp),
            color = ArtColors.Surface,
            contentColor = ArtColors.PaperWhite,
            elevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // â”€â”€ Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                Text(
                    "Settings",
                    style = MaterialTheme.typography.h6,
                    color = ArtColors.PaperWhite
                )

                Spacer(Modifier.height(20.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(20.dp))

                // â”€â”€ Section: System â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                SettingsSectionLabel("SYSTEM")
                Spacer(Modifier.height(12.dp))

                SettingsRow(
                    label = "Start with Windows",
                    description = "Launch automatically at login",
                    checked = isAutoStartEnabled,
                    onCheckedChange = { enabled ->
                        try {
                            if (enabled) WindowsAutoStart.enable() else WindowsAutoStart.disable()
                            isAutoStartEnabled = WindowsAutoStart.isEnabled()
                            logger.info("Auto-start ${if (enabled) "enabled" else "disabled"}")
                            onSettingsChange(
                                settings.copy(
                                    startWithSystem = enabled,
                                    hasEnabledAutoStart = enabled
                                )
                            )
                        } catch (e: Exception) {
                            logger.error("Failed to ${if (enabled) "enable" else "disable"} auto-start", e)
                            isAutoStartEnabled = WindowsAutoStart.isEnabled()
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(16.dp))

                // â”€â”€ Section: Wallpaper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                SettingsSectionLabel("WALLPAPER")
                Spacer(Modifier.height(12.dp))

                TimePickerSection(
                    settings = settings,
                    onSettingsChange = onSettingsChange
                )

                Spacer(Modifier.height(16.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(16.dp))

                // â”€â”€ Section: Notifications â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                SettingsSectionLabel("NOTIFICATIONS")
                Spacer(Modifier.height(12.dp))

                SettingsRow(
                    label = "Wallpaper change alerts",
                    description = "Show a notification when a new artwork is set",
                    checked = isNotificationsEnabled,
                    onCheckedChange = { enabled ->
                        logger.info("Notifications ${if (enabled) "enabled" else "disabled"}")
                        onSettingsChange(settings.copy(notificationsEnabled = enabled))
                        isNotificationsEnabled = enabled
                    }
                )

                Spacer(Modifier.height(24.dp))

                // â”€â”€ Footer â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = ArtColors.GalleryGold,
                            contentColor = ArtColors.Canvas
                        ),
                        elevation = ButtonDefaults.elevation(0.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("CLOSE", style = MaterialTheme.typography.button)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.caption,
        color = ArtColors.GalleryGold,
        letterSpacing = 1.5.dp.value.sp
    )
}

@Composable
private fun SettingsRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.subtitle2,
                color = ArtColors.PaperWhite
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.caption,
                color = ArtColors.PaperMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ArtColors.GalleryGold,
                checkedTrackColor = ArtColors.GalleryGold.copy(alpha = 0.4f),
                uncheckedThumbColor = ArtColors.PaperMuted,
                uncheckedTrackColor = ArtColors.PaperMuted.copy(alpha = 0.3f)
            )
        )
    }
}

// â”€â”€ Time Picker â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
fun TimePickerSection(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
) {
    val currentSettings by Settings.currentSettings.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    val timeString = remember(currentSettings.updateTimeHour, currentSettings.updateTimeMinute) {
        "%02d:%02d".format(currentSettings.updateTimeHour, currentSettings.updateTimeMinute)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                "Daily update time",
                style = MaterialTheme.typography.subtitle2,
                color = ArtColors.PaperWhite
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Wallpaper refreshes automatically at this time",
                style = MaterialTheme.typography.caption,
                color = ArtColors.PaperMuted
            )
        }
        TextButton(
            onClick = { showPicker = true },
            colors = ButtonDefaults.textButtonColors(contentColor = ArtColors.GalleryGold)
        ) {
            Text(timeString, style = MaterialTheme.typography.subtitle1)
        }
    }

    if (showPicker) {
        TimePickerDialog(
            initialHour = currentSettings.updateTimeHour,
            initialMinute = currentSettings.updateTimeMinute,
            onTimeSelected = { hour, minute ->
                onSettingsChange(
                    settings.copy(
                        updateTimeHour = hour,
                        updateTimeMinute = minute,
                        hasSetUpdateTime = true
                    )
                )
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
            shape = RoundedCornerShape(8.dp),
            color = ArtColors.Surface,
            contentColor = ArtColors.PaperWhite,
            elevation = 12.dp,
            modifier = Modifier.width(300.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "Update time",
                    style = MaterialTheme.typography.h6,
                    color = ArtColors.PaperWhite
                )

                var selectedHour by remember { mutableStateOf(initialHour) }
                var selectedMinute by remember { mutableStateOf(initialMinute) }

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour
                    TimeSpinner(
                        label = "HH",
                        value = selectedHour,
                        onDecrement = { selectedHour = (selectedHour - 1).coerceIn(0, 23) },
                        onIncrement = { selectedHour = (selectedHour + 1).coerceIn(0, 23) },
                        format = "%02d"
                    )

                    Text(
                        ":",
                        style = MaterialTheme.typography.h5,
                        color = ArtColors.GalleryGold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Minute
                    TimeSpinner(
                        label = "MM",
                        value = selectedMinute,
                        onDecrement = { selectedMinute = (selectedMinute - 5).coerceIn(0, 55) },
                        onIncrement = { selectedMinute = (selectedMinute + 5).coerceIn(0, 55) },
                        format = "%02d"
                    )
                }

                Divider(color = ArtColors.DividerLine)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = ArtColors.PaperMuted)
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.button)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onTimeSelected(selectedHour, selectedMinute)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = ArtColors.GalleryGold,
                            contentColor = ArtColors.Canvas
                        ),
                        elevation = ButtonDefaults.elevation(0.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("SET", style = MaterialTheme.typography.button)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSpinner(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    format: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.caption, color = ArtColors.PaperMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement, modifier = Modifier.size(32.dp)) {
                Text("âˆ’", color = ArtColors.PaperMuted, style = MaterialTheme.typography.h6)
            }
            Text(
                format.format(value),
                style = MaterialTheme.typography.h5,
                color = ArtColors.PaperWhite,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = onIncrement, modifier = Modifier.size(32.dp)) {
                Text("+", color = ArtColors.PaperMuted, style = MaterialTheme.typography.h6)
            }
        }
    }
}
