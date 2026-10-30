package io.github.omega28.proclimit

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProcLimitTheme {
                ProcLimitScreen()
            }
        }
    }
}

private data class UiState(
    val permissionGranted: Boolean,
    val liveLimit: Int?,
    val liveError: String?,
    val boot: ProcLimit.BootRecord?,
    val notificationsAllowed: Boolean,
)

private fun readState(context: Context): UiState {
    val (live, error) = try {
        ProcLimit.getLimit() to null
    } catch (t: Throwable) {
        null to t.toString()
    }
    return UiState(
        permissionGranted = ProcLimit.hasPermission(context),
        liveLimit = live,
        liveError = error,
        boot = ProcLimit.bootRecord(context),
        notificationsAllowed = StatusNotifier.canNotify(context),
    )
}

@Composable
private fun ProcLimitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProcLimitScreen() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readState(context)) }
    LifecycleResumeEffect(Unit) {
        state = readState(context)
        onPauseOrDispose {}
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Once denied, Android stops showing the dialog; send the user to the app's notification settings instead.
        if (!granted) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
        state = readState(context)
    }

    fun applyLimit(limit: Int) {
        val result = ProcLimit.apply(limit)
        state = readState(context)
        scope.launch { snackbar.showSnackbar(if (result.ok) result.message else "Failed: ${result.message}") }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("ProcLimit") }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LimitCard(state)
            if (state.permissionGranted) {
                StatusCard(
                    icon = R.drawable.ic_check_circle,
                    title = "Permission granted",
                    body = ProcLimit.PERMISSION,
                )
            } else {
                GrantCard(command = ProcLimit.grantCommand(context), onCopy = { copy(context, it) })
            }
            BootCard(state.boot)
            if (!state.notificationsAllowed) {
                NotificationCard(onAllow = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) })
            }
            Button(
                onClick = { applyLimit(ProcLimit.TARGET_LIMIT) },
                enabled = state.permissionGranted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply ${ProcLimit.TARGET_LIMIT} now")
            }
            OutlinedButton(
                onClick = { applyLimit(ProcLimit.STANDARD) },
                enabled = state.permissionGranted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset to Standard")
            }
        }
    }
}

@Composable
private fun LimitCard(state: UiState) {
    val atTarget = state.liveLimit == ProcLimit.TARGET_LIMIT
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (atTarget) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Background process limit", style = MaterialTheme.typography.labelLarge)
            Text(
                state.liveLimit?.let(ProcLimit::describe) ?: "Unknown",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                when {
                    state.liveError != null -> "Couldn't read it: ${state.liveError}"
                    atTarget -> "Active now"
                    else -> "Not at ${ProcLimit.TARGET_LIMIT} right now"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun GrantCard(command: String, onCopy: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(R.drawable.ic_error, "Permission not granted")
            Text(
                "Run this once from a computer, or from Termux's adb connected to Wireless debugging:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    command,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
            FilledTonalButton(onClick = { onCopy(command) }) {
                Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null, Modifier.size(18.dp))
                Text("Copy command", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun BootCard(boot: ProcLimit.BootRecord?) {
    val context = LocalContext.current
    when {
        boot == null -> StatusCard(R.drawable.ic_schedule, "Last boot", "Nothing recorded yet. Reboot to test.")
        boot.ok -> StatusCard(
            R.drawable.ic_check_circle, "Last boot",
            "${boot.message}\n${ProcLimit.formatTime(context, boot.time)}",
        )
        else -> StatusCard(
            R.drawable.ic_error, "Last boot failed",
            "${boot.message}\n${ProcLimit.formatTime(context, boot.time)}",
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun NotificationCard(onAllow: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(R.drawable.ic_notifications, "Boot confirmation")
            Text(
                "Allow notifications to get a silent notification after each boot confirming the limit was set, " +
                    "or an alert if it failed.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(onClick = onAllow) { Text("Allow notifications") }
        }
    }
}

@Composable
private fun StatusCard(icon: Int, title: String, body: String, tint: Color = MaterialTheme.colorScheme.primary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = tint)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CardHeader(icon: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), contentDescription = null)
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("grant command", text))
}
