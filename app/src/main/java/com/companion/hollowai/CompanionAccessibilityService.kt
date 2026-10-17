package com.companion.hollowai
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class CompanionAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val textList = event.text
            if (textList.isNotEmpty()) {
                val fullText = textList.joinToString(" ")
                if (fullText.trim().length > 4) {
                    val intent = Intent("ACTION_INPUT_TEXT_CHANGED").apply {
                        putExtra("typed_text", fullText)
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                }
            }
        }
    }
    override fun onInterrupt() {}
}