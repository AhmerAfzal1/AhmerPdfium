package com.ahmer.pdfviewer.async

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AsyncWorker private constructor() {
    val executorService: ExecutorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS)
    var handler: Handler = Handler(Looper.getMainLooper())

    companion object {
        val instance = AsyncWorker()
        private const val NUMBER_OF_THREADS = 4
    }

}
