package com.ahmer.pdfviewer

import android.graphics.RectF
import com.ahmer.pdfviewer.model.PagePart
import com.ahmer.pdfviewer.util.PdfConstants.Cache.CACHE_SIZE
import com.ahmer.pdfviewer.util.PdfConstants.Cache.THUMBNAILS_CACHE_SIZE
import java.util.*

class CacheManager {
    private val mActiveCache: PriorityQueue<PagePart>
    private val mOrderComparator = PagePartComparator()
    private val mPassiveActiveLock = Any()
    private val mPassiveCache: PriorityQueue<PagePart>
    private val mThumbnails: MutableList<PagePart>

    fun cachePart(part: PagePart) {
        synchronized(mPassiveActiveLock) {
            // If cache too big, remove and recycle
            makeAFreeSpace()
            // Then add part
            mActiveCache.offer(part)
        }
    }

    fun makeANewSet() {
        synchronized(mPassiveActiveLock) {
            mPassiveCache.addAll(mActiveCache)
            mActiveCache.clear()
        }
    }

    private fun makeAFreeSpace() {
        synchronized(mPassiveActiveLock) {
            while (mActiveCache.size + mPassiveCache.size >= CACHE_SIZE && !mPassiveCache.isEmpty()) {
                mPassiveCache.poll()?.renderedBitmap?.recycle()
            }
            while (mActiveCache.size + mPassiveCache.size >= CACHE_SIZE && !mActiveCache.isEmpty()) {
                mActiveCache.poll()?.renderedBitmap?.recycle()
            }
        }
    }

    fun cacheThumbnail(part: PagePart) {
        synchronized(mThumbnails) {
            // If cache too big, remove and recycle
            while (mThumbnails.size >= THUMBNAILS_CACHE_SIZE) {
                mThumbnails.removeAt(0).renderedBitmap?.recycle()
            }
            // Then add thumbnail
            addWithoutDuplicates(mThumbnails, part)
        }
    }

    fun upPartIfContained(page: Int, pageRelativeBounds: RectF, toOrder: Int, searchQuery: String?):
            Boolean {
        val mFakePart = PagePart(page, null, pageRelativeBounds, false, 0, searchQuery ?: "")
        var mFound: PagePart? = null
        synchronized(mPassiveActiveLock) {
            if (find(mPassiveCache, mFakePart)?.also { mFound = it } != null) {
                mFound?.let { mPassiveCache.remove(it) }
                mFound?.cacheOrder = toOrder
                mActiveCache.offer(mFound)
                return true
            }
            return find(mActiveCache, mFakePart) != null
        }
    }

    /**
     * Return true if already contains the described PagePart
     */
    fun containsThumbnail(page: Int, pageRelativeBounds: RectF, searchQuery: String?): Boolean {
        val mFakePart = PagePart(page, null, pageRelativeBounds, true, 0, searchQuery ?: "")
        synchronized(mThumbnails) {
            for (mPart in mThumbnails) {
                if (mPart == mFakePart) {
                    return true
                }
            }
            return false
        }
    }

    /**
     * Add part if it doesn't exist, recycle bitmap otherwise
     */
    private fun addWithoutDuplicates(collection: MutableCollection<PagePart>, newPart: PagePart) {
        for (mPart in collection) {
            if (mPart == newPart) {
                newPart.renderedBitmap!!.recycle()
                return
            }
        }
        collection.add(newPart)
    }

    val pageParts: List<PagePart>
        get() {
            synchronized(mPassiveActiveLock) {
                val mParts: MutableList<PagePart> = ArrayList(mPassiveCache)
                mParts.addAll(mActiveCache)
                return mParts
            }
        }

    fun getThumbnails(): List<PagePart> {
        synchronized(mThumbnails) { return mThumbnails }
    }

    fun recycle() {
        synchronized(mPassiveActiveLock) {
            for (mPartPassive in mPassiveCache) mPartPassive.renderedBitmap?.recycle()
            mPassiveCache.clear()
            for (mPartActive in mActiveCache) mPartActive.renderedBitmap?.recycle()
            mActiveCache.clear()
        }
        synchronized(mThumbnails) {
            for (mPartThumbnails in mThumbnails) mPartThumbnails.renderedBitmap?.recycle()
            mThumbnails.clear()
        }
    }

    internal class PagePartComparator : Comparator<PagePart> {
        override fun compare(part1: PagePart, part2: PagePart): Int {
            if (part1.cacheOrder == part2.cacheOrder) return 0
            return if (part1.cacheOrder > part2.cacheOrder) 1 else -1
        }
    }

    companion object {
        private fun find(vector: PriorityQueue<PagePart>, fakePart: PagePart): PagePart? {
            for (mPart in vector) {
                if (mPart.equals(fakePart)) {
                    return mPart
                }
            }
            return null
        }
    }

    init {
        mActiveCache = PriorityQueue(CACHE_SIZE, mOrderComparator)
        mPassiveCache = PriorityQueue(CACHE_SIZE, mOrderComparator)
        mThumbnails = ArrayList()
    }
}