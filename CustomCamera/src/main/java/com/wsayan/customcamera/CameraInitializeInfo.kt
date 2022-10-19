package com.wsayan.customcamera

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CameraInitializeInfo(
    var title: String? = null,
    var hasFlashOption: Boolean = false,
    var hasSwitchCameraOption: Boolean = false,
    var hasCardCropper: Boolean = false,
    var defaultLensFacing: Int? = null,
    var customResolutionWidth: Int? = null,
    var customResolutionHeight: Int? = null,
) : Parcelable
