package com.journal.cxrcore.app

import android.app.Application
import com.rokid.cxr.link.CXRLink

/**
 * Global Application class for the Journal CXR project.
 * Holds the shared [CXRLink] singleton, mirroring Sample's [CXRLApplication].
 *
 * The phone-side app must register this in its AndroidManifest.xml.
 */
class JournalApplication : Application() {

    companion object {
        lateinit var instance: JournalApplication
    }

    var sharedLink: CXRLink? = null
    var isSessionReady: Boolean = false

    fun requireReadyLink(): CXRLink {
        check(sharedLink != null && isSessionReady) {
            "CXRLink session not ready: complete configCXRSession and connect first"
        }
        return sharedLink!!
    }

    fun resetSession() {
        sharedLink = null
        isSessionReady = false
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
