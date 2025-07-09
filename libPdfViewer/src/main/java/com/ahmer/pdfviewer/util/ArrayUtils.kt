package com.ahmer.pdfviewer.util

object ArrayUtils {

    /**
     * Transforms (0,1,2,2,3) to (0,1,2,3)
     */
    fun deleteDuplicatedPages(pages: IntArray): IntArray {
        if (pages.isEmpty()) return intArrayOf()

        val uniquePages: MutableList<Int> = mutableListOf()
        var previousPage: Int = -1

        for (currentPage in pages) {
            if (currentPage != previousPage) {
                uniquePages.add(currentPage)
                previousPage = currentPage
            }
        }
        return uniquePages.toIntArray()
    }

    /**
     * Transforms (0, 4, 4, 6, 6, 6, 3) into (0, 1, 1, 2, 2, 2, 3)
     */
    fun calculateIndexesInDuplicateArray(originalPages: IntArray): IntArray {
        if (originalPages.isEmpty()) return intArrayOf()

        val groupIndices = IntArray(size = originalPages.size).apply {
            this[0] = 0  // First element always belongs to group 0
        }
        var currentGroupIndex = 0

        for (i in 1 until originalPages.size) {
            if (originalPages[i] != originalPages[i - 1]) {
                currentGroupIndex++
            }
            groupIndices[i] = currentGroupIndex
        }

        return groupIndices
    }

    fun arrayToString(array: IntArray): String {
        return array.joinToString(prefix = "[", postfix = "]", separator = ",")
    }
}
