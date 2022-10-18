package com.wsayan.customrescamera

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import java.io.File

fun Context.getOutputDirectory(): File {
    val mediaDir = externalMediaDirs.firstOrNull()?.let {
        File(it, applicationContext.resources.getString(R.string.app_name)).apply { mkdirs() }
    }
    return if (mediaDir != null && mediaDir.exists())
        mediaDir else applicationContext.filesDir
}

fun ImageView.loadImage(file: File?) {
    Glide
        .with(context)
        .load(file)
        .centerCrop()
        .into(this);
}