package com.ahmer.pdfium

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException

class PdfiumCore(context: Context) {
    var getContext: Context = context

    private external fun nativeOpenDocument(fd: Int, password: String?): Long
    private external fun nativeOpenMemDocument(data: ByteArray, password: String?): Long

    /**
     * Create new document from file
     * @param parcelFileDescriptor opened file descriptor of file
     * @return PdfDocument
     */
    @Throws(IOException::class)
    fun newDocument(parcelFileDescriptor: ParcelFileDescriptor): PdfDocument {
        return newDocument(parcelFileDescriptor, null)
    }

    /**
     * Create new document from file with password
     * @param parcelFileDescriptor opened file descriptor of file
     * @param password password for decryption
     * @return PdfDocument
     */
    @Throws(IOException::class)
    fun newDocument(parcelFileDescriptor: ParcelFileDescriptor, password: String?): PdfDocument {
        synchronized(lock) {
            return PdfDocument(
                nativeDocPtr = nativeOpenDocument(parcelFileDescriptor.fd, password)
            ).also { document ->
                document.parcelFileDescriptor = parcelFileDescriptor
            }
        }
    }

    /**
     * Create new document from bytearray
     * @param data bytearray of pdf file
     * @return PdfDocument
     */
    @Throws(IOException::class)
    fun newDocument(data: ByteArray): PdfDocument {
        return newDocument(data, null)
    }

    /**
     * Create new document from bytearray with password
     * @param data bytearray of pdf file
     * @param password password for decryption
     * @return PdfDocument
     */
    @Throws(IOException::class)
    fun newDocument(data: ByteArray, password: String?): PdfDocument {
        synchronized(lock) {
            return PdfDocument(nativeOpenMemDocument(data, password)).also { document ->
                document.parcelFileDescriptor = null
            }
        }
    }

    companion object {
        private val TAG: String = PdfiumCore::class.java.name
        val lock: Any = Any()
        var screenDpi: Int = 0

        init {
            try {
                System.loadLibrary("pdfsdk")
                System.loadLibrary("pdfsdk_jni")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native libraries failed to load", e)
            }
        }
    }

    init {
        screenDpi = context.resources.displayMetrics.densityDpi
        Log.d(TAG, "Starting AhmerPdfium...")
    }
}