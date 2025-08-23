package com.ahmer.afzal.pdfviewer

import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahmer.afzal.pdfviewer.databinding.ActivityPdfBinding
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfPasswordException
import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.link.DefaultLinkHandler
import com.ahmer.pdfviewer.listener.OnErrorListener
import com.ahmer.pdfviewer.listener.OnLoadCompleteListener
import com.ahmer.pdfviewer.listener.OnPageChangeListener
import com.ahmer.pdfviewer.listener.OnPageErrorListener
import com.ahmer.pdfviewer.listener.OnRenderListener
import com.ahmer.pdfviewer.listener.OnTapListener
import com.ahmer.pdfviewer.scroll.DefaultScrollHandle
import com.ahmer.pdfviewer.util.FitPolicy
import com.ahmer.pdfviewer.util.PdfUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class PdfActivity : AppCompatActivity(), OnPageChangeListener, OnLoadCompleteListener {

    private lateinit var binding: ActivityPdfBinding
    private lateinit var pdfView: PDFView
    private lateinit var progressBar: ProgressBar
    //private lateinit var searchManager: PdfSearchManager

    private val viewModel: PdfActivityModel by viewModels()
    private var currentPage: Int = 0
    private var password: String = ""
    private var pdfFileName: String = ""

    private var isModified: Boolean = false
    private var shouldExitAfterSave: Boolean = false
    private var currentPageCount: Int = 0

    private val createFile: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.CreateDocument(mimeType = "application/pdf")) { uri ->
            uri?.let { savePdfToUri(uri = it) } ?: run { shouldExitAfterSave = false }
        }

    private val backCallback = object : OnBackPressedCallback(enabled = true) {
        override fun handleOnBackPressed() {
            if (isModified) showUnsavedChangesDialog() else finishWithTransition()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        setupObservers()
        setupMenu()
        loadPdf()
        onBackPressedDispatcher.addCallback(owner = this@PdfActivity, onBackPressedCallback = backCallback)
    }

    private fun initViews() {
        pdfView = binding.pdfView.apply {
            setBackgroundColor(Color.LTGRAY)
        }
        progressBar = binding.progressBar

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            backCallback.handleOnBackPressed()
        }

        /*searchManager = PdfSearchManager(
            pdfView = binding.pdfView,
            searchContainer = binding.searchControls.root,
            searchQuery = binding.searchControls.searchQuery,
            matchCase = binding.searchControls.matchCase,
            wholeWord = binding.searchControls.wholeWord,
            searchPrevBtn = binding.searchControls.searchPrev,
            searchNextBtn = binding.searchControls.searchNext,
            closeSearchBtn = binding.searchControls.closeSearch,
            searchCounter = binding.searchControls.searchCounter,
        )*/
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.pdfUiState.collectLatest { updateMenuItems(state = it) }
        }
    }

    private fun setupMenu() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            setMenuEnabled(enabled = false)
            val handled: Boolean = when (menuItem.itemId) {
                R.id.menuNightMode -> {
                    toggleNightMode()
                    true
                }

                R.id.menuPageSnap -> {
                    togglePageSnap()
                    true
                }

                R.id.menuSwitchView -> {
                    toggleViewOrientation()
                    true
                }

                R.id.menuJumpTo -> {
                    showJumpToDialog()
                    true
                }

                R.id.menuInfo -> {
                    showPdfInfoDialog()
                    true
                }

                R.id.menuDeletePage -> {
                    deleteCurrentPage()
                    true
                }

                R.id.menuSaveCopy -> {
                    savePdfCopy()
                    true
                }

                R.id.menuSearch -> {
                    //searchManager.showSearch()
                    true
                }

                else -> false
            }
            setMenuEnabled(enabled = true)
            handled
        }
    }

    private fun toggleNightMode(): Boolean {
        val newNightMode: Boolean = !viewModel.pdfUiState.value.isNightMode
        viewModel.updateNightMode(isChecked = newNightMode)
        reloadPdfViewer()
        return true
    }

    private fun togglePageSnap(): Boolean {
        val newPageSnap: Boolean = !viewModel.pdfUiState.value.isPageSnap
        viewModel.updatePageSnap(isChecked = newPageSnap)
        viewModel.updateAutoSpacing(isChecked = newPageSnap)
        viewModel.updateSpacing(spacing = if (newPageSnap) 5 else 10)
        reloadPdfViewer()
        return true
    }

    private fun toggleViewOrientation(): Boolean {
        val newViewHorizontal: Boolean = !viewModel.pdfUiState.value.isViewHorizontal
        viewModel.updateViewHorizontal(isChecked = newViewHorizontal)
        reloadPdfViewer()
        return true
    }

    private fun deleteCurrentPage() {
        lifecycleScope.launch(context = Dispatchers.IO) {
            if (currentPageCount <= 1) {
                withContext(context = Dispatchers.Main) { showToast(msg = "Cannot delete last page") }
                return@launch
            }

            pdfView.pdfFile?.deletePage(pageIndex = currentPage)
            isModified = true

            val temp: File = File(cacheDir, "edited.pdf").apply { deleteOnExit() }
            FileOutputStream(temp).use { out ->
                pdfView.saveAsCopy(out = out, flags = PDFView.FPDF_NO_INCREMENTAL)
            }

            withContext(context = Dispatchers.Main) {
                currentPageCount = pdfView.pdfFile?.pagesCount ?: 0
                if (currentPage >= currentPageCount) currentPage = currentPageCount - 1
                pdfView.recycle()
                displayFromFile(file = temp)
            }
        }
    }

    private fun updateMenuItems(state: PdfUiState) {
        binding.toolbar.menu?.let { menu ->
            menu.findItem(R.id.menuNightMode)?.setIcon(
                if (state.isNightMode) R.drawable.ic_baseline_light_mode else R.drawable.ic_baseline_dark_mode
            )

            menu.findItem(R.id.menuPageSnap)?.title = getString(
                if (state.isPageSnap) R.string.menu_pdf_disable_snap_page else R.string.menu_pdf_enable_snap_page
            )

            menu.findItem(R.id.menuSwitchView)?.apply {
                setIcon(
                    if (state.isViewHorizontal) R.drawable.ic_baseline_swipe_vert else R.drawable.ic_baseline_swipe_horiz
                )
                title = getString(
                    if (state.isViewHorizontal) R.string.menu_pdf_view_vertical else R.string.menu_pdf_view_horizontal
                )
            }
        }
    }

    private fun setMenuEnabled(enabled: Boolean) {
        binding.toolbar.menu?.let { menu ->
            listOf(
                R.id.menuInfo,
                R.id.menuJumpTo,
                R.id.menuSwitchView,
                R.id.menuNightMode,
                R.id.menuDeletePage,
                R.id.menuSaveCopy
            ).forEach { menu.findItem(it).isEnabled = enabled }
        }
    }

    private fun loadPdf() {
        when {
            intent.data != null -> loadFromUri(uri = intent.data!!)
            intent.hasExtra(Constants.PDF_FILE) -> loadFromAssets()
            else -> {
                showToast(msg = "No PDF selected")
                finish()
            }
        }
    }

    private fun loadFromUri(uri: Uri) {
        try {
            pdfFileName = PdfHelper.getFileNameFromUri(context = this@PdfActivity, uri = uri).toString()
            lifecycleScope.launch {
                currentPage = viewModel.loadLastPage(fileName = pdfFileName).first()
                displayFromUri(uri = uri)
            }
        } catch (e: Exception) {
            showToast("Error loading PDF from URI: ${e.message ?: "Unknown error"}")
            finish()
        }
    }

    private fun loadFromAssets() {
        try {
            pdfFileName = when (intent.getStringExtra(Constants.PDF_FILE)) {
                Constants.PDF_FILE_MAIN -> {
                    password = "112233"
                    "proverbs.pdf"
                }

                Constants.PDF_FILE_PROTECTED -> "proverbs.pdf"
                Constants.PDF_FILE_1 -> "example.pdf"
                Constants.PDF_FILE_2 -> "example1.pdf"
                Constants.PDF_FILE_3 -> "example3.pdf"
                Constants.PDF_FILE_4 -> "statement.pdf"
                else -> throw IllegalArgumentException("Unknown PDF file")
            }
            displayFromAsset(fileName = pdfFileName)
        } catch (e: Exception) {
            showToast(msg = "Error loading PDF from assets: ${e.message ?: "Unknown error"}")
            finish()
        }
    }

    private fun displayFromAsset(fileName: String) {
        lifecycleScope.launch {
            currentPage = viewModel.loadLastPage(fileName = fileName).first()
            val pdfState: PdfUiState = viewModel.pdfUiState.first()

            pdfView.fromAsset(name = fileName)
                .applyPdfViewConfig(state = pdfState)
                .load()

            applyPdfViewQualitySettings()
        }
    }

    private fun displayFromFile(file: File) {
        lifecycleScope.launch {
            currentPage = viewModel.loadLastPage(fileName = pdfFileName).first()
            val pdfState: PdfUiState = viewModel.pdfUiState.first()

            pdfView.fromFile(file = file)
                .applyPdfViewConfig(state = pdfState)
                .load()

            applyPdfViewQualitySettings()
        }
    }

    private fun displayFromUri(uri: Uri) {
        pdfView.fromUri(uri = uri)
            .applyPdfViewConfig(state = viewModel.pdfUiState.value)
            .load()
        applyPdfViewQualitySettings()
    }

    private fun PDFView.Configurator.applyPdfViewConfig(state: PdfUiState): PDFView.Configurator {
        return this.apply {
            defaultPage(page = currentPage)
            onLoad(listener = this@PdfActivity)
            onPageChange(listener = this@PdfActivity)
            onError(listener = createOnErrorListener())
            onPageError(listener = createOnPageErrorListener())
            onRender(listener = createOnRenderListener())
            onTap(listener = createOnTapListener())
            fitEachPage(enable = true)
            nightMode(enable = state.isNightMode)
            swipeHorizontal(horizontal = state.isViewHorizontal)
            pageSnap(enable = state.isPageSnap)
            autoSpacing(enable = state.isAutoSpacing)
            password(password = password)
            spacing(spacing = state.spacing)
            enableSwipe(enable = true)
            pageFling(enable = false)
            enableDoubleTap(enable = true)
            enableAnnotationRendering(enable = true)
            scrollHandle(handle = DefaultScrollHandle(context = this@PdfActivity))
            enableAntialiasing(enable = true)
            linkHandler(handler = DefaultLinkHandler(pdfView = pdfView))
            pageFitPolicy(policy = FitPolicy.BOTH)
        }
    }

    private fun applyPdfViewQualitySettings() {
        pdfView.setBestQuality(enabled = true)
        pdfView.setMinZoom(zoom = 1f)
        pdfView.setMidZoom(zoom = 2.5f)
        pdfView.setMaxZoom(zoom = 4f)
    }

    private fun reloadPdfViewer() {
        pdfView.recycle()
        if (intent.data != null) displayFromUri(uri = intent.data!!) else displayFromAsset(fileName = pdfFileName)
    }

    override fun onPageChanged(page: Int, totalPages: Int) {
        currentPage = page
        binding.toolbar.title = "Page ${page + 1} of $totalPages"
        viewModel.saveLastPage(fileName = pdfFileName, page)
    }

    override fun loadComplete(totalPages: Int) {
        progressBar.visibility = View.GONE
        setMenuEnabled(enabled = true)
        logBookmarks(bookmarks = pdfView.bookmarks)
        currentPageCount = totalPages
    }

    private fun logBookmarks(bookmarks: List<PdfDocument.Bookmark>, prefix: String = "") {
        lifecycleScope.launch(context = Dispatchers.Default) {
            bookmarks.forEach { bookmark ->
                Log.v(Constants.TAG, "Bookmark $prefix ${bookmark.title}, Page: ${bookmark.pageIndex}")
                if (bookmark.hasChildren) {
                    logBookmarks(bookmarks = bookmark.children, prefix = "$prefix-")
                }
            }
        }
    }

    private fun createOnErrorListener(): OnErrorListener {
        return object : OnErrorListener {
            override fun onError(t: Throwable?) {
                if (t is PdfPasswordException) {
                    showPasswordDialog()
                } else {
                    showToast(msg = resources.getString(R.string.error_loading_pdf))
                    t?.printStackTrace()
                    Log.v(Constants.TAG, " onError: $t")
                }
            }
        }
    }

    private fun createOnPageErrorListener(): OnPageErrorListener {
        return object : OnPageErrorListener {
            override fun onPageError(page: Int, t: Throwable?) {
                t?.printStackTrace()
                showToast(msg = "onPageError")
                Log.v(Constants.TAG, "onPageError: $t on page: $page")
            }
        }
    }

    private fun createOnRenderListener(): OnRenderListener {
        return object : OnRenderListener {
            override fun onInitiallyRendered(totalPages: Int) {
                pdfView.fitToWidth(page = currentPage)
            }
        }
    }

    private fun createOnTapListener(): OnTapListener {
        return object : OnTapListener {
            override fun onTap(e: MotionEvent?): Boolean {
                return true
            }
        }
    }

    private fun showPasswordDialog() {
        PdfDialogHelper.createPasswordDialog(
            context = this@PdfActivity,
            onPasswordEntered = { password ->
                this.password = password
                displayFromAsset(fileName = pdfFileName)
            },
            onDismiss = ::finishWithTransition
        ).show()
    }

    private fun showJumpToDialog() {
        PdfDialogHelper.createJumpToDialog(
            context = this@PdfActivity,
            pageCount = pdfView.pagesCount,
            onPageSelected = { page -> pdfView.jumpTo(page = page - 1, withAnimation = true) }
        ).show()
    }

    private fun showPdfInfoDialog() {
        lifecycleScope.launch {
            PdfDialogHelper.createInfoDialog(
                pdfView = pdfView,
                meta = pdfView.documentMeta,
                file = pdfFileName.let {
                    PdfUtils.fileFromAsset(context = this@PdfActivity, assetName = it)
                }
            ).show()
        }
    }

    private fun savePdfCopy() {
        if (currentPageCount == 0) {
            showToast(msg = "Cannot save empty PDF")
            return
        }

        val defaultName: String = pdfFileName.removeSuffix(suffix = ".pdf") + "_copy.pdf"
        createFile.launch(defaultName)
    }

    private fun savePdfToUri(uri: Uri) {
        lifecycleScope.launch(context = Dispatchers.IO) {
            val success: Boolean = try {
                contentResolver.openOutputStream(uri)?.use { outStream ->
                    pdfView.saveAsCopy(out = outStream, flags = PDFView.FPDF_INCREMENTAL)
                    true
                } ?: false
            } catch (_: Exception) {
                false
            }

            withContext(context = Dispatchers.Main) {
                if (success) {
                    showToast(msg = "PDF saved successfully")
                    isModified = false
                    if (shouldExitAfterSave) finish()
                } else {
                    showToast(msg = "Failed to save PDF")
                }
                shouldExitAfterSave = false
            }
        }
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this@PdfActivity)
            .setTitle("Unsaved Changes")
            .setMessage("Save changes before exiting?")
            .setPositiveButton("Save") { _, _ ->
                shouldExitAfterSave = true
                savePdfCopy()
            }
            .setNegativeButton("Discard") { _, _ -> finish() }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this@PdfActivity, msg, Toast.LENGTH_LONG).show()
    }

    private fun finishWithTransition() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE, R.anim.left_to_right, R.anim.right_to_left
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.left_to_right, R.anim.right_to_left)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.pdf, menu)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfView.recycle()
        //searchManager.destroy()
    }
}