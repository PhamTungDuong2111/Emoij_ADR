package com.randomvisual.emojimask.ui.dialog

import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.core.content.FileProvider
import com.randomvisual.emojimask.R
import com.randomvisual.emojimask.databinding.DialogPhotoPreviewBinding
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoPreviewDialog(
    context: Context,
    private val photoBitmap: Bitmap
) : Dialog(context, R.style.Theme_EmojiMask) {

    private lateinit var binding: DialogPhotoPreviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogPhotoPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        binding.ivPreviewPhoto.setImageBitmap(photoBitmap)

        binding.btnClosePreview.setOnClickListener { dismiss() }
        binding.btnRetake.setOnClickListener { dismiss() }

        binding.btnSave.setOnClickListener {
            savePhotoToGallery()
        }

        binding.btnShare.setOnClickListener {
            sharePhoto()
        }
    }

    private fun savePhotoToGallery() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "EmojiMask_$timeStamp.jpg"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EmojiMask")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        photoBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                        stream.flush()
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val emojiMaskDir = File(picturesDir, "EmojiMask")
                if (!emojiMaskDir.exists()) emojiMaskDir.mkdirs()

                val imageFile = File(emojiMaskDir, fileName)
                FileOutputStream(imageFile).use { stream ->
                    photoBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                    stream.flush()
                }

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(imageFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
            }

            Toast.makeText(context, R.string.photo_saved_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, R.string.photo_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePhoto() {
        try {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "emoji_mask_share.jpg")
            val stream = FileOutputStream(file)
            photoBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            stream.flush()
            stream.close()

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject))
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, context.getString(R.string.share_photo)).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, R.string.photo_save_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
