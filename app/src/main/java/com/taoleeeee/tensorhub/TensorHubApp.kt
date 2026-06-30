package com.taoleeeee.tensorhub

import android.app.Application
import android.util.Log

class TensorHubApp : Application() {

    companion object {
        const val TAG = "TensorHub"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Tensor Hub starting")
    }
}
