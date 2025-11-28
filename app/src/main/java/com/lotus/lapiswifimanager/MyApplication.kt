package com.lotus.lapiswifimanager

import android.app.Application
import com.lotus.lapiswifimanager.helpers.FileLoggingTree
import com.squareup.leakcanary.core.BuildConfig
import leakcanary.LeakCanary
import timber.log.Timber

class MyApplication : Application() {

    private val myApplication: String
        get() = "MyApplication"

    companion object {
        lateinit var myApplication: MyApplication
            private set
    }


    override fun onCreate() {
        super.onCreate()
        try {
            MyApplication.myApplication = this

            setupTimber()

            if (BuildConfig.DEBUG) {
                LeakCanary.config = LeakCanary.config.copy(
                    dumpHeap = true,                        // heap dump alsın mı
                    retainedVisibleThreshold = 3,           // 3 ekran sonra hala tutuluyorsa bildir

                    useExperimentalLeakFinders = true       // Android 14+ ve Huawei gibi cihazlarda dikkat edilmesi gerekenler
                                                            // Bazı cihazlar background’da heap dump almayı engeller. LeakCanary 2.12+ bunu otomatik handle eder
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun setupTimber() {
        // Debug build'lerde Timber Debug tree'yi ekle
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Release build'lerde dosyaya loglama yap
        Timber.plant(FileLoggingTree(this))

        // Eski logları temizle (7 günden eski)
        (Timber.forest().find { it is FileLoggingTree } as? FileLoggingTree)?.clearOldLogs(7)
    }


}