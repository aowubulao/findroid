package dev.jdtech.jellyfin.core.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType
import timber.log.Timber

@Composable
fun AppUpdateDialog(
    deviceType: DeviceType,
    onDismiss: () -> Unit,
    viewModel: AppUpdateViewModel = hiltViewModel(),
    button: @Composable (String, () -> Unit) -> Unit = { label, click ->
        TextButton(onClick = click) { Text(label) }
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ready = state as? UpdateState.Ready
    var installFailed by remember(ready?.apk) { mutableStateOf(false) }

    fun install(download: UpdateState.Ready) {
        if (!download.apk.isFile) {
            viewModel.download(download.update)
            return
        }
        try {
            context.startActivity(UpdateFileProvider.installIntent(context, download.apk))
            viewModel.installerOpened()
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not open the Android package installer")
            installFailed = true
        }
    }

    val settings =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (context.packageManager.canRequestPackageInstalls()) {
                (viewModel.state.value as? UpdateState.Ready)?.let(::install)
            }
        }

    LaunchedEffect(deviceType) { viewModel.open(deviceType) }
    LaunchedEffect(ready?.prompt) {
        if (ready?.prompt != true) return@LaunchedEffect
        lifecycle.withResumed {
            if (
                viewModel.consumeInstallPrompt() &&
                    context.packageManager.canRequestPackageInstalls()
            )
                install(ready)
        }
    }

    val close = {
        viewModel.close()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = close,
        title = {
            val update = (state as? UpdateState.Available)?.update ?: ready?.update
            Text(
                if (update == null) stringResource(R.string.app_update_check)
                else stringResource(R.string.app_update_version, update.versionName)
            )
        },
        text = {
            when (val current = state) {
                UpdateState.Idle,
                UpdateState.Checking -> Text(stringResource(R.string.app_update_checking))
                UpdateState.Current -> Text(stringResource(R.string.app_update_current))
                is UpdateState.Failed -> Text(stringResource(current.message))
                is UpdateState.Available -> {
                    val scroll = rememberScrollState()
                    Text(
                        current.update.notes.ifBlank {
                            stringResource(R.string.app_update_available)
                        },
                        modifier =
                            Modifier.heightIn(max = 250.dp)
                                .verticalScroll(scroll)
                                .focusable(deviceType == DeviceType.TV && scroll.maxValue > 0),
                    )
                }
                is UpdateState.Downloading ->
                    Column {
                        Text(stringResource(R.string.app_update_downloading, current.percent))
                        LinearProgressIndicator(
                            progress = { current.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                is UpdateState.Ready ->
                    Text(
                        stringResource(
                            when {
                                installFailed -> R.string.app_update_install_failed
                                context.packageManager.canRequestPackageInstalls() ->
                                    R.string.app_update_ready
                                else -> R.string.app_update_permission
                            }
                        )
                    )
            }
        },
        confirmButton = {
            when (val current = state) {
                is UpdateState.Available ->
                    button(stringResource(R.string.app_update_download)) {
                        viewModel.download(current.update)
                    }
                is UpdateState.Failed ->
                    button(stringResource(R.string.retry)) {
                        if (current.update != null) viewModel.download(current.update)
                        else viewModel.check(deviceType)
                    }
                is UpdateState.Ready -> {
                    val allowed = context.packageManager.canRequestPackageInstalls()
                    button(
                        stringResource(
                            if (allowed) R.string.app_update_install else R.string.app_update_allow
                        )
                    ) {
                        installFailed = false
                        if (context.packageManager.canRequestPackageInstalls()) install(current)
                        else
                            try {
                                val sourceSettings =
                                    Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.fromParts("package", context.packageName, null),
                                    )
                                try {
                                    settings.launch(sourceSettings)
                                } catch (_: ActivityNotFoundException) {
                                    // Some TV launchers expose only the general security settings.
                                    settings.launch(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                }
                            } catch (e: RuntimeException) {
                                Timber.w(e, "Could not open install-source settings")
                                installFailed = true
                            }
                    }
                }
                else -> Unit
            }
        },
        dismissButton = {
            val busy = state is UpdateState.Downloading || state == UpdateState.Checking
            button(stringResource(if (busy) R.string.cancel else R.string.close), close)
        },
    )
}
