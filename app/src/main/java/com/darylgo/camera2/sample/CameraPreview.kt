package com.darylgo.camera2.sample

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

/**
 *
 *
 * @author hjd 2019.01.24
 */
class CameraPreview @JvmOverloads constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int = 0) : TextureView(context, attrs, defStyleAttr) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, width / 3 * 4)
    }
}