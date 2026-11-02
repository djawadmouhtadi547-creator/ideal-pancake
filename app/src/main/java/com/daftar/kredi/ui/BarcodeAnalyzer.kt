package com.daftar.kredi.ui

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage

/**
 * يحلل إطارات الكاميرا. للدقة: لا يقبل الباركود إلا إذا قُرئ بنفس القيمة
 * في إطارين متتاليين على الأقل (يمنع القراءات الخاطئة العابرة).
 */
class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onCode: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastValue: String? = null
    private var hits = 0

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { codes ->
                val v = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (v == null) {
                    lastValue = null
                    hits = 0
                } else {
                    if (v == lastValue) {
                        hits++
                    } else {
                        lastValue = v
                        hits = 1
                    }
                    if (hits >= 2) {
                        hits = 0
                        onCode(v)
                    }
                }
            }
            .addOnCompleteListener { proxy.close() }
    }
}
