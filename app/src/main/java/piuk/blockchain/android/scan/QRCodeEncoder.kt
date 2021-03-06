package piuk.blockchain.android.scan

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import java.util.EnumMap

/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class QRCodeEncoder(
    data: String,
    private val dimension: Int
) {
    private var contents: String? = null
    var title: String? = null
    private val format = BarcodeFormat.QR_CODE
    private val encoded: Boolean

    init {
        if (data.isNotEmpty()) {
            contents = data
            title = "Text"
        }
        encoded = contents?.isNotEmpty() == true
    }

    @Throws(WriterException::class)
    fun encodeAsBitmap(): Bitmap? {
        if (!encoded) return null
        var hints: MutableMap<EncodeHintType?, Any?>? = null
        val encoding = guessAppropriateEncoding(contents)
        if (encoding != null) {
            hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            hints[EncodeHintType.CHARACTER_SET] = encoding
        }
        val writer = MultiFormatWriter()
        val result = writer.encode(contents, format, dimension, dimension, hints)
        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)
        // All are 0, or black, by default
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (result[x, y]) BLACK else WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val BLACK = 0xFF000000.toInt()

        private fun guessAppropriateEncoding(contents: CharSequence?): String? {
            // Very crude at the moment
            for (element in contents!!) {
                if (element.toInt() > 0xFF) {
                    return "UTF-8"
                }
            }
            return null
        }
    }
}