package com.example.burnnote

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.util.function.Consumer

/**
 * In-memory handoff so secret text never touches Android Intent IPC bundles.
 * Automatically word-wraps long text into single-slit lines (~26 chars).
 */
object VolatileNoteHandoff {
    private var pendingLines: List<CharArray>? = null

    fun stage(rawText: String, maxCharsPerLine: Int = 26) {
        clear()
        val wrapped = mutableListOf<CharArray>()
        for (paragraph in rawText.replace("\r\n", "\n").split("\n")) {
            if (paragraph.isBlank()) {
                wrapped.add(" ".toCharArray())
                continue
            }
            var current = StringBuilder()
            for (word in paragraph.trim().split(Regex("\\s+"))) {
                if (word.length > maxCharsPerLine) {
                    if (current.isNotEmpty()) {
                        wrapped.add(current.toString().toCharArray())
                        current = StringBuilder()
                    }
                    var i = 0
                    while (i < word.length) {
                        val end = (i + maxCharsPerLine).coerceAtMost(word.length)
                        wrapped.add(word.substring(i, end).toCharArray())
                        i = end
                    }
                } else if (current.isEmpty()) {
                    current.append(word)
                } else if (current.length + 1 + word.length <= maxCharsPerLine) {
                    current.append(' ').append(word)
                } else {
                    wrapped.add(current.toString().toCharArray())
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) {
                wrapped.add(current.toString().toCharArray())
            }
        }
        pendingLines = wrapped.ifEmpty { listOf("(empty note)".toCharArray()) }
    }

    fun consume(): List<CharArray>? {
        val data = pendingLines
        pendingLines = null
        return data
    }

    fun clear() {
        pendingLines?.forEach { it.fill('\u0000') }
        pendingLines = null
    }
}

class SecretNoteBuffer(rawLines: List<CharArray>) {
    private val lines = rawLines.toMutableList()

    fun getLines(): List<CharArray> = lines

    fun wipe() {
        for (line in lines) {
            line.fill('\u0000')
        }
        lines.clear()
    }
}

class SecureNoteActivity : ComponentActivity() {

    private var noteBuffer: SecretNoteBuffer? = null
    private var burnReason by mutableStateOf<String?>(null)

    private var screenCaptureCallback: ScreenCaptureCallback? = null
    private var screenRecordingCallback: Consumer<Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        window.attributes = window.attributes.apply {
            screenBrightness = 0.01f
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }

        val staged = VolatileNoteHandoff.consume()
        if (staged == null) {
            burnReason = "NO_NOTE_PAYLOAD"
        } else {
            noteBuffer = SecretNoteBuffer(staged)
        }

        val deviceTag = generateShortDeviceTag()

        setContent {
            SecureReaderScreen(
                buffer = noteBuffer,
                deviceWatermark = deviceTag,
                burnReason = burnReason,
                onBurnTriggered = { reason -> burnNote(reason) },
                onExit = {
                    burnNote("CLOSED_BY_USER")
                    finish()
                }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        registerCaptureDetectors()
    }

    override fun onStop() {
        super.onStop()
        unregisterCaptureDetectors()
        if (burnReason == null) {
            burnNote("APP_BACKGROUNDED")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        noteBuffer?.wipe()
        noteBuffer = null
    }

    private fun registerCaptureDetectors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val cb = ScreenCaptureCallback {
                burnNote("SCREENSHOT_ATTEMPT")
                notifySenderAlert("SCREENSHOT_ATTEMPT")
            }
            screenCaptureCallback = cb
            registerScreenCaptureCallback(mainExecutor, cb)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val cb = Consumer<Int> { state ->
                if (state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE) {
                    burnNote("SCREEN_RECORDING_DETECTED")
                    notifySenderAlert("SCREEN_RECORDING_DETECTED")
                }
            }
            screenRecordingCallback = cb
            val currentState = windowManager.addScreenRecordingCallback(mainExecutor, cb)
            if (currentState == WindowManager.SCREEN_RECORDING_STATE_VISIBLE) {
                burnNote("SCREEN_RECORDING_ALREADY_ACTIVE")
                notifySenderAlert("SCREEN_RECORDING_ALREADY_ACTIVE")
            }
        }
    }

    private fun unregisterCaptureDetectors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            screenCaptureCallback?.let { unregisterScreenCaptureCallback(it) }
            screenCaptureCallback = null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            screenRecordingCallback?.let { windowManager.removeScreenRecordingCallback(it) }
            screenRecordingCallback = null
        }
    }

    private fun burnNote(reason: String) {
        if (burnReason != null) return
        noteBuffer?.wipe()
        noteBuffer = null
        burnReason = reason
    }

    private fun notifySenderAlert(eventType: String) {
        android.util.Log.w("BurnNoteSecurity", "Sender alert queued: $eventType")
    }

    private fun generateShortDeviceTag(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "DEV"
        val digest = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray())
        val shortHex = digest.take(4).joinToString("") { "%02X".format(it) }
        return "ID-$shortHex"
    }
}
