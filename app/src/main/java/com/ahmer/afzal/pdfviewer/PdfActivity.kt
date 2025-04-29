package com.ahmer.afzal.pdfviewer

import android.app.Dialog
import android.app.SearchManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
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
import com.google.android.material.appbar.MaterialToolbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class PdfActivity : AppCompatActivity(), OnPageChangeListener, OnLoadCompleteListener {

    private lateinit var mBinding: ActivityPdfBinding
    private lateinit var mMenu: Menu
    private lateinit var mPdfView: PDFView
    private lateinit var mProgressBar: ProgressBar
    private val mViewModel: PdfActivityModel by viewModels()
    private var mCurrentPage: Int = 0
    private var mIsAutoSpacing: Boolean = true
    private var mIsNightMode: Boolean = false
    private var mIsPageSnap: Boolean = true
    private var mIsViewHorizontal: Boolean = false
    private var mPassword: String = ""
    private var mPdfFile: String? = null
    private var mSpacing: Int = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityPdfBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        mBinding.apply {
            mPdfView = pdfView
            mProgressBar = progressBar
            mMenu = toolbar.menu
            toolbar.setOnClickListener {
                finish()
                if (Build.VERSION.SDK_INT >= 34) {
                    overrideActivityTransition(
                        OVERRIDE_TRANSITION_OPEN, R.anim.left_to_right, R.anim.right_to_left
                    )
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(R.anim.left_to_right, R.anim.right_to_left)
                }
            }
            toolbarClick(toolbar)
        }
        lifecycleScope.launch(Dispatchers.Main) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mViewModel.isAutoSpacing.collectLatest { mIsAutoSpacing = it }
                mViewModel.isPageSnap.collectLatest { mIsPageSnap = it }
                mViewModel.getSpacing.collectLatest { mSpacing = it }
                mViewModel.isViewHorizontal.collectLatest { mIsViewHorizontal = it }
            }
        }
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

    private fun displayFromAsset(pdfView: PDFView, file: String) {
        pdfView.setBackgroundColor(Color.LTGRAY)
        pdfView.fromAsset(file)
            .defaultPage(mCurrentPage)
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
            .scrollHandle(DefaultScrollHandle(this))
            .enableAntialiasing(true)
            .spacing(mSpacing)
            .linkHandler(DefaultLinkHandler(pdfView))
            .pageFitPolicy(FitPolicy.BOTH)
            .load()
        pdfView.setBestQuality(true)
        pdfView.setMinZoom(1f)
        pdfView.setMidZoom(2.5f)
        pdfView.setMaxZoom(4.0f)
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        mCurrentPage = page
        mBinding.toolbar.title = "Page ${page + 1} of $pageCount"
    }

    override fun loadComplete(nbPages: Int) {
        mProgressBar.visibility = View.GONE
        lifecycleScope.launch {
            printBookmarksTree(mPdfView.bookmarks(), "-")
        }
        mMenu.findItem(R.id.menuInfo).isEnabled = true
        mMenu.findItem(R.id.menuJumpTo).isEnabled = true
        mMenu.findItem(R.id.menuSwitchView).isEnabled = true
        mMenu.findItem(R.id.menuNightMode).isEnabled = true
    }

    private fun printBookmarksTree(tree: List<PdfDocument.Bookmark>, sep: String) {
        for (b in tree) {
            Log.v(Constants.TAG, "Bookmark $sep ${b.title}, Page: ${b.pageIndex}")
            if (b.hasChildren()) {
                printBookmarksTree(b.children, "$sep-")
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.pdf, menu)
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchView = menu.findItem(R.id.menuSearch).actionView as SearchView
        // Assumes current activity is the searchable activity
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.queryHint = getString(android.R.string.search_go)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(query: String?): Boolean {
                //mPdfView.setSearchQuery(query.orEmpty())
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    private fun toolbarClick(toolbar: MaterialToolbar) {
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menuNightMode -> {
                    if (!mIsNightMode) {
                        mIsNightMode = true
                        mMenu.findItem(R.id.menuNightMode)
                            .setIcon(R.drawable.ic_baseline_light_mode)
                    } else {
                        mIsNightMode = false
                        mMenu.findItem(R.id.menuNightMode).setIcon(R.drawable.ic_baseline_dark_mode)
                    }
                    mPdfFile?.let { displayFromAsset(mPdfView, it) }
                }

                R.id.menuJumpTo -> {
                    showJumpToDialog()
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
                    mViewModel.updatePageSnap(mIsPageSnap)
                    mPdfFile?.let { displayFromAsset(mPdfView, it) }
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
                    mViewModel.updateViewChange(mIsViewHorizontal)
                    mPdfFile?.let { displayFromAsset(mPdfView, it) }
                }

                R.id.menuInfo -> {
                    showInfoDialog()
                }
            }
            true
        }
    }

    private fun showPasswordDialog() {
        val dialog = Dialog(this).apply {
            setContentView(R.layout.dialog_pdf_password)
            window?.setLayout(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.apply {
            val etPassword = findViewById<EditText>(R.id.inputPassword)
            val btnOpen = findViewById<Button>(R.id.btnOpen)

            etPassword.postDelayed({ showKeyboard(etPassword) }, 100)

            btnOpen.setOnClickListener {
                when {
                    etPassword.text.isNullOrEmpty() -> showToast(getString(R.string.password_not_empty))
                    etPassword.text.isBlank() -> showToast(getString(R.string.password_not_space))
                    else -> {
                        mPassword = etPassword.text.toString()
                        mPdfFile?.let { displayFromAsset(mPdfView, it) }
                        dismiss()
                    }
                }
            }

            findViewById<Button>(R.id.btnCancel).setOnClickListener {
                hideKeyboard()
                dismiss()
                finishWithTransition()
            }
        }.show()
    }

    private fun showJumpToDialog() {
        val dialog = Dialog(this).apply {
            setContentView(R.layout.dialog_pdf_jumpto)
            window?.setLayout(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.apply {
            val etPage = findViewById<EditText>(R.id.inputPageNumber)
            val totalPages = mPdfView.getPageCount()

            etPage.postDelayed({ showKeyboard(etPage) }, 100)

            findViewById<Button>(R.id.btnGoTo).setOnClickListener {
                etPage.text.toString().toIntOrNull()?.let { page ->
                    when {
                        page > totalPages -> showToast(getString(R.string.no_page))
                        else -> {
                            mPdfView.jumpTo(page - 1)
                            dismiss()
                        }
                    }
                } ?: showToast(getString(R.string.no_page))
            }

            findViewById<Button>(R.id.btnCancel).setOnClickListener {
                hideKeyboard()
                dismiss()
            }
        }.show()
    }

    private fun showInfoDialog() {
        lifecycleScope.launch {
            val file = mPdfFile?.let { PdfUtils.fileFromAsset(this@PdfActivity, it) }
            mPdfView.documentMeta()?.let { meta ->
                Dialog(this@PdfActivity).apply {
                    setContentView(R.layout.dialog_pdf_info)
                    window?.setLayout(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                    )

                    findViewById<TextView>(R.id.dialogTvTitle).text = meta.title
                    findViewById<TextView>(R.id.dialogTvAuthor).text = meta.author
                    findViewById<TextView>(R.id.dialogTvTotalPage).text = meta.totalPages.toString()
                    findViewById<TextView>(R.id.dialogTvSubject).text = meta.subject
                    findViewById<TextView>(R.id.dialogTvKeywords).text = meta.keywords
                    findViewById<TextView>(R.id.dialogTvCreationDate).text = meta.creationDate
                    findViewById<TextView>(R.id.dialogTvModifyDate).text = meta.modDate
                    findViewById<TextView>(R.id.dialogTvCreator).text = meta.creator
                    findViewById<TextView>(R.id.dialogTvProducer).text = meta.producer
                    findViewById<TextView>(R.id.dialogTvFileSize).text = file?.let {
                        formatFileSize(it)
                    }
                    findViewById<TextView>(R.id.dialogTvFilePath).text = file?.path

                    findViewById<Button>(R.id.btnOk).setOnClickListener { dismiss() }
                }.show()
            }
        }
    }


    private fun showToast(msg: String) {
        Toast.makeText(this@PdfActivity, msg, Toast.LENGTH_LONG).show()
    }

    private fun enableMenuItems(isEnabled: Boolean) {
        mBinding.toolbar.menu.apply {
            findItem(R.id.menuInfo).isEnabled = isEnabled
            findItem(R.id.menuJumpTo).isEnabled = isEnabled
            findItem(R.id.menuSwitchView).isEnabled = isEnabled
            findItem(R.id.menuNightMode).isEnabled = isEnabled
        }
    }

    private fun showKeyboard(view: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun finishWithTransition() {
        finish()
        overridePendingTransition(R.anim.left_to_right, R.anim.right_to_left)
    }

    fun formatFileSize(file: File): String {
        val units = listOf("B", "KB", "MB", "GB")
        var size = file.length().toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.lastIndex) {
            size /= 1024
            unitIndex++
        }
        return "%.2f %s".format(size, units[unitIndex])
    }

    public override fun onResume() {
        super.onResume()
    }

    public override fun onPause() {
        super.onPause()
    }

    public override fun onDestroy() {
        super.onDestroy()
    }

    private fun init() {
        try {
            if (Constants.PDF_FILE_MAIN == intent.getStringExtra(Constants.PDF_FILE)) {
                mPdfFile = "grammar.pdf"
                mPassword = "5632"
            } else if (Constants.PDF_FILE_1 == intent.getStringExtra(Constants.PDF_FILE)) {
                mPdfFile = "example.pdf"
            } else if (Constants.PDF_FILE_2 == intent.getStringExtra(Constants.PDF_FILE)) {
                mPdfFile = "example1.pdf"
            } else if (Constants.PDF_FILE_3 == intent.getStringExtra(Constants.PDF_FILE)) {
                mPdfFile = "example3.pdf"
            } else if (Constants.PDF_FILE_PROTECTED == intent.getStringExtra(Constants.PDF_FILE)) {
                mPdfFile = "grammar.pdf"
            }
            mPdfFile?.let { displayFromAsset(mPdfView, it) }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.v(Constants.TAG, "Calling Intent or getIntent won't work due to ${e.message}")
        }
    }
}