package com.iris.util

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("iris_prefs", Context.MODE_PRIVATE)

    var tutorialDone: Boolean
        get() = sp.getBoolean(KEY_TUTORIAL, false)
        set(value) { sp.edit().putBoolean(KEY_TUTORIAL, value).apply() }

    var preflightDone: Boolean
        get() = sp.getBoolean(KEY_PREFLIGHT, false)
        set(value) { sp.edit().putBoolean(KEY_PREFLIGHT, value).apply() }

    companion object {
        private const val KEY_TUTORIAL = "tutorialDone"
        private const val KEY_PREFLIGHT = "preflightDone"
    }
}
