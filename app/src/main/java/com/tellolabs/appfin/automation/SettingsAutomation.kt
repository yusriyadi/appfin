package com.tellolabs.appfin.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.tellolabs.appfin.domain.*
import java.util.Locale

internal fun normalized(value: CharSequence?): String = value?.toString()?.trim()
    ?.replace(Regex("\\s+"), " ")?.lowercase(Locale.ROOT).orEmpty()

/** Only operates on fresh Settings trees. Never retains nodes between polling steps/events. */
class SettingsAutomation(
    private val service: AccessibilityService,
    private val returnToAppAllowed: () -> Boolean = { true },
) : AutomationPort {
    private var target: InstalledApp? = null
    private var settingsPackage: String? = null
    private var openedAt = 0L
    private var validatedPageWindow: Int? = null
    private var clickedForceStop = false
    private var sessionOpenedSettings = false
    private val stopTexts = setOf("paksa berhenti", "force stop")

    override fun openAppInfo(app: InstalledApp) {
        target = app
        validatedPageWindow = null
        clickedForceStop = false
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val resolved = intent.resolveActivity(service.packageManager) ?: error("Settings unavailable")
        settingsPackage = resolved.packageName
        intent.setPackage(resolved.packageName)
        openedAt = SystemClock.uptimeMillis()
        service.startActivity(intent)
        sessionOpenedSettings = true
    }

    override fun clickForceStop(app: InstalledApp): Interaction {
        if (target != app || SystemClock.uptimeMillis() - openedAt < 350) return Interaction.WAITING
        return withSettingsTree { root, nodes ->
            val texts = nodes.map { normalized(it.text) }
            val identityMatches = normalized(app.packageName) in texts ||
                (app.uniqueLabel && normalized(app.label) in texts)
            if (!identityMatches || nodes.any { isDialogTitle(it.viewIdResourceName) }) {
                return@withSettingsTree Interaction.WAITING
            }
            val matches = nodes.filter {
                it.isVisibleToUser && (normalized(it.text) in stopTexts || normalized(it.contentDescription) in stopTexts)
            }.mapNotNull { node ->
                when {
                    node.isClickable -> node
                    else -> {
                        val parent = node.parent
                        val candidate = nodes.firstOrNull { it == parent && it.isClickable }
                        parent?.recycleCompat()
                        candidate
                    }
                }
            }
            val uniqueMatches = matches.distinct()
            // Multiple candidates or a broad container are ambiguous; wait and eventually time out.
            if (uniqueMatches.size != 1) return@withSettingsTree Interaction.WAITING
            val button = uniqueMatches.single()
            if (!button.isEnabled) return@withSettingsTree Interaction.DISABLED
            validatedPageWindow = root.windowId
            val clicked = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            clickedForceStop = clicked
            if (clicked) Interaction.CLICKED else Interaction.REJECTED
        }
    }

    override fun clickConfirmation(app: InstalledApp): Interaction {
        if (target != app || !clickedForceStop || validatedPageWindow == null) return Interaction.REJECTED
        return withSettingsTree { _, nodes ->
            if (!isForceStopDialog(nodes)) return@withSettingsTree Interaction.WAITING
            val positive = nodes.filter {
                (it.viewIdResourceName == "android:id/button1" || it.viewIdResourceName?.endsWith(":id/btn_positive") == true) &&
                    it.isVisibleToUser && it.isEnabled && it.isClickable &&
                    normalized(it.text) in (stopTexts + setOf("ok", "oke", "ya", "yes"))
            }.singleOrNull() ?: return@withSettingsTree Interaction.WAITING
            if (positive.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                clickedForceStop = false
                Interaction.CLICKED
            } else Interaction.REJECTED
        }
    }

    private fun isDialogTitle(id: String?): Boolean = id == "android:id/alertTitle" || id?.endsWith(":id/text_title") == true

    private fun isForceStopDialog(nodes: List<AccessibilityNodeInfo>): Boolean {
        val dialogText = nodes.filter {
            isDialogTitle(it.viewIdResourceName) || it.viewIdResourceName == "android:id/message" ||
                it.viewIdResourceName?.endsWith(":id/text_message") == true
        }.joinToString(" ") { normalized(it.text) }
        return stopTexts.any { dialogText.contains(it) }
    }

    private fun withSettingsTree(action: (AccessibilityNodeInfo, List<AccessibilityNodeInfo>) -> Interaction): Interaction {
        val windows = service.windows
        try {
            val top = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.maxByOrNull { it.layer }
                ?: return Interaction.WAITING
            val root = top.root ?: return Interaction.WAITING
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            try {
                if (root.packageName?.toString() != settingsPackage) return Interaction.WAITING
                fun walk(node: AccessibilityNodeInfo, depth: Int) {
                    nodes += node
                    if (depth < 35 && nodes.size < 1500) {
                        for (index in 0 until node.childCount) node.getChild(index)?.let { walk(it, depth + 1) }
                    }
                }
                walk(root, 0)
                return action(root, nodes)
            } finally {
                if (nodes.isEmpty()) root.recycleCompat() else nodes.forEach { it.recycleCompat() }
            }
        } finally {
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT < 33) windows.forEach { it.recycle() }
        }
    }

    override fun endTarget() {
        if (clickedForceStop) {
            withSettingsTree { _, nodes ->
                if (isForceStopDialog(nodes)) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                Interaction.WAITING
            }
        }
        target = null
        clickedForceStop = false
        validatedPageWindow = null
    }

    override fun finish() {
        endTarget()
        if (sessionOpenedSettings) {
            sessionOpenedSettings = false
            if (returnToAppAllowed()) {
                service.startActivity(Intent(service, com.tellolabs.appfin.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun AccessibilityNodeInfo.recycleCompat() {
    if (android.os.Build.VERSION.SDK_INT < 33) recycle()
}
