import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.slf4j.LoggerFactory
import service.ServiceController
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    serviceController: ServiceController? = null,
    updateManager: service.UpdateManager = remember { service.UpdateManager() },
    onOpenUpdateDetails: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isMac = remember { MacPlatform.isMac }
    val osNameLabel = if (isMac) "macOS" else "Windows"
    var isAutoStartEnabled by remember { mutableStateOf(WindowsAutoStart.isEnabled()) }
    var isNotificationsEnabled by remember { mutableStateOf(settings.notificationsEnabled) }
    var isSyncLockScreen by remember { mutableStateOf(settings.syncLockScreen) }
    var isAutoCheckUpdates by remember { mutableStateOf(settings.autoCheckUpdates) }
    var currentThemeMode by remember { mutableStateOf(settings.themeMode) }
    var showUninstallConfirmation by remember { mutableStateOf(false) }
    var deleteUserDataOnUninstall by remember { mutableStateOf(true) }
    val logger = LoggerFactory.getLogger("SettingsDialog")
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        val scale by animateFloatAsState(
            targetValue = if (visible) 1f else 0.95f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        )

        Surface(
            modifier = Modifier
                .width(460.dp)
                .heightIn(max = 620.dp)
                .scale(scale),
            shape = RoundedCornerShape(12.dp),
            color = ArtColors.Surface,
            contentColor = ArtColors.PaperWhite,
            border = BorderStroke(1.dp, ArtColors.CardBorder),
            elevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Preferences",
                        style = MaterialTheme.typography.h6,
                        color = ArtColors.PaperWhite
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ArtColors.PaperMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(14.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── Section 1: Appearance & Theme ────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSectionLabel("APPEARANCE")
                        Text(
                            "Choose your gallery ambiance or follow $osNameLabel appearance automatically.",
                            style = MaterialTheme.typography.caption,
                            color = ArtColors.PaperMuted
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemePill(
                                label = "System",
                                subtitle = "Follow OS",
                                selected = currentThemeMode == AppTheme.SYSTEM,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentThemeMode = AppTheme.SYSTEM
                                    onSettingsChange(settings.copy(themeMode = AppTheme.SYSTEM))
                                }
                            )
                            ThemePill(
                                label = "Light",
                                subtitle = "Museum Linen",
                                selected = currentThemeMode == AppTheme.LIGHT,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentThemeMode = AppTheme.LIGHT
                                    onSettingsChange(settings.copy(themeMode = AppTheme.LIGHT))
                                }
                            )
                            ThemePill(
                                label = "Dark",
                                subtitle = "Obsidian",
                                selected = currentThemeMode == AppTheme.DARK,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentThemeMode = AppTheme.DARK
                                    onSettingsChange(settings.copy(themeMode = AppTheme.DARK))
                                }
                            )
                        }
                    }

                    Divider(color = ArtColors.DividerLine)

                    // -- Section 2: Permanent Museum Ambient Matting ---------------
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSectionLabel("WALLPAPER PRESENTATION")
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = ArtColors.SurfaceVariant,
                            border = BorderStroke(1.dp, ArtColors.GalleryGold.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "Ambient Blur Matting - Permanent Standard",
                                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                                    color = ArtColors.GalleryGold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Preserves 100% authentic fine art aspect ratios without stretching, cropping, or letterbox bars. The screen is filled with a soft Gaussian bokeh sampled directly from the artwork's pigments.",
                                    style = MaterialTheme.typography.caption,
                                    color = ArtColors.PaperMuted
                                )
                            }
                        }
                    }
                    Divider(color = ArtColors.DividerLine)

                    // ── Section 3: Daily Schedule ─────────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSectionLabel("DAILY ROTATION SCHEDULE")
                        TimePickerSection(
                            settings = settings,
                            onSettingsChange = onSettingsChange
                        )
                    }

                    Divider(color = ArtColors.DividerLine)

                    // ── Section 4: System Integration ─────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSectionLabel(if (isMac) "MACOS INTEGRATION" else "WINDOWS INTEGRATION")

                        SettingsRow(
                            label = if (isMac) "Launch at login" else "Start with Windows",
                            description = if (isMac) "Launch quietly into the menu bar when you sign in" else "Launch automatically into system tray at login",
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

                        SettingsRow(
                            label = "Wallpaper update notifications",
                            description = "Show a discreet notification when wallpaper rotates",
                            checked = isNotificationsEnabled,
                            onCheckedChange = { enabled ->
                                onSettingsChange(settings.copy(notificationsEnabled = enabled))
                                isNotificationsEnabled = enabled
                            }
                        )

                        if (!isMac) {
                            SettingsRow(
                                label = "Sync lock screen wallpaper",
                                description = "Keep your Windows lock screen in sync with your current artwork",
                                checked = isSyncLockScreen,
                                onCheckedChange = { enabled ->
                                    isSyncLockScreen = enabled
                                    onSettingsChange(settings.copy(syncLockScreen = enabled))
                                }
                            )
                        }
                    }

                    Divider(color = ArtColors.DividerLine)

                    // ── Section 5: In-App Updates ─────────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSectionLabel("SOFTWARE UPDATES")

                        SettingsRow(
                            label = "New release alerts",
                            description = "Check in the background and notify once when a new version is published",
                            checked = isAutoCheckUpdates,
                            onCheckedChange = { enabled ->
                                isAutoCheckUpdates = enabled
                                onSettingsChange(
                                    settings.copy(
                                        autoCheckUpdates = enabled,
                                        dismissedUpdateVersion = if (enabled) null else settings.dismissedUpdateVersion
                                    )
                                )
                            }
                        )

                        val updateStatus by updateManager.status.collectAsState()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = ArtColors.SurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (updateStatus is service.UpdateStatus.Available) ArtColors.GalleryGold.copy(alpha = 0.6f)
                                else ArtColors.CardBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            "Version: v${AppInfo.version}",
                                            style = MaterialTheme.typography.subtitle2,
                                            color = ArtColors.PaperWhite
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            when (val s = updateStatus) {
                                                is service.UpdateStatus.Idle -> "Background release watcher ready"
                                                is service.UpdateStatus.Checking -> "Checking GitHub releases..."
                                                is service.UpdateStatus.UpToDate -> "You are on the latest release"
                                                is service.UpdateStatus.Available -> {
                                                    val sizeSuffix = service.UpdateManager.formatAssetSizeMb(s.asset)?.let { " ($it)" } ?: ""
                                                    "New version v${s.latestVersion} is ready$sizeSuffix"
                                                }
                                                is service.UpdateStatus.Downloading -> "Downloading installer (${(s.progress * 100).toInt()}%)..."
                                                is service.UpdateStatus.ReadyToInstall -> "Launching installer..."
                                                is service.UpdateStatus.Installing -> "Installing update... restarting shortly"
                                                is service.UpdateStatus.Error -> "Status: ${s.message}"
                                            },
                                            style = MaterialTheme.typography.caption,
                                            color = if (updateStatus is service.UpdateStatus.Available) ArtColors.GalleryGold else ArtColors.PaperMuted
                                        )
                                    }

                                    when (val s = updateStatus) {
                                        is service.UpdateStatus.Available -> {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (onOpenUpdateDetails != null) {
                                                    TextButton(
                                                        onClick = {
                                                            onDismiss()
                                                            onOpenUpdateDetails()
                                                        },
                                                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                                    ) {
                                                        Text("NOTES", color = ArtColors.GalleryGold, style = MaterialTheme.typography.button)
                                                    }
                                                }
                                                PulsingButton(
                                                    onClick = {
                                                        s.asset?.let { asset ->
                                                            coroutineScope.launch {
                                                                updateManager.downloadAndInstall(asset)
                                                            }
                                                        } ?: run {
                                                            onOpenUpdateDetails?.invoke()
                                                        }
                                                    }
                                                ) {
                                                    Text("UPDATE", style = MaterialTheme.typography.button)
                                                }
                                            }
                                        }
                                        is service.UpdateStatus.Downloading -> {
                                            CircularProgressIndicator(
                                                color = ArtColors.GalleryGold,
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                        is service.UpdateStatus.Installing -> {
                                            CircularProgressIndicator(
                                                color = ArtColors.GalleryGold,
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                        is service.UpdateStatus.Checking -> {
                                            CircularProgressIndicator(
                                                color = ArtColors.GalleryGold,
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                        else -> {
                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        updateManager.checkForUpdates(silent = false)
                                                    }
                                                },
                                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    backgroundColor = Color.Transparent,
                                                    contentColor = ArtColors.GalleryGold
                                                ),
                                                border = BorderStroke(1.dp, ArtColors.GalleryGold.copy(alpha = 0.5f)),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("CHECK", style = MaterialTheme.typography.button)
                                            }
                                        }
                                    }
                                }

                                if (updateStatus is service.UpdateStatus.Downloading) {
                                    val progress = (updateStatus as service.UpdateStatus.Downloading).progress
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = ArtColors.GalleryGold,
                                        backgroundColor = ArtColors.DividerLine
                                    )
                                }

                                if (updateStatus is service.UpdateStatus.Installing) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = ArtColors.GalleryGold,
                                        backgroundColor = ArtColors.DividerLine
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = ArtColors.DividerLine)

                    // ── Section 6: Maintenance & Uninstall ───────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSectionLabel("MAINTENANCE & UNINSTALL")

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = ArtColors.SurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ArtColors.CardBorder)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            "Uninstall Art Wallpaper",
                                            style = MaterialTheme.typography.subtitle2,
                                            color = ArtColors.PaperWhite
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "Cleanly remove app, startup services, and local wallpaper cache",
                                            style = MaterialTheme.typography.caption,
                                            color = ArtColors.PaperMuted
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { showUninstallConfirmation = !showUninstallConfirmation },
                                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            backgroundColor = Color.Transparent,
                                            contentColor = Color(0xFFEF5350)
                                        ),
                                        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            if (showUninstallConfirmation) "CANCEL" else "UNINSTALL",
                                            style = MaterialTheme.typography.button,
                                            color = Color(0xFFEF5350)
                                        )
                                    }
                                }

                                if (showUninstallConfirmation) {
                                    val dataSizeMb = remember { service.UninstallManager.getEstimatedUserDataSizeMb() }
                                    Divider(color = ArtColors.DividerLine)

                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            "Leaving the gallery? We are sorry to see you go.",
                                            style = MaterialTheme.typography.caption,
                                            color = ArtColors.GalleryGold
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .pointerHoverIcon(PointerIcon.Hand)
                                                .clickable { deleteUserDataOnUninstall = !deleteUserDataOnUninstall }
                                                .background(ArtColors.Surface)
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = deleteUserDataOnUninstall,
                                                onCheckedChange = { deleteUserDataOnUninstall = it },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = ArtColors.GalleryGold,
                                                    uncheckedColor = ArtColors.PaperMuted,
                                                    checkmarkColor = ArtColors.Canvas
                                                )
                                            )
                                            Column {
                                                Text(
                                                    "Delete cached artworks, database & history",
                                                    style = MaterialTheme.typography.subtitle2,
                                                    color = ArtColors.PaperWhite
                                                )
                                                val sizeText = if (dataSizeMb > 0) " (~$dataSizeMb MB)" else ""
                                                val storagePathLabel = if (isMac) "~/.artwallpaper$sizeText" else "%USERPROFILE%\\.artwallpaper$sizeText"
                                                Text(
                                                    "Location: $storagePathLabel",
                                                    style = MaterialTheme.typography.caption,
                                                    color = ArtColors.PaperMuted
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { showUninstallConfirmation = false },
                                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                            ) {
                                                Text("KEEP APP", color = ArtColors.PaperMuted)
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    service.UninstallManager.performUninstall(deleteUserDataOnUninstall)
                                                },
                                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                                colors = ButtonDefaults.buttonColors(
                                                    backgroundColor = Color(0xFFD32F2F),
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("CONFIRM UNINSTALL", style = MaterialTheme.typography.button)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = ArtColors.DividerLine)

                    // ── Section 7: About ──────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Image(
                            painter = painterResource("icons/128x128.png"),
                            contentDescription = "ArtWallpaper Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column {
                            Text(
                                "Art Wallpaper v${AppInfo.version}",
                                style = MaterialTheme.typography.subtitle2,
                                color = ArtColors.PaperWhite
                            )
                            Text(
                                "Open-access masterpieces curated from The Metropolitan Museum of Art",
                                style = MaterialTheme.typography.caption,
                                color = ArtColors.PaperMuted
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Divider(color = ArtColors.DividerLine)
                Spacer(Modifier.height(14.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    PulsingButton(onClick = onDismiss) {
                        Text("DONE", style = MaterialTheme.typography.button)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePill(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) ArtColors.GalleryGold else ArtColors.DividerLine
    val bgColor = if (selected) ArtColors.SurfaceVariant else ArtColors.Surface

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.subtitle2.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = if (selected) ArtColors.GalleryGold else ArtColors.PaperWhite
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.caption,
                color = ArtColors.PaperMuted
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
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
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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
                "Updates quietly in the background at this hour",
                style = MaterialTheme.typography.caption,
                color = ArtColors.PaperMuted
            )
        }

        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ArtColors.GalleryGold,
                backgroundColor = Color.Transparent
            ),
            border = BorderStroke(1.dp, ArtColors.GalleryGold)
        ) {
            Text(timeString, style = MaterialTheme.typography.button)
        }
    }

    if (showPicker) {
        TimePickerDialog(
            initialHour = currentSettings.updateTimeHour,
            initialMinute = currentSettings.updateTimeMinute,
            onTimeSelected = { hour, minute ->
                val newSettings = currentSettings.copy(
                    updateTimeHour = hour,
                    updateTimeMinute = minute,
                    hasSetUpdateTime = true
                )
                onSettingsChange(newSettings)
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
    var selectedHour by remember { mutableStateOf(initialHour) }
    var selectedMinute by remember { mutableStateOf(initialMinute) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = ArtColors.Surface,
            contentColor = ArtColors.PaperWhite,
            border = BorderStroke(1.dp, ArtColors.CardBorder),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Set Rotation Time",
                    style = MaterialTheme.typography.h6,
                    color = ArtColors.PaperWhite
                )

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberPicker(
                        value = selectedHour,
                        range = 0..23,
                        onValueChange = { selectedHour = it }
                    )
                    Text(" : ", style = MaterialTheme.typography.h5, color = ArtColors.PaperWhite)
                    NumberPicker(
                        value = selectedMinute,
                        range = 0..59,
                        onValueChange = { selectedMinute = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("CANCEL", color = ArtColors.PaperMuted)
                    }
                    Spacer(Modifier.width(8.dp))
                    PulsingButton(
                        onClick = { onTimeSelected(selectedHour, selectedMinute) }
                    ) {
                        Text("SAVE", style = MaterialTheme.typography.button)
                    }
                }
            }
        }
    }
}

@Composable
fun NumberPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = {
                val next = if (value + 1 > range.last) range.first else value + 1
                onValueChange(next)
            },
            modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
        ) {
            Text("+", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
        }

        Surface(
            modifier = Modifier.width(56.dp).height(44.dp),
            shape = RoundedCornerShape(6.dp),
            color = ArtColors.SurfaceVariant,
            border = BorderStroke(1.dp, ArtColors.DividerLine)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    String.format(java.util.Locale.ROOT, "%02d", value),
                    style = MaterialTheme.typography.h6,
                    color = ArtColors.GalleryGold
                )
            }
        }

        IconButton(
            onClick = {
                val prev = if (value - 1 < range.first) range.last else value - 1
                onValueChange(prev)
            },
            modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
        ) {
            Text("-", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
        }
    }
}
