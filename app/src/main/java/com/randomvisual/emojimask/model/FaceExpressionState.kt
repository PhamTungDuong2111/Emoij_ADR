package com.randomvisual.emojimask.model

import android.graphics.PointF
import androidx.annotation.StringRes
import com.randomvisual.emojimask.R

data class FaceExpressionState(
    val isFaceDetected: Boolean = false,
    val trackingId: Int? = null,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val faceWidth: Float = 0f,
    val faceHeight: Float = 0f,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val smileRatio: Float = 0f,
    val leftEyeOpenRatio: Float = 1f,
    val rightEyeOpenRatio: Float = 1f,
    val mouthOpenRatio: Float = 0f,
    val mouthPuckerRatio: Float = 0f,
    val browFurrowRatio: Float = 0f,
    val eyebrowsRaisedRatio: Float = 0f,
    val mouthFrownRatio: Float = 0f,
    // Contour points (Image Analysis space)
    val faceContour: List<PointF> = emptyList(),
    val leftEyeContour: List<PointF> = emptyList(),
    val rightEyeContour: List<PointF> = emptyList(),
    val upperLipTop: List<PointF> = emptyList(),
    val upperLipBottom: List<PointF> = emptyList(),
    val lowerLipTop: List<PointF> = emptyList(),
    val lowerLipBottom: List<PointF> = emptyList(),
    val noseBridge: List<PointF> = emptyList(),
    val noseBottom: List<PointF> = emptyList()
) {
    @StringRes
    fun getDetectedEmotionRes(): Int {
        if (!isFaceDetected) return R.string.no_face_detected

        return when {
            // Kissing: puckered mouth
            mouthPuckerRatio > 0.30f -> R.string.expr_kissing
            // Laughing: smiling + mouth open wide
            smileRatio > 0.40f && mouthOpenRatio > 0.25f -> R.string.expr_laughing
            // Frowning: brow furrowed or mouth frowning (higher threshold to avoid false positives)
            browFurrowRatio > 0.45f || (mouthFrownRatio > 0.40f && smileRatio < 0.25f) -> R.string.expr_frowning
            // Surprised: open mouth wide or eyebrows raised
            mouthOpenRatio > 0.50f || eyebrowsRaisedRatio > 0.45f -> R.string.expr_surprised
            // Both eyes closed / Blinking
            leftEyeOpenRatio < 0.42f && rightEyeOpenRatio < 0.42f -> R.string.expr_eyes_closed
            // Winking left eye (subject's left closed, right open)
            leftEyeOpenRatio < 0.42f && rightEyeOpenRatio > 0.55f -> R.string.expr_winking_left
            // Winking right eye (subject's right closed, left open)
            rightEyeOpenRatio < 0.42f && leftEyeOpenRatio > 0.55f -> R.string.expr_winking_right
            // Smiling
            smileRatio > 0.35f -> R.string.expr_smiling
            // Mouth open
            mouthOpenRatio > 0.25f -> R.string.expr_mouth_open
            // Neutral
            else -> R.string.expr_neutral
        }
    }
}
