package com.ahmer.afzal.pdfviewer

import android.graphics.Color
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ahmer.afzal.pdfviewer.databinding.ActivityPdfBinding
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfPasswordException
import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.link.DefaultLinkHandler
import com.ahmer.pdfviewer.listener.OnErrorListener
import com.ahmer.pdfviewer.listener.OnLoadCompleteListener
import com.ahmer.pdfviewer.listener.OnPageChangeListener
import com.ahmer.pdfviewer.listener.OnPageErrorListener
import com.ahmer.pdfviewer.listener.OnPageScrollListener
import com.ahmer.pdfviewer.listener.OnRenderListener
import com.ahmer.pdfviewer.listener.OnTapListener
import com.ahmer.pdfviewer.scroll.DefaultScrollHandle
import com.ahmer.pdfviewer.util.FitPolicy
import com.ahmer.pdfviewer.util.PdfUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PdfActivity : AppCompatActivity(), OnPageChangeListener, OnLoadCompleteListener {

    private var _binding: ActivityPdfBinding? = null
    private val binding get() = _binding!!

    private lateinit var pdfView: PDFView
    private lateinit var progressBar: ProgressBar

    private val viewModel: PdfActivityModel by viewModels()
    private var currentPage: Int = 0
    private var password: String = ""
    private var pdfFileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupMenu()
        observeViewModel()
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

    private fun setupMenu() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            Log.d("MENU_CLICK", "Clicked: ${menuItem.title}")
            when (menuItem.itemId) {
                R.id.menuNightMode -> {
                    viewModel.toggleNightMode()
                    pdfFileName?.let { displayFromAsset(it) }
                    true
                }

                R.id.menuJumpTo -> {
                    showJumpToDialog()
                    true
                }

                R.id.menuPageSnap -> {
                    viewModel.togglePageSnap()
                    pdfFileName?.let { displayFromAsset(it) }
                    true
                }

                R.id.menuSwitchView -> {
                    viewModel.toggleViewOrientation()
                    pdfFileName?.let { displayFromAsset(it) }
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
                        viewModel.setSearchQuery(newText.orEmpty())
                    }
                })
            }
        }
    }

    private fun updateUI(state: PdfUiState) {
        binding.toolbar.menu?.let { menu ->
            menu.findItem(R.id.menuInfo).isEnabled = state.isPdfLoaded
            menu.findItem(R.id.menuJumpTo).isEnabled = state.isPdfLoaded
            menu.findItem(R.id.menuSwitchView).isEnabled = state.isPdfLoaded
            menu.findItem(R.id.menuNightMode).isEnabled = state.isPdfLoaded

            menu.findItem(R.id.menuNightMode).setIcon(
                if (state.isNightMode) R.drawable.ic_baseline_light_mode
                else R.drawable.ic_baseline_dark_mode
            )

            val switchViewItem = menu.findItem(R.id.menuSwitchView)
            switchViewItem.setIcon(
                if (state.isViewHorizontal) R.drawable.ic_baseline_swipe_vert
                else R.drawable.ic_baseline_swipe_horiz
            )
            switchViewItem.title = getString(
                if (state.isViewHorizontal) R.string.menu_pdf_view_vertical
                else R.string.menu_pdf_view_horizontal
            )

            val pageSnapItem = menu.findItem(R.id.menuPageSnap)
            pageSnapItem.title = getString(
                if (state.isPageSnap) R.string.menu_pdf_disable_snap_page
                else R.string.menu_pdf_enable_snap_page
            )
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun loadPdf() {
        pdfFileName = when (intent.getStringExtra(Constants.PDF_FILE)) {
            Constants.PDF_FILE_MAIN, Constants.PDF_FILE_PROTECTED -> {
                password = "5632"
                "grammar.pdf"
            }

            Constants.PDF_FILE_1 -> "example.pdf"
            Constants.PDF_FILE_2 -> "example1.pdf"
            Constants.PDF_FILE_3 -> "example3.pdf"
            else -> throw IllegalArgumentException("Unknown PDF file")
        }

        pdfFileName?.let { displayFromAsset(it) }
    }


    private fun displayFromAsset(fileName: String) {
        pdfView.setBackgroundColor(Color.LTGRAY)
        pdfView.fromAsset(fileName)
            .defaultPage(currentPage)
            .onLoad(this)
            .onPageChange(this)
            .onPageScroll(object : OnPageScrollListener {
                override fun onPageScrolled(page: Int, positionOffset: Float) {
                    Log.v(
                        Constants.TAG,
                        "onPageScrolled: Page $page PositionOffset: $positionOffset"
                    )
                }
            })
            .onError(object : OnErrorListener {
                override fun onError(t: Throwable?) {
                    if (t is PdfPasswordException) {
                        showPasswordDialog()
                    } else {
                        showToast(resources.getString(R.string.error_loading_pdf))
                        t?.printStackTrace()
                        Log.v(Constants.TAG, " onError: $t")
                    }
                }
            })
            .onPageError(object : OnPageErrorListener {
                override fun onPageError(page: Int, t: Throwable?) {
                    t?.printStackTrace()
                    showToast("onPageError")
                    Log.v(Constants.TAG, "onPageError: $t on page: $page")
                }
            })
            .onRender(object : OnRenderListener {
                override fun onInitiallyRendered(nbPages: Int) {
                    pdfView.fitToWidth(currentPage)
                }
            })
            .onTap(object : OnTapListener {
                override fun onTap(e: MotionEvent?): Boolean {
                    return true
                }
            })
            .fitEachPage(true)
            .nightMode(viewModel.uiState.value.isNightMode)
            .swipeHorizontal(viewModel.uiState.value.isViewHorizontal)
            .pageSnap(viewModel.uiState.value.isPageSnap)
            .autoSpacing(viewModel.uiState.value.isAutoSpacing)
            .password(password)
            .spacing(viewModel.uiState.value.spacing)
            .enableSwipe(true)
            .pageFling(false)
            .enableDoubleTap(true)
            .enableAnnotationRendering(true)
            .scrollHandle(DefaultScrollHandle(this))
            .enableAntialiasing(true)
            .linkHandler(DefaultLinkHandler(pdfView))
            .pageFitPolicy(FitPolicy.BOTH)
            .load()
        pdfView.setBestQuality(true)
        pdfView.setMinZoom(1f)
        pdfView.setMidZoom(2.5f)
        pdfView.setMaxZoom(4.0f)
    }

    private fun logBookmarks(bookmarks: List<PdfDocument.Bookmark>, prefix: String = "") {
        lifecycleScope.launch(Dispatchers.Default) {
            bookmarks.forEach { bookmark ->
                Log.v(
                    Constants.TAG,
                    "Bookmark $prefix ${bookmark.title}, Page: ${bookmark.pageIndex}"
                )
                if (bookmark.hasChildren) logBookmarks(bookmark.children, "$prefix-")
            }
        }
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        currentPage = page
        binding.toolbar.title = "Page ${page + 1} of $pageCount"
    }

    override fun loadComplete(nbPages: Int) {
        progressBar.visibility = View.GONE
        viewModel.setPdfLoaded(true)
        logBookmarks(pdfView.bookmarks())
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
                pdfFileName?.let { displayFromAsset(it) }
            },
            onDismiss = ::finishWithTransition
        ).show()
    }

    private fun showJumpToDialog() {
        PdfDialogHelper.createJumpToDialog(
            context = this,
            pageCount = pdfView.getPageCount(),
            onPageSelected = { page -> pdfView.jumpTo(page - 1) }
        ).show()
    }

    private fun showPdfInfoDialog() {
        lifecycleScope.launch {
            PdfDialogHelper.createInfoDialog(
                context = this@PdfActivity,
                meta = pdfView.documentMeta(),
                file = pdfFileName?.let { PdfUtils.fileFromAsset(this@PdfActivity, it) }
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