package com.ahmer.afzal.pdfium

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ahmer.afzal.pdfium.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    val TAG = "AhmerPDF"
    private val SAMPLE_FILE = "grammar.pdf"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.title = getString(R.string.app_name)
        binding.toolbar.setOnClickListener { v ->
            finish()
            overridePendingTransition(R.anim.left_to_right, R.anim.right_to_left)
        }
        binding.pdfNormal.setOnClickListener { v ->
            val intent = Intent(v.context, PdfActivity::class.java)
            intent.putExtra("pdfNormal", SAMPLE_FILE)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
        binding.pdfProtected.setOnClickListener { v ->
            val intent = Intent(v.context, PdfActivity::class.java)
            intent.putExtra("pdfProtected", SAMPLE_FILE)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }
}