package com.ahmer.pdfium.util


class SizeF(val width: Float, val height: Float) {

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }
        if (this === obj) {
            return true
        }
        if (obj is SizeF) {
            return width == obj.width && height == obj.height
        }
        return false
    }

    override fun toString(): String {
        return width.toString() + "x" + height
    }

    override fun hashCode(): Int {
        return java.lang.Float.floatToIntBits(width) xor java.lang.Float.floatToIntBits(height)
    }

    fun toSize(): Size {
        return Size(width.toInt(), height.toInt())
    }
}
