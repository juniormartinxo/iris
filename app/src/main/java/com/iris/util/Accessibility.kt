package com.iris.util

import android.content.Context
import android.view.accessibility.AccessibilityManager

fun isTalkBackOn(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        ?: return false
    return manager.isEnabled && manager.isTouchExplorationEnabled
}
