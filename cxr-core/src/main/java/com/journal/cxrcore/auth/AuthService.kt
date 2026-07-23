package com.journal.cxrcore.auth

import android.app.Activity
import android.content.Intent
import android.util.Pair
import com.rokid.sprite.aiapp.externalapp.auth.AuthResult
import com.rokid.sprite.aiapp.externalapp.auth.AuthorizationHelper
import com.rokid.sprite.aiapp.externalapp.auth.GlassPermission

/**
 * Thin wrapper around [AuthorizationHelper] for auth flow.
 *
 * Usage:
 * 1. Call [isRokidAppInstalled] to check companion app presence.
 * 2. Call [requestAuth] to launch the auth UI (may return immediate result).
 * 3. Call [parseAuthResult] in onActivityResult (or with the immediate result).
 */
object AuthService {

    private const val DEFAULT_REQUEST_CODE = 1001

    /**
     * Checks if the Rokid AI App (mainland China) is installed and meets minimum version.
     */
    fun isRokidAppInstalled(activity: Activity): Boolean =
        AuthorizationHelper.isRequiredRokidAppInstalled(activity)

    /**
     * Checks if the Hi Rokid App (international) is installed.
     */
    fun isHiRokidInstalled(activity: Activity): Boolean =
        AuthorizationHelper.isRequiredHiRokidInstalled(activity)

    /**
     * Returns true if the device connects to Hi Rokid services (international).
     */
    fun isConnectHiRokid(): Boolean = AuthorizationHelper.isConnectHiRokid()

    /**
     * Requests authorization with the given permissions.
     * May return a pair (resultCode, intent) immediately if user previously authorized,
     * or null if the auth UI was launched (result via onActivityResult).
     */
    fun requestAuth(
        activity: Activity,
        permissions: Array<GlassPermission> = arrayOf(
            GlassPermission.MICROPHONE,
            GlassPermission.CAMERA,
        ),
        requestCode: Int = DEFAULT_REQUEST_CODE,
    ): Pair<Int, Intent>? {
        return AuthorizationHelper.requestAuthorization(activity, permissions, requestCode)
    }

    /**
     * Parses the authorization result from onActivityResult or immediate return.
     * Returns the token on success, null on failure/cancel.
     */
    fun parseAuthResult(resultCode: Int, data: Intent?): AuthResult =
        AuthorizationHelper.parseAuthorizationResult(resultCode, data)

    /**
     * Checks if a specific glass permission has been granted.
     */
    fun hasGlassPermission(permission: GlassPermission): Boolean =
        AuthorizationHelper.hasGlassPermission(permission)
}
