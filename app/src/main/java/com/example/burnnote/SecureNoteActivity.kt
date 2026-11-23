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
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.function.Consumer

/**
 * In-memory handoff so decrypted notes never touch Android Intent bundles
 * or immutable Strings, and auto-wrap cleanly into single-slit lines.
 */
object VolatileNoteHandoff {
    private var pendingLines: List<CharArray>? = null

    fun stage(rawText: String, maxCharsPerLine: Int = 26) {
        val chars = rawText.toCharArray()
        try {
            clear()
            pendingLines = wrapCharArray(chars, maxCharsPerLine)
        } finally {
            chars.fill('\u0000')
        }
    }

    fun stageUtf8Bytes(rawBytes: ByteArray, maxCharsPerLine: Int = 26) {
        var decodedChars: CharArray? = null
        try {
            clear()
            val charBuf = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(ByteBuffer.wrap(rawBytes))

            decodedChars = CharArray(charBuf.remaining()).also { charBuf.get(it) }
            if (charBuf.hasArray()) charBuf.array().fill('\u0000')

            pendingLines = wrapCharArray(decodedChars, maxCharsPerLine)
        } finally {
            rawBytes.fill(0)
            decodedChars?.fill('\u0000')
        }
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

    private fun wrapCharArray(chars: CharArray, maxCharsPerLine: Int): List<CharArray> {
        val wrapped = mutableListOf<CharArray>()
        var lineStart = 0
        val n = chars.size

        while (lineStart <= n) {
            var lineEnd = lineStart
            while (lineEnd < n && chars[lineEnd] != '\n') {
                lineEnd++
            }
            val effectiveEnd = if (lineEnd > lineStart && chars[lineEnd - 1] == '\r') {
                lineEnd - 1
            } else {
                lineEnd
            }

            val words = mutableListOf<IntRange>()
            var i = lineStart
            while (i < effectiveEnd) {
                while (i < effectiveEnd && chars[i].isWhitespace()) i++
                val wStart = i
                while (i < effectiveEnd && !chars[i].isWhitespace()) i++
                if (i > wStart) {
                    words.add(wStart until i)
                }
            }

            if (words.isEmpty()) {
                if (lineStart < n) {
                    wrapped.add(" ".toCharArray())
                }
            } else {
                val currentLine = CharArray(maxCharsPerLine)
                var currentLen = 0

                for (word in words) {
                    val wStart = word.first
                    val wLen = word.last - word.first + 1
                    if (wLen > maxCharsPerLine) {
                        if (currentLen > 0) {
                            wrapped.add(currentLine.copyOf(currentLen))
                            currentLen = 0
                        }
                        var pos = wStart
                        val wEndExclusive = wStart + wLen
                        while (pos < wEndExclusive) {
                            val chunkLen = (wEndExclusive - pos).coerceAtMost(maxCharsPerLine)
                            wrapped.add(chars.copyOfRange(pos, pos + chunkLen))
                            pos += chunkLen
                        }
                    } else if (currentLen == 0) {
                        System.arraycopy(chars, wStart, currentLine, 0, wLen)
                        currentLen = wLen
                    } else if (currentLen + 1 + wLen <= maxCharsPerLine) {
                        currentLine[currentLen] = ' '
                        System.arraycopy(chars, wStart, currentLine, currentLen + 1, wLen)
                        currentLen += 1 + wLen
                    } else {
                        wrapped.add(currentLine.copyOf(currentLen))
                        System.arraycopy(chars, wStart, currentLine, 0, wLen)
                        currentLen = wLen
                    }
                }
                if (currentLen > 0) {
                    wrapped.add(currentLine.copyOf(currentLen))
                }
                currentLine.fill('\u0000')
            }

            if (lineEnd >= n) break
            lineStart = lineEnd + 1
        }

        return wrapped.ifEmpty { listOf("(empty note)".toCharArray()) }
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
