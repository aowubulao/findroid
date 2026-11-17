package dev.jdtech.jellyfin.core.update

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber

internal sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data object Current : UpdateState

    data class Available(val update: AppUpdate) : UpdateState

    data class Downloading(val percent: Int) : UpdateState

    data class Ready(val update: AppUpdate, val apk: File, val prompt: Boolean = true) : UpdateState

    data class Failed(@param:StringRes val message: Int, val update: AppUpdate? = null) :
        UpdateState
}

@HiltViewModel
class AppUpdateViewModel
@Inject
internal constructor(
    private val updater: AppUpdater,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    internal val state = mutableState.asStateFlow()
    private var operation: Job? = null

    internal fun open(deviceType: DeviceType) {
        if (state.value != UpdateState.Idle) return
        val pending = savedState.get<String>(PENDING_DOWNLOAD)
        if (pending != null) download(Json.decodeFromString<AppUpdate>(pending))
        else check(deviceType)
    }

    internal fun check(deviceType: DeviceType) {
        if (operation?.isActive == true) return
        mutableState.value = UpdateState.Checking
        operation = viewModelScope.launch {
            try {
                val update = updater.findUpdate(deviceType)
                mutableState.value =
                    update?.let { UpdateState.Available(it) } ?: UpdateState.Current
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not check GitHub releases")
                mutableState.value = UpdateState.Failed(R.string.app_update_check_failed)
            }
        }
    }

    internal fun download(update: AppUpdate) {
        if (operation?.isActive == true) return
        // Android may recreate the activity while the user is in the install-source settings.
        savedState[PENDING_DOWNLOAD] = Json.encodeToString(update.copy(notes = ""))
        mutableState.value = UpdateState.Downloading(0)
        operation = viewModelScope.launch {
            try {
                val apk =
                    updater.download(update) { percent ->
                        if (isActive) mutableState.value = UpdateState.Downloading(percent)
                    }
                if (apk == null) {
                    savedState.remove<String>(PENDING_DOWNLOAD)
                    mutableState.value = UpdateState.Current
                } else {
                    mutableState.value = UpdateState.Ready(update, apk)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not prepare app update")
                savedState.remove<String>(PENDING_DOWNLOAD)
                val message =
                    if (e is InvalidUpdateApk) R.string.app_update_invalid
                    else R.string.app_update_download_failed
                mutableState.value = UpdateState.Failed(message, update)
            }
        }
    }

    internal fun consumeInstallPrompt(): Boolean {
        val ready = state.value as? UpdateState.Ready ?: return false
        if (!ready.prompt) return false
        mutableState.value = ready.copy(prompt = false)
        return true
    }

    internal fun installerOpened() {
        consumeInstallPrompt()
        savedState.remove<String>(PENDING_DOWNLOAD)
    }

    internal fun close() {
        operation?.cancel()
        operation = null
        savedState.remove<String>(PENDING_DOWNLOAD)
        mutableState.value = UpdateState.Idle
    }

    companion object {
        private const val PENDING_DOWNLOAD = "pendingAppUpdate"
    }
}
