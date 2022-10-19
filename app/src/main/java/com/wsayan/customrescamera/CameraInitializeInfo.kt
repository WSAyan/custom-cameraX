package com.wsayan.customrescamera

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CameraInitializeInfo(
    val title: String? = null,
    val hasFlashOption: Boolean = false,
    val hasSwitchCameraOption: Boolean = false,
    val hasCardCropper: Boolean = false,
    val defaultLensFacing: Int? = null,
    val customResolutionWidth: Int? = null,
    val customResolutionHeight: Int? = null,
) : Parcelable
