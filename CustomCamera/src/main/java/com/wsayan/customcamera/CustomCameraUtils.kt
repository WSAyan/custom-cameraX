package com.wsayan.customcamera

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import java.io.*

fun Context.getOutputDirectory(dirName: String): File {
    val mediaDir = externalMediaDirs.firstOrNull()?.let {
        File(it, dirName).apply { mkdirs() }
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

fun cropImage(bitmap: Bitmap, frame: View, reference: View): ByteArray {
    val heightOriginal = frame.height
    val widthOriginal = frame.width
    val heightFrame = reference.height
    val widthFrame = reference.width
    val leftFrame = reference.left
    val topFrame = reference.top
    val heightReal = bitmap.height
    val widthReal = bitmap.width
    val widthFinal = widthFrame * widthReal / widthOriginal
    val heightFinal = heightFrame * heightReal / heightOriginal
    val leftFinal = leftFrame * widthReal / widthOriginal
    val topFinal = topFrame * heightReal / heightOriginal
    val bitmapFinal = Bitmap.createBitmap(
        bitmap,
        leftFinal, topFinal, widthFinal, heightFinal
    )
    val stream = ByteArrayOutputStream()
    bitmapFinal.compress(
        Bitmap.CompressFormat.JPEG,
        100,
        stream
    ) //100 is the best quality possibe
    return stream.toByteArray()
}

fun File.saveCroppedImage(context: Context, bytes: ByteArray): File {
    val outStream: FileOutputStream

    try {
        createNewFile()
        outStream = FileOutputStream(this)
        outStream.write(bytes)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(path),
            arrayOf("image/jpeg"), null
        )
        outStream.close()
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return this
}
