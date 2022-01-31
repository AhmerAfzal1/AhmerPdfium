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
    private val mViewModel: PdfFragmentModel by viewModels()
    private var mCurrentPage: Int = 0
    private var mIsAutoSpacing: Boolean = true
    private var mIsNightMode: Boolean = false
    private var mIsPageSnap: Boolean = true
    private var mIsViewHorizontal: Boolean = false
    private var mPassword: String = ""
    private var mPdfFile: String? = null
    private var mSpacing: Int = 5

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
        viewLifecycleOwner.lifecycleScope.launch {
            mIsAutoSpacing = mViewModel.flow.first().isAutoSpacing
            mIsPageSnap = mViewModel.flow.first().isPageSnap
            mIsViewHorizontal = mViewModel.flow.first().isViewHorizontal
            mSpacing = mViewModel.flow.first().spacing
        }
        setHasOptionsMenu(true)
        val mMenuInfo = mMenu.findItem(R.id.menuInfo)
        val mMenuJumpTo = mMenu.findItem(R.id.menuJumpTo)
        val mMenuNightMode = mMenu.findItem(R.id.menuNightMode)
        val mMenuSnapPage = mMenu.findItem(R.id.menuPageSnap)
        val mMenuSwitchView = mMenu.findItem(R.id.menuSwitchView)
        mMenuInfo.isEnabled = false
        mMenuJumpTo.isEnabled = false
        mMenuNightMode.isEnabled = false
        mMenuSnapPage.isEnabled = false
        mMenuSwitchView.isEnabled = false
        if (!mIsViewHorizontal) {
            mMenuSwitchView.setIcon(R.drawable.ic_baseline_swipe_horiz)
            mMenuSwitchView.title = getString(R.string.menu_pdf_view_horizontal)
        } else {
            mMenuSwitchView.setIcon(R.drawable.ic_baseline_swipe_vert)
            mMenuSwitchView.title = getString(R.string.menu_pdf_view_vertical)
        }
        if (mIsPageSnap) {
            mMenuSnapPage.title = getString(R.string.menu_pdf_disable_snap_page)
        } else {
            mMenuSnapPage.title = getString(R.string.menu_pdf_enable_snap_page)
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
            val open = dialog.findViewById<TextView>(R.id.btnOpen)
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
                super.requireActivity().onBackPressed()
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
                        dialog.dismiss()
                    }
                }
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
            val tvTitle = dialog.findViewById<TextView>(R.id.dialogTvTitle)
            val tvAuthor = dialog.findViewById<TextView>(R.id.dialogTvAuthor)
            val tvTotalPage = dialog.findViewById<TextView>(R.id.dialogTvTotalPage)
            val tvSubject = dialog.findViewById<TextView>(R.id.dialogTvSubject)
            val tvKeywords = dialog.findViewById<TextView>(R.id.dialogTvKeywords)
            val tvCreationDate = dialog.findViewById<TextView>(R.id.dialogTvCreationDate)
            val tvModifyDate = dialog.findViewById<TextView>(R.id.dialogTvModifyDate)
            val tvCreator = dialog.findViewById<TextView>(R.id.dialogTvCreator)
            val tvProducer = dialog.findViewById<TextView>(R.id.dialogTvProducer)
            val tvFileSize = dialog.findViewById<TextView>(R.id.dialogTvFileSize)
            val tvFilePath = dialog.findViewById<TextView>(R.id.dialogTvFilePath)
            val tvOk = dialog.findViewById<TextView>(R.id.btnOk)
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
            .autoSpacing(mIsAutoSpacing) // add dynamic spacing to fit each page on its own on the screen
            .pageFling(false) // make a fling change only a single page like ViewPager
            .enableDoubleTap(true)
            .enableAnnotationRendering(true)
            .password(mPassword)
            .scrollHandle(DefaultScrollHandle(requireContext()))
            .enableAntialiasing(true)
            .spacing(mSpacing)
            .linkHandler(DefaultLinkHandler(pdfView))
            .pageFitPolicy(FitPolicy.BOTH)
            .load()
        pdfView.setBestQuality(true)
        pdfView.setMinZoom(1f)
        pdfView.setMidZoom(2.5f)
        pdfView.setMaxZoom(4.0f)
        pdfView.setTextHighlightColor(Color.RED)
    }

    private fun printBookmarksTree(tree: List<Bookmark>, sep: String) {
        for (b in tree) {
            Log.v(
                Constants.LOG_TAG,
                String.format(
                    Locale.getDefault(), "Bookmark %s %s, Page %d", sep, b.title, b.pageIdx
                )
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
                val mPageSnap = mMenu.findItem(R.id.menuPageSnap)
                if (mIsPageSnap) {
                    mIsPageSnap = false
                    mIsAutoSpacing = false
                    mSpacing = 10
                    mPageSnap.title = getString(R.string.menu_pdf_enable_snap_page)
                } else {
                    mIsPageSnap = true
                    mIsAutoSpacing = true
                    mSpacing = 5
                    mPageSnap.title = getString(R.string.menu_pdf_disable_snap_page)
                }
                mViewModel.updateAutoSpacing(mIsAutoSpacing)
                mViewModel.updatePageSnap(mIsPageSnap)
                mViewModel.updateSpacing(mSpacing)
                mPdfFile?.let { displayFromAsset(mPdfView, it) }
                true
            }
            R.id.menuSwitchView -> {
                val mHorizontal = mMenu.findItem(R.id.menuSwitchView)
                if (!mIsViewHorizontal) {
                    mIsViewHorizontal = true
                    mHorizontal.setIcon(R.drawable.ic_baseline_swipe_vert)
                    mHorizontal.title = getString(R.string.menu_pdf_view_vertical)
                } else {
                    mIsViewHorizontal = false
                    mHorizontal.setIcon(R.drawable.ic_baseline_swipe_horiz)
                    mHorizontal.title = getString(R.string.menu_pdf_view_horizontal)
                }
                mViewModel.updateViewHorizontal(mIsViewHorizontal)
                mPdfFile?.let { displayFromAsset(mPdfView, it) }
                true
            }
            R.id.menuInfo -> {
                showMoreInfoDialog(mPdfView)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mSearchView.setOnQueryTextListener(null)
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
}