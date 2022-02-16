[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Pdfium

Ahmer Pdfium library fork [barteksc/PdfiumAndroid](https://github.com/barteksc/PdfiumAndroid)

## Simple example

```kotlin
fun openPdf(context: Context, file: File) {
    val iv: ImageView = findViewById(R.id.imageView)
    val fd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val pageNum = 0
    val pdfiumCore = PdfiumCore(context)
    try {
        val pdfDocument: PdfDocument = pdfiumCore.newDocument(fd)
        pdfiumCore.openPage(pdfDocument, pageNum)
        val width: Int = pdfiumCore.getPageWidthPoint(pdfDocument, pageNum)
        val height: Int = pdfiumCore.getPageHeightPoint(pdfDocument, pageNum)
        // ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
        // RGB_565 - little worse quality, twice less memory usage
        val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        pdfiumCore.renderPageBitmap(pdfDocument, bitmap, pageNum, 0, 0, width, height)
        //if you need to render annotations and form fields, you can use
        //the same method above adding 'true' as last param
        iv.setImageBitmap(bitmap)
        printInfo(pdfiumCore, pdfDocument)
        pdfiumCore.closeDocument(pdfDocument); // important!
    } catch (ex: IOException) {
        ex.printStackTrace()
    }
}

fun printInfo(core: PdfiumCore, doc: PdfDocument) {
    val meta: PdfDocument.Meta = core.getDocumentMeta(doc)
    Log.e(TAG, "Title = " + meta.title)
    Log.e(TAG, "Author = " + meta.author)
    Log.e(TAG, "Subject = " + meta.subject)
    Log.e(TAG, "Keywords = " + meta.keywords)
    Log.e(TAG, "Creator = " + meta.creator)
    Log.e(TAG, "Producer = " + meta.producer)
    Log.e(TAG, "CreationDate = " + meta.creationDate)
    Log.e(TAG, "ModDate = " + meta.modDate)
    Log.e(TAG, "TotalPages = " + meta.totalPages)
    printBookmarksTree(core.getTableOfContents(doc), "-")
}

fun printBookmarksTree(tree: List<PdfDocument.Bookmark>, sep: String) {
    for (b in tree) {
        Log.v(Constants.LOG_TAG, "Bookmark $sep ${b.title}, Page: ${b.pageIdx}")
        if (b.hasChildren()) {
            printBookmarksTree(b.children, "$sep-")
        }
    }
}
```