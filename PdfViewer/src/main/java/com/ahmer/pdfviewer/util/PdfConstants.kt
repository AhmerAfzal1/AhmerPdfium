package com.ahmer.pdfviewer.util

object PdfConstants {
    const val DEBUG_MODE = false

    /**
     * Between 0 and 1, the thumbnails quality (default 0.3). Increasing this value may cause performance decrease
     */
    const val THUMBNAIL_RATIO = 0.6f // Default 0.3f

    /**
     * The size of the rendered parts (default 256)
     * Tinier : a little bit slower to have the whole page rendered but more reactive.
     * Bigger : user will have to wait longer to have the first visual results
     */
    const val PART_SIZE = 384f // Default 256

    /**
     * Part of document above and below screen that should be preloaded, in dp
     */
    const val PRELOAD_OFFSET = 30 // Default 20

    /**
     * Max pages to load at the time, others are in queue
     */
    const val MAX_PAGES = 15

    object Cache {
        /**
         * The size of the cache (number of bitmaps kept)
         */
        const val CACHE_SIZE = 150 // Default 150
        const val THUMBNAILS_CACHE_SIZE = 10 // Default 8
    }

    object Pinch {
        const val MAXIMUM_ZOOM = 10f
        const val MINIMUM_ZOOM = 1f
    }
}
