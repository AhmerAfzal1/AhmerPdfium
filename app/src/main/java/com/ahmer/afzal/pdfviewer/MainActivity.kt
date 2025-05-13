package com.ahmer.afzal.pdfviewer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import com.ahmer.afzal.pdfviewer.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.fragmentContainer.isVisible) {
                    binding.toolbar.visibility = View.VISIBLE
                    binding.buttonContainer.visibility = View.VISIBLE
                    binding.fragmentContainer.visibility = View.GONE
                    supportFragmentManager.popBackStack()
                } else {
                    finish()
                }
            }
        })
        setupToolbar()
        setupClickListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.title = getString(R.string.app_name)
        binding.toolbar.setNavigationOnClickListener {
            finishWithTransition()
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            // Fragment examples
            pdfNormalFragment.setOnClickListener {
                showPdfFragment(PdfFragment.TYPE_NORMAL)
            }

            pdfProtectedFragment.setOnClickListener {
                showPdfFragment(PdfFragment.TYPE_PROTECTED)
            }

            // Activity examples
            pdfNormalActivity.setOnClickListener {
                launchPdfActivity(Constants.PDF_FILE_MAIN)
            }

            pdfProtectedActivity.setOnClickListener {
                launchPdfActivity(Constants.PDF_FILE_PROTECTED)
            }

            // Other PDF examples
            pdfFile1.setOnClickListener { launchPdfActivity(Constants.PDF_FILE_1) }
            pdfFile2.setOnClickListener { launchPdfActivity(Constants.PDF_FILE_2) }
            pdfFile3.setOnClickListener { launchPdfActivity(Constants.PDF_FILE_3) }
            pdfFile4.setOnClickListener { launchTestPdfiumActivity() }
        }
    }

    private fun showPdfFragment(pdfType: Int) {
        binding.toolbar.visibility = View.GONE
        binding.buttonContainer.visibility = View.GONE
        binding.fragmentContainer.visibility = View.VISIBLE

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragment_container, PdfFragment.newInstance(pdfType))
            addToBackStack("pdf_fragment")
        }
    }

    private fun launchPdfActivity(pdfFileConstant: String) {
        val intent = Intent(this, PdfActivity::class.java).apply {
            putExtra(Constants.PDF_FILE, pdfFileConstant)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        startActivity(intent)
    }

    private fun launchTestPdfiumActivity() {
        val intent = Intent(this, TestPdfium::class.java).apply {
            putExtra(Constants.PDF_FILE, Constants.PDF_FILE_MAIN)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun finishWithTransition() {
        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.left_to_right,
                R.anim.right_to_left
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.left_to_right, R.anim.right_to_left)
        }
    }
}