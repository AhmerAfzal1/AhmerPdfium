package com.ahmer.afzal.pdfium

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.ahmer.afzal.pdfium.databinding.ActivityPdfBinding
import com.ahmer.pdfium.Bookmark
import com.ahmer.pdfium.Meta
import com.ahmer.pdfium.PdfPasswordException
import com.ahmer.pdfviewer.link.DefaultLinkHandler
import com.ahmer.pdfviewer.listener.*
import com.ahmer.pdfviewer.scroll.DefaultScrollHandle
import com.ahmer.pdfviewer.util.FitPolicy
import com.ahmer.pdfviewer.util.PdfFileUtils.fileFromAsset
import io.ahmer.utils.utilcode.*
import java.util.*

class PdfActivity : AppCompatActivity(), OnPageChangeListener, OnLoadCompleteListener {

    val TAG = "AhmerPDF"
    private var password: String? = null
    private var isNightMode = false
    private var pdfFile: String? = null
    private var totalPages = 0
    private var prefPage: SPUtils? = null
    private var prefSwab: SPUtils? = null
    private var binding: ActivityPdfBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        prefPage = SPUtils.getInstance("page")
        prefSwab = SPUtils.getInstance("swab")
        binding!!.toolbar.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.left_to_right, R.anim.right_to_left)
        }
        binding!!.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menuPdfInfo -> {
                    showMoreInfoDialog()
                }
                R.id.menuPdfJumpTo -> {
                    showJumpToDialog()
                }
                R.id.menuPdfSwitchView -> {
                    if (!isHorizontal) {
                        isHorizontal = true
                        //prefSwab.put("rememberSwipe", true)
                        binding!!.toolbar.menu.findItem(R.id.menuPdfSwitchView)
                            .setIcon(R.drawable.ic_menu_pdf_vertical)
                    } else {
                        isHorizontal = false
                        //prefSwab.put("rememberSwipe", false)
                        binding!!.toolbar.menu.findItem(R.id.menuPdfSwitchView)
                            .setIcon(R.drawable.ic_menu_pdf_horizontal)
                    }
                    displayFromAsset()
                }
                R.id.menuPdfNightMode -> {
                    if (!isNightMode) {
                        isNightMode = true
                        binding!!.toolbar.menu.findItem(R.id.menuPdfNightMode)
                            .setIcon(R.drawable.ic_menu_pdf_sun)
                    } else {
                        isNightMode = false
                        binding!!.toolbar.menu.findItem(R.id.menuPdfNightMode)
                            .setIcon(R.drawable.ic_menu_pdf_moon)
                    }
                    displayFromAsset()
                }
                R.id.menuPdfSearch -> {
                    /*if (binding!!.layoutSearch.visibility != View.VISIBLE) {
                        binding!!.layoutSearch.visibility = View.VISIBLE
                    } else {
                        binding!!.etSearch.setText("")
                        binding!!.layoutSearch.visibility = View.GONE
                    }*/
                    ToastUtils.showLong(getString(R.string.under_progress))
                }
            }
            false
        }
        binding!!.ivCancelSearch.setOnClickListener {
            binding!!.etSearch.setText("")
            binding!!.layoutSearch.visibility = View.GONE
        }
        if (!isHorizontal) {
            binding!!.toolbar.menu.findItem(R.id.menuPdfSwitchView)
                .setIcon(R.drawable.ic_menu_pdf_horizontal)
        } else {
            binding!!.toolbar.menu.findItem(R.id.menuPdfSwitchView)
                .setIcon(R.drawable.ic_menu_pdf_vertical)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            binding!!.toolbar.menu.findItem(R.id.menuPdfInfo).icon.setTint(Color.WHITE)
            binding!!.toolbar.menu.findItem(R.id.menuPdfJumpTo).icon.setTint(Color.WHITE)
            binding!!.toolbar.menu.findItem(R.id.menuPdfSwitchView).icon.setTint(Color.WHITE)
            binding!!.toolbar.menu.findItem(R.id.menuPdfNightMode).icon.setTint(Color.WHITE)
        }
        init()
    }

    private fun init() {
        try {
            if (intent.hasExtra("pdfNormal")) {
                pdfFile = "grammar.pdf"
                password = "5632"
            } else if (intent.hasExtra("pdfProtected")) {
                pdfFile = "grammar.pdf"
                password = null
            }
            displayFromAsset()
        } catch (e: Exception) {
            ThrowableUtils.getFullStackTrace(e)
            Log.v(TAG, "Calling Intent or getIntent won't work in $javaClass Activity!")
        }
    }

    private fun displayFromAsset() {
        binding?.toolbar?.menu?.findItem(R.id.menuPdfInfo)?.isEnabled = false
        binding?.toolbar?.menu?.findItem(R.id.menuPdfJumpTo)?.isEnabled = false
        binding?.toolbar?.menu?.findItem(R.id.menuPdfSwitchView)?.isEnabled = false
        binding?.toolbar?.menu?.findItem(R.id.menuPdfNightMode)?.isEnabled = false
        binding?.pdfView?.setBackgroundColor(Color.LTGRAY)
        binding!!.pdfView.fromAsset(pdfFile)
            .defaultPage(prefPage!!.getInt(pdfFile!!))
            .onLoad(this)
            .onPageChange(this)
            .onPageScroll(object : OnPageScrollListener {
                override fun onPageScrolled(page: Int, positionOffset: Float) {
                    Log.v(TAG, "onPageScrolled: Page $page PositionOffset: $positionOffset")
                }
            })
            .onError(object : OnErrorListener {
                override fun onError(t: Throwable?) {
                    if (t is PdfPasswordException) {
                        showPasswordDialog()
                    } else {
                        ToastUtils.showLong(resources.getString(R.string.error_loading_pdf))
                        t?.printStackTrace()
                        Log.v(TAG, " onError: $t")
                    }
                }
            })
            .onPageError(object : OnPageErrorListener {
                override fun onPageError(page: Int, t: Throwable?) {
                    t?.printStackTrace()
                    ToastUtils.showLong("onPageError")
                    Log.v(TAG, "onPageError: $t on page: $page")
                }
            })
            .onRender(object : OnRenderListener {
                override fun onInitiallyRendered(nbPages: Int) {
                    binding!!.pdfView.fitToWidth(prefPage!!.getInt(pdfFile!!))
                }
            })
            .onTap(object : OnTapListener {
                override fun onTap(e: MotionEvent?): Boolean {
                    return true
                }
            })
            .fitEachPage(true)
            .nightMode(isNightMode)
            .enableSwipe(true)
            .swipeHorizontal(prefSwab!!.getBoolean("rememberSwipe"))
            .pageSnap(true) // snap pages to screen boundaries
            .autoSpacing(true) // add dynamic spacing to fit each page on its own on the screen
            .pageFling(false) // make a fling change only a single page like ViewPager
            .enableDoubletap(true)
            .enableAnnotationRendering(true)
            .password(password)
            .textHighlightColor(Color.RED)
            .scrollHandle(DefaultScrollHandle(applicationContext))
            .enableAntialiasing(true)
            .spacing(5)
            .linkHandler(DefaultLinkHandler(binding!!.pdfView))
            .pageFitPolicy(FitPolicy.BOTH)
            .load()
        binding!!.pdfView.useBestQuality(true)
        binding!!.pdfView.minZoom = 1.0f
        binding!!.pdfView.midZoom = 2.5f
        binding!!.pdfView.maxZoom = 4.0f
    }

    private fun showPasswordDialog() {
        val dialog = Dialog(this)
        try {
            dialog.window!!.requestFeature(Window.FEATURE_NO_TITLE)
            dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.setContentView(R.layout.dialog_pdf_password)
            dialog.window!!.setLayout(-1, -2)
            dialog.window!!.setLayout(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            val inputPass = dialog.findViewById<EditText>(R.id.inputPassword)
            inputPass.requestFocus()
            inputPass.postDelayed({
                val imm = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
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
                        password = inputPass.text.toString()
                        displayFromAsset()
                        dialog.dismiss()
                    }
                }
            }
            val cancel = dialog.findViewById<TextView>(R.id.btnCancel)
            cancel.setOnClickListener {
                val imm = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                dialog.dismiss()
                imm.hideSoftInputFromInputMethod(inputPass.windowToken, 0)
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

    private fun showJumpToDialog() {
        val dialog = Dialog(this)
        try {
            dialog.window!!.requestFeature(Window.FEATURE_NO_TITLE)
            dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.setContentView(R.layout.dialog_pdf_jumpto)
            dialog.window!!.setLayout(-1, -2)
            dialog.window!!.setLayout(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            val inputPageNo = dialog.findViewById<EditText>(R.id.inputPageNumber)
            inputPageNo.requestFocus()
            inputPageNo.postDelayed({
                val imm = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                imm.showSoftInput(inputPageNo, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
            val goTo = dialog.findViewById<Button>(R.id.btnGoTo)
            goTo.isClickable = false
            goTo.setOnClickListener {
                val pageNumber: String = inputPageNo.text.toString()
                if (pageNumber == "") {
                    ToastUtils.showShort(getString(R.string.please_enter_number))
                } else {
                    //requestHideSelf(InputMethodManager.HIDE_NOT_ALWAYS)
                    val imm =
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    imm.hideSoftInputFromInputMethod(inputPageNo.windowToken, 0)
                    val number = pageNumber.toInt()
                    if (number > totalPages) {
                        ToastUtils.showShort(getString(R.string.no_page))
                    } else {
                        binding!!.pdfView.jumpTo(number - 1, true)
                    }
                }
                dialog.dismiss()
            }
            val cancel = dialog.findViewById<Button>(R.id.btnCancel)
            cancel.setOnClickListener {
                val imm = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                imm.hideSoftInputFromInputMethod(inputPageNo.windowToken, 0)
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

    private fun showMoreInfoDialog() {
        val dialog = Dialog(this)
        try {
            dialog.window!!.requestFeature(Window.FEATURE_NO_TITLE)
            dialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.setContentView(R.layout.dialog_pdf_info)
            dialog.window!!.setLayout(-1, -2)
            dialog.window!!.setLayout(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
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
            val meta: Meta? = binding!!.pdfView.getDocumentMeta()
            tvTitle.text = meta!!.title
            tvAuthor.text = meta.author
            tvTotalPage.text = String.format(Locale.getDefault(), "%d", meta.totalPages)
            tvSubject.text = meta.subject
            tvKeywords.text = meta.keywords
            tvCreationDate.text = meta.creationDate
            tvModifyDate.text = meta.modDate
            tvCreator.text = meta.creator
            tvProducer.text = meta.producer
            val file = fileFromAsset(this@PdfActivity, pdfFile!!)
            tvFileSize.text = FileUtils.getSize(file)
            tvFilePath.text = file.path
            tvOk.setOnClickListener {
                dialog.dismiss()
            }
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        prefPage!!.put(pdfFile!!, page)
        binding?.toolbar?.title =
            String.format(Locale.getDefault(), "%s %s of %s", "Page", page + 1, pageCount)
    }

    override fun loadComplete(nbPages: Int) {
        printBookmarksTree(binding?.pdfView!!.getTableOfContents(), "-")
        binding?.progressBarPdfView?.visibility = View.GONE
        totalPages = nbPages
        binding?.toolbar?.menu?.findItem(R.id.menuPdfInfo)?.isEnabled = true
        binding?.toolbar?.menu?.findItem(R.id.menuPdfJumpTo)?.isEnabled = true
        binding?.toolbar?.menu?.findItem(R.id.menuPdfSwitchView)?.isEnabled = true
        binding?.toolbar?.menu?.findItem(R.id.menuPdfNightMode)?.isEnabled = true
    }

    private fun printBookmarksTree(tree: List<Bookmark>, sep: String) {
        for (bookmark in tree) {
            Log.v(
                TAG, String.format(
                    Locale.getDefault(), "%s %s, Page %d", sep,
                    bookmark.title, bookmark.pageIdx
                )
            )
            if (bookmark.hasChildren()) {
                printBookmarksTree(bookmark.children, "$sep-")
            }
        }
    }

    public override fun onResume() {
        super.onResume()
    }

    public override fun onPause() {
        super.onPause()
        binding?.etSearch?.setText("")
        binding?.layoutSearch?.visibility = View.GONE
    }

    public override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private var isHorizontal = false
    }
}