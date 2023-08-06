package com.ahmer.afzal.pdfviewer

import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import dagger.hilt.android.HiltAndroidApp
import io.ahmer.utils.utilcode.Utils

@HiltAndroidApp
class App : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        MultiDex.install(this)
        Utils.init(this)
    }
}