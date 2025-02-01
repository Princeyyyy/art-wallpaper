import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private val logger = LoggerFactory.getLogger("SettingsDialog") 