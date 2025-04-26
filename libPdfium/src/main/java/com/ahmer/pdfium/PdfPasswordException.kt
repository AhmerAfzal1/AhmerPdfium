package com.ahmer.pdfium

import java.io.IOException

/**
 * Thrown when attempting to access a password-protected PDF document
 * without providing a valid password.
 *
 * @param message Optional detailed description of the exception scenario.
 * @constructor Creates an instance of the password requirement exception.
 */
class PdfPasswordException(message: String?) : IOException(message)