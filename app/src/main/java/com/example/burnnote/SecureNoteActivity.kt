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
 * Holds decrypted note lines as mutable CharArrays so they can be
 * overwritten with null bytes immediately on burn (Layer 1).
 */
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

        // Layer 4: Block screenshots, recordings, casting, and task-switcher previews
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Layer 3: Dark render — force window brightness to minimum (no extra permission needed)
        window.attributes = window.attributes.apply {
            screenBrightness = 0.01f
        }

        // Extra hardening: block overlays (Android 12+) & recents thumbnails (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }

        // Demo payload (replace with decrypted libsodium output in production)
        noteBuffer = SecretNoteBuffer(
            listOf(
                "TOP SECRET // BURN AFTER READING".toCharArray(),
                "Meeting moved to Gate 4 at 19:30.".toCharArray(),
                "Access code for the locker: 84-19-07.".toCharArray(),
                "Do not reply on regular messaging apps.".toCharArray(),
                "Confirm receipt by nodding once.".toCharArray(),
                "This note self-destructs permanently.".toCharArray()
            )
        )

        // Layer 6: Short recipient device watermark hash (replace with Keystore/Account ID in prod)
        val deviceTag = generateShortDeviceTag()

        setContent {
            SecureReaderScreen(
                buffer = noteBuffer,
                deviceWatermark = deviceTag,
                burnReason = burnReason,
                onBurnTriggered = { reason -> burnNote(reason) }
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
        // Burn immediately if user leaves the app (prevents background inspection / second open)
        if (burnReason == null) {
            burnNote("APP_BACKGROUNDED")
        }
    }

    private fun registerCaptureDetectors() {
        // Android 14+: Detect blocked screenshot attempts -> wipe + sender alert
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val cb = ScreenCaptureCallback {
                burnNote("SCREENSHOT_ATTEMPT")
                notifySenderAlert("SCREENSHOT_ATTEMPT")
            }
            screenCaptureCallback = cb
            registerScreenCaptureCallback(mainExecutor, cb)
        }

        // Android 15+: Detect active or newly started MediaProjection screen recording
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
        // Hook for your backend WebSocket / FCM alert endpoint
        android.util.Log.w("BurnNoteSecurity", "Sender alert queued: $eventType")
    }

    private fun generateShortDeviceTag(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "DEV"
        val digest = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray())
        val shortHex = digest.take(4).joinToString("") { "%02X".format(it) }
        return "ID-$shortHex"
    }
}
