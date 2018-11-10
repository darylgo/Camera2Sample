package com.darylgo.camera2.sample

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.annotation.WorkerThread

class MainActivity : AppCompatActivity() {

    private var frontCameraId: String? = null
    private var frontCameraCharacteristics: CameraCharacteristics? = null

    private var backCameraId: String? = null
    private var backCameraCharacteristics: CameraCharacteristics? = null

    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cameraManager = getSystemService(CameraManager::class.java)
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

    private inner class CameraStateCallback : CameraDevice.StateCallback() {
        @WorkerThread
        override fun onOpened(camera: CameraDevice) {

        }

        @WorkerThread
        override fun onDisconnected(camera: CameraDevice) {

        }

        @WorkerThread
        override fun onError(camera: CameraDevice, error: Int) {

        }
    }

    private inner class CameraCaptureSessionStateCallback : CameraCaptureSession.StateCallback() {
        @WorkerThread
        override fun onConfigureFailed(session: CameraCaptureSession) {

        }

        @WorkerThread
        override fun onConfigured(session: CameraCaptureSession) {

        }
    }

}
