package com.robocore.camerat

import android.content.Context
import android.util.Log
import android.view.View
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONTokener
import java.util.*
import kotlin.collections.ArrayList


@Singleton
class WebRtcEngine @Inject constructor(@ApplicationContext private val context: Context) :
    SdpObserver,
    PeerConnection.Observer {
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

    /***/
    private var _iceServer: List<PeerConnection.IceServer>? = null
    private var _peerConnection: PeerConnection? = null
    private var _dataChannelInit: DataChannel.Init? = null
    private var _dataChannel: DataChannel? = null

    /***/
    private var _localSdp: SessionDescription? = null
    private var _remoteSdp: SessionDescription? = null
    private var _pcId: String? = null

    init {
        /***/
        _eglBaseContext = EglBase.create().eglBaseContext
        /***/
        createPeerConnectionFactory()

        /***/
        createLocalStream()
    }

    fun makeCall() {
        /***/
        createPeerConnection()

        /***/
        _peerConnection?.addStream(_localStream)

        /***/
        createDataChannel()

        /***/
        createOffer()
    }

    fun stopCall() {
        if (_localSink != null) {
            _localSink!!.target = null
            _localSink = null
        }
        if (_audioSource != null) {
            _audioSource!!.dispose()
            _audioSource = null
        }
        // 释放摄像头
        if (_videoCapturer != null) {
            try {
                _videoCapturer!!.stopCapture()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            _videoCapturer!!.dispose()
            _videoCapturer = null
        }
        // 释放画布
        if (_surfaceTextureHelper != null) {
            _surfaceTextureHelper!!.dispose()
            _surfaceTextureHelper = null
        }
        if (_videoSource != null) {
            _videoSource!!.dispose()
            _videoSource = null
        }
        if (_localStream != null) {
            _localStream = null
        }
        if (_localRenderer != null) {
            _localRenderer!!.release()
        }

        /***/
        _eglBaseContext = EglBase.create().eglBaseContext
        /***/
        createPeerConnectionFactory()

        /***/
        createLocalStream()
    }

    private fun createOffer() {
        _peerConnection?.createOffer(this, offerOrAnswerConstraint())
    }

    private fun createDataChannel() {
        _dataChannelInit = DataChannel.Init()
        _dataChannelInit?.ordered = true
        _dataChannel = _peerConnection?.createDataChannel("chat", _dataChannelInit)
    }

    private fun createPeerConnection() {
        _iceServer = listOf(
            PeerConnection.IceServer.builder("turn:16.163.180.160:3478?transport=tcp")
            .setUsername("michael")
            .setPassword("P@ssw0rd")
            .createIceServer())
        _iceServer?.let {
            val rtcConfig = PeerConnection.RTCConfiguration(it)
            _peerConnection = _peerConnectionFactory?.createPeerConnection(rtcConfig, this)
        }
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

    private fun offerOrAnswerConstraint(): MediaConstraints {
        val mediaConstraints = MediaConstraints()
        val keyValuePairs: ArrayList<MediaConstraints.KeyValuePair> = ArrayList()
        keyValuePairs.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        keyValuePairs.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        mediaConstraints.mandatory.addAll(keyValuePairs)
        return mediaConstraints
    }

    private fun setLocalDescription(sdp: SessionDescription?) {
        _peerConnection?.setLocalDescription(this, sdp)
        Log.d("setLocalDescription", "sdpString: ${sdp?.description}")
        Log.d("setLocalDescription", "typeString: ${sdp?.type?.name}")
    }

    private fun setRemoteDescription(sdp: SessionDescription?) {
        _peerConnection?.setRemoteDescription(this, sdp)
        Log.d("setRemoteDescription", "sdpString: ${sdp?.description}")
        Log.d("setRemoteDescription", "typeString: ${sdp?.type?.name}")
    }

    companion object {
        private const val VIDEO_RESOLUTION_WIDTH = 1600
        private const val VIDEO_RESOLUTION_HEIGHT = 1200
        private const val FPS = 30
    }

    /** SdpObserver */
    override fun onCreateSuccess(p0: SessionDescription?) {
        Log.i("SdpObserver", "onCreateSuccess")
        val sdpString = p0?.description
        val sdp = SessionDescription(p0?.type, sdpString)
        _localSdp = sdp
        setLocalDescription(sdp)
    }

    override fun onSetSuccess() {
        Log.i("SdpObserver", "onSetSuccess: ${_peerConnection?.signalingState().toString()}")
        if (_peerConnection?.remoteDescription == null) {
            val client: OkHttpClient = OkHttpClient().newBuilder()
                .build()
            val mediaType: okhttp3.MediaType? = "application/json".toMediaTypeOrNull()
            var sdpString: String? = _localSdp?.description
            var typeString: String? = _localSdp?.type?.name
            val requestObj = JSONObject()
            requestObj.put("sdp", sdpString)
            requestObj.put("type", typeString)
            requestObj.put("video_transform", "object detection")
            val body: RequestBody = requestObj.toString().toRequestBody(mediaType)
            val request: Request = Request.Builder()
                .url("http://16.163.180.160:8080/offer")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response: Response = client.newCall(request).execute()
            if (response.code == 200) {
                val responseObj = JSONTokener(response.body?.string()).nextValue() as JSONObject
                sdpString = responseObj.getString("sdp")
                typeString = responseObj.getString("type").uppercase(Locale.getDefault())
                _pcId = responseObj.getString("pc_id")
                Log.d("onSetSuccess", "sdpString: $sdpString")
                Log.d("onSetSuccess", "typeString: $typeString")
                Log.d("onSetSuccess", "_pcId: $_pcId")
                val sdp = SessionDescription(SessionDescription.Type.fromCanonicalForm(typeString), sdpString)
                _remoteSdp = sdp
                setRemoteDescription(sdp)
            }
        }
    }

    override fun onCreateFailure(p0: String?) {
        Log.i("SdpObserver", "onCreateFailure: $p0")
    }

    override fun onSetFailure(p0: String?) {
        Log.i("SdpObserver", "onSetFailure: $p0")
    }
    /** SdpObserver END */

    /** Observer */
    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        Log.i("Observer", "onSignalingChange: $p0")
    }

    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        Log.i("Observer", "onIceConnectionChange: $p0")
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
        Log.i("Observer", "onIceConnectionReceivingChange: $p0")
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        Log.i("Observer", "onIceGatheringChange: $p0")
    }

    override fun onIceCandidate(p0: IceCandidate?) {
        Log.i("Observer", "onIceCandidate: $p0")
        p0?.let {
            val client: OkHttpClient = OkHttpClient().newBuilder()
                .build()
            val mediaType: okhttp3.MediaType? = "application/json".toMediaTypeOrNull()
            val requestObj = JSONObject()
            requestObj.put("candidate", p0.toString())
            requestObj.put("sdpMid", p0.sdpMid)
            requestObj.put("sdpMLineIndex", p0.sdpMLineIndex)
            requestObj.put("pc_id", _pcId.toString())
            val body: RequestBody = requestObj.toString().toRequestBody(mediaType)
            val request: Request = Request.Builder()
                .url("http://16.163.180.160:8080/new-ice-candidate")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response: Response = client.newCall(request).execute()
            if (response.code == 200) {
                val responseObj = JSONTokener(response.body?.string()).nextValue() as JSONObject
                val pcId = responseObj.getString("pc_id")
                Log.d("onIceCandidate", "pcId: $pcId")
            }
        }
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        Log.i("Observer", "onIceCandidatesRemoved")
    }

    override fun onAddStream(p0: MediaStream?) {
        Log.i("Observer", "onAddStream")
    }

    override fun onRemoveStream(p0: MediaStream?) {
        Log.i("Observer", "onRemoveStream")
    }

    override fun onDataChannel(p0: DataChannel?) {
        Log.i("Observer", "onDataChannel")
    }

    override fun onRenegotiationNeeded() {
        Log.i("Observer", "onRenegotiationNeeded")
    }

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        Log.i("Observer", "onAddTrack: $p1.size")
    }
    /** Observer END */
}