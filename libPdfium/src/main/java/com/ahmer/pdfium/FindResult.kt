package com.ahmer.pdfium

import java.io.Closeable

class FindResult(
    val handle: FindHandle,
) : Closeable {
    private external fun nativeCloseFind(findHandle: Long)
    private external fun nativeFindNext(findHandle: Long): Boolean
    private external fun nativeFindPrev(findHandle: Long): Boolean
    private external fun nativeGetSchCount(findHandle: Long): Int
    private external fun nativeGetSchResultIndex(findHandle: Long): Int

    fun findNext(): Boolean {
        synchronized(lock = PdfiumCore.lock) {
            return nativeFindNext(findHandle = handle)
        }
    }

    fun findPrev(): Boolean {
        synchronized(lock = PdfiumCore.lock) {
            return nativeFindPrev(findHandle = handle)
        }
    }

    fun getSchResultIndex(): Int {
        synchronized(lock = PdfiumCore.lock) {
            return nativeGetSchResultIndex(findHandle = handle)
        }
    }

    fun getSchCount(): Int {
        synchronized(lock = PdfiumCore.lock) {
            return nativeGetSchCount(findHandle = handle)
        }
    }

    fun closeFind() {
        synchronized(lock = PdfiumCore.lock) {
            nativeCloseFind(findHandle = handle)
        }
    }

    override fun close() {
        nativeCloseFind(findHandle = handle)
    }
}