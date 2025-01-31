import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsPanel(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    serviceController: ServiceController
) {
    rememberCoroutineScope()
    
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Service Control Card
        Card(Modifier.fillMaxWidth(), elevation = 4.dp) {
            Column(Modifier.padding(16.dp)) {
                Text("Service Control", style = MaterialTheme.typography.subtitle1)
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-update wallpaper every 24 hours")
                    Switch(
                        checked = serviceController.isServiceRunning,
                        onCheckedChange = { isRunning ->
                            if (isRunning) {
                                serviceController.startService()
                            } else {
                                serviceController.stopService()
                            }
                        }
                    )
                }
            }
        }

        // System Integration Card
        Card(Modifier.fillMaxWidth(), elevation = 4.dp) {
            Column(Modifier.padding(16.dp)) {
                Text("System Integration", style = MaterialTheme.typography.subtitle1)
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Start with Windows")
                    Switch(
                        checked = settings.startWithSystem,
                        onCheckedChange = { 
                            onSettingsChange(settings.copy(startWithSystem = it))
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
                        onCheckedChange = { 
                            onSettingsChange(settings.copy(showNotifications = it))
                        }
                    )
                }
            }
        }
    }
} 