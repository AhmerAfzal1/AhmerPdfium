package com.ahmer.pdfviewer.util

object MathUtils {
    private const val BIG_ENOUGH_INT = 16 * 1024
    private const val BIG_ENOUGH_FLOOR = BIG_ENOUGH_INT.toDouble()
    private const val BIG_ENOUGH_CEIL = 16384.999999999996

    /**
     * Limits the given **number** between the other values
     * @param number  The number to limit.
     * @param between The smallest value the number can take.
     * @param and     The biggest value the number can take.
     * @return The limited number.
     */
    fun limit(number: Int, between: Int, and: Int): Int {
        return if (number <= between) {
            between
        } else number.coerceAtMost(and)
    }

    /**
     * Limits the given **number** between the other values
     * @param number  The number to limit.
     * @param between The smallest value the number can take.
     * @param and     The biggest value the number can take.
     * @return The limited number.
     */
    fun limit(number: Float, between: Float, and: Float): Float {
        return if (number <= between) {
            between
        } else number.coerceAtMost(and)
    }

    fun max(number: Float, max: Float): Float {
        return number.coerceAtMost(max)
    }

    fun min(number: Float, min: Float): Float {
        return number.coerceAtLeast(min)
    }

    fun max(number: Int, max: Int): Int {
        return number.coerceAtMost(max)
    }

    fun min(number: Int, min: Int): Int {
        return number.coerceAtLeast(min)
    }

    /**
     * Returns the largest integer less than or equal to the specified float. This method will only properly floor floats from
     * -(2^14) to (Float.MAX_VALUE - 2^14).
     */
    fun floor(value: Float): Int {
        return (value + BIG_ENOUGH_FLOOR).toInt() - BIG_ENOUGH_INT
    }

    /**
     * Returns the smallest integer greater than or equal to the specified float. This method will only properly ceil floats from
     * -(2^14) to (Float.MAX_VALUE - 2^14).
     */
    fun ceil(value: Float): Int {
        return (value + BIG_ENOUGH_CEIL).toInt() - BIG_ENOUGH_INT
    }
}