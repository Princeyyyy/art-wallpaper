import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        val scale by animateFloatAsState(
            targetValue = if (visible) 1f else 0.95f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        )

        Surface(
            modifier = Modifier
                .width(420.dp)
                .scale(scale),
            shape = RoundedCornerShape(12.dp),
            color = ArtColors.Surface,
            contentColor = ArtColors.PaperWhite,
            elevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.h6,
                        color = ArtColors.PaperWhite
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text("x", color = ArtColors.PaperMuted, style = MaterialTheme.typography.subtitle1)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(16.dp))

                // Section: System
                SettingsSectionLabel("SYSTEM INTEGRATION")
                Spacer(Modifier.height(12.dp))

                SettingsRow(
                    label = "Start with Windows",
                    description = "Launch automatically into system tray at login",
                    checked = isAutoStartEnabled,
                    onCheckedChange = { enabled ->
                        try {
                            if (enabled) WindowsAutoStart.enable() else WindowsAutoStart.disable()
                            isAutoStartEnabled = WindowsAutoStart.isEnabled()
                            onSettingsChange(
                                settings.copy(
                                    startWithSystem = enabled,
                                    hasEnabledAutoStart = enabled
                                )
                            )
                        } catch (e: Exception) {
                            logger.error("Auto-start toggle error", e)
                            isAutoStartEnabled = WindowsAutoStart.isEnabled()
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(16.dp))

                // Section: Wallpaper Schedule
                SettingsSectionLabel("DAILY SCHEDULE")
                Spacer(Modifier.height(12.dp))

                TimePickerSection(
                    settings = settings,
                    onSettingsChange = onSettingsChange
                )

                Spacer(Modifier.height(16.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(16.dp))

                // Section: Notifications
                SettingsSectionLabel("NOTIFICATIONS")
                Spacer(Modifier.height(12.dp))

                SettingsRow(
                    label = "Wallpaper update alerts",
                    description = "Show a discreet notification when wallpaper rotates",
                    checked = isNotificationsEnabled,
                    onCheckedChange = { enabled ->
                        onSettingsChange(settings.copy(notificationsEnabled = enabled))
                        isNotificationsEnabled = enabled
                    }
                )

                Spacer(Modifier.height(16.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(16.dp))

                // Section: About
                SettingsSectionLabel("ABOUT")
                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource("icons/48x48.png"),
                        contentDescription = "ArtWallpaper Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Column {
                        Text(
                            "ArtWallpaper Manager v3.3.0",
                            style = MaterialTheme.typography.subtitle2,
                            color = ArtColors.PaperWhite
                        )
                        Text(
                            "Curated open-access masterpieces from The Met",
                            style = MaterialTheme.typography.caption,
                            color = ArtColors.PaperMuted
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    PulsingButton(
                        onClick = onDismiss
                    ) {
                        Text("DONE", style = MaterialTheme.typography.button)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption.copy(letterSpacing = 1.2.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        color = ArtColors.GalleryGold
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
                checkedTrackColor = ArtColors.GalleryGold.copy(alpha = 0.45f),
                uncheckedThumbColor = ArtColors.PaperMuted,
                uncheckedTrackColor = ArtColors.PaperMuted.copy(alpha = 0.3f)
            )
        )
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
        "%02d:%02d".format(currentSettings.updateTimeHour, currentSettings.updateTimeMinute)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                "Daily rotation time",
                style = MaterialTheme.typography.subtitle2,
                color = ArtColors.PaperWhite
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Wallpaper automatically changes at this time",
                style = MaterialTheme.typography.caption,
                color = ArtColors.PaperMuted
            )
        }
        TextButton(
            onClick = { showPicker = true },
            colors = ButtonDefaults.textButtonColors(contentColor = ArtColors.GalleryGold)
        ) {
            Text(
                timeString,
                style = MaterialTheme.typography.subtitle1.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            )
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
            shape = RoundedCornerShape(12.dp),
            color = ArtColors.Surface,
            contentColor = ArtColors.PaperWhite,
            elevation = 16.dp,
            modifier = Modifier.width(320.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    "Set Update Time",
                    style = MaterialTheme.typography.h6,
                    color = ArtColors.PaperWhite
                )

                var selectedHour by remember { mutableStateOf(initialHour) }
                var selectedMinute by remember { mutableStateOf(initialMinute) }

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeSpinner(
                        label = "HOUR",
                        value = selectedHour,
                        onDecrement = { selectedHour = (selectedHour - 1 + 24) % 24 },
                        onIncrement = { selectedHour = (selectedHour + 1) % 24 },
                        format = "%02d"
                    )

                    Text(
                        ":",
                        style = MaterialTheme.typography.h4,
                        color = ArtColors.GalleryGold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    TimeSpinner(
                        label = "MINUTE",
                        value = selectedMinute,
                        onDecrement = { selectedMinute = (selectedMinute - 5 + 60) % 60 },
                        onIncrement = { selectedMinute = (selectedMinute + 5) % 60 },
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
                        Text("CANCEL", style = MaterialTheme.typography.button)
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
                        Text("CONFIRM", style = MaterialTheme.typography.button)
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
                Text("-", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
            }
            Text(
                format.format(value),
                style = MaterialTheme.typography.h5,
                color = ArtColors.PaperWhite,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = onIncrement, modifier = Modifier.size(32.dp)) {
                Text("+", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
            }
        }
    }
}
