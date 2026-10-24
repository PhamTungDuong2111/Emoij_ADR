package com.randomvisual.emojimask

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.randomvisual.emojimask.databinding.ActivityMainBinding
import com.randomvisual.emojimask.ml.FaceExpressionTracker
import com.randomvisual.emojimask.model.EmojiMaskType
import com.randomvisual.emojimask.model.FaceExpressionState
import com.randomvisual.emojimask.ui.adapter.EmojiMaskAdapter
import com.randomvisual.emojimask.ui.dialog.PhotoPreviewDialog
import com.randomvisual.emojimask.util.LanguageManager
import com.randomvisual.emojimask.view.EmojiMaskOverlayView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService? = null
    private var faceExpressionTracker: FaceExpressionTracker? = null

    private var isFrontCamera = true
    private var flashMode = ImageCapture.FLASH_MODE_AUTO
    private var currentFaceStates: List<FaceExpressionState> = emptyList()
    private var lastImageW = 1
    private var lastImageH = 1

    private lateinit var maskAdapter: EmojiMaskAdapter

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            binding.cardPermissionPrompt.visibility = View.GONE
            startCamera()
        } else {
            binding.cardPermissionPrompt.visibility = View.VISIBLE
            Toast.makeText(this, R.string.permission_denied_toast, Toast.LENGTH_SHORT).show()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val updatedContext = LanguageManager.applySavedLocale(newBase)
        super.attachBaseContext(updatedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupUI()
        checkAndRequestPermissions()
    }

    private fun setupUI() {
        // Language Toggle button
        val currentLang = LanguageManager.getSavedLanguage(this)
        binding.tvCurrentLanguage.text = if (currentLang == LanguageManager.LANG_VI) "🇻🇳 VI" else "🇬🇧 EN"

        binding.btnLanguage.setOnClickListener {
            toggleLanguage()
        }

        // Mode Toggle Button (AR Cutout Mode vs 2D Cartoon Mode)
        binding.tvModeToggle.setText(if (binding.emojiMaskOverlay.isCutoutMode) R.string.mode_ar_cutout else R.string.mode_cartoon)
        binding.btnModeToggle.setOnClickListener {
            val isCutout = binding.emojiMaskOverlay.toggleCutoutMode()
            binding.tvModeToggle.setText(if (isCutout) R.string.mode_ar_cutout else R.string.mode_cartoon)
            val msgRes = if (isCutout) R.string.mode_switched_ar else R.string.mode_switched_cartoon
            Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
        }

        // Mask Carousel
        val masks = EmojiMaskType.values().toList()
        maskAdapter = EmojiMaskAdapter(masks) { selectedMask ->
            binding.emojiMaskOverlay.setMaskType(selectedMask)
        }
        binding.rvEmojiMasks.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = maskAdapter
        }

        // Flip Camera Button
        binding.btnFlipCamera.setOnClickListener {
            isFrontCamera = !isFrontCamera
            faceExpressionTracker?.isFrontCamera = isFrontCamera
            startCamera()
        }

        // Flash Button
        binding.btnFlash.setOnClickListener {
            cycleFlashMode()
        }

        // Shutter Button
        binding.btnCaptureShutter.setOnClickListener {
            capturePhoto()
        }

        // Permission Card Grant button
        binding.btnGrantPermission.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            binding.cardPermissionPrompt.visibility = View.GONE
            startCamera()
        } else {
            binding.cardPermissionPrompt.visibility = View.VISIBLE
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return

        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

        faceExpressionTracker = FaceExpressionTracker { states, imgW, imgH, frontCam ->
            runOnUiThread {
                currentFaceStates = states
                lastImageW = imgW
                lastImageH = imgH

                binding.emojiMaskOverlay.updateFaceStates(states, imgW, imgH, frontCam)

                // Update real-time expression pill
                if (states.isNotEmpty()) {
                    val primaryFace = states[0]
                    if (states.size > 1) {
                        binding.tvExpressionStatus.text = "${getString(primaryFace.getDetectedEmotionRes())} (${states.size})"
                    } else {
                        binding.tvExpressionStatus.setText(primaryFace.getDetectedEmotionRes())
                    }
                    binding.viewTrackingDot.setBackgroundColor(ContextCompat.getColor(this, R.color.badge_green))
                } else {
                    binding.tvExpressionStatus.setText(R.string.no_face_detected)
                    binding.viewTrackingDot.setBackgroundColor(ContextCompat.getColor(this, R.color.text_muted))
                }
            }
        }.apply {
            this.isFrontCamera = this@MainActivity.isFrontCamera
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                cameraExecutor?.let { exec ->
                    it.setAnalyzer(exec, faceExpressionTracker!!)
                }
            }

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cycleFlashMode() {
        val cam = camera ?: return
        when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> {
                flashMode = ImageCapture.FLASH_MODE_ON
                binding.btnFlash.setImageResource(R.drawable.ic_flash_on)
                try { cam.cameraControl.enableTorch(true) } catch (_: Exception) {}
            }
            ImageCapture.FLASH_MODE_ON -> {
                flashMode = ImageCapture.FLASH_MODE_OFF
                binding.btnFlash.setImageResource(R.drawable.ic_flash_off)
                try { cam.cameraControl.enableTorch(false) } catch (_: Exception) {}
            }
            else -> {
                flashMode = ImageCapture.FLASH_MODE_AUTO
                binding.btnFlash.setImageResource(R.drawable.ic_flash_auto)
                try { cam.cameraControl.enableTorch(false) } catch (_: Exception) {}
            }
        }
    }

    private fun capturePhoto() {
        triggerHapticFeedback()
        playShutterFlashEffect()

        val previewBitmap = binding.viewFinder.bitmap
        if (previewBitmap == null) {
            Toast.makeText(this, R.string.photo_save_failed, Toast.LENGTH_SHORT).show()
            return
        }

        // Composite the exact emoji mask onto the high-resolution frame
        val compositedBitmap = Bitmap.createBitmap(previewBitmap.width, previewBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(compositedBitmap)
        canvas.drawBitmap(previewBitmap, 0f, 0f, null)

        if (currentFaceStates.isNotEmpty()) {
            val scale = Math.max(previewBitmap.width.toFloat() / lastImageW, previewBitmap.height.toFloat() / lastImageH)
            val offsetX = (previewBitmap.width - lastImageW * scale) / 2f
            val offsetY = (previewBitmap.height - lastImageH * scale) / 2f

            for (faceState in currentFaceStates) {
                if (!faceState.isFaceDetected) continue

                val mappedCenterX = if (isFrontCamera) {
                    previewBitmap.width - (faceState.centerX * scale + offsetX)
                } else {
                    faceState.centerX * scale + offsetX
                }
                val mappedCenterY = faceState.centerY * scale + offsetY
                val radius = Math.max((Math.max(faceState.faceWidth, faceState.faceHeight) * scale) * 0.65f, 50f)

                EmojiMaskOverlayView.drawEmojiMask(
                    canvas = canvas,
                    cx = mappedCenterX,
                    cy = mappedCenterY,
                    radius = radius,
                    state = faceState,
                    maskType = maskAdapter.getSelectedMask(),
                    isFrontCam = isFrontCamera,
                    phase = 0.5f,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    viewW = previewBitmap.width.toFloat(),
                    isCutoutMode = binding.emojiMaskOverlay.isCutoutMode
                )
            }
        }

        // Show photo preview dialog with Save and Share actions
        val dialog = PhotoPreviewDialog(this, compositedBitmap)
        dialog.show()
    }

    private fun playShutterFlashEffect() {
        binding.shutterFlashView.apply {
            alpha = 0.85f
            visibility = View.VISIBLE
            animate()
                .alpha(0f)
                .setDuration(160)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        visibility = View.GONE
                    }
                })
                .start()
        }
    }

    private fun triggerHapticFeedback() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun toggleLanguage() {
        val currentLang = LanguageManager.getSavedLanguage(this)
        val newLang = if (currentLang == LanguageManager.LANG_VI) LanguageManager.LANG_EN else LanguageManager.LANG_VI
        LanguageManager.saveLanguage(this, newLang)
        recreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
        faceExpressionTracker?.stop()
    }
}
