package com.journal.cxrcore.link

/**
 * Link state machine: Idle → Connecting → LinkReady → SessionBuilt → Disconnected.
 *
 * - Idle: no CXRLink created yet.
 * - Connecting: configCXRSession + connect() called, waiting for callbacks.
 * - LinkReady: onCXRLConnected(true) && onGlassBtConnected(true).
 * - SessionBuilt: glass app started / customView opened → capabilities ready.
 * - Disconnected: onCXRLConnected(false) || onGlassBtConnected(false).
 */
enum class LinkState {
    Idle,
    Connecting,
    LinkReady,
    SessionBuilt,
    Disconnected,
}

/**
 * Session phase mirrors [CxrScenePhase] from Sample.
 */
enum class SessionPhase {
    Connecting,
    SceneNotReady,
    CapabilitiesReady,
}
