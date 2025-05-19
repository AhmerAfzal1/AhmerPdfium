package com.ahmer.afzal.pdfviewer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import com.ahmer.afzal.pdfviewer.databinding.ActivityPdfBinding
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfPasswordException
import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.link.DefaultLinkHandler
import com.ahmer.pdfviewer.listener.OnDrawListener
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PdfActivity : AppCompatActivity(), OnPageChangeListener, OnLoadCompleteListener {

    private lateinit var pdfView: PDFView
    private lateinit var progressBar: ProgressBar

    private var _binding: ActivityPdfBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PdfActivityModel by viewModels()
    private var currentPage: Int = 0
    private var password: String = ""
    private var pdfFileName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            viewModel.pdfUiState.collectLatest { state ->
                updateMenuItems(state)
            }
        }

        setupUI()
        setupMenu()
        loadPdf()
    }

    private fun setupUI() {
        pdfView = binding.pdfView.apply {
            setBackgroundColor(Color.LTGRAY)
        }
        progressBar = binding.progressBar

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finishWithTransition()
        }
    }

    private fun updateMenuItems(state: PdfUiState) {
        binding.toolbar.menu?.let { menu ->
            // Update night mode icon
            menu.findItem(R.id.menuNightMode)?.setIcon(
                if (state.isNightMode) R.drawable.ic_baseline_light_mode
                else R.drawable.ic_baseline_dark_mode
            )

            // Update page snap
            menu.findItem(R.id.menuPageSnap)?.title = getString(
                if (state.isPageSnap) R.string.menu_pdf_disable_snap_page
                else R.string.menu_pdf_enable_snap_page
            )

            // Update view orientation
            menu.findItem(R.id.menuSwitchView)?.apply {
                setIcon(
                    if (state.isViewHorizontal) R.drawable.ic_baseline_swipe_vert
                    else R.drawable.ic_baseline_swipe_horiz
                )
                title = getString(
                    if (state.isViewHorizontal) R.string.menu_pdf_view_vertical
                    else R.string.menu_pdf_view_horizontal
                )
            }
        }
    }

    private fun setupMenu() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            menuState(isEnabled = false)
            when (menuItem.itemId) {
                R.id.menuNightMode -> {
                    val newNightMode = !viewModel.pdfUiState.value.isNightMode
                    Log.v("AhmerPdf", "Night mode: $newNightMode")
                    viewModel.updateNightMode(newNightMode)
                    reloadPdfViewer()
                    true
                }

                R.id.menuPageSnap -> {
                    val newPageSnap = !viewModel.pdfUiState.value.isPageSnap
                    viewModel.updatePageSnap(newPageSnap)
                    viewModel.updateAutoSpacing(newPageSnap)
                    viewModel.updateSpacing(if (newPageSnap) 5 else 10)
                    reloadPdfViewer()
                    true
                }

                R.id.menuSwitchView -> {
                    val newViewHorizontal = !viewModel.pdfUiState.value.isViewHorizontal
                    viewModel.updateViewHorizontal(newViewHorizontal)
                    reloadPdfViewer()
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

                else -> false
            }
        }

        binding.toolbar.menu?.findItem(R.id.menuSearch)?.let { searchItem ->
            (searchItem.actionView as? SearchView)?.apply {
                queryHint = getString(android.R.string.search_go)
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?) = true
                    override fun onQueryTextChange(newText: String?) = true.also {
                        viewModel.updateSearchQuery(query = newText.orEmpty())
                    }
                })
            }
        }
    }

    private fun menuState(isEnabled: Boolean = false) {
        binding.toolbar.menu?.let { menu ->
            menu.findItem(R.id.menuInfo).isEnabled = isEnabled
            menu.findItem(R.id.menuJumpTo).isEnabled = isEnabled
            menu.findItem(R.id.menuSwitchView).isEnabled = isEnabled
            menu.findItem(R.id.menuNightMode).isEnabled = isEnabled
        }
    }

    private fun loadPdf() {
        when {
            intent.data != null -> {
                try {
                    val uri = intent.data!!
                    pdfFileName = PdfHelper.getFileNameFromUri(
                        context = this@PdfActivity,
                        uri = uri
                    ).toString()
                    lifecycleScope.launch {
                        viewModel.loadLastPage(fileName = pdfFileName)
                            .onEach { currentPage = it }
                            .first()
                        displayFromUri(uri = uri)
                    }
                } catch (e: Exception) {
                    showToast(msg = "Error loading PDF from URI: ${e.message ?: "Unknown error"}")
                    finish()
                }
            }

            intent.hasExtra(Constants.PDF_FILE) -> {
                try {
                    val pdfFileExtra = intent.getStringExtra(Constants.PDF_FILE)!!
                    pdfFileName = when (pdfFileExtra) {
                        Constants.PDF_FILE_MAIN, Constants.PDF_FILE_PROTECTED -> {
                            password = "5632" // Set password for protected PDFs
                            "grammar.pdf"
                        }

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

            else -> {
                showToast(msg = "No PDF selected")
                finish()
            }
        }
    }

    private fun displayFromAsset(fileName: String) {
        lifecycleScope.launch {
            val lastPage: Int = viewModel.loadLastPage(fileName = pdfFileName).first()
            val pdfState: PdfUiState = viewModel.pdfUiState.first()

            currentPage = lastPage
            pdfView.setBackgroundColor(Color.LTGRAY)

            pdfView.fromAsset(name = fileName)
                .applyPdfViewConfig(state = pdfState)
                .load()

            applyPdfViewQualitySettings()
        }
    }

    private fun displayFromUri(uri: Uri) {
        pdfView.setBackgroundColor(Color.LTGRAY)
        pdfView.fromUri(uri = uri)
            .applyPdfViewConfig(state = viewModel.pdfUiState.value)
            .load()
        applyPdfViewQualitySettings()
    }

    private fun reloadPdfViewer() {
        pdfView.recycle()
        if (intent.data != null) {
            displayFromUri(intent.data!!)
        } else {
            displayFromAsset(pdfFileName)
        }
    }

    private fun PDFView.Configurator.applyPdfViewConfig(state: PdfUiState): PDFView.Configurator {
        return this
            .defaultPage(page = currentPage)
            .onLoad(listener = this@PdfActivity)
            .onPageChange(listener = this@PdfActivity)
            .onError(listener = createOnErrorListener())
            .onPageError(listener = createOnPageErrorListener())
            .onRender(listener = createOnRenderListener())
            .onTap(listener = createOnTapListener())
            .onDrawAll(listener = createOnDrawListener())
            .fitEachPage(enable = true)
            .nightMode(enable = state.isNightMode)
            .swipeHorizontal(horizontal = state.isViewHorizontal)
            .pageSnap(enable = state.isPageSnap)
            .autoSpacing(enable = state.isAutoSpacing)
            .password(password = password)
            .spacing(spacing = state.spacing)
            .enableSwipe(enable = true)
            .pageFling(enable = false)
            .enableDoubleTap(enable = true)
            .enableAnnotationRendering(enable = true)
            .scrollHandle(handle = DefaultScrollHandle(context = this@PdfActivity))
            .enableAntialiasing(enable = true)
            .linkHandler(handler = DefaultLinkHandler(pdfView = pdfView))
            .pageFitPolicy(policy = FitPolicy.BOTH)
    }

    private fun logBookmarks(bookmarks: List<PdfDocument.Bookmark>, prefix: String = "") {
        lifecycleScope.launch(context = Dispatchers.Default) {
            bookmarks.forEach { bookmark ->
                Log.v(
                    Constants.TAG,
                    "Bookmark $prefix ${bookmark.title}, Page: ${bookmark.pageIndex}"
                )
                if (bookmark.hasChildren) {
                    logBookmarks(
                        bookmarks = bookmark.children,
                        prefix = "$prefix-"
                    )
                }
            }
        }
    }

    override fun onPageChanged(page: Int, totalPages: Int) {
        currentPage = page
        binding.toolbar.title = "Page ${page + 1} of $totalPages"
        viewModel.saveLastPage(fileName = pdfFileName, page = page)
    }

    override fun loadComplete(totalPages: Int) {
        progressBar.visibility = View.GONE
        menuState(isEnabled = true)
        logBookmarks(bookmarks = pdfView.bookmarks())
    }

    private fun applyPdfViewQualitySettings() {
        pdfView.setBestQuality(enabled = true)
        pdfView.setMinZoom(zoom = 1f)
        pdfView.setMidZoom(zoom = 2.5f)
        pdfView.setMaxZoom(zoom = 4.0f)
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

    private fun createOnDrawListener(): OnDrawListener {
        return object : OnDrawListener {
            override fun onLayerDrawn(
                canvas: Canvas?,
                pageWidth: Float,
                pageHeight: Float,
                currentPage: Int
            ) {
                val pdfFile = pdfView.pdfFile ?: return

                // Paint for URL text overlay (semi-transparent blue)
                val textOverlayPaint = Paint().apply {
                    color = Color.argb(77, 0, 0, 255) // 70% transparent (30% visible)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                // Paint for underline (thicker blue line)
                val underlinePaint = Paint().apply {
                    color = Color.BLUE
                    style = Paint.Style.STROKE
                    strokeWidth = 2f // Thicker than default underline
                    isAntiAlias = true
                }

                val links = pdfFile.getPageLinks(
                    pageIndex = currentPage,
                    size = pdfFile.getPageSize(pageIndex = currentPage),
                    posX = 0f,
                    posY = 0f
                )

                links.forEach { link ->
                    val devRect = pdfFile.mapRectToDevice(
                        pageIndex = currentPage,
                        startX = 0,
                        startY = 0,
                        sizeX = pageWidth.toInt(),
                        sizeY = pageHeight.toInt(),
                        rect = link.bounds
                    ).apply { sort() }

                    // 1. Draw semi-transparent blue overlay (simulates colored text)
                    canvas?.drawRect(devRect, textOverlayPaint)

                    // 2. Draw custom underline (thicker than default)
                    val underlineY = devRect.bottom - 2f // Adjust position as needed
                    canvas?.drawLine(
                        devRect.left,
                        underlineY,
                        devRect.right,
                        underlineY,
                        underlinePaint
                    )
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.pdf, menu)
        return true
    }

    private fun showPasswordDialog() {
        PdfDialogHelper.createPasswordDialog(
            context = this,
            onPasswordEntered = { password ->
                this.password = password
                displayFromAsset(fileName = pdfFileName)
            },
            onDismiss = ::finishWithTransition
        ).show()
    }

    private fun showJumpToDialog() {
        PdfDialogHelper.createJumpToDialog(
            context = this,
            pageCount = pdfView.getPageCount(),
            onPageSelected = { page -> pdfView.jumpTo(page = page - 1, withAnimation = true) }
        ).show()
    }

    private fun showPdfInfoDialog() {
        lifecycleScope.launch {
            PdfDialogHelper.createInfoDialog(
                context = this@PdfActivity,
                meta = pdfView.documentMeta(),
                file = pdfFileName.let {
                    PdfUtils.fileFromAsset(
                        context = this@PdfActivity,
                        assetName = it
                    )
                }
            ).show()
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this@PdfActivity, msg, Toast.LENGTH_LONG).show()
    }

    private fun finishWithTransition() {
        finish()
        overridePendingTransition(R.anim.left_to_right, R.anim.right_to_left)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        pdfView.recycle()
    }
}