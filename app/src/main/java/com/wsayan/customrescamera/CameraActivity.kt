package com.wsayan.customrescamera

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.wsayan.customrescamera.databinding.ActivityCameraBinding
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.titleTV.text = "Capture photo"
        binding.closeIV.setOnClickListener {
            finish()
        }

        startCamera()

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.captureIV.setOnClickListener { takePhoto() }

        val orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                imageCapture?.targetRotation = getOrientationFromDegrees(orientation)
            }
        }.apply {
            enable()
        }

        // Setup for button used to switch cameras
        binding.rotateIV.let {

            // Disable the button until the camera is set up
            it.isEnabled = false

            // Listener for button used to switch cameras. Only called if the button is enabled
            it.setOnClickListener {
                lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
                // Re-bind use cases to update selected camera
                bindCameraUseCases()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }

            bindCameraUseCases()

            updateCameraSwitchButton()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val rotation = binding.viewFinder.display.rotation

        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

        val targetResolution =
            if (isLandscape) TARGET_RESOLUTION_LANDSCAPE else TARGET_RESOLUTION

        // Preview
        preview = Preview.Builder()
            .setTargetRotation(rotation)
            .setTargetResolution(targetResolution)
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setTargetRotation(rotation)
            .setTargetResolution(targetResolution)
            .build()

        // Select back camera as a default
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        try {
            // Unbind use cases before rebinding
            cameraProvider?.unbindAll()

            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview ?: return)
                .addUseCase(imageCapture ?: return)
                .build()

            // Bind use cases to camera
            val camera = cameraProvider?.bindToLifecycle(
                this, cameraSelector, useCaseGroup
            )

            camera?.let { handleGestureEvents(it) }

        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun handleGestureEvents(camera: Camera) {
        val cameraControl = camera.cameraControl
        val cameraInfo = camera.cameraInfo

        // Listen to pinch gestures
        val simpleOnScaleGestureListener =
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    pinchZoom(
                        cameraControl = cameraControl,
                        cameraInfo = cameraInfo,
                        detector = detector
                    )
                    return true
                }
            }

        val scaleGestureDetector = ScaleGestureDetector(this, simpleOnScaleGestureListener)

        binding.viewFinder.setOnTouchListener { view: View, motionEvent: MotionEvent ->
            scaleGestureDetector.onTouchEvent(motionEvent)
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> return@setOnTouchListener true
                MotionEvent.ACTION_UP -> {
                    binding.viewFinder.performClick()

                    focusOnTap(cameraControl = cameraControl, motionEvent = motionEvent)

                    return@setOnTouchListener true
                }
                else -> return@setOnTouchListener false
            }
        }
    }

    private fun pinchZoom(
        cameraControl: CameraControl,
        cameraInfo: CameraInfo,
        detector: ScaleGestureDetector
    ) {
        val currentZoomRatio = cameraInfo.zoomState.value?.zoomRatio ?: 0F
        val delta = detector.scaleFactor
        cameraControl.setZoomRatio(currentZoomRatio * delta)
    }

    private fun focusOnTap(cameraControl: CameraControl, motionEvent: MotionEvent) {
        val factory = binding.viewFinder.meteringPointFactory
        val point = factory.createPoint(motionEvent.x, motionEvent.y)
        val action = FocusMeteringAction.Builder(point).build()
        cameraControl.startFocusAndMetering(action)
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        createFile()?.let {
            val outputOptions = ImageCapture.OutputFileOptions.Builder(it).build()

            // Set up image capture listener, which is triggered after photo has
            // been taken
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val msg = "Photo capture succeeded: ${output.savedUri}"
                        Log.d(TAG, msg)

                        val intent = Intent()
                        intent.putExtra(IMAGE_EXTRA_KEY, output.savedUri)
                        setResult(ACTIVITY_REQUEST_CODE, intent)
                        finish()
                    }
                }
            )
        }

    }

    companion object {
        private val TARGET_RESOLUTION = Size(412, 847)
        private val TARGET_RESOLUTION_LANDSCAPE = Size(847, 412)
        private const val TAG = "CameraXApp"
        const val ACTIVITY_REQUEST_CODE = 100
        const val IMAGE_EXTRA_KEY = "_image_extra"
    }


    private fun getOrientationFromDegrees(orientation: Int): Int {
        return when {
            orientation == OrientationEventListener.ORIENTATION_UNKNOWN -> {
                Surface.ROTATION_0
            }
            orientation >= 315 || orientation < 45 -> {
                Surface.ROTATION_0 //portrait
            }
            orientation < 135 -> {
                //Surface.ROTATION_90
                Surface.ROTATION_270 //landscape
            }
            orientation < 225 -> {
                Surface.ROTATION_180
            }
            else -> {
                //Surface.ROTATION_270
                Surface.ROTATION_90
            }
        }
    }

    override fun onBackPressed() {
        finish()
    }

    private fun createFile(): File? {
        return try {
            val path = this.getOutputDirectory()

            // creates folder if not present
            path.mkdir()

            // image file
            val imageFile = path.path + "/" + System.currentTimeMillis() + ".png"

            File(imageFile)
        } catch (ex: Exception) {

            ex.printStackTrace()
            null
        }
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun updateCameraSwitchButton() {
        try {
            binding.rotateIV.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            binding.rotateIV.isEnabled = false
        }
    }

    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Rebind the camera with the updated display metrics
        bindCameraUseCases()

        // Enable or disable switching between cameras
        updateCameraSwitchButton()
    }
}