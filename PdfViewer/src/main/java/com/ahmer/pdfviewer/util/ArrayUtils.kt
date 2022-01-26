package com.ahmer.pdfviewer.util

import java.util.*

object ArrayUtils {

    /**
     * Transforms (0,1,2,2,3) to (0,1,2,3)
     */
    @JvmStatic
    fun deleteDuplicatedPages(pages: IntArray): IntArray {
        var mLastInt = -1
        val mResult: MutableList<Int> = ArrayList()
        for (mCurrentInt in pages) {
            if (mLastInt != mCurrentInt) mResult.add(mCurrentInt)
            mLastInt = mCurrentInt
        }
        val mArrayResult = IntArray(mResult.size)
        for (i in mResult.indices) {
            mArrayResult[i] = mResult[i]
        }
        return mArrayResult
    }

    /**
     * Transforms (0, 4, 4, 6, 6, 6, 3) into (0, 1, 1, 2, 2, 2, 3)
     */
    @JvmStatic
    fun calculateIndexesInDuplicateArray(originalUserPages: IntArray): IntArray {
        val mResult = IntArray(originalUserPages.size)
        if (originalUserPages.isEmpty()) return mResult
        var mIndex = 0
        mResult[0] = mIndex
        for (i in 1 until originalUserPages.size) {
            if (originalUserPages[i] != originalUserPages[i - 1]) mIndex++
            mResult[i] = mIndex
        }
        return mResult
    }

    @JvmStatic
    fun arrayToString(array: IntArray): String {
        val mBuilder = StringBuilder("[")
        for (i in array.indices) {
            mBuilder.append(array[i])
            if (i != array.size - 1) mBuilder.append(",")
        }
        mBuilder.append("]")
        return mBuilder.toString()
    }
}
