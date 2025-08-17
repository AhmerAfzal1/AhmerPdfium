package com.ahmer.pdfviewer

import android.graphics.RectF
import com.ahmer.pdfviewer.model.PagePart
import com.ahmer.pdfviewer.util.PdfConstants.Cache.CACHE_SIZE
import com.ahmer.pdfviewer.util.PdfConstants.Cache.THUMBNAILS_CACHE_SIZE
import java.util.PriorityQueue

class CacheManager {
    private val activeCache = PriorityQueue(CACHE_SIZE, PagePartComparator())
    private val passiveCache = PriorityQueue(CACHE_SIZE, PagePartComparator())
    private val thumbnails: MutableList<PagePart> = mutableListOf()
    private val cacheLock: Any = Any()
    private val thumbnailsLock: Any = Any()

    fun cachePart(part: PagePart) {
        synchronized(lock = cacheLock) {
            clearCacheSpace()
            activeCache.offer(part)
        }
    }

    fun makeNewSet() {
        synchronized(lock = cacheLock) {
            passiveCache.addAll(activeCache)
            activeCache.clear()
        }
    }

    private fun clearCacheSpace() {
        synchronized(lock = cacheLock) {
            while (activeCache.size + passiveCache.size >= CACHE_SIZE && passiveCache.isNotEmpty()) {
                passiveCache.poll()?.renderedBitmap?.recycle()
            }
            while (activeCache.size + passiveCache.size >= CACHE_SIZE && activeCache.isNotEmpty()) {
                activeCache.poll()?.renderedBitmap?.recycle()
            }
        }
    }

    fun cacheThumbnail(part: PagePart) {
        synchronized(lock = thumbnailsLock) {
            while (thumbnails.size >= THUMBNAILS_CACHE_SIZE) {
                thumbnails.removeAt(index = 0).renderedBitmap?.recycle()
            }
            if (thumbnails.any { it == part }) part.renderedBitmap?.recycle() else thumbnails.add(part)

        }
    }

    fun moveToActiveCache(page: Int, bounds: RectF, newOrder: Int): Boolean {
        val fakePart = PagePart(
            page = page,
            renderedBitmap = null,
            pageBounds = bounds,
            isThumbnail = false,
            cacheOrder = 0
        )
        return synchronized(lock = cacheLock) {
            passiveCache.firstOrNull { it == fakePart }?.let { found ->
                passiveCache.remove(found)
                found.cacheOrder = newOrder
                activeCache.offer(found)
                true
            } ?: activeCache.any { it == fakePart }
        }
    }

    fun hasThumbnail(page: Int, bounds: RectF): Boolean {
        val fakePart = PagePart(
            page = page,
            renderedBitmap = null,
            pageBounds = bounds,
            isThumbnail = true,
            cacheOrder = 0
        )
        return synchronized(lock = thumbnailsLock) {
            thumbnails.any { it == fakePart }
        }
    }

    val pageParts: List<PagePart>
        get() = synchronized(lock = cacheLock) {
            mutableListOf<PagePart>().apply {
                addAll(elements = passiveCache)
                addAll(elements = activeCache)
            }
        }

    val allThumbnails: List<PagePart> = synchronized(lock = thumbnailsLock) { thumbnails.toList() }

    fun clearAll() {
        synchronized(lock = cacheLock) {
            passiveCache.forEach { it.renderedBitmap?.recycle() }
            passiveCache.clear()
            activeCache.forEach { it.renderedBitmap?.recycle() }
            activeCache.clear()
        }
        synchronized(lock = thumbnailsLock) {
            thumbnails.forEach { it.renderedBitmap?.recycle() }
            thumbnails.clear()
        }
    }

    private class PagePartComparator : Comparator<PagePart> {
        override fun compare(part1: PagePart, part2: PagePart): Int {
            return part1.cacheOrder.compareTo(other = part2.cacheOrder)
        }
    }
}