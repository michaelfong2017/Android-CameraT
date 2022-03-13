package com.robocore.camerat.ui

import android.Manifest
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.robocore.camerat.*
import kotlinx.coroutines.CoroutineScope

@Composable
fun CameraView(viewModel: IMainViewModel = hiltViewModel()) {
    var init by remember {
        mutableStateOf(true)
    }
    val activity = LocalContext.current
    val requestPermission =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { map ->
            handlePermissionResult(viewModel = viewModel, map = map)
        }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(key1 = init) {
        if (init) {
            init = false
            requestPermission.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    val viewState by
    viewModel.viewState
        .observeAsState(
            MainViewState()
        )
    LaunchedEffect(key1 = viewState.havePermission) {
        if (viewState.havePermission) {
            viewModel.sendAction(
                MainViewAction(
                    MainViewAction.MainViewActionValue.CREATE_LOCAL,
                    mapOf()
                )
            )
        }
    }

    if (viewState.havePermission) {
        CameraViewWithPermission(viewState = viewState, viewModel = viewModel)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "You need to request permission"
            )
        }
    }
}

@Composable
fun CameraViewWithPermission(viewState: MainViewState, viewModel: IMainViewModel) {
    val localSurfaceView = viewState.localSurfaceView

    localSurfaceView?.let { sv ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(factory = {
                sv.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            }, update = {
//                it.setZOrderMediaOverlay(isOverlay)
            }
            )
        }
    }
}

private fun handlePermissionResult(viewModel: IMainViewModel, map: Map<String, Boolean>) {
    var isAllGranted = true
    map.forEach { (_, o) ->
        if (!o) {
            isAllGranted = false
            return@forEach
        }
    }
    viewModel.sendAction(
        MainViewAction(
            MainViewAction.MainViewActionValue.CHANGE_PERMISSION,
            mapOf(MainViewAction.MainViewActionMapKey.PERMISSION_KEY to isAllGranted)
        )
    )
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
fun CameraViewPreview() {
    CameraView(viewModel = MainViewModelPreview())
}

class MainViewModelPreview : IMainViewModel {
    override val viewState: LiveData<MainViewState> = MutableLiveData()

    override fun sendAction(action: MainViewAction, coroutineScope: CoroutineScope) {
        return
    }

    override fun sendAction(action: MainViewAction) {
        return
    }
}