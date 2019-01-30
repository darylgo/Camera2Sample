package com.darylgo.camera2.sample

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), Handler.Callback {

    companion object {
        private const val TAG: String = "MainActivity"
        private const val REQUEST_PERMISSION_CODE: Int = 1
        private val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        private const val REQUIRED_SUPPORTED_HARDWARE_LEVEL: Int = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
        private const val MSG_OPEN_CAMERA: Int = 1
        private const val MSG_CLOSE_CAMERA: Int = 2
        private const val MSG_CREATE_SESSION: Int = 3
        private const val MSG_CLOSE_SESSION: Int = 4
        private const val MSG_SET_PREVIEW_SIZE: Int = 5
        private const val MSG_START_PREVIEW: Int = 6
        private const val MSG_STOP_PREVIEW: Int = 7
    }

    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    private val cameraManager: CameraManager by lazy { getSystemService(CameraManager::class.java) }
    private val cameraPreview: CameraPreview by lazy { findViewById<CameraPreview>(R.id.camera_preview) }
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var frontCameraId: String? = null
    private var frontCameraCharacteristics: CameraCharacteristics? = null
    private var backCameraId: String? = null
    private var backCameraCharacteristics: CameraCharacteristics? = null
    private var cameraDevice: SettableFuture<CameraDevice> = NullFuture()
    private var cameraCharacteristics: SettableFuture<CameraCharacteristics> = NullFuture()
    private var captureSession: SettableFuture<CameraCaptureSession> = NullFuture()
    private var previewSurfaceTexture: SettableFuture<SurfaceTexture> = NullFuture()
    private var previewSurface: Surface? = null
    private var previewDataImageReader: ImageReader? = null
    private var previewDataSurface: Surface? = null

    @SuppressLint("MissingPermission")
    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_OPEN_CAMERA -> {
                val cameraId = msg.obj as String
                val cameraStateCallback = CameraStateCallback()
                cameraManager.openCamera(cameraId, cameraStateCallback, mainHandler)
                Log.d(TAG, "Handle message: MSG_OPEN_CAMERA")
            }
            MSG_CLOSE_CAMERA -> {
                cameraDevice.get()?.close()
                Log.d(TAG, "Handle message: MSG_CLOSE_CAMERA")
            }
            MSG_CREATE_SESSION -> {
                val sessionStateCallback = SessionStateCallback()
                val outputs = mutableListOf<Surface>()
                val previewSurface = previewSurface
                val previewDataSurface = previewDataSurface
                outputs.add(previewSurface!!)
                if (previewDataSurface != null) {
                    outputs.add(previewDataSurface)
                }
                cameraDevice.get()?.createCaptureSession(outputs, sessionStateCallback, mainHandler)
                Log.d(TAG, "Handle message: MSG_CREATE_SESSION")
            }
            MSG_CLOSE_SESSION -> {
                captureSession.get()?.close()
                Log.d(TAG, "Handle message: MSG_CLOSE_SESSION")
            }
            MSG_SET_PREVIEW_SIZE -> {
                val cameraCharacteristics = cameraCharacteristics.get()
                val previewSurfaceTexture = previewSurfaceTexture.get()
                if (cameraCharacteristics != null && previewSurfaceTexture != null) {
                    // Get optimal preview size according to the specified max width and max height.
                    val maxWidth = msg.arg1
                    val maxHeight = msg.arg2
                    val previewSize = getOptimalSize(cameraCharacteristics, SurfaceTexture::class.java, maxWidth, maxHeight)!!

                    // Set the SurfaceTexture's buffer size with preview size.
                    previewSurfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                    previewSurface = Surface(previewSurfaceTexture)

                    // Set up an ImageReader to receive preview frame data if YUV_420_888 is supported.
                    val imageFormat = ImageFormat.YUV_420_888
                    val streamConfigurationMap = cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
                    if (streamConfigurationMap?.isOutputSupportedFor(imageFormat) == true) {
                        previewDataImageReader = ImageReader.newInstance(previewSize.width, previewSize.height, imageFormat, 3)
                        previewDataImageReader?.setOnImageAvailableListener(OnPreviewDataAvailableListener(), cameraHandler)
                        previewDataSurface = previewDataImageReader?.surface
                    }
                }
                Log.d(TAG, "Handle message: MSG_SET_PREVIEW_SIZE")
            }
            MSG_START_PREVIEW -> {
                val cameraDevice = cameraDevice.get()
                val captureSession = captureSession.get()
                if (cameraDevice != null && captureSession != null) {
                    val requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    val previewSurface = previewSurface
                    val previewDataSurface = previewDataSurface
                    requestBuilder.addTarget(previewSurface!!)
                    if (previewDataSurface != null) {
                        requestBuilder.addTarget(previewDataSurface)
                    }
                    val request = requestBuilder.build()
                    captureSession.setRepeatingRequest(request, RepeatingCaptureStateCallback(), mainHandler)
                }
                Log.d(TAG, "Handle message: MSG_START_PREVIEW")
            }
            MSG_STOP_PREVIEW -> {
                captureSession.get()?.stopRepeating()
                Log.d(TAG, "Handle message: MSG_START_PREVIEW")
            }
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startCameraThread()

        val cameraIdList = cameraManager.cameraIdList
        cameraIdList.forEach { cameraId ->
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            if (cameraCharacteristics.isHardwareLevelSupported(REQUIRED_SUPPORTED_HARDWARE_LEVEL)) {
                if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = cameraId
                    frontCameraCharacteristics = cameraCharacteristics
                } else if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameraId = cameraId
                    backCameraCharacteristics = cameraCharacteristics
                }
            }
        }

        cameraPreview.surfaceTextureListener = PreviewSurfaceTextureListener()
        previewSurfaceTexture = SettableFuture()
    }

    override fun onResume() {
        super.onResume()
        if (checkRequiredPermissions()) {
            openCamera()
            setPreviewSize(1440, 1080)
            createSession()
            startPreview()
        }
    }

    override fun onPause() {
        super.onPause()
        closeCamera()
        previewDataImageReader?.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCameraThread()
    }

    private fun startCameraThread() {
        cameraThread = HandlerThread("CameraThread")
        cameraThread!!.start()
        cameraHandler = Handler(cameraThread!!.looper, this)
    }

    private fun stopCameraThread() {
        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
    }

    @MainThread
    private fun openCamera() {
        // Only open back or front camera.
        val cameraId = backCameraId ?: frontCameraId
        if (cameraId != null) {
            cameraDevice = SettableFuture()
            cameraCharacteristics = SettableFuture()
            cameraHandler?.obtainMessage(MSG_OPEN_CAMERA, cameraId)?.sendToTarget()
        } else {
            throw RuntimeException("Camera id must not be null.")
        }
    }

    @MainThread
    private fun closeCamera() {
        cameraHandler?.sendEmptyMessage(MSG_CLOSE_CAMERA)
    }

    @MainThread
    private fun setPreviewSize(maxWidth: Int, maxHeight: Int) {
        cameraHandler?.obtainMessage(MSG_SET_PREVIEW_SIZE, maxWidth, maxHeight)?.sendToTarget()
    }

    @MainThread
    private fun createSession() {
        captureSession = SettableFuture()
        cameraHandler?.sendEmptyMessage(MSG_CREATE_SESSION)
    }

    @MainThread
    private fun closeSession() {
        cameraHandler?.sendEmptyMessage(MSG_CLOSE_SESSION)
    }

    @MainThread
    private fun startPreview() {
        cameraHandler?.sendEmptyMessage(MSG_START_PREVIEW)
    }

    @MainThread
    private fun stopPreview() {
        cameraHandler?.sendEmptyMessage(MSG_STOP_PREVIEW)
    }

    @WorkerThread
    private fun getOptimalSize(cameraCharacteristics: CameraCharacteristics, clazz: Class<*>, maxWidth: Int, maxHeight: Int): Size? {
        val aspectRatio = maxWidth.toFloat() / maxHeight
        val streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val supportedSizes = streamConfigurationMap?.getOutputSizes(clazz)
        if (supportedSizes != null) {
            for (size in supportedSizes) {
                if (size.width.toFloat() / size.height == aspectRatio && size.height <= maxHeight && size.width <= maxWidth) {
                    return size
                }
            }
        }
        return null
    }

    private fun getDisplayRotation(cameraCharacteristics: CameraCharacteristics): Int {
        val rotation = windowManager.defaultDisplay.rotation
        val degrees = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val sensorOrientation = cameraCharacteristics[CameraCharacteristics.SENSOR_ORIENTATION]!!
        return if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_FRONT) {
            (360 - (sensorOrientation + degrees) % 360) % 360
        } else {
            (sensorOrientation - degrees + 360) % 360
        }
    }

    private fun checkRequiredPermissions(): Boolean {
        val deniedPermissions = mutableListOf<String>()
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
                deniedPermissions.add(permission)
            }
        }
        if (deniedPermissions.isEmpty().not()) {
            requestPermissions(deniedPermissions.toTypedArray(), REQUEST_PERMISSION_CODE)
        }
        return deniedPermissions.isEmpty()
    }

    private inner class CameraStateCallback : CameraDevice.StateCallback() {
        @MainThread
        override fun onOpened(camera: CameraDevice) {
            cameraDevice.set(camera)
            cameraCharacteristics.set(when (camera.id) {
                frontCameraId -> frontCameraCharacteristics
                backCameraId -> backCameraCharacteristics
                else -> null
            })
        }

        @MainThread
        override fun onClosed(camera: CameraDevice) {
            cameraDevice = NullFuture()
            cameraCharacteristics = NullFuture()
        }

        @MainThread
        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice.set(camera)
            closeCamera()
        }

        @MainThread
        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice.set(camera)
            closeCamera()
        }
    }

    private inner class SessionStateCallback : CameraCaptureSession.StateCallback() {
        @MainThread
        override fun onConfigureFailed(session: CameraCaptureSession) {

        }

        @MainThread
        override fun onConfigured(session: CameraCaptureSession) {
            captureSession.set(session)
        }

        @MainThread
        override fun onClosed(session: CameraCaptureSession) {
            captureSession = NullFuture()
        }
    }

    private inner class PreviewSurfaceTextureListener : TextureView.SurfaceTextureListener {
        @MainThread
        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) = Unit

        @MainThread
        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

        @MainThread
        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean = false

        @MainThread
        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
            previewSurfaceTexture.set(surfaceTexture)
        }
    }

    private inner class RepeatingCaptureStateCallback : CameraCaptureSession.CaptureCallback() {
        @MainThread
        override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
            super.onCaptureStarted(session, request, timestamp, frameNumber)
        }

        @MainThread
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            super.onCaptureCompleted(session, request, result)
        }
    }

    private inner class OnPreviewDataAvailableListener : ImageReader.OnImageAvailableListener {

        /**
         * Called every time the preview frame data is available.
         */
        override fun onImageAvailable(imageReader: ImageReader) {
            val image = imageReader.acquireNextImage()
            if (image != null) {
                val planes = image.planes
                val yPlane = planes[0]
                val uPlane = planes[1]
                val vPlane = planes[2]
                val yBuffer = yPlane.buffer // Data from Y channel
                val uBuffer = uPlane.buffer // Data from U channel
                val vBuffer = vPlane.buffer // Data from V channel
            }
            image?.close()
        }
    }

}
