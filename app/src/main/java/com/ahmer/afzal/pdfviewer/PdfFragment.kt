package com.ahmer.afzal.pdfviewer

import android.graphics.Color
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
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PdfFragment : Fragment(R.layout.fragment_pdf), MenuProvider, OnPageChangeListener,
    OnLoadCompleteListener {

    // View Binding
    private var _binding: FragmentPdfBinding? = null
    private val binding get() = _binding!!

    // PDF Components
    private lateinit var pdfView: PDFView
    private lateinit var progressBar: ProgressBar

    // Menu Components
    private lateinit var searchView: SearchView
    private var menu: Menu? = null

    // ViewModel
    private val viewModel: PdfFragmentModel by viewModels()

    // State
    private var currentPage: Int = 0
    private var password: String = ""
    private var pdfFileName: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPdfBinding.bind(view)

        setupUI()
        setupMenu()
        observeViewModel()
        loadPdf()
    }

    private fun setupUI() {
        Log.d(
            "PdfFragment",
            "Toolbar is action bar: ${(requireActivity() as? AppCompatActivity)?.supportActionBar != null}"
        )
        // Setup toolbar
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Add menu provider
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Initialize PDF components
        pdfView = binding.pdfView.apply {
            setBackgroundColor(Color.LTGRAY)
        }
        progressBar = binding.progressBar
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: PdfUiState) {
        // Update menu items based on state
        menu?.let { safeMenu -> // Safe access to menu
            safeMenu.findItem(R.id.menuInfo).isEnabled = state.isPdfLoaded
            safeMenu.findItem(R.id.menuJumpTo).isEnabled = state.isPdfLoaded
            safeMenu.findItem(R.id.menuSwitchView).isEnabled = state.isPdfLoaded
            safeMenu.findItem(R.id.menuNightMode).isEnabled = state.isPdfLoaded

            // Update icons based on state
            safeMenu.findItem(R.id.menuNightMode).setIcon(
                if (state.isNightMode) R.drawable.ic_baseline_light_mode
                else R.drawable.ic_baseline_dark_mode
            )

            val switchViewItem = safeMenu.findItem(R.id.menuSwitchView)
            switchViewItem.setIcon(
                if (state.isViewHorizontal) R.drawable.ic_baseline_swipe_vert
                else R.drawable.ic_baseline_swipe_horiz
            )
            switchViewItem.title = getString(
                if (state.isViewHorizontal) R.string.menu_pdf_view_vertical
                else R.string.menu_pdf_view_horizontal
            )

            val pageSnapItem = safeMenu.findItem(R.id.menuPageSnap)
            pageSnapItem.title = getString(
                if (state.isPageSnap) R.string.menu_pdf_disable_snap_page
                else R.string.menu_pdf_enable_snap_page
            )
        }
    }

    private fun loadPdf() {
        pdfFileName = when (requireArguments().getInt(ARG_PDF_TYPE)) {
            TYPE_NORMAL -> {
                password = "5632"
                "grammar.pdf"
            }

            TYPE_PROTECTED -> {
                Constants.PDF_SAMPLE_FILE_PASSWORD_PROTECTED
            }

            else -> throw IllegalArgumentException("Unknown PDF type")
        }

        pdfFileName?.let { displayFromAsset(it) }
    }

    private fun displayFromAsset(fileName: String) {
        pdfView.fromAsset(fileName)
            .defaultPage(currentPage)
            .onLoad(this)
            .onPageChange(this)
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
            .nightMode(viewModel.uiState.value.isNightMode)
            .swipeHorizontal(viewModel.uiState.value.isViewHorizontal)
            .pageSnap(viewModel.uiState.value.isPageSnap)
            .autoSpacing(viewModel.uiState.value.isAutoSpacing)
            .password(password)
            .spacing(viewModel.uiState.value.spacing)
            .enableDoubleTap(true)
            .enableAnnotationRendering(true)
            .enableAntialiasing(true)
            .scrollHandle(DefaultScrollHandle(requireContext()))
            .pageFitPolicy(FitPolicy.BOTH)
            .load()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        Log.d("PdfFragment", "Creating menu")
        menuInflater.inflate(R.menu.pdf, menu)
        this.menu = menu

        // Setup search view
        searchView = (menu.findItem(R.id.menuSearch).actionView as SearchView).apply {
            queryHint = getString(android.R.string.search_go)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let { viewModel.setSearchQuery(it) }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let { viewModel.setSearchQuery(it) }
                    return true
                }
            })
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
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

    private fun showPasswordDialog() {
        PdfDialogHelper.createPasswordDialog(
            context = requireContext(),
            onPasswordEntered = { password ->
                this.password = password
                pdfFileName?.let { displayFromAsset(it) }
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
                pdfView.jumpTo(page - 1, true)
            }
        ).show()
    }

    private fun showPdfInfoDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            PdfDialogHelper.createInfoDialog(
                context = requireContext(),
                meta = pdfView.documentMeta(),
                file = pdfFileName?.let { PdfUtils.fileFromAsset(requireContext(), it) }
            ).show()
        }
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        currentPage = page
        binding.toolbar.title = "Page ${page + 1} of $pageCount"
    }

    override fun loadComplete(nbPages: Int) {
        progressBar.visibility = View.GONE
        viewModel.setPdfLoaded(true)

        viewLifecycleOwner.lifecycleScope.launch {
            logBookmarks(pdfView.bookmarks())
        }
    }

    private fun logBookmarks(bookmarks: List<PdfDocument.Bookmark>, prefix: String = "") {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            bookmarks.forEach { bookmark ->
                Log.v(
                    Constants.TAG,
                    "Bookmark $prefix ${bookmark.title}, Page: ${bookmark.pageIndex}"
                )
                if (bookmark.hasChildren) {
                    logBookmarks(bookmark.children, "$prefix-")
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        menu = null
        _binding = null
        searchView.setOnQueryTextListener(null)
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