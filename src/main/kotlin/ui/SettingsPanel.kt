import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsPanel(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    serviceController: ServiceController
) {
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(Modifier.fillMaxWidth(), elevation = 4.dp) {
            Column(Modifier.padding(16.dp)) {
                Text("Application Settings", style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Start with Windows")
                    Switch(
                        checked = settings.startWithSystem,
                        onCheckedChange = { checked ->
                            scope.launch {
                                try {
                                    if (checked) WindowsAutoStart.enable()
                                    else WindowsAutoStart.disable()
                                    onSettingsChange(settings.copy(
                                        startWithSystem = checked,
                                        hasEnabledAutoStart = checked
                                    ))
                                } catch (e: Exception) {
                                    // Handle error
                                }
                            }
                        }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Notifications")
                    Switch(
                        checked = settings.showNotifications,
                        onCheckedChange = { checked ->
                            onSettingsChange(settings.copy(showNotifications = checked))
                        }
                    )
                }
            }
        }
    }
} 