package com.randomvisual.emojimask.ml

import android.graphics.PointF
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.randomvisual.emojimask.model.FaceExpressionState
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class FaceExpressionTracker(
    private val onExpressionsUpdated: (List<FaceExpressionState>, Int, Int, Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setMinFaceSize(0.12f)
            .enableTracking()
            .build()
    )

    private class FaceSmoother {
        var smoothedCenterX = 0f
        var smoothedCenterY = 0f
        var smoothedWidth = 0f
        var smoothedHeight = 0f
        var smoothedPitch = 0f
        var smoothedYaw = 0f
        var smoothedRoll = 0f
        var smoothedSmile = 0f
        var smoothedLeftEye = 1f
        var smoothedRightEye = 1f
        var smoothedMouthOpen = 0f
        var smoothedPucker = 0f
        var smoothedBrowFurrow = 0f
        var smoothedBrowRaise = 0f
        var smoothedMouthFrown = 0f
        var hasPrevData = false
        var lastSeenFrame = 0L

        private val alphaCoord = 0.45f
        private val alphaExpr = 0.65f
        private val alphaEyeClose = 0.85f // Fast response for winks (<100ms)
        private val alphaEyeOpen = 0.50f  // Smooth return when opening eyes

        fun smooth(
            rawCenterX: Float, rawCenterY: Float, rawWidth: Float, rawHeight: Float,
            rawPitch: Float, rawYaw: Float, rawRoll: Float,
            rawSmile: Float, rawLeftEye: Float, rawRightEye: Float,
            rawMouthOpen: Float, rawPucker: Float,
            rawBrowFurrow: Float, rawBrowRaise: Float, rawMouthFrown: Float,
            frameNum: Long
        ): FloatArray {
            lastSeenFrame = frameNum
            if (!hasPrevData) {
                smoothedCenterX = rawCenterX
                smoothedCenterY = rawCenterY
                smoothedWidth = rawWidth
                smoothedHeight = rawHeight
                smoothedPitch = rawPitch
                smoothedYaw = rawYaw
                smoothedRoll = rawRoll
                smoothedSmile = rawSmile
                smoothedLeftEye = rawLeftEye
                smoothedRightEye = rawRightEye
                smoothedMouthOpen = rawMouthOpen
                smoothedPucker = rawPucker
                smoothedBrowFurrow = rawBrowFurrow
                smoothedBrowRaise = rawBrowRaise
                smoothedMouthFrown = rawMouthFrown
                hasPrevData = true
            } else {
                smoothedCenterX = lerp(smoothedCenterX, rawCenterX, alphaCoord)
                smoothedCenterY = lerp(smoothedCenterY, rawCenterY, alphaCoord)
                smoothedWidth = lerp(smoothedWidth, rawWidth, alphaCoord)
                smoothedHeight = lerp(smoothedHeight, rawHeight, alphaCoord)
                smoothedPitch = lerp(smoothedPitch, rawPitch, alphaExpr)
                smoothedYaw = lerp(smoothedYaw, rawYaw, alphaExpr)
                smoothedRoll = lerp(smoothedRoll, rawRoll, alphaExpr)
                smoothedSmile = lerp(smoothedSmile, rawSmile, alphaExpr)

                val alphaLeft = if (rawLeftEye < smoothedLeftEye) alphaEyeClose else alphaEyeOpen
                val alphaRight = if (rawRightEye < smoothedRightEye) alphaEyeClose else alphaEyeOpen
                smoothedLeftEye = lerp(smoothedLeftEye, rawLeftEye, alphaLeft)
                smoothedRightEye = lerp(smoothedRightEye, rawRightEye, alphaRight)

                smoothedMouthOpen = lerp(smoothedMouthOpen, rawMouthOpen, alphaExpr)
                smoothedPucker = lerp(smoothedPucker, rawPucker, alphaExpr)
                smoothedBrowFurrow = lerp(smoothedBrowFurrow, rawBrowFurrow, alphaExpr)
                smoothedBrowRaise = lerp(smoothedBrowRaise, rawBrowRaise, alphaExpr)
                smoothedMouthFrown = lerp(smoothedMouthFrown, rawMouthFrown, alphaExpr)
            }

            return floatArrayOf(
                smoothedCenterX, smoothedCenterY, smoothedWidth, smoothedHeight,
                smoothedPitch, smoothedYaw, smoothedRoll,
                smoothedSmile, smoothedLeftEye, smoothedRightEye,
                smoothedMouthOpen, smoothedPucker,
                smoothedBrowFurrow, smoothedBrowRaise, smoothedMouthFrown
            )
        }

        private fun lerp(start: Float, stop: Float, amount: Float): Float {
            return start + (stop - start) * amount
        }
    }

    private val smoothers = mutableMapOf<Int, FaceSmoother>()
    private var frameCounter = 0L

    var isFrontCamera: Boolean = true

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        // For coordinates mapping: image width & height in the rotated space
        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val imageWidth = if (isRotated) imageProxy.height else imageProxy.width
        val imageHeight = if (isRotated) imageProxy.width else imageProxy.height

        frameCounter++

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    onExpressionsUpdated(emptyList(), imageWidth, imageHeight, isFrontCamera)
                } else {
                    val states = faces.map { face ->
                        extractExpressionState(face, frameCounter)
                    }

                    // Clean up smoothers not seen in the last 20 frames
                    if (frameCounter % 15 == 0L) {
                        smoothers.entries.removeAll { (frameCounter - it.value.lastSeenFrame) > 20 }
                    }

                    onExpressionsUpdated(states, imageWidth, imageHeight, isFrontCamera)
                }
            }
            .addOnFailureListener {
                onExpressionsUpdated(emptyList(), imageWidth, imageHeight, isFrontCamera)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun extractExpressionState(face: Face, frameNum: Long): FaceExpressionState {
        val box: Rect = face.boundingBox

        val rawCenterX = box.exactCenterX()
        val rawCenterY = box.exactCenterY()
        val rawWidth = box.width().toFloat()
        val rawHeight = box.height().toFloat()

        val rawPitch = face.headEulerAngleX
        val rawYaw = face.headEulerAngleY
        val rawRoll = face.headEulerAngleZ

        // 1. SMILE PROBABILITY (from ML Kit classification)
        val rawSmile = (face.smilingProbability ?: 0f).coerceIn(0f, 1f)

        // Contours
        val faceContour = face.getContour(FaceContour.FACE)?.points ?: emptyList()
        val leftBrowContour = face.getContour(FaceContour.LEFT_EYEBROW_TOP)?.points ?: emptyList()
        val rightBrowContour = face.getContour(FaceContour.RIGHT_EYEBROW_TOP)?.points ?: emptyList()
        val leftEyeContour = face.getContour(FaceContour.LEFT_EYE)?.points ?: emptyList()
        val rightEyeContour = face.getContour(FaceContour.RIGHT_EYE)?.points ?: emptyList()
        val upperLipTop = face.getContour(FaceContour.UPPER_LIP_TOP)?.points ?: emptyList()
        val upperLipBottom = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points ?: emptyList()
        val lowerLipTop = face.getContour(FaceContour.LOWER_LIP_TOP)?.points ?: emptyList()
        val lowerLipBottom = face.getContour(FaceContour.LOWER_LIP_BOTTOM)?.points ?: emptyList()
        val noseBridge = face.getContour(FaceContour.NOSE_BRIDGE)?.points ?: emptyList()
        val noseBottom = face.getContour(FaceContour.NOSE_BOTTOM)?.points ?: emptyList()

        // 2. EYE OPENING PROBABILITY (Fusion of ML Kit classification and Eye Aspect Ratio EAR)
        val mlKitLeft = face.leftEyeOpenProbability
        val mlKitRight = face.rightEyeOpenProbability

        val leftContourRatio = if (leftEyeContour.size >= 8) {
            val minX = leftEyeContour.minOf { it.x }
            val maxX = leftEyeContour.maxOf { it.x }
            val minY = leftEyeContour.minOf { it.y }
            val maxY = leftEyeContour.maxOf { it.y }
            val w = max(1f, maxX - minX)
            val h = max(0f, maxY - minY)
            val ear = h / w
            ((ear - 0.13f) / 0.15f).coerceIn(0f, 1f)
        } else null

        val rightContourRatio = if (rightEyeContour.size >= 8) {
            val minX = rightEyeContour.minOf { it.x }
            val maxX = rightEyeContour.maxOf { it.x }
            val minY = rightEyeContour.minOf { it.y }
            val maxY = rightEyeContour.maxOf { it.y }
            val w = max(1f, maxX - minX)
            val h = max(0f, maxY - minY)
            val ear = h / w
            ((ear - 0.13f) / 0.15f).coerceIn(0f, 1f)
        } else null

        val rawLeftEye = when {
            mlKitLeft != null && leftContourRatio != null -> kotlin.math.min(mlKitLeft, leftContourRatio)
            mlKitLeft != null -> mlKitLeft
            leftContourRatio != null -> leftContourRatio
            else -> 1f
        }.coerceIn(0f, 1f)

        val rawRightEye = when {
            mlKitRight != null && rightContourRatio != null -> kotlin.math.min(mlKitRight, rightContourRatio)
            mlKitRight != null -> mlKitRight
            rightContourRatio != null -> rightContourRatio
            else -> 1f
        }.coerceIn(0f, 1f)

        // 3. MOUTH OPENING RATIO (from inner lip contours)
        var rawMouthOpen = 0f
        if (upperLipBottom.isNotEmpty() && lowerLipTop.isNotEmpty()) {
            val upperMid = upperLipBottom[upperLipBottom.size / 2]
            val lowerMid = lowerLipTop[lowerLipTop.size / 2]
            val verticalGap = max(0f, lowerMid.y - upperMid.y)
            val normGap = verticalGap / max(1f, rawHeight * 0.11f)
            rawMouthOpen = normGap.coerceIn(0f, 1f)
        }

        // 4. CHU MÔI HÔN (Kissing / Mouth Pucker - 😘)
        var rawPucker = 0f
        val allLipPoints = upperLipTop + lowerLipBottom
        if (allLipPoints.isNotEmpty()) {
            val mouthMinX = allLipPoints.minOf { it.x }
            val mouthMaxX = allLipPoints.maxOf { it.x }
            val mouthWidth = max(1f, mouthMaxX - mouthMinX)

            val mouthMinY = allLipPoints.minOf { it.y }
            val mouthMaxY = allLipPoints.maxOf { it.y }
            val mouthHeight = max(1f, mouthMaxY - mouthMinY)

            val widthRatio = mouthWidth / max(1f, rawWidth)
            val aspect = mouthWidth / mouthHeight

            val puckerFromWidth = ((0.36f - widthRatio) / 0.12f).coerceIn(0f, 1f)
            val puckerFromAspect = ((3.2f - aspect) / 1.5f).coerceIn(0f, 1f)

            if (rawSmile < 0.35f && rawMouthOpen < 0.35f) {
                rawPucker = max(puckerFromWidth, (puckerFromWidth * 0.5f + puckerFromAspect * 0.5f)).coerceIn(0f, 1f)
            }
        }

        // 5. CAU MÀY (Brow Furrow / Angry - 😠) & NHƯỚNG MÀY (Eyebrows Raised - 😲)
        var rawBrowFurrow = 0f
        var rawBrowRaise = 0f

        if (leftBrowContour.isNotEmpty() && rightBrowContour.isNotEmpty()) {
            val leftInner = leftBrowContour.minByOrNull { abs(it.x - rawCenterX) } ?: leftBrowContour.last()
            val rightInner = rightBrowContour.minByOrNull { abs(it.x - rawCenterX) } ?: rightBrowContour.first()

            val innerBrowDist = hypot(rightInner.x - leftInner.x, rightInner.y - leftInner.y)
            val normInnerDist = innerBrowDist / max(1f, rawWidth)

            val furrowFromDist = if (normInnerDist < 0.18f) {
                ((0.18f - normInnerDist) / 0.08f).coerceIn(0f, 1f)
            } else 0f

            val leftOuter = leftBrowContour.maxByOrNull { abs(it.x - rawCenterX) } ?: leftBrowContour.first()
            val rightOuter = rightBrowContour.maxByOrNull { abs(it.x - rawCenterX) } ?: rightBrowContour.last()
            val innerAvgY = (leftInner.y + rightInner.y) / 2f
            val outerAvgY = (leftOuter.y + rightOuter.y) / 2f
            val browSlant = (innerAvgY - outerAvgY) / max(1f, rawHeight)
            val furrowFromSlant = if (browSlant > 0.030f) {
                ((browSlant - 0.030f) / 0.040f).coerceIn(0f, 1f)
            } else 0f

            rawBrowFurrow = if (furrowFromDist > 0.05f && furrowFromSlant > 0.05f) {
                (furrowFromDist * 0.5f + furrowFromSlant * 0.5f).coerceIn(0f, 1f)
            } else {
                (max(furrowFromDist, furrowFromSlant) * 0.3f).coerceIn(0f, 1f)
            }

            if (leftEyeContour.isNotEmpty() && rightEyeContour.isNotEmpty()) {
                val leftEyeY = leftEyeContour.map { it.y }.average().toFloat()
                val rightEyeY = rightEyeContour.map { it.y }.average().toFloat()
                val leftBrowY = leftBrowContour.map { it.y }.average().toFloat()
                val rightBrowY = rightBrowContour.map { it.y }.average().toFloat()

                val browEyeDist = ((leftEyeY - leftBrowY) + (rightEyeY - rightBrowY)) / 2f
                val normBrowHeight = browEyeDist / max(1f, rawHeight)

                if (normBrowHeight > 0.17f && rawBrowFurrow < 0.2f) {
                    rawBrowRaise = ((normBrowHeight - 0.17f) / 0.06f).coerceIn(0f, 1f)
                }
            }
        }

        // 6. MOUTH FROWN (Mép miệng trễ xuống - ☹️)
        var rawMouthFrown = 0f
        if (upperLipTop.isNotEmpty() && rawSmile < 0.25f) {
            val leftCorner = upperLipTop.minByOrNull { it.x } ?: upperLipTop.first()
            val rightCorner = upperLipTop.maxByOrNull { it.x } ?: upperLipTop.last()
            val midLip = upperLipTop.minByOrNull { it.y } ?: upperLipTop[upperLipTop.size / 2]

            val cornerAvgY = (leftCorner.y + rightCorner.y) / 2f
            val cornerDrop = (cornerAvgY - midLip.y) / max(1f, rawHeight)

            if (cornerDrop > 0.020f) {
                rawMouthFrown = ((cornerDrop - 0.020f) / 0.030f).coerceIn(0f, 1f)
            }
        }

        // Retrieve or create smoother per trackingId (or spatial bucket if null)
        val trackerKey = face.trackingId ?: (box.centerX() / 100 * 1000 + box.centerY() / 100)
        val smoother = smoothers.getOrPut(trackerKey) { FaceSmoother() }

        val smoothed = smoother.smooth(
            rawCenterX, rawCenterY, rawWidth, rawHeight,
            rawPitch, rawYaw, rawRoll,
            rawSmile, rawLeftEye, rawRightEye,
            rawMouthOpen, rawPucker,
            rawBrowFurrow, rawBrowRaise, rawMouthFrown,
            frameNum
        )

        return FaceExpressionState(
            isFaceDetected = true,
            trackingId = face.trackingId,
            centerX = smoothed[0],
            centerY = smoothed[1],
            faceWidth = smoothed[2],
            faceHeight = smoothed[3],
            pitch = smoothed[4],
            yaw = smoothed[5],
            roll = smoothed[6],
            smileRatio = smoothed[7],
            leftEyeOpenRatio = smoothed[8],
            rightEyeOpenRatio = smoothed[9],
            mouthOpenRatio = smoothed[10],
            mouthPuckerRatio = smoothed[11],
            browFurrowRatio = smoothed[12],
            eyebrowsRaisedRatio = smoothed[13],
            mouthFrownRatio = smoothed[14],
            faceContour = faceContour,
            leftEyeContour = leftEyeContour,
            rightEyeContour = rightEyeContour,
            upperLipTop = upperLipTop,
            upperLipBottom = upperLipBottom,
            lowerLipTop = lowerLipTop,
            lowerLipBottom = lowerLipBottom,
            noseBridge = noseBridge,
            noseBottom = noseBottom
        )
    }

    fun stop() {
        try {
            detector.close()
        } catch (_: Exception) {}
    }
}
