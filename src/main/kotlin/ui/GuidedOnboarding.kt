import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Path
import service.ServiceController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.io.path.exists

// Fix #10 (Medium): Extracted 4-step onboarding flow from MainScreen.kt into a dedicated component.
// Completely removes 650+ lines of onboarding UI and animation definitions from MainScreen recomposition.

// ── Complete 4-Step Guided Onboarding Experience ─────────────────────────────

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GuidedOnboarding(
    settings: Settings,
    serviceController: ServiceController,
    currentArtwork: Pair<Path, ArtworkMetadata>?,
    onSettingsChange: (Settings) -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit,
    coroutineScope: CoroutineScope
) {
    var step by remember { mutableStateOf(1) }
    var selectedHour by remember { mutableStateOf(settings.updateTimeHour) }
    var selectedMinute by remember { mutableStateOf(settings.updateTimeMinute) }
    var autoStartEnabled by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var syncLockScreenEnabled by remember { mutableStateOf(settings.syncLockScreen) }
    var isCurating by remember { mutableStateOf(false) }
    var curationStage by remember { mutableStateOf("Connecting to museum collection...") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArtColors.Canvas),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxHeight()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Stepper Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource("icons/64x64.png"),
                        contentDescription = "Logo",
                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        "SETUP WIZARD",
                        style = MaterialTheme.typography.caption.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                        color = ArtColors.GalleryGold
                    )
                }
                StepIndicator(currentStep = step, totalSteps = 4)
            }

            // Step Body with Horizontal Slide & Fade
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width / 3 } + fadeIn()) with
                                (slideOutHorizontally { width -> -width / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width / 3 } + fadeIn()) with
                                (slideOutHorizontally { width -> width / 3 } + fadeOut())
                    }
                }
            ) { activeStep ->
                when (activeStep) {
                    1 -> OnboardingStep1Welcome()
                    2 -> OnboardingStep2Schedule(
                        hour = selectedHour,
                        minute = selectedMinute,
                        onTimeChange = { h, m ->
                            selectedHour = h
                            selectedMinute = m
                        }
                    )
                    3 -> OnboardingStep3Integration(
                        autoStart = autoStartEnabled,
                        notifications = notificationsEnabled,
                        syncLockScreen = syncLockScreenEnabled,
                        onAutoStartChange = { autoStartEnabled = it },
                        onNotificationsChange = { notificationsEnabled = it },
                        onSyncLockScreenChange = { syncLockScreenEnabled = it }
                    )
                    4 -> OnboardingStep4Curate(
                        isCurating = isCurating,
                        curationStage = curationStage,
                        currentArtwork = currentArtwork,
                        onCurate = {
                            if (!isCurating) {
                                isCurating = true
                                coroutineScope.launch(Dispatchers.Default) {
                                    try {
                                        curationStage = "Connecting to The Met collection..."
                                        delay(400)
                                        if (autoStartEnabled) {
                                            WindowsAutoStart.enable()
                                        }

                                        val lockScreenPref = if (MacPlatform.isMac) false else syncLockScreenEnabled
                                        serviceController.wallpaperManager.updateSettings(
                                            Settings.currentSettings.value.copy(syncLockScreen = lockScreenPref)
                                        )

                                        curationStage = "Selecting unvisited masterpiece..."
                                        withTimeout(45_000L) {
                                            serviceController.nextWallpaper()
                                        }

                                        curationStage = "Enhancing and applying to desktop..."
                                        withTimeout(45_000L) {
                                            serviceController.wallpaperManager.currentArtwork.first { art ->
                                                art != null && art.first.exists()
                                            }
                                        }

                                        curationStage = "Masterpiece applied successfully!"
                                        val finalSettings = Settings.currentSettings.value.copy(
                                            isFirstRun = false,
                                            updateTimeHour = selectedHour,
                                            updateTimeMinute = selectedMinute,
                                            hasSetUpdateTime = true,
                                            startWithSystem = autoStartEnabled,
                                            hasEnabledAutoStart = autoStartEnabled,
                                            notificationsEnabled = notificationsEnabled,
                                            syncLockScreen = lockScreenPref
                                        )
                                        withContext(Dispatchers.Main) {
                                            Settings.updateCurrentSettings(finalSettings)
                                            serviceController.wallpaperManager.updateSettings(finalSettings)
                                            onSettingsChange(finalSettings)
                                        }
                                        serviceController.startService()
                                    } catch (e: TimeoutCancellationException) {
                                        withContext(Dispatchers.Main) {
                                            onError("Network took longer than usual. Please retry.")
                                            isCurating = false
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            onError("Setup note: ${e.message}")
                                            isCurating = false
                                        }
                                    }
                                }
                            }
                        },
                        onFinish = onComplete
                    )
                }
            }

            // Bottom Navigation Actions
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 1 && step < 4 && !isCurating) {
                    TextButton(
                        onClick = { step-- },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        colors = ButtonDefaults.textButtonColors(contentColor = ArtColors.PaperMuted)
                    ) {
                        Text("BACK", style = MaterialTheme.typography.button)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (step < 4) {
                    PulsingButton(
                        onClick = { step++ }
                    ) {
                        Text(if (step == 1) "GET STARTED" else "CONTINUE", style = MaterialTheme.typography.button)
                    }
                }
            }
        }
    }
}

// ── Step 1: Welcome ─────────────────────────────────────────────────────────

@Composable
private fun OnboardingStep1Welcome() {
    val osLabel = if (MacPlatform.isMac) "macOS" else "Windows"
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource("icons/128x128.png"),
            contentDescription = "ArtWallpaper Logo",
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(18.dp))
        )

        Spacer(Modifier.height(18.dp))

        Text(
            "ArtWallpaper",
            style = MaterialTheme.typography.h4,
            color = ArtColors.GalleryGold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "A museum gallery on your $osLabel desktop",
            style = MaterialTheme.typography.subtitle1,
            color = ArtColors.PaperWhite,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
        Divider(color = ArtColors.DividerLine, thickness = 1.dp)
        Spacer(Modifier.height(24.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            WelcomePillar(
                title = "400,000+ Curated Works",
                description = "Directly sourced from The Metropolitan Museum of Art open access archive."
            )
            WelcomePillar(
                title = "True Display Screen Fit",
                description = "Smart ambient matting prevents stretching, fitting your display resolution perfectly."
            )
            WelcomePillar(
                title = "Automatic Daily Refresh",
                description = "Wakes up silently and changes your background at the exact time you prefer."
            )
        }
    }
}

@Composable
private fun WelcomePillar(title: String, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .background(ArtColors.GalleryGold, CircleShape)
        )
        Column {
            Text(title, style = MaterialTheme.typography.subtitle2, color = ArtColors.PaperWhite)
            Text(description, style = MaterialTheme.typography.body2, color = ArtColors.PaperMuted)
        }
    }
}

// ── Step 2: Schedule ────────────────────────────────────────────────────────

@Composable
private fun OnboardingStep2Schedule(
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Daily Schedule",
            style = MaterialTheme.typography.h5,
            color = ArtColors.GalleryGold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Choose when your wallpaper should rotate each day.",
            style = MaterialTheme.typography.body2,
            color = ArtColors.PaperMuted
        )

        Spacer(Modifier.height(20.dp))

        // Preset 1: Morning
        OnboardingOptionCard(
            title = "Morning Coffee (07:00 AM)",
            description = "Start your morning with a fresh artistic masterpiece.",
            selected = hour == 7 && minute == 0,
            onClick = { onTimeChange(7, 0) }
        )

        Spacer(Modifier.height(10.dp))

        // Preset 2: Workday
        OnboardingOptionCard(
            title = "Workday Start (09:00 AM)",
            description = "A new classical painting right as your workday begins.",
            selected = hour == 9 && minute == 0,
            onClick = { onTimeChange(9, 0) }
        )

        Spacer(Modifier.height(10.dp))

        // Preset 3: Evening
        OnboardingOptionCard(
            title = "Evening Unwind (18:00 PM)",
            description = "Relax with fine art after your workday wraps up.",
            selected = hour == 18 && minute == 0,
            onClick = { onTimeChange(18, 0) }
        )

        Spacer(Modifier.height(16.dp))

        // Custom time picker box (hours + 5-minute steps)
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = ArtColors.Surface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, ArtColors.CardBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    String.format(java.util.Locale.ROOT, "Custom Time: %02d:%02d", hour, minute),
                    style = MaterialTheme.typography.subtitle2,
                    color = ArtColors.PaperWhite
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onTimeChange((hour - 1 + 24) % 24, minute) },
                        modifier = Modifier.size(30.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("-", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
                    }
                    Text(
                        String.format(java.util.Locale.ROOT, "%02d", hour),
                        style = MaterialTheme.typography.subtitle1,
                        color = ArtColors.GalleryGold,
                        modifier = Modifier.padding(horizontal = 3.dp)
                    )
                    IconButton(
                        onClick = { onTimeChange((hour + 1) % 24, minute) },
                        modifier = Modifier.size(30.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("+", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
                    }

                    Text(":", style = MaterialTheme.typography.subtitle1, color = ArtColors.PaperMuted, modifier = Modifier.padding(horizontal = 4.dp))

                    IconButton(
                        onClick = { onTimeChange(hour, (minute - 5 + 60) % 60) },
                        modifier = Modifier.size(30.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("-", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
                    }
                    Text(
                        String.format(java.util.Locale.ROOT, "%02d", minute),
                        style = MaterialTheme.typography.subtitle1,
                        color = ArtColors.GalleryGold,
                        modifier = Modifier.padding(horizontal = 3.dp)
                    )
                    IconButton(
                        onClick = { onTimeChange(hour, (minute + 5) % 60) },
                        modifier = Modifier.size(30.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("+", color = ArtColors.GalleryGold, style = MaterialTheme.typography.h6)
                    }
                }
            }
        }
    }
}

// ── Step 3: System Integration ──────────────────────────────────────────────

@Composable
private fun OnboardingStep3Integration(
    autoStart: Boolean,
    notifications: Boolean,
    syncLockScreen: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onSyncLockScreenChange: (Boolean) -> Unit
) {
    val isMac = MacPlatform.isMac
    val osLabel = if (isMac) "macOS" else "Windows"
    val trayGuideIcon = if (isMac) "tray/tray-mono-black-64.png" else "tray/tray-64.png"

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "$osLabel Integration",
            style = MaterialTheme.typography.h5,
            color = ArtColors.GalleryGold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (isMac) "ArtWallpaper runs quietly in your menu bar without appearing in your Dock." else "ArtWallpaper runs silently in your system tray without interrupting your work.",
            style = MaterialTheme.typography.body2,
            color = ArtColors.PaperMuted
        )

        Spacer(Modifier.height(20.dp))

        // Auto-start toggle card
        OnboardingOptionCard(
            title = if (isMac) "Launch at Login" else "Launch on System Startup",
            description = if (isMac) "Starts quietly in your menu bar when you sign in. Recommended." else "Starts minimized in your taskbar tray when you login. Recommended.",
            selected = autoStart,
            onClick = { onAutoStartChange(!autoStart) },
            trailingContent = {
                Switch(
                    checked = autoStart,
                    onCheckedChange = onAutoStartChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ArtColors.GalleryGold,
                        checkedTrackColor = ArtColors.GalleryGold.copy(alpha = 0.5f)
                    )
                )
            }
        )

        Spacer(Modifier.height(12.dp))

        // Notifications toggle card
        OnboardingOptionCard(
            title = "Artwork Change Notifications",
            description = "Displays a subtle $osLabel notification showing the artwork title and artist.",
            selected = notifications,
            onClick = { onNotificationsChange(!notifications) },
            trailingContent = {
                Switch(
                    checked = notifications,
                    onCheckedChange = onNotificationsChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ArtColors.GalleryGold,
                        checkedTrackColor = ArtColors.GalleryGold.copy(alpha = 0.5f)
                    )
                )
            }
        )

        if (!isMac) {
            Spacer(Modifier.height(12.dp))

            // Windows-only lock screen sync toggle card
            OnboardingOptionCard(
                title = "Sync Lock Screen Wallpaper",
                description = "Automatically mirrors your framed artwork onto your Windows lock screen.",
                selected = syncLockScreen,
                onClick = { onSyncLockScreenChange(!syncLockScreen) },
                trailingContent = {
                    Switch(
                        checked = syncLockScreen,
                        onCheckedChange = onSyncLockScreenChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ArtColors.GalleryGold,
                            checkedTrackColor = ArtColors.GalleryGold.copy(alpha = 0.5f)
                        )
                    )
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // System tray guide notice
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = ArtColors.SurfaceVariant,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, ArtColors.CardBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(trayGuideIcon),
                    contentDescription = "Tray Icon",
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    if (isMac) "ArtWallpaper lives in the macOS menu bar at the top-right of your screen. Click the icon anytime to open, pause, check updates, or switch artwork." else "ArtWallpaper lives in the Windows notification area (bottom-right taskbar). Right-click the icon anytime to open, pause, check updates, or switch artwork.",
                    style = MaterialTheme.typography.caption,
                    color = ArtColors.PaperMuted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ── Step 4: Curate First Wallpaper ──────────────────────────────────────────

@Composable
private fun OnboardingStep4Curate(
    isCurating: Boolean,
    curationStage: String,
    currentArtwork: Pair<Path, ArtworkMetadata>?,
    onCurate: () -> Unit,
    onFinish: () -> Unit
) {
    val osLabel = if (MacPlatform.isMac) "macOS" else "Windows"

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (currentArtwork != null) {
            // Success state - reveal artwork
            val (_, metadata) = currentArtwork
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(ArtColors.GalleryGold.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Success",
                    tint = ArtColors.GalleryGold,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Your Gallery is Ready!",
                style = MaterialTheme.typography.h5,
                color = ArtColors.GalleryGold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "\"${metadata.title}\"",
                style = MaterialTheme.typography.subtitle1,
                color = ArtColors.PaperWhite,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            metadata.artist?.let {
                Text(
                    "By $it",
                    style = MaterialTheme.typography.body2,
                    color = ArtColors.PaperMuted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))

            PulsingButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("ENTER GALLERY", style = MaterialTheme.typography.button)
            }

        } else if (isCurating) {
            // Live curating state
            CircularProgressIndicator(
                color = ArtColors.GalleryGold,
                modifier = Modifier.size(52.dp),
                strokeWidth = 3.dp
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Curating Your First Masterpiece",
                style = MaterialTheme.typography.h6,
                color = ArtColors.PaperWhite,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                curationStage,
                style = MaterialTheme.typography.body2,
                color = ArtColors.GalleryGold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Fetching high-resolution scans from The Metropolitan Museum of Art...",
                style = MaterialTheme.typography.caption,
                color = ArtColors.PaperMuted,
                textAlign = TextAlign.Center
            )

        } else {
            // Ready to curate prompt
            Image(
                painter = painterResource("icons/icon.png"),
                contentDescription = "Master Artwork Icon",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "Begin Your Art Journey",
                style = MaterialTheme.typography.h5,
                color = ArtColors.GalleryGold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Click below to curate your first museum wallpaper and apply it directly to your $osLabel desktop with true screen fit.",
                style = MaterialTheme.typography.body2,
                color = ArtColors.PaperMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            PulsingButton(
                onClick = onCurate,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("CURATE MY FIRST WALLPAPER", style = MaterialTheme.typography.button)
            }
        }
    }
}
