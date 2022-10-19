package com.wsayan.customcamera

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.camera.core.CameraSelector
import androidx.fragment.app.Fragment

class CustomCamera {
    private constructor(
        context: Activity,
        cameraInitializeInfo: CameraInitializeInfo
    ) {
        val intent = Intent(
            context,
            CameraActivity::class.java
        )
        intent.putExtra(
            CameraActivity.INITIAL_PARAMS_KEY,
            cameraInitializeInfo
        )
        context.startActivityForResult(
            intent,
            REQUEST_CODE
        )
    }

    private constructor(
        context: Fragment,
        cameraInitializeInfo: CameraInitializeInfo
    ) {
        val intent = Intent(
            context.requireContext(),
            CameraActivity::class.java
        )
        intent.putExtra(
            CameraActivity.INITIAL_PARAMS_KEY,
            cameraInitializeInfo
        )
        context.startActivityForResult(
            intent,
            REQUEST_CODE
        )
    }

    companion object {
        val REQUEST_CODE = CameraActivity.CAMERA_ACTIVITY_REQUEST_CODE
        val CAPTURED_IMAGE = CameraActivity.CAPTURED_IMAGE_KEY
    }

    class Builder(
        val activity: Activity? = null,
        val fragment: Fragment? = null,
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

        fun build() {
            if (activity != null) {
                CustomCamera(activity, cameraInitializeInfo)
            } else if (fragment != null) {
                CustomCamera(fragment, cameraInitializeInfo)
            }
        }
    }
}