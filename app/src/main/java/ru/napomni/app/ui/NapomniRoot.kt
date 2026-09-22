package ru.napomni.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import ru.napomni.app.NapomniApp
import ru.napomni.app.domain.PermissionChecks
import ru.napomni.app.ui.help.HelpScreen
import ru.napomni.app.ui.notes.NoteEditScreen
import ru.napomni.app.ui.notes.NotesScreen
import ru.napomni.app.ui.onboarding.OnboardingScreen
import ru.napomni.app.ui.reminders.ReminderEditScreen
import ru.napomni.app.ui.reminders.RemindersScreen
import ru.napomni.app.ui.settings.SettingsScreen
import ru.napomni.app.ui.stats.StatsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector, val iconOutlined: ImageVector)

private val tabs = listOf(
    Tab("reminders", "Напоминания", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    Tab("notes", "Заметки", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    Tab("stats", "Статистика", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    Tab("settings", "Настройки", Icons.Filled.Settings, Icons.Outlined.Settings),
)

/**
 * Корень UI: онбординг разрешений (FR-9.4), баннер разрешений на главном экране,
 * нижняя навигация и NavHost (FR-6.2). [newReminderRequest] — «+» из виджета (FR-11).
 */
@Composable
fun NapomniRoot(
    startOnboarding: Boolean = false,
    newReminderRequest: androidx.compose.runtime.State<Boolean> = mutableStateOf(false),
) {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    LaunchedEffect(newReminderRequest.value) {
        if (newReminderRequest.value) {
            navController.navigate("reminders_edit?id=-1")
        }
    }

    // Баннер разрешений: пересчёт при возврате с экрана настроек (FR-9.4).
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val permissionsOk = remember(resumeTick) {
        PermissionChecks.notificationsAllowed(context) &&
            PermissionChecks.exactAlarmsAllowed(context) &&
            PermissionChecks.fullScreenAllowed(context) &&
            PermissionChecks.batteryUnrestricted(context)
    }
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }
    val showBanner = !permissionsOk && !bannerDismissed && currentRoute == "reminders"

    val showBottomBar = tabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) tab.icon else tab.iconOutlined,
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showBanner) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Чтобы напоминания приходили точно, включите разрешения",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { navController.navigate("settings") }) { Text("Настроить") }
                        IconButton(onClick = { bannerDismissed = true }) {
                            Icon(Icons.Default.Close, contentDescription = "Скрыть")
                        }
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = if (startOnboarding) "onboarding" else "reminders",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("onboarding") {
                    val app = context.applicationContext as NapomniApp
                    OnboardingScreen(onDone = {
                        kotlinx.coroutines.MainScope().launch {
                            app.container.settingsRepository.setOnboardingDone(true)
                        }
                        navController.navigate("reminders") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    })
                }
                composable("reminders") {
                    RemindersScreen(
                        onOpenReminder = { id ->
                            navController.navigate("reminders_edit?id=$id")
                        },
                    )
                }
                composable(
                    route = "reminders_edit?id={id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                ) {
                    ReminderEditScreen(
                        onDone = { navController.popBackStack() },
                    )
                }
                composable("notes") {
                    NotesScreen(
                        onOpenNote = { id ->
                            navController.navigate("notes_edit?id=$id")
                        },
                    )
                }
                composable(
                    route = "notes_edit?id={id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                ) {
                    NoteEditScreen(onDone = { navController.popBackStack() })
                }
                composable("stats") {
                    StatsScreen()
                }
                composable("settings") {
                    SettingsScreen(onOpenHelp = { navController.navigate("help") })
                }
                composable("help") {
                    HelpScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
