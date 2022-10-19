package com.wsayan.customrescamera

import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
    private val _torchFlashEnabled: MutableLiveData<Boolean> = MutableLiveData(false)
    private var torchFlashEnabled: LiveData<Boolean> = _torchFlashEnabled

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

        torchFlashEnabled.observe(this) {
            if (it) {
                binding.flashIV.setImageDrawable(
                    AppCompatResources.getDrawable(
                        this,
                        R.drawable.ic_baseline_flash_off_24
                    )
                )
            } else {
                binding.flashIV.setImageDrawable(
                    AppCompatResources.getDrawable(
                        this,
                        R.drawable.ic_baseline_flash_on_24
                    )
                )
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

        val imageAnalyzer = ImageAnalysis.Builder()
            .build()

        // Select back camera as a default
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        try {
            // Unbind use cases before rebinding
            cameraProvider?.unbindAll()

            val viewPort = ViewPort.Builder(
                Rational(
                    binding.cropAreaView.width,
                    binding.cropAreaView.height
                ), Surface.ROTATION_0
            ).build()

            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview ?: return)
                .addUseCase(imageAnalyzer)
                .addUseCase(imageCapture ?: return)
                //.setViewPort(viewPort)
                .build()

            // Bind use cases to camera
            val camera = cameraProvider?.bindToLifecycle(
                this, cameraSelector, useCaseGroup
            )

            cameraProvider?.bindToLifecycle(
                this, cameraSelector, preview, imageCapture, imageAnalyzer
            )

            camera?.let { handleUIEvents(it) }

            if (BuildConfig.DEBUG) {
                camera?.cameraInfo?.let { observeCameraState(it) }
            }
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun toggleFlash(cameraControl: CameraControl) {
        val isEnabled = !(_torchFlashEnabled.value ?: false)
        _torchFlashEnabled.value = isEnabled
        cameraControl.enableTorch(isEnabled)
    }

    private fun handleUIEvents(camera: Camera) {
        val cameraControl = camera.cameraControl
        val cameraInfo = camera.cameraInfo

        binding.flashIV.setOnClickListener {
            toggleFlash(cameraControl)
        }

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

                        output.savedUri?.apply {
                            cropSavedImage(this)
                        }

                        val intent = Intent()
                        intent.putExtra(IMAGE_EXTRA_KEY, output.savedUri)
                        setResult(ACTIVITY_REQUEST_CODE, intent)
                        finish()
                    }
                }
            )
        }
    }

    private fun cropSavedImage(uri: Uri): Uri {
        val bitmap = BitmapFactory.decodeFile(uri.path)
        val byteArray = cropImage(bitmap, binding.root, binding.cropAreaView)
        return uri.toFile().saveCroppedImage(this@CameraActivity, byteArray).toUri()
    }

    private fun observeCameraState(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.observe(this) { cameraState ->
            run {
                when (cameraState.type) {
                    CameraState.Type.PENDING_OPEN -> {
                        // Ask the user to close other camera apps
                        Toast.makeText(
                            this,
                            "CameraState: Pending Open",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    CameraState.Type.OPENING -> {
                        // Show the Camera UI
                        Toast.makeText(
                            this,
                            "CameraState: Opening",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    CameraState.Type.OPEN -> {
                        // Setup Camera resources and begin processing
                        Toast.makeText(
                            this,
                            "CameraState: Open",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    CameraState.Type.CLOSING -> {
                        // Close camera UI
                        Toast.makeText(
                            this,
                            "CameraState: Closing",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    CameraState.Type.CLOSED -> {
                        // Free camera resources
                        Toast.makeText(
                            this,
                            "CameraState: Closed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            cameraState.error?.let { error ->
                when (error.code) {
                    // Open errors
                    CameraState.ERROR_STREAM_CONFIG -> {
                        // Make sure to setup the use cases properly
                        Toast.makeText(
                            this,
                            "Stream config error",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Opening errors
                    CameraState.ERROR_CAMERA_IN_USE -> {
                        // Close the camera or ask user to close another camera app that's using the
                        // camera
                        Toast.makeText(
                            this,
                            "Camera in use",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                        // Close another open camera in the app, or ask the user to close another
                        // camera app that's using the camera
                        Toast.makeText(
                            this,
                            "Max cameras in use",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                        Toast.makeText(
                            this,
                            "Other recoverable error",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Closing errors
                    CameraState.ERROR_CAMERA_DISABLED -> {
                        // Ask the user to enable the device's cameras
                        Toast.makeText(
                            this,
                            "Camera disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                        // Ask the user to reboot the device to restore camera function
                        Toast.makeText(
                            this,
                            "Fatal error",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Closed errors
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                        // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                        Toast.makeText(
                            this,
                            "Do not disturb mode enabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
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
            val path = this.getOutputDirectory("photos")

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