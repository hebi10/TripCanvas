package com.hebi.tripcanvas.data.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** User-selected image is processed on device, with the bundled Korean OCR model. */
class ReceiptOcrService(private val context: Context) {
    suspend fun recognizeSelectedImage(uri: Uri): String = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "선택한 이미지만 인식할 수 있습니다." }
        val resolver = context.contentResolver
        require(resolver.getType(uri)?.startsWith("image/") == true) {
            "이미지 파일을 선택해 주세요."
        }
        resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val size = descriptor.statSize
            require(size < 0 || size <= 15L * 1024 * 1024) {
                "15MB 이하의 이미지를 선택해 주세요."
            }
        }
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        try {
            Tasks.await(recognizer.process(image)).text.take(12_000)
        } finally {
            recognizer.close()
        }
    }
}
