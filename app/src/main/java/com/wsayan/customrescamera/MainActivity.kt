package com.wsayan.customrescamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toFile
import com.wsayan.customcamera.CustomCamera
import com.wsayan.customcamera.loadImage
import com.wsayan.customrescamera.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.openCameraBTN.setOnClickListener {
            CustomCamera
                .Builder(this@MainActivity)
                .setTitle("Capture Photo")
                .setResolution(width = 412, height = 847)
                .showTorchFlash()
                .showCardCropper()
                .showCameraRotation()
                .build()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CustomCamera.REQUEST_CODE) {
            val imageUri: Uri? = data?.getParcelableExtra(CustomCamera.CAPTURED_IMAGE)

            binding.previewIV.loadImage(imageUri?.toFile())
        }
    }
}