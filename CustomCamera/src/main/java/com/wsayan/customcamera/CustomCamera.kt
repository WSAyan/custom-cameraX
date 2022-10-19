package com.wsayan.customcamera

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.camera.core.CameraSelector

class CustomCamera private constructor(
    context: Context,
    cameraInitializeInfo: CameraInitializeInfo
) {

    companion object {
        val REQUEST_CODE = CameraActivity.CAMERA_ACTIVITY_REQUEST_CODE
        val CAPTURED_IMAGE = CameraActivity.CAPTURED_IMAGE_KEY
    }

    init {
        val intent = Intent(
            context,
            CameraActivity::class.java
        )
        intent.putExtra(
            CameraActivity.INITIAL_PARAMS_KEY,
            cameraInitializeInfo
        )
        (context as Activity).startActivityForResult(
            intent,
            REQUEST_CODE
        )
    }

    class Builder(
        val context: Context,
        private var cameraInitializeInfo: CameraInitializeInfo = CameraInitializeInfo(),
    ) {
        fun setTitle(titleText: String) =
            this.apply { this.cameraInitializeInfo.apply { title = titleText } }

        fun showTorchFlash() =
            this.apply { this.cameraInitializeInfo.apply { hasFlashOption = true } }

        fun showCameraRotation() =
            this.apply { this.cameraInitializeInfo.apply { hasSwitchCameraOption = true } }

        fun showCardCropper() =
            this.apply { this.cameraInitializeInfo.apply { hasCardCropper = true } }

        fun setDefaultLens(lensFacing: Int = CameraSelector.LENS_FACING_BACK) =
            this.apply { this.cameraInitializeInfo.apply { defaultLensFacing = lensFacing } }

        fun setResolution(width: Int, height: Int) =
            this.apply {
                this.cameraInitializeInfo.apply {
                    customResolutionWidth = width
                    customResolutionHeight = height
                }
            }

        fun build() = CustomCamera(context, cameraInitializeInfo)
    }
}