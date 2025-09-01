package com.sevenk.launcher

import android.app.Application
import com.sevenk.launcher.iconpack.IconPackHelper
import com.sevenk.launcher.iconpack.IconPackManager

class SevenKApplication : Application() {

    lateinit var iconPackHelper: IconPackHelper
    lateinit var iconPackManager: IconPackManager

    override fun onCreate() {
        super.onCreate()

        // Initialize IconPackHelper
        iconPackHelper = IconPackHelper(this)

        // Initialize IconPackManager
        iconPackManager = IconPackManager(this)
    }
}
