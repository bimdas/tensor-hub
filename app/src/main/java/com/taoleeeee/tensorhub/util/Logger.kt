package com.taoleeeee.tensorhub.util

import android.util.Log

/**
 * Structured logger with tag filtering.
 */
object Logger {
    private const val TAG = "TensorHub"

    fun d(subtag: String, message: String) = Log.d("$TAG/$subtag", message)
    fun i(subtag: String, message: String) = Log.i("$TAG/$subtag", message)
    fun w(subtag: String, message: String) = Log.w("$TAG/$subtag", message)
    fun e(subtag: String, message: String, throwable: Throwable? = null) =
        Log.e("$TAG/$subtag", message, throwable)
}
