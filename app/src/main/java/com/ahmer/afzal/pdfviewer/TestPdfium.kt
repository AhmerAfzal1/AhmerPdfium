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
        var pdfFile: String? = null
        var password: String? = null
        if (Constants.PDF_FILE_MAIN == intent.getStringExtra(Constants.PDF_FILE)) {
            pdfFile = "proverbs.pdf"
            password = "112233"
        }
        val file: File? = pdfFile?.let { PdfUtils.fileFromAsset(context = this@TestPdfium, assetName = it) }
        if (file != null) {
            lifecycleScope.launch {
                openPdf(context = this@TestPdfium, file = file, password = password)
            }
        }
    }

    private fun openPdf(context: Context, file: File, password: String? = null) {
        val iv: ImageView = findViewById(R.id.imageView)
        val fd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val page = 0
        try {
            PdfiumCore(context).use {
                val pdfDocument: PdfDocument = it.newDocument(parcelFileDescriptor = fd, password = password)
                pdfDocument.openPage(pageIndex = page)
                val width: Int = it.getPageWidthPoint(pageIndex = page)
                val height: Int = it.getPageHeightPoint(pageIndex = page)
                // ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
                // RGB_565 - little worse quality, twice less memory usage
                val bitmap: Bitmap = createBitmap(width = width, height = height, config = Bitmap.Config.ARGB_8888)
                it.renderPageBitmap(
                    pageIndex = page,
                    bitmap = bitmap,
                    startX = 0,
                    startY = 0,
                    drawSizeX = width,
                    drawSizeY = height
                )
                //If you need to render annotations and form fields, you can use
                //the same method above adding 'true' as last param
                iv.setImageBitmap(bitmap)
                printInfo(doc = pdfDocument)
            }
        } catch (ex: IOException) {
            Log.e(TAG, ex.localizedMessage, ex)
            ex.printStackTrace()
        }
    }

    private fun printInfo(doc: PdfDocument) {
        val meta: PdfDocument.Meta = doc.metaData
        Log.v(TAG, "Title = ${meta.title}")
        Log.v(TAG, "Author = ${meta.author}")
        Log.v(TAG, "Subject = ${meta.subject}")
        Log.v(TAG, "Keywords = ${meta.keywords}")
        Log.v(TAG, "Creator = ${meta.creator}")
        Log.v(TAG, "Producer = ${meta.producer}")
        Log.v(TAG, "CreationDate = ${meta.creationDate}")
        Log.v(TAG, "ModDate = ${meta.modDate}")
        printBookmarksTree(tree = doc.bookmarks, sep = "-")
    }

    private fun printBookmarksTree(tree: List<PdfDocument.Bookmark>, sep: String) {
        for (b in tree) {
            Log.v(TAG, "Bookmark $sep ${b.title}, Page: ${b.pageIndex}")
            if (b.hasChildren) {
                printBookmarksTree(tree = b.children, sep = "$sep-")
            }
        }
    }

    private fun isEmptyBitmap(bitmap: Bitmap?): Boolean {
        return bitmap == null || bitmap.width == 0 || bitmap.height == 0
    }

    fun thumbnailsDir(): File {
        val dir = File(cacheDir, "PdfThumbs")
        if (!dir.exists()) {
            if (dir.mkdir()) {
                Log.v(TAG, "The directory has been created: $dir")
            } else {
                Log.v(TAG, "Could not create the directory for some unknown reason")
            }
        } else {
            Log.v(TAG, "This directory has already been created")
        }
        return dir
    }
}