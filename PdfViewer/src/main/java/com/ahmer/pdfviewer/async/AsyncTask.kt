package com.ahmer.pdfviewer.async

import io.ahmer.utils.async.AsyncWorker


abstract class AsyncTask<INPUT, PROGRESS, OUTPUT> {
    /**
     * @return Returns true if the background work should be cancelled
     */
    private var isCancelled = false
    private var onProgressListener: OnProgressListener<PROGRESS>? = null
    private var onCancelledListener: OnCancelledListener? = null
    /**
     * Starts is all
     *
     * @param input Data you want to work with in the background
     */
    /**
     * @see .execute
     */
    @JvmOverloads
    fun execute(input: INPUT? = null): AsyncTask<INPUT, PROGRESS, OUTPUT> {
        onPreExecute()
        val executorService = AsyncWorker.getInstance().executorService
        executorService.execute {
            val output = doInBackground(input)
            AsyncWorker.getInstance().handler.post { onPostExecute(output) }
        }
        return this
    }

    /**
     * Call to publish progress from background
     *
     * @param progress Progress made
     */
    protected fun publishProgress(progress: PROGRESS) {
        AsyncWorker.getInstance().handler.post {
            onProgress(progress)
            if (onProgressListener != null) {
                onProgressListener!!.onProgress(progress)
            }
        }
    }

    private fun onProgress(progress: PROGRESS) {}

    /**
     * Call to cancel background work
     */
    fun cancel() {
        isCancelled = true
    }

    /**
     * Call this method after cancelling background work
     */
    protected open fun onCancelled() {
        AsyncWorker.getInstance().handler.post {
            if (onCancelledListener != null) {
                onCancelledListener!!.onCancelled()
            }
        }
    }

    /**
     * Work which you want to be done on UI thread before [.doInBackground]
     */
    protected open fun onPreExecute() {}

    /**
     * Work on background
     *
     * @param input Input data
     * @return Output data
     */
    protected abstract fun doInBackground(input: INPUT?): OUTPUT

    /**
     * Work which you want to be done on UI thread after [.doInBackground]
     *
     * @param output Output data from [.doInBackground]
     */
    open fun onPostExecute(output: OUTPUT) {}
    fun setOnProgressListener(onProgressListener: OnProgressListener<PROGRESS>?) {
        this.onProgressListener = onProgressListener
    }

    fun setOnCancelledListener(onCancelledListener: OnCancelledListener?) {
        this.onCancelledListener = onCancelledListener
    }

    interface OnProgressListener<PROGRESS> {
        fun onProgress(progress: PROGRESS)
    }

    interface OnCancelledListener {
        fun onCancelled()
    }
}
