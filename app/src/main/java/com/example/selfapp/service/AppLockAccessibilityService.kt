package com.example.selfapp.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.selfapp.data.dao.AppLockConfigDao
import com.example.selfapp.data.dao.PanicConfigDao
import com.example.selfapp.data.entity.PanicConfig
import com.example.selfapp.ui.lock.LockOverlayActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AppLockAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var appLockConfigDao: AppLockConfigDao

    @Inject
    lateinit var panicConfigDao: PanicConfigDao

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var blockedApps = mapOf<String, Long>()
    private var panicConfig: PanicConfig? = null

    // Track state to differentiate character typing vs. committed navigation submission
    private var activeAddressText: String? = null
    private var lastRoutedUrl: String? = null
    private var lastRoutedTimestamp: Long = 0L

    override fun onCreate() {
        super.onCreate()
        // Listen to database in real-time to keep active lock cache updated
        serviceScope.launch {
            appLockConfigDao.getAllLockConfigsFlow().collectLatest { configs ->
                blockedApps = configs.associate { it.packageName to it.lockedUntil }
            }
        }
        
        serviceScope.launch {
            panicConfigDao.getPanicConfigFlow().collectLatest { config ->
                panicConfig = config
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        val spinPkg = com.example.selfapp.util.PanicManager.detectSpinBrowserPackage(this)

        // Protect Spin Browser data from being deleted in Settings (Allowing Clear Cache)
        if (packageName.contains("settings", ignoreCase = true) || packageName.contains("securitycenter", ignoreCase = true)) {
            handleSettingsSpinDataProtection(event, spinPkg)
        }
        
        // Never process Spin Browser or SelfApp itself to prevent redirect loops
        if (packageName == spinPkg || packageName == this.packageName) return

        val eventType = event.eventType

        // 1. Capture address-bar text changes while typing (DO NOT redirect while typing)
        if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            val nodeText = extractNodeText(event.source)
            if (!nodeText.isNullOrEmpty()) {
                activeAddressText = nodeText
            }
        }

        // 2. Intercept committed address-bar navigation submission, click events, or window state changes
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            val committedUrl = extractCommittedUrl(event)
            if (committedUrl != null) {
                val now = System.currentTimeMillis()
                if (committedUrl != lastRoutedUrl || (now - lastRoutedTimestamp) > 2500L) {
                    lastRoutedUrl = committedUrl
                    lastRoutedTimestamp = now

                    performGlobalAction(GLOBAL_ACTION_HOME)
                    com.example.selfapp.util.PanicManager.routeUrlToSpin(this, committedUrl)
                    return
                }
            }
        }

        // 3. Check Panic Mode (highest priority)
        val pc = panicConfig
        if (pc != null && pc.isActive && System.currentTimeMillis() < pc.expiresAt) {
            val allowedPackages: List<String> = Gson().fromJson(pc.allowedPackagesJson, object : TypeToken<List<String>>() {}.type)
            if (!allowedPackages.contains(packageName)) {
                blockByPanic()
                return
            }
        }

        // 4. Check Standard App Lock
        val lockedUntil = blockedApps[packageName] ?: return
        if (System.currentTimeMillis() < lockedUntil) {
            blockApp(packageName, lockedUntil)
        }
    }

    private fun extractCommittedUrl(event: AccessibilityEvent): String? {
        val rootNode = rootInActiveWindow
        val rawInput = findAddressBarTextInNode(rootNode) ?: activeAddressText ?: extractEventTextUrl(event)
        return formatDestinationUrl(rawInput)
    }

    private fun formatDestinationUrl(rawInput: String?): String? {
        val text = rawInput?.trim() ?: return null
        if (text.isEmpty() || text.length < 2) return null

        // Ignore standard placeholder strings
        val placeholders = listOf(
            "search or type url", "search or type web address", "type a web address",
            "search web", "search or enter address", "type url", "about:blank"
        )
        if (placeholders.any { text.equals(it, ignoreCase = true) }) {
            return null
        }

        // 1. Preserve HTTP / HTTPS URLs (including query params, paths, and fragments)
        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            return text
        }

        // 2. Format Domain entries without scheme (e.g. youtube.com, reddit.com/r/android)
        val domainRegex = Regex("^[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+(/.*)?$")
        if (domainRegex.matches(text) && !text.contains(" ")) {
            return "https://$text"
        }

        // 3. Format Search Queries into search engine URL (e.g. "quantum computing")
        return try {
            "https://www.google.com/search?q=${URLEncoder.encode(text, "UTF-8")}"
        } catch (e: Exception) {
            "https://www.google.com/search?q=$text"
        }
    }

    private fun findAddressBarTextInNode(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null

        val className = node.className?.toString() ?: ""
        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

        if (className.contains("EditText", ignoreCase = true) || 
            viewId.contains("url") || viewId.contains("search") || viewId.contains("address") || viewId.contains("omnibox") || viewId.contains("location")) {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && text.length > 1) {
                return text
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findAddressBarTextInNode(child)
            if (result != null) return result
        }
        return null
    }

    private fun extractNodeText(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) return text
        return null
    }

    private fun extractEventTextUrl(event: AccessibilityEvent): String? {
        try {
            val text = event.text.joinToString(" ")
            val urlRegex = Regex("(https?://[\\w\\d:#@%/;\$()~_?\\+-=\\.\\&]+)")
            val match = urlRegex.find(text)
            if (match != null) return match.value
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun blockByPanic() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val intent = packageManager.getLaunchIntentForPackage(this.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        if (intent != null) {
            startActivity(intent)
        }
    }

    private fun blockApp(packageName: String, lockedUntil: Long) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_PACKAGE_NAME", packageName)
            putExtra("EXTRA_LOCKED_UNTIL", lockedUntil)
        }
        startActivity(intent)
    }

    private fun handleSettingsSpinDataProtection(event: AccessibilityEvent, spinPkg: String) {
        val rootNode = rootInActiveWindow ?: return

        if (!isSpinBrowserSettingsScreen(rootNode, spinPkg)) {
            return
        }

        if (isAttemptingToClearData(rootNode, event)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun isSpinBrowserSettingsScreen(node: AccessibilityNodeInfo?, spinPkg: String): Boolean {
        if (node == null) return false
        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

        if (text.contains("spin") || text.contains(spinPkg) ||
            contentDesc.contains("spin") || contentDesc.contains(spinPkg) ||
            viewId.contains("spin") || viewId.contains(spinPkg)) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (isSpinBrowserSettingsScreen(child, spinPkg)) {
                return true
            }
        }
        return false
    }

    private fun isAttemptingToClearData(rootNode: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        if (hasClearDataConfirmationDialog(rootNode)) {
            return true
        }

        val clickedNode = event.source
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && clickedNode != null) {
            if (isClearDataNode(clickedNode)) {
                return true
            }
        }

        return false
    }

    private fun hasClearDataConfirmationDialog(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        val combined = "$text $contentDesc".trim()

        if (combined.contains("cache")) {
            return false
        }

        val dataClearPhrases = listOf(
            "delete app data", "clear app data", "clear all data",
            "all of this app's data will be deleted", "delete all data",
            "clear storage?", "delete data?"
        )

        if (dataClearPhrases.any { combined.contains(it) }) {
            return true
        }

        for (i in 0 until node.childCount) {
            if (hasClearDataConfirmationDialog(node.getChild(i))) {
                return true
            }
        }
        return false
    }

    private fun isClearDataNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        val combined = "$text $contentDesc".trim()

        if (combined.contains("cache")) {
            return false
        }

        val clearDataKeywords = listOf(
            "clear data", "clear storage", "manage space", "clear all data", "delete data", "delete app data"
        )

        return clearDataKeywords.any { combined.contains(it) }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
