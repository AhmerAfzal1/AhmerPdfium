[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Pdfium

Ahmer Pdfium library fork [barteksc/PdfiumAndroid](https://github.com/barteksc/PdfiumAndroid)

## Simple example

```kotlin
fun openPdf(context: Context, file: File, password: String? = null) {
    val iv: ImageView = findViewById(R.id.imageView)
    val fd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val pageNum: Int = 0
    val pdfiumCore:PdfiumCore = PdfiumCore(context)
    try {
        val pdfDocument: PdfDocument = pdfiumCore.newDocument(fd, password)
        val pdfPage: PdfPage = pdfDocument.openPage(pageNum)
        val width: Int = pdfPage.getPageWidthPoint
        val height: Int = pdfPage.getPageHeightPoint
        // ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
        // RGB_565 - little worse quality, twice less memory usage
        val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        pdfPage.renderPageBitmap(bitmap, 0, 0, width, height)
        //if you need to render annotations and form fields, you can use
        //the same method above adding 'true' as last param
        iv.setImageBitmap(bitmap)
        printInfo(pdfDocument)
        pdfDocument.close() // important!
    } catch (ex: IOException) {
        ex.printStackTrace()
    }
}

fun printInfo(doc: PdfDocument) {
    val meta: PdfDocument.Meta = doc.getDocumentMeta
    Log.v(TAG, "Title = " + meta.title)
    Log.v(TAG, "Author = " + meta.author)
    Log.v(TAG, "Subject = " + meta.subject)
    Log.v(TAG, "Keywords = " + meta.keywords)
    Log.v(TAG, "Creator = " + meta.creator)
    Log.v(TAG, "Producer = " + meta.producer)
    Log.v(TAG, "CreationDate = " + meta.creationDate)
    Log.v(TAG, "ModDate = " + meta.modDate)
    Log.v(TAG, "TotalPages = " + meta.totalPages)
    printBookmarksTree(doc.getTableOfContents, "-")
}

fun printBookmarksTree(tree: List<PdfDocument.Bookmark>, sep: String) {
    for (b in tree) {
        Log.v(Constants.LOG_TAG, "Bookmark $sep ${b.title}, Page: ${b.pageIdx}")
        if (b.hasChildren) {
            printBookmarksTree(b.children, "$sep-")
        }
    }
}
```