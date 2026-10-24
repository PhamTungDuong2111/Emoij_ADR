package com.randomvisual.emojimask.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.randomvisual.emojimask.model.EmojiMaskType
import com.randomvisual.emojimask.model.FaceExpressionState
import kotlin.math.*

class EmojiMaskOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentMaskType: EmojiMaskType = EmojiMaskType.defaultMask()
    private var faceStates: List<FaceExpressionState> = emptyList()
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var isFrontCamera: Boolean = true

    // Mode: AR Cutout Mode (reveals real eyes, real mouth/tongue, 3D nose) vs 2D Cartoon Mode
    var isCutoutMode: Boolean = true
        private set

    // Animation ticker for dynamic elements like tears, sparkle, party confetti
    private var animPhase: Float = 0f
    private val tickerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animPhase = it.animatedValue as Float
            if (faceStates.isNotEmpty()) {
                invalidate()
            }
        }
    }

    init {
        tickerAnimator.start()
    }

    fun setMaskType(maskType: EmojiMaskType) {
        currentMaskType = maskType
        invalidate()
    }

    fun setCutoutMode(enabled: Boolean) {
        isCutoutMode = enabled
        invalidate()
    }

    fun toggleCutoutMode(): Boolean {
        isCutoutMode = !isCutoutMode
        invalidate()
        return isCutoutMode
    }

    fun updateFaceStates(states: List<FaceExpressionState>, imgW: Int, imgH: Int, frontCam: Boolean) {
        faceStates = states
        imageWidth = imgW
        imageHeight = imgH
        isFrontCamera = frontCam
        postInvalidateOnAnimation()
    }

    fun updateFaceState(state: FaceExpressionState, imgW: Int, imgH: Int, frontCam: Boolean) {
        faceStates = if (state.isFaceDetected) listOf(state) else emptyList()
        imageWidth = imgW
        imageHeight = imgH
        isFrontCamera = frontCam
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faceStates.isEmpty() || imageWidth <= 0 || imageHeight <= 0) {
            return
        }

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        // Calculate aspect fill scale
        val scale = max(viewW / imageWidth, viewH / imageHeight)
        val offsetX = (viewW - imageWidth * scale) / 2f
        val offsetY = (viewH - imageHeight * scale) / 2f

        for (state in faceStates) {
            if (!state.isFaceDetected) continue

            val mappedCenterX = if (isFrontCamera) {
                viewW - (state.centerX * scale + offsetX)
            } else {
                state.centerX * scale + offsetX
            }
            val mappedCenterY = state.centerY * scale + offsetY

            val baseRadius = (max(state.faceWidth, state.faceHeight) * scale) * 0.65f
            val radius = max(baseRadius, 50f)

            drawEmojiMask(
                canvas = canvas,
                cx = mappedCenterX,
                cy = mappedCenterY,
                radius = radius,
                state = state,
                maskType = currentMaskType,
                isFrontCam = isFrontCamera,
                phase = animPhase,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                viewW = viewW,
                isCutoutMode = isCutoutMode
            )
        }
    }

    companion object {
        fun drawEmojiMask(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float,
            state: FaceExpressionState,
            maskType: EmojiMaskType,
            isFrontCam: Boolean,
            phase: Float,
            scale: Float = 1f,
            offsetX: Float = 0f,
            offsetY: Float = 0f,
            viewW: Float = 0f,
            isCutoutMode: Boolean = true
        ) {
            canvas.save()

            // 3D rotation: Roll (tilt)
            val rollAngle = if (isFrontCam) state.roll else -state.roll
            canvas.rotate(rollAngle, cx, cy)

            // Perspective shift based on Yaw and Pitch
            val yawRatio = (state.yaw / 45f).coerceIn(-1f, 1f)
            val pitchRatio = (state.pitch / 30f).coerceIn(-1f, 1f)

            val featureShiftX = (if (isFrontCam) -yawRatio else yawRatio) * (radius * 0.20f)
            val featureShiftY = -pitchRatio * (radius * 0.16f)

            if (isCutoutMode) {
                // ==========================================
                // AR CUTOUT MODE (Physical 3D Mask hugging face)
                // ==========================================
                drawArCutoutMask(
                    canvas = canvas,
                    cx = cx,
                    cy = cy,
                    radius = radius,
                    state = state,
                    maskType = maskType,
                    isFrontCam = isFrontCam,
                    yawRatio = yawRatio,
                    pitchRatio = pitchRatio,
                    featureShiftX = featureShiftX,
                    featureShiftY = featureShiftY,
                    phase = phase,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    viewW = viewW,
                    rollAngle = rollAngle
                )
            } else {
                // ==========================================
                // 2D CARTOON MODE (Full face emoji)
                // ==========================================
                // 1. Draw 3D Base Face
                drawBaseHead(canvas, cx, cy, radius, maskType, yawRatio, pitchRatio, state)

                // 2. Draw Eyes
                drawEyes(canvas, cx + featureShiftX, cy + featureShiftY, radius, state, maskType, isFrontCam, phase)

                // 3. Draw Eyebrows / Forehead features
                drawEyebrows(canvas, cx + featureShiftX, cy + featureShiftY, radius, state, maskType)

                // 4. Draw Mouth with Expression Morphing
                drawMouth(canvas, cx + featureShiftX, cy + featureShiftY, radius, state, maskType, phase)

                // 5. Draw Special Mask Decor
                drawSpecialDecorations(canvas, cx, cy, featureShiftX, featureShiftY, radius, state, maskType, phase)
            }

            canvas.restore()
        }

        // ======================================================================
        // ======================================================================
        // AR CUTOUT MODE IMPLEMENTATION (Clean 3D Mask with Eye & Dynamic Mouth Cutouts)
        // ======================================================================
        private fun drawArCutoutMask(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float,
            state: FaceExpressionState,
            maskType: EmojiMaskType,
            isFrontCam: Boolean,
            yawRatio: Float,
            pitchRatio: Float,
            featureShiftX: Float,
            featureShiftY: Float,
            phase: Float,
            scale: Float,
            offsetX: Float,
            offsetY: Float,
            viewW: Float,
            rollAngle: Float
        ) {
            // Calculate natural face dimensions hugging the user's face snugly
            val actualFaceW = if (state.faceWidth > 10f) state.faceWidth * scale else radius * 1.5f
            val actualFaceH = if (state.faceHeight > 10f) state.faceHeight * scale else radius * 1.8f

            val halfW = actualFaceW * 0.52f
            val halfH = actualFaceH * 0.58f

            val layerMargin = max(halfW, halfH) * 1.5f
            val layerRect = RectF(cx - layerMargin, cy - layerMargin, cx + layerMargin, cy + layerMargin)
            val saveCount = canvas.saveLayer(layerRect, null)

            // 1. DRAW 3D BASE MASK CONTOUR (Anatomical 3D face hugging the face, tapered at chin)
            val facePath = Path().apply {
                val topY = cy - halfH * 0.95f
                val botY = cy + halfH * 1.02f
                val chinW = halfW * 0.66f // Tapered jaw to chin
                val cheekY = cy + halfH * 0.10f

                moveTo(cx, topY)
                // Forehead right to temple
                cubicTo(cx + halfW * 0.65f, topY, cx + halfW, cy - halfH * 0.50f, cx + halfW, cy - halfH * 0.15f)
                // Temple through cheek down to jawline
                cubicTo(cx + halfW, cheekY, cx + halfW * 0.90f, cy + halfH * 0.48f, cx + chinW, cy + halfH * 0.88f)
                // Jaw across rounded chin
                cubicTo(cx + chinW * 0.55f, botY, cx - chinW * 0.55f, botY, cx - chinW, cy + halfH * 0.88f)
                // Left jaw up to cheek
                cubicTo(cx - halfW * 0.90f, cy + halfH * 0.48f, cx - halfW, cheekY, cx - halfW, cy - halfH * 0.15f)
                // Left cheek up through temple to top of forehead
                cubicTo(cx - halfW, cy - halfH * 0.50f, cx - halfW * 0.65f, topY, cx, topY)
                close()
            }

            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val lightX = cx - halfW * 0.28f + (yawRatio * halfW * 0.1f)
            val lightY = cy - halfH * 0.32f + (pitchRatio * halfH * 0.1f)

            when (maskType) {
                EmojiMaskType.ANGRY -> {
                    maskPaint.shader = RadialGradient(
                        lightX, lightY, max(halfW, halfH) * 1.35f,
                        intArrayOf(Color.parseColor("#FF7043"), Color.parseColor("#E53935"), Color.parseColor("#B71C1C")),
                        floatArrayOf(0f, 0.65f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                EmojiMaskType.SCREAM -> {
                    maskPaint.shader = LinearGradient(
                        cx, cy - halfH, cx, cy + halfH,
                        intArrayOf(Color.parseColor("#5C93C4"), Color.parseColor("#A0C4E2"), Color.parseColor("#FFE47A")),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                EmojiMaskType.DEVIL -> {
                    maskPaint.shader = RadialGradient(
                        lightX, lightY, max(halfW, halfH) * 1.35f,
                        intArrayOf(Color.parseColor("#BA68C8"), Color.parseColor("#8E24AA"), Color.parseColor("#4A148C")),
                        floatArrayOf(0f, 0.65f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                EmojiMaskType.ALIEN -> {
                    maskPaint.shader = RadialGradient(
                        lightX, lightY, max(halfW, halfH) * 1.35f,
                        intArrayOf(Color.parseColor("#A5D6A7"), Color.parseColor("#81C784"), Color.parseColor("#2E7D32")),
                        floatArrayOf(0f, 0.65f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                EmojiMaskType.ROBOT -> {
                    maskPaint.shader = LinearGradient(
                        cx - halfW, cy - halfH, cx + halfW, cy + halfH,
                        intArrayOf(Color.parseColor("#ECEFF1"), Color.parseColor("#B0BEC5"), Color.parseColor("#607D8B")),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                EmojiMaskType.CLOWN -> {
                    maskPaint.shader = RadialGradient(
                        lightX, lightY, max(halfW, halfH) * 1.3f,
                        intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#F5F5F5"), Color.parseColor("#D5D5D5")),
                        floatArrayOf(0f, 0.70f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                else -> {
                    // Classic Apple 3D Emoji Golden-Yellow Mask
                    maskPaint.shader = RadialGradient(
                        lightX, lightY, max(halfW, halfH) * 1.35f,
                        intArrayOf(
                            Color.parseColor("#FFF9C4"), // soft highlight center
                            Color.parseColor("#FFD54F"), // core golden yellow
                            Color.parseColor("#FFA000"), // body amber
                            Color.parseColor("#E65100"), // rich shadow rim
                            Color.parseColor("#8D2600")  // ambient rim occlusion
                        ),
                        floatArrayOf(0f, 0.40f, 0.72f, 0.92f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
            }

            // Draw anatomical 3D face hugging the user
            canvas.drawPath(facePath, maskPaint)

            // Subtle outer edge ambient drop shadow for realistic 3D mask depth
            val edgeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(halfW * 0.04f, 3.5f)
                color = Color.parseColor("#38000000")
            }
            canvas.drawPath(facePath, edgeShadowPaint)

            // Soft 3D specular highlight inside forehead
            val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 60
            }
            val glossW = halfW * 0.32f
            val glossH = halfH * 0.14f
            val glossRect = RectF(
                cx - halfW * 0.38f - glossW / 2,
                cy - halfH * 0.52f - glossH / 2,
                cx - halfW * 0.38f + glossW / 2,
                cy - halfH * 0.52f + glossH / 2
            )
            canvas.drawOval(glossRect, glossPaint)

            // 2. SUBTLE 3D NOSE TIP SHADING (Natural 3D mask contour)
            draw3DNoseBridge(canvas, cx + featureShiftX, cy + featureShiftY, halfW, halfH)

            // 3. CUT OUT EYES (PorterDuff CLEAR) OR EXPRESSIVE WINK/LAUGH OVERLAYS
            val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            val eyeBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(halfW * 0.035f, 3f)
                color = Color.parseColor("#45240B") // subtle 3D socket depth
            }

            val eyeSpacing = halfW * 0.38f
            val eyeY = cy + featureShiftY - halfH * 0.18f
            val eyeLeftX = cx + featureShiftX - eyeSpacing
            val eyeRightX = cx + featureShiftX + eyeSpacing

            val screenLeftOpen = if (isFrontCam) state.leftEyeOpenRatio else state.rightEyeOpenRatio
            val screenRightOpen = if (isFrontCam) state.rightEyeOpenRatio else state.leftEyeOpenRatio

            val eyeCutoutW = halfW * 0.20f
            val eyeCutoutH = halfH * 0.15f

            // LEFT EYE
            if (maskType == EmojiMaskType.LAUGHING || (screenLeftOpen < 0.42f && maskType != EmojiMaskType.SUNGLASSES)) {
                drawWinkArc(canvas, eyeLeftX, eyeY, eyeCutoutW)
            } else if (maskType == EmojiMaskType.HEART_EYES) {
                val heartPath = createHeartPath(eyeLeftX, eyeY, eyeCutoutW * 1.4f)
                canvas.drawPath(heartPath, clearPaint)
                val heartRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = halfW * 0.05f
                    color = Color.parseColor("#E91E63")
                }
                canvas.drawPath(heartPath, heartRimPaint)
            } else if (maskType != EmojiMaskType.SUNGLASSES) {
                val leftEyeRect = RectF(eyeLeftX - eyeCutoutW, eyeY - eyeCutoutH, eyeLeftX + eyeCutoutW, eyeY + eyeCutoutH)
                canvas.drawOval(leftEyeRect, clearPaint)
                canvas.drawOval(leftEyeRect, eyeBevelPaint)
            }

            // RIGHT EYE
            if (maskType == EmojiMaskType.LAUGHING || (screenRightOpen < 0.42f && maskType != EmojiMaskType.SUNGLASSES)) {
                drawWinkArc(canvas, eyeRightX, eyeY, eyeCutoutW)
            } else if (maskType == EmojiMaskType.HEART_EYES) {
                val heartPath = createHeartPath(eyeRightX, eyeY, eyeCutoutW * 1.4f)
                canvas.drawPath(heartPath, clearPaint)
                val heartRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = halfW * 0.05f
                    color = Color.parseColor("#E91E63")
                }
                canvas.drawPath(heartPath, heartRimPaint)
            } else if (maskType != EmojiMaskType.SUNGLASSES) {
                val rightEyeRect = RectF(eyeRightX - eyeCutoutW, eyeY - eyeCutoutH, eyeRightX + eyeCutoutW, eyeY + eyeCutoutH)
                canvas.drawOval(rightEyeRect, clearPaint)
                canvas.drawOval(rightEyeRect, eyeBevelPaint)
            }

            // 4. DYNAMIC EMOJI MOUTH CUTOUT (Chuẩn App Mẫu - Ôm Khuôn Miệng & Lè Lưỡi)
            val smile = state.smileRatio
            val openRatio = state.mouthOpenRatio
            val pucker = state.mouthPuckerRatio
            val frown = max(state.mouthFrownRatio, state.browFurrowRatio * 0.5f)

            var mX = cx + featureShiftX
            var localMouthTopY = cy + featureShiftY + halfH * 0.22f

            if (state.upperLipTop.isNotEmpty() && viewW > 0f) {
                val rawLipX = state.upperLipTop.map { it.x }.average().toFloat()
                val rawLipY = state.upperLipTop.map { it.y }.average().toFloat()
                val sx = if (isFrontCam) viewW - (rawLipX * scale + offsetX) else rawLipX * scale + offsetX
                val sy = rawLipY * scale + offsetY

                // Unrotate relative to (cx, cy)
                if (abs(rollAngle) > 0.1f) {
                    val rad = Math.toRadians(-rollAngle.toDouble())
                    val cosA = cos(rad).toFloat()
                    val sinA = sin(rad).toFloat()
                    val dx = sx - cx
                    val dy = sy - cy
                    mX = cx + dx * cosA - dy * sinA
                    localMouthTopY = cy + dx * sinA + dy * cosA
                } else {
                    mX = sx
                    localMouthTopY = sy
                }
            }

            if ((pucker > 0.32f && smile < 0.35f) || maskType == EmojiMaskType.KISSING) {
                // Kissing puckered lips & floating hearts
                val kissY = localMouthTopY + halfH * 0.12f
                drawKissingLips(canvas, mX, kissY, halfW, phase)
            } else if (maskType == EmojiMaskType.MASK) {
                val medY = localMouthTopY + halfH * 0.12f
                drawMedicalMask(canvas, mX, medY, halfW)
            } else {
                // Dynamic dimensions matching authentic emoji mouth geometry
                val mw = halfW * (0.38f + smile * 0.16f + openRatio * 0.10f)
                val mh = halfH * (0.10f + openRatio * 0.50f)
                val mouthCenterY = localMouthTopY + mh * (0.32f + openRatio * 0.15f)

                val mouthPath = Path()
                val cornerY = mouthCenterY - mh * 0.15f + (frown * mh * 0.30f) - (smile * mh * 0.22f)
                val leftCornerX = mX - mw
                val rightCornerX = mX + mw

                if (openRatio > 0.12f) {
                    // Open Mouth / Laughing / Tongue out (Matches Screenshot 2 Left Girl!)
                    mouthPath.moveTo(leftCornerX, cornerY)
                    // Top lip curve: smooth gentle bow
                    mouthPath.quadTo(mX, mouthCenterY - mh * 0.32f, rightCornerX, cornerY)
                    // Deep rounded crescent bowl extending down around jaw and tongue
                    val bottomY = mouthCenterY + mh * 0.88f
                    mouthPath.cubicTo(
                        rightCornerX + mw * 0.05f, bottomY,
                        leftCornerX - mw * 0.05f, bottomY,
                        leftCornerX, cornerY
                    )
                    mouthPath.close()
                } else if (smile > 0.18f) {
                    // Smiling crescent / smirk (Matches Screenshot 2 Right Man!)
                    val smileDepth = mh * (0.50f + smile * 0.45f)
                    mouthPath.moveTo(leftCornerX, cornerY)
                    mouthPath.quadTo(mX, cornerY + smileDepth * 0.25f, rightCornerX, cornerY)
                    mouthPath.quadTo(mX, cornerY + smileDepth, leftCornerX, cornerY)
                    mouthPath.close()
                } else if (frown > 0.30f) {
                    // Frowning arch
                    val frownArch = mh * 0.50f
                    mouthPath.moveTo(leftCornerX, cornerY + frownArch)
                    mouthPath.quadTo(mX, cornerY, rightCornerX, cornerY + frownArch)
                    mouthPath.quadTo(mX, cornerY + frownArch * 0.6f, leftCornerX, cornerY + frownArch)
                    mouthPath.close()
                } else {
                    // Neutral relaxed lips
                    val slitH = mh * 0.36f
                    val neutRect = RectF(leftCornerX, mouthCenterY - slitH, rightCornerX, mouthCenterY + slitH)
                    mouthPath.addRoundRect(neutRect, slitH, slitH, Path.Direction.CW)
                }

                // If mouth is open, draw dark emoji mouth cavity background behind the teeth & tongue
                if (openRatio > 0.18f) {
                    val cavityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#280F05")
                    }
                    canvas.drawPath(mouthPath, cavityPaint)
                }

                // Cut out single, clean, smooth opening revealing teeth & tongue!
                canvas.drawPath(mouthPath, clearPaint)

                // Draw rich dark espresso emoji mouth outline/rim
                val mouthRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = max(halfW * 0.045f, 4f)
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = Color.parseColor("#210B03")
                }
                canvas.drawPath(mouthPath, mouthRimPaint)
            }

            // 5. RESTORE CUTOUT LAYER
            canvas.restoreToCount(saveCount)

            // 6. DRAW OVERLAYS & ACCESSORIES (Cat ears/whiskers, Tears of joy, Sunglasses, Horns, Clown nose)
            drawSpecialDecorations(canvas, cx, cy, featureShiftX, featureShiftY, halfW, state, maskType, phase)

            // 7. EYEBROWS
            drawEyebrows(canvas, cx + featureShiftX, cy + featureShiftY, halfW, state, maskType)
        }

        private fun draw3DNoseBridge(canvas: Canvas, cx: Float, cy: Float, halfW: Float, halfH: Float) {
            val noseY = cy + halfH * 0.05f
            val noseW = halfW * 0.13f
            val noseH = halfH * 0.12f

            // Soft 3D nose bridge highlight
            val noseHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 50
                strokeWidth = max(halfW * 0.04f, 3f)
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(cx - noseW * 0.15f, noseY - noseH * 0.6f, cx - noseW * 0.15f, noseY + noseH * 0.1f, noseHighlight)

            // Soft warm 3D shadow under nose tip
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#30000000")
                style = Paint.Style.STROKE
                strokeWidth = max(halfW * 0.04f, 3.5f)
                strokeCap = Paint.Cap.ROUND
            }
            val shadowPath = Path().apply {
                moveTo(cx - noseW, noseY + noseH * 0.25f)
                quadTo(cx, noseY + noseH * 0.55f, cx + noseW, noseY + noseH * 0.25f)
            }
            canvas.drawPath(shadowPath, shadowPaint)
        }

        private fun createHeartPath(x: Float, y: Float, size: Float): Path {
            return Path().apply {
                moveTo(x, y + size * 0.55f)
                cubicTo(
                    x - size * 0.85f, y - size * 0.1f,
                    x - size * 0.85f, y - size * 0.75f,
                    x, y - size * 0.35f
                )
                cubicTo(
                    x + size * 0.85f, y - size * 0.75f,
                    x + size * 0.85f, y - size * 0.1f,
                    x, y + size * 0.55f
                )
                close()
            }
        }

        private fun drawKissingLips(canvas: Canvas, cx: Float, mouthY: Float, radius: Float, phase: Float) {
            val puckerX = cx - radius * 0.04f
            val puckerSize = radius * 0.14f

            val kissPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#212121")
                style = Paint.Style.STROKE
                strokeWidth = radius * 0.075f
                strokeCap = Paint.Cap.ROUND
            }
            val lipFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E91E63")
            }

            val kissPath = Path().apply {
                moveTo(puckerX - puckerSize, mouthY)
                cubicTo(puckerX - puckerSize * 0.5f, mouthY - puckerSize * 0.8f, puckerX + puckerSize * 0.5f, mouthY - puckerSize * 0.8f, puckerX + puckerSize, mouthY)
                cubicTo(puckerX + puckerSize * 0.5f, mouthY + puckerSize * 0.8f, puckerX - puckerSize * 0.5f, mouthY + puckerSize * 0.8f, puckerX - puckerSize, mouthY)
                close()
            }
            canvas.drawPath(kissPath, lipFill)
            canvas.drawPath(kissPath, kissPaint)
            canvas.drawLine(puckerX - puckerSize * 0.4f, mouthY, puckerX + puckerSize * 0.4f, mouthY, kissPaint)

            // Animated drifting hearts
            val heartDrift = phase * radius * 0.45f
            val heartX = cx + radius * 0.35f + heartDrift * 0.45f
            val heartY = mouthY - radius * 0.12f - heartDrift
            val heartScale = (1f - phase * 0.3f)
            drawHeart(canvas, heartX, heartY, radius * 0.20f * heartScale, Color.parseColor("#E91E63"))
        }

        // ======================================================================
        // 2D CARTOON BASE HEAD & FEATURES
        // ======================================================================
        private fun drawBaseHead(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float,
            maskType: EmojiMaskType,
            yawRatio: Float,
            pitchRatio: Float,
            state: FaceExpressionState
        ) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            when (maskType) {
                EmojiMaskType.ANGRY -> {
                    val shader = RadialGradient(
                        cx - radius * 0.25f, cy - radius * 0.35f, radius * 1.3f,
                        intArrayOf(Color.parseColor("#FF6B4A"), Color.parseColor("#FF3B30"), Color.parseColor("#B71C1C")),
                        floatArrayOf(0f, 0.65f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = shader
                    canvas.drawCircle(cx, cy, radius, paint)
                }
                EmojiMaskType.SCREAM -> {
                    val shader = LinearGradient(
                        cx, cy - radius, cx, cy + radius,
                        intArrayOf(Color.parseColor("#5C93C4"), Color.parseColor("#A0C4E2"), Color.parseColor("#FFE47A")),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = shader
                    val oval = RectF(cx - radius * 0.88f, cy - radius * 1.08f, cx + radius * 0.88f, cy + radius * 1.08f)
                    canvas.drawOval(oval, paint)
                }
                EmojiMaskType.DEVIL -> {
                    val shader = RadialGradient(
                        cx - radius * 0.25f, cy - radius * 0.35f, radius * 1.3f,
                        intArrayOf(Color.parseColor("#BA68C8"), Color.parseColor("#9C27B0"), Color.parseColor("#4A148C")),
                        floatArrayOf(0f, 0.65f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = shader
                    canvas.drawCircle(cx, cy, radius, paint)
                }
                EmojiMaskType.ALIEN -> {
                    val shader = RadialGradient(
                        cx, cy - radius * 0.2f, radius * 1.2f,
                        intArrayOf(Color.parseColor("#A5D6A7"), Color.parseColor("#81C784"), Color.parseColor("#388E3C")),
                        floatArrayOf(0f, 0.7f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = shader
                    val path = Path().apply {
                        val ovalTop = RectF(cx - radius * 1.05f, cy - radius * 1.15f, cx + radius * 1.05f, cy + radius * 0.1f)
                        arcTo(ovalTop, 180f, 180f)
                        cubicTo(cx + radius * 0.95f, cy + radius * 0.4f, cx + radius * 0.45f, cy + radius * 1.05f, cx, cy + radius * 1.12f)
                        cubicTo(cx - radius * 0.45f, cy + radius * 1.05f, cx - radius * 0.95f, cy + radius * 0.4f, cx - radius * 1.05f, cy - radius * 0.5f)
                        close()
                    }
                    canvas.drawPath(path, paint)
                }
                EmojiMaskType.ROBOT -> {
                    val shader = LinearGradient(
                        cx - radius, cy - radius, cx + radius, cy + radius,
                        intArrayOf(Color.parseColor("#CFD8DC"), Color.parseColor("#90A4AE"), Color.parseColor("#607D8B")),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = shader
                    val rect = RectF(cx - radius * 0.95f, cy - radius * 0.95f, cx + radius * 0.95f, cy + radius * 0.95f)
                    canvas.drawRoundRect(rect, radius * 0.35f, radius * 0.35f, paint)

                    val earPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#78909C") }
                    canvas.drawCircle(cx - radius * 1.02f, cy, radius * 0.14f, earPaint)
                    canvas.drawCircle(cx + radius * 1.02f, cy, radius * 0.14f, earPaint)
                }
                EmojiMaskType.CLOWN -> {
                    val shader = RadialGradient(
                        cx - radius * 0.2f, cy - radius * 0.3f, radius * 1.2f,
                        intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#F5F5F5"), Color.parseColor("#DEDEDE")),
                        floatArrayOf(0f, 0.75f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = shader
                    canvas.drawCircle(cx, cy, radius, paint)
                }
                else -> {
                    // Classic Apple/Google 3D Emoji Yellow Sphere
                    val lightX = cx - radius * 0.28f + (yawRatio * radius * 0.1f)
                    val lightY = cy - radius * 0.32f + (pitchRatio * radius * 0.1f)
                    val shader = RadialGradient(
                        lightX, lightY, radius * 1.25f,
                        intArrayOf(
                            Color.parseColor("#FFF475"), // highlight
                            Color.parseColor("#FFD027"), // core yellow
                            Color.parseColor("#FFA000"), // body amber
                            Color.parseColor("#E65100")  // edge shadow
                        ),
                        floatArrayOf(0f, 0.55f, 0.88f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = shader
                    canvas.drawCircle(cx, cy, radius, paint)

                    // Specular glossy top-left highlight arc
                    val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        alpha = 85
                        style = Paint.Style.STROKE
                        strokeWidth = radius * 0.08f
                        strokeCap = Paint.Cap.ROUND
                    }
                    val specPath = Path().apply {
                        val ovalOuter = RectF(cx - radius * 0.82f, cy - radius * 0.88f, cx + radius * 0.82f, cy + radius * 0.88f)
                        arcTo(ovalOuter, 210f, 60f)
                    }
                    canvas.drawPath(specPath, specPaint)

                    if (state.browFurrowRatio > 0.50f) {
                        val flushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            this.shader = RadialGradient(
                                cx, cy - radius * 0.25f, radius * 0.75f,
                                Color.parseColor("#FF3B30"),
                                Color.TRANSPARENT,
                                Shader.TileMode.CLAMP
                            )
                            alpha = ((state.browFurrowRatio - 0.50f) * 2f * 130).toInt().coerceIn(0, 130)
                        }
                        canvas.drawCircle(cx, cy - radius * 0.25f, radius * 0.75f, flushPaint)
                    }
                }
            }

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = radius * 0.025f
                color = Color.parseColor("#26000000")
            }
            canvas.drawCircle(cx, cy, radius, borderPaint)
        }

        private fun drawEyes(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float,
            state: FaceExpressionState,
            maskType: EmojiMaskType,
            isFrontCam: Boolean,
            phase: Float
        ) {
            val eyeSpacing = radius * 0.38f
            val eyeY = cy - radius * 0.16f
            val eyeLeftX = cx - eyeSpacing
            val eyeRightX = cx + eyeSpacing

            val screenLeftOpen = if (isFrontCam) state.leftEyeOpenRatio else state.rightEyeOpenRatio
            val screenRightOpen = if (isFrontCam) state.rightEyeOpenRatio else state.leftEyeOpenRatio

            when (maskType) {
                EmojiMaskType.HEART_EYES -> {
                    val pulse = 1f + (state.smileRatio * 0.25f) + (sin(phase * Math.PI * 2).toFloat() * 0.08f)
                    val baseSize = radius * 0.26f * pulse
                    drawExpressiveHeartEye(canvas, eyeLeftX, eyeY, baseSize, screenLeftOpen)
                    drawExpressiveHeartEye(canvas, eyeRightX, eyeY, baseSize, screenRightOpen)
                }
                EmojiMaskType.STAR_STRUCK -> {
                    val starSize = radius * 0.28f
                    val starAngle = (phase * 360f) % 360f
                    drawExpressiveStarEye(canvas, eyeLeftX, eyeY, starSize, starAngle, screenLeftOpen)
                    drawExpressiveStarEye(canvas, eyeRightX, eyeY, starSize, -starAngle, screenRightOpen)
                }
                EmojiMaskType.SUNGLASSES -> {}
                EmojiMaskType.LAUGHING -> {
                    val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#212121")
                        style = Paint.Style.STROKE
                        strokeWidth = radius * 0.075f
                        strokeCap = Paint.Cap.ROUND
                    }
                    val arcW = radius * 0.24f
                    val arcH = radius * 0.18f
                    canvas.drawArc(RectF(eyeLeftX - arcW / 2, eyeY - arcH, eyeLeftX + arcW / 2, eyeY + arcH), 200f, 140f, false, arcPaint)
                    canvas.drawArc(RectF(eyeRightX - arcW / 2, eyeY - arcH, eyeRightX + arcW / 2, eyeY + arcH), 200f, 140f, false, arcPaint)
                }
                EmojiMaskType.CRYING -> {
                    val weepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#212121")
                        style = Paint.Style.STROKE
                        strokeWidth = radius * 0.07f
                        strokeCap = Paint.Cap.ROUND
                    }
                    val arcW = radius * 0.26f
                    val arcH = radius * 0.16f
                    canvas.drawArc(RectF(eyeLeftX - arcW / 2, eyeY - arcH, eyeLeftX + arcW / 2, eyeY + arcH), 200f, 140f, false, weepPaint)
                    canvas.drawArc(RectF(eyeRightX - arcW / 2, eyeY - arcH, eyeRightX + arcW / 2, eyeY + arcH), 200f, 140f, false, weepPaint)
                }
                EmojiMaskType.ALIEN -> {
                    drawAlienEye(canvas, eyeLeftX, eyeY, radius, -22f, screenLeftOpen)
                    drawAlienEye(canvas, eyeRightX, eyeY, radius, 22f, screenRightOpen)
                }
                EmojiMaskType.ROBOT -> {
                    drawRobotEye(canvas, eyeLeftX, eyeY, radius, screenLeftOpen)
                    drawRobotEye(canvas, eyeRightX, eyeY, radius, screenRightOpen)
                }
                EmojiMaskType.ZANY -> {
                    val leftWink = screenLeftOpen < 0.42f
                    val rightWink = screenRightOpen < 0.42f
                    if (leftWink) drawWinkArc(canvas, eyeLeftX, eyeY, radius * 0.24f)
                    else drawStandardEye(canvas, eyeLeftX, eyeY, radius * 0.24f, radius * 0.28f, screenLeftOpen)

                    if (rightWink) drawWinkArc(canvas, eyeRightX, eyeY, radius * 0.14f)
                    else drawStandardEye(canvas, eyeRightX, eyeY, radius * 0.12f, radius * 0.14f, screenRightOpen)
                }
                else -> {
                    val eyeW = radius * 0.15f
                    val eyeMaxH = radius * 0.20f
                    drawStandardEye(canvas, eyeLeftX, eyeY, eyeW, eyeMaxH, screenLeftOpen)
                    drawStandardEye(canvas, eyeRightX, eyeY, eyeW, eyeMaxH, screenRightOpen)
                }
            }
        }

        private fun drawStandardEye(canvas: Canvas, x: Float, y: Float, width: Float, maxH: Float, openRatio: Float) {
            if (openRatio < 0.42f) {
                drawWinkArc(canvas, x, y, width)
            } else {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#212121") }
                val curH = max(maxH * 0.25f, maxH * openRatio)
                canvas.drawOval(RectF(x - width, y - curH, x + width, y + curH), paint)

                if (openRatio > 0.45f) {
                    val catchLight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                    val sparkleSize = min(width, curH) * 0.35f
                    canvas.drawCircle(x - width * 0.30f, y - curH * 0.30f, sparkleSize, catchLight)
                }
            }
        }

        private fun drawWinkArc(canvas: Canvas, x: Float, y: Float, width: Float) {
            val winkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#212121")
                style = Paint.Style.STROKE
                strokeWidth = max(width * 0.38f, 6f)
                strokeCap = Paint.Cap.ROUND
            }
            val arcW = width * 1.8f
            val arcH = width * 1.1f
            canvas.drawArc(
                RectF(x - arcW / 2, y - arcH * 0.7f, x + arcW / 2, y + arcH * 0.3f),
                200f, 140f, false, winkPaint
            )
        }

        private fun drawExpressiveHeartEye(canvas: Canvas, x: Float, y: Float, size: Float, openRatio: Float) {
            if (openRatio < 0.42f) {
                drawWinkArc(canvas, x, y, size * 0.7f)
            } else {
                canvas.save()
                canvas.scale(1f, max(0.2f, openRatio), x, y)
                drawHeart(canvas, x, y, size, Color.parseColor("#E91E63"))
                canvas.restore()
            }
        }

        private fun drawExpressiveStarEye(canvas: Canvas, x: Float, y: Float, size: Float, rotation: Float, openRatio: Float) {
            if (openRatio < 0.42f) {
                drawWinkArc(canvas, x, y, size * 0.7f)
            } else {
                canvas.save()
                canvas.scale(1f, max(0.2f, openRatio), x, y)
                drawStar(canvas, x, y, size, rotation)
                canvas.restore()
            }
        }

        private fun drawAlienEye(canvas: Canvas, x: Float, y: Float, radius: Float, angle: Float, openRatio: Float) {
            if (openRatio < 0.35f) {
                drawWinkArc(canvas, x, y, radius * 0.16f)
            } else {
                val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#121212") }
                canvas.save()
                canvas.rotate(angle, x, y)
                canvas.scale(1f, max(0.15f, openRatio), x, y)
                canvas.drawOval(RectF(x - radius * 0.22f, y - radius * 0.35f, x + radius * 0.22f, y + radius * 0.35f), eyePaint)
                canvas.drawCircle(x - radius * 0.06f, y - radius * 0.12f, radius * 0.06f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                canvas.restore()
            }
        }

        private fun drawRobotEye(canvas: Canvas, x: Float, y: Float, radius: Float, openRatio: Float) {
            val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#263238") }
            val eyeSize = radius * 0.22f
            val h = max(4f, eyeSize * openRatio)
            canvas.drawRect(x - eyeSize / 2, y - h / 2, x + eyeSize / 2, y + h / 2, eyePaint)
            if (openRatio > 0.35f) {
                val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00E5FF") }
                val ph = max(2f, eyeSize * 0.3f * openRatio)
                canvas.drawRect(x - eyeSize * 0.3f, y - ph, x + eyeSize * 0.3f, y + ph, pupilPaint)
            }
        }

        private fun drawEyebrows(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float,
            state: FaceExpressionState,
            maskType: EmojiMaskType
        ) {
            val browPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4E342E")
                style = Paint.Style.STROKE
                strokeWidth = radius * 0.07f
                strokeCap = Paint.Cap.ROUND
            }

            val browY = cy - radius * 0.38f
            val browSpacing = radius * 0.38f

            val furrow = state.browFurrowRatio
            val raise = state.eyebrowsRaisedRatio

            when {
                maskType == EmojiMaskType.SCREAM -> {
                    val screamRaise = radius * 0.12f
                    canvas.drawArc(RectF(cx - browSpacing - radius * 0.16f, browY - screamRaise - radius * 0.1f, cx - radius * 0.08f, browY - screamRaise + radius * 0.1f), 200f, 140f, false, browPaint)
                    canvas.drawArc(RectF(cx + radius * 0.08f, browY - screamRaise - radius * 0.1f, cx + browSpacing + radius * 0.16f, browY - screamRaise + radius * 0.1f), 200f, 140f, false, browPaint)
                }
                furrow > 0.45f || maskType == EmojiMaskType.ANGRY || maskType == EmojiMaskType.DEVIL -> {
                    browPaint.color = Color.parseColor("#2E1C14")
                    val furrowStrength = max(furrow, if (maskType == EmojiMaskType.ANGRY || maskType == EmojiMaskType.DEVIL) 0.5f else 0f)
                    browPaint.strokeWidth = radius * (0.045f + furrowStrength * 0.012f)

                    val innerDrop = radius * (0.04f + furrowStrength * 0.08f)
                    val outerY = browY - radius * 0.03f
                    val innerY = browY + innerDrop

                    val leftOuterX = cx - browSpacing - radius * 0.15f
                    val leftInnerX = cx - radius * 0.08f
                    val leftPath = Path().apply {
                        moveTo(leftOuterX, outerY)
                        quadTo((leftOuterX + leftInnerX) / 2f, outerY - radius * 0.02f, leftInnerX, innerY)
                    }
                    canvas.drawPath(leftPath, browPaint)

                    val rightInnerX = cx + radius * 0.08f
                    val rightOuterX = cx + browSpacing + radius * 0.15f
                    val rightPath = Path().apply {
                        moveTo(rightInnerX, innerY)
                        quadTo((rightInnerX + rightOuterX) / 2f, outerY - radius * 0.02f, rightOuterX, outerY)
                    }
                    canvas.drawPath(rightPath, browPaint)
                }
                else -> {
                    val browRaiseY = (raise * radius * 0.10f)
                    val leftY = browY - browRaiseY
                    val rightY = browY - browRaiseY

                    val arcW = radius * 0.25f
                    val arcH = radius * (0.12f + raise * 0.06f)
                    canvas.drawArc(RectF(cx - browSpacing - arcW / 2, leftY - arcH, cx - browSpacing + arcW / 2, leftY + arcH), 200f, 140f, false, browPaint)
                    canvas.drawArc(RectF(cx + browSpacing - arcW / 2, rightY - arcH, cx + browSpacing + arcW / 2, rightY + arcH), 200f, 140f, false, browPaint)
                }
            }
        }

        private fun drawMouth(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float,
            state: FaceExpressionState,
            maskType: EmojiMaskType,
            phase: Float
        ) {
            val mouthY = cy + radius * 0.28f
            val smile = state.smileRatio
            val openRatio = state.mouthOpenRatio
            val pucker = state.mouthPuckerRatio
            val frown = max(state.mouthFrownRatio, state.browFurrowRatio * 0.5f)

            if (maskType == EmojiMaskType.ROBOT) {
                drawRobotMouth(canvas, cx, mouthY, radius)
                return
            }
            if (maskType == EmojiMaskType.MASK) {
                drawMedicalMask(canvas, cx, mouthY, radius)
                return
            }
            if (maskType == EmojiMaskType.SCREAM && openRatio < 0.2f) {
                drawScreamMouth(canvas, cx, mouthY, radius, openRatio)
                return
            }

            when {
                // Kissing
                (pucker > 0.28f && smile < 0.35f && openRatio < 0.28f) ||
                (maskType == EmojiMaskType.KISSING && smile < 0.35f && openRatio < 0.28f && frown < 0.28f) -> {
                    drawKissingLips(canvas, cx, mouthY, radius, phase)
                }

                // Open Mouth / Laughing
                openRatio > 0.22f || (maskType == EmojiMaskType.SCREAM && smile < 0.25f) -> {
                    if (maskType == EmojiMaskType.SCREAM) {
                        drawScreamMouth(canvas, cx, mouthY, radius, openRatio)
                    } else {
                        val mw = radius * (0.35f + smile * 0.18f)
                        val mh = radius * (0.28f + openRatio * 0.40f)

                        val mouthPath = Path().apply {
                            moveTo(cx - mw, mouthY)
                            quadTo(cx, mouthY - radius * 0.06f, cx + mw, mouthY)
                            cubicTo(cx + mw * 0.9f, mouthY + mh, cx - mw * 0.9f, mouthY + mh, cx - mw, mouthY)
                            close()
                        }
                        val mouthCavityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#212121") }
                        canvas.drawPath(mouthPath, mouthCavityPaint)

                        canvas.save()
                        canvas.clipPath(mouthPath)

                        val teethPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                        canvas.drawRoundRect(
                            RectF(cx - mw * 0.85f, mouthY - radius * 0.08f, cx + mw * 0.85f, mouthY + radius * 0.14f),
                            8f, 8f, teethPaint
                        )

                        val tonguePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF2D55") }
                        canvas.drawCircle(cx, mouthY + mh * 0.85f, mw * 0.55f, tonguePaint)
                        canvas.restore()
                    }
                }

                // Frowning / Sad / Angry
                (frown > 0.40f && smile < 0.25f) ||
                (maskType == EmojiMaskType.ANGRY && smile < 0.25f && openRatio < 0.22f) -> {
                    val frownStrength = max(frown, if (maskType == EmojiMaskType.ANGRY) 0.7f else 0f)
                    val mw = radius * 0.36f
                    val frownDepth = radius * (0.08f + frownStrength * 0.16f)

                    val frownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#212121")
                        style = Paint.Style.STROKE
                        strokeWidth = radius * 0.08f
                        strokeCap = Paint.Cap.ROUND
                    }
                    val path = Path().apply {
                        moveTo(cx - mw, mouthY + frownDepth * 0.6f)
                        quadTo(cx, mouthY - frownDepth * 0.6f, cx + mw, mouthY + frownDepth * 0.6f)
                    }
                    canvas.drawPath(path, frownPaint)
                }

                // Smiling
                smile > 0.20f -> {
                    val mw = radius * (0.35f + smile * 0.18f)
                    val smileDepth = radius * (0.12f + smile * 0.20f)

                    val smileCurvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#212121")
                        style = Paint.Style.STROKE
                        strokeWidth = radius * 0.08f
                        strokeCap = Paint.Cap.ROUND
                    }
                    val path = Path().apply {
                        moveTo(cx - mw, mouthY)
                        quadTo(cx, mouthY + smileDepth, cx + mw, mouthY)
                    }
                    canvas.drawPath(path, smileCurvePaint)

                    if (smile > 0.35f) {
                        val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#FF5252")
                            alpha = (smile * 110).toInt().coerceIn(30, 110)
                        }
                        canvas.drawCircle(cx - radius * 0.55f, mouthY - radius * 0.08f, radius * 0.16f, blushPaint)
                        canvas.drawCircle(cx + radius * 0.55f, mouthY - radius * 0.08f, radius * 0.16f, blushPaint)
                    }
                }

                // Neutral
                else -> {
                    val mw = radius * 0.32f
                    val neutralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#212121")
                        style = Paint.Style.STROKE
                        strokeWidth = radius * 0.075f
                        strokeCap = Paint.Cap.ROUND
                    }
                    val path = Path().apply {
                        moveTo(cx - mw, mouthY)
                        quadTo(cx, mouthY + radius * 0.03f, cx + mw, mouthY)
                    }
                    canvas.drawPath(path, neutralPaint)
                }
            }
        }

        private fun drawRobotMouth(canvas: Canvas, cx: Float, mouthY: Float, radius: Float) {
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#263238")
                strokeWidth = radius * 0.045f
            }
            val bgMouth = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#ECEFF1") }
            val mw = radius * 0.42f
            val mh = radius * 0.18f
            canvas.drawRect(cx - mw, mouthY - mh / 2, cx + mw, mouthY + mh / 2, bgMouth)
            for (i in -3..3) {
                val barX = cx + i * (radius * 0.11f)
                canvas.drawLine(barX, mouthY - mh / 2, barX, mouthY + mh / 2, barPaint)
            }
        }

        private fun drawMedicalMask(canvas: Canvas, cx: Float, mouthY: Float, radius: Float) {
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#80DEEA") }
            val mw = radius * 0.72f
            val mh = radius * 0.52f
            val maskRect = RectF(cx - mw, mouthY - mh * 0.4f, cx + mw, mouthY + mh * 0.8f)
            canvas.drawRoundRect(maskRect, 22f, 22f, maskPaint)

            val pleatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4DD0E1")
                strokeWidth = radius * 0.03f
            }
            canvas.drawLine(cx - mw * 0.85f, mouthY, cx + mw * 0.85f, mouthY, pleatPaint)
            canvas.drawLine(cx - mw * 0.85f, mouthY + mh * 0.35f, cx + mw * 0.85f, mouthY + mh * 0.35f, pleatPaint)
        }

        private fun drawScreamMouth(canvas: Canvas, cx: Float, mouthY: Float, radius: Float, openRatio: Float) {
            val screamMouth = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A1A") }
            val ovalW = radius * 0.28f
            val ovalH = radius * (0.42f + openRatio * 0.25f)
            canvas.drawOval(RectF(cx - ovalW, mouthY - ovalH * 0.2f, cx + ovalW, mouthY + ovalH * 1.1f), screamMouth)
        }

        // ======================================================================
        // SPECIAL DECORATIONS (CAT, LAUGHING TEARS, SUNGLASSES, HORNS, CLOWN NOSE...)
        // ======================================================================
        private fun drawSpecialDecorations(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            shiftX: Float,
            shiftY: Float,
            radius: Float,
            state: FaceExpressionState,
            maskType: EmojiMaskType,
            phase: Float
        ) {
            when (maskType) {
                // 1. CAT MASK (🐱 - Screenshots 1 & 4)
                EmojiMaskType.CAT -> {
                    drawCatFeatures(canvas, cx, cy, shiftX, shiftY, radius)
                }

                // 2. SUNGLASSES (😎)
                EmojiMaskType.SUNGLASSES -> {
                    val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1A1A") }
                    val frameY = cy + shiftY - radius * 0.18f
                    val gw = radius * 0.42f
                    val gh = radius * 0.32f

                    val leftLens = RectF(cx + shiftX - radius * 0.48f - gw / 2, frameY - gh / 2, cx + shiftX - radius * 0.48f + gw / 2, frameY + gh / 2)
                    canvas.drawRoundRect(leftLens, 16f, 16f, glassPaint)

                    val rightLens = RectF(cx + shiftX + radius * 0.48f - gw / 2, frameY - gh / 2, cx + shiftX + radius * 0.48f + gw / 2, frameY + gh / 2)
                    canvas.drawRoundRect(rightLens, 16f, 16f, glassPaint)

                    val bridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#1A1A1A")
                        strokeWidth = radius * 0.08f
                    }
                    canvas.drawLine(leftLens.right, frameY - radius * 0.04f, rightLens.left, frameY - radius * 0.04f, bridgePaint)

                    val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        alpha = 80
                        strokeWidth = radius * 0.05f
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawLine(leftLens.left + gw * 0.2f, leftLens.bottom - gh * 0.2f, leftLens.right - gw * 0.2f, leftLens.top + gh * 0.2f, glossPaint)
                    canvas.drawLine(rightLens.left + gw * 0.2f, rightLens.bottom - gh * 0.2f, rightLens.right - gw * 0.2f, rightLens.top + gh * 0.2f, glossPaint)
                }

                // 3. CRYING (😭)
                EmojiMaskType.CRYING -> {
                    val tearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4FC3F7") }
                    val tearYBase = cy + shiftY - radius * 0.1f
                    val flowOffset = (phase * radius * 0.6f)
                    val tearW = radius * 0.16f
                    val tearH = radius * 0.32f

                    canvas.drawRoundRect(RectF(cx + shiftX - radius * 0.42f - tearW / 2, tearYBase + flowOffset, cx + shiftX - radius * 0.42f + tearW / 2, tearYBase + flowOffset + tearH), 20f, 20f, tearPaint)
                    canvas.drawRoundRect(RectF(cx + shiftX + radius * 0.42f - tearW / 2, tearYBase + flowOffset, cx + shiftX + radius * 0.42f + tearW / 2, tearYBase + flowOffset + tearH), 20f, 20f, tearPaint)
                }

                // 4. LAUGHING TEARS (😂 - Screenshot 1)
                EmojiMaskType.LAUGHING -> {
                    val tearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#29B6F6")
                    }
                    val tearLeftX = cx + shiftX - radius * 0.68f
                    val tearRightX = cx + shiftX + radius * 0.68f
                    val tearY = cy + shiftY - radius * 0.05f

                    // Stream curve of joy tears flowing outward from eye corners
                    val tearSize = radius * 0.22f
                    val leftTearPath = Path().apply {
                        moveTo(tearLeftX, tearY)
                        cubicTo(tearLeftX - tearSize * 1.2f, tearY - tearSize * 0.5f, tearLeftX - tearSize * 1.5f, tearY + tearSize * 1.2f, tearLeftX - tearSize * 0.6f, tearY + tearSize * 1.4f)
                        cubicTo(tearLeftX, tearY + tearSize * 0.9f, tearLeftX, tearY + tearSize * 0.3f, tearLeftX, tearY)
                        close()
                    }
                    val rightTearPath = Path().apply {
                        moveTo(tearRightX, tearY)
                        cubicTo(tearRightX + tearSize * 1.2f, tearY - tearSize * 0.5f, tearRightX + tearSize * 1.5f, tearY + tearSize * 1.2f, tearRightX + tearSize * 0.6f, tearY + tearSize * 1.4f)
                        cubicTo(tearRightX, tearY + tearSize * 0.9f, tearRightX, tearY + tearSize * 0.3f, tearRightX, tearY)
                        close()
                    }
                    canvas.drawPath(leftTearPath, tearPaint)
                    canvas.drawPath(rightTearPath, tearPaint)

                    // Tear highlight shine
                    val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 160 }
                    canvas.drawCircle(tearLeftX - tearSize * 0.6f, tearY + tearSize * 0.4f, tearSize * 0.18f, shinePaint)
                    canvas.drawCircle(tearRightX + tearSize * 0.6f, tearY + tearSize * 0.4f, tearSize * 0.18f, shinePaint)
                }

                // 5. DEVIL HORNS (😈)
                EmojiMaskType.DEVIL -> {
                    val hornPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7B1FA2") }
                    val leftHorn = Path().apply {
                        moveTo(cx - radius * 0.58f, cy - radius * 0.75f)
                        cubicTo(cx - radius * 0.85f, cy - radius * 1.15f, cx - radius * 0.95f, cy - radius * 1.35f, cx - radius * 0.82f, cy - radius * 1.40f)
                        cubicTo(cx - radius * 0.65f, cy - radius * 1.25f, cx - radius * 0.42f, cy - radius * 0.95f, cx - radius * 0.38f, cy - radius * 0.82f)
                        close()
                    }
                    val rightHorn = Path().apply {
                        moveTo(cx + radius * 0.58f, cy - radius * 0.75f)
                        cubicTo(cx + radius * 0.85f, cy - radius * 1.15f, cx + radius * 0.95f, cy - radius * 1.35f, cx + radius * 0.82f, cy - radius * 1.40f)
                        cubicTo(cx + radius * 0.65f, cy - radius * 1.25f, cx + radius * 0.42f, cy - radius * 0.95f, cx + radius * 0.38f, cy - radius * 0.82f)
                        close()
                    }
                    canvas.drawPath(leftHorn, hornPaint)
                    canvas.drawPath(rightHorn, hornPaint)
                }

                // 6. CLOWN (🤡)
                EmojiMaskType.CLOWN -> {
                    val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F44336") }
                    val noseX = cx + shiftX
                    val noseY = cy + shiftY + radius * 0.05f
                    canvas.drawCircle(noseX, noseY, radius * 0.20f, nosePaint)
                    canvas.drawCircle(noseX - radius * 0.05f, noseY - radius * 0.05f, radius * 0.05f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

                    val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5722") }
                    canvas.drawCircle(cx - radius * 0.98f, cy - radius * 0.25f, radius * 0.26f, hairPaint)
                    canvas.drawCircle(cx - radius * 1.05f, cy + radius * 0.05f, radius * 0.24f, hairPaint)
                    canvas.drawCircle(cx + radius * 0.98f, cy - radius * 0.25f, radius * 0.26f, hairPaint)
                    canvas.drawCircle(cx + radius * 1.05f, cy + radius * 0.05f, radius * 0.24f, hairPaint)
                }

                // 7. PARTY HAT (🥳)
                EmojiMaskType.PARTY -> {
                    val hatPath = Path().apply {
                        moveTo(cx - radius * 0.55f, cy - radius * 0.75f)
                        lineTo(cx, cy - radius * 1.65f)
                        lineTo(cx + radius * 0.55f, cy - radius * 0.75f)
                        close()
                    }
                    val hatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9C27B0") }
                    canvas.drawPath(hatPath, hatPaint)
                    val pomPom = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFEB3B") }
                    canvas.drawCircle(cx, cy - radius * 1.65f, radius * 0.12f, pomPom)
                }

                // 8. ROBOT ANTENNA (🤖)
                EmojiMaskType.ROBOT -> {
                    val antennaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#78909C")
                        strokeWidth = radius * 0.06f
                    }
                    canvas.drawLine(cx, cy - radius * 0.95f, cx, cy - radius * 1.35f, antennaPaint)
                    val bulbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF1744") }
                    canvas.drawCircle(cx, cy - radius * 1.35f, radius * 0.12f, bulbPaint)
                }

                // 9. SCREAM HANDS (😱)
                EmojiMaskType.SCREAM -> {
                    val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#90CAF9") }
                    val leftHand = RectF(cx - radius * 1.05f, cy + radius * 0.05f, cx - radius * 0.65f, cy + radius * 0.75f)
                    val rightHand = RectF(cx + radius * 0.65f, cy + radius * 0.05f, cx + radius * 1.05f, cy + radius * 0.75f)
                    canvas.drawRoundRect(leftHand, 30f, 30f, handPaint)
                    canvas.drawRoundRect(rightHand, 30f, 30f, handPaint)
                }
                else -> {}
            }
        }

        // CAT EARS, CAT WHISKERS & NOSE (🐱 - Screenshot 4)
        private fun drawCatFeatures(canvas: Canvas, cx: Float, cy: Float, shiftX: Float, shiftY: Float, radius: Float) {
            val earPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD54F") }
            val innerEarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF80AB") }
            val whiskerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#212121")
                style = Paint.Style.STROKE
                strokeWidth = radius * 0.045f
                strokeCap = Paint.Cap.ROUND
            }

            // Left Cat Ear
            val leftEar = Path().apply {
                moveTo(cx - radius * 0.72f, cy - radius * 0.70f)
                lineTo(cx - radius * 0.92f, cy - radius * 1.45f)
                lineTo(cx - radius * 0.28f, cy - radius * 0.95f)
                close()
            }
            val leftInnerEar = Path().apply {
                moveTo(cx - radius * 0.68f, cy - radius * 0.78f)
                lineTo(cx - radius * 0.85f, cy - radius * 1.32f)
                lineTo(cx - radius * 0.36f, cy - radius * 0.96f)
                close()
            }
            canvas.drawPath(leftEar, earPaint)
            canvas.drawPath(leftInnerEar, innerEarPaint)

            // Right Cat Ear
            val rightEar = Path().apply {
                moveTo(cx + radius * 0.72f, cy - radius * 0.70f)
                lineTo(cx + radius * 0.92f, cy - radius * 1.45f)
                lineTo(cx + radius * 0.28f, cy - radius * 0.95f)
                close()
            }
            val rightInnerEar = Path().apply {
                moveTo(cx + radius * 0.68f, cy - radius * 0.78f)
                lineTo(cx + radius * 0.85f, cy - radius * 1.32f)
                lineTo(cx + radius * 0.36f, cy - radius * 0.96f)
                close()
            }
            canvas.drawPath(rightEar, earPaint)
            canvas.drawPath(rightInnerEar, innerEarPaint)

            // Cat Whiskers on Cheeks
            val cheekY = cy + shiftY + radius * 0.15f
            val whiskerLen = radius * 0.42f

            // Left whiskers
            canvas.drawLine(cx + shiftX - radius * 0.50f, cheekY - radius * 0.08f, cx + shiftX - radius * 0.50f - whiskerLen, cheekY - radius * 0.18f, whiskerPaint)
            canvas.drawLine(cx + shiftX - radius * 0.52f, cheekY, cx + shiftX - radius * 0.52f - whiskerLen, cheekY, whiskerPaint)
            canvas.drawLine(cx + shiftX - radius * 0.50f, cheekY + radius * 0.08f, cx + shiftX - radius * 0.50f - whiskerLen, cheekY + radius * 0.18f, whiskerPaint)

            // Right whiskers
            canvas.drawLine(cx + shiftX + radius * 0.50f, cheekY - radius * 0.08f, cx + shiftX + radius * 0.50f + whiskerLen, cheekY - radius * 0.18f, whiskerPaint)
            canvas.drawLine(cx + shiftX + radius * 0.52f, cheekY, cx + shiftX + radius * 0.52f + whiskerLen, cheekY, whiskerPaint)
            canvas.drawLine(cx + shiftX + radius * 0.50f, cheekY + radius * 0.08f, cx + shiftX + radius * 0.50f + whiskerLen, cheekY + radius * 0.18f, whiskerPaint)

            // Cute Pink Cat Nose
            val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF4081") }
            val noseY = cy + shiftY + radius * 0.08f
            val nosePath = Path().apply {
                moveTo(cx + shiftX - radius * 0.09f, noseY - radius * 0.05f)
                lineTo(cx + shiftX + radius * 0.09f, noseY - radius * 0.05f)
                lineTo(cx + shiftX, noseY + radius * 0.06f)
                close()
            }
            canvas.drawPath(nosePath, nosePaint)
        }

        private fun drawHeart(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val path = Path().apply {
                moveTo(x, y + size * 0.6f)
                cubicTo(x - size * 0.85f, y - size * 0.1f, x - size * 0.85f, y - size * 0.75f, x, y - size * 0.35f)
                cubicTo(x + size * 0.85f, y - size * 0.75f, x + size * 0.85f, y - size * 0.1f, x, y + size * 0.6f)
                close()
            }
            canvas.drawPath(path, paint)
        }

        private fun drawStar(canvas: Canvas, x: Float, y: Float, size: Float, rotation: Float) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD700") }
            canvas.save()
            canvas.rotate(rotation, x, y)
            val path = Path()
            val points = 5
            val innerSize = size * 0.45f
            val angleStep = Math.PI / points

            for (i in 0 until (points * 2)) {
                val r = if (i % 2 == 0) size else innerSize
                val angle = i * angleStep - Math.PI / 2
                val px = x + (r * cos(angle)).toFloat()
                val py = y + (r * sin(angle)).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }
}
