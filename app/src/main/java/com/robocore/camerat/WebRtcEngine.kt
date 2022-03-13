package com.robocore.camerat

import android.content.Context
import android.util.Log
import android.view.View
import dagger.hilt.android.qualifiers.ApplicationContext
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRtcEngine @Inject constructor(@ApplicationContext private val context: Context) {
    private var _peerConnectionFactory: PeerConnectionFactory? = null
    private var _eglBaseContext: EglBase.Context? = null

    /** Local stream */
    private var _localStream: MediaStream? = null
    private var _audioSource: AudioSource? = null
    private var _localAudioTrack: AudioTrack? = null
    private var _videoCapturer: VideoCapturer? = null
    private var _surfaceTextureHelper: SurfaceTextureHelper? = null
    private var _videoSource: VideoSource? = null
    private var _localVideoTrack: VideoTrack? = null

    /** Local stream END */
    private var _localSink: ProxyVideoSink? = null
    private var _localRenderer: SurfaceViewRenderer? = null

    init {
        /***/
        _eglBaseContext = EglBase.create().eglBaseContext
        /***/
        createPeerConnectionFactory()

        /***/
        createLocalStream()
    }

    fun startPreview(isOverlay: Boolean): View? {
        _eglBaseContext?.let {
            _localRenderer = SurfaceViewRenderer(context)
            _localRenderer?.init(_eglBaseContext, null)
            _localRenderer?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            _localRenderer?.setMirror(true)
            _localRenderer?.setZOrderMediaOverlay(isOverlay)
            _localSink = ProxyVideoSink()
            _localSink?.let {
                it.target = _localRenderer
            }
            _localStream?.let {
                if (it.videoTracks.size > 0) {
                    it.videoTracks[0].addSink(_localSink)
                }
            }
            return _localRenderer
        }

        return null
    }

    private fun createLocalStream() {
        _peerConnectionFactory?.let { pcf ->
            _localStream = pcf.createLocalMediaStream("TEMI")

            /** Audio */
            _audioSource = pcf.createAudioSource(MediaConstraints())
            _localAudioTrack = pcf.createAudioTrack("TEMIa0", _audioSource)
            _localStream?.addTrack(_localAudioTrack)

            /** Video */
            _videoCapturer = createVideoCapturer()
            _videoCapturer?.let { cap ->
                _videoSource = pcf.createVideoSource(cap.isScreencast)
                _surfaceTextureHelper =
                    SurfaceTextureHelper.create("CaptureThread", _eglBaseContext)
                cap.initialize(
                    _surfaceTextureHelper,
                    context,
                    _videoSource?.capturerObserver
                )
                cap.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)
                _localVideoTrack = pcf.createVideoTrack("TEMIv0", _videoSource)
                _localStream?.addTrack(_localVideoTrack)
            }
        }
    }

    private fun createPeerConnectionFactory() {
        /***/
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions()
        )

        /***/
        val videoEncoderFactory: VideoEncoderFactory = DefaultVideoEncoderFactory(
            _eglBaseContext, true, true
        )
        for (i in videoEncoderFactory.supportedCodecs.indices) {
            Log.d("Codecs", "Supported codecs: " + videoEncoderFactory.supportedCodecs[i].name)
        }
        val videoDecoderFactory: VideoDecoderFactory = DefaultVideoDecoderFactory(_eglBaseContext)

        /***/
        val audioDeviceModule: AudioDeviceModule =
            JavaAudioDeviceModule.builder(context).createAudioDeviceModule()

        /***/
        val options = PeerConnectionFactory.Options()
        _peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .createPeerConnectionFactory()
    }

    private fun createVideoCapturer(): VideoCapturer? {
        return if (Camera2Enumerator.isSupported(context)) {
            createCameraCapture(Camera2Enumerator(context))
        } else {
            createCameraCapture(Camera1Enumerator(true))
        }
    }

    private fun createCameraCapture(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        // First, try to find back facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        // Back facing camera not found, try something else
        for (deviceName in deviceNames) {
            if (!enumerator.isBackFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }

    companion object {
        private const val VIDEO_RESOLUTION_WIDTH = 1600
        private const val VIDEO_RESOLUTION_HEIGHT = 1200
        private const val FPS = 30
    }
}