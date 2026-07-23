package com.journal.cxrcore.link

import android.content.Context
import android.util.Log
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.utils.CxrDefs
import com.journal.cxrcore.app.JournalApplication

/**
 * Creates a CXRLink session: config → register global link callback → connect.
 *
 * Lifecycle conventions (same as Sample's [CxrSessionGate]):
 * - [JournalApplication.sharedLink] is created and owned here.
 * - Sub-pages (Audio/Photo/Cmd) must NOT call disconnect().
 * - Sub-pages call their own release() to stop streams/clear callbacks.
 * - Session close (customViewClose / appStop) only when Activity.isFinishing.
 */
object LinkSessionGate {

    private const val TAG = "LinkSessionGate"

    /**
     * Creates a CUSTOMAPP session with the given target package name.
     * Returns the CXRLink if successful, null otherwise.
     */
    fun createCustomAppSession(context: Context, token: String, glassesPackageName: String): CXRLink? {
        Log.i(
            TAG,
            "createCustomAppSession: packageName=$glassesPackageName, tokenLen=${token.length}"
        )
        return createSession(
            context = context,
            session = CxrDefs.CXRSession(
                CxrDefs.CXRSessionType.CUSTOMAPP,
                glassesPackageName
            )
        )?.also { link ->
            Log.i(TAG, "createCustomAppSession: connect()")
            link.connect(token)
        }
    }

    private fun createSession(context: Context, session: CxrDefs.CXRSession): CXRLink? {
        val app = context.applicationContext as? JournalApplication
        if (app == null) {
            Log.e(TAG, "createSession: JournalApplication not found")
            return null
        }
        app.resetSession()
        LinkConnectionHub.reset()

        val link = CXRLink(context).apply {
            configCXRSession(session)
            setCXRLinkCbk(LinkConnectionHub.linkCallback)
        }
        app.sharedLink = link
        Log.i(
            TAG,
            "createSession: type=${session.sessionType.name}, packageName=${session.customAppPackageName}, link=$link"
        )
        return link
    }
}
