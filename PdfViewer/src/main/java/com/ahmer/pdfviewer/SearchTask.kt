package com.ahmer.pdfviewer

import com.ahmer.pdfviewer.model.SearchRecord
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

class SearchTask(pdfView: PDFView, key: String) : Runnable {
    val abort = AtomicBoolean()
    val key: String = key + "\u0000"
    val flag = 0
    private val arr: ArrayList<SearchRecord> = ArrayList()
    private val pdfView: WeakReference<PDFView> = WeakReference<PDFView>(pdfView)
    private var t: Thread? = null
    var keyStr: Long = 0
        get() {
            if (field == 0L) {
                field = pdfView.get()!!.pdfFile!!.pdfiumCore.nativeGetStringChars(key)
            }
            return field
        }
        private set
    private var finished = false
    override fun run() {
        val a: PDFView = pdfView.get() ?: return
        if (finished) {
            //a.setSearchResults(arr);
            //a.showT("findAllTest_Time : "+(System.currentTimeMillis()-CMN.ststrt)+" sz="+arr.size());
            a.endSearch(arr)
        } else {
            var schRecord: SearchRecord?
            for (i in 0 until a.getPageCount()) {
                if (abort.get()) break
                schRecord = a.findPageCached(key, i, 0)
                if (schRecord != null) {
                    a.notifyItemAdded(this, arr, schRecord, i)
                } else {
                    //  a.notifyProgress(i);
                }
            }
            finished = true
            t = null
            a.post(this)
        }
    }

    fun start() {
        if (finished) {
            return
        }
        if (t == null) {
            pdfView.get()?.startSearch(arr, key, flag)
            t = Thread(this)
            t!!.start()
        }
    }

    fun abort() {
        abort.set(true)
    }

    val isAborted: Boolean
        get() = abort.get()

}