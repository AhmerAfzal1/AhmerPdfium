package com.ahmer.pdfium

/**
 * PdfWriteCallback is the callback interface for saveAsCopy
 */
interface PdfWriteCallback {
    /**
     * WriteBlock is called by native code to write a block of data
     * @param data the data to write
     *
     * note: The name need to be exactly what it is.
     *  The native call is looking for is as WriteBlock
     *
     */
    @Suppress("FunctionName")
    fun WriteBlock(data: ByteArray?): Int
}
