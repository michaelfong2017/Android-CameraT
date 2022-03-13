package com.robocore.camerat

import android.view.SurfaceView
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainViewState(
    val havePermission: Boolean = false,
    val localSurfaceView: SurfaceView? = null,
//        val remoteSurfaceView: SurfaceView? = null,
)

data class MainViewAction(
    val action: MainViewActionValue,
    val map: Map<MainViewActionMapKey, Any>
) {
    enum class MainViewActionValue {
        CHANGE_PERMISSION,
        CREATE_LOCAL,
        INIT_CALL,
        CREATE_REMOTE,
    }

    enum class MainViewActionMapKey {
        PERMISSION_KEY
    }
}

/** For the purpose of highlighting public objects and jetpack compose preview */
interface IMainViewModel {
    val viewState: LiveData<MainViewState>
    fun sendAction(action: MainViewAction, coroutineScope: CoroutineScope)
    fun sendAction(action: MainViewAction)
}

/***/

@HiltViewModel
class MainViewModel @Inject constructor(
    private val webRtcEngine: WebRtcEngine
) : ViewModel(), IMainViewModel {


    private val _userIntent = Channel<MainViewAction>(Channel.UNLIMITED)
    private val _sharedFlow: SharedFlow<MainViewState> = handleAction()
    override val viewState: LiveData<MainViewState> = _sharedFlow.asLiveData()

    private fun handleAction() =
        _userIntent.receiveAsFlow().map {
            reduce(it, viewState.value ?: MainViewState())
        }.shareIn(viewModelScope, SharingStarted.Lazily, 1)

    private fun reduce(action: MainViewAction, state: MainViewState): MainViewState {
        return when (action.action) {
            MainViewAction.MainViewActionValue.CHANGE_PERMISSION -> {
                val havePermission =
                    action.map[MainViewAction.MainViewActionMapKey.PERMISSION_KEY] as Boolean
                if (havePermission)
                    state.copy(havePermission = true)
                else {
                    state.copy(havePermission = false)
                }
            }
            MainViewAction.MainViewActionValue.CREATE_LOCAL -> {
                if (state.localSurfaceView == null) {
                    val surfaceView: View? =
                        webRtcEngine.startPreview(false)

                    surfaceView?.let {
                        if (it is SurfaceView) {
                            it.setZOrderMediaOverlay(true)
                            state.copy(localSurfaceView = it)
                        } else {
                            state.copy()
                        }
                    } ?: run {
                        state.copy()
                    }
                } else {
                    state.copy()
                }
            }
            else -> state.copy()
        }
    }

    override fun sendAction(action: MainViewAction, coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            _userIntent.send(action)
        }
    }

    override fun sendAction(action: MainViewAction) {
        sendAction(action = action, viewModelScope)
    }
}