package com.darylgo.camera2.sample

import android.hardware.camera2.CameraCharacteristics

/**
 * 判断相机的 Hardware Level 是否大于等于指定的 Level。
 */
fun CameraCharacteristics.isHardwareLevelSupported(requiredLevel: Int): Boolean {
    val sortedLevels = intArrayOf(
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
    )
    val deviceLevel = this[CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL]
    if (requiredLevel == deviceLevel) {
        return true
    }
    for (sortedLevel in sortedLevels) {
        if (requiredLevel == sortedLevel) {
            return true
        } else if (deviceLevel == sortedLevel) {
            return false
        }
    }
    return false
}