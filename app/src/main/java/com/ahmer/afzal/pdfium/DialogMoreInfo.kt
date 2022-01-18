package com.ahmer.afzal.pdfium

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import com.ahmer.afzal.pdfium.databinding.DialogPdfInfoBinding
import com.ahmer.pdfium.Meta
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfviewer.PDFView
import io.ahmer.utils.utilcode.FileUtils
import java.io.File
import java.util.*

class DialogMoreInfo : Dialog {
    private val file: File
    private var meta: Meta? = null

    constructor(context: Context, file: File) : super(context) {
        this.file = file
        try {
            val pdfiumCore = PdfiumCore(context, file)
            meta = pdfiumCore.metaData
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    constructor(context: Context, file: File, password: String?) : super(context) {
        this.file = file
        try {
            val pdfiumCore = PdfiumCore(context, file, password)
            meta = pdfiumCore.metaData
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    constructor(context: Context, pdfView: PDFView, file: File) : super(context) {
        this.file = file
        meta = pdfView.getDocumentMeta()
    }

    override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        val binding: DialogPdfInfoBinding = DialogPdfInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.text = meta!!.title
        binding.tvAuthor.text = meta!!.author
        binding.tvTotalPage.text = String.format(Locale.getDefault(), "%d", meta!!.totalPages)
        binding.tvSubject.text = meta!!.subject
        binding.tvKeywords.text = meta!!.keywords
        binding.tvCreationDate.text = meta!!.creationDate
        binding.tvModifyDate.text = meta!!.modDate
        binding.tvCreator.text = meta!!.creator
        binding.tvProducer.text = meta!!.producer
        binding.tvFileSize.text = FileUtils.getSize(file)
        binding.tvFilePath.text = file.path
        binding.tvOk.setOnClickListener { v -> dismiss() }
    }
}