package com.ahmer.pdfium.util

import androidx.annotation.Keep

/**
 * Size is a simple value class that represents a width and height.
 * @property width the width
 * @property height the height
 */
@Keep
data class SizeF(
    val width: Float,
    val height: Float,
)