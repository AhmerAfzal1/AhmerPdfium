package com.ahmer.afzal.pdfium

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import com.ahmer.afzal.pdfium.databinding.DialogPdfJumptoBinding
import io.ahmer.utils.utilcode.ToastUtils
import java.util.*

class DialogJumpTo(context: Context, private var listener: JumpListener) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        val binding: DialogPdfJumptoBinding = DialogPdfJumptoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.inputPageNumber.postDelayed({
            binding.inputPageNumber.requestFocus()
            binding.inputPageNumber.isCursorVisible = true
            val imm = (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            imm.showSoftInput(binding.inputPageNumber, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
        binding.tvGoTo.setOnClickListener { v ->
            val pageNumber: String =
                Objects.requireNonNull(binding.inputPageNumber.text).toString()
            if (pageNumber == "") {
                ToastUtils.showShort(context.getString(R.string.please_enter_number))
            } else {
                //requestHideSelf(InputMethodManager.HIDE_NOT_ALWAYS)
                val imm =
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                dismiss()
                val number = pageNumber.toInt()
                listener.onJump(number)
            }
        }
        binding.tvCancel.setOnClickListener { v -> dismiss() }
    }

    interface JumpListener {
        fun onJump(numPage: Int)
    }
}