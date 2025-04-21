package com.ahmer.pdfium

import java.io.IOException

/**
 * PdfPasswordException is thrown when a password is required to open a document
 */
class PdfPasswordException(message: String?) : IOException(message)