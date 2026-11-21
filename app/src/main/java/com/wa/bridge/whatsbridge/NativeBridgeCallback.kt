package com.wa.bridge.whatsbridge

/**
 * Callback interface invoked by `libwhatsbridge_rs.so` on a dedicated JVM-attached pump thread
 * whenever `whatsapp-rust` emits a JSON bridge event.
 */
fun interface NativeBridgeCallback {
    fun onJsonEvent(eventType: String, payloadJson: String)
}
