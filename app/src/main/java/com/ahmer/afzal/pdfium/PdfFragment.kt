package com.ahmer.afzal.pdfium

import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ahmer.afzal.pdfium.databinding.FragmentPdfBinding
import com.ahmer.pdfium.Bookmark
import com.ahmer.pdfium.Meta
import com.ahmer.pdfium.PdfPasswordException
import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.link.DefaultLinkHandler
import com.ahmer.pdfviewer.listener.*
import com.ahmer.pdfviewer.scroll.DefaultScrollHandle
import com.ahmer.pdfviewer.util.FitPolicy
import com.ahmer.pdfviewer.util.PdfFileUtils
import dagger.hilt.android.AndroidEntryPoint
import io.ahmer.utils.utilcode.FileUtils
import io.ahmer.utils.utilcode.StringUtils
import io.ahmer.utils.utilcode.ThrowableUtils
import io.ahmer.utils.utilcode.ToastUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

@AndroidEntryPoint
class PdfFragment : Fragment(R.layout.fragment_pdf), OnPageChangeListener, OnLoadCompleteListener {

    private lateinit var mBinding: FragmentPdfBinding
    private lateinit var mMenu: Menu
    private lateinit var mPdfView: PDFView
    private lateinit var mProgressBar: ProgressBar
    private lateinit var mSearchView: SearchView
    private var mCurrentPage: Int = 0
    private val mViewModel: PdfViewModel by viewModels()
    private var mIsNightMode: Boolean = false
    private var mIsPageSnap: Boolean = true
    private var mIsViewHorizontal: Boolean = false
    private var mPassword: String = ""
    private var mPdfFile: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mBinding = FragmentPdfBinding.bind(view)
        (activity as AppCompatActivity).setSupportActionBar(mBinding.toolbar)
        mBinding.apply {
            mPdfView = pdfView
            mProgressBar = progressBar
            mMenu = toolbar.menu
            toolbar.setNavigationOnClickListener {
                requireActivity().onBackPressed()
            }
        }
        lifecycleScope.launch {
            mIsPageSnap = mViewModel.flow.first().pdfPageSnap
            mIsViewHorizontal = mViewModel.flow.first().pdfViewChange
        }
        setHasOptionsMenu(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mMenu.findItem(R.id.menuInfo).icon.setTint(Color.WHITE)
            mMenu.findItem(R.id.menuJumpTo).icon.setTint(Color.WHITE)
            mMenu.findItem(R.id.menuSwitchView).icon.setTint(Color.WHITE)
            mMenu.findItem(R.id.menuNightMode).icon.setTint(Color.WHITE)
        }
        init()
    }

    private fun showPasswordDialog() {
        val dialog = Dialog(requireContext())
        try {
            dialog.window!!.requestFeature(Window.FEATURE_NO_TITLE)
            dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.setContentView(R.layout.dialog_pdf_password)
            dialog.window!!.setLayout(-1, -2)
            dialog.window!!.setLayout(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            val inputPass = dialog.findViewById<EditText>(R.id.inputPassword)
            inputPass.requestFocus()
            inputPass.postDelayed({
                val imm =
                    (requireContext().getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager)
                imm.showSoftInput(inputPass, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
            val open = dialog.findViewById<TextView>(R.id.tvOpen)
            open.isClickable = false
            open.setOnClickListener { v: View ->
                when {
                    inputPass.text.toString() == "" -> {
                        ToastUtils.showShort(v.context.getString(R.string.password_not_empty))
                    }
                    StringUtils.isSpace(
                        inputPass.text.toString()
                    ) -> {
                        ToastUtils.showShort(v.context.getString(R.string.password_not_space))
                    }
                    else -> {
                        mPassword = inputPass.text.toString()
                        mPdfFile?.let { displayFromAsset(mPdfView, it) }
                        dialog.dismiss()
                    }
                }
            }
            val cancel = dialog.findViewById<TextView>(R.id.btnCancel)
            cancel.setOnClickListener {
                AppServices.hideKeyboard()
                dialog.dismiss()
            }
            inputPass.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, srt: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {
                    open.isClickable = !TextUtils.isEmpty(s)
                }
            })
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showJumpToDialog(pdfView: PDFView) {
        val dialog = Dialog(requireContext())
        try {
            dialog.window!!.requestFeature(Window.FEATURE_NO_TITLE)
            dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.setContentView(R.layout.dialog_pdf_jumpto)
            dialog.window!!.setLayout(-1, -2)
            dialog.window!!.setLayout(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            val inputPageNo = dialog.findViewById<EditText>(R.id.inputPageNumber)
            inputPageNo.requestFocus()
            inputPageNo.postDelayed({
                val imm =
                    (requireContext().getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager)
                imm.showSoftInput(inputPageNo, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
            val goTo = dialog.findViewById<Button>(R.id.btnGoTo)
            goTo.isClickable = false
            goTo.setOnClickListener {
                val pageNumber: String = inputPageNo.text.toString()
                val number: Int = pageNumber.toInt()
                when {
                    pageNumber == "" -> {
                        ToastUtils.showShort(getString(R.string.please_enter_number))
                    }
                    number > pdfView.getTotalPagesCount() -> {
                        ToastUtils.showShort(getString(R.string.no_page))
                    }
                    else -> {
                        AppServices.hideKeyboard()
                        pdfView.jumpTo(number - 1, true)
                    }
                }
                dialog.dismiss()
            }
            val cancel = dialog.findViewById<Button>(R.id.btnCancel)
            cancel.setOnClickListener {
                AppServices.hideKeyboard()
                dialog.dismiss()
            }
            inputPageNo.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, srt: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {
                    goTo.isClickable = !TextUtils.isEmpty(s)
                }
            })
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showMoreInfoDialog(pdfView: PDFView) {
        val dialog = Dialog(requireContext())
        try {
            dialog.window!!.requestFeature(Window.FEATURE_NO_TITLE)
            dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.setContentView(R.layout.dialog_pdf_info)
            dialog.window!!.setLayout(-1, -2)
            dialog.window!!.setLayout(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            val tvTitle = dialog.findViewById<TextView>(R.id.tvTitle)
            val tvAuthor = dialog.findViewById<TextView>(R.id.tvAuthor)
            val tvTotalPage = dialog.findViewById<TextView>(R.id.tvTotalPage)
            val tvSubject = dialog.findViewById<TextView>(R.id.tvSubject)
            val tvKeywords = dialog.findViewById<TextView>(R.id.tvKeywords)
            val tvCreationDate = dialog.findViewById<TextView>(R.id.tvCreationDate)
            val tvModifyDate = dialog.findViewById<TextView>(R.id.tvModifyDate)
            val tvCreator = dialog.findViewById<TextView>(R.id.tvCreator)
            val tvProducer = dialog.findViewById<TextView>(R.id.tvProducer)
            val tvFileSize = dialog.findViewById<TextView>(R.id.tvFileSize)
            val tvFilePath = dialog.findViewById<TextView>(R.id.tvFilePath)
            val tvOk = dialog.findViewById<TextView>(R.id.tvOk)
            val meta: Meta? = pdfView.getDocumentMeta()
            tvTitle.text = meta!!.title
            tvAuthor.text = meta.author
            tvTotalPage.text = String.format(Locale.getDefault(), "%d", meta.totalPages)
            tvSubject.text = meta.subject
            tvKeywords.text = meta.keywords
            tvCreationDate.text = meta.creationDate
            tvModifyDate.text = meta.modDate
            tvCreator.text = meta.creator
            tvProducer.text = meta.producer
            val file = mPdfFile?.let { PdfFileUtils.fileFromAsset(requireContext(), it) }
            tvFileSize.text = FileUtils.getSize(file)
            tvFilePath.text = file?.path
            tvOk.setOnClickListener {
                dialog.dismiss()
            }
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun displayFromAsset(pdfView: PDFView, file: String) {
        mMenu.findItem(R.id.menuInfo).isEnabled = false
        mMenu.findItem(R.id.menuJumpTo).isEnabled = false
        mMenu.findItem(R.id.menuSwitchView).isEnabled = false
        mMenu.findItem(R.id.menuNightMode).isEnabled = false
        pdfView.setBackgroundColor(Color.LTGRAY)
        pdfView.fromAsset(file)
            .defaultPage(mCurrentPage)
            .onLoad(this)
            .onPageChange(this)
            .onPageScroll(object : OnPageScrollListener {
                override fun onPageScrolled(page: Int, positionOffset: Float) {
                    Log.v(
                        Constants.LOG_TAG,
                        "onPageScrolled: Page $page PositionOffset: $positionOffset"
                    )
                }
            })
            .onError(object : OnErrorListener {
                override fun onError(t: Throwable?) {
                    if (t is PdfPasswordException) {
                        showPasswordDialog()
                    } else {
                        ToastUtils.showLong(resources.getString(R.string.error_loading_pdf))
                        t?.printStackTrace()
                        Log.v(Constants.LOG_TAG, " onError: $t")
                    }
                }
            })
            .onPageError(object : OnPageErrorListener {
                override fun onPageError(page: Int, t: Throwable?) {
                    t?.printStackTrace()
                    ToastUtils.showLong("onPageError")
                    Log.v(Constants.LOG_TAG, "onPageError: $t on page: $page")
                }
            })
            .onRender(object : OnRenderListener {
                override fun onInitiallyRendered(nbPages: Int) {
                    pdfView.fitToWidth(mCurrentPage)
                }
            })
            .onTap(object : OnTapListener {
                override fun onTap(e: MotionEvent?): Boolean {
                    return true
                }
            })
            .fitEachPage(true)
            .nightMode(mIsNightMode)
            .enableSwipe(true)
            .swipeHorizontal(mIsViewHorizontal)
            .pageSnap(mIsPageSnap) // snap pages to screen boundaries
            .autoSpacing(true) // add dynamic spacing to fit each page on its own on the screen
            .pageFling(false) // make a fling change only a single page like ViewPager
            .enableDoubleTap(true)
            .enableAnnotationRendering(true)
            .password(mPassword)
            .scrollHandle(DefaultScrollHandle(requireContext()))
            .enableAntialiasing(true)
            .spacing(5)
            .linkHandler(DefaultLinkHandler(pdfView))
            .pageFitPolicy(FitPolicy.BOTH)
            .load()
        pdfView.useBestQuality(true)
        pdfView.minZoom = 1.0f
        pdfView.midZoom = 2.5f
        pdfView.maxZoom = 4.0f
        pdfView.setTextHighlightColor(Color.RED)
    }

    private fun printBookmarksTree(tree: List<Bookmark>, sep: String) {
        for (b in tree) {
            Log.v(
                Constants.LOG_TAG,
                " ${
                    String.format(
                        Locale.getDefault(), "Bookmark %s %s, Page %d", sep, b.title, b.pageIdx
                    )
                }"
            )
            if (b.hasChildren()) {
                printBookmarksTree(b.children, "$sep-")
            }
        }
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        mCurrentPage = page
        val title = String.format(Locale.getDefault(), "%s %s of %s", "Page", page + 1, pageCount)
        mBinding.toolbar.title = title
    }

    override fun loadComplete(nbPages: Int) {
        mProgressBar.visibility = View.GONE
        printBookmarksTree(mPdfView.getTableOfContents(), "-")
        mMenu.findItem(R.id.menuInfo).isEnabled = true
        mMenu.findItem(R.id.menuJumpTo).isEnabled = true
        mMenu.findItem(R.id.menuSwitchView).isEnabled = true
        mMenu.findItem(R.id.menuNightMode).isEnabled = true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.pdf, menu)
        val searchItem = menu.findItem(R.id.menuSearch)
        mSearchView = searchItem.actionView as SearchView
        val pendingSearchQuery = mViewModel.searchDescription.value
        if (pendingSearchQuery.isNotEmpty()) {
            searchItem.expandActionView()
            mSearchView.setQuery(pendingSearchQuery, false)
        }
        mSearchView.onQueryTextChanged {
            mViewModel.searchDescription.value = it
            mPdfView.setSearchQuery(it)
            Log.v(Constants.LOG_TAG, "Search query: $it")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuNightMode -> {
                if (!mIsNightMode) {
                    mIsNightMode = true
                    mMenu.findItem(R.id.menuNightMode).setIcon(R.drawable.ic_baseline_light_mode)
                } else {
                    mIsNightMode = false
                    mMenu.findItem(R.id.menuNightMode).setIcon(R.drawable.ic_baseline_dark_mode)
                }
                mPdfFile?.let { displayFromAsset(mPdfView, it) }
                true
            }
            R.id.menuJumpTo -> {
                showJumpToDialog(mPdfView)
                true
            }
            R.id.menuPageSnap -> {
                mIsPageSnap = !mIsPageSnap
                mViewModel.updatePdfPageSnap(mIsPageSnap)
                true
            }
            R.id.menuSwitchView -> {
                if (mIsViewHorizontal) {
                    mIsViewHorizontal = true
                    mMenu.findItem(R.id.menuSwitchView).setIcon(R.drawable.ic_baseline_swipe_vert)
                } else {
                    mIsViewHorizontal = false
                    mMenu.findItem(R.id.menuSwitchView).setIcon(R.drawable.ic_baseline_swipe_horiz)
                }
                mPdfFile?.let { displayFromAsset(mPdfView, it) }
                mViewModel.updatePdfViewChange(mIsViewHorizontal)
                true
            }
            R.id.menuInfo -> {
                showMoreInfoDialog(mPdfView)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun init() {
        try {
            if (arguments?.getBoolean(Constants.PDF_IS_NORMAL) == true) {
                mPdfFile = Constants.PDF_SAMPLE_FILE
                mPassword = "5632"
            } else {
                mPdfFile = Constants.PDF_SAMPLE_FILE
            }
            mPdfFile?.let { displayFromAsset(mPdfView, it) }
        } catch (e: Exception) {
            ThrowableUtils.getFullStackTrace(e)
            Log.v(
                Constants.LOG_TAG, "Calling arguments or getArguments won't work. Ex: ${e.message}"
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mSearchView.setOnQueryTextListener(null)
    }
}