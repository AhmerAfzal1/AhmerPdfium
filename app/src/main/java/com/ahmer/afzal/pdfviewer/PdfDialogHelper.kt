package com.ahmer.afzal.pdfviewer

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfviewer.PDFView
import java.io.File
import java.util.Locale

object PdfDialogHelper {

    fun createPasswordDialog(
        context: Context,
        onPasswordEntered: (String) -> Unit,
        onDismiss: () -> Unit
    ): Dialog {
        return Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setContentView(R.layout.dialog_pdf_password)
            window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            val inputPass: EditText = findViewById(R.id.inputPassword)
            val btnOpen: Button = findViewById(R.id.btnOpen)
            val btnCancel: Button = findViewById(R.id.btnCancel)

            inputPass.requestFocus()
            inputPass.postDelayed({
                showKeyboard(context = context, view = inputPass)
            }, 100)

            btnOpen.isEnabled = false
            btnOpen.setOnClickListener {
                onPasswordEntered(inputPass.text.toString())
                dismiss()
            }

            btnCancel.setOnClickListener {
                hideKeyboard(context = context, view = inputPass)
                onDismiss()
                dismiss()
            }

            inputPass.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {
                    btnOpen.isEnabled = s.isNotEmpty() && s.toString().isNotBlank()
                }
            })
        }
    }

    fun createJumpToDialog(
        context: Context,
        pageCount: Int,
        onPageSelected: (Int) -> Unit
    ): Dialog {
        return Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setContentView(R.layout.dialog_pdf_jumpto)
            window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            val inputPage: EditText = findViewById(R.id.inputPageNumber)
            val btnGoTo: Button = findViewById(R.id.btnGoTo)
            val btnCancel: Button = findViewById(R.id.btnCancel)

            inputPage.requestFocus()
            inputPage.postDelayed({
                showKeyboard(context = context, view = inputPage)
            }, 100)

            btnGoTo.isEnabled = false
            btnGoTo.setOnClickListener {
                inputPage.text.toString().toIntOrNull()?.let { page ->
                    if (page in 1..pageCount) {
                        onPageSelected(page)
                        dismiss()
                    }
                }
            }

            btnCancel.setOnClickListener {
                hideKeyboard(context = context, view = inputPage)
                dismiss()
            }

            inputPage.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {
                    btnGoTo.isEnabled = s.isNotEmpty()
                }
            })
        }
    }

    fun createInfoDialog(
        pdfView: PDFView,
        meta: PdfDocument.Meta?,
        file: File?
    ): Dialog {
        return Dialog(pdfView.context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setContentView(R.layout.dialog_pdf_info)
            window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)

            meta?.let {
                findViewById<TextView>(R.id.dialogTvTitle).text = it.title
                findViewById<TextView>(R.id.dialogTvAuthor).text = it.author
                findViewById<TextView>(R.id.dialogTvTotalPage).text = pdfView.pagesCount.toString()
                findViewById<TextView>(R.id.dialogTvSubject).text = it.subject
                findViewById<TextView>(R.id.dialogTvKeywords).text = it.keywords
                findViewById<TextView>(R.id.dialogTvCreationDate).text = it.creationDate
                findViewById<TextView>(R.id.dialogTvModifyDate).text = it.modDate
                findViewById<TextView>(R.id.dialogTvCreator).text = it.creator
                findViewById<TextView>(R.id.dialogTvProducer).text = it.producer
            }

            file?.let {
                findViewById<TextView>(R.id.dialogTvFileSize).text = formatFileSize(file = it)
                findViewById<TextView>(R.id.dialogTvFilePath).text = it.path
            }

            findViewById<Button>(R.id.btnOk).setOnClickListener { dismiss() }
        }
    }

    private fun showKeyboard(context: Context, view: EditText) {
        val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java)
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(context: Context, view: EditText) {
        val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun formatFileSize(file: File): String {
        val units: List<String> = listOf("B", "KB", "MB", "GB")
        var size: Double = file.length().toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.lastIndex) {
            size /= 1024
            unitIndex++
        }
        return "%.2f %s".format(locale = Locale.getDefault(), size, units[unitIndex])
    }
}