[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Pdfium

Ahmer Pdfium library fork [barteksc/PdfiumAndroid](https://github.com/barteksc/PdfiumAndroid), now with [16
KB page size support](https://developer.android.com/guide/practices/page-sizes) for improved memory efficiency
on Android.

## Simple example

```kotlin
fun openPdf(context: Context, file: File, password: String? = null) {
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

fun printInfo(doc: PdfDocument) {
    val meta: PdfDocument.Meta = doc.metaData
    Log.v(TAG, "Title = " + meta.title)
    Log.v(TAG, "Author = " + meta.author)
    Log.v(TAG, "Subject = " + meta.subject)
    Log.v(TAG, "Keywords = " + meta.keywords)
    Log.v(TAG, "Creator = " + meta.creator)
    Log.v(TAG, "Producer = " + meta.producer)
    Log.v(TAG, "CreationDate = " + meta.creationDate)
    Log.v(TAG, "ModDate = " + meta.modDate)
    printBookmarksTree(doc.bookmarks, "-")
}

fun printBookmarksTree(tree: List<PdfDocument.Bookmark>, sep: String) {
    for (b in tree) {
        Log.v(TAG, "Bookmark $sep ${b.title}, Page: ${b.pageIndex}")
        if (b.hasChildren) {
            printBookmarksTree(b.children, "$sep-")
        }
    }
}
```