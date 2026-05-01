package com.aitunes.app.engine

/** Callback SAM para JNI ([LlamaNativeBridge] → llama.cpp). */
fun interface NativeTokenCallback {
    fun onToken(text: String)
}
