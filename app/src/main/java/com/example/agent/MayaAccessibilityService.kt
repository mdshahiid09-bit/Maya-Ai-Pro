package com.example.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

data class ScreenNodeInfo(
    val id: String,
    val text: String,
    val description: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val bounds: String,
    val centerX: Int,
    val centerY: Int
)

class MayaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MayaAccessibility"
        @Volatile
        var instance: MayaAccessibilityService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Maya Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events monitored for UI state changes
    }

    override fun onInterrupt() {
        Log.w(TAG, "Maya Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "Maya Accessibility Service Destroyed")
    }

    fun getCompactScreenNodes(): List<ScreenNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<ScreenNodeInfo>()
        traverseNode(root, result)
        return result
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, list: MutableList<ScreenNodeInfo>) {
        if (node == null || !node.isVisibleToUser) return

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val clickable = node.isClickable
        val editable = node.isEditable

        val rect = Rect()
        node.getBoundsInScreen(rect)

        if ((text.isNotEmpty() || desc.isNotEmpty() || clickable || editable) && rect.width() > 0 && rect.height() > 0) {
            list.add(
                ScreenNodeInfo(
                    id = viewId,
                    text = text,
                    description = desc,
                    isClickable = clickable,
                    isEditable = editable,
                    bounds = "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]",
                    centerX = rect.centerX(),
                    centerY = rect.centerY()
                )
            )
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNode(child, list)
                child.recycle()
            }
        }
    }

    suspend fun clickCoordinates(x: Int, y: Int): Boolean {
        val gestureBuilder = GestureDescription.Builder()
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        gestureBuilder.addStroke(stroke)

        val deferred = CompletableDeferred<Boolean>()
        val dispatched = dispatchGesture(
            gestureBuilder.build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    deferred.complete(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    deferred.complete(false)
                }
            },
            null
        )

        if (!dispatched) return false
        return withTimeoutOrNull(2000L) { deferred.await() } ?: false
    }

    fun clickNodeByTextOrId(targetText: String?, viewId: String?): Boolean {
        val root = rootInActiveWindow ?: return false

        if (!targetText.isNullOrEmpty()) {
            val nodes = root.findAccessibilityNodeInfosByText(targetText)
            for (node in nodes) {
                if (node.isVisibleToUser) {
                    var curr: AccessibilityNodeInfo? = node
                    while (curr != null) {
                        if (curr.isClickable) {
                            val success = curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            if (success) return true
                        }
                        curr = curr.parent
                    }
                    // Try coordinates tap if action click failed
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        dispatchSimpleTap(rect.centerX(), rect.centerY())
                        return true
                    }
                }
            }
        }

        if (!viewId.isNullOrEmpty()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            for (node in nodes) {
                if (node.isVisibleToUser) {
                    if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun dispatchSimpleTap(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun enterText(text: String, viewId: String?): Boolean {
        val root = rootInActiveWindow ?: return false

        var targetNode: AccessibilityNodeInfo? = null
        if (!viewId.isNullOrEmpty()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            targetNode = nodes.firstOrNull { it.isEditable && it.isVisibleToUser }
        }

        if (targetNode == null) {
            targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }

        if (targetNode == null) {
            // Find first editable node
            val list = mutableListOf<AccessibilityNodeInfo>()
            findEditableNodes(root, list)
            targetNode = list.firstOrNull()
        }

        if (targetNode != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        return false
    }

    private fun findEditableNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isEditable && node.isVisibleToUser) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            findEditableNodes(node.getChild(i), list)
        }
    }

    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = when (direction.lowercase()) {
            "down", "forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "up", "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        return root.performAction(action)
    }

    fun performSystemAction(action: String): Boolean {
        val globalAction = when (action.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "lock_screen" -> GLOBAL_ACTION_LOCK_SCREEN
            else -> GLOBAL_ACTION_BACK
        }
        return performGlobalAction(globalAction)
    }

    fun captureScreenshot(callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        callback(true)
                    }
                    override fun onFailure(errorCode: Int) {
                        callback(false)
                    }
                }
            )
        } else {
            callback(false)
        }
    }
}
