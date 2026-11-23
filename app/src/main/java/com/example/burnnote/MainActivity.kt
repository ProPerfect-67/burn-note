package com.example.burnnote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import javax.crypto.AEADBadTagException

private enum class AppScreen {
    HOME, WRITE, OPEN
}

private val BgBlack = Color(0xFF09090B)
private val CardDark = Color(0xFF141418)
private val BorderDim = Color(0xFF27272E)
private val Crimson = Color(0xFFD93838)
private val Emerald = Color(0xFF1F9D6B)
private val TextPrimary = Color(0xFFE4E4E7)
private val TextMuted = Color(0xFF71717A)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            BurnNoteApp(
                onLaunchSecureReaderText = { rawText ->
                    VolatileNoteHandoff.stage(rawText)
                    startActivity(Intent(this, SecureNoteActivity::class.java))
                },
                onLaunchSecureReaderBytes = { plainBytes ->
                    VolatileNoteHandoff.stageUtf8Bytes(plainBytes)
                    startActivity(Intent(this, SecureNoteActivity::class.java))
                }
            )
        }
    }
}

private fun shareText(context: Context, title: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

@Composable
private fun BurnNoteApp(
    onLaunchSecureReaderText: (String) -> Unit,
    onLaunchSecureReaderBytes: (ByteArray) -> Unit
) {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

    BackHandler(enabled = currentScreen != AppScreen.HOME) {
        currentScreen = AppScreen.HOME
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        when (currentScreen) {
            AppScreen.HOME -> HomeScreen(
                onWriteClick = { currentScreen = AppScreen.WRITE },
                onOpenClick = { currentScreen = AppScreen.OPEN },
                onSandboxClick = {
                    onLaunchSecureReaderText(
                        "TOP SECRET // BURN AFTER READING\n" +
                            "Meeting moved to Gate 4 at 19:30.\n" +
                            "Access code for the locker: 84-19-07.\n" +
                            "This demo note self-destructs permanently."
                    )
                }
            )

            AppScreen.WRITE -> WriteNoteScreen(
                onBack = { currentScreen = AppScreen.HOME }
            )

            AppScreen.OPEN -> OpenNoteScreen(
                onBack = { currentScreen = AppScreen.HOME },
                onDecryptedBytes = { bytes -> onLaunchSecureReaderBytes(bytes) }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onWriteClick: () -> Unit,
    onOpenClick: () -> Unit,
    onSandboxClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.padding(top = 36.dp)) {
            Text(
                text = "BURN NOTE",
                color = Crimson,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Text(
                text = "7-LAYER ENCRYPTED READER",
                color = TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Button(
                onClick = onWriteClick,
                colors = ButtonDefaults.buttonColors(containerColor = Crimson),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "ENCRYPT A BURN NOTE",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            OutlinedButton(
                onClick = onOpenClick,
                border = BorderStroke(1.dp, BorderDim),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "DECRYPT & OPEN NOTE",
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            OutlinedButton(
                onClick = onSandboxClick,
                border = BorderStroke(1.dp, Color(0xFF1C1C22)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "TEST READER SANDBOX",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, BorderDim),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "ACTIVE PROTECTIONS (PHASE 2)",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "AES-256-GCM • Split One-Time Key • Local Burn Ledger • FLAG_SECURE • Dark Render • 30s Timer • Anti-Camera Mask • Device Watermark • Single-Line Slit",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun WriteNoteScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var draftText by remember { mutableStateOf("") }
    var bundle by remember { mutableStateOf<EncryptedBundle?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(title = "ENCRYPT NOTE", onBack = onBack)

        if (bundle == null) {
            OutlinedTextField(
                value = draftText,
                onValueChange = { if (it.length <= 600) draftText = it },
                placeholder = {
                    Text(
                        text = "Type your secret message...\nEncrypted locally with a fresh 256-bit one-time key.",
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                },
                minLines = 6,
                maxLines = 10,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Crimson,
                    unfocusedBorderColor = BorderDim,
                    cursorColor = Crimson,
                    focusedContainerColor = CardDark,
                    unfocusedContainerColor = CardDark
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "${draftText.length} / 600 chars",
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.End)
            )

            Button(
                onClick = {
                    val trimmed = draftText.trim()
                    if (trimmed.isNotEmpty()) {
                        val chars = trimmed.toCharArray()
                        // Wipe draft input immediately once encrypted
                        draftText = ""
                        bundle = BurnCrypto.encrypt(chars)
                    }
                },
                enabled = draftText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Crimson),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "ENCRYPT & GENERATE ONE-TIME KEY",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        } else {
            val active = bundle!!

            Text(
                text = "Plaintext wiped from screen. Send Part 1 and Part 2 separately for maximum safety.",
                color = Emerald,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp
            )

            // Part 1: Encrypted Ciphertext
            SecretOutputCard(
                label = "PART 1 // ENCRYPTED NOTE (ID ${active.noteId.take(6)})",
                accent = Crimson,
                value = active.cipherCode,
                onCopy = {
                    clipboard.setText(AnnotatedString(active.cipherCode))
                    Toast.makeText(context, "Encrypted note copied", Toast.LENGTH_SHORT).show()
                },
                onShare = { shareText(context, "Share Encrypted Note", active.cipherCode) }
            )

            // Part 2: One-Time Key
            SecretOutputCard(
                label = "PART 2 // ONE-TIME KEY (DESTROY AFTER SENDING)",
                accent = Emerald,
                value = active.oneTimeKey,
                onCopy = {
                    clipboard.setText(AnnotatedString(active.oneTimeKey))
                    Toast.makeText(context, "One-time key copied", Toast.LENGTH_SHORT).show()
                },
                onShare = { shareText(context, "Share One-Time Key", active.oneTimeKey) }
            )

            OutlinedButton(
                onClick = {
                    val combined = "${active.cipherCode}\n${active.oneTimeKey}"
                    clipboard.setText(AnnotatedString(combined))
                    Toast.makeText(context, "Note + Key copied together (for quick testing)", Toast.LENGTH_SHORT).show()
                },
                border = BorderStroke(1.dp, BorderDim),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text(
                    text = "COPY BOTH (QUICK SELF-TEST)",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = { bundle = null },
                colors = ButtonDefaults.buttonColors(containerColor = BorderDim),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "WIPE & WRITE ANOTHER",
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SecretOutputCard(
    label: String,
    accent: Color,
    value: String,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, accent),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                color = accent,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onCopy,
                    colors = ButtonDefaults.buttonColors(containerColor = BorderDim),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "COPY",
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(
                    onClick = onShare,
                    border = BorderStroke(1.dp, accent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "SHARE",
                        color = accent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenNoteScreen(
    onBack: () -> Unit,
    onDecryptedBytes: (ByteArray) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var inputCipher by remember { mutableStateOf("") }
    var inputKey by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun smartPaste(raw: String) {
        val lines = raw.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        var matched = false
        for (line in lines) {
            if (line.startsWith("BURN2:")) {
                inputCipher = line
                matched = true
            } else if (line.startsWith("KEY2:")) {
                inputKey = line
                matched = true
            }
        }
        if (!matched) {
            if (inputCipher.isBlank()) inputCipher = raw.trim() else inputKey = raw.trim()
        }
        errorMsg = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(title = "DECRYPT NOTE", onBack = onBack)

        OutlinedTextField(
            value = inputCipher,
            onValueChange = {
                inputCipher = it
                errorMsg = null
            },
            label = {
                Text("PART 1: ENCRYPTED NOTE (BURN2:...)", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            },
            minLines = 3,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = Crimson,
                unfocusedBorderColor = BorderDim,
                focusedLabelColor = Crimson,
                unfocusedLabelColor = TextMuted,
                cursorColor = Crimson,
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = inputKey,
            onValueChange = {
                inputKey = it
                errorMsg = null
            },
            label = {
                Text("PART 2: ONE-TIME KEY (KEY2:...)", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            },
            minLines = 2,
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = Emerald,
                unfocusedBorderColor = BorderDim,
                focusedLabelColor = Emerald,
                unfocusedLabelColor = TextMuted,
                cursorColor = Emerald,
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
        OutlinedButton(
            onClick = onBack,
            border = BorderStroke(1.dp, BorderDim),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "< BACK",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
        Text(
            text = title,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
 }
        
