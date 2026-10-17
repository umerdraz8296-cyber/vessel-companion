package com.companion.hollowai
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class CompanionAccessibilityService : AccessibilityService() {
    companion object {
        var isRunning = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val text = event.text.joinToString(" ").trim()
            if (text.length > 3) {
                FloatingCompanionService.onTextReceived(text)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}