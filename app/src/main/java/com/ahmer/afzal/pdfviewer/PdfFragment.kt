package com.ahmer.afzal.pdfviewer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ahmer.afzal.pdfviewer.databinding.FragmentPdfBinding
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PdfFragment : Fragment(R.layout.fragment_pdf), MenuProvider, OnPageChangeListener,
    OnLoadCompleteListener {

    private var _binding: FragmentPdfBinding? = null
    private val binding get() = _binding!!

    private lateinit var pdfView: PDFView
    private lateinit var progressBar: ProgressBar

    private val viewModel: PdfFragmentModel by viewModels()
    private var currentPage: Int = 0
    private var password: String = ""
    private var pdfFileName: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPdfBinding.bind(view)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pdfUiState.collect { state ->
                    updateMenuItems(state)
                }
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

        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun updateMenuItems(state: PdfUiState) {
        binding.toolbar.menu?.let { menu ->
            menu.findItem(R.id.menuNightMode)?.setIcon(
                if (state.isNightMode) R.drawable.ic_baseline_light_mode
                else R.drawable.ic_baseline_dark_mode
            )

            menu.findItem(R.id.menuPageSnap)?.title = getString(
                if (state.isPageSnap) R.string.menu_pdf_disable_snap_page
                else R.string.menu_pdf_enable_snap_page
            )

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
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
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
        try {
            when (requireArguments().getInt(ARG_PDF_TYPE)) {
                TYPE_NORMAL -> {
                    password = "5632"
                    pdfFileName = "grammar.pdf"
                }

                TYPE_PROTECTED -> {
                    pdfFileName = Constants.PDF_SAMPLE_FILE_PASSWORD_PROTECTED
                }

                else -> throw IllegalArgumentException("Unknown PDF type")
            }

            lifecycleScope.launch {
                viewModel.loadLastPage(fileName = pdfFileName)
                    .onEach { currentPage = it }
                    .first()
                displayFromAsset(fileName = pdfFileName)
            }
        } catch (e: Exception) {
            showToast(msg = "Error loading PDF: ${e.message ?: "Unknown error"}")
            requireActivity().onBackPressedDispatcher.onBackPressed()
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

    private fun applyPdfViewQualitySettings() {
        pdfView.setBestQuality(enabled = true)
        pdfView.setMinZoom(zoom = 1f)
        pdfView.setMidZoom(zoom = 2.5f)
        pdfView.setMaxZoom(zoom = 4.0f)
    }

    private fun PDFView.Configurator.applyPdfViewConfig(state: PdfUiState): PDFView.Configurator {
        return this
            .defaultPage(page = currentPage)
            .onLoad(listener = this@PdfFragment)
            .onPageChange(listener = this@PdfFragment)
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
            .scrollHandle(handle = DefaultScrollHandle(context = requireContext()))
            .enableAntialiasing(enable = true)
            .linkHandler(handler = DefaultLinkHandler(pdfView = pdfView))
            .pageFitPolicy(policy = FitPolicy.BOTH)
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

                val textOverlayPaint = Paint().apply {
                    color = Color.argb(77, 0, 0, 255)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                val underlinePaint = Paint().apply {
                    color = Color.BLUE
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
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

                    canvas?.drawRect(devRect, textOverlayPaint)

                    val underlineY = devRect.bottom - 2f
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

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.pdf, menu)

        menu.findItem(R.id.menuSearch)?.let { searchItem ->
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

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        menuState(isEnabled = false)
        when (menuItem.itemId) {
            R.id.menuNightMode -> {
                val newNightMode = !viewModel.pdfUiState.value.isNightMode
                viewModel.updateNightMode(newNightMode)
                displayFromAsset(fileName = pdfFileName)
                return true
            }

            R.id.menuPageSnap -> {
                val newPageSnap = !viewModel.pdfUiState.value.isPageSnap
                viewModel.updatePageSnap(newPageSnap)
                viewModel.updateAutoSpacing(newPageSnap)
                viewModel.updateSpacing(if (newPageSnap) 5 else 10)
                displayFromAsset(fileName = pdfFileName)
                return true
            }

            R.id.menuSwitchView -> {
                val newViewHorizontal = !viewModel.pdfUiState.value.isViewHorizontal
                viewModel.updateViewHorizontal(newViewHorizontal)
                displayFromAsset(fileName = pdfFileName)
                return true
            }

            R.id.menuJumpTo -> {
                showJumpToDialog()
                return true
            }

            R.id.menuInfo -> {
                showPdfInfoDialog()
                return true
            }
        }
        return false
    }

    private fun showPasswordDialog() {
        PdfDialogHelper.createPasswordDialog(
            context = requireContext(),
            onPasswordEntered = { password ->
                this.password = password
                displayFromAsset(fileName = pdfFileName)
            },
            onDismiss = {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        ).show()
    }

    private fun showJumpToDialog() {
        PdfDialogHelper.createJumpToDialog(
            context = requireContext(),
            pageCount = pdfView.getPageCount(),
            onPageSelected = { page ->
                pdfView.jumpTo(page = page - 1, withAnimation = true)
            }
        ).show()
    }

    private fun showPdfInfoDialog() {
        lifecycleScope.launch {
            PdfDialogHelper.createInfoDialog(
                context = requireContext(),
                meta = pdfView.documentMeta(),
                file = pdfFileName.let {
                    PdfUtils.fileFromAsset(
                        context = requireContext(),
                        assetName = it
                    )
                }
            ).show()
        }
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

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        pdfView.recycle()
    }

    companion object {
        private const val ARG_PDF_TYPE = "pdf_type"
        const val TYPE_NORMAL = 1
        const val TYPE_PROTECTED = 2

        fun newInstance(pdfType: Int): PdfFragment {
            return PdfFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_PDF_TYPE, pdfType)
                }
            }
        }
    }
}