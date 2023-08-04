package com.ahmer.afzal.pdfium

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ahmer.afzal.pdfium.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        mBinding.apply {
            toolbar.title = getString(R.string.app_name)
            toolbar.setOnClickListener {
                finish()
                overridePendingTransition(R.anim.left_to_right, R.anim.right_to_left)
            }
            val id: Int = android.R.id.content

            pdfNormalFragment.setOnClickListener {
                val bundle = Bundle()
                bundle.putBoolean(Constants.PDF_IS_NORMAL, true)
                val fragment = PdfFragment()
                fragment.arguments = bundle
                supportFragmentManager.beginTransaction().replace(id, fragment).commitNow()
            }

            pdfNormalActivity.setOnClickListener { v ->
                val intent = Intent(v.context, PdfActivity::class.java)
                intent.putExtra(Constants.PDF_IS_NORMAL, true)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }

            pdfProtectedFragment.setOnClickListener {
                val bundle = Bundle()
                bundle.putBoolean(Constants.PDF_IS_NORMAL, false)
                val fragment = PdfFragment()
                fragment.arguments = bundle
                supportFragmentManager.beginTransaction().replace(id, fragment).commitNow()
            }

            pdfProtectedActivity.setOnClickListener { v ->
                val intent = Intent(v.context, PdfActivity::class.java)
                intent.putExtra(Constants.PDF_IS_NORMAL, false)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
    }
}