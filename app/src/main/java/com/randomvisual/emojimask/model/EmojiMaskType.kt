package com.randomvisual.emojimask.model

import androidx.annotation.StringRes
import com.randomvisual.emojimask.R

enum class EmojiMaskType(
    val id: String,
    val emoji: String,
    @StringRes val titleRes: Int,
    val primaryColor: Int = 0xFFFFD027.toInt()
) {
    GRINNING("grinning", "😀", R.string.mask_grinning, 0xFFFFD027.toInt()),
    LAUGHING("laughing", "😂", R.string.mask_laughing, 0xFFFFD027.toInt()),
    CAT("cat", "🐱", R.string.mask_cat, 0xFFFFD027.toInt()),
    HEART_EYES("heart_eyes", "😍", R.string.mask_heart_eyes, 0xFFFFD027.toInt()),
    KISSING("kissing", "😘", R.string.mask_kissing, 0xFFFFD027.toInt()),
    WINK("wink", "😉", R.string.mask_wink, 0xFFFFD027.toInt()),
    SCREAM("scream", "😱", R.string.mask_scream, 0xFF62A0D8.toInt()),
    SUNGLASSES("sunglasses", "😎", R.string.mask_sunglasses, 0xFFFFD027.toInt()),
    CRYING("crying", "😭", R.string.mask_crying, 0xFFFFD027.toInt()),
    ANGRY("angry", "😡", R.string.mask_angry, 0xFFFF3B30.toInt()),
    ZANY("zany", "🤪", R.string.mask_zany, 0xFFFFD027.toInt()),
    STAR_STRUCK("star_struck", "🤩", R.string.mask_star_struck, 0xFFFFD027.toInt()),
    PARTY("party", "🥳", R.string.mask_party, 0xFFFFD027.toInt()),
    ROBOT("robot", "🤖", R.string.mask_robot, 0xFF90A4AE.toInt()),
    ALIEN("alien", "👽", R.string.mask_alien, 0xFF81C784.toInt()),
    CLOWN("clown", "🤡", R.string.mask_clown, 0xFFFFFFFF.toInt()),
    DEVIL("devil", "😈", R.string.mask_devil, 0xFF9C27B0.toInt()),
    MASK("mask", "😷", R.string.mask_mask, 0xFFFFD027.toInt());

    companion object {
        fun defaultMask(): EmojiMaskType = GRINNING
    }
}
