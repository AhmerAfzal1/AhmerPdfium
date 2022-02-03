[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# AhmerPdfium

Ahmer Pdfium library fork [barteksc/PdfiumAndroid](https://github.com/barteksc/PdfiumAndroid)

## Sample usage:

``` java
PdfiumCore core = ...;
PdfDocument document = ...;
int pageIndex = 0;
core.openPage(document, pageIndex);
List<PdfDocument.Link> links = core.getPageLinks(document, pageIndex);
for (PdfDocument.Link link : links) {
    RectF mappedRect = core.mapRectToDevice(document, pageIndex, ..., link.getBounds())
    if (clickedArea(mappedRect)) {
        String uri = link.getUri();
        if (link.getDestPageIdx() != null) {
            // jump to page
        } else if (uri != null && !uri.isEmpty()) {
            // open URI using Intent
        }
    }
}
```

## Simple example

``` java
void openPdf() {
    ImageView iv = (ImageView) findViewById(R.id.imageView);
    ParcelFileDescriptor fd = ...;
    int pageNum = 0;
    PdfiumCore pdfiumCore = new PdfiumCore(context);
    try {
        PdfDocument pdfDocument = pdfiumCore.newDocument(fd);
        pdfiumCore.openPage(pdfDocument, pageNum);
        int width = pdfiumCore.getPageWidthPoint(pdfDocument, pageNum);
        int height = pdfiumCore.getPageHeightPoint(pdfDocument, pageNum);
        // ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
        // RGB_565 - little worse quality, twice less memory usage
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        pdfiumCore.renderPageBitmap(pdfDocument, bitmap, pageNum, 0, 0, width, height);
        //if you need to render annotations and form fields, you can use
        //the same method above adding 'true' as last param
        iv.setImageBitmap(bitmap);
        printInfo(pdfiumCore, pdfDocument);
        pdfiumCore.closeDocument(pdfDocument); // important!
    } catch(IOException ex) {
        ex.printStackTrace();
    }
}

public void printInfo(PdfiumCore core, PdfDocument doc) {
    PdfDocument.Meta meta = core.getDocumentMeta(doc);
    Log.e(TAG, "title = " + meta.getTitle());
    Log.e(TAG, "author = " + meta.getAuthor());
    Log.e(TAG, "subject = " + meta.getSubject());
    Log.e(TAG, "keywords = " + meta.getKeywords());
    Log.e(TAG, "creator = " + meta.getCreator());
    Log.e(TAG, "producer = " + meta.getProducer());
    Log.e(TAG, "creationDate = " + meta.getCreationDate());
    Log.e(TAG, "modDate = " + meta.getModDate());
    printBookmarksTree(core.getTableOfContents(doc), "-");
}

public void printBookmarksTree(List<PdfDocument.Bookmark> tree, String sep) {
    for (PdfDocument.Bookmark b : tree) {
        Log.e(TAG, String.format("%s %s, p %d", sep, b.getTitle(), b.getPageIdx()));
        if (b.hasChildren()) {
            printBookmarksTree(b.getChildren(), sep + "-");
        }
    }
}
```