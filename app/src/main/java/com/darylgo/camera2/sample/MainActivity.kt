package com.darylgo.camera2.sample

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import android.widget.Toast
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

        private const val MSG_OPEN_CAMERA: Int = 1
        private const val MSG_CLOSE_CAMERA: Int = 2
    }

    private val cameraManager: CameraManager by lazy { getSystemService(CameraManager::class.java) }
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var frontCameraId: String? = null
    private var frontCameraCharacteristics: CameraCharacteristics? = null
    private var backCameraId: String? = null
    private var backCameraCharacteristics: CameraCharacteristics? = null
    private var cameraDevice: CameraDevice? = null

    private data class OpenCameraMessage(val cameraId: String, val cameraStateCallback: CameraStateCallback)

    @SuppressLint("MissingPermission")
    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_OPEN_CAMERA -> {
                val openCameraMessage = msg.obj as OpenCameraMessage
                val cameraId = openCameraMessage.cameraId
                val cameraStateCallback = openCameraMessage.cameraStateCallback
                cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler)
                Log.d(TAG, "Handle message: MSG_OPEN_CAMERA")
            }
            MSG_CLOSE_CAMERA -> {
                cameraDevice?.close()
                Log.d(TAG, "Handle message: MSG_CLOSE_CAMERA")
            }
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startCameraThread()

        // 遍历所有可用的摄像头 ID，只取出其中的前置和后置摄像头信息。
        val cameraIdList = cameraManager.cameraIdList
        cameraIdList.forEach { cameraId ->
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_FRONT) {
                frontCameraId = cameraId
                frontCameraCharacteristics = cameraCharacteristics
            } else if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK) {
                backCameraId = cameraId
                backCameraCharacteristics = cameraCharacteristics
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkRequiredPermissions()) {
            openCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        closeCamera()
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

    private fun openCamera() {
        // 有限选择后置摄像头，其次才是前置摄像头。
        val cameraId = backCameraId ?: frontCameraId
        if (cameraId != null) {
            val openCameraMessage = OpenCameraMessage(cameraId, CameraStateCallback())
            cameraHandler?.obtainMessage(MSG_OPEN_CAMERA, openCameraMessage)?.sendToTarget()
        } else {
            throw RuntimeException("Camera id must not be null.")
        }
    }

    private fun closeCamera() {
        cameraHandler?.sendEmptyMessage(MSG_CLOSE_CAMERA)
    }

    /**
     * 判断我们需要的权限是否被授予，只要有一个没有授权，我们都会返回 false，并且进行权限申请操作。
     *
     * @return true 权限都被授权
     */
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
        @WorkerThread
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            runOnUiThread { Toast.makeText(this@MainActivity, "相机已开启", Toast.LENGTH_SHORT).show() }
        }

        @WorkerThread
        override fun onClosed(camera: CameraDevice) {
            cameraDevice = null
            runOnUiThread { Toast.makeText(this@MainActivity, "相机已关闭", Toast.LENGTH_SHORT).show() }
        }

        @WorkerThread
        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice = camera
            closeCamera()
        }

        @WorkerThread
        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice = camera
            closeCamera()
        }
    }

}
