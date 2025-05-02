package com.ahmer.afzal.pdfviewer

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.lifecycle.lifecycleScope
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfviewer.util.PdfUtils
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class TestPdfium : AppCompatActivity() {

    private val TAG: String = "TAG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_pdfium)
        var mPdfFile: String? = null
        var mPassword: String? = null
        if (Constants.PDF_FILE_MAIN == intent.getStringExtra(Constants.PDF_FILE)) {
            mPdfFile = "grammar.pdf"
            mPassword = "5632"
        }
        val file = mPdfFile?.let { PdfUtils.fileFromAsset(applicationContext, it) }
        if (file != null) {
            lifecycleScope.launch {
                openPdf(applicationContext, file, mPassword)
            }
        }
    }

    private fun openPdf(context: Context, file: File, password: String? = null) {
        val iv: ImageView = findViewById(R.id.imageView)
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val page = 0
        try {
            PdfiumCore(context).use {
                val pdfDocument: PdfDocument = it.newDocument(fd, password)
                pdfDocument.openPage(page)
                val width: Int = it.getPageWidthPoint(page)
                val height: Int = it.getPageHeightPoint(page)
                // ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
                // RGB_565 - little worse quality, twice less memory usage
                val bitmap: Bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                it.renderPageBitmap(page, bitmap, 0, 0, width, height)
                //If you need to render annotations and form fields, you can use
                //the same method above adding 'true' as last param
                iv.setImageBitmap(bitmap)
                printInfo(pdfDocument)
            }
        } catch (ex: IOException) {
            Log.e(TAG, ex.localizedMessage, ex)
            ex.printStackTrace()
        }
    }

    private fun printInfo(doc: PdfDocument) {
        val meta: PdfDocument.Meta = doc.metaData
        Log.v(TAG, "Title = " + meta.title)
        Log.v(TAG, "Author = " + meta.author)
        Log.v(TAG, "Subject = " + meta.subject)
        Log.v(TAG, "Keywords = " + meta.keywords)
        Log.v(TAG, "Creator = " + meta.creator)
        Log.v(TAG, "Producer = " + meta.producer)
        Log.v(TAG, "CreationDate = " + meta.creationDate)
        Log.v(TAG, "ModDate = " + meta.modDate)
        Log.v(TAG, "TotalPages = " + meta.totalPages)
        printBookmarksTree(doc.bookmarks, "-")
    }

    private fun printBookmarksTree(tree: List<PdfDocument.Bookmark>, sep: String) {
        for (b in tree) {
            Log.v(TAG, "Bookmark $sep ${b.title}, Page: ${b.pageIndex}")
            if (b.hasChildren()) {
                printBookmarksTree(b.children, "$sep-")
            }
        }
    }

    private fun isEmptyBitmap(bitmap: Bitmap?): Boolean {
        return bitmap == null || bitmap.width == 0 || bitmap.height == 0
    }

    fun thumbnailsDir(): File {
        val mDir = File(cacheDir, "PdfThumbs")
        if (!mDir.exists()) {
            if (mDir.mkdir()) {
                Log.v(TAG, "The directory has been created: $mDir")
            } else {
                Log.v(TAG, "Could not create the directory for some unknown reason")
            }
        } else {
            Log.v(TAG, "This directory has already been created")
        }
        return mDir
    }
}