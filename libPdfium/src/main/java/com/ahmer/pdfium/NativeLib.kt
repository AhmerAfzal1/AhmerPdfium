package com.ahmer.pdfium

class NativeLib {

    /**
     * A native method that is implemented by the 'pdfium' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'pdfium' library on application startup.
        init {
            System.loadLibrary("pdfium")
        }
    }
}